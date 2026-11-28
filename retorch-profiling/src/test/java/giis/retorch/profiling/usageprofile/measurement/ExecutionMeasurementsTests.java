/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import giis.retorch.profiling.usageprofile.dto.measurement.ContainerUsageDTO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static giis.retorch.profiling.usageprofile.TestFixtures.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExecutionMeasurementsTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testTimelineJoinsPhasesAndResources() throws IOException {
        Timeline t = timeline();
        assertEquals(81, t.slices.size());
        assertEquals(ExecutionMeasurements.COI_SETUP, t.slices.get(0).coiPhase);
        assertEquals(null, t.slices.get(30).coiPhase);
        assertEquals(ExecutionMeasurements.COI_TEARDOWN, t.slices.get(75).coiPhase);
        assertEquals(1.5, t.peak("cpu"), 1e-9);
        assertEquals(1.5, t.peak("mem"), 1e-9);
        assertEquals(1.0, t.peak("disk"), 1e-9);
        // at second 25 both TJobs run their test execution
        assertEquals(2, t.slices.get(25).loads.size());
        assertEquals("tjob-testexec", t.slices.get(25).loads.get(0).phase);
    }

    @Test
    public void testRealJenkinsRunIsReadFromASingleFile() throws IOException {
        Timeline t = ExecutionMeasurements.read(Paths.get(DATA + "real-run.csv")).toTimeline();
        // 10 TJobs measured by the pipeline; the execution lasts from the COI set-up to the COI tear-down (316 s)
        assertEquals(317, t.slices.size());
        assertTrue(t.peak("cpu") > 1.0);
        assertTrue(t.peak("mem") > 0.1);
        java.util.Set<String> tjobs = new java.util.HashSet<>();
        for (Slice s : t.slices) {
            for (Load l : s.loads) tjobs.add(l.tjob);
        }
        assertEquals(10, tjobs.size());
    }

    @Test(expected = IOException.class)
    public void testIncompletePhasesAreRejected() throws IOException {
        Path file = tmp.newFile("incomplete.csv").toPath();
        Files.write(file, ("kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes\n"
                + "phase;tjoba;0;tjob-setup;1700000010;1700000020;;;\n").getBytes(StandardCharsets.UTF_8));
        ExecutionMeasurements.read(file);
    }

    @Test
    public void testTJobMetricsComeFromPhasesAndTimeline() throws IOException {
        ExecutionMeasurements m = ExecutionMeasurements.read(Paths.get(DATA + "usage.csv"));
        Map<String, TJobMetrics> metrics = m.tjobMetrics(m.toTimeline());
        assertEquals(2, metrics.size());
        TJobMetrics a =metrics.get("tjoba");
        assertEquals(10.0, a.startSec, 1e-9);
        assertEquals(10.0, metrics.get("tjobb").startSec, 1e-9);
        assertEquals(10.0, a.setupSec, 1e-9);
        assertEquals(40.0, a.execSec, 1e-9);
        assertEquals(10.0, a.teardownSec, 1e-9);
        assertEquals(1.0, a.peakCpu, 1e-9);
        assertEquals(1.0, a.peakMemGb, 1e-9);
        assertEquals(20.0, metrics.get("tjobb").execSec, 1e-9);
        assertEquals(0.5, metrics.get("tjobb").peakCpu, 1e-9);
        assertEquals(0.5, metrics.get("tjobb").peakMemGb, 1e-9);
    }

    @Test
    public void testPeakDiskIncludesImagesAndVolumesAndSharedImagesCountOncePerTJob() throws IOException {
        Timeline t = containers().toTimeline();
        // second 12: tjoba = web layer 0.25 + app:1 image 1; tjobb = redis:7 image 2
        assertEquals(3.25, t.slices.get(12).loads.get(0).diskGb + t.slices.get(12).loads.get(1).diskGb, 1e-9);
        // second 22: db-1 appears (shares app:1, adds a 0.5 GB volume); web layer 0.5 -> tjoba = 0.5 + 0.5 + 1; tjobb = 2
        assertEquals(4.0, t.peak("disk"), 1e-9);
        assertEquals(4.0, t.peak("cpu"), 1e-9); // second 32: tjoba 2 + 1.5, tjobb 0.5 (not affected by storage)
        assertEquals(1.0, ExecutionMeasurements.read(Paths.get(DATA + "usage.csv")).toTimeline().peak("disk"), 1e-9);
    }

    @Test
    public void testContainerUsageHasPeaksAveragesAndStorage() throws IOException {
        Map<String, List<ContainerUsageDTO>> usage = containers().containerUsage();
        assertEquals(set("tjoba", "tjobb"), usage.keySet());
        List<ContainerUsageDTO> a = usage.get("tjoba");
        assertEquals(2, a.size());
        ContainerUsageDTO db = a.get(0);
        assertEquals("db-1", db.name);
        assertEquals("app:1", db.image);
        assertEquals(1.5, db.peakCpu, 1e-9);
        assertEquals(1.0, db.avgCpu, 1e-9);
        assertEquals(0.5, db.peakMemGb, 1e-9);
        assertEquals(0.5, db.avgMemGb, 1e-9);
        assertEquals(0.0, db.layerGb, 1e-9);
        assertEquals(1.0, db.imageGb, 1e-9);
        assertEquals(0.5, db.volumeGb, 1e-9);
        assertEquals(1.5, db.storageGb, 1e-9);
        ContainerUsageDTO web = a.get(1);
        assertEquals("web-1", web.name);
        assertEquals(2.0, web.peakCpu, 1e-9);
        assertEquals(1.5, web.avgCpu, 1e-9);
        assertEquals(2.0, web.peakMemGb, 1e-9);
        assertEquals(1.5, web.avgMemGb, 1e-9);
        assertEquals(0.5, web.layerGb, 1e-9);
        assertEquals(1.0, web.imageGb, 1e-9); // a shared image counts fully in each container row
        assertEquals(1.5, web.storageGb, 1e-9);
        assertEquals("redis:7", usage.get("tjobb").get(0).image);
        assertEquals(2.0, usage.get("tjobb").get(0).imageGb, 1e-9);
    }
}
