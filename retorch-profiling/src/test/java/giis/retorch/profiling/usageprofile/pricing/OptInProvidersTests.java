/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The opt-in providers Oracle Cloud and IBM Cloud with canned responses trimmed from the real public APIs (no network).
 */
public class OptInProvidersTests {

    private static final double EPS = 1e-9;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RES = "/pricing/";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final HttpGet DOWN = url -> {
        throw new IOException("down");
    };

    private static InputStream resource(String name) throws IOException {
        InputStream in = OptInProvidersTests.class.getResourceAsStream(RES + name);
        if (in == null) {
            throw new FileNotFoundException(name);
        }
        return in;
    }

    private static InputStream text(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static PriceCatalog.VmOffer vm(PriceCatalog catalog, String name) {
        for (PriceCatalog.VmOffer v : catalog.vms) {
            if (v.name.equals(name)) {
                return v;
            }
        }
        throw new AssertionError("no VM " + name + " in " + catalog.vms.size() + " VMs");
    }

    // ---- Oracle

    private static HttpGet oracleHttp(String file) {
        return url -> {
            assertEquals(OracleCloudPrices.URL, url);
            return resource(file);
        };
    }

    @Test
    public void testOracleShapesAreFlexibleAndTheCheapestFamilyWins() throws IOException {
        PriceCatalog catalog = new OracleCloudPrices(oracleHttp("oracle-products.json")).fetch("global");

        assertEquals(12, catalog.vms.size()); // 6 OCPU counts x (8 GB and 16 GB per OCPU)
        assertTrue(catalog.vms.toString(), catalog.warnings.isEmpty());
        // E3 and E4 tie (0.025 + 0.0015/GB) and are cheaper than E5 (0.03 + 0.002/GB): the first one wins
        PriceCatalog.VmOffer small = vm(catalog, "VM.Flex.E3.1ocpu-8gb");
        assertEquals("Oracle Cloud", small.provider);
        assertEquals("global", small.region);
        assertEquals(2, small.vcpu, EPS);                 // 1 OCPU = 2 vCPU
        assertEquals(8, small.memGb, EPS);
        assertEquals(0.025 + 8 * 0.0015, small.hourly, EPS);
        PriceCatalog.VmOffer big = vm(catalog, "VM.Flex.E3.32ocpu-512gb");
        assertEquals(64, big.vcpu, EPS);
        assertEquals(512, big.memGb, EPS);
        assertEquals(32 * 0.025 + 512 * 0.0015, big.hourly, EPS);
        assertTrue(small.reservations.isEmpty());
        assertTrue(catalog.containers.isEmpty());
    }

    @Test
    public void testOracleBlockVolumeIsTheStorageRate() throws IOException {
        PriceCatalog catalog = new OracleCloudPrices(oracleHttp("oracle-products.json")).fetch("global");
        assertEquals(1, catalog.storage.size());
        assertEquals("Oracle Cloud", catalog.storage.get(0).provider);
        assertEquals(0.0255, catalog.storage.get(0).gbMonth, EPS);
    }

    @Test
    public void testOracleIgnoresArmCloudAtCustomerOptimizedAndIncompleteFamilies() throws IOException {
        PriceCatalog catalog = new OracleCloudPrices(oracleHttp("oracle-products.json")).fetch("global");
        for (PriceCatalog.VmOffer v : catalog.vms) {
            // A1 (arm, free tier first), E5 Cloud@Customer, E6 Ax, Optimized X9 and X9 without memory item never win
            assertTrue(v.name, v.name.matches("VM\\.Flex\\.E[345]\\..*"));
        }
    }

    @Test
    public void testOracleMissingComputeItemsAreAWarning() throws IOException {
        String noCompute = "{\"items\":[{\"displayName\":\"Storage - Block Volume - Storage\","
                + "\"metricName\":\"Gigabyte Storage Capacity Per Month\",\"currencyCodeLocalizations\":"
                + "[{\"currencyCode\":\"USD\",\"prices\":[{\"model\":\"PAY_AS_YOU_GO\",\"value\":0.0255}]}]}]}";
        PriceProviderRegistry registry = new PriceProviderRegistry().register(new OracleCloudPrices(url -> text(noCompute)));
        try {
            new PricingFetcher(registry).fetchOrLoad(
                    ProviderRegions.none().with("oracle", "global"), new File(tmp.getRoot(), "o.json"), 1_000L);
            throw new AssertionError("nothing could be priced");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "Oracle Cloud prices for global not available: Oracle price list has no flexible compute family"));
        }
    }

    @Test
    public void testOracleWithoutBlockVolumeFailsWithItsName() throws IOException {
        JsonNode all = MAPPER.readTree(resource("oracle-products.json"));
        List<JsonNode> kept = new ArrayList<>();
        for (JsonNode item : all.path("items")) {
            if (!item.path("displayName").asText().startsWith("Storage")) {
                kept.add(item);
            }
        }
        String json = "{\"items\":" + MAPPER.writeValueAsString(kept) + "}";
        try {
            new OracleCloudPrices(url -> text(json)).fetch("global");
            throw new AssertionError("expected a failure");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Storage - Block Volume - Storage"));
        }
    }

    // ---- IBM

    /** Catalog responses: plans (2 pages) and volume plans from the fixtures; deployments and prices built from them. */
    private static final class IbmCatalogHttp implements HttpGet {
        final List<String> urls = new ArrayList<>();
        final Map<String, String> planNames = new HashMap<>();
        /** Plan name -> regions where it has a deployment (others: only eu-de and us-south). */
        final Map<String, List<String>> deployedIn = new HashMap<>();

        IbmCatalogHttp() throws IOException {
            for (String f : new String[]{"ibm-instance-plans-1.json", "ibm-instance-plans-2.json", "ibm-volume-plans.json"}) {
                for (JsonNode plan : MAPPER.readTree(resource(f)).path("resources")) {
                    planNames.put(plan.path("id").asText(), plan.path("name").asText());
                }
            }
        }

        @Override
        public InputStream open(String url) throws IOException {
            urls.add(url);
            String path = url.substring(IbmCloudPrices.CATALOG.length());
            if (path.equals("is.instance/plan?languages=en")) {
                return resource("ibm-instance-plans-1.json");
            } else if (path.startsWith("is.instance/plan?") && path.contains("_offset=50")) {
                return resource("ibm-instance-plans-2.json");
            } else if (path.equals("is.volume/plan?languages=en")) {
                return resource("ibm-volume-plans.json");
            } else if (path.endsWith("/deployment?languages=en")) {
                return deployments(path.substring(0, path.indexOf('/')));
            } else if (path.endsWith("/pricing")) {
                return pricing(path.substring(0, path.indexOf('/')));
            }
            throw new FileNotFoundException(url);
        }

        private InputStream deployments(String planId) {
            String name = planNames.get(planId);
            List<String> regions = deployedIn.containsKey(name) ? deployedIn.get(name)
                    : java.util.Arrays.asList("eu-de", "us-south");
            StringBuilder sb = new StringBuilder("{\"resources\":[");
            for (String r : regions) {
                sb.append(sb.charAt(sb.length() - 1) == '[' ? "" : ",")
                        .append("{\"id\":\"").append(planId).append(':').append(r).append("\",\"geo_tags\":[\"")
                        .append(r).append("\"]}");
            }
            return text(sb.append("]}").toString());
        }

        private InputStream pricing(String deploymentId) throws IOException {
            String planId = deploymentId.substring(0, deploymentId.indexOf(':'));
            String region = deploymentId.substring(deploymentId.indexOf(':') + 1);
            String name = planNames.get(planId);
            if ("gen2-volume-general-purpose".equals(name)) {
                return resource("ibm-pricing-volume-eu-de.json");
            }
            if ("bxf-4x16".equals(name) && "eu-de".equals(region)) {
                return resource("ibm-pricing-bxf-4x16-eu-de.json");
            }
            int vcpu = Integer.parseInt(name.substring(name.indexOf('-') + 1, name.lastIndexOf('x')));
            return text("{\"metrics\":[{\"metric_id\":\"part-is.instance-hours-" + name + "\",\"charge_unit\":\"Instance-Hour\","
                    + "\"amounts\":[{\"country\":\"USA\",\"currency\":\"USD\",\"prices\":[{\"quantity_tier\":1,\"price\":"
                    + vcpu * 0.05 + "}]}]}]}");
        }
    }

    @Test
    public void testIbmProfilesAndPricesFromTheCatalog() throws IOException {
        IbmCatalogHttp http = new IbmCatalogHttp();
        PriceCatalog catalog = new IbmCloudPrices(http).fetch("eu-de");

        // bxf-2x8, bxf-4x16, cxf-4x8, mxf-4x32; not bx4-4x16 (not flex) nor bxf-64x256 (too big); bxf-4x16 listed twice
        assertEquals(4, catalog.vms.size());
        assertTrue(catalog.warnings.toString(), catalog.warnings.isEmpty());
        PriceCatalog.VmOffer b = vm(catalog, "bxf-4x16");
        assertEquals("IBM Cloud", b.provider);
        assertEquals("eu-de", b.region);
        assertEquals(4, b.vcpu, EPS);
        assertEquals(16, b.memGb, EPS);
        assertEquals(0.223, b.hourly, EPS);               // real eu-de Instance-Hour price; the dh- (dedicated) metric is 0
        assertEquals(32, vm(catalog, "mxf-4x32").memGb, EPS);
        assertEquals(0.1, vm(catalog, "bxf-2x8").hourly, EPS);   // generated: 0.05 per vCPU
        assertTrue(b.reservations.isEmpty());
        assertTrue(catalog.containers.isEmpty());
        // volume: GB-hour * 730
        assertEquals(1, catalog.storage.size());
        assertEquals(0.00013949 * 730, catalog.storage.get(0).gbMonth, EPS);
        assertEquals("IBM Cloud", catalog.storage.get(0).provider);
        for (String url : http.urls) {
            assertTrue(url, url.startsWith("https://globalcatalog.cloud.ibm.com/api/v1/"));
        }
    }

    @Test
    public void testIbmProfileWithoutDeploymentInTheRegionIsAWarning() throws IOException {
        IbmCatalogHttp http = new IbmCatalogHttp();
        http.deployedIn.put("cxf-4x8", java.util.Arrays.asList("eu-de"));
        PriceCatalog catalog = new IbmCloudPrices(http).fetch("us-south");
        assertEquals(3, catalog.vms.size());
        assertEquals(1, catalog.warnings.size());
        assertTrue(catalog.warnings.get(0), catalog.warnings.get(0)
                .contains("cxf-4x8 not priced in us-south: no deployment in us-south"));
    }

    @Test
    public void testIbmRegionWithoutPricedProfilesFailsWithAWarningInTheFetcher() throws IOException {
        IbmCatalogHttp http = new IbmCatalogHttp();
        PriceProviderRegistry registry = new PriceProviderRegistry().register(new IbmCloudPrices(http));
        PriceCatalog catalog = new PricingFetcher(registry).fetchOrLoad(
                ProviderRegions.none().with("ibm", "eu-de,mars-1"), new File(tmp.getRoot(), "i.json"), 1_000L);
        assertEquals(4, catalog.vms.size());
        assertTrue(catalog.warnings.toString(), catalog.warnings.toString()
                .contains("IBM Cloud prices for mars-1 not available: no priced bxf/cxf/mxf profile"));
    }

    // ---- wiring: opt-in, cache key, system properties

    @Test
    public void testProvidersAreRegisteredButDisabledWithoutRegions() throws IOException {
        List<String> calls = new ArrayList<>();
        HttpGet spy = url -> {
            calls.add(url);
            throw new IOException("down");
        };
        PriceProviderRegistry registry = PriceProviderRegistry.standard(spy);
        assertTrue(registry.providers().toString(), registry.providers().stream().anyMatch(p -> "oracle".equals(p.id())));
        assertTrue(registry.providers().stream().anyMatch(p -> "ibm".equals(p.id())));
        try {
            new PricingFetcher(registry).fetchOrLoad(ProviderRegions.of("", ""), new File(tmp.getRoot(), "x.json"), 1_000L);
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("[]")); // no provider asked, so no warnings
        }
        assertTrue(calls.toString(), calls.isEmpty());
    }

    @Test
    public void testNothingEnabledLeavesTheCacheKeyAsBefore() {
        PriceProviderRegistry registry = PriceProviderRegistry.standard(DOWN);
        assertEquals("azure=a;aws=", registry.regionsKey(ProviderRegions.of("a", null)));
        assertEquals("azure=a;aws=;oracle=global;ibm=us-south,eu-de",
                registry.regionsKey(ProviderRegions.of("a", "").with("oracle", "global").with("ibm", "us-south, eu-de")));
        assertEquals("azure=;aws=;ibm=eu-de", registry.regionsKey(ProviderRegions.none().with("ibm", "eu-de")));
    }

    @Test
    public void testPriceSourceReadsExtraRegionsFromSystemProperties() throws IOException {
        System.setProperty("usageprofile-regions-oracle", "global");
        System.setProperty("usageprofile-regions-ibm", " eu-de , us-south ");
        try {
            ProviderRegions regions = PriceSource.regionsFromSystemProperties(ProviderRegions.of("westeurope", "eu-west-1"),
                    PriceProviderRegistry.standard(DOWN));
            assertEquals(java.util.Arrays.asList("global"), regions.regions("oracle"));
            assertEquals(java.util.Arrays.asList("eu-de", "us-south"), regions.regions("ibm"));
            assertEquals(java.util.Arrays.asList("westeurope"), regions.regions("azure"));
            assertNotNull(PriceProviderRegistry.standard(DOWN).regionsKey(regions));
        } finally {
            System.clearProperty("usageprofile-regions-oracle");
            System.clearProperty("usageprofile-regions-ibm");
        }
        ProviderRegions none = PriceSource.regionsFromSystemProperties(ProviderRegions.of("a", "b"),
                PriceProviderRegistry.standard(DOWN));
        assertTrue(none.regions("oracle").isEmpty());
        assertTrue(none.regions("ibm").isEmpty());
    }
}
