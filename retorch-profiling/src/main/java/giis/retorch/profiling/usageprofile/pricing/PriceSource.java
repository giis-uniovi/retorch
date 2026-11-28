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

    /** System property prefix of the regions of an opt-in provider: {@code usageprofile-regions-<id>}. */
    static final String REGIONS_PROPERTY_PREFIX = "usageprofile-regions-";

    /** {@code base} plus, for each opt-in provider of the registry, the regions of its system property if it is not blank. */
    static ProviderRegions regionsFromSystemProperties(ProviderRegions base, PriceProviderRegistry registry) {
        ProviderRegions result = base;
        for (PriceProvider p : registry.providers()) {
            String value = p.optional() ? System.getProperty(REGIONS_PROPERTY_PREFIX + p.configId()) : null;
            if (value != null && !value.trim().isEmpty()) {
                result = result.with(p.configId(), value);
            }
        }
        return result;
    }

    /** Builder of a {@link PriceSource} for any set of providers. */
    public static final class Builder {
        private ProviderRegions regions = ProviderRegions.none();
        private File cacheFile = new File("prices-cache.json");
        private int maxAgeHours = 24;
        private PriceProviderRegistry registry;
        private boolean systemPropertyRegions;

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

        /**
         * Also reads, for every registered opt-in provider, its regions from the system property
         * {@code usageprofile-regions-<id>} (e.g. {@code -Dusageprofile-regions-ibm=us-south,eu-de}); a property that is
         * not set or blank leaves the provider as configured by {@link #regions}.
         */
        public Builder systemPropertyRegions() {
            this.systemPropertyRegions = true;
            return this;
        }

        public PriceSource build() {
            if (systemPropertyRegions) {
                PriceProviderRegistry used = registry == null ? PriceProviderRegistry.standard(HttpGet.standard()) : registry;
                return new PriceSource(regionsFromSystemProperties(regions, used), cacheFile, maxAgeHours, registry);
            }
            return new PriceSource(regions, cacheFile, maxAgeHours, registry);
        }
    }
}
