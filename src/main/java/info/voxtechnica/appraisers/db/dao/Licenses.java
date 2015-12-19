package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.*;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.model.License;
import info.voxtechnica.appraisers.model.Tuid;
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
 * Licenses are used to capture and retain raw, unimproved information about appraisers, their contact information, and
 * their licenses and certifications. It’s also used to store standardized (e.g. uppercased) data. It is used to track
 * changes in the data set, and it will only be recorded for original and modified records in the daily asc.gov batches.
 * The three attributes stateAbbrev, licenseNumber, and licenseType provide a unique index into the asc.gov data.
 * Unmodified raw strings are stored in rawData. Standardized, enhanced data are stored in the named fields.
 */
public class Licenses {
    private static final Logger LOG = LoggerFactory.getLogger(Licenses.class);
    private static boolean initialized = false;
    private static Session session;

    private static PreparedStatement psWriteVersion;
    private static PreparedStatement psReadAllIds;
    private static PreparedStatement psReadVersion;
    private static PreparedStatement psReadVersions;
    private static PreparedStatement psReadCurrentVersion;
    private static PreparedStatement psDeleteVersions;
    private static PreparedStatement psWriteLicenseIdAscKey;
    private static PreparedStatement psReadLicenseIdAscKey;
    private static PreparedStatement psDeleteLicenseIdAscKey;
    private static PreparedStatement psWriteLicenseUpdateIdDay;
    private static PreparedStatement psReadLicenseUpdateIdsDay;
    private static PreparedStatement psDeleteLicenseUpdateIdsDay;
    private static PreparedStatement psWriteLicenseIdState;
    private static PreparedStatement psReadLicenseIdsState;
    private static PreparedStatement psDeleteLicenseIdState;
    private static PreparedStatement psWriteLicenseIdNumber;
    private static PreparedStatement psReadLicenseIdsNumber;
    private static PreparedStatement psDeleteLicenseIdNumber;

    private static final String createTableLicenses = "CREATE TABLE IF NOT EXISTS licenses (\n" +
            "  id text,\n" +
            "  update_id text,\n" +
            "  json text,\n" +
            "  PRIMARY KEY ((id), update_id)\n" +
            ") WITH\n" +
            "  comment='License Version History' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableLicenseIdAscKey = "CREATE TABLE IF NOT EXISTS license_id_asc_key (\n" +
            "  asc_key text,\n" +
            "  id text,\n" +
            "  PRIMARY KEY (asc_key)\n" +
            ") WITH\n" +
            "  comment='Look up License by ASC Key' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableLicenseUpdateIdsDay = "CREATE TABLE IF NOT EXISTS license_update_ids_day (\n" +
            "  day int,\n" +
            "  id text,\n" +
            "  update_id text,\n" +
            "  PRIMARY KEY ((day), id, update_id)\n" +
            ") WITH\n" +
            "  comment='License Updates by Day' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableLicenseIdsState = "CREATE TABLE IF NOT EXISTS license_ids_state (\n" +
            "  state text,\n" +
            "  id text,\n" +
            "  PRIMARY KEY ((state), id)\n" +
            ") WITH\n" +
            "  comment='Look up Licenses by State Abbreviation' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableLicenseIdsLicenseNumber = "CREATE TABLE IF NOT EXISTS license_ids_license_number (\n" +
            "  license_number text,\n" +
            "  id text,\n" +
            "  PRIMARY KEY ((license_number), id)\n" +
            ") WITH\n" +
            "  comment='Look up Licenses by License Number' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient) {
        if (!initialized) {
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableLicenses);
                    session.execute(createTableLicenseIdAscKey);
                    session.execute(createTableLicenseUpdateIdsDay);
                    session.execute(createTableLicenseIdsState);
                    session.execute(createTableLicenseIdsLicenseNumber);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one):
            // regular object persistence
            psWriteVersion = session.prepare("INSERT INTO licenses (id, update_id, json) VALUES (?, ?, ?)");
            psReadAllIds = session.prepare("SELECT DISTINCT id FROM licenses LIMIT ?");
            psReadVersion = session.prepare("SELECT * FROM licenses WHERE id=? AND update_id=?");
            psReadVersions = session.prepare("SELECT * FROM licenses WHERE id=? ORDER BY update_id ASC");
            psReadCurrentVersion = session.prepare("SELECT * FROM licenses WHERE id=? ORDER BY update_id DESC LIMIT 1");
            psDeleteVersions = session.prepare("DELETE FROM licenses WHERE id=?");
            // ASC key index (stateAbbrev, licenseNumber, licenseType)
            psWriteLicenseIdAscKey = session.prepare("INSERT INTO license_id_asc_key (asc_key, id) VALUES (?, ?)");
            psReadLicenseIdAscKey = session.prepare("SELECT id FROM license_id_asc_key WHERE asc_key=?");
            psDeleteLicenseIdAscKey = session.prepare("DELETE FROM license_id_asc_key WHERE asc_key=?");
            // license updates by day (YYYYMMDD)
            psWriteLicenseUpdateIdDay = session.prepare("INSERT INTO license_update_ids_day (day, id, update_id) VALUES (?, ?, ?)");
            psReadLicenseUpdateIdsDay = session.prepare("SELECT id, update_id FROM license_update_ids_day WHERE day=? LIMIT ?");
            psDeleteLicenseUpdateIdsDay = session.prepare("DELETE FROM license_update_ids_day WHERE day=?");
            // state (abbreviation) index
            psWriteLicenseIdState = session.prepare("INSERT INTO license_ids_state (state, id) VALUES (?, ?)");
            psReadLicenseIdsState = session.prepare("SELECT id FROM license_ids_state WHERE state=? AND id > ? ORDER BY id ASC LIMIT ?");
            psDeleteLicenseIdState = session.prepare("DELETE FROM license_ids_state WHERE state=? AND id=?");
            // license number index (not necessarily unique)
            psWriteLicenseIdNumber = session.prepare("INSERT INTO license_ids_license_number (license_number, id) VALUES (?, ?)");
            psReadLicenseIdsNumber = session.prepare("SELECT id FROM license_ids_license_number WHERE license_number=?");
            psDeleteLicenseIdNumber = session.prepare("DELETE FROM license_ids_license_number WHERE license_number=? AND id=?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static License createLicense(License license) throws IOException {
        if (license.getId() == null) license.setId(TuidFactory.getId());
        license.setUpdateId(license.getId()); // updateId matches id on first version
        return createLicenseVersion(license);
    }

    public static License createLicenseVersion(License license) throws IOException {
        // id and updateId must be properly set before using this method
        if (license.getId() == null || license.getUpdateId() == null) return null;
        session.executeAsync(psWriteVersion.bind(license.getId(), license.getUpdateId(), JsonSerializer.getJson(license)));
        updateIndexes(license);
        return license;
    }

    public static License updateLicense(License license) throws IOException, MissingResourceException {
        if (license.getId() == null)
            throw new MissingResourceException("Missing ID", License.class.getSimpleName(), "id");
        license.setUpdateId(TuidFactory.getId());
        return createLicenseVersion(license);
    }

    public static void updateIndexes(License license) {
        if (license != null && license.getId() != null) {
            if (license.getAscKey() != null)
                session.executeAsync(psWriteLicenseIdAscKey.bind(license.getAscKey(), license.getId()));
            if (license.getUpdateId() != null)
                session.executeAsync(psWriteLicenseUpdateIdDay.bind((new Tuid(license.getUpdateId())).getYearMonthDay(), license.getId(), license.getUpdateId()));
            if (license.getStateAbbrev() != null)
                session.executeAsync(psWriteLicenseIdState.bind(license.getStateAbbrev(), license.getId()));
            if (license.getLicenseNumber() != null)
                session.executeAsync(psWriteLicenseIdNumber.bind(license.getLicenseNumber(), license.getId()));
        }
    }

    public static void deleteLicense(String id) throws IOException {
        License license = readLicense(id);
        if (license != null) {
            session.executeAsync(psDeleteVersions.bind(license.getId()));
            if (license.getAscKey() != null)
                session.executeAsync(psDeleteLicenseIdAscKey.bind(license.getAscKey()));
            if (license.getStateAbbrev() != null)
                session.executeAsync(psDeleteLicenseIdState.bind(license.getStateAbbrev(), license.getId()));
            if (license.getLicenseNumber() != null)
                session.executeAsync(psDeleteLicenseIdNumber.bind(license.getLicenseNumber(), license.getId()));
        }
    }

    public static void deleteLicenseUpdateIdsByDay(Integer day) {
        if (day != null) session.executeAsync(psDeleteLicenseUpdateIdsDay.bind(day));
    }

    public static License readLicense(String id) throws IOException {
        if (id == null) return null;
        Row row = session.execute(psReadCurrentVersion.bind(id)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), License.class);
    }

    public static License readLicenseVersion(String id, String updateId) throws IOException {
        if (id == null || updateId == null) return null;
        Row row = session.execute(psReadVersion.bind(id, updateId)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), License.class);
    }

    public static List<License> readLicenseVersions(String id) throws IOException {
        if (id == null) return null;
        ArrayList<License> versions = new ArrayList<>();
        ResultSet resultSet = session.execute(psReadVersions.bind(id));
        for (Row row : resultSet.all()) {
            versions.add(JsonSerializer.getObject(row.getString("json"), License.class));
        }
        return versions;
    }

    public static License readLicenseByAscKey(String ascKey) throws IOException {
        if (ascKey == null) return null;
        Row row = session.execute(psReadLicenseIdAscKey.bind(ascKey)).one();
        return row == null ? null : readLicense(row.getString("id"));
    }

    private static List<String> readIds(BoundStatement query, String idField) {
        ArrayList<String> ids = new ArrayList<>();
        ResultSet resultSet = session.execute(query);
        for (Row idRow : resultSet.all())
            ids.add(idRow.getString(idField));
        return ids;
    }

    public static SortedSet<License> readLicenses(List<String> ids, Comparator<License> order) throws IOException, ExecutionException, InterruptedException {
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = Lists.newArrayListWithExpectedSize(ids.size());
        for (String id : ids) if (id != null) futures.add(session.executeAsync(psReadCurrentVersion.bind(id)));
        // Process the results as they come in, deserializing JSON to pojos in sorted order
        ConcurrentSkipListSet<License> licenses = new ConcurrentSkipListSet<>(order);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null) licenses.add(JsonSerializer.getObject(row.getString("json"), License.class));
        }
        return licenses;
    }

    public static SortedSet<License> readLicenses(Integer limit) throws IOException, ExecutionException, InterruptedException {
        return readLicenses(readIds(psReadAllIds.bind(limit == null ? 10000 : limit), "id"), License.Chronological);
    }

    public static SortedSet<License> readLicensesByState(String stateAbbrev, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return readLicenses(readIds(psReadLicenseIdsState.bind(stateAbbrev, offset == null ? "0" : offset, limit == null ? 10000 : limit), "id"), License.Chronological);
    }

    public static SortedSet<License> readLicensesByLicenseNumber(String licenseNumber) throws IOException, ExecutionException, InterruptedException {
        return readLicenses(readIds(psReadLicenseIdsNumber.bind(licenseNumber), "id"), License.Chronological);
    }

    public static SortedSet<License> readLicenseUpdatesByDay(Integer day, Integer limit) throws IOException, ExecutionException, InterruptedException {
        if (day == null) return null;
        // Fetch ids for the specified day
        ResultSet resultSet = session.execute(psReadLicenseUpdateIdsDay.bind(day, limit == null ? 10000 : limit));
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = new ArrayList<>();
        for (Row row : resultSet.all()) futures.add(session.executeAsync(psReadVersion.bind(row.getString("id"), row.getString("update_id"))));
        // Process the results as they come in, deserializing JSON to pojos in sorted order
        ConcurrentSkipListSet<License> licenses = new ConcurrentSkipListSet<>(License.Chronological);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null) licenses.add(JsonSerializer.getObject(row.getString("json"), License.class));
        }
        return licenses;
    }
}
