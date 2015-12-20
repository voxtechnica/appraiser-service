package info.voxtechnica.appraisers.model;

import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Comparator;

/**
 * Imports track ASC license import operations (when they were run) and how many licenses were created, updated, or ignored.
 * The integer 'day' is in the BASIC_ISO_DATE form of 'YYYYMMDD'. It can be historical, rather than the date of the import.
 */
@Data
public class Import implements Comparable<Import> {
    private String id;
    private Integer day;
    private Long created = 0L;
    private Long updated = 0L;
    private Long ignored = 0L;

    public Import() {
    }

    public Import(String id, Integer day, Long created, Long updated, Long ignored) {
        this.id = id;
        this.day = day == null ? (new Tuid(id)).getYearMonthDay() : day;
        this.created = created == null ? 0L : created;
        this.updated = updated == null ? 0L : updated;
        this.ignored = ignored == null ? 0L : ignored;
    }

    public Long getTotal() {
        return created + updated + ignored;
    }

    public String getCreatedAt() {
        return id == null ? null : (new Tuid(id)).getCreatedAt();
    }

    @Override
    public int compareTo(Import that) {
        return ChronologicalDay.compare(this, that);
    }

    public static Comparator<Import> Chronological = new Comparator<Import>() {
        @Override
        public int compare(Import one, Import two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            return ObjectUtils.compare(one.getId(), two.getId());
        }
    };

    public static Comparator<Import> ReverseChronological = new Comparator<Import>() {
        @Override
        public int compare(Import one, Import two) {
            return Chronological.compare(two, one);
        }
    };

    public static Comparator<Import> ChronologicalDay = new Comparator<Import>() {
        @Override
        public int compare(Import one, Import two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            int c1 = ObjectUtils.compare(one.getDay(), two.getDay());
            if (c1 != 0) return c1;
            return ObjectUtils.compare(one.getId(), two.getId());
        }
    };

    public static Comparator<Import> ReverseChronologicalDay = new Comparator<Import>() {
        @Override
        public int compare(Import one, Import two) {
            return ChronologicalDay.compare(two, one);
        }
    };

}
