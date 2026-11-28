/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.CostReport;
import giis.retorch.profiling.usageprofile.dto.ExecutionGraph;
import giis.retorch.profiling.usageprofile.dto.ReservationOption;
import giis.retorch.profiling.usageprofile.dto.SutInfo;
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
        PriceCatalog.VmOffer v = new PriceCatalog.VmOffer();
        v.provider = provider;
        v.region = region;
        v.name = name;
        v.vcpu = vcpu;
        v.memGb = mem;
        v.hourly = hourly;
        return v;
    }

    /** Two providers, two regions of the first one, VMs with reservations, a container service and two browser services. */
    private static PriceCatalog richCatalog() {
        PriceCatalog catalog = new PriceCatalog();
        catalog.fetchedAtEpochMs = 1700000000000L;
        PriceCatalog.VmOffer small = vm("Azure", "westeurope", "D2s", 2, 8, 0.1);
        small.reservations.add(ReservationOption.allUpfront(1, 600));
        small.reservations.add(ReservationOption.allUpfront(3, 1500));
        PriceCatalog.VmOffer big = vm("Azure", "westeurope", "D4s", 4, 16, 0.2);
        big.reservations.add(ReservationOption.allUpfront(1, 1200));
        PriceCatalog.VmOffer east = vm("Azure", "eastus", "D2s", 2, 8, 0.09);
        PriceCatalog.VmOffer aws = vm("AWS", "eu-west-1", "m5.large", 2, 8, 0.11);
        aws.reservations.add(new ReservationOption(1, ReservationOption.Payment.NO_UPFRONT, 0.07));
        catalog.vms.addAll(Arrays.asList(small, big, east, aws));
        for (String[] r : new String[][]{{"Azure", "westeurope"}, {"AWS", "eu-west-1"}}) {
            PriceCatalog.ContainerRate rate = new PriceCatalog.ContainerRate();
            rate.provider = r[0];
            rate.region = r[1];
            rate.service = "Serverless containers";
            rate.vcpuHour = 0.05;
            rate.gbHour = 0.006;
            catalog.containers.add(rate);
            PriceCatalog.StorageRate disk = new PriceCatalog.StorageRate();
            disk.provider = r[0];
            disk.region = r[1];
            disk.name = "ssd";
            disk.gbMonth = 0.1;
            catalog.storage.add(disk);
        }
        PriceCatalog.StorageRate east1 = new PriceCatalog.StorageRate();
        east1.provider = "Azure";
        east1.region = "eastus";
        east1.name = "ssd";
        east1.gbMonth = 0.1;
        catalog.storage.add(east1);
        for (String browser : new String[]{"Chrome", "Firefox"}) {
            PriceCatalog.BrowserRate rate = new PriceCatalog.BrowserRate();
            rate.provider = "AWS";
            rate.region = "us-west-2";
            rate.service = "AWS Device Farm";
            rate.name = browser;
            rate.minuteUsd = 0.005;
            catalog.browsers.add(rate);
        }
        return catalog;
    }

    /** Writes the page of the test fixtures (containers, plan with a browser TJob) with a fixed SUT, so it is deterministic. */
    static Path writeFixturePage(Path output) throws IOException {
        ExecutionMeasurements measurements = ExecutionMeasurements.read(Paths.get(DATA + "usage-containers.csv"));
        ExecutionMeasurements.Timeline timeline = measurements.toTimeline();
        List<ExecutionPlanReader.PlanNode> plan = ExecutionPlanReader.read(Paths.get(DATA + "executionplan.json"));
        PriceCatalog catalog = richCatalog();
        CostReport report = new CostCalculator().calculate(timeline, catalog, new HashSet<>(Arrays.asList("TJOBB")));
        ExecutionGraph graph = ExecutionGraphBuilder.build(plan, measurements.tjobMetrics(timeline), measurements.containerUsage());
        new UsageProfileGenerator().generate("FixturePlan", timeline, report, catalog, graph,
                new SutInfo("petclinic", "https://github.com/giis-uniovi/petclinic", "main"), output);
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
}
