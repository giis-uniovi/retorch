/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import giis.retorch.profiling.usageprofile.dto.ReservationOptionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Prices of AWS from the Price List bulk files, parsed in streaming (the EC2 file is ~450 MB per region): current
 * generation Linux shared on-demand instances with their reservations, EBS gp3 and Fargate (x86 Linux). Only the AWS
 * specific filters and mapping live here; the streaming is in {@link AbstractPriceProvider}.
 */
public final class AwsPrices extends AbstractPriceProvider {

    static final String PROVIDER = "AWS";
    static final String AWS_URL = "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/";
    private static final Logger log = LoggerFactory.getLogger(AwsPrices.class);
    private static final Pattern FARGATE_USAGE = Pattern.compile("^[A-Z0-9]+-Fargate-(vCPU-Hours:perCPU|GB-Hours)$");
    private static final String PRODUCT_FAMILY = "productFamily";
    private static final String STORAGE_FAMILY = "Storage";

    public AwsPrices(HttpGet http) {
        super(http, "aws", PROVIDER, PROVIDER);
    }

    @Override
    protected void collect(String region, PriceCatalog.Builder catalog) throws IOException {
        try (InputStream in = open(AWS_URL + "AmazonECS/current/index.json")) {
            parseFargate(readOffer(in, p -> FARGATE_USAGE.matcher(attr(p, USAGE_TYPE)).matches()
                    && region.equals(attr(p, "regionCode"))), region, catalog);
        }
        log.info("Downloading the AWS EC2 price list for {}, this can take a few minutes", region);
        try (InputStream in = open(AWS_URL + "AmazonEC2/current/" + region + "/index.json")) {
            Map<JsonNode, List<ReservationOptionDTO>> reserved = new HashMap<>();
            Map<JsonNode, Double> offer = readOffer(in, AwsPrices::isEc2Candidate, reserved);
            parseEc2(offer, reserved, region, catalog);
        }
    }

    private static boolean isEc2Candidate(JsonNode p) {
        String family = p.path(PRODUCT_FAMILY).asText();
        if ("Compute Instance".equals(family)) {
            return "Linux".equals(attr(p, "operatingSystem")) && "Shared".equals(attr(p, "tenancy"))
                    && "NA".equals(attr(p, "preInstalledSw")) && "Used".equals(attr(p, "capacitystatus"))
                    && "Yes".equals(attr(p, "currentGeneration"));
        }
        return STORAGE_FAMILY.equals(family) && "gp3".equals(attr(p, "volumeApiName"));
    }

    void parseFargate(Map<JsonNode, Double> offer, String region, PriceCatalog.Builder out) {
        double cpu = Double.NaN;
        double mem = Double.NaN;
        for (Map.Entry<JsonNode, Double> e : offer.entrySet()) {
            if (attr(e.getKey(), USAGE_TYPE).endsWith("vCPU-Hours:perCPU")) {
                cpu = e.getValue();
            } else {
                mem = e.getValue();
            }
        }
        if (!Double.isNaN(cpu) && !Double.isNaN(mem)) {
            out.addContainer(container(region, "Fargate", cpu, mem));
        }
    }

    void parseEc2(Map<JsonNode, Double> offer, String region, PriceCatalog.Builder out) {
        parseEc2(offer, Collections.<JsonNode, List<ReservationOptionDTO>>emptyMap(), region, out);
    }

    /** Adds the cheapest offer of each instance type and the EBS gp3 rate; an instance without a numeric vCPU or memory is skipped. */
    void parseEc2(Map<JsonNode, Double> offer, Map<JsonNode, List<ReservationOptionDTO>> reserved, String region,
                  PriceCatalog.Builder out) {
        Map<String, PriceCatalog.VmOffer> cheapest = new LinkedHashMap<>();
        for (Map.Entry<JsonNode, Double> e : offer.entrySet()) {
            JsonNode p = e.getKey();
            if (STORAGE_FAMILY.equals(p.path(PRODUCT_FAMILY).asText())) {
                out.addStorage(storage(region, "EBS gp3", e.getValue()));
                continue;
            }
            String type = attr(p, "instanceType");
            PriceCatalog.VmOffer previous = cheapest.get(type);
            double vcpu = number(attr(p, "vcpu"), Double.NaN);
            double memGb = number(attr(p, "memory"), Double.NaN);
            if ((previous == null || e.getValue() < previous.hourly) && !Double.isNaN(vcpu) && !Double.isNaN(memGb)) {
                cheapest.put(type, vm(region, type, vcpu, memGb, e.getValue(),
                        new ArrayList<>(reserved.getOrDefault(p, Collections.<ReservationOptionDTO>emptyList()))));
            }
        }
        for (PriceCatalog.VmOffer vm : cheapest.values()) {
            out.addVm(vm);
        }
    }
}
