/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.measurement.TJobMetrics;
import giis.retorch.profiling.usageprofile.ExecutionPlanReader.PlanNode;
import giis.retorch.profiling.usageprofile.dto.ContainerUsage;
import giis.retorch.profiling.usageprofile.dto.ExecutionGraph;
import giis.retorch.profiling.usageprofile.dto.GraphNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Merges the plan (which TJobs exist and how they depend on each other) with their measurements. */
public final class ExecutionGraphBuilder {

    private ExecutionGraphBuilder() {
    }

    /**
     * @param plan    TJobs of the plan; it is the source of truth for the nodes
     * @param metrics measurements by lower case TJob id; TJobs missing here keep {@code null} values
     */
    public static ExecutionGraph build(List<PlanNode> plan, Map<String, TJobMetrics> metrics) {
        return build(plan, metrics, Collections.<String, List<ContainerUsage>>emptyMap());
    }

    /**
     * @param containers usage per container by lower case TJob id; TJobs missing here get an empty list
     */
    public static ExecutionGraph build(List<PlanNode> plan, Map<String, TJobMetrics> metrics,
                                       Map<String, List<ContainerUsage>> containers) {
        List<GraphNode> nodes = new ArrayList<>();
        for (PlanNode p : plan) {
            String key = p.id.toLowerCase(Locale.ROOT);
            TJobMetrics m = metrics.get(key);
            Double[] measured = m == null ? new Double[6] : m.toArray();
            nodes.add(new GraphNode(p.id, p.stage, p.resources, p.tests, p.predecessors, measured, containers.get(key)));
        }
        return new ExecutionGraph(nodes);
    }
}
