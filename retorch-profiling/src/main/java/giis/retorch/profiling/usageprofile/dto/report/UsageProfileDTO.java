/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto.report;

import giis.retorch.profiling.usageprofile.dto.cost.AlternativeDTO;
import giis.retorch.profiling.usageprofile.dto.cost.CostReportDTO;
import giis.retorch.profiling.usageprofile.dto.measurement.TimelineSeriesDTO;
import giis.retorch.profiling.usageprofile.dto.plan.ExecutionGraphDTO;
import java.util.List;

/**
 * Everything the usage profile page needs, exactly as it is embedded in the HTML as JSON. The property names are read by
 * the scripts under {@code resources/usageprofile/js}; renaming a field breaks the page. Immutable.
 */
public final class UsageProfileDTO {
    public final String plan;
    /** Epoch ms when the prices were fetched. */
    public final long pricesFetchedAt;
    public final double durationSec;
    public final double peakCpu;
    public final double peakMemGb;
    public final double peakDiskGb;
    public final List<String> warnings;
    public final List<AlternativeDTO> alternatives;
    public final TimelineSeriesDTO timeline;
    /** The execution graph, or null when no execution plan is available. */
    public final ExecutionGraphDTO graph;
    /** SUT and branch shown in the header (empty strings when unknown). */
    public final SutInfoDTO sut;
    /** The vocabulary of phases, kinds and objects, so the page does not repeat it. */
    public final MetaDTO meta;

    /** Without SUT information. */
    public UsageProfileDTO(String plan, long pricesFetchedAt, CostReportDTO report, TimelineSeriesDTO timeline,
                         ExecutionGraphDTO graph) {
        this(plan, pricesFetchedAt, report, timeline, graph, SutInfoDTO.none());
    }

    public UsageProfileDTO(String plan, long pricesFetchedAt, CostReportDTO report, TimelineSeriesDTO timeline,
                         ExecutionGraphDTO graph, SutInfoDTO sut) {
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
        this.meta = MetaDTO.standard();
    }
}
