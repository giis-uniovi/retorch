/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The measured peaks of an execution and the cost of every alternative, cheapest first. Immutable. */
public final class CostReport {
    public final double peakCpu;
    public final double peakMemGb;
    public final double peakDiskGb;
    public final double durationSec;
    /** Sorted by {@link Alternative#total}, ascending. */
    public final List<Alternative> alternatives;
    public final List<String> warnings;

    public CostReport(double peakCpu, double peakMemGb, double peakDiskGb, double durationSec,
                      List<Alternative> alternatives, List<String> warnings) {
        this.peakCpu = peakCpu;
        this.peakMemGb = peakMemGb;
        this.peakDiskGb = peakDiskGb;
        this.durationSec = durationSec;
        this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }
}
