/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static giis.retorch.profiling.usageprofile.TestFixtures.*;

public class PlanContractFixtureTests {

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
}
