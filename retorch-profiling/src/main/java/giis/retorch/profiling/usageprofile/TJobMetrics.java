/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

/** Duration of the phases (seconds) and peak usage of one TJob; the peaks are null if the TJob has no samples. Immutable. */
public final class TJobMetrics {
    /** Seconds from the start of the timeline to the start of the TJob set-up. */
    public final double startSec;
    public final double setupSec;
    public final double execSec;
    public final double teardownSec;
    public final Double peakCpu;
    public final Double peakMemGb;

    TJobMetrics(double[] bounds, double timelineStart, Double peakCpu, Double peakMemGb) {
        this.startSec = bounds[0] - timelineStart;
        this.setupSec = bounds[1] - bounds[0];
        this.execSec = bounds[2] - bounds[1];
        this.teardownSec = bounds[3] - bounds[2];
        this.peakCpu = peakCpu;
        this.peakMemGb = peakMemGb;
    }

    /** Values in the order start, setup, exec, teardown, peak CPU, peak memory. */
    public Double[] toArray() {
        return new Double[]{startSec, setupSec, execSec, teardownSec, peakCpu, peakMemGb};
    }
}
