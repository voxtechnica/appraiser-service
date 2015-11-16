package info.voxtechnica.appraisers.model;

import info.voxtechnica.appraisers.util.TuidFactory;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.net.URI;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * An ‘event’ is something that gets logged in the database, to record system and user activity. It can be useful for
 * understanding system activity and for debugging when things go badly. Events are never updated; they have no versions.
 * We just record what happened. The list of associated entity IDs should be kept short, unless it’s truly meaningful
 * (e.g. if someone gets a paginated list, don’t necessarily record every item in the list; maybe just the parent call).
 * Furthermore, the set of entity IDs should not include the userId since it's already included.
 */
@Data
public class Event implements Comparable<Event> {
    private String id;
    private String userId;
    private Set<String> entityIds;
    private URI resourceUri;
    private HttpMethod httpMethod;
    private LogLevel logLevel;
    private String message;
    private String stackTrace;

    public Event() {}

    // No entityIds
    public Event(String userId, URI resourceUri, HttpMethod httpMethod, LogLevel logLevel, String message, String stackTrace) {
        id = TuidFactory.getId();
        this.userId = userId;
        this.resourceUri = resourceUri;
        this.httpMethod = httpMethod;
        this.logLevel = logLevel;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    // One entityId
    public Event(String userId, String entityId, URI resourceUri, HttpMethod httpMethod, LogLevel logLevel, String message, String stackTrace) {
        id = TuidFactory.getId();
        this.userId = userId;
        addEntity(entityId);
        this.resourceUri = resourceUri;
        this.httpMethod = httpMethod;
        this.logLevel = logLevel;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    // Set of entityIds
    public Event(String userId, Set<String> entityIds, URI resourceUri, HttpMethod httpMethod, LogLevel logLevel, String message, String stackTrace) {
        id = TuidFactory.getId();
        this.userId = userId;
        this.entityIds = entityIds;
        this.resourceUri = resourceUri;
        this.httpMethod = httpMethod;
        this.logLevel = logLevel;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    public String getCreatedAt() {
        return id == null ? null : (new Tuid(id)).getCreatedAt();
    }
    
    public void addEntity(String entityId) {
        if (entityId != null) {
            if (entityIds == null) entityIds = new HashSet<>();
            entityIds.add(entityId);
        }
    }

    @Override
    public int compareTo(Event that) {
        return Chronological.compare(this, that);
    }

    public static Comparator<Event> Chronological = new Comparator<Event>() {
        @Override
        public int compare(Event one, Event two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            return ObjectUtils.compare(one.getId(), two.getId());
        }
    };

    public static Comparator<Event> ReverseChronological = new Comparator<Event>() {
        @Override
        public int compare(Event one, Event two) {
            return Chronological.compare(two, one);
        }
    };
    
    public enum HttpMethod {CONNECT, DELETE, GET, HEAD, MOVE, OPTIONS, POST, PRI, PROXY, PUT, TRACE}
    
    // Ordinal position of log levels is important! It's used for configurable logging levels.
    public enum LogLevel {TRACE, DEBUG, INFO, WARN, ERROR}
}
