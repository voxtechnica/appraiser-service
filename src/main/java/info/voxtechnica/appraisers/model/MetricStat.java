package info.voxtechnica.appraisers.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * MetricStats are used for computing summary descriptive statistics for Metrics
 */
public class MetricStat implements Comparable<MetricStat> {
    @JsonIgnore
    private final DescriptiveStatistics durationStats = new DescriptiveStatistics();
    private String tag;
    private String firstId;
    private String lastId;

    public MetricStat() {
    }

    public MetricStat(String tag) {
        this.tag = tag;
    }

    public MetricStat(String tag, Map<String, Long> durations) {
        this.tag = tag;
        addDurations(durations);
    }

    public void addDuration(@NotNull String id, @NotNull Long duration) {
        if (firstId == null || firstId.compareTo(id) > 0) firstId = id;
        if (lastId == null || lastId.compareTo(id) < 0) lastId = id;
        durationStats.addValue((double) duration);
    }

    public void addDurations(@NotNull Map<String, Long> durations) {
        for (String id : durations.keySet()) {
            if (firstId == null || firstId.compareTo(id) > 0) firstId = id;
            if (lastId == null || lastId.compareTo(id) < 0) lastId = id;
            durationStats.addValue((double) durations.get(id));
        }
    }

    @JsonProperty
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @JsonProperty
    public String getFirstId() {
        return firstId;
    }

    @JsonProperty
    public String getFirstTimestamp() {
        return firstId == null ? null : new Tuid(firstId).getCreatedAt();
    }

    @JsonProperty
    public String getLastId() {
        return lastId;
    }

    @JsonProperty
    public String getLastTimestamp() {
        return lastId == null ? null : new Tuid(lastId).getCreatedAt();
    }

    @JsonProperty
    public Long getDeltaSeconds() {
        return (firstId == null || lastId == null) ? null : new Tuid(firstId).durationSeconds(new Tuid(lastId));
    }

    @JsonProperty
    public Long getCount() {
        return durationStats.getN();
    }

    @JsonProperty
    public Long getMin() {
        return Math.round(durationStats.getMin());
    }

    @JsonProperty
    public Long getMean() {
        return Math.round(durationStats.getMean());
    }

    @JsonProperty
    public Long getMedian() {
        return Math.round(durationStats.getPercentile(50));
    }

    @JsonProperty
    public Long getMax() {
        return Math.round(durationStats.getMax());
    }

    @JsonProperty
    public Long getStDev() {
        return Math.round(durationStats.getStandardDeviation());
    }

    @JsonProperty
    public Long getSum() {
        return Math.round(durationStats.getSum());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricStat that = (MetricStat) o;
        return Objects.equal(tag, that.tag) &&
                Objects.equal(firstId, that.firstId) &&
                Objects.equal(lastId, that.lastId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tag, firstId, lastId);
    }

    @Override
    public String toString() {
        return "MetricStat{" +
                "tag='" + tag + '\'' +
                ", firstId='" + firstId + '\'' +
                ", lastId='" + lastId + '\'' +
                '}';
    }

    @Override
    public int compareTo(MetricStat that) {
        return this.tag.compareTo(that.tag);
    }
}
