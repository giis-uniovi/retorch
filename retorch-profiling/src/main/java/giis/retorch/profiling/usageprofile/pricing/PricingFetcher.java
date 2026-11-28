/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link PriceCatalog} from the public price lists of the cloud providers (no credentials required), through
 * the {@link PriceProvider}s of a {@link PriceProviderRegistry}. It knows no concrete provider. The catalog is cached on
 * disk by {@link PriceCache}, because the AWS EC2 file weighs hundreds of MB. A provider that cannot be reached only adds
 * a warning. The HTTP access is injected ({@link HttpGet}) so the whole flow can be tested without network.
 */
public class PricingFetcher {

    private static final Logger log = LoggerFactory.getLogger(PricingFetcher.class);

    private final PriceProviderRegistry registry;

    /** With the real HTTP access. */
    public PricingFetcher() {
        this(HttpGet.standard());
    }

    /** With the standard providers over this HTTP access. */
    public PricingFetcher(HttpGet http) {
        this(PriceProviderRegistry.standard(http));
    }

    /** With the providers of a registry (e.g. fakes in tests, or the standard ones plus a new one). */
    public PricingFetcher(PriceProviderRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the cached catalog when it is younger than {@code maxAgeMs}; otherwise downloads the prices again and
     * refreshes the cache. If every provider fails but an old cache exists, the old cache is used with a warning.
     */
    public PriceCatalog fetchOrLoad(ProviderRegions regions, File cacheFile, long maxAgeMs) throws IOException {
        String regionsKey = registry.regionsKey(regions);
        PriceCache cache = new PriceCache(cacheFile);
        PriceCatalog cached = cache.load(regionsKey);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtEpochMs < maxAgeMs) {
            log.info("Using the cached prices from {}", cache);
            return cached;
        }
        PriceCatalog fresh = download(regions, regionsKey);
        if (!fresh.vms.isEmpty() || !fresh.containers.isEmpty()) {
            cache.save(fresh);
            return fresh;
        }
        if (cached == null) {
            throw new IOException("No prices could be obtained: " + fresh.warnings);
        }
        List<String> warnings = new ArrayList<>();
        warnings.add("Prices could not be refreshed, using the cache from " + cached.fetchedAtEpochMs);
        warnings.addAll(fresh.warnings);
        return cached.withWarnings(warnings);
    }

    /** The classic form with Azure and AWS regions (comma separated lists, blank = disabled). */
    public PriceCatalog fetchOrLoad(String azureRegions, String awsRegions, File cacheFile, long maxAgeMs) throws IOException {
        return fetchOrLoad(ProviderRegions.of(azureRegions, awsRegions), cacheFile, maxAgeMs);
    }

    private PriceCatalog download(ProviderRegions regions, String regionsKey) {
        PriceCatalog.Builder fresh = PriceCatalog.builder().fetchedAt(System.currentTimeMillis()).regionsKey(regionsKey);
        for (PriceProvider provider : registry.providers()) {
            fetchRegions(fresh, provider, provider.regionsFor(regions));
        }
        return fresh.build();
    }

    /** Fetches the prices of the regions of a provider; a region that fails only adds a warning to the target. */
    private static void fetchRegions(PriceCatalog.Builder target, PriceProvider provider, List<String> regions) {
        for (String region : regions) {
            try {
                target.merge(provider.fetch(region));
            } catch (IOException | RuntimeException e) {
                target.addWarning(provider.describe(region) + " not available: " + e.getMessage());
            }
        }
    }

    /** See {@link ProviderRegions}: "westeurope, northeurope" -> [westeurope, northeurope]; blanks are ignored. */
    static List<String> split(String regions) {
        return ProviderRegions.split(regions);
    }
}
