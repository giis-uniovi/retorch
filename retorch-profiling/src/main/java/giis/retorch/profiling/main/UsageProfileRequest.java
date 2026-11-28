/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.main;

import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;
import giis.retorch.profiling.usageprofile.pricing.PriceSource;

/**
 * Everything {@link UsageProfilerToolBox#generate(UsageProfileRequest)} needs to build one usage profile. Immutable; use
 * {@link #builder(String, String, String)}.
 */
public final class UsageProfileRequest {

    /** System property with the text that marks a resource of the execution plan as a browser. */
    public static final String BROWSER_RESOURCES_PROPERTY = "usageprofile-browser-resources";
    /** Default of {@link #BROWSER_RESOURCES_PROPERTY}. */
    public static final String DEFAULT_BROWSER_RESOURCES = "browser";

    private final String planName;
    private final String usageCsv;
    private final String outputHtml;
    private final PriceSource priceSource;
    private final PriceCatalog catalog;
    private final String executionPlanJson;
    private final String browserResourcePattern;

    private UsageProfileRequest(Builder b) {
        this.planName = b.planName;
        this.usageCsv = b.usageCsv;
        this.outputHtml = b.outputHtml;
        this.priceSource = b.priceSource;
        this.catalog = b.catalog;
        this.executionPlanJson = b.executionPlanJson;
        this.browserResourcePattern = b.browserResourcePattern;
    }

    /**
     * @param planName   name shown in the usage profile
     * @param usageCsv   single usage file exported by the orchestration scripts
     * @param outputHtml the usage profile to write
     */
    public static Builder builder(String planName, String usageCsv, String outputHtml) {
        return new Builder(planName, usageCsv, outputHtml);
    }

    public String planName() {
        return planName;
    }

    public String usageCsv() {
        return usageCsv;
    }

    public String outputHtml() {
        return outputHtml;
    }

    /** The regions and cache to fetch prices from; null when a ready {@link #catalog()} was given. */
    public PriceSource priceSource() {
        return priceSource;
    }

    /** The ready prices; null when they are fetched through {@link #priceSource()}. */
    public PriceCatalog catalog() {
        return catalog;
    }

    /** Path of {@code .retorch/executionplan.json}; null or a missing file means no execution graph. */
    public String executionPlanJson() {
        return executionPlanJson;
    }

    /** Text (case-insensitive) a resource name contains to mark its TJob as one that uses a browser. */
    public String browserResourcePattern() {
        return browserResourcePattern;
    }

    /** Builds a {@link UsageProfileRequest}. */
    public static final class Builder {
        private final String planName;
        private final String usageCsv;
        private final String outputHtml;
        private PriceSource priceSource;
        private PriceCatalog catalog;
        private String executionPlanJson;
        private String browserResourcePattern = DEFAULT_BROWSER_RESOURCES;

        private Builder(String planName, String usageCsv, String outputHtml) {
            this.planName = planName;
            this.usageCsv = usageCsv;
            this.outputHtml = outputHtml;
            browserResourcePattern(System.getProperty(BROWSER_RESOURCES_PROPERTY));
        }

        /** Fetch the prices of these regions (with their cache). */
        public Builder prices(PriceSource source) {
            this.priceSource = source;
            this.catalog = null;
            return this;
        }

        /** Use these prices, e.g. loaded from a file or defined by the user. */
        public Builder prices(PriceCatalog readyCatalog) {
            this.catalog = readyCatalog;
            this.priceSource = null;
            return this;
        }

        /** The execution plan file; optional. */
        public Builder executionPlan(String path) {
            this.executionPlanJson = path;
            return this;
        }

        /** Overrides the system property {@code usageprofile-browser-resources}; blank means the default. */
        public Builder browserResourcePattern(String pattern) {
            this.browserResourcePattern = pattern == null || pattern.trim().isEmpty() ? DEFAULT_BROWSER_RESOURCES : pattern.trim();
            return this;
        }

        public UsageProfileRequest build() {
            if (priceSource == null && catalog == null) {
                throw new IllegalStateException("The request needs prices: a PriceSource or a PriceCatalog");
            }
            return new UsageProfileRequest(this);
        }
    }
}
