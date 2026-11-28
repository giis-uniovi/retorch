/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The total CPU, memory and disk over time, as parallel lists (one entry per point). Immutable. */
public final class TimelineSeriesDTO {
    /** Seconds since the start of the execution. */
    public final List<Integer> t;
    public final List<Double> cpu;
    public final List<Double> memGb;
    public final List<Double> diskGb;

    public TimelineSeriesDTO(List<Integer> t, List<Double> cpu, List<Double> memGb, List<Double> diskGb) {
        this.t = Collections.unmodifiableList(new ArrayList<>(t));
        this.cpu = Collections.unmodifiableList(new ArrayList<>(cpu));
        this.memGb = Collections.unmodifiableList(new ArrayList<>(memGb));
        this.diskGb = Collections.unmodifiableList(new ArrayList<>(diskGb));
    }
}
