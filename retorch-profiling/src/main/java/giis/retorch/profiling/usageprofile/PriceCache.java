/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/** The local JSON file where the fetched prices are kept. */
final class PriceCache {

    private static final Logger log = LoggerFactory.getLogger(PriceCache.class);
    private final File file;

    PriceCache(File file) {
        this.file = file;
    }

    /** The cached catalog, or null if there is none, it has another schema version or it was built for other regions. */
    PriceCatalog load(String regionsKey) throws IOException {
        if (!file.exists()) {
            return null;
        }
        PriceCatalog cached = PriceCatalog.load(file);
        if (cached.schemaVersion != PriceCatalog.SCHEMA_VERSION) {
            log.info("The cached prices have an old format (version {}), ignoring the cache", cached.schemaVersion);
            return null;
        }
        if (regionsKey.equals(cached.regionsKey)) {
            return cached;
        }
        log.info("The cached prices were fetched for other regions, ignoring the cache");
        return null;
    }

    void save(PriceCatalog catalog) throws IOException {
        catalog.save(file);
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
