/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.measurement.ExecutionMeasurements;
import giis.retorch.profiling.usageprofile.measurement.Timeline;

import com.fasterxml.jackson.databind.JsonNode;
import giis.retorch.profiling.usageprofile.dto.CostReport;
import giis.retorch.profiling.usageprofile.dto.UsageProfileData;
import giis.retorch.profiling.usageprofile.dto.ExecutionGraph;
import giis.retorch.profiling.usageprofile.dto.GraphNode;
import giis.retorch.profiling.usageprofile.dto.ReservationOption;
import giis.retorch.profiling.usageprofile.dto.SutInfo;
import giis.retorch.profiling.usageprofile.dto.TimelineSeries;
import giis.retorch.profiling.main.UsageProfilerToolBox;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;
import giis.retorch.profiling.usageprofile.cost.CostCalculator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static giis.retorch.profiling.usageprofile.TestFixtures.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UsageProfileTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testJsonContractMatchesWhatTheUsageProfilePageReads() throws IOException {
        Timeline t = timeline();
        PriceCatalog catalog = smallCatalog();
        CostReport report = new CostCalculator().calculate(t, catalog);
        assertFalse(report.alternatives.isEmpty());
        TimelineSeries series = new TimelineSeries(Arrays.asList(0, 1), Arrays.asList(0.5, 1.5),
                Arrays.asList(0.1, 0.2), Arrays.asList(0.0, 1.0));
        JsonNode json = MAPPER.readTree(UsageProfileGenerator.toJson(
                new UsageProfileData("MyPlan", catalog.fetchedAtEpochMs, report, series, null)));

        assertEquals(set("plan", "pricesFetchedAt", "durationSec", "peakCpu", "peakMemGb", "peakDiskGb", "warnings",
                "alternatives", "timeline", "graph", "sut", "meta"), names(json));
        assertEquals(set("phases", "kinds", "objects"), names(json.get("meta")));
        assertEquals(Arrays.asList("COI-setup", "tjob-setup", "tjob-testexec", "tjob-teardown", "coi-teardown",
                "oversubscription"), texts(json.get("meta").get("phases")));
        assertEquals(Arrays.asList("VM", "CONTAINER", "BROWSER_SERVICE"), texts(json.get("meta").get("kinds")));
        assertEquals(Arrays.asList("compute", "storage", "browser"), texts(json.get("meta").get("objects")));
        assertTrue(json.has("graph") && json.get("graph").isNull());
        assertEquals("MyPlan", json.get("plan").asText());
        assertTrue(json.get("warnings").isArray());
        assertTrue(json.get("alternatives").isArray());
        assertTrue(json.get("alternatives").size() > 0);
        for (JsonNode alt : json.get("alternatives")) {
            assertEquals(set("id", "provider", "kind", "label", "region", "hourlyPrice", "total", "items",
                    "reservations"), names(alt));
            assertTrue(alt.get("reservations").isArray());
            assertTrue(alt.get("items").size() > 0);
            for (JsonNode item : alt.get("items")) {
                assertEquals(set("object", "tjob", "phase", "cost"), names(item));
            }
        }
        JsonNode timeline = json.get("timeline");
        assertEquals(set("t", "cpu", "memGb", "diskGb"), names(timeline));
        int n = timeline.get("t").size();
        assertEquals(n, timeline.get("cpu").size());
        assertEquals(n, timeline.get("memGb").size());
        assertEquals(n, timeline.get("diskGb").size());
    }

    @Test
    public void testLongTimelineIsDownsampledToAtMost600Points() throws IOException {
        StringBuilder csv = new StringBuilder("kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes\n");
        csv.append("phase;COI;;COI-setup;1700000000;1700000010;;;\n");
        csv.append("phase;tjoba;0;tjob-setup;1700000010;1700000020;;;\n");
        csv.append("phase;tjoba;0;tjob-testexec;1700000020;1700001500;;;\n");
        csv.append("phase;tjoba;0;tjob-teardown;1700001500;1700001510;;;\n");
        csv.append("phase;COI;;coi-teardown;1700001510;1700001520;;;\n");
        for (int s = 12; s < 1500; s += 10) {
            csv.append("sample;tjoba;;web-1;").append(1700000000 + s).append(";;100;1073741824;1073741824\n");
        }
        Path usage = tmp.newFile("long-usage.csv").toPath();
        Files.write(usage, csv.toString().getBytes(StandardCharsets.UTF_8));
        Timeline t = ExecutionMeasurements.read(usage).toTimeline();
        assertTrue(t.slices.size() > 600);

        PriceCatalog catalog = smallCatalog();
        CostReport report = new CostCalculator().calculate(t, catalog);
        Path out = new File(tmp.getRoot(), "long/usageprofile.html").toPath();
        new UsageProfileGenerator().generate("Long", t, report, catalog, null, out);

        JsonNode timeline = embeddedJson(new String(Files.readAllBytes(out), StandardCharsets.UTF_8)).get("timeline");
        int n = timeline.get("t").size();
        assertTrue(n > 1 && n <= 600);
        assertEquals(n, timeline.get("cpu").size());
        assertEquals(n, timeline.get("memGb").size());
        assertEquals(n, timeline.get("diskGb").size());
    }

    @Test
    public void testReservationsAreInTheJsonOfAVmAlternativeAndEmptyForContainers() throws IOException {
        PriceCatalog catalog = smallCatalog();
        catalog = smallCatalogBuilder().replaceVms(Arrays.asList(catalog.vms.get(0).withReservations(Arrays.asList(
                new ReservationOption(1, ReservationOption.Payment.NO_UPFRONT, 0.5),
                ReservationOption.allUpfront(3, 8760 * 3 * 0.25))))).build();
        CostReport report = new CostCalculator().calculate(timeline(), catalog);
        TimelineSeries series = new TimelineSeries(Arrays.asList(0), Arrays.asList(0.5), Arrays.asList(0.1),
                Arrays.asList(0.0));
        JsonNode json = MAPPER.readTree(UsageProfileGenerator.toJson(
                new UsageProfileData("P", catalog.fetchedAtEpochMs, report, series, null)));
        int vms = 0;
        int containers = 0;
        for (JsonNode alt : json.get("alternatives")) {
            JsonNode res = alt.get("reservations");
            if ("VM".equals(alt.get("kind").asText())) {
                vms++;
                assertEquals(2, res.size());
                assertEquals(set("termYears", "payment", "effectiveHourly"), names(res.get(0)));
                assertEquals(1, res.get(0).get("termYears").asInt());
                assertEquals("NO_UPFRONT", res.get(0).get("payment").asText());
                assertEquals(0.5, res.get(0).get("effectiveHourly").asDouble(), 1e-9);
                assertEquals("ALL_UPFRONT", res.get(1).get("payment").asText());
                assertEquals(0.25, res.get(1).get("effectiveHourly").asDouble(), 1e-9);
            } else {
                containers++;
                assertTrue(res.isArray());
                assertEquals(0, res.size());
            }
        }
        assertEquals(1, vms);
        assertEquals(1, containers);
    }

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
        ExecutionGraph graph = ExecutionGraphBuilder.build(
                ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json")), m.tjobMetrics(m.toTimeline()));
        assertEquals(3, graph.nodes.size());
        GraphNode b = graph.nodes.get(1);
        assertEquals("tjobb", b.id);
        assertEquals(20.0, b.execSec, 1e-9);
        assertEquals(Arrays.asList("tjoba"), b.predecessors);
        GraphNode gate = graph.nodes.get(2);
        assertEquals(null, gate.startSec);
        assertEquals(null, gate.setupSec);
        assertEquals(null, gate.peakCpu);
        assertEquals(null, gate.peakMemGb);
    }

    @Test
    public void testJsonGraphIsPresentAsNullOrAsNodes() throws IOException {
        ExecutionMeasurements m = ExecutionMeasurements.read(Paths.get(DATA + "usage.csv"));
        ExecutionGraph graph = ExecutionGraphBuilder.build(
                ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json")), m.tjobMetrics(m.toTimeline()));
        CostReport report = new CostCalculator().calculate(m.toTimeline(), smallCatalog());
        TimelineSeries series = new TimelineSeries(Arrays.asList(0), Arrays.asList(0.5), Arrays.asList(0.1), Arrays.asList(0.0));
        JsonNode withGraph = MAPPER.readTree(UsageProfileGenerator.toJson(new UsageProfileData("P", 1L, report, series, graph)));
        JsonNode node = withGraph.get("graph").get("nodes").get(0);
        assertEquals(set("id", "stage", "resources", "tests", "predecessors", "startSec", "setupSec", "execSec", "teardownSec",
                "peakCpu", "peakMemGb", "containers"), names(node));
        assertTrue(withGraph.get("graph").get("nodes").get(2).get("setupSec").isNull());
        JsonNode without = MAPPER.readTree(UsageProfileGenerator.toJson(new UsageProfileData("P", 1L, report, series, null)));
        assertTrue(without.has("graph"));
        assertTrue(without.get("graph").isNull());
    }

    @Test
    public void testOldFileWithoutContainerRowsHasNoContainers() throws IOException {
        ExecutionMeasurements m = ExecutionMeasurements.read(Paths.get(DATA + "usage.csv"));
        assertTrue(m.containerUsage().isEmpty());
        ExecutionGraph graph = ExecutionGraphBuilder.build(ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json")),
                m.tjobMetrics(m.toTimeline()), m.containerUsage());
        for (GraphNode n : graph.nodes) {
            assertTrue(n.containers.isEmpty());
        }
    }

    @Test
    public void testJsonGraphNodeCarriesTheContainers() throws IOException {
        ExecutionMeasurements m = containers();
        ExecutionGraph graph = ExecutionGraphBuilder.build(ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json")),
                m.tjobMetrics(m.toTimeline()), m.containerUsage());
        CostReport report = new CostCalculator().calculate(m.toTimeline(), smallCatalog());
        TimelineSeries series = new TimelineSeries(Arrays.asList(0), Arrays.asList(0.5), Arrays.asList(0.1), Arrays.asList(0.0));
        JsonNode nodes = MAPPER.readTree(UsageProfileGenerator.toJson(new UsageProfileData("P", 1L, report, series, graph)))
                .get("graph").get("nodes");
        JsonNode list = nodes.get(0).get("containers");
        assertEquals(2, list.size());
        assertEquals(set("name", "image", "peakCpu", "avgCpu", "peakMemGb", "avgMemGb", "layerGb", "imageGb", "volumeGb",
                "storageGb"), names(list.get(0)));
        assertEquals("db-1", list.get(0).get("name").asText());
        assertEquals(1.5, list.get(0).get("storageGb").asDouble(), 1e-9);
        assertTrue(nodes.get(2).get("containers").isArray());
        assertEquals(0, nodes.get(2).get("containers").size());
    }

    @Test
    public void testAssembledPageHasNoPlaceholdersAndInlinesEverything() throws IOException {
        String out = new File(tmp.getRoot(), "page/usageprofile.html").getPath();
        new UsageProfilerToolBox().generateUsageProfile("MyPlan", DATA + "usage.csv", out, smallCatalog(),
                DATA + "executionplan.json");
        String content = html(Paths.get(out));
        for (String placeholder : new String[]{"/*CSS*/", "/*DATA*/", "/*ECHARTS*/", "/*APP*/", "/*I18N*/", "<!--HEADER_LOGO_SVG-->",
                "<!--FOOTER_SVG-->"}) {
            assertFalse(placeholder, content.contains(placeholder));
        }
        assertTrue(content.contains("app.title"));
        assertTrue(content.contains("<svg"));
        assertTrue(content.contains("https://giis.uniovi.es/"));
        assertFalse(content.contains("src=\"http"));
        assertTrue(embeddedJson(content).get("graph").get("nodes").size() == 3);
        int previous = -1;
        for (String script : UsageProfileGenerator.appScripts()) {
            String source = new String(Files.readAllBytes(Paths.get("src/main/resources/" + script)),
                    StandardCharsets.UTF_8).trim().replace("</script", "<\\/script");
            int at = content.indexOf(source);
            assertTrue(script + " is not inlined", at > previous);
            previous = at;
        }
        List<String> scripts = UsageProfileGenerator.appScripts();
        assertTrue(scripts.get(0).endsWith("js/i18n.js"));
        assertTrue(scripts.get(scripts.size() - 1).endsWith("js/app.js"));
        assertTrue(scripts.contains("usageprofile/js/views/simulator.js"));
    }

    @Test
    public void testMissingPlanFileMeansNoGraph() throws IOException {
        String out = new File(tmp.getRoot(), "nograph/usageprofile.html").getPath();
        new UsageProfilerToolBox().generateUsageProfile("MyPlan", DATA + "usage.csv", out, smallCatalog(), "no-such-file.json");
        assertTrue(embeddedJson(html(Paths.get(out))).get("graph").isNull());
    }

    @Test
    public void testUsageProfileIsSelfContained() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder().fetchedAt(1700000000000L).addVm(vm("P", "big", 4, 8, 3.6)).build();
        String out = new File(tmp.getRoot(), "out/usageprofile.html").getPath();

        new UsageProfilerToolBox().generateUsageProfile("MyPlan", DATA + "usage.csv", out, catalog);

        Path html = Paths.get(out);
        assertTrue(Files.exists(html));
        String content = new String(Files.readAllBytes(html), StandardCharsets.UTF_8);
        assertNotNull(content);
        assertFalse(content.contains("/*DATA*/"));
        assertFalse(content.contains("/*ECHARTS*/"));
        assertTrue(content.contains("\"plan\":\"MyPlan\""));
        assertTrue(content.contains("Licensed to the Apache Software Foundation"));
        assertFalse(content.contains("src=\"http"));
    }

    /** XML namespace identifiers of the inlined SVG logos: they are names, the browser never fetches them. */
    private static final String[] ALLOWED_URL_PREFIXES = {"https://giis.uniovi.es", "http://www.w3.org/",
        "http://purl.org/", "http://creativecommons.org/", "http://www.inkscape.org/", "http://sodipodi.sourceforge.net/"};

    @Test
    public void testTheProfileIsOneSingleFileWithNoExternalReferences() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder().fetchedAt(1700000000000L).addVm(vm("P", "big", 4, 8, 3.6)).build();
        File outDir = new File(tmp.getRoot(), "single");
        String out = new File(outDir, "usageprofile.html").getPath();

        new UsageProfilerToolBox().generateUsageProfile("MyPlan", DATA + "usage.csv", out, catalog, DATA + "executionplan.json");

        assertEquals("only the HTML file is written", 1, outDir.list().length);
        String page = new String(Files.readAllBytes(Paths.get(out)), StandardCharsets.UTF_8);
        // ECharts is third-party code with URLs in its comments and messages: check everything else
        int libStart = page.indexOf("Licensed to the Apache Software Foundation");
        int libEnd = page.indexOf("</script>", libStart);
        assertTrue(libStart >= 0 && libEnd > libStart);
        String own = page.substring(0, libStart) + page.substring(libEnd);
        String[] forbidden = {"<script src", "<img ", "<iframe", "@import", "url(http", "url('http",
            "url(" + (char) 34 + "http", "fetch(", "XMLHttpRequest", "src=" + (char) 34, "import("};
        for (String reference : forbidden) {
            assertFalse("external reference found: " + reference, own.contains(reference));
        }
        // the only <link> is the page icon (the GIIS logo) embedded as a data URI: no other file is needed
        int links = 0;
        for (int i = own.indexOf("<link "); i >= 0; i = own.indexOf("<link ", i + 5)) {
            links++;
        }
        assertEquals("only the page icon", 1, links);
        String icon = "<link rel=\"icon\" type=\"image/svg+xml\" href=\"data:image/svg+xml;base64,";
        int iconAt = own.indexOf(icon);
        assertTrue("the page has the GIIS logo as icon", iconAt >= 0);
        String encoded = own.substring(iconAt + icon.length(), own.indexOf('"', iconAt + icon.length()));
        assertTrue(new String(java.util.Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8).startsWith("<svg"));
        assertFalse(own.contains("/*FAVICON*/"));
        for (int i = own.indexOf("http"); i >= 0; i = own.indexOf("http", i + 4)) {
            String rest = own.substring(i);
            assertTrue("unexpected URL: " + rest.substring(0, Math.min(70, rest.length())),
                    !(rest.startsWith("http://") || rest.startsWith("https://")) || isAllowedUrl(rest));
        }
    }

    @Test
    public void testSutAndBranchComeFromTheJenkinsEnvironmentWithoutCredentials() {
        Map<String, String> env = new HashMap<>();
        env.put("GIT_URL", "https://user:secret-token@github.com/giis-uniovi/retorch-st-petclinic.git");
        env.put("BRANCH_NAME", "feature-x");
        SutInfo sut = SutInfo.detect(env, tmp.getRoot().toPath());
        assertEquals("retorch-st-petclinic", sut.name);
        assertEquals("https://github.com/giis-uniovi/retorch-st-petclinic", sut.url);
        assertEquals("feature-x", sut.branch);

        env.clear();
        env.put("GIT_URL", "git@github.com:giis-uniovi/retorch-st-fullteaching.git");
        env.put("GIT_BRANCH", "origin/main");
        sut = SutInfo.detect(env, tmp.getRoot().toPath());
        assertEquals("https://github.com/giis-uniovi/retorch-st-fullteaching", sut.url);
        assertEquals("main", sut.branch);
    }

    @Test
    public void testSutAndBranchComeFromTheWorkspaceGitFilesWhenThereIsNoEnvironment() throws IOException {
        File git = tmp.newFolder("ws", ".git");
        Files.write(new File(git, "config").toPath(), ("[core]\n\tbare = false\n[remote \"origin\"]\n\turl = ssh://git@github.com:22/giis-uniovi/shop.git\n"
                + "\tfetch = +refs/heads/*:refs/remotes/origin/*\n").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(git, "HEAD").toPath(), "ref: refs/heads/dev\n".getBytes(StandardCharsets.UTF_8));
        SutInfo sut = SutInfo.detect(new HashMap<String, String>(), git.getParentFile().toPath());
        assertEquals("shop", sut.name);
        assertEquals("https://github.com/giis-uniovi/shop", sut.url);
        assertEquals("dev", sut.branch);
        // unknown values are empty, never null, and a local path is not turned into a link
        SutInfo none = SutInfo.detect(new HashMap<String, String>(), tmp.newFolder("empty").toPath());
        assertEquals("", none.name + none.url + none.branch);
        assertEquals("", new SutInfo("x", "/local/path", null).branch);
    }

    @Test
    public void testTheHeaderDataTravelsInThePage() throws IOException {
        Timeline t = timeline();
        PriceCatalog catalog = smallCatalog();
        CostReport report = new CostCalculator().calculate(t, catalog);
        Path out = new File(tmp.getRoot(), "sut/usageprofile.html").toPath();
        new UsageProfileGenerator().generate("P", t, report, catalog, null,
                new SutInfo("petclinic", "https://github.com/giis-uniovi/petclinic", "main"), out);
        JsonNode sut = embeddedJson(new String(Files.readAllBytes(out), StandardCharsets.UTF_8)).get("sut");
        assertEquals("petclinic", sut.get("name").asText());
        assertEquals("https://github.com/giis-uniovi/petclinic", sut.get("url").asText());
        assertEquals("main", sut.get("branch").asText());
    }

    private static boolean isAllowedUrl(String text) {
        for (String prefix : ALLOWED_URL_PREFIXES) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
