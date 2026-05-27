package giis.retorch.profiling.profilegeneration;

/**
 * Tracks the provisioned-capacity gaps across a fixed number of slots as time advances. Replaces the previous
 * For each slot the tracker records three timestamps:
 * <ul>
 *   <li>{@code firstSeen} — when the slot was first allocated (immutable for the lifetime of the slot)</li>
 *   <li>{@code billingPeriodStart} — when the current billing period started</li>
 *   <li>{@code lastSeen} — the most recent tick at which the slot was allocated</li>
 * </ul>
 */
final class CapacityGapTracker {

    private static final class Gap {
        final int firstSeen;
        int billingPeriodStart;
        int lastSeen;

        Gap(int currentTime) {
            this.firstSeen = currentTime;
            this.billingPeriodStart = currentTime;
            this.lastSeen = currentTime;
        }
    }

    private final Gap[] gaps;
    private final double granularity;
    private final int billingTimePeriod;

    CapacityGapTracker(int slotCount, double granularity, int billingTimePeriod) {
        this.gaps = new Gap[slotCount];
        this.granularity = granularity;
        this.billingTimePeriod = billingTimePeriod;
    }

    /**
     * Used to allocate a certain number of slots at a {@code currentTime}.
     */
    void recordUsage(int currentTime, int slotsUsed) {
        int effective = Math.min(slotsUsed, gaps.length);
        for (int i = 0; i < effective; i++) {
            if (gaps[i] == null) {
                gaps[i] = new Gap(currentTime);
            } else {
                gaps[i].lastSeen = currentTime;
                if (gaps[i].billingPeriodStart - gaps[i].lastSeen >= billingTimePeriod) {
                    gaps[i].billingPeriodStart = currentTime;
                }
            }
        }
        for (int i = effective; i < gaps.length; i++) {
            Gap g = gaps[i];
            if (g != null && (currentTime - g.billingPeriodStart >= billingTimePeriod) && currentTime != g.lastSeen) {
                gaps[i] = null;
            }
        }
    }

    /**
     * Returns the provisioned quantity, calculated as the granularity multiplied by the number of active slots.
     */
    double provisionedQuantity() {
        int active = 0;
        for (Gap g : gaps) {
            if (g != null) active++;
        }
        return granularity * active;
    }
}
