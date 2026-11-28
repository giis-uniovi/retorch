/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.main;

import giis.retorch.profiling.usageprofile.pricing.PriceSource;
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
 * The opt-in providers (Oracle Cloud, IBM Cloud) read their regions from the system properties
 * {@code usageprofile-regions-oracle} / {@code usageprofile-regions-ibm} (comma separated), e.g.
 * {@code java -Dusageprofile-regions-ibm=us-south,eu-de -jar ...}; without them they are disabled.
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
        PriceSource prices = PriceSource.builder().regions("azure", args[3]).regions("aws", args[4])
                .cacheFile(args[5]).maxAgeHours(PRICE_CACHE_HOURS).systemPropertyRegions().build();
        new UsageProfilerToolBox().generate(UsageProfileRequest.builder(args[0], args[1], args[2]).prices(prices)
                .executionPlan(args.length == MAX_ARGS ? args[MAX_ARGS - 1] : null).build());
    }
}
