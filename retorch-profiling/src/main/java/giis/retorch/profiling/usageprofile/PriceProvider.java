/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import java.io.IOException;

/** A source of prices (a cloud provider or one of its services) that can be asked for one region. */
public interface PriceProvider {

    /** Provider name used in the warnings. */
    String name();

    /** The prices of one region; a failure is reported by the caller as a warning. */
    PriceCatalog fetch(String region) throws IOException;

    /** What the warning says could not be obtained for {@code region}. */
    default String describe(String region) {
        return name() + " prices for " + region;
    }
}
