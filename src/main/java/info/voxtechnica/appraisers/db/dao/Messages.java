package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.*;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.client.SendGridClient;
import info.voxtechnica.appraisers.client.SlackClient;
import info.voxtechnica.appraisers.model.Message;
import info.voxtechnica.appraisers.util.JsonSerializer;
import info.voxtechnica.appraisers.util.TuidFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

/**
 * A ‘message’ is a notification of some sort. It could be delivered by email, SMS, HipChat, or through a call-back
 * service, using our own protocol and message format. Only administrators can send messages through the API, but users
 * can see their own messages. Usually, the Message Service will be used internally by other services for things like
 * sending password reset messages and verifying email addresses.
 */
public class Messages {
    private static final Logger LOG = LoggerFactory.getLogger(Messages.class);
    private static boolean initialized = false;
    private static Session session;
    private static PreparedStatement psWriteVersion;
    private static PreparedStatement psReadAllIds;
    private static PreparedStatement psReadVersion;
    private static PreparedStatement psReadVersions;
    private static PreparedStatement psReadCurrentVersion;
    private static PreparedStatement psDeleteVersions;
    private static PreparedStatement psWriteEmailId;
    private static PreparedStatement psReadEmailId;
    private static PreparedStatement psDeleteEmailId;

    private static final String createTableMessages = "CREATE TABLE IF NOT EXISTS messages (\n" +
            "  id text,\n" +
            "  update_id text,\n" +
            "  json text,\n" +
            "  PRIMARY KEY ((id), update_id)\n" +
            ") WITH\n" +
            "  comment='Messages' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableMessageIdsEmail = "CREATE TABLE IF NOT EXISTS message_ids_email (\n" +
            "  email text,\n" +
            "  message_id text,\n" +
            "  PRIMARY KEY ((email), message_id)\n" +
            ") WITH\n" +
            "  comment='Messages by Email Address' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient) {
        if (!initialized) {
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableMessages);
                    session.execute(createTableMessageIdsEmail);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one):
            // regular object persistence
            psWriteVersion = session.prepare("INSERT INTO messages (id, update_id, json) VALUES (?, ?, ?)");
            psReadAllIds = session.prepare("SELECT DISTINCT id FROM messages LIMIT ?");
            psReadVersion = session.prepare("SELECT * FROM messages WHERE id=? AND update_id=?");
            psReadVersions = session.prepare("SELECT * FROM messages WHERE id=? ORDER BY update_id ASC");
            psReadCurrentVersion = session.prepare("SELECT * FROM messages WHERE id=? ORDER BY update_id DESC LIMIT 1");
            psDeleteVersions = session.prepare("DELETE FROM messages WHERE id=?");
            // email index
            psWriteEmailId = session.prepare("INSERT INTO message_ids_email (email, message_id) VALUES (?, ?)");
            psReadEmailId = session.prepare("SELECT message_id FROM message_ids_email WHERE email=? AND message_id > ? ORDER BY message_id ASC LIMIT ?");
            psDeleteEmailId = session.prepare("DELETE FROM message_ids_email WHERE email=? AND message_id=?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Message createMessage(Message msg) throws IOException {
        if (msg.getId() == null) msg.setId(TuidFactory.getId());
        msg.setUpdateId(msg.getId());
        if (Message.Type.EMAIL.equals(msg.getType())) msg.setStatus(SendGridClient.send(msg));
        else if (Message.Type.SLACK.equals(msg.getType())) msg.setStatus(SlackClient.send(msg));
        BatchStatement batch = new BatchStatement();
        batch.add(psWriteVersion.bind(msg.getId(), msg.getUpdateId(), JsonSerializer.getJson(msg)));
        if (msg.getFrom() != null) batch.add(psWriteEmailId.bind(msg.getFrom(), msg.getId()));
        if (msg.getRecipients() != null)
            for (String email : msg.getRecipients()) batch.add(psWriteEmailId.bind(email, msg.getId()));
        session.execute(batch);
        return msg;
    }

    public static Message updateMessage(Message msg, Boolean resend) throws IOException {
        if (msg.getId() == null) throw new MissingResourceException("Missing ID", Message.class.getSimpleName(), "id");
        msg.setUpdateId(TuidFactory.getId());
        if (resend != null && resend) {
            if (Message.Type.EMAIL.equals(msg.getType())) msg.setStatus(SendGridClient.send(msg));
            else if (Message.Type.SLACK.equals(msg.getType())) msg.setStatus(SlackClient.send(msg));
        }
        BatchStatement batch = new BatchStatement();
        batch.add(psWriteVersion.bind(msg.getId(), msg.getUpdateId(), JsonSerializer.getJson(msg)));
        if (msg.getFrom() != null) batch.add(psWriteEmailId.bind(msg.getFrom(), msg.getId()));
        if (msg.getRecipients() != null)
            for (String email : msg.getRecipients()) batch.add(psWriteEmailId.bind(email, msg.getId()));
        session.execute(batch);
        return msg;
    }

    public static void deleteMessage(String id) throws IOException {
        if (id == null) throw new MissingResourceException("Missing ID", Message.class.getSimpleName(), "id");
        Message msg = readMessage(id);
        if (msg == null) throw new MissingResourceException("Message not found", Message.class.getSimpleName(), "id");
        BatchStatement batch = new BatchStatement();
        batch.add(psDeleteVersions.bind(id));
        if (msg.getFrom() != null) batch.add(psDeleteEmailId.bind(msg.getFrom(), msg.getId()));
        if (msg.getRecipients() != null)
            for (String email : msg.getRecipients()) batch.add(psDeleteEmailId.bind(email, msg.getId()));
        session.execute(batch);
    }

    public static Message readMessage(String id) throws IOException {
        if (id == null) return null;
        Row row = session.execute(psReadCurrentVersion.bind(id)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), Message.class);
    }

    public static Message readMessageVersion(String id, String updateId) throws IOException {
        if (id == null || updateId == null) return null;
        Row row = session.execute(psReadVersion.bind(id, updateId)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), Message.class);
    }

    public static List<Message> readMessageVersions(String id) throws IOException {
        if (id == null) return null;
        ArrayList<Message> versions = new ArrayList<>();
        ResultSet resultSet = session.execute(psReadVersions.bind(id));
        for (Row row : resultSet.all()) versions.add(JsonSerializer.getObject(row.getString("json"), Message.class));
        return versions;
    }

    private static List<String> readIds(BoundStatement query, String idField) {
        ArrayList<String> ids = new ArrayList<>();
        ResultSet resultSet = session.execute(query);
        for (Row idRow : resultSet.all()) ids.add(idRow.getString(idField));
        return ids;
    }

    public static SortedSet<Message> readMessages(List<String> ids, Comparator<Message> order) throws IOException, ExecutionException, InterruptedException {
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = Lists.newArrayListWithExpectedSize(ids.size());
        for (String id : ids) if (id != null) futures.add(session.executeAsync(psReadCurrentVersion.bind(id)));
        // Process the results as they come in, deserializing JSON to pojos in sorted order
        ConcurrentSkipListSet<Message> messages = new ConcurrentSkipListSet<>(order);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null) messages.add(JsonSerializer.getObject(row.getString("json"), Message.class));
        }
        return messages;
    }

    public static SortedSet<Message> readMessages(Integer limit) throws IOException, ExecutionException, InterruptedException {
        return readMessages(readIds(psReadAllIds.bind(limit == null ? 100000 : limit), "id"), Message.Chronological);
    }

    public static SortedSet<Message> readMessagesByEmail(String email, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return email == null ? null : readMessages(readIds(psReadEmailId.bind(email, offset == null ? "0" : offset, limit == null ? 100000 : limit), "message_id"), Message.Chronological);
    }

}
