package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.*;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.model.Metric;
import info.voxtechnica.appraisers.model.MetricCount;
import info.voxtechnica.appraisers.model.MetricStat;
import info.voxtechnica.appraisers.util.JsonSerializer;
import info.voxtechnica.appraisers.util.TuidFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

/**
 * Metric is a performance metric used for calculating count and duration (millisecond) statistics.
 */
public class Metrics {
    private static final Logger LOG = LoggerFactory.getLogger(Metrics.class);
    private static boolean initialized = false;
    private static Integer ttl = 7776000; // TODO: put this Time-to-Live (TTL) in config file (90 days = 7776000 seconds)
    private static Session session;
    private static PreparedStatement psWriteMetric;
    private static PreparedStatement psReadMetric;
    private static PreparedStatement psDeleteMetric;
    private static PreparedStatement psWriteMetricTag;
    private static PreparedStatement psReadIdsTag;
    private static PreparedStatement psReadMetricDurationsTag;
    private static PreparedStatement psDeleteMetricTag;
    private static PreparedStatement psWriteCountTag;
    private static PreparedStatement psWriteDurationTag;
    private static PreparedStatement psReadCountsTag;
    private static PreparedStatement psWriteIdEntity;
    private static PreparedStatement psReadIdsEntity;
    private static PreparedStatement psDeleteIdEntity;

    private static final String createTableMetrics = "CREATE TABLE IF NOT EXISTS metrics (\n" +
            "  id text,\n" +
            "  json text,\n" +
            "  PRIMARY KEY (id)\n" +
            ") WITH\n" +
            "  comment='Metrics' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableMetricsTag = "CREATE TABLE IF NOT EXISTS metrics_tag (\n" +
            "  tag text,\n" +
            "  metric_id text,\n" +
            "  duration bigint,\n" +
            "  PRIMARY KEY ((tag), metric_id)\n" +
            ") WITH\n" +
            "  comment='Metrics by Tag' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableMetricCountsTag = "CREATE TABLE IF NOT EXISTS metric_counts_tag (\n" +
            "  tag text,\n" +
            "  metric_count counter,\n" +
            "  metric_duration counter,\n" +
            "  PRIMARY KEY (tag)\n" +
            ") WITH\n" +
            "  comment='Metric Counts by Tag' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableMetricIdsEntity = "CREATE TABLE IF NOT EXISTS metric_ids_entity (\n" +
            "  entity_id text,\n" +
            "  metric_id text,\n" +
            "  PRIMARY KEY ((entity_id), metric_id)\n" +
            ") WITH\n" +
            "  comment='Metrics by Entity ID' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient) {
        if (!initialized) {
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableMetrics);
                    session.execute(createTableMetricsTag);
                    session.execute(createTableMetricCountsTag);
                    session.execute(createTableMetricIdsEntity);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one):
            // regular object persistence
            psWriteMetric = session.prepare("INSERT INTO metrics (id, json) VALUES (?, ?) USING TTL ?");
            psReadMetric = session.prepare("SELECT json FROM metrics WHERE id=?");
            psDeleteMetric = session.prepare("DELETE FROM metrics WHERE id=?");
            // tag
            psWriteMetricTag = session.prepare("INSERT INTO metrics_tag (tag, metric_id, duration) VALUES (?, ?, ?) USING TTL ?");
            psReadIdsTag = session.prepare("SELECT metric_id FROM metrics_tag WHERE tag=? AND metric_id > ? ORDER BY metric_id ASC LIMIT ?");
            psReadMetricDurationsTag = session.prepare("SELECT metric_id, duration FROM metrics_tag WHERE tag=? AND metric_id > ? ORDER BY metric_id ASC LIMIT ?");
            psDeleteMetricTag = session.prepare("DELETE FROM metrics_tag WHERE tag=? AND metric_id=?");
            // tag counts
            psWriteCountTag = session.prepare("UPDATE metric_counts_tag SET metric_count = metric_count + 1 WHERE tag=?");
            psWriteDurationTag = session.prepare("UPDATE metric_counts_tag SET metric_duration = metric_duration + ? WHERE tag=?");
            psReadCountsTag = session.prepare("SELECT tag, metric_count, metric_duration FROM metric_counts_tag");
            // entity
            psWriteIdEntity = session.prepare("INSERT INTO metric_ids_entity (entity_id, metric_id) VALUES (?, ?) USING TTL ?");
            psReadIdsEntity = session.prepare("SELECT metric_id FROM metric_ids_entity WHERE entity_id=? AND metric_id > ? ORDER BY metric_id ASC LIMIT ?");
            psDeleteIdEntity = session.prepare("DELETE from metric_ids_entity WHERE entity_id=? AND metric_id=?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Metric createMetric(@NotNull Metric metric) throws IOException {
        // Atomicity is less important for metrics, and they're very high volume, so we don't bother using BatchStatement here
        if (metric.getId() == null) metric.setId(TuidFactory.getId());
        session.executeAsync(psWriteMetric.bind(metric.getId(), JsonSerializer.getJson(metric), ttl));
        if (metric.getTags() != null) for (String tag : metric.getTags()) {
            session.executeAsync(psWriteMetricTag.bind(tag, metric.getId(), metric.getDuration(), ttl));
            session.executeAsync(psWriteCountTag.bind(tag));
            session.executeAsync(psWriteDurationTag.bind(metric.getDuration() == null ? 0 : metric.getDuration(), tag));
        }
        if (metric.getEntityIds() != null) for (String entity_id : metric.getEntityIds())
            session.executeAsync(psWriteIdEntity.bind(entity_id, metric.getId(), ttl));
        return metric;
    }

    public static Metric readMetric(@NotNull String id) throws IOException {
        Row row = session.execute(psReadMetric.bind(id)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), Metric.class);
    }

    public static void deleteMetric(@NotNull String id) throws IOException {
        Metric metric = readMetric(id);
        if (metric != null) {
            BatchStatement batch = new BatchStatement();
            batch.add(psDeleteMetric.bind(id));
            if (metric.getTags() != null)
                for (String tag : metric.getTags()) batch.add(psDeleteMetricTag.bind(tag, metric.getId()));
            if (metric.getEntityIds() != null)
                for (String entity_id : metric.getEntityIds())
                    batch.add(psDeleteIdEntity.bind(entity_id, metric.getId()));
            session.execute(batch);
        }
    }

    private static List<String> readMetricIds(BoundStatement query) {
        ArrayList<String> ids = new ArrayList<>();
        ResultSet resultSet = session.execute(query);
        for (Row idRow : resultSet.all()) ids.add(idRow.getString("metric_id"));
        return ids;
    }

    public static SortedSet<Metric> readMetrics(List<String> ids, Comparator<Metric> order) throws IOException, ExecutionException, InterruptedException {
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = Lists.newArrayListWithExpectedSize(ids.size());
        for (String id : ids) if (id != null) futures.add(session.executeAsync(psReadMetric.bind(id)));
        // Process the results as they come in, deserializing JSON to pojos in natural sorted order
        ConcurrentSkipListSet<Metric> metrics = new ConcurrentSkipListSet<>(order);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null) metrics.add(JsonSerializer.getObject(row.getString("json"), Metric.class));
        }
        return metrics;
    }

    public static SortedSet<Metric> readMetricsByTag(@NotNull String tag, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return readMetrics(readMetricIds(psReadIdsTag.bind(tag, offset == null ? "0" : offset, limit == null ? 100000 : limit)), Metric.Chronological);
    }

    public static SortedSet<Metric> readMetricsByEntity(@NotNull String entityId, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return readMetrics(readMetricIds(psReadIdsEntity.bind(entityId, offset == null ? "0" : offset, limit == null ? 100000 : limit)), Metric.Chronological);
    }

    public static Map<String, Long> readMetricDurationsByTag(@NotNull String tag, Integer limit, String offset) {
        Map<String, Long> map = new TreeMap<>();
        ResultSet resultSet = session.execute(psReadMetricDurationsTag.bind(tag, offset == null ? "0" : offset, limit == null ? 100000 : limit));
        for (Row row : resultSet.all()) map.put(row.getString("metric_id"), row.getLong("duration"));
        return map;
    }

    public static MetricStat readMetricDurationStatsByTag(@NotNull String tag, Integer limit, String offset) {
        MetricStat stat = new MetricStat(tag);
        ResultSet resultSet = session.execute(psReadMetricDurationsTag.bind(tag, offset == null ? "0" : offset, limit == null ? 100000 : limit));
        for (Row row : resultSet.all()) stat.addDuration(row.getString("metric_id"), row.getLong("duration"));
        return stat;
    }

    public static Map<String, Long> readRecentMetricDurationsByTag(@NotNull String tag, @NotNull Integer seconds) {
        String offset = TuidFactory.getFirstId((long) -seconds * 1000L);
        return readMetricDurationsByTag(tag, null, offset);
    }

    public static MetricStat readRecentMetricDurationStatsByTag(@NotNull String tag, @NotNull Integer seconds) {
        String offset = TuidFactory.getFirstId((long) -seconds * 1000L);
        return readMetricDurationStatsByTag(tag, null, offset);
    }

    public static List<MetricCount> readMetricCountsByTag() {
        List<MetricCount> counts = new ArrayList<>();
        ResultSet resultSet = session.execute(psReadCountsTag.bind());
        for (Row row : resultSet.all())
            counts.add(new MetricCount(row.getString("tag"), row.getLong("metric_count"), row.getLong("metric_duration")));
        Collections.sort(counts);
        return counts;
    }

}
