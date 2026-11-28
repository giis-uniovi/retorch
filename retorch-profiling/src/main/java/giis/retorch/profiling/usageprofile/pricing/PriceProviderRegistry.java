/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The price providers known to the fetcher, in the order they are queried (and their warnings reported). It is the only
 * place that knows the concrete providers: to add one, register it in {@link #standard(HttpGet)}.
 */
public final class PriceProviderRegistry {

    private final List<PriceProvider> providers = new ArrayList<>();

    /** The providers shipped with RETORCH: Azure, AWS and AWS Device Farm. */
    public static PriceProviderRegistry standard(HttpGet http) {
        return new PriceProviderRegistry()
                .register(new AzurePrices(http))
                .register(new AwsPrices(http))
                .register(new DeviceFarmPrices(http));
    }

    /** Adds a provider; its id must be unique. Returns this, to chain. */
    public PriceProviderRegistry register(PriceProvider provider) {
        for (PriceProvider p : providers) {
            if (p.id().equals(provider.id())) {
                throw new IllegalArgumentException("Price provider id already registered: " + provider.id());
            }
        }
        providers.add(provider);
        return this;
    }

    /** The registered providers, in registration order. */
    public List<PriceProvider> providers() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * The cache key of a region configuration: {@code azure=a,b;aws=c}, one entry per region setting
     * ({@link PriceProvider#configId()}) in registration order, so a provider that is added or whose regions change
     * invalidates the cache.
     */
    public String regionsKey(ProviderRegions config) {
        Set<String> ids = new LinkedHashSet<>();
        for (PriceProvider p : providers) {
            ids.add(p.configId());
        }
        List<String> parts = new ArrayList<>();
        for (String id : ids) {
            parts.add(id + "=" + String.join(",", config.regions(id)));
        }
        return String.join(";", parts);
    }
}
