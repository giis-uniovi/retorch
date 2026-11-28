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

    private final String azureRegions;
    private final String awsRegions;
    private final File cacheFile;
    private final int maxAgeHours;

    /**
     * @param azureRegions Azure regions, comma separated; blank = no Azure
     * @param awsRegions   AWS regions, comma separated; blank = no AWS
     * @param cacheFile    JSON file where the prices are cached
     * @param maxAgeHours  hours before the cached prices are downloaded again
     */
    public PriceSource(String azureRegions, String awsRegions, String cacheFile, int maxAgeHours) {
        this.azureRegions = azureRegions;
        this.awsRegions = awsRegions;
        this.cacheFile = new File(cacheFile);
        this.maxAgeHours = maxAgeHours;
    }

    /** The prices of the configured regions, from the cache when it is fresh and was built for the same regions. */
    public PriceCatalog load() throws IOException {
        return new PricingFetcher().fetchOrLoad(azureRegions, awsRegions, cacheFile, maxAgeHours * MILLIS_PER_HOUR);
    }
}
