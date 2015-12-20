package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.*;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.model.Import;
import info.voxtechnica.appraisers.model.Tuid;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

/**
 * Imports track ASC license import operations (when they were run) and how many licenses were created, updated, or ignored.
 * The integer 'day' is in the BASIC_ISO_DATE form of 'YYYYMMDD'. It can be historical, rather than the date of the import.
 */
public class Imports {
    private static final Logger LOG = LoggerFactory.getLogger(Imports.class);
    private static boolean initialized = false;
    private static Session session;

    private static PreparedStatement psIncrementCreated;
    private static PreparedStatement psIncrementUpdated;
    private static PreparedStatement psIncrementIgnored;
    private static PreparedStatement psReadImport;
    private static PreparedStatement psReadImports;
    private static PreparedStatement psReadImportsDay;
    private static PreparedStatement psDeleteImport;

    private static final String createTableImportsDay = "CREATE TABLE IF NOT EXISTS imports_day (\n" +
            "  day int,\n" +
            "  id text,\n" +
            "  created counter,\n" +
            "  updated counter,\n" +
            "  ignored counter,\n" +
            "  PRIMARY KEY ((day), id)\n" +
            ") WITH\n" +
            "  comment='License Import Counts by Day' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient) {
        if (!initialized) {
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableImportsDay);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one):
            psIncrementCreated = session.prepare("UPDATE imports_day SET created = created + 1 WHERE day=? AND id=?");
            psIncrementUpdated = session.prepare("UPDATE imports_day SET updated = updated + 1 WHERE day=? AND id=?");
            psIncrementIgnored = session.prepare("UPDATE imports_day SET ignored = ignored + 1 WHERE day=? AND id=?");
            psReadImport = session.prepare("SELECT * FROM imports_day WHERE day=? AND id=?");
            psReadImports = session.prepare("SELECT * FROM imports_day");
            psReadImportsDay = session.prepare("SELECT * FROM imports_day WHERE day=?");
            psDeleteImport = session.prepare("DELETE FROM imports_day WHERE day=? AND id=?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void incrementCreated(String id, Integer day) {
        if (day == null) day = (new Tuid(id)).getYearMonthDay();
        session.execute(psIncrementCreated.bind(day, id));
    }

    public static void incrementUpdated(String id, Integer day) {
        if (day == null) day = (new Tuid(id)).getYearMonthDay();
        session.execute(psIncrementUpdated.bind(day, id));
    }

    public static void incrementIgnored(String id, Integer day) {
        if (day == null) day = (new Tuid(id)).getYearMonthDay();
        session.execute(psIncrementIgnored.bind(day, id));
    }

    public static void deleteImport(String id, Integer day) {
        if (day == null) day = (new Tuid(id)).getYearMonthDay();
        session.execute(psDeleteImport.bind(day, id));
    }

    public static Import readImport(String id, Integer day) {
        if (day == null) day = (new Tuid(id)).getYearMonthDay();
        Row row = session.execute(psReadImport.bind(day, id)).one();
        return row == null ? null : new Import(row.getString("id"), row.getInt("day"), row.getLong("created"), row.getLong("updated"), row.getLong("ignored"));
    }

    public static SortedSet<Import> readImports() {
        SortedSet<Import> imports = new TreeSet<Import>();
        ResultSet resultSet = session.execute(psReadImports.bind());
        for (Row row : resultSet.all())
            imports.add(new Import(row.getString("id"), row.getInt("day"), row.getLong("created"), row.getLong("updated"), row.getLong("ignored")));
        return imports;
    }

    public static SortedSet<Import> readImports(Integer day) {
        SortedSet<Import> imports = new TreeSet<Import>();
        ResultSet resultSet = session.execute(psReadImportsDay.bind(day));
        for (Row row : resultSet.all())
            imports.add(new Import(row.getString("id"), row.getInt("day"), row.getLong("created"), row.getLong("updated"), row.getLong("ignored")));
        return imports;
    }

    public static SortedSet<Import> readImports(List<Integer> days) throws IOException, ExecutionException, InterruptedException {
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = Lists.newArrayListWithExpectedSize(days.size());
        for (Integer day : days) if (day != null) futures.add(session.executeAsync(psReadImportsDay.bind(day)));
        // Process the results as they come in, deserializing in sorted order
        ConcurrentSkipListSet<Import> imports = new ConcurrentSkipListSet<>(Import.ChronologicalDay);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null)
                imports.add(new Import(row.getString("id"), row.getInt("day"), row.getLong("created"), row.getLong("updated"), row.getLong("ignored")));
        }
        return imports;
    }
}
