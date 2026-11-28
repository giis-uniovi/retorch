/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static giis.retorch.profiling.usageprofile.TestFixtures.*;

public class UsageContractFixtureTests {

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
}
