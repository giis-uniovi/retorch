/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.measurement.Timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** What the {@link AlternativeCost} strategies need: the execution, the prices, its peaks and where to report warnings. */
final class CostContext {
    final Timeline timeline;
    final PriceCatalog catalog;
    /** Lower case ids of the TJobs that use a browser. */
    final Set<String> browserTjobs;
    final double peakCpu;
    final double peakMemGb;
    final double peakDiskGb;
    /** The warnings of the report: those of the catalog plus the ones the strategies add. */
    final List<String> warnings;

    CostContext(Timeline timeline, PriceCatalog catalog, Set<String> browserTjobs) {
        this.timeline = timeline;
        this.catalog = catalog;
        this.browserTjobs = browserTjobs;
        this.peakCpu = timeline.peak("cpu");
        this.peakMemGb = timeline.peak("mem");
        this.peakDiskGb = timeline.peak("disk");
        this.warnings = new ArrayList<>(catalog.warnings);
    }

    /** Two decimals at most, without trailing zeros: 2.0 -> "2", 1.456 -> "1.46". */
    static String fmt(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
