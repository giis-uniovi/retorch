/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The profiling side of the two contracts shared with the orchestration module: the execution plan written by the
 * orchestrator ({@code .retorch/executionplan.json}) and the usage file written by {@code resourcemonitor.sh export}. The
 * fixtures under {@code src/test/resources/contract} are the agreed examples (see the spec); the orchestration tests check
 * that its writers produce exactly the same fields and columns.
 */
public class ContractFixturesTests {

    private static Path fixture(String name) throws URISyntaxException {
        return Paths.get(ContractFixturesTests.class.getClassLoader().getResource("contract/" + name).toURI());
    }

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
    public void testUsageFixtureHasExactlyTheColumnsTheReaderExpects() throws Exception {
        List<String> lines = Files.readAllLines(fixture("usage.csv"), StandardCharsets.UTF_8);
        assertEquals(UsageCsvReader.COLUMNS, Arrays.asList(lines.get(0).split(";", -1)));
        Set<String> kinds = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(";", -1);
            assertEquals("every row has all the columns: " + line, UsageCsvReader.COLUMNS.size(), cells.length);
            kinds.add(cells[0]);
        }
        assertEquals(new HashSet<>(Arrays.asList("phase", "sample", "container")), kinds);
    }

    @Test
    public void testUsageFixtureIsReadWithContainersAndCoiPhases() throws Exception {
        ExecutionMeasurements m = ExecutionMeasurements.read(fixture("usage.csv"));
        Timeline timeline = m.toTimeline();
        assertEquals(ExecutionMeasurements.COI_SETUP, timeline.slices.get(0).coiPhase);
        assertEquals(ExecutionMeasurements.COI_TEARDOWN, timeline.slices.get(timeline.slices.size() - 1).coiPhase);
        assertEquals(new HashSet<>(Arrays.asList("tjoba", "tjobb")), m.tjobMetrics(timeline).keySet());
        assertFalse(m.containerUsage().get("tjoba").isEmpty());
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
