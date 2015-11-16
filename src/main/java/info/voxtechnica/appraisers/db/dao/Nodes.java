package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.*;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.model.Node;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Node IDs are used by the TuidFactory to ensure cluster-unique identifiers (they're part of the 18 bits of entropy).
 * Each node in the application cluster gets an 8-bit (one byte) nodeId, ranging from -127 to 127.
 */
public class Nodes {
    private static final Logger LOG = LoggerFactory.getLogger(Nodes.class);
    private static boolean initialized = false;
    private static Session session = null;
    private static PreparedStatement psWriteNode = null;
    private static PreparedStatement psReadNode = null;
    private static PreparedStatement psReadNodeHistory = null;
    private static PreparedStatement psReadNodeIds = null;
    private static PreparedStatement psDeleteNode = null;
    private static PreparedStatement psWriteNodeIp = null;
    private static PreparedStatement psReadNodeIp = null;
    private static PreparedStatement psDeleteNodeIp = null;

    private static final String createTableNodes = "CREATE TABLE IF NOT EXISTS nodes (\n" +
            "  id int,\n" +
            "  updatedAt timestamp,\n" +
            "  ip_address inet,\n" +
            "  PRIMARY KEY ((id), updatedAt)\n" +
            ") WITH\n" +
            "  comment='Node ID Assignment History' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableNodesIp = "CREATE TABLE IF NOT EXISTS nodes_ip (\n" +
            "  ip_address inet,\n" +
            "  id int,\n" +
            "  createdAt timestamp,\n" +
            "  PRIMARY KEY (ip_address)\n" +
            ") WITH\n" +
            "  comment='Node ID Assignment' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient) {
        if (!initialized) {
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableNodes);
                    session.execute(createTableNodesIp);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one)
            psWriteNode = session.prepare("INSERT INTO nodes (id, updatedAt, ip_address) VALUES (?, ?, ?)");
            psReadNode = session.prepare("SELECT * FROM nodes WHERE id=? ORDER BY updatedAt DESC LIMIT 1");
            psReadNodeHistory = session.prepare("SELECT * FROM nodes WHERE id=?");
            psReadNodeIds = session.prepare("SELECT DISTINCT id FROM nodes");
            psDeleteNode = session.prepare("DELETE FROM nodes WHERE id=?");
            psWriteNodeIp = session.prepare("INSERT INTO nodes_ip (ip_address, id, createdAt) VALUES (?, ?, ?)");
            psReadNodeIp = session.prepare("SELECT * FROM nodes_ip WHERE ip_address=?");
            psDeleteNodeIp = session.prepare("DELETE FROM nodes_ip WHERE ip_address=?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Node createNode(Node node) {
        node.setTimestamp(new Date());
        BatchStatement batch = new BatchStatement();
        batch.add(psWriteNode.bind((int) node.getId(), node.getTimestamp(), node.getIpAddress()));
        batch.add(psWriteNodeIp.bind(node.getIpAddress(), (int) node.getId(), node.getTimestamp()));
        session.execute(batch);
        return node;
    }

    public static Node updateNode(Node node) {
        node.setTimestamp(new Date());
        session.execute(psWriteNode.bind((int) node.getId(), node.getTimestamp(), node.getIpAddress()));
        return node;
    }

    public static Node readNode(Byte id) {
        Row row = session.execute(psReadNode.bind((int) id)).one();
        if (row == null) return null;
        return new Node((byte) row.getInt("id"), row.getInet("ip_address"), row.getTimestamp("updatedAt"));
    }

    public static List<Node> readNodeHistory(Byte id) {
        List<Node> list = new ArrayList<>();
        ResultSet resultSet = session.execute(psReadNodeHistory.bind((int) id));
        for (Row row : resultSet.all())
            list.add(new Node((byte) row.getInt("id"), row.getInet("ip_address"), row.getTimestamp("updatedAt")));
        return list;
    }

    public static Node readNode(InetAddress ipAddress) {
        Row row = session.execute(psReadNodeIp.bind(ipAddress)).one();
        if (row == null) return null;
        return new Node((byte) row.getInt("id"), row.getInet("ip_address"), row.getTimestamp("createdAt"));
    }

    public static List<Byte> readNodeIds() {
        List<Byte> list = new ArrayList<>();
        ResultSet resultSet = session.execute(psReadNodeIds.bind());
        for (Row row : resultSet.all())
            list.add((byte) row.getInt("id"));
        return list;
    }

    public static SortedSet<Node> readNodes() throws ExecutionException, InterruptedException {
        List<Byte> ids = readNodeIds();
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = Lists.newArrayListWithExpectedSize(ids.size());
        for (Byte id : ids) if (id != null) futures.add(session.executeAsync(psReadNode.bind((int) id)));
        // Process the results as they come in, creating Nodes in sorted order
        ConcurrentSkipListSet<Node> nodes = new ConcurrentSkipListSet<>(Node.NodeIdOrder);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null) nodes.add(new Node((byte) row.getInt("id"), row.getInet("ip_address"), row.getTimestamp("updatedAt")));
        }
        return nodes;
    }

    public static void deleteNode(Byte id) {
        Node node = readNode(id);
        if (node != null) deleteNode(node);
    }

    public static void deleteNode(Node node) {
        BatchStatement batch = new BatchStatement();
        batch.add(psDeleteNode.bind((int) node.getId()));
        batch.add(psDeleteNodeIp.bind(node.getIpAddress()));
        session.execute(batch);
    }

    /**
     * Get an auto-assigned node ID reservation for use when initializing a TuidFactory.
     *
     * @param ipAddress The IP address of the application server, assuming only one TuidFactory per IP address
     * @return the previously-assigned Node, or an available Node on first use
     */
    public static Node getNode(InetAddress ipAddress) {
        if (ipAddress == null) return null;
        // Look for an existing reservation
        Node node = readNode(ipAddress);
        if (node != null) return updateNode(node);
        // Get a list of currently-used nodeIds
        List<Byte> existingNodeIds = readNodeIds();
        // Create a list of available nodeIds
        List<Byte> availableNodeIds = new ArrayList<>();
        for (byte b = 127; b >= -127; b--) if (!existingNodeIds.contains(b)) availableNodeIds.add(b);
        // If we've run out of IDs, return null
        if (availableNodeIds.isEmpty()) return null;
        // Create a new Node using a randomly-selected available nodeId
        node = new Node();
        node.setId(availableNodeIds.get(ThreadLocalRandom.current().nextInt(0, availableNodeIds.size())));
        node.setIpAddress(ipAddress);
        return createNode(node);
    }

}
