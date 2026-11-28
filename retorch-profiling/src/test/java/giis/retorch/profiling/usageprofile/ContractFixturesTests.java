/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.measurement.ExecutionMeasurements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static giis.retorch.profiling.usageprofile.TestFixtures.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The profiling side of the two contracts shared with the orchestration module: the execution plan written by the
 * orchestrator ({@code .retorch/executionplan.json}) and the usage file written by {@code resourcemonitor.sh export}. The
 * fixtures under {@code src/test/resources/contract} are the agreed examples (see the spec); the orchestration tests check
 * that its writers produce exactly the same fields and columns.
 */
public class ContractFixturesTests {

    @Test
    public void testExecutionPlanFixtureHasExactlyTheFieldsTheReaderExpects() throws Exception {
        JsonNode plan = new ObjectMapper().readTree(fixture("executionplan.json").toFile());
        Set<String> top = new HashSet<>();
        for (Iterator<String> it = plan.fieldNames(); it.hasNext();) {
            top.add(it.next());
        }
        assertEquals(new HashSet<>(Arrays.asList("tjobs")), top);
        assertTrue(plan.get("tjobs").size() > 0);
        for (JsonNode node : plan.get("tjobs")) {
            Set<String> fields = new HashSet<>();
            for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
                fields.add(it.next());
            }
            assertEquals(new HashSet<>(ExecutionPlanReader.NODE_FIELDS), fields);
        }
        List<ExecutionPlanReader.PlanNode> nodes = ExecutionPlanReader.read(fixture("executionplan.json"));
        assertEquals(plan.get("tjobs").size(), nodes.size());
        assertEquals("tjoba", nodes.get(0).id);
        assertEquals(Arrays.asList("tjoba"), nodes.get(1).predecessors);
    }

    @Test
    public void testThePlanFixtureAndTheUsageFixtureDescribeTheSameTJobs() throws Exception {
        Set<String> inPlan = new HashSet<>();
        for (ExecutionPlanReader.PlanNode n : ExecutionPlanReader.read(fixture("executionplan.json"))) {
            inPlan.add(n.id);
        }
        List<String> measured = new ArrayList<>(ExecutionMeasurements.read(fixture("usage.csv"))
                .tjobMetrics(ExecutionMeasurements.read(fixture("usage.csv")).toTimeline()).keySet());
        assertTrue(inPlan.containsAll(measured));
    }
}
