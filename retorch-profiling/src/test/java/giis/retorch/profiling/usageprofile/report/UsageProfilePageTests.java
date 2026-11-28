/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.report;

import giis.retorch.profiling.usageprofile.measurement.ExecutionMeasurements;
import giis.retorch.profiling.usageprofile.measurement.Timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.CostReportDTO;
import giis.retorch.profiling.usageprofile.dto.ExecutionGraphDTO;
import giis.retorch.profiling.usageprofile.dto.ReservationOptionDTO;
import giis.retorch.profiling.usageprofile.dto.SutInfoDTO;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;
import giis.retorch.profiling.usageprofile.cost.CostCalculator;
import giis.retorch.profiling.usageprofile.plan.ExecutionGraphBuilder;
import giis.retorch.profiling.usageprofile.plan.ExecutionPlanReader;
import giis.retorch.profiling.usageprofile.report.UsageProfileGenerator;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests of the assembled page (front-end): the fixture page that the browser smoke test opens and the files that make
 * the page (script list, translations).
 */
public class UsageProfilePageTests {

    private static final String DATA = "src/test/java/giis/retorch/profiling/testdata/usageprofile/";
    /** Where the page for {@code src/test/js/smoke.mjs} is written (the profile {@code js-tests} opens it). */
    static final String SMOKE_PAGE = "target/usageprofile-smoke/usageprofile.html";

    private static PriceCatalog.VmOffer vm(String provider, String region, String name, double vcpu, double mem, double hourly) {
        return new PriceCatalog.VmOffer(provider, region, name, vcpu, mem, hourly);
    }

    /** Two providers, two regions of the first one, VMs with reservations, a container service and two browser services. */
    private static PriceCatalog richCatalog() {
        PriceCatalog.Builder catalog = PriceCatalog.builder().fetchedAt(1700000000000L);
        catalog.addVm(vm("Azure", "westeurope", "D2s", 2, 8, 0.1).withReservations(Arrays.asList(
                ReservationOptionDTO.allUpfront(1, 600), ReservationOptionDTO.allUpfront(3, 1500))));
        catalog.addVm(vm("Azure", "westeurope", "D4s", 4, 16, 0.2).withReservations(Arrays.asList(
                ReservationOptionDTO.allUpfront(1, 1200))));
        catalog.addVm(vm("Azure", "eastus", "D2s", 2, 8, 0.09));
        catalog.addVm(vm("AWS", "eu-west-1", "m5.large", 2, 8, 0.11).withReservations(Arrays.asList(
                new ReservationOptionDTO(1, ReservationOptionDTO.Payment.NO_UPFRONT, 0.07))));
        for (String[] r : new String[][]{{"Azure", "westeurope"}, {"AWS", "eu-west-1"}}) {
            catalog.addContainer(new PriceCatalog.ContainerRate(r[0], r[1], "Serverless containers", 0.05, 0.006));
            catalog.addStorage(new PriceCatalog.StorageRate(r[0], r[1], "ssd", 0.1));
        }
        catalog.addStorage(new PriceCatalog.StorageRate("Azure", "eastus", "ssd", 0.1));
        for (String browser : new String[]{"Chrome", "Firefox"}) {
            catalog.addBrowser(new PriceCatalog.BrowserRate("AWS", "us-west-2", "AWS Device Farm", browser, 0.005));
        }
        return catalog.build();
    }

    /** Writes the page of the test fixtures (containers, plan with a browser TJob) with a fixed SUT, so it is deterministic. */
    static Path writeFixturePage(Path output) throws IOException {
        ExecutionMeasurements measurements = ExecutionMeasurements.read(Paths.get(DATA + "usage-containers.csv"));
        Timeline timeline = measurements.toTimeline();
        List<ExecutionPlanReader.PlanNode> plan = ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json"));
        PriceCatalog catalog = richCatalog();
        CostReportDTO report = new CostCalculator().calculate(timeline, catalog, new HashSet<>(Arrays.asList("TJOBB")));
        ExecutionGraphDTO graph = ExecutionGraphBuilder.build(plan, measurements.tjobMetrics(timeline), measurements.containerUsage());
        new UsageProfileGenerator().generate("FixturePlan", timeline, report, catalog, graph,
                new SutInfoDTO("petclinic", "https://github.com/giis-uniovi/petclinic", "main"), output);
        return output;
    }

    @Test
    public void testFixturePageForTheBrowserSmokeTestIsWritten() throws IOException {
        Path page = writeFixturePage(Paths.get(SMOKE_PAGE));
        String html = new String(Files.readAllBytes(page), StandardCharsets.UTF_8);
        assertTrue(html.contains("\"plan\":\"FixturePlan\""));
        assertTrue(html.contains("BROWSER_SERVICE"));
        assertTrue(html.contains("\"reservations\":[{"));
        assertFalse(html.contains("\"graph\":null"));
        assertEquals(1, html.split("<\\/script>\\s*</body>", -1).length - 1);
    }

    // ---- translations: one JSON per language, the same keys as English ----

    private static final String I18N_DIR = "src/main/resources/usageprofile/i18n/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode translations(String language) throws IOException {
        return MAPPER.readTree(Files.readAllBytes(Paths.get(I18N_DIR + language + ".json")));
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = Pattern.compile("\\{[A-Za-z]+\\}").matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    /** Fails with the keys a language lacks or adds, and the texts that are empty or lose a {placeholder}. */
    private static void assertSameKeysAsEnglish(String language, JsonNode english, JsonNode other) {
        Set<String> missing = keys(english);
        missing.removeAll(keys(other));
        Set<String> extra = keys(other);
        extra.removeAll(keys(english));
        assertTrue(language + " lacks the keys " + missing, missing.isEmpty());
        assertTrue(language + " has keys that English does not have " + extra, extra.isEmpty());
        for (String key : keys(english)) {
            assertFalse(language + " has an empty text for " + key, other.get(key).asText().trim().isEmpty());
            assertEquals(language + " changes the placeholders of " + key, placeholders(english.get(key).asText()),
                    placeholders(other.get(key).asText()));
        }
    }

    @Test
    public void testEveryLanguageHasExactlyTheKeysOfEnglish() throws IOException {
        JsonNode english = translations("en");
        assertTrue(english.size() > 100);
        for (String language : UsageProfileGenerator.LANGUAGES) {
            assertSameKeysAsEnglish(language, english, translations(language));
        }
    }

    @Test
    public void testTheLanguageFilesAreTheListedLanguages() {
        Set<String> files = new TreeSet<>();
        for (String name : new java.io.File(I18N_DIR).list()) {
            files.add(name.replace(".json", ""));
        }
        assertEquals(new TreeSet<>(UsageProfileGenerator.LANGUAGES), files);
    }

    @Test
    public void testThePageCarriesTheTranslationsOfEveryLanguageUnderOnePlaceholder() throws IOException {
        String html = new String(Files.readAllBytes(writeFixturePage(Paths.get(SMOKE_PAGE))), StandardCharsets.UTF_8);
        assertFalse(html.contains("/*I18N*/"));
        String open = "<script type=\"application/json\" id=\"i18n\">";
        int start = html.indexOf(open) + open.length();
        JsonNode inlined = MAPPER.readTree(html.substring(start, html.indexOf("</script>", start)).replace("<\\/", "</"));
        assertEquals(new TreeSet<>(UsageProfileGenerator.LANGUAGES), keys(inlined));
        for (String language : UsageProfileGenerator.LANGUAGES) {
            assertEquals(translations(language), inlined.get(language));
        }
    }

    // ---- the list of scripts ----

    private static void collectScripts(java.io.File dir, String prefix, Set<String> found) {
        for (java.io.File f : dir.listFiles()) {
            if (f.isDirectory()) {
                collectScripts(f, prefix + f.getName() + "/", found);
            } else if (f.getName().endsWith(".js")) {
                found.add(prefix + f.getName());
            }
        }
    }

    @Test
    public void testEveryScriptFileIsListedExactlyOnceAndEveryListedScriptExists() throws IOException {
        String root = "src/main/resources/usageprofile/js/";
        List<String> listed = new java.util.ArrayList<>();
        for (String resource : UsageProfileGenerator.appScripts()) {
            listed.add(resource.substring("usageprofile/js/".length()));
        }
        Set<String> files = new TreeSet<>();
        collectScripts(new java.io.File(root), "", files);
        assertEquals("a script is listed twice: " + listed, new TreeSet<>(listed).size(), listed.size());
        assertEquals("scripts.txt and the .js files under js/ differ", files, new TreeSet<>(listed));
        assertEquals("i18n.js", listed.get(0));
        assertEquals("app.js", listed.get(listed.size() - 1));
        for (String name : listed) {
            assertTrue(name + " is listed but does not exist", Files.exists(Paths.get(root + name)));
        }
    }
}
