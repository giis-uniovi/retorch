/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.plan;

import giis.retorch.profiling.usageprofile.dto.plan.ExecutionGraphDTO;
import giis.retorch.profiling.usageprofile.dto.plan.GraphNodeDTO;
import giis.retorch.profiling.usageprofile.measurement.ExecutionMeasurements;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static giis.retorch.profiling.usageprofile.TestFixtures.*;

public class ExecutionPlanTests {

    @Test
    public void testExecutionPlanIsRead() throws IOException {
        java.util.List<ExecutionPlanReader.PlanNode> plan = ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json"));
        assertEquals(3, plan.size());
        assertEquals("tjobb", plan.get(1).id);
        assertEquals(1, plan.get(1).stage);
        assertEquals(Arrays.asList("executor", "browser"), plan.get(1).resources);
        assertEquals(Arrays.asList("CartTest#addItem"), plan.get(1).tests);
        assertEquals(Arrays.asList("tjoba"), plan.get(1).predecessors);
        assertTrue(plan.get(2).tests.isEmpty());
    }

    @Test
    public void testGraphMergesPlanAndMeasurementsAndKeepsNullsForUnmeasuredTJobs() throws IOException {
        ExecutionMeasurements m = ExecutionMeasurements.read(Paths.get(DATA + "usage.csv"));
        ExecutionGraphDTO graph = ExecutionGraphBuilder.build(
                ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json")), m.tjobMetrics(m.toTimeline()));
        assertEquals(3, graph.nodes.size());
        GraphNodeDTO b = graph.nodes.get(1);
        assertEquals("tjobb", b.id);
        assertEquals(20.0, b.execSec, 1e-9);
        assertEquals(Arrays.asList("tjoba"), b.predecessors);
        GraphNodeDTO gate = graph.nodes.get(2);
        assertEquals(null, gate.startSec);
        assertEquals(null, gate.setupSec);
        assertEquals(null, gate.peakCpu);
        assertEquals(null, gate.peakMemGb);
    }

    @Test
    public void testOldFileWithoutContainerRowsHasNoContainers() throws IOException {
        ExecutionMeasurements m = ExecutionMeasurements.read(Paths.get(DATA + "usage.csv"));
        assertTrue(m.containerUsage().isEmpty());
        ExecutionGraphDTO graph = ExecutionGraphBuilder.build(ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json")),
                m.tjobMetrics(m.toTimeline()), m.containerUsage());
        for (GraphNodeDTO n : graph.nodes) {
            assertTrue(n.containers.isEmpty());
        }
    }
}
