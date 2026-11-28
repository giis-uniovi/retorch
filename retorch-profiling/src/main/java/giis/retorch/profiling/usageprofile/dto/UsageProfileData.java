/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

import java.util.List;

/**
 * Everything the usage profile page needs, exactly as it is embedded in the HTML as JSON. The property names are read by
 * the scripts under {@code resources/usageprofile/js}; renaming a field breaks the page. Immutable.
 */
public final class UsageProfileData {
    public final String plan;
    /** Epoch ms when the prices were fetched. */
    public final long pricesFetchedAt;
    public final double durationSec;
    public final double peakCpu;
    public final double peakMemGb;
    public final double peakDiskGb;
    public final List<String> warnings;
    public final List<Alternative> alternatives;
    public final TimelineSeries timeline;
    /** The execution graph, or null when no execution plan is available. */
    public final ExecutionGraph graph;
    /** SUT and branch shown in the header (empty strings when unknown). */
    public final SutInfo sut;

    /** Without SUT information. */
    public UsageProfileData(String plan, long pricesFetchedAt, CostReport report, TimelineSeries timeline,
                         ExecutionGraph graph) {
        this(plan, pricesFetchedAt, report, timeline, graph, SutInfo.none());
    }

    public UsageProfileData(String plan, long pricesFetchedAt, CostReport report, TimelineSeries timeline,
                         ExecutionGraph graph, SutInfo sut) {
        this.plan = plan;
        this.pricesFetchedAt = pricesFetchedAt;
        this.durationSec = report.durationSec;
        this.peakCpu = report.peakCpu;
        this.peakMemGb = report.peakMemGb;
        this.peakDiskGb = report.peakDiskGb;
        this.warnings = report.warnings;
        this.alternatives = report.alternatives;
        this.timeline = timeline;
        this.graph = graph;
        this.sut = sut;
    }
}
