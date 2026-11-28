/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import giis.retorch.profiling.usageprofile.dto.ContainerUsageDTO;
import giis.retorch.profiling.usageprofile.dto.Phase;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Entry point to the usage file exported by the orchestration scripts ({@code resourcemonitor.sh export}). It delegates
 * to {@link UsageCsvReader} (parsing and validation), {@link TimelineBuilder} (resampling at one second and storage
 * rule) and {@link TJobMetricsCalculator} (TJob metrics and container usage); the results are the immutable
 * {@link Timeline}, {@link Slice}, {@link Load} and {@link TJobMetrics}.
 */
public final class ExecutionMeasurements {

    public static final String COI_SETUP = Phase.COI_SETUP.label();
    public static final String COI_TEARDOWN = Phase.COI_TEARDOWN.label();
    /** TJob name the COI lifecycle phases are exported under. */
    public static final String COI_TJOB = UsageCsvReader.COI_TJOB;

    private final UsageData data;

    private ExecutionMeasurements(UsageData data) {
        this.data = data;
    }

    /** Reads and validates the usage file; see {@link UsageCsvReader} for the columns. */
    public static ExecutionMeasurements read(Path usageProfileCsv) throws IOException {
        return new ExecutionMeasurements(UsageCsvReader.read(usageProfileCsv));
    }

    /** Resamples the execution at one second; each container keeps its last sample until a newer one arrives. */
    public Timeline toTimeline() {
        return TimelineBuilder.build(data);
    }

    /** Metrics of every TJob by lower case TJob name, see {@link TJobMetricsCalculator}. */
    public Map<String, TJobMetrics> tjobMetrics(Timeline timeline) {
        return TJobMetricsCalculator.metrics(data, timeline);
    }

    /** Usage of every container of every TJob that has container rows and samples, by lower case TJob name. */
    public Map<String, List<ContainerUsageDTO>> containerUsage() {
        return TJobMetricsCalculator.containerUsage(data);
    }
}
