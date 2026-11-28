/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto.plan;

import java.util.List;

/** The TJobs of the execution (nodes) with their dependencies; embedded in the usage profile JSON as {@code graph}. */
public final class ExecutionGraphDTO {
    public final List<GraphNodeDTO> nodes;

    public ExecutionGraphDTO(List<GraphNodeDTO> nodes) {
        this.nodes = nodes;
    }
}
