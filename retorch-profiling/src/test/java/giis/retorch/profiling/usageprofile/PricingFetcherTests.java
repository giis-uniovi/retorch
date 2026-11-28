/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** {@code PricingFetcher.fetchOrLoad} with canned HTTP responses and fake providers: no network. */
public class PricingFetcherTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A provider that returns one VM named after the region, or fails. */
    private static final class FakeProvider implements PriceProvider {
        final boolean fails;
        final List<String> calls = new ArrayList<>();

        FakeProvider(boolean fails) {
            this.fails = fails;
        }

        @Override
        public String name() {
            return "Fake";
        }

        @Override
        public PriceCatalog fetch(String region) throws IOException {
            calls.add(region);
            if (fails) {
                throw new IOException("boom");
            }
            return PriceCatalog.builder().addVm(new PriceCatalog.VmOffer("Fake", region, "vm-" + region, 2, 4, 0.1)).build();
        }
    }

    private static PriceCatalog cached(long fetchedAt, String regionsKey) {
        return PriceCatalog.builder().fetchedAt(fetchedAt).regionsKey(regionsKey)
                .addVm(new PriceCatalog.VmOffer("Fake", "r", "cached", 1, 1, 1)).build();
    }

    @Test
    public void testFreshCacheIsUsedWithoutCallingTheProviders() throws IOException {
        File file = new File(tmp.getRoot(), "prices.json");
        cached(System.currentTimeMillis(), "azure=a;aws=").save(file);
        FakeProvider azure = new FakeProvider(false);
        PriceCatalog loaded = new PricingFetcher(azure, new FakeProvider(false), new FakeProvider(false))
                .fetchOrLoad("a", "", file, 3600_000L);
        assertEquals("cached", loaded.vms.get(0).name);
        assertTrue(azure.calls.isEmpty());
    }

    @Test
    public void testExpiredCacheIsRefetchedAndSaved() throws IOException {
        File file = new File(tmp.getRoot(), "prices.json");
        cached(System.currentTimeMillis() - 10_000L, "azure=a,b;aws=").save(file);
        FakeProvider azure = new FakeProvider(false);
        PriceCatalog fresh = new PricingFetcher(azure, new FakeProvider(false), new FakeProvider(false))
                .fetchOrLoad("a,b", "", file, 1_000L);
        assertEquals(2, fresh.vms.size());
        assertEquals("vm-a", fresh.vms.get(0).name);
        assertEquals(java.util.Arrays.asList("a", "b"), azure.calls);
        assertEquals("azure=a,b;aws=", fresh.regionsKey);
        assertEquals(2, PriceCatalog.load(file).vms.size());
    }

    @Test
    public void testCacheFromOtherRegionsIsIgnored() throws IOException {
        File file = new File(tmp.getRoot(), "prices.json");
        cached(System.currentTimeMillis(), "azure=other;aws=").save(file);
        PriceCatalog fresh = new PricingFetcher(new FakeProvider(false), new FakeProvider(false), new FakeProvider(false))
                .fetchOrLoad("a", "", file, 3600_000L);
        assertEquals("vm-a", fresh.vms.get(0).name);
    }

    @Test
    public void testAllProvidersFailingFallsBackToTheExpiredCacheWithWarnings() throws IOException {
        File file = new File(tmp.getRoot(), "prices.json");
        cached(System.currentTimeMillis() - 10_000L, "azure=a;aws=").save(file);
        PriceCatalog loaded = new PricingFetcher(new FakeProvider(true), new FakeProvider(true), new FakeProvider(true))
                .fetchOrLoad("a", "", file, 1_000L);
        assertEquals("cached", loaded.vms.get(0).name);
        assertEquals(2, loaded.warnings.size());
        assertTrue(loaded.warnings.get(0).startsWith("Prices could not be refreshed"));
        assertTrue(loaded.warnings.get(1).contains("Fake prices for a not available: boom"));
    }

    @Test
    public void testNothingAvailableThrows() throws IOException {
        File file = new File(tmp.getRoot(), "none.json");
        try {
            new PricingFetcher(new FakeProvider(true), new FakeProvider(true), new FakeProvider(true))
                    .fetchOrLoad("a", "", file, 1_000L);
            fail("no prices and no cache must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("No prices could be obtained"));
        }
    }

    @Test
    public void testOneFailingRegionOnlyAddsAWarning() throws IOException {
        File file = new File(tmp.getRoot(), "prices.json");
        PriceProvider flaky = new PriceProvider() {
            @Override
            public String name() {
                return "Flaky";
            }

            @Override
            public PriceCatalog fetch(String region) throws IOException {
                if ("bad".equals(region)) {
                    throw new IOException("down");
                }
                return PriceCatalog.builder().addVm(new PriceCatalog.VmOffer("Flaky", region, "ok", 1, 1, 1)).build();
            }
        };
        PriceCatalog fresh = new PricingFetcher(flaky, new FakeProvider(false), new FakeProvider(false))
                .fetchOrLoad("good,bad", "", file, 1_000L);
        assertEquals(1, fresh.vms.size());
        assertEquals(1, fresh.warnings.size());
        assertTrue(fresh.warnings.get(0).contains("Flaky prices for bad not available: down"));
    }

    @Test
    public void testDeviceFarmIsFetchedOnceAndOnlyWhenAwsIsEnabled() throws IOException {
        FakeProvider deviceFarm = new FakeProvider(false);
        new PricingFetcher(new FakeProvider(false), new FakeProvider(false), deviceFarm)
                .fetchOrLoad("a", "", new File(tmp.getRoot(), "a.json"), 1_000L);
        assertTrue(deviceFarm.calls.isEmpty());
        new PricingFetcher(new FakeProvider(false), new FakeProvider(false), deviceFarm)
                .fetchOrLoad("", "r1,r2", new File(tmp.getRoot(), "b.json"), 1_000L);
        assertEquals(1, deviceFarm.calls.size());
    }

    @Test
    public void testAzureWithCannedHttpResponses() throws IOException {
        List<String> urls = new ArrayList<>();
        HttpGet http = url -> {
            urls.add(url);
            String body;
            if (url.contains("Container%20Instances")) {
                body = "{\"Items\":[{\"meterName\":\"Standard vCPU Duration\",\"retailPrice\":0.04},"
                        + "{\"meterName\":\"Standard Memory Duration\",\"retailPrice\":0.005}]}";
            } else if (url.contains("Reservation")) {
                body = "{\"Items\":[{\"reservationTerm\":\"1 Year\",\"retailPrice\":876.0,\"armSkuName\":\"Standard_D2s_v5\","
                        + "\"productName\":\"Virtual Machines Dsv5 Series\"}]}";
            } else if (url.contains("Storage")) {
                body = "{\"Items\":[{\"meterName\":\"E10 LRS Disk\",\"unitOfMeasure\":\"1/Month\",\"retailPrice\":9.6}]}";
            } else {
                body = "{\"Items\":[{\"skuName\":\"Standard_D2s_v5\",\"armSkuName\":\"Standard_D2s_v5\","
                        + "\"productName\":\"Virtual Machines Dsv5 Series\",\"unitOfMeasure\":\"1 Hour\",\"retailPrice\":0.1}]}";
            }
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        };
        PriceCatalog azure = new AzurePrices(http).fetch("westeurope");
        assertEquals(4, urls.size());
        assertEquals(1, azure.vms.size());
        assertEquals("Standard_D2s_v5", azure.vms.get(0).name);
        assertEquals(1, azure.vms.get(0).reservations.size());
        assertEquals(0.1, azure.vms.get(0).reservations.get(0).effectiveHourly, 1e-9);
        assertEquals(0.04, azure.containers.get(0).vcpuHour, 1e-9);
        assertEquals(0.075, azure.storage.get(0).gbMonth, 1e-9);
    }

    @Test
    public void testHttpFailureBecomesAWarningThroughTheRealProviders() throws IOException {
        HttpGet down = url -> {
            throw new IOException("HTTP 503 from " + url);
        };
        File file = new File(tmp.getRoot(), "down.json");
        try {
            new PricingFetcher(down).fetchOrLoad("westeurope", "eu-west-1", file, 1_000L);
            fail("without prices and cache it must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Azure prices for westeurope not available"));
            assertTrue(expected.getMessage().contains("AWS prices for eu-west-1 not available"));
            assertTrue(expected.getMessage().contains("AWS Device Farm prices not available"));
        }
    }
}
