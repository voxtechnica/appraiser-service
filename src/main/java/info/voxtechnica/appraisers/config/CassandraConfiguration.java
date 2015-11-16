package info.voxtechnica.appraisers.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration settings for the Apache Cassandra database
 */
@Data
public class CassandraConfiguration {
    private List<String> hosts = new ArrayList<>();
    private Integer port = 9042;
    private String keyspace = "appraisers";
    private String clusterName = "Production";
    private String dataCenterName = "us-west-2";
    private int replicationFactor = 3;
    private int readRetries = 3;
    private int writeRetries = 3;
    private boolean createIfMissing = true;
}
