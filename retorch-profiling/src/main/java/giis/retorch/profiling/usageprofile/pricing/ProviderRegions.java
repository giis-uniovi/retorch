/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable map provider id -> regions to price (e.g. {@code azure -> [westeurope, northeurope]}). A provider with no
 * regions is disabled. Regions are given as comma separated lists, blanks are ignored.
 */
public final class ProviderRegions {

    private static final ProviderRegions NONE = new ProviderRegions(Collections.<String, List<String>>emptyMap());

    private final Map<String, List<String>> byId;

    private ProviderRegions(Map<String, List<String>> byId) {
        this.byId = byId;
    }

    /** No provider enabled. */
    public static ProviderRegions none() {
        return NONE;
    }

    /** The two classic settings: Azure and AWS regions (blank = disabled). */
    public static ProviderRegions of(String azureRegions, String awsRegions) {
        return NONE.with("azure", azureRegions).with("aws", awsRegions);
    }

    /** A copy where provider {@code id} uses these regions (comma separated; blank disables it). */
    public ProviderRegions with(String id, String regions) {
        Map<String, List<String>> copy = new LinkedHashMap<>(byId);
        copy.put(id, Collections.unmodifiableList(split(regions)));
        return new ProviderRegions(Collections.unmodifiableMap(copy));
    }

    /** The regions of a provider id; empty if it is not configured. */
    public List<String> regions(String id) {
        List<String> r = byId.get(id);
        return r == null ? Collections.<String>emptyList() : r;
    }

    /** "westeurope, northeurope" -> [westeurope, northeurope]; blanks are ignored. */
    static List<String> split(String regions) {
        List<String> result = new ArrayList<>();
        for (String r : regions == null ? new String[0] : regions.split(",")) {
            if (!r.trim().isEmpty()) {
                result.add(r.trim());
            }
        }
        return result;
    }
}
