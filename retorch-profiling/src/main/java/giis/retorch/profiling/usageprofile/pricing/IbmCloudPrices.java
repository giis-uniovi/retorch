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
 * Prices of IBM Cloud Virtual Servers for VPC from the public Global Catalog (no credentials). Opt-in: it runs only for
 * the regions configured (IBM region names: {@code us-south}, {@code eu-de}, {@code eu-gb}...).
 * <p>
 * The catalog has one plan per profile ({@code is.instance} service, plan name = profile, e.g. {@code bxf-4x16}) and one
 * deployment per region under each plan; the price is on the deployment: the metric
 * {@code part-is.instance-hours-<profile>} (unit {@code Instance-Hour}) holds the plain on-demand hour in USD. The
 * profiles considered are the Flex ones (balanced {@code bxf}, compute {@code cxf}, memory {@code mxf}) with 2, 4, 8, 16
 * and 32 vCPU: they are the generation available in every region (the Gen4 {@code bx4/cx4/mx4} plans only exist in
 * us-south). vCPU and GB come from the profile name {@code family-VCPUxGB}. The cost is two HTTP calls per profile and
 * region (deployment of the region, then its pricing) plus the plan list. Block storage is the {@code is.volume} plan
 * {@code gen2-volume-general-purpose}: metric {@code is.volume.generalpurpose.GB} (per GB-hour, converted to GB-month
 * with {@link #HOURS_PER_MONTH}). No container service and no reservations. A profile with no deployment or price in the
 * region only adds a warning; if no VM can be priced, or the plan list lacks the services, the region fails.
 */
public final class IbmCloudPrices extends AbstractPriceProvider {

    static final String PROVIDER = "IBM Cloud";
    static final String CATALOG = "https://globalcatalog.cloud.ibm.com/api/v1/";
    static final String INSTANCE_PLANS = CATALOG + "is.instance/plan?languages=en";
    static final String VOLUME_PLANS = CATALOG + "is.volume/plan?languages=en";
    static final String VOLUME_PLAN = "gen2-volume-general-purpose";
    static final String VOLUME_METRIC = "is.volume.generalpurpose.GB";
    static final String INSTANCE_METRIC_PREFIX = "part-is.instance-hours-";
    static final String INSTANCE_UNIT = "Instance-Hour";
    static final double HOURS_PER_MONTH = 730;
    /** {@code family-VCPUxGB}, of the families considered. */
    static final Pattern PROFILE = Pattern.compile("^(bxf|cxf|mxf)-(\\d+)x(\\d+)$");
    static final int[] VCPUS = {2, 4, 8, 16, 32};

    public IbmCloudPrices(HttpGet http) {
        super(http, "ibm", PROVIDER, PROVIDER);
    }

    @Override
    public boolean optional() {
        return true;
    }

    @Override
    protected void collect(String region, PriceCatalog.Builder out) throws IOException {
        Map<String, String> plans = planIds(INSTANCE_PLANS);
        int priced = 0;
        for (Map.Entry<String, String> plan : plans.entrySet()) {
            Matcher m = PROFILE.matcher(plan.getKey());
            if (!m.matches() || !isConsidered(Integer.parseInt(m.group(2)))) {
                continue;
            }
            try {
                double hourly = price(plan.getValue(), region, INSTANCE_METRIC_PREFIX + plan.getKey(), INSTANCE_UNIT);
                out.addVm(vm(region, plan.getKey(), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), hourly));
                priced++;
            } catch (IOException e) {
                out.addWarning(PROVIDER + " profile " + plan.getKey() + " not priced in " + region + ": " + e.getMessage());
            }
        }
        if (priced == 0) {
            throw new IOException("no priced bxf/cxf/mxf profile found in the IBM catalog for " + region);
        }
        String volume = planIds(VOLUME_PLANS).get(VOLUME_PLAN);
        try {
            if (volume == null) {
                throw new IOException("plan " + VOLUME_PLAN + " not found");
            }
            out.addStorage(storage(region, "Block Storage (general purpose)",
                    price(volume, region, VOLUME_METRIC, null) * HOURS_PER_MONTH));
        } catch (IOException e) {
            out.addWarning(PROVIDER + " block storage not priced in " + region + ": " + e.getMessage());
        }
    }

    private static boolean isConsidered(int vcpu) {
        for (int v : VCPUS) {
            if (v == vcpu) {
                return true;
            }
        }
        return false;
    }

    /** Plan name -> plan id of a catalog plan list (all pages; the first of a repeated name wins). */
    private Map<String, String> planIds(String url) throws IOException {
        Map<String, String> ids = new LinkedHashMap<>();
        for (JsonNode plan : pages(url, "resources", "next")) {
            ids.putIfAbsent(plan.path("name").asText(), plan.path("id").asText());
        }
        return ids;
    }

    /** The USD price of a metric of the deployment of a plan in a region; the unit is checked when not null. */
    private double price(String planId, String region, String metricId, String unit) throws IOException {
        String deployment = null;
        for (JsonNode d : pages(CATALOG + planId + "/deployment?languages=en", "resources", "next")) {
            for (JsonNode tag : d.path("geo_tags")) {
                if (region.equals(tag.asText())) {
                    deployment = d.path("id").asText();
                }
            }
            if (deployment != null) {
                break;
            }
        }
        if (deployment == null) {
            throw new IOException("no deployment in " + region);
        }
        for (JsonNode metric : readJson(CATALOG + deployment + "/pricing").path("metrics")) {
            if (metricId.equals(metric.path("metric_id").asText())
                    && (unit == null || unit.equals(metric.path("charge_unit").asText()))) {
                for (JsonNode amount : metric.path("amounts")) {
                    if ("USD".equals(amount.path("currency").asText()) && amount.path("prices").size() > 0) {
                        return amount.path("prices").get(0).path("price").asDouble();
                    }
                }
            }
        }
        throw new IOException("metric " + metricId + " not found");
    }
}
