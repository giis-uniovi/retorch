/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.main;

import giis.retorch.profiling.usageprofile.ExecutionPlanReader;
import giis.retorch.profiling.usageprofile.PriceCatalog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** {@link UsageProfileRequest}, the browser resource pattern and the single {@code generate} entry point. */
public class UsageProfileRequestTests {

    private static final String DATA = "src/test/java/giis/retorch/profiling/testdata/usageprofile/";
    private static final String PROPERTY = UsageProfileRequest.BROWSER_RESOURCES_PROPERTY;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static PriceCatalog catalog() {
        return PriceCatalog.builder().fetchedAt(1700000000000L)
                .addVm(new PriceCatalog.VmOffer("P", "r1", "big", 4, 8, 3.6)).build();
    }

    private static ExecutionPlanReader.PlanNode node(String id, String... resources) {
        ExecutionPlanReader.PlanNode n = new ExecutionPlanReader.PlanNode();
        n.id = id;
        n.resources = new ArrayList<>(Arrays.asList(resources));
        return n;
    }

    @Test
    public void testBrowserPatternDefaultsToBrowserAndComesFromTheProperty() {
        String previous = System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            assertEquals("browser", UsageProfileRequest.builder("p", "u", "o").prices(catalog()).build().browserResourcePattern());
            System.setProperty(PROPERTY, "selenium");
            assertEquals("selenium", UsageProfileRequest.builder("p", "u", "o").prices(catalog()).build().browserResourcePattern());
            assertEquals("chrome", UsageProfileRequest.builder("p", "u", "o").prices(catalog())
                    .browserResourcePattern("chrome").build().browserResourcePattern());
            assertEquals("browser", UsageProfileRequest.builder("p", "u", "o").prices(catalog())
                    .browserResourcePattern("  ").build().browserResourcePattern());
        } finally {
            if (previous == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, previous);
            }
        }
    }

    @Test
    public void testRequestNeedsPrices() {
        try {
            UsageProfileRequest.builder("p", "u", "o").build();
            fail("a request without prices must be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("prices"));
        }
    }

    @Test
    public void testBrowserTjobsUseThePatternCaseInsensitively() {
        List<ExecutionPlanReader.PlanNode> plan = Arrays.asList(node("a", "executor"), node("b", "executor", "Browser-Chrome"),
                node("c", "Selenium-Hub"));
        assertEquals(1, UsageProfilerToolBox.browserTjobs(plan, "browser").size());
        assertTrue(UsageProfilerToolBox.browserTjobs(plan, "browser").contains("b"));
        assertTrue(UsageProfilerToolBox.browserTjobs(plan, "SELENIUM").contains("c"));
        assertTrue(UsageProfilerToolBox.browserTjobs(new ArrayList<ExecutionPlanReader.PlanNode>(), "browser").isEmpty());
    }

    @Test
    public void testMissingPlanFileMeansNoGraphButAnEmptyPlanMeansAnEmptyGraph() throws IOException {
        File plan = new File(tmp.getRoot(), "empty-plan.json");
        Files.write(plan.toPath(), "{\"tjobs\":[]}".getBytes(StandardCharsets.UTF_8));
        String withoutFile = new File(tmp.getRoot(), "a.html").getPath();
        String withEmpty = new File(tmp.getRoot(), "b.html").getPath();

        new UsageProfilerToolBox().generate(UsageProfileRequest.builder("P", DATA + "usage.csv", withoutFile)
                .prices(catalog()).executionPlan(new File(tmp.getRoot(), "missing.json").getPath()).build());
        new UsageProfilerToolBox().generate(UsageProfileRequest.builder("P", DATA + "usage.csv", withEmpty)
                .prices(catalog()).executionPlan(plan.getPath()).build());

        String none = new String(Files.readAllBytes(Paths.get(withoutFile)), StandardCharsets.UTF_8);
        String empty = new String(Files.readAllBytes(Paths.get(withEmpty)), StandardCharsets.UTF_8);
        assertTrue(none.contains("\"graph\":null"));
        assertFalse(empty.contains("\"graph\":null"));
        assertTrue(empty.contains("\"graph\":{\"nodes\":[]}"));
    }
}
