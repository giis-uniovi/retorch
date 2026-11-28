/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The resampled execution; {@code slices.get(i)} describes the second {@code startSec + i}. Immutable. */
public final class Timeline {
    public final double startSec;
    public final List<Slice> slices;

    Timeline(double startSec, List<Slice> slices) {
        this.startSec = startSec;
        this.slices = Collections.unmodifiableList(new ArrayList<>(slices));
    }

    /** Highest simultaneous consumption of {@code resource} ("cpu" in cores, "mem" or "disk" in GB). */
    public double peak(String resource) {
        double peak = 0;
        for (Slice s : slices) {
            double sum = 0;
            for (Load l : s.loads) {
                sum += value(l, resource);
            }
            peak = Math.max(peak, sum);
        }
        return peak;
    }

    private static double value(Load l, String resource) {
        switch (resource) {
            case "cpu": return l.cpu;
            case "mem": return l.memGb;
            default: return l.diskGb;
        }
    }
}
