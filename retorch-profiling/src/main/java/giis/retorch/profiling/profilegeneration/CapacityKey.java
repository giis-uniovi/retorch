package giis.retorch.profiling.profilegeneration;

import java.util.Objects;

import static giis.retorch.profiling.utils.CsvConstants.AGGREGATION_VALUE;

/**
 * Identifies a (tJobId, lifecycle, capacity) slot in the {@code ProfileGenerator} usage map. Replaces the previous
 * brittle {@code "tjobId-lifecycle-capacity"} string keys that had to be parsed with {@code split("-")}.
 */
final class CapacityKey implements Comparable<CapacityKey> {

    private final String tJobId;
    private final String lifecycle;
    private final String capacity;

    CapacityKey(String tJobId, String lifecycle, String capacity) {
        this.tJobId = tJobId;
        this.lifecycle = lifecycle;
        this.capacity = capacity;
    }

    /** Key for an aggregated (cross-TJob) capacity slot. */
    static CapacityKey aggregated(String lifecycle, String capacity) {
        return new CapacityKey(AGGREGATION_VALUE, lifecycle, capacity);
    }

    String getTJobId() {
        return tJobId;
    }

    String getLifecycle() {
        return lifecycle;
    }

    String getCapacity() {
        return capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CapacityKey)) return false;
        CapacityKey other = (CapacityKey) o;
        return Objects.equals(tJobId, other.tJobId)
                && Objects.equals(lifecycle, other.lifecycle)
                && Objects.equals(capacity, other.capacity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tJobId, lifecycle, capacity);
    }

    @Override
    public String toString() {
        return tJobId + "-" + lifecycle + "-" + capacity;
    }

    @Override
    public int compareTo(CapacityKey other) {
        int c = tJobId.compareTo(other.tJobId);
        if (c != 0) return c;
        c = lifecycle.compareTo(other.lifecycle);
        if (c != 0) return c;
        return capacity.compareTo(other.capacity);
    }
}
