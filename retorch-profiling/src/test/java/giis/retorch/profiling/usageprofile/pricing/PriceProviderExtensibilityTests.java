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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Proves that a new provider is one small class plus a registration, and tests the helpers of
 * {@link AbstractPriceProvider}. No network: the standard providers get an HTTP that always fails.
 */
public class PriceProviderExtensibilityTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final HttpGet DOWN = url -> {
        throw new IOException("down");
    };

    /** The whole cost of a new provider: id, name and its parsing. Region "bad" fails. */
    private static final class FakeCloudPrices extends AbstractPriceProvider {
        final List<String> calls = new ArrayList<>();

        FakeCloudPrices(HttpGet http) {
            super(http, "fakecloud", "FakeCloud", "FakeCloud");
        }

        @Override
        protected void collect(String region, PriceCatalog.Builder out) throws IOException {
            calls.add(region);
            if ("bad".equals(region)) {
                throw new IOException("quota");
            }
            for (JsonNode item : pages("http://fake/prices?r=" + region, "items", "next")) {
                out.addVm(vm(region, item.path("name").asText(), 2, 8, item.path("usd").asDouble()));
            }
            out.addBrowser(browser(region, "FakeGrid", "Chrome", 0.01));
        }
    }

    /** Two pages of canned responses. */
    private static HttpGet fakeCloudHttp() {
        return url -> {
            String body = url.endsWith("page2")
                    ? "{\"items\":[{\"name\":\"small\",\"usd\":0.5}]}"
                    : "{\"items\":[{\"name\":\"tiny\",\"usd\":0.1}],\"next\":\"http://fake/page2\"}";
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        };
    }

    @Test
    public void testNewProviderContributesToTheCatalogAndTheOthersFailIndependently() throws IOException {
        FakeCloudPrices fake = new FakeCloudPrices(fakeCloudHttp());
        PriceProviderRegistry registry = PriceProviderRegistry.standard(DOWN).register(fake);
        ProviderRegions regions = ProviderRegions.of("westeurope", "eu-west-1").with("fakecloud", "r1,bad,r2");
        PriceCatalog catalog = new PricingFetcher(registry).fetchOrLoad(regions, new File(tmp.getRoot(), "p.json"), 1_000L);

        assertEquals(Arrays.asList("r1", "bad", "r2"), fake.calls);
        assertEquals(4, catalog.vms.size());
        assertEquals("FakeCloud", catalog.vms.get(0).provider);
        assertEquals("tiny", catalog.vms.get(0).name);
        assertEquals("small", catalog.vms.get(1).name);
        assertEquals(2, catalog.browsers.size());
        assertEquals("FakeGrid", catalog.browsers.get(0).service);
        assertTrue(catalog.warnings.toString(), catalog.warnings.contains("FakeCloud prices for bad not available: quota"));
        assertTrue(catalog.warnings.toString().contains("Azure prices for westeurope not available"));
        assertTrue(catalog.warnings.toString().contains("AWS prices for eu-west-1 not available"));
        assertEquals(4, catalog.warnings.size());
    }

    @Test
    public void testNewProviderIsPartOfTheCacheKey() {
        PriceProviderRegistry registry = PriceProviderRegistry.standard(DOWN).register(new FakeCloudPrices(DOWN));
        assertEquals("azure=a,b;aws=c;fakecloud=x",
                registry.regionsKey(ProviderRegions.of("a, b", "c").with("fakecloud", "x")));
        assertEquals("azure=a;aws=;fakecloud=", registry.regionsKey(ProviderRegions.of("a", "")));
        assertEquals("azure=a;aws=", PriceProviderRegistry.standard(DOWN).regionsKey(ProviderRegions.of("a", null)));
    }

    @Test
    public void testCacheOfOtherRegionsOfTheNewProviderIsIgnored() throws IOException {
        File file = new File(tmp.getRoot(), "p.json");
        FakeCloudPrices fake = new FakeCloudPrices(fakeCloudHttp());
        PricingFetcher fetcher = new PricingFetcher(new PriceProviderRegistry().register(fake));
        fetcher.fetchOrLoad(ProviderRegions.none().with("fakecloud", "r1"), file, 3600_000L);
        fetcher.fetchOrLoad(ProviderRegions.none().with("fakecloud", "r1"), file, 3600_000L);
        assertEquals(Arrays.asList("r1"), fake.calls);
        fetcher.fetchOrLoad(ProviderRegions.none().with("fakecloud", "r2"), file, 3600_000L);
        assertEquals(Arrays.asList("r1", "r2"), fake.calls);
    }

    @Test
    public void testProviderWithoutRegionsIsDisabled() throws IOException {
        FakeCloudPrices fake = new FakeCloudPrices(fakeCloudHttp());
        PriceProviderRegistry registry = new PriceProviderRegistry().register(fake)
                .register(new FakeCloudPricesOther());
        PriceCatalog catalog = new PricingFetcher(registry)
                .fetchOrLoad(ProviderRegions.none().with("fakecloud", " , ").with("other", "o1"), new File(tmp.getRoot(), "d.json"), 1_000L);
        assertTrue(fake.calls.isEmpty());
        assertEquals(1, catalog.vms.size());
        assertEquals("o1", catalog.vms.get(0).region);
    }

    /** A second tiny provider, to have something that works next to a disabled one. */
    private static final class FakeCloudPricesOther extends AbstractPriceProvider {
        FakeCloudPricesOther() {
            super(DOWN, "other", "Other", "Other");
        }

        @Override
        protected void collect(String region, PriceCatalog.Builder out) {
            out.addVm(vm(region, "o", 1, 1, 1));
        }
    }

    @Test
    public void testPriceSourceBuilderUsesAnyRegisteredProvider() throws IOException {
        FakeCloudPrices fake = new FakeCloudPrices(fakeCloudHttp());
        PriceCatalog catalog = PriceSource.builder().regions("fakecloud", "r1").cacheFile(new File(tmp.getRoot(), "s.json").getPath())
                .maxAgeHours(1).providers(new PriceProviderRegistry().register(fake)).build().load();
        assertEquals(2, catalog.vms.size());
        assertEquals("fakecloud=r1", catalog.regionsKey);
    }

    @Test
    public void testDuplicatedProviderIdIsRejected() {
        PriceProviderRegistry registry = PriceProviderRegistry.standard(DOWN);
        try {
            registry.register(new AzurePrices(DOWN));
            fail("an id can be registered once");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("azure"));
        }
    }

    @Test
    public void testDeviceFarmFollowsTheAwsRegions() {
        DeviceFarmPrices df = new DeviceFarmPrices(DOWN);
        assertEquals(Arrays.asList("global"), df.regionsFor(ProviderRegions.of("", "eu-west-1,us-east-1")));
        assertTrue(df.regionsFor(ProviderRegions.of("westeurope", "")).isEmpty());
        assertEquals("aws", df.configId());
    }

    // ---- shared helpers

    @Test
    public void testPagesFollowsTheNextLinkUntilItIsMissing() throws IOException {
        FakeCloudPrices fake = new FakeCloudPrices(fakeCloudHttp());
        List<JsonNode> items = fake.pages("http://fake/page1", "items", "next");
        assertEquals(2, items.size());
        assertEquals("tiny", items.get(0).path("name").asText());
        assertEquals("small", items.get(1).path("name").asText());
    }

    @Test
    public void testReadOfferStreamsOnlyTheAcceptedProducts() throws IOException {
        String json = "{\"products\":{\"A\":{\"sku\":\"A\",\"attributes\":{\"usagetype\":\"keep\"}},"
                + "\"B\":{\"sku\":\"B\",\"attributes\":{\"usagetype\":\"drop\"}}},\"terms\":{\"OnDemand\":{"
                + "\"A\":{\"A.T\":{\"priceDimensions\":{\"A.T.R\":{\"pricePerUnit\":{\"USD\":\"0.5\"}}}}},"
                + "\"B\":{\"B.T\":{\"priceDimensions\":{\"B.T.R\":{\"pricePerUnit\":{\"USD\":\"9\"}}}}}}}}";
        Map<JsonNode, Double> offer = AbstractPriceProvider.readOffer(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                p -> "keep".equals(AbstractPriceProvider.attr(p, AbstractPriceProvider.USAGE_TYPE)));
        assertEquals(1, offer.size());
        assertEquals(0.5, offer.values().iterator().next(), 1e-9);
    }

    @Test
    public void testSafeNumberParsing() {
        assertEquals(8, AbstractPriceProvider.number("8 GiB", -1), 1e-9);
        assertEquals(1024, AbstractPriceProvider.number("1,024 GiB", -1), 1e-9);
        assertEquals(2.5, AbstractPriceProvider.number(" 2.5 ", -1), 1e-9);
        assertEquals(-1, AbstractPriceProvider.number("NA", -1), 1e-9);
        assertEquals(-1, AbstractPriceProvider.number("", -1), 1e-9);
        assertEquals(-1, AbstractPriceProvider.number(null, -1), 1e-9);
    }

    @Test
    public void testReservationHelpers() {
        assertEquals(1, AbstractPriceProvider.termYears("1 Year"));
        assertEquals(3, AbstractPriceProvider.termYears("3 Years"));
        assertEquals(1, AbstractPriceProvider.termYears("1yr"));
        assertEquals(3, AbstractPriceProvider.termYears("3yr"));
        assertEquals(0, AbstractPriceProvider.termYears("5 Years"));
        assertEquals(0, AbstractPriceProvider.termYears("Monthly"));
        assertEquals(0, AbstractPriceProvider.termYears(null));

        List<ReservationOptionDTO> options = new ArrayList<>();
        AbstractPriceProvider.addCheapest(options, ReservationOptionDTO.allUpfront(3, 3000));
        AbstractPriceProvider.addCheapest(options, ReservationOptionDTO.allUpfront(1, 1000));
        AbstractPriceProvider.addCheapest(options, ReservationOptionDTO.allUpfront(1, 900));
        AbstractPriceProvider.addCheapest(options, ReservationOptionDTO.allUpfront(1, 950));
        AbstractPriceProvider.sortByTerm(options);
        assertEquals(2, options.size());
        assertEquals(1, options.get(0).termYears);
        assertEquals(900.0 / ReservationOptionDTO.HOURS_PER_YEAR, options.get(0).effectiveHourly, 1e-9);
        assertEquals(3, options.get(1).termYears);
    }

    @Test
    public void testFactoriesLabelTheEntriesWithTheCatalogProvider() {
        DeviceFarmPrices df = new DeviceFarmPrices(DOWN);
        PriceCatalog.BrowserRate rate = df.browser("us-west-2", "AWS Device Farm", "Chrome", 0.005);
        assertEquals("AWS", rate.provider);
        assertEquals("AWS Device Farm", df.name());
        assertEquals("aws-devicefarm", df.id());
    }
}
