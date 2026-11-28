/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import java.io.File;
import java.io.IOException;

/**
 * Where the prices of a usage profile come from: the cloud regions to compare and the local cache of the downloaded
 * prices. Regions are comma separated lists (e.g. {@code westeurope,northeurope}); a blank list disables the provider.
 */
public class PriceSource {

    private static final long MILLIS_PER_HOUR = 3600_000L;

    private final ProviderRegions regions;
    private final File cacheFile;
    private final int maxAgeHours;
    private final PriceProviderRegistry registry;

    /**
     * @param azureRegions Azure regions, comma separated; blank = no Azure
     * @param awsRegions   AWS regions, comma separated; blank = no AWS
     * @param cacheFile    JSON file where the prices are cached
     * @param maxAgeHours  hours before the cached prices are downloaded again
     */
    public PriceSource(String azureRegions, String awsRegions, String cacheFile, int maxAgeHours) {
        this(ProviderRegions.of(azureRegions, awsRegions), new File(cacheFile), maxAgeHours, null);
    }

    private PriceSource(ProviderRegions regions, File cacheFile, int maxAgeHours, PriceProviderRegistry registry) {
        this.regions = regions;
        this.cacheFile = cacheFile;
        this.maxAgeHours = maxAgeHours;
        this.registry = registry;
    }

    /** A generic source: {@code PriceSource.builder().regions("azure", "westeurope").regions("gcp", "...").build()}. */
    public static Builder builder() {
        return new Builder();
    }

    /** The prices of the configured regions, from the cache when it is fresh and was built for the same regions. */
    public PriceCatalog load() throws IOException {
        PricingFetcher fetcher = registry == null ? new PricingFetcher() : new PricingFetcher(registry);
        return fetcher.fetchOrLoad(regions, cacheFile, maxAgeHours * MILLIS_PER_HOUR);
    }

    /** Builder of a {@link PriceSource} for any set of providers. */
    public static final class Builder {
        private ProviderRegions regions = ProviderRegions.none();
        private File cacheFile = new File("prices-cache.json");
        private int maxAgeHours = 24;
        private PriceProviderRegistry registry;

        /** The regions (comma separated; blank = disabled) of provider {@code providerId}. */
        public Builder regions(String providerId, String commaSeparatedRegions) {
            regions = regions.with(providerId, commaSeparatedRegions);
            return this;
        }

        public Builder cacheFile(String path) {
            this.cacheFile = new File(path);
            return this;
        }

        public Builder maxAgeHours(int hours) {
            this.maxAgeHours = hours;
            return this;
        }

        /** The providers to use instead of {@link PriceProviderRegistry#standard(HttpGet)}. */
        public Builder providers(PriceProviderRegistry providers) {
            this.registry = providers;
            return this;
        }

        public PriceSource build() {
            return new PriceSource(regions, cacheFile, maxAgeHours, registry);
        }
    }
}
