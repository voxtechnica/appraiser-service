package info.voxtechnica.appraisers.client;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.LoggingRetryPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import info.voxtechnica.appraisers.config.CassandraConfiguration;
import info.voxtechnica.appraisers.db.CassandraEmbedded;
import info.voxtechnica.appraisers.db.policy.SimpleRetryPolicy;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * CassandraClient provides a database client for Apache Cassandra. Credits go to Stuart Gunter for the
 * Dropwizard-Cassandra Bundle, which informed this and related classes.
 *
 * @see <a href="https://github.com/stuartgunter/dropwizard-cassandra">Dropwizard-Cassandra Bundle</a>
 */
public class CassandraClient implements Managed {
    private final Logger LOG = LoggerFactory.getLogger(CassandraClient.class);
    private final CassandraConfiguration dbConfig;
    private final String keyspaceName;
    private final Boolean createIfMissing;
    private Cluster cluster;
    private Session clusterSession;
    private Session session = null;
    private String cassandraVersion = null;
    private Integer protocolVersion = null;

    public CassandraClient(CassandraConfiguration dbConfig) throws IOException {
        long startTime = System.currentTimeMillis();
        this.dbConfig = dbConfig;
        this.keyspaceName = dbConfig.getKeyspace();
        this.createIfMissing = dbConfig.isCreateIfMissing();
        Cluster.Builder builder = Cluster.builder();
        if (dbConfig.getHosts().contains("embedded")) {
            // spin up a local Cassandra node for development and testing
            CassandraEmbedded.start();
            builder.addContactPoint("localhost");
        } else for (String host : dbConfig.getHosts()) builder.addContactPoint(host);
        builder.withPort(dbConfig.getPort());
        builder.withCompression(ProtocolOptions.Compression.LZ4);
        builder.withProtocolVersion(ProtocolVersion.NEWEST_SUPPORTED);
        builder.withRetryPolicy(new LoggingRetryPolicy(new SimpleRetryPolicy(dbConfig.getReadRetries(), dbConfig.getWriteRetries())));
        builder.withLoadBalancingPolicy(new TokenAwarePolicy(new LatencyAwarePolicy.Builder(new RoundRobinPolicy()).withRetryPeriod(60, TimeUnit.SECONDS).build()));
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.setConsistencyLevel(ConsistencyLevel.QUORUM);
        queryOptions.setSerialConsistencyLevel(ConsistencyLevel.SERIAL);
        builder.withQueryOptions(queryOptions);
        builder.withClusterName(dbConfig.getClusterName());
        builder.withTimestampGenerator(new AtomicMonotonicTimestampGenerator());
        cluster = builder.build();
        clusterSession = cluster.connect();
        // Get Cassandra Version
        Row row = clusterSession.execute("SELECT release_version FROM system.local WHERE key='local'").one();
        if (row != null) cassandraVersion = row.getString("release_version");
        // Get Protocol Version
        protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersion().toInt();
        // Get Keyspace Session (creating Keyspace if necessary and permitted)
        if (keyspaceExists()) session = cluster.connect(keyspaceName);
        else if (dbConfig.isCreateIfMissing()) {
            createKeyspace();
            // Table creation/updates reside in the individual DAOs (plural entities)
            LOG.info("Created keyspace {}", keyspaceName);
        }

        Long duration = System.currentTimeMillis() - startTime;
        LOG.info("Initialized in {} ms: cassandra={} protocol={} cluster={} dataCenter={} keyspace={} replication={} hosts={}",
                duration, cassandraVersion, protocolVersion, dbConfig.getClusterName(), dbConfig.getDataCenterName(),
                dbConfig.getKeyspace(), dbConfig.getReplicationFactor(), String.join(",", dbConfig.getHosts()));
    }

    @Override
    public void start() throws Exception {
        // started in the constructor
    }

    @Override
    public void stop() throws Exception {
        LOG.info("Shutting down Cassandra client for cluster: {}", cluster.getClusterName());
        cluster.close();
        if (CassandraEmbedded.isRunning()) {
            LOG.info("Shutting down embedded Cassandra server...");
            CassandraEmbedded.stop();
        }
    }

    public Boolean keyspaceExists() {
        return cluster.getMetadata().getKeyspace(keyspaceName) != null;
    }

    public void dropKeyspace() {
        session = null;
        clusterSession.execute("DROP KEYSPACE " + keyspaceName);
    }

    /**
     * Create a keyspace with the provided name.
     * Single-node environments use the SimpleStrategy and replication factor 1.
     * Multi-node environments use NetworkTopologyStrategy and the provided datacenter name and replication factor.
     */
    public void createKeyspace() {
        StringBuilder statement = new StringBuilder("CREATE KEYSPACE IF NOT EXISTS ");
        statement.append(keyspaceName);
        statement.append(" WITH REPLICATION = {'class': ");
        if (dbConfig.getReplicationFactor() == 1) {
            statement.append("'SimpleStrategy', 'replication_factor': 1}");
        } else {
            statement.append("'NetworkTopologyStrategy', '");
            statement.append(dbConfig.getDataCenterName());
            statement.append("' : ");
            statement.append(dbConfig.getReplicationFactor());
            statement.append("}");
        }
        clusterSession.execute(statement.toString());
        session = cluster.connect(keyspaceName);
    }

    public CassandraConfiguration getDbConfig() {
        return dbConfig;
    }

    public Boolean isCreateIfMissing() {
        return createIfMissing;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public String getKeyspaceName() {
        return keyspaceName;
    }

    public Session getClusterSession() {
        return clusterSession;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getCassandraVersion() {
        return cassandraVersion;
    }

    public Integer getProtocolVersion() {
        return protocolVersion;
    }
}
