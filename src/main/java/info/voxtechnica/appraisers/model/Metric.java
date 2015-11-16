package info.voxtechnica.appraisers.model;

import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import javax.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Metric is a performance metric used for calculating count and duration (millisecond) statistics.
 */
@Data
public class Metric implements Comparable<Metric> {
    private String id;
    private Set<String> tags = new TreeSet<>();
    private Set<String> entityIds = new TreeSet<>();
    private Long duration = 0L; // milliseconds

    public Metric() {
    }

    public Metric(@NotNull String tag, @NotNull Long duration) {
        tags.add(tag);
        this.duration = duration;
    }

    public Metric(@NotNull String tag, @NotNull String entityId, @NotNull Long duration) {
        tags.add(tag);
        entityIds.add(entityId);
        this.duration = duration;
    }

    public Metric(@NotNull Set<String> tags, @NotNull Set<String> entityIds, @NotNull Long duration) {
        this.tags = tags;
        this.entityIds = entityIds;
        this.duration = duration;
    }

    public void addTag(@NotNull String tag) {
        if (tags == null) tags = new TreeSet<>();
        tags.add(tag);
    }

    public void addEntityId(@NotNull String entityId) {
        if (entityIds == null) entityIds = new TreeSet<>();
        entityIds.add(entityId);
    }

    public String getCreatedAt() {
        return id == null ? null : (new Tuid(id)).getCreatedAt();
    }

    @Override
    public int compareTo(Metric that) {
        return Chronological.compare(this, that);
    }

    public static Comparator<Metric> Chronological = new Comparator<Metric>() {
        @Override
        public int compare(Metric left, Metric right) {
            if (left == null && right == null) return 0;
            int c0 = left == null ? -1 : (right == null ? 1 : 0);
            if (c0 != 0) return c0;
            return ObjectUtils.compare(left.getId(), right.getId());
        }
    };

    public static Comparator<Metric> ReverseChronological = new Comparator<Metric>() {
        @Override
        public int compare(Metric left, Metric right) {
            return Chronological.compare(right, left);
        }
    };

}
