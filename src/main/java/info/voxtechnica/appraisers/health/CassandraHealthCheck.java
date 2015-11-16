package info.voxtechnica.appraisers.health;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.health.HealthCheck;
import com.datastax.driver.core.Metrics;
import info.voxtechnica.appraisers.client.CassandraClient;

public class CassandraHealthCheck extends HealthCheck {
    private final CassandraClient cassandraClient;

    public CassandraHealthCheck(CassandraClient cassandraClient) {
        this.cassandraClient = cassandraClient;
    }

    @Override
    protected Result check() throws Exception {
        if (cassandraClient.keyspaceExists()) {
            Metrics metrics = cassandraClient.getCluster().getMetrics();
            Gauge<Integer> connections = metrics.getOpenConnections();
            String message = String.format("cassandra=%s protocol=%d cluster=%s connections=%d",
                    cassandraClient.getCassandraVersion(),
                    cassandraClient.getProtocolVersion(),
                    cassandraClient.getCluster().getClusterName(),
                    connections.getValue());
            return Result.healthy(message);
        } else return Result.unhealthy("keyspace '" + cassandraClient.getKeyspaceName() + "' does not exist.");
    }
}
