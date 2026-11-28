/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a {@link PriceCatalog} from the public price lists of the cloud providers (no credentials required), through
 * the {@link PriceProvider}s ({@link AzurePrices}, {@link AwsPrices}, {@link DeviceFarmPrices}). The catalog is cached on
 * disk by {@link PriceCache}, because the AWS EC2 file weighs hundreds of MB. A provider that cannot be reached only adds
 * a warning. The HTTP access is injected ({@link HttpGet}) so the whole flow can be tested without network.
 */
public class PricingFetcher {

    private static final Logger log = LoggerFactory.getLogger(PricingFetcher.class);

    private final PriceProvider azure;
    private final PriceProvider aws;
    private final PriceProvider deviceFarm;

    /** With the real HTTP access. */
    public PricingFetcher() {
        this(HttpGet.standard());
    }

    public PricingFetcher(HttpGet http) {
        this(new AzurePrices(http), new AwsPrices(http), new DeviceFarmPrices(http));
    }

    PricingFetcher(PriceProvider azure, PriceProvider aws, PriceProvider deviceFarm) {
        this.azure = azure;
        this.aws = aws;
        this.deviceFarm = deviceFarm;
    }

    /**
     * Returns the cached catalog when it is younger than {@code maxAgeMs}; otherwise downloads the prices again and
     * refreshes the cache. If every provider fails but an old cache exists, the old cache is used with a warning.
     */
    public PriceCatalog fetchOrLoad(String azureRegions, String awsRegions, File cacheFile, long maxAgeMs) throws IOException {
        String regionsKey = "azure=" + String.join(",", split(azureRegions)) + ";aws=" + String.join(",", split(awsRegions));
        PriceCache cache = new PriceCache(cacheFile);
        PriceCatalog cached = cache.load(regionsKey);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtEpochMs < maxAgeMs) {
            log.info("Using the cached prices from {}", cache);
            return cached;
        }
        PriceCatalog fresh = download(azureRegions, awsRegions, regionsKey);
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

    private PriceCatalog download(String azureRegions, String awsRegions, String regionsKey) {
        PriceCatalog.Builder fresh = PriceCatalog.builder().fetchedAt(System.currentTimeMillis()).regionsKey(regionsKey);
        fetchRegions(fresh, azure, split(azureRegions));
        fetchRegions(fresh, aws, split(awsRegions));
        if (!split(awsRegions).isEmpty()) {
            fetchRegions(fresh, deviceFarm, Collections.singletonList("global"));
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

    /** "westeurope, northeurope" -> [westeurope, northeurope]; blanks are ignored (a provider can be disabled). */
    static List<String> split(String regions) {
        List<String> result = new ArrayList<>();
        for (String r : regions == null ? new String[0] : regions.split(",")) {
            if (!r.trim().isEmpty()) {
                result.add(r.trim());
            }
        }
        return result;
    }
}
