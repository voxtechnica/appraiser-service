package info.voxtechnica.appraisers.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * MetricCounts are used for computing quick counts and average durations for each tag.
 */
@Data
public class MetricCount implements Comparable<MetricCount> {
    private String tag;
    private Long count;
    private Long duration;

    public MetricCount() {
    }

    public MetricCount(@NotNull String tag, @NotNull Long count, @NotNull Long duration) {
        this.tag = tag;
        this.count = count;
        this.duration = duration;
    }

    @JsonProperty
    public Double getDurationAverage() {
        if (duration == null || count == null || count == 0) return 0.0;
        else return (double) duration / (double) count;
    }

    @Override
    public int compareTo(MetricCount that) {
        return this.tag.compareTo(that.tag);
    }
}
