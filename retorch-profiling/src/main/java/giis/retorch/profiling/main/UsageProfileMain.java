/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.main;

import giis.retorch.profiling.usageprofile.PriceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Command line entry point used by the generated Jenkins pipeline to build the usage profile of an
 * execution.
 * <pre>
 * java -jar retorch-profiling-&lt;version&gt;-cli.jar
 *      planName usageCsv outputHtml azureRegions awsRegions priceCacheFile [executionPlanJson]
 * </pre>
 */
public final class UsageProfileMain {

    private static final Logger log = LoggerFactory.getLogger(UsageProfileMain.class);
    private static final int PRICE_CACHE_HOURS = 24;
    private static final int MIN_ARGS = 6;
    private static final int MAX_ARGS = 7;

    private UsageProfileMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < MIN_ARGS || args.length > MAX_ARGS) {
            log.error("Usage: UsageProfileMain <planName> <usageCsv> <outputHtml> <azureRegions> <awsRegions> <priceCacheFile> [executionPlanJson]");
            java.lang.System.exit(1);
        }
        PriceSource prices = new PriceSource(args[3], args[4], args[5], PRICE_CACHE_HOURS);
        new UsageProfilerToolBox().generateUsageProfile(args[0], args[1], args[2], prices,
                args.length == MAX_ARGS ? args[MAX_ARGS - 1] : null);
    }
}
