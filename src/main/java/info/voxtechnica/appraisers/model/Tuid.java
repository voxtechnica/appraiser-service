package info.voxtechnica.appraisers.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import info.voxtechnica.appraisers.util.TuidFactory;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

/**
 * A ‘tuid’ is a time-based unique identifier with an embedded millisecond-resolution createdAt timestamp. It’s guaranteed
 * to be cluster-unique, and it sorts chronologically. It’s a 64-bit long integer, expressed as a base-36 string. 
 * Example: 2TNSOS9L376Y, or 371688814796603434 decimal, with a createdAt timestamp of 2014-12-06T07:38:25.468-08:00. 
 * The left 46 bits are the embedded milliseconds since epoch, and the right 18 bits are ‘entropy’ (a 10-bit counter 
 * plus an 8-bit node id), designed to ensure uniqueness.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tuid implements Comparable<Tuid> {

    private final String id;

    @JsonIgnore
    private final DateTime createdAt;

    public Tuid() {
        id = TuidFactory.getId();
        createdAt = new DateTime(Long.parseLong(id, 36) >> 18);
    }

    @JsonCreator
    public Tuid(@JsonProperty("id") String id) throws NumberFormatException {
        this.id = id.trim().toUpperCase();
        createdAt = new DateTime(Long.parseLong(id, 36) >> 18);
    }

    @JsonProperty
    public String getId() {
        return id;
    }

    @JsonProperty
    public String getCreatedAt() {
        return ISODateTimeFormat.dateTime().print(createdAt);
    }

    /**
     * Extract the embedded timestamp from a TUID.
     *
     * @return milliseconds since epoch
     */
    @JsonProperty
    public long getMillis() {
        return Long.parseLong(id, 36) >> 18;
    }

    /**
     * Extract the year and month of the embedded timestamp in a TUID into a sortable integer.
     *
     * @return year-month integer of the form 'yyyymm'
     */
    @JsonProperty
    public int getYearMonth() {
        return createdAt.getYear() * 100 + createdAt.getMonthOfYear();
    }

    /**
     * Extract the year, month, and day of the embedded timestamp in a TUID into a sortable integer.
     *
     * @return year-month-day integer of the form 'yyyymmdd'
     */
    @JsonProperty
    public int getYearMonthDay() {
        return (createdAt.getYear() * 10000) + (createdAt.getMonthOfYear() * 100) + createdAt.getDayOfMonth();
    }

    /**
     * Extract the year and week number of the embedded timestamp in a TUID into a sortable integer.
     * The first week of the year has at least four days in the new year, and weeks start on a Monday.
     * Week numbers range from 1 to 52 or 53.
     *
     * @return year-week integer of the form 'yyyyww'
     */
    @JsonProperty
    public int getYearWeek() {
        return createdAt.getWeekyear() * 100 + createdAt.getWeekOfWeekyear();
    }

    /**
     * Extract the year and ordinal day of the embedded timestamp in a TUID into a sortable integer.
     *
     * @return year-day integer in the form of 'yyyyddd'
     */
    @JsonProperty
    public int getYearDay() {
        return createdAt.getYear() * 1000 + createdAt.getDayOfYear();
    }

    /**
     * Calculate duration in seconds between two TUIDs.
     * If thisID is greater than thatID, then the duration will be negative.
     *
     * @param thatTuid
     * @return duration in seconds
     */
    public long durationSeconds(Tuid thatTuid) {
        long start = this.getMillis();
        long stop = thatTuid.getMillis();
        return Math.round((stop - start) / 1000d);
    }

    @Override
    public String toString() {
        return "Tuid{" +
                "id='" + id + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuid that = (Tuid) o;
        return id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public int compareTo(Tuid that) {
        return this.id.compareTo(that.id);
    }
}