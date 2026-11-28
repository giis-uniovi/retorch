/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prices of Oracle Cloud Infrastructure from the public Cost Estimator API (no credentials). Oracle prices are the same in
 * every region, so the provider has one pseudo region ({@code global}) and is opt-in (it runs only when configured).
 * <p>
 * VMs are the flexible shapes: a shape is {@code ocpu} OCPUs and {@code ocpu * GB_PER_OCPU} GB, priced
 * {@code ocpu * pricePerOcpuHour + gb * pricePerGbHour}; the cheapest x86 family (E3, E4, E5, E6, X9) wins for each shape.
 * One OCPU is a physical core with two threads, so it is counted as 2 vCPU (ARM shapes, where OCPU = vCPU, are not used).
 * The storage rate is the Block Volume capacity. There is no container service and no reservations. Items are chosen by
 * the exact display names/metrics held in the constants below; Cloud@Customer, free-tier, Dedicated and other variants do
 * not match. If an expected part is missing the region fails with a clear message (a warning in the catalog).
 */
public final class OracleCloudPrices extends AbstractPriceProvider {

    static final String PROVIDER = "Oracle Cloud";
    static final String URL = "https://apexapps.oracle.com/pls/apex/cetools/api/v1/products/?currencyCode=USD";
    static final String BLOCK_VOLUME = "Storage - Block Volume - Storage";
    static final String BLOCK_VOLUME_METRIC = "Gigabyte Storage Capacity Per Month";
    static final String VM_CATEGORY = "Compute - Virtual Machine";

    /** E.g. {@code Compute - Standard - E4 - OCPU}, {@code OCI - Compute - Standard - E6 - Memory}. */
    static final Pattern COMPUTE_ITEM = Pattern.compile(
            "^(?:OCI - )?Compute - Standard - (E3|E4|E5|E6|X9) - (OCPU|Memory)$");
    static final String OCPU_METRIC = "OCPU Per Hour";
    /** Both "Gigabyte Per Hour" and "Gigabytes Per Hour" occur. */
    static final Pattern MEMORY_METRIC = Pattern.compile("^Gigabytes? Per Hour$");
    static final int VCPU_PER_OCPU = 2;
    static final int[] OCPUS = {1, 2, 4, 8, 16, 32};
    /** GB per OCPU of the general purpose and of the memory optimised variants. */
    static final int[] GB_PER_OCPU = {8, 16};

    public OracleCloudPrices(HttpGet http) {
        super(http, "oracle", PROVIDER, PROVIDER);
    }

    @Override
    public boolean optional() {
        return true;
    }

    @Override
    protected void collect(String region, PriceCatalog.Builder out) throws IOException {
        parse(readJson(URL), region, out);
    }

    void parse(JsonNode root, String region, PriceCatalog.Builder out) throws IOException {
        Map<String, double[]> families = new LinkedHashMap<>(); // family -> {ocpuPrice, gbPrice}, NaN if unknown
        double block = Double.NaN;
        for (JsonNode item : root.path("items")) {
            String name = item.path("displayName").asText("").replaceAll("\\s+", " ").trim();
            double price = paygUsd(item);
            if (BLOCK_VOLUME.equals(name) && BLOCK_VOLUME_METRIC.equals(item.path("metricName").asText())) {
                block = price;
                continue;
            }
            Matcher m = COMPUTE_ITEM.matcher(name);
            if (!m.matches() || !VM_CATEGORY.equals(item.path("serviceCategory").asText()) || Double.isNaN(price)) {
                continue;
            }
            String metric = item.path("metricName").asText();
            boolean ocpu = "OCPU".equals(m.group(2));
            if (ocpu ? !OCPU_METRIC.equals(metric) : !MEMORY_METRIC.matcher(metric).matches()) {
                continue;
            }
            families.computeIfAbsent(m.group(1), f -> new double[]{Double.NaN, Double.NaN})[ocpu ? 0 : 1] = price;
        }
        addShapes(families, region, out);
        if (Double.isNaN(block)) {
            throw new IOException("Oracle price list has no '" + BLOCK_VOLUME + "' item with metric '"
                    + BLOCK_VOLUME_METRIC + "'");
        }
        out.addStorage(storage(region, "Block Volume", block));
    }

    private void addShapes(Map<String, double[]> families, String region, PriceCatalog.Builder out) throws IOException {
        Map<String, double[]> complete = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> f : families.entrySet()) {
            if (!Double.isNaN(f.getValue()[0]) && !Double.isNaN(f.getValue()[1])) {
                complete.put(f.getKey(), f.getValue());
            }
        }
        if (complete.isEmpty()) {
            throw new IOException("Oracle price list has no flexible compute family with both '... - OCPU' ("
                    + OCPU_METRIC + ") and '... - Memory' (Gigabyte Per Hour) items");
        }
        for (int gbPerOcpu : GB_PER_OCPU) {
            for (int ocpus : OCPUS) {
                String best = null;
                double bestPrice = Double.MAX_VALUE;
                for (Map.Entry<String, double[]> f : complete.entrySet()) {
                    double hourly = ocpus * f.getValue()[0] + ocpus * gbPerOcpu * f.getValue()[1];
                    if (hourly < bestPrice) {
                        bestPrice = hourly;
                        best = f.getKey();
                    }
                }
                String shape = "VM.Flex." + best + "." + ocpus + "ocpu-" + ocpus * gbPerOcpu + "gb";
                out.addVm(vm(region, shape, (double) ocpus * VCPU_PER_OCPU, (double) ocpus * gbPerOcpu, bestPrice));
            }
        }
    }

    /** First USD pay-as-you-go price greater than 0 (the free tier of some items has a first tier at 0), or NaN. */
    private static double paygUsd(JsonNode item) {
        for (JsonNode loc : item.path("currencyCodeLocalizations")) {
            if (!"USD".equals(loc.path("currencyCode").asText())) {
                continue;
            }
            for (JsonNode price : loc.path("prices")) {
                double v = price.path("value").asDouble(0);
                if ("PAY_AS_YOU_GO".equals(price.path("model").asText()) && v > 0) {
                    return v;
                }
            }
        }
        return Double.NaN;
    }
}
