package info.voxtechnica.appraisers.config;

import info.voxtechnica.appraisers.model.Event;
import lombok.Data;

/**
 * Configuration settings for our Event service
 */
@Data
public class EventConfiguration {
    private Event.LogLevel logLevel = Event.LogLevel.INFO;
    private Integer timeToLive = 7776000; // default 90 days, in seconds
}
