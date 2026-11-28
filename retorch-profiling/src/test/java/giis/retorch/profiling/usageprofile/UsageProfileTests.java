/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.Alternative;
import giis.retorch.profiling.usageprofile.dto.ContainerUsage;
import giis.retorch.profiling.usageprofile.dto.CostItem;
import giis.retorch.profiling.usageprofile.dto.CostReport;
import giis.retorch.profiling.usageprofile.dto.UsageProfileData;
import giis.retorch.profiling.usageprofile.dto.ExecutionGraph;
import giis.retorch.profiling.usageprofile.dto.GraphNode;
import giis.retorch.profiling.usageprofile.dto.ReservationOption;
import giis.retorch.profiling.usageprofile.dto.SutInfo;
import giis.retorch.profiling.usageprofile.dto.TimelineSeries;
import giis.retorch.profiling.main.UsageProfilerToolBox;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UsageProfileTests {

    private static final String DATA = "src/test/java/giis/retorch/profiling/testdata/usageprofile/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Timeline timeline() throws IOException {
        return ExecutionMeasurements.read(Paths.get(DATA + "usage.csv")).toTimeline();
    }

    private static PriceCatalog.VmOffer vm(String provider, String name, double vcpu, double mem, double hourly) {
        return new PriceCatalog.VmOffer(provider, "r1", name, vcpu, mem, hourly);
    }

    private static boolean hasItem(Alternative alt, String object, String tjob, String phase) {
        for (CostItem i : alt.items) {
            if (object.equals(i.object) && tjob.equals(i.tjob) && phase.equals(i.phase)) return true;
        }
        return false;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asText());
        }
        return out;
    }

    private static Set<String> names(JsonNode node) {
        Set<String> result = new HashSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) result.add(it.next());
        return result;
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static PriceCatalog smallCatalog() {
        return smallCatalogBuilder().build();
    }

    private static PriceCatalog.Builder smallCatalogBuilder() {
        return PriceCatalog.builder().fetchedAt(1700000000000L)
                .addVm(vm("P", "big", 4, 8, 3.6))
                .addContainer(new PriceCatalog.ContainerRate("P", "r1", "Serverless", 1, 1))
                .addStorage(new PriceCatalog.StorageRate("P", "r1", "ssd", 0.1));
    }

    private static JsonNode embeddedJson(String html) throws IOException {
        int start = html.indexOf("{\"plan\":");
        assertTrue(start >= 0);
        return MAPPER.readTree(html.substring(start)); // reads the first value, ignores what follows
    }

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
    public void testVmCostIsFullyDistributedAndShowsOversubscription() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder()
                .addVm(vm("P", "big", 4, 8, 3.6))
                .addVm(vm("P", "small", 2, 2, 1.8))
                .addVm(vm("P", "tiny", 1, 1, 0.5)) // cannot hold the peak, must be discarded
                .addStorage(new PriceCatalog.StorageRate("P", "r1", "ssd", 0.1)).build();

        CostReport report = new CostCalculator().calculate(timeline(), catalog);

        assertEquals(2, report.alternatives.size());
        Alternative small = report.alternatives.get(0);
        assertEquals("small (2 vCPU, 2 GB)", small.label);
        double expectedVm = 81 * 1.8 / 3600.0;
        double expectedDisk = 0.1 * 1 / (30.0 * 86400) * 81;
        assertEquals(expectedVm + expectedDisk, small.total, 1e-9);
        double coiCompute = 0;
        double idleCompute = 0;
        for (CostItem i : small.items) {
            if (CostCalculator.COMPUTE.equals(i.object) && CostCalculator.COI.equals(i.tjob)) coiCompute += i.cost;
            if (CostCalculator.COMPUTE.equals(i.object) && CostCalculator.OVERSUBSCRIPTION.equals(i.phase)) idleCompute += i.cost;
        }
        assertEquals(21 * 1.8 / 3600.0, coiCompute, 1e-9); // 10 s of COI set-up and 11 s of COI tear-down
        assertTrue(idleCompute > 0);
        assertTrue(hasItem(small, "compute", "tjoba", "tjob-testexec"));
        assertTrue(hasItem(small, "storage", "tjoba", "tjob-testexec"));
    }

    @Test
    public void testContainerCostIsPayPerUse() throws IOException {
        // 1 USD per vCPU-second makes the expectation easy to compute
        PriceCatalog catalog = PriceCatalog.builder()
                .addContainer(new PriceCatalog.ContainerRate("P", "r1", "Serverless", 3600, 0)).build();

        Alternative alt = new CostCalculator().calculate(timeline(), catalog).alternatives.get(0);

        // vCPU-seconds: tjoba uses 1 vCPU for the 10+40+10... the samples hold between 12 s and 70 s
        double cpuSeconds = 0;
        Timeline t = timeline();
        for (Slice s : t.slices) {
            for (Load l : s.loads) cpuSeconds += l.cpu;
        }
        assertEquals(cpuSeconds, alt.total, 1e-6);
        for (CostItem i : alt.items) {
            assertFalse(CostCalculator.OVERSUBSCRIPTION.equals(i.phase));
            assertFalse(CostCalculator.COI.equals(i.tjob));
        }
    }

    @Test
    public void testNoVmFitsProducesWarning() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder().addVm(vm("P", "tiny", 1, 1, 0.5)).build();
        CostReport report = new CostCalculator().calculate(timeline(), catalog);
        assertTrue(report.alternatives.isEmpty());
        assertEquals(1, report.warnings.size());
    }

    @Test
    public void testAzureParsersIgnoreSpotAndWindows() throws IOException {
        JsonNode items = MAPPER.readTree("[" +
                "{\"skuName\":\"Standard_D2s_v5\",\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series\",\"unitOfMeasure\":\"1 Hour\",\"retailPrice\":0.115}," +
                "{\"skuName\":\"Standard_D2s_v5 Spot\",\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series\",\"unitOfMeasure\":\"1 Hour\",\"retailPrice\":0.02}," +
                "{\"skuName\":\"Standard_D2s_v5\",\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series Windows\",\"unitOfMeasure\":\"1 Hour\",\"retailPrice\":0.2}]");
        PriceCatalog.Builder out = PriceCatalog.builder();
        AzurePrices.parseVms(toArray(items), "westeurope", out);
        assertEquals(1, out.build().vms.size());
        assertEquals(0.115, out.build().vms.get(0).hourly, 1e-9);
        assertEquals(8, out.build().vms.get(0).memGb, 1e-9);

        JsonNode aci = MAPPER.readTree("[{\"meterName\":\"Standard vCPU Duration\",\"retailPrice\":0.0466}," +
                "{\"meterName\":\"Standard Memory Duration\",\"retailPrice\":0.00511},{\"meterName\":\"Standard Windows Software Duration\",\"retailPrice\":1}]");
        AzurePrices.parseContainers(toArray(aci), "westeurope", out);
        assertEquals(0.0466, out.build().containers.get(0).vcpuHour, 1e-9);

        JsonNode disks = MAPPER.readTree("[{\"meterName\":\"E10 LRS Disk Mount\",\"unitOfMeasure\":\"1/Month\",\"retailPrice\":1.183}," +
                "{\"meterName\":\"E10 LRS Disk\",\"unitOfMeasure\":\"1/Month\",\"retailPrice\":9.6}]");
        AzurePrices.parseDisks(toArray(disks), "westeurope", out);
        assertEquals(0.075, out.build().storage.get(0).gbMonth, 1e-9);
    }

    private static JsonNode[] toArray(JsonNode array) {
        JsonNode[] result = new JsonNode[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i);
        return result;
    }

    @Test
    public void testAwsOfferIsReadInStreamingAndFiltered() throws IOException {
        String json = "{\"formatVersion\":\"v1.0\",\"products\":{" +
                "\"S1\":{\"sku\":\"S1\",\"productFamily\":\"Compute Instance\",\"attributes\":{\"instanceType\":\"m5.large\",\"vcpu\":\"2\",\"memory\":\"8 GiB\",\"operatingSystem\":\"Linux\",\"tenancy\":\"Shared\",\"preInstalledSw\":\"NA\",\"capacitystatus\":\"Used\",\"currentGeneration\":\"Yes\"}}," +
                "\"S2\":{\"sku\":\"S2\",\"productFamily\":\"Compute Instance\",\"attributes\":{\"instanceType\":\"m5.large\",\"vcpu\":\"2\",\"memory\":\"8 GiB\",\"operatingSystem\":\"Windows\",\"tenancy\":\"Shared\",\"preInstalledSw\":\"NA\",\"capacitystatus\":\"Used\",\"currentGeneration\":\"Yes\"}}," +
                "\"S3\":{\"sku\":\"S3\",\"productFamily\":\"Storage\",\"attributes\":{\"volumeApiName\":\"gp3\"}}}," +
                "\"terms\":{\"OnDemand\":{" +
                "\"S1\":{\"S1.T\":{\"priceDimensions\":{\"S1.T.R\":{\"unit\":\"Hrs\",\"pricePerUnit\":{\"USD\":\"0.096\"}}}}}," +
                "\"S2\":{\"S2.T\":{\"priceDimensions\":{\"S2.T.R\":{\"unit\":\"Hrs\",\"pricePerUnit\":{\"USD\":\"0.2\"}}}}}," +
                "\"S3\":{\"S3.T\":{\"priceDimensions\":{\"S3.T.R\":{\"unit\":\"GB-Mo\",\"pricePerUnit\":{\"USD\":\"0.08\"}}}}}}," +
                "\"Reserved\":{}}}";
        Map<JsonNode, Double> offer = AwsPrices.readOffer(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), p -> p.path("attributes").path("operatingSystem").asText("Linux").equals("Linux"));
        PriceCatalog.Builder builder = PriceCatalog.builder();
        AwsPrices.parseEc2(offer, "eu-west-1", builder);
        PriceCatalog out = builder.build();
        assertEquals(1, out.vms.size());
        assertEquals(0.096, out.vms.get(0).hourly, 1e-9);
        assertEquals(8, out.vms.get(0).memGb, 1e-9);
        assertEquals(0.08, out.storage.get(0).gbMonth, 1e-9);
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
    public void testAzureReservationsAreAllUpfrontPerTerm() throws IOException {
        PriceCatalog.Builder builder = PriceCatalog.builder()
                .addVm(new PriceCatalog.VmOffer("Azure", "westeurope", "Standard_D2s_v5", 2, 8, 0.1));
        JsonNode items = MAPPER.readTree("[" +
                "{\"reservationTerm\":\"1 Year\",\"retailPrice\":876.0,\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series\",\"unitOfMeasure\":\"1 Hour\",\"type\":\"Reservation\"}," +
                "{\"reservationTerm\":\"3 Years\",\"retailPrice\":1752.0,\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series\",\"unitOfMeasure\":\"1 Hour\",\"type\":\"Reservation\"}," +
                "{\"reservationTerm\":\"5 Years\",\"retailPrice\":1,\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"x\"}," +
                "{\"reservationTerm\":\"1 Year\",\"retailPrice\":5,\"armSkuName\":\"Other\",\"productName\":\"x\"}]");
        AzurePrices.parseReservations(toArray(items), "westeurope", builder);
        List<ReservationOption> r = builder.build().vms.get(0).reservations;
        assertEquals(2, r.size());
        assertEquals(1, r.get(0).termYears);
        assertEquals(ReservationOption.Payment.ALL_UPFRONT, r.get(0).payment);
        assertEquals(0.1, r.get(0).effectiveHourly, 1e-9);
        assertEquals(3, r.get(1).termYears);
        assertEquals(1752.0 / (3 * 8760), r.get(1).effectiveHourly, 1e-9);
    }

    @Test
    public void testAwsReservedTermsKeepOnlyStandardNoUpfrontAndAllUpfront() throws IOException {
        String attrs = "\"attributes\":{\"instanceType\":\"m5.large\",\"vcpu\":\"2\",\"memory\":\"8 GiB\",\"operatingSystem\":\"Linux\",\"tenancy\":\"Shared\",\"preInstalledSw\":\"NA\",\"capacitystatus\":\"Used\",\"currentGeneration\":\"Yes\"}";
        String json = "{\"products\":{\"S1\":{\"sku\":\"S1\",\"productFamily\":\"Compute Instance\"," + attrs + "},"
                + "\"S9\":{\"sku\":\"S9\",\"productFamily\":\"Compute Instance\",\"attributes\":{\"operatingSystem\":\"Windows\"}}},"
                + "\"terms\":{\"OnDemand\":{\"S1\":{\"S1.T\":{\"priceDimensions\":{\"S1.T.R\":{\"unit\":\"Hrs\",\"pricePerUnit\":{\"USD\":\"0.096\"}}}}}},"
                + "\"Reserved\":{\"S1\":{"
                + term("A", "1yr", "No Upfront", "standard", "0.060", null)
                + "," + term("B", "1yr", "All Upfront", "standard", "0.000", "525.6")
                + "," + term("C", "3yr", "All Upfront", "standard", "0.000", "788.4")
                + "," + term("D", "3yr", "No Upfront", "standard", "0.041", null)
                + "," + term("E", "1yr", "Partial Upfront", "standard", "0.03", "260")
                + "," + term("F", "1yr", "No Upfront", "convertible", "0.07", null)
                + "},\"S9\":{" + term("G", "1yr", "No Upfront", "standard", "0.1", null) + "}}}}";
        Map<JsonNode, List<ReservationOption>> reserved = new HashMap<>();
        Map<JsonNode, Double> offer = AwsPrices.readOffer(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                p -> "Linux".equals(p.path("attributes").path("operatingSystem").asText("Linux"))
                        && p.path("attributes").has("instanceType"), reserved);
        PriceCatalog.Builder builder = PriceCatalog.builder();
        AwsPrices.parseEc2(offer, reserved, "eu-west-1", builder);
        PriceCatalog out = builder.build();
        assertEquals(1, out.vms.size());
        List<ReservationOption> r = out.vms.get(0).reservations;
        assertEquals(4, r.size());
        assertEquals(1, r.get(0).termYears);
        assertEquals(ReservationOption.Payment.NO_UPFRONT, r.get(0).payment);
        assertEquals(0.06, r.get(0).effectiveHourly, 1e-9);
        assertEquals(ReservationOption.Payment.ALL_UPFRONT, r.get(1).payment);
        assertEquals(525.6 / 8760, r.get(1).effectiveHourly, 1e-9);
        assertEquals(3, r.get(2).termYears);
        assertEquals(788.4 / (3 * 8760), r.get(2).effectiveHourly, 1e-9);
        assertEquals(0.041, r.get(3).effectiveHourly, 1e-9);
        assertEquals(0.096, out.vms.get(0).hourly, 1e-9);
    }

    /** A Reserved term as in the real AWS file; the upfront fee (unit Quantity) is added when not null. */
    private static String term(String code, String lease, String purchase, String cls, String hourly, String fee) {
        String dims = "\"S1." + code + ".H\":{\"unit\":\"Hrs\",\"pricePerUnit\":{\"USD\":\"" + hourly + "\"}}";
        if (fee != null) {
            dims += ",\"S1." + code + ".Q\":{\"unit\":\"Quantity\",\"description\":\"Upfront Fee\",\"pricePerUnit\":{\"USD\":\"" + fee + "\"}}";
        }
        return "\"S1." + code + "\":{\"offerTermCode\":\"" + code + "\",\"sku\":\"S1\",\"priceDimensions\":{" + dims
                + "},\"termAttributes\":{\"LeaseContractLength\":\"" + lease + "\",\"PurchaseOption\":\"" + purchase
                + "\",\"OfferingClass\":\"" + cls + "\"}}";
    }

    @Test
    public void testCacheWithoutOrWithAnotherSchemaVersionIsRefetched() throws IOException {
        File old = new File(tmp.getRoot(), "old.json");
        Files.write(old.toPath(), ("{\"fetchedAtEpochMs\":" + System.currentTimeMillis()
                + ",\"regionsKey\":\"azure=;aws=\",\"vms\":[],\"containers\":[],\"storage\":[],\"warnings\":[]}")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(0, PriceCatalog.load(old).schemaVersion);
        try {
            new PricingFetcher().fetchOrLoad("", "", old, 3600_000L); // old cache ignored, nothing to fetch
            org.junit.Assert.fail("the cache without version must not be used");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("No prices"));
        }
        File cache = new File(tmp.getRoot(), "v.json");
        PriceCatalog current = PriceCatalog.builder().addVm(vm("P", "x", 1, 1, 1)
                .withReservations(Arrays.asList(ReservationOption.allUpfront(1, 8760)))).build();
        current.save(cache);
        PriceCatalog loaded = PriceCatalog.load(cache);
        assertEquals(PriceCatalog.SCHEMA_VERSION, loaded.schemaVersion);
        assertEquals(1.0, loaded.vms.get(0).reservations.get(0).effectiveHourly, 1e-9);
    }

    @Test
    public void testFreshCacheAvoidsTheNetwork() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder().fetchedAt(System.currentTimeMillis()).regionsKey("azure=x;aws=y")
                .addVm(vm("P", "cached", 1, 1, 1)).build();
        File cache = new File(tmp.getRoot(), "prices.json");
        catalog.save(cache);
        PriceCatalog loaded = new PricingFetcher().fetchOrLoad("x", "y", cache, 3600_000L);
        assertEquals("cached", loaded.vms.get(0).name);
    }

    @Test
    public void testPriceSourceUsesTheCacheForTheSameRegions() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder().fetchedAt(System.currentTimeMillis())
                .regionsKey("azure=westeurope;aws=eu-west-1").addVm(vm("P", "cached", 1, 1, 1)).build();
        File cache = new File(tmp.getRoot(), "source-prices.json");
        catalog.save(cache);
        PriceCatalog loaded = new PriceSource("westeurope", "eu-west-1", cache.getPath(), 1).load();
        assertEquals("cached", loaded.vms.get(0).name);
    }

    @Test
    public void testRegionsAreCommaSeparatedAndBlankDisablesAProvider() {
        assertEquals(java.util.Arrays.asList("westeurope", "northeurope"), PricingFetcher.split(" westeurope , northeurope,"));
        assertTrue(PricingFetcher.split("").isEmpty());
        assertTrue(PricingFetcher.split(null).isEmpty());
    }

    private static final String[] SCRIPTS = {"i18n", "format", "theme", "charts", "graph", "simulator", "app"};

    private static String html(Path out) throws IOException {
        return new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
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

    private static ExecutionMeasurements containers() throws IOException {
        return ExecutionMeasurements.read(Paths.get(DATA + "usage-containers.csv"));
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
        Map<String, List<ContainerUsage>> usage = containers().containerUsage();
        assertEquals(set("tjoba", "tjobb"), usage.keySet());
        List<ContainerUsage> a = usage.get("tjoba");
        assertEquals(2, a.size());
        ContainerUsage db = a.get(0);
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
        ContainerUsage web = a.get(1);
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
        for (String placeholder : new String[]{"/*CSS*/", "/*DATA*/", "/*ECHARTS*/", "/*APP*/", "<!--HEADER_LOGO_SVG-->",
                "<!--FOOTER_SVG-->"}) {
            assertFalse(placeholder, content.contains(placeholder));
        }
        assertTrue(content.contains("app.title"));
        assertTrue(content.contains("<svg"));
        assertTrue(content.contains("https://giis.uniovi.es/"));
        assertFalse(content.contains("src=\"http"));
        assertTrue(embeddedJson(content).get("graph").get("nodes").size() == 3);
        int previous = -1;
        for (String script : SCRIPTS) {
            String source = new String(Files.readAllBytes(Paths.get("src/main/resources/usageprofile/js/" + script + ".js")),
                    StandardCharsets.UTF_8).trim().replace("</script", "<\\/script");
            int at = content.indexOf(source);
            assertTrue(script + ".js is not inlined", at > previous);
            previous = at;
        }
        assertEquals(SCRIPTS.length, UsageProfileGenerator.APP_SCRIPTS.size());
        assertTrue(UsageProfileGenerator.APP_SCRIPTS.get(0).endsWith("js/i18n.js"));
        assertTrue(UsageProfileGenerator.APP_SCRIPTS.get(SCRIPTS.length - 1).endsWith("js/app.js"));
        assertTrue(UsageProfileGenerator.APP_SCRIPTS.get(5).endsWith("js/simulator.js"));
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
    private static String deviceFarmProduct(String sku, String usage, String price) {
        return "\"" + sku + "\":{\"sku\":\"" + sku + "\",\"attributes\":{\"usagetype\":\"" + usage
                + "\",\"regionCode\":\"us-west-2\"}}|\"" + sku + "\":{\"T\":{\"priceDimensions\":{\"D\":{\"pricePerUnit\":{\"USD\":\""
                + price + "\"}}}}}";
    }

    @Test
    public void testDeviceFarmPricesOnlyMeteredBrowsersAndDevices() throws IOException {
        String[][] rows = {{"A", "USW2-Windows-Chrome-automated-minute", "0.005"}, {"B", "USW2-Windows-IE-automated-minute", "0.005"},
            {"C", "USW2-Android-Device-Minute", "0.17"}, {"D", "USW2-Android-Device-Minute-Trial", "0.0"},
            {"E", "USW2-Android-Unmetered", "0.0"}, {"F", "USW2-iOS-Device-Slot-Month", "250.0"}};
        StringBuilder products = new StringBuilder();
        StringBuilder terms = new StringBuilder();
        for (String[] r : rows) {
            String[] parts = deviceFarmProduct(r[0], r[1], r[2]).split("[|]");
            products.append(products.length() == 0 ? "" : ",").append(parts[0]);
            terms.append(terms.length() == 0 ? "" : ",").append(parts[1]);
        }
        JsonNode offer = MAPPER.readTree("{\"products\":{" + products + "},\"terms\":{\"OnDemand\":{" + terms + "}}}");
        PriceCatalog.Builder builder = PriceCatalog.builder();
        DeviceFarmPrices.parse(offer, builder);
        PriceCatalog catalog = builder.build();

        assertEquals(2, catalog.browsers.size());
        assertEquals("Chrome", catalog.browsers.get(0).name);
        assertEquals(0.005, catalog.browsers.get(0).minuteUsd, 1e-9);
        assertEquals("Android devices", catalog.browsers.get(1).name);
        assertEquals("AWS Device Farm", catalog.browsers.get(1).service);
        assertEquals("us-west-2", catalog.browsers.get(1).region);
    }

    @Test
    public void testBrowserServiceAddsBrowserMinutesToTheCheapestAlternative() throws IOException {
        PriceCatalog catalog = smallCatalogBuilder()
                .addBrowser(new PriceCatalog.BrowserRate("AWS", "us-west-2", "AWS Device Farm", "Chrome", 0.6)).build();
        CostReport plain = new CostCalculator().calculate(timeline(), catalog);
        Alternative cheapest = plain.alternatives.get(0);

        CostReport report = new CostCalculator().calculate(timeline(), catalog, set("TJOBA"));
        Alternative service = null;
        for (Alternative a : report.alternatives) {
            if (Alternative.KIND_BROWSER_SERVICE.equals(a.kind)) {
                service = a;
            }
        }
        assertNotNull(service);
        assertEquals("AWS Device Farm", service.provider);
        assertTrue(service.label.startsWith("Chrome + "));
        double browser = 0;
        for (CostItem item : service.items) {
            if ("browser".equals(item.object)) {
                assertEquals("tjoba", item.tjob);
                assertEquals("tjob-testexec", item.phase);
                browser += item.cost;
            }
        }
        // tjoba runs its tests during 40 s, at 0.6 USD per minute
        assertEquals(0.4, browser, 0.03);
        assertEquals(cheapest.total + browser, service.total, 1e-9);
        assertEquals(plain.alternatives.size() + 1, report.alternatives.size());

        CostReport noBrowsers = new CostCalculator().calculate(timeline(), catalog, new HashSet<String>());
        assertEquals(plain.alternatives.size(), noBrowsers.alternatives.size());
        assertTrue(noBrowsers.warnings.toString().contains("Browser services not calculated"));
    }
}
