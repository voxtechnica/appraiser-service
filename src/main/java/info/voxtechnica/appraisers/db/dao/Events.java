package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.*;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.config.EventConfiguration;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Tuid;
import info.voxtechnica.appraisers.util.JsonSerializer;
import info.voxtechnica.appraisers.util.TuidFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

/**
 * An ‘event’ is something that gets logged in the database, to record system and user activity. It can be useful for
 * understanding system activity and for debugging when things go badly. Events are never updated; they have no versions.
 * We just record what happened. The list of associated entity IDs should be kept short, unless it’s truly meaningful
 * (e.g. if someone gets a paginated list, don’t necessarily record every item in the list; maybe just the parent call).
 * Furthermore, the set of entity IDs should not include the userId or organizationId, since they're already included.
 */
public class Events {
    private static final Logger LOG = LoggerFactory.getLogger(Events.class);
    private static boolean initialized = false;
    private static Session session;
    private static Integer ttl; // default Time-to-Live (TTL) is 90 days (7776000 seconds)
    private static EventConfiguration configuration;
    private static PreparedStatement psWriteEvent;
    private static PreparedStatement psReadEvent;
    private static PreparedStatement psWriteIdsEntity;
    private static PreparedStatement psReadIdsEntity;
    private static PreparedStatement psWriteIdsDay;
    private static PreparedStatement psReadIdsDay;
    private static PreparedStatement psWriteCountsLevelDay;
    private static PreparedStatement psReadCountsLevelDay;
    private static PreparedStatement psWriteIdsLevelDay;
    private static PreparedStatement psReadIdsLevelDay;
    private static PreparedStatement psWriteCountsUserDay;
    private static PreparedStatement psReadCountsUserDay;
    private static PreparedStatement psWriteIdsUserDay;
    private static PreparedStatement psReadIdsUserDay;

    private static final String createTableEvents = "CREATE TABLE IF NOT EXISTS events (\n" +
            "  id text,\n" +
            "  json text,\n" +
            "  PRIMARY KEY (id)\n" +
            ") WITH\n" +
            "  comment='Events' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableEventIdsEntity = "CREATE TABLE IF NOT EXISTS event_ids_entity (\n" +
            "  entity_id text,\n" +
            "  event_id text,\n" +
            "  PRIMARY KEY ((entity_id), event_id)\n" +
            ") WITH\n" +
            "  comment='Events by Entity ID' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableEventIdsDay = "CREATE TABLE IF NOT EXISTS event_ids_day (\n" +
            "  day int,\n" +
            "  event_id text,\n" +
            "  PRIMARY KEY ((day), event_id)\n" +
            ") WITH\n" +
            "  comment='Events by Integer Day' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableEventIdsLevelDay = "CREATE TABLE IF NOT EXISTS event_ids_level_day (\n" +
            "  log_level text,\n" +
            "  day int,\n" +
            "  event_id text,\n" +
            "  PRIMARY KEY ((log_level, day), event_id)\n" +
            ") WITH\n" +
            "  comment='Events by Log Level and Integer Day' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableEventCountsLevelDay = "CREATE TABLE IF NOT EXISTS event_counts_level_day (\n" +
            "  log_level text,\n" +
            "  day int,\n" +
            "  event_count counter,\n" +
            "  PRIMARY KEY ((log_level), day)\n" +
            ") WITH\n" +
            "  comment='Daily Event Counts by Log Level' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableEventIdsUserDay = "CREATE TABLE IF NOT EXISTS event_ids_user_day (\n" +
            "  user_id text,\n" +
            "  day int,\n" +
            "  event_id text,\n" +
            "  PRIMARY KEY ((user_id, day), event_id)\n" +
            ") WITH\n" +
            "  comment='Events by User and Integer Day' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableEventCountsUserDay = "CREATE TABLE IF NOT EXISTS event_counts_user_day (\n" +
            "  user_id text,\n" +
            "  day int,\n" +
            "  event_count counter,\n" +
            "  PRIMARY KEY ((user_id), day)\n" +
            ") WITH\n" +
            "  comment='Daily Event Counts by User' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient, EventConfiguration config) {
        if (!initialized) {
            configuration = config;
            ttl = configuration.getTimeToLive();
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableEvents);
                    session.execute(createTableEventIdsEntity);
                    session.execute(createTableEventIdsDay);
                    session.execute(createTableEventIdsLevelDay);
                    session.execute(createTableEventCountsLevelDay);
                    session.execute(createTableEventIdsUserDay);
                    session.execute(createTableEventCountsUserDay);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one):
            // regular object persistence
            psWriteEvent = session.prepare("INSERT INTO events (id, json) VALUES (?, ?) USING TTL ?");
            psReadEvent = session.prepare("SELECT json FROM events WHERE id=?");
            // entity
            psWriteIdsEntity = session.prepare("INSERT INTO event_ids_entity (entity_id, event_id) VALUES (?, ?) USING TTL ?");
            psReadIdsEntity = session.prepare("SELECT event_id FROM event_ids_entity WHERE entity_id=? AND event_id > ? ORDER BY event_id ASC LIMIT ?");
            // integer day
            psWriteIdsDay = session.prepare("INSERT INTO event_ids_day (day, event_id) VALUES (?, ?) USING TTL ?");
            psReadIdsDay = session.prepare("SELECT event_id FROM event_ids_day WHERE day=? AND event_id > ? ORDER BY event_id ASC LIMIT ?");
            // log level, integer day
            psWriteCountsLevelDay = session.prepare("UPDATE event_counts_level_day SET event_count = event_count + 1 WHERE log_level=? AND day=?");
            psReadCountsLevelDay = session.prepare("SELECT day, event_count FROM event_counts_level_day WHERE log_level=? AND day > ? ORDER BY day ASC LIMIT ?");
            psWriteIdsLevelDay = session.prepare("INSERT INTO event_ids_level_day (log_level, day, event_id) VALUES (?, ?, ?) USING TTL ?");
            psReadIdsLevelDay = session.prepare("SELECT event_id FROM event_ids_level_day WHERE log_level=? AND day=? AND event_id > ? ORDER BY event_id ASC LIMIT ?");
            // user, integer day
            psWriteCountsUserDay = session.prepare("UPDATE event_counts_user_day SET event_count = event_count + 1 WHERE user_id=? AND day=?");
            psReadCountsUserDay = session.prepare("SELECT day, event_count FROM event_counts_user_day WHERE user_id=? AND day > ? ORDER BY day ASC LIMIT ?");
            psWriteIdsUserDay = session.prepare("INSERT INTO event_ids_user_day (user_id, day, event_id) VALUES (?, ?, ?) USING TTL ?");
            psReadIdsUserDay = session.prepare("SELECT event_id FROM event_ids_user_day WHERE user_id=? AND day=? AND event_id > ? ORDER BY event_id ASC LIMIT ?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Event createEvent(Event event) {
        // Skip logging if less than the configured system logLevel
        if (event.getLogLevel() != null && event.getLogLevel().ordinal() < configuration.getLogLevel().ordinal())
            return event;
        // Otherwise save the event in the database
        try {
            if (event.getId() == null) event.setId(TuidFactory.getId());
            Integer day = (new Tuid(event.getId())).getYearMonthDay();
            String json = JsonSerializer.getJson(event);
            session.executeAsync(psWriteEvent.bind(event.getId(), json, ttl));
            if (event.getEntityIds() != null)
                for (String id : event.getEntityIds())
                    session.executeAsync(psWriteIdsEntity.bind(id, event.getId(), ttl));
            session.executeAsync(psWriteIdsDay.bind(day, event.getId(), ttl));
            if (event.getLogLevel() != null) {
                session.executeAsync(psWriteIdsLevelDay.bind(event.getLogLevel().name(), day, event.getId(), ttl));
                session.executeAsync(psWriteCountsLevelDay.bind(event.getLogLevel().name(), day));
            }
            if (event.getUserId() != null) {
                session.executeAsync(psWriteIdsUserDay.bind(event.getUserId(), day, event.getId(), ttl));
                session.executeAsync(psWriteCountsUserDay.bind(event.getUserId(), day));
            }
            return event;
        } catch (Exception e) {
            // Backup logging to the console or file system
            LOG.error("createEvent error: " + e.getCause().getMessage() + "\n" + event.toString());
            return null;
        }
    }

    public static Event readEvent(String id) throws IOException {
        if (id == null) return null;
        Row row = session.execute(psReadEvent.bind(id)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), Event.class);
    }

    public static SortedSet<Event> readEvents(List<String> ids, Comparator<Event> order) throws IOException, ExecutionException, InterruptedException {
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = Lists.newArrayListWithExpectedSize(ids.size());
        for (String id : ids) if (id != null) futures.add(session.executeAsync(psReadEvent.bind(id)));
        // Process the results as they come in, deserializing JSON to pojos in natural sorted order
        ConcurrentSkipListSet<Event> events = new ConcurrentSkipListSet<>(order);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null) events.add(JsonSerializer.getObject(row.getString("json"), Event.class));
        }
        return events;
    }

    private static List<String> readEventIds(BoundStatement query) {
        ArrayList<String> ids = new ArrayList<>();
        ResultSet resultSet = session.execute(query);
        for (Row idRow : resultSet.all()) ids.add(idRow.getString("event_id"));
        return ids;
    }

    private static Map<Integer, Long> readEventCounts(PreparedStatement query, String queryId, Integer limit, Integer offset) {
        TreeMap<Integer, Long> map = new TreeMap<>();
        if (queryId != null) {
            ResultSet resultSet = session.execute(query.bind(queryId, offset == null ? 0 : offset, limit == null ? 100000 : limit));
            for (Row row : resultSet.all()) map.put(row.getInt("day"), row.getLong("event_count"));
        }
        return map;
    }

    public static SortedSet<Event> readEventsByEntity(String entityId, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return entityId == null ? null : readEvents(readEventIds(psReadIdsEntity.bind(entityId, offset == null ? "0" : offset, limit == null ? 100000 : limit)), Event.Chronological);
    }

    public static SortedSet<Event> readEventsByDay(Integer day, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return day == null ? null : readEvents(readEventIds(psReadIdsDay.bind(day, offset == null ? "0" : offset, limit == null ? 100000 : limit)), Event.Chronological);
    }

    public static Map<Integer, Long> readEventCountsByLevelDay(Event.LogLevel logLevel, Integer limit, Integer offset) {
        return logLevel == null ? null : readEventCounts(psReadCountsLevelDay, logLevel.name(), limit, offset);
    }

    public static SortedSet<Event> readEventsByLevelDay(Event.LogLevel logLevel, Integer day, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return logLevel == null || day == null ? null : readEvents(readEventIds(psReadIdsLevelDay.bind(logLevel.name(), day, offset == null ? "0" : offset, limit == null ? 100000 : limit)), Event.Chronological);
    }

    public static Map<Integer, Long> readEventCountsByUserDay(String userId, Integer limit, Integer offset) {
        return userId == null ? null : readEventCounts(psReadCountsUserDay, userId, limit, offset);
    }

    public static SortedSet<Event> readEventsByUserDay(String userId, Integer day, Integer limit, String offset) throws IOException, ExecutionException, InterruptedException {
        return userId == null || day == null ? null : readEvents(readEventIds(psReadIdsUserDay.bind(userId, day, offset == null ? "0" : offset, limit == null ? 100000 : limit)), Event.Chronological);
    }

    public static SortedSet<Event> readRecentEvents(Integer seconds) throws IOException, ExecutionException, InterruptedException {
        String offset = TuidFactory.getFirstId((long) -seconds * 1000L);
        Integer day = (new Tuid(offset)).getYearMonthDay();
        Integer today = (new Tuid()).getYearMonthDay();
        SortedSet<Event> events = readEventsByDay(day, null, offset);
        // This accommodates spanning midnight (two days), but not more. Want more? Use readEventsByDay.
        if (!day.equals(today)) events.addAll(readEventsByDay(today, null, null));
        return events;
    }

    public static Event info(String userId, String entityId, String message) {
        Event event = new Event(userId, entityId, null, null, Event.LogLevel.INFO, message, null);
        return createEvent(event);
    }

    public static Event info(String userId, Set<String> entityIds, String message) {
        Event event = new Event(userId, entityIds, null, null, Event.LogLevel.INFO, message, null);
        return createEvent(event);
    }

    public static Event info(String userId, URI resourceUri, Event.HttpMethod httpMethod, String message) {
        Event event = new Event(userId, resourceUri, httpMethod, Event.LogLevel.INFO, message, null);
        return createEvent(event);
    }

    public static Event info(String userId, String entityId, URI resourceUri, Event.HttpMethod httpMethod, String message) {
        Event event = new Event(userId, entityId, resourceUri, httpMethod, Event.LogLevel.INFO, message, null);
        return createEvent(event);
    }

    public static Event info(String userId, Set<String> entityIds, URI resourceUri, Event.HttpMethod httpMethod, String message) {
        Event event = new Event(userId, entityIds, resourceUri, httpMethod, Event.LogLevel.INFO, message, null);
        return createEvent(event);
    }

    public static Event error(String message, String stackTrace) {
        Event event = new Event();
        event.setLogLevel(Event.LogLevel.ERROR);
        event.setMessage(message);
        event.setStackTrace(stackTrace);
        return createEvent(event);
    }

    public static Event error(String userId, String entityId, String message, String stackTrace) {
        Event event = new Event(userId, entityId, null, null, Event.LogLevel.ERROR, message, stackTrace);
        return createEvent(event);
    }

    public static Event error(String userId, Set<String> entityIds, String message, String stackTrace) {
        Event event = new Event(userId, entityIds, null, null, Event.LogLevel.ERROR, message, stackTrace);
        return createEvent(event);
    }

    public static Event error(String userId, URI resourceUri, Event.HttpMethod httpMethod, String message, String stackTrace) {
        Event event = new Event(userId, resourceUri, httpMethod, Event.LogLevel.ERROR, message, stackTrace);
        return createEvent(event);
    }

    public static Event error(String userId, String entityId, URI resourceUri, Event.HttpMethod httpMethod, String message, String stackTrace) {
        Event event = new Event(userId, entityId, resourceUri, httpMethod, Event.LogLevel.ERROR, message, stackTrace);
        return createEvent(event);
    }

    public static Event error(String userId, Set<String> entityIds, URI resourceUri, Event.HttpMethod httpMethod, String message, String stackTrace) {
        Event event = new Event(userId, entityIds, resourceUri, httpMethod, Event.LogLevel.ERROR, message, stackTrace);
        return createEvent(event);
    }

}
