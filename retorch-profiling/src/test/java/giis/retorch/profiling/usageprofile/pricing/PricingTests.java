/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import giis.retorch.profiling.usageprofile.dto.ReservationOptionDTO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static giis.retorch.profiling.usageprofile.TestFixtures.*;

public class PricingTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final HttpGet NO_HTTP = url -> {
        throw new IOException("no network in tests");
    };

    @Test
    public void testAzureParsersIgnoreSpotAndWindows() throws IOException {
        JsonNode items = MAPPER.readTree("[" +
                "{\"skuName\":\"Standard_D2s_v5\",\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series\",\"unitOfMeasure\":\"1 Hour\",\"retailPrice\":0.115}," +
                "{\"skuName\":\"Standard_D2s_v5 Spot\",\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series\",\"unitOfMeasure\":\"1 Hour\",\"retailPrice\":0.02}," +
                "{\"skuName\":\"Standard_D2s_v5\",\"armSkuName\":\"Standard_D2s_v5\",\"productName\":\"Virtual Machines Dsv5 Series Windows\",\"unitOfMeasure\":\"1 Hour\",\"retailPrice\":0.2}]");
        PriceCatalog.Builder out = PriceCatalog.builder();
        new AzurePrices(NO_HTTP).parseVms(toArray(items), "westeurope", out);
        assertEquals(1, out.build().vms.size());
        assertEquals(0.115, out.build().vms.get(0).hourly, 1e-9);
        assertEquals(8, out.build().vms.get(0).memGb, 1e-9);

        JsonNode aci = MAPPER.readTree("[{\"meterName\":\"Standard vCPU Duration\",\"retailPrice\":0.0466}," +
                "{\"meterName\":\"Standard Memory Duration\",\"retailPrice\":0.00511},{\"meterName\":\"Standard Windows Software Duration\",\"retailPrice\":1}]");
        new AzurePrices(NO_HTTP).parseContainers(toArray(aci), "westeurope", out);
        assertEquals(0.0466, out.build().containers.get(0).vcpuHour, 1e-9);

        JsonNode disks = MAPPER.readTree("[{\"meterName\":\"E10 LRS Disk Mount\",\"unitOfMeasure\":\"1/Month\",\"retailPrice\":1.183}," +
                "{\"meterName\":\"E10 LRS Disk\",\"unitOfMeasure\":\"1/Month\",\"retailPrice\":9.6}]");
        new AzurePrices(NO_HTTP).parseDisks(toArray(disks), "westeurope", out);
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
        new AwsPrices(NO_HTTP).parseEc2(offer, "eu-west-1", builder);
        PriceCatalog out = builder.build();
        assertEquals(1, out.vms.size());
        assertEquals(0.096, out.vms.get(0).hourly, 1e-9);
        assertEquals(8, out.vms.get(0).memGb, 1e-9);
        assertEquals(0.08, out.storage.get(0).gbMonth, 1e-9);
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
        List<ReservationOptionDTO> r = builder.build().vms.get(0).reservations;
        assertEquals(2, r.size());
        assertEquals(1, r.get(0).termYears);
        assertEquals(ReservationOptionDTO.Payment.ALL_UPFRONT, r.get(0).payment);
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
        Map<JsonNode, List<ReservationOptionDTO>> reserved = new HashMap<>();
        Map<JsonNode, Double> offer = AwsPrices.readOffer(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                p -> "Linux".equals(p.path("attributes").path("operatingSystem").asText("Linux"))
                        && p.path("attributes").has("instanceType"), reserved);
        PriceCatalog.Builder builder = PriceCatalog.builder();
        new AwsPrices(NO_HTTP).parseEc2(offer, reserved, "eu-west-1", builder);
        PriceCatalog out = builder.build();
        assertEquals(1, out.vms.size());
        List<ReservationOptionDTO> r = out.vms.get(0).reservations;
        assertEquals(4, r.size());
        assertEquals(1, r.get(0).termYears);
        assertEquals(ReservationOptionDTO.Payment.NO_UPFRONT, r.get(0).payment);
        assertEquals(0.06, r.get(0).effectiveHourly, 1e-9);
        assertEquals(ReservationOptionDTO.Payment.ALL_UPFRONT, r.get(1).payment);
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
                .withReservations(Arrays.asList(ReservationOptionDTO.allUpfront(1, 8760)))).build();
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
        new DeviceFarmPrices(NO_HTTP).parse(offer, builder);
        PriceCatalog catalog = builder.build();

        assertEquals(2, catalog.browsers.size());
        assertEquals("Chrome", catalog.browsers.get(0).name);
        assertEquals(0.005, catalog.browsers.get(0).minuteUsd, 1e-9);
        assertEquals("Android devices", catalog.browsers.get(1).name);
        assertEquals("AWS Device Farm", catalog.browsers.get(1).service);
        assertEquals("us-west-2", catalog.browsers.get(1).region);
    }
}
