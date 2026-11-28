/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import java.io.IOException;
import java.util.List;

/**
 * Strategy: a source of prices (a cloud provider or one of its services) that can be asked for one region. Providers
 * are registered in a {@link PriceProviderRegistry}; see {@link AbstractPriceProvider} for the shared plumbing.
 */
public interface PriceProvider {

    /** Short unique id ({@code azure}, {@code aws-devicefarm}): the key of its regions in {@link ProviderRegions}. */
    String id();

    /** Provider name used in the warnings. */
    String name();

    /** The id of the region setting this provider reads; a provider that follows another one's regions returns that id. */
    default String configId() {
        return id();
    }

    /**
     * An opt-in provider is part of the cache key only when it has regions, so enabling nothing leaves the key (and the
     * caches already written) as it was. Default: false.
     */
    default boolean optional() {
        return false;
    }

    /** The regions to fetch for this configuration; empty = the provider is disabled. */
    default List<String> regionsFor(ProviderRegions config) {
        return config.regions(configId());
    }

    /** The prices of one region; a failure is reported by the caller as a warning. */
    PriceCatalog fetch(String region) throws IOException;

    /** What the warning says could not be obtained for {@code region}. */
    default String describe(String region) {
        return name() + " prices for " + region;
    }
}
