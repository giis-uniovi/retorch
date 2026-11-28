/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.ReservationOption;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Prices of AWS from the Price List bulk files, parsed in streaming (the EC2 file is ~450 MB per region): current
 * generation Linux shared on-demand instances with their reservations, EBS gp3 and Fargate (x86 Linux).
 */
public final class AwsPrices implements PriceProvider {

    static final String PROVIDER = "AWS";
    static final String AWS_URL = "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/";
    /** Attribute of a product that tells what is billed, e.g. {@code EU-Fargate-GB-Hours}. */
    static final String USAGE_TYPE = "usagetype";
    private static final Logger log = LoggerFactory.getLogger(AwsPrices.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FARGATE_USAGE = Pattern.compile("^[A-Z0-9]+-Fargate-(vCPU-Hours:perCPU|GB-Hours)$");
    private static final String PRODUCT_FAMILY = "productFamily";
    private static final String STORAGE_FAMILY = "Storage";

    private final HttpGet http;

    public AwsPrices(HttpGet http) {
        this.http = http;
    }

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public PriceCatalog fetch(String region) throws IOException {
        PriceCatalog.Builder catalog = PriceCatalog.builder();
        try (InputStream in = http.open(AWS_URL + "AmazonECS/current/index.json")) {
            parseFargate(readOffer(in, p -> FARGATE_USAGE.matcher(attr(p, USAGE_TYPE)).matches()
                    && region.equals(attr(p, "regionCode"))), region, catalog);
        }
        log.info("Downloading the AWS EC2 price list for {}, this can take a few minutes", region);
        try (InputStream in = http.open(AWS_URL + "AmazonEC2/current/" + region + "/index.json")) {
            Map<JsonNode, List<ReservationOption>> reserved = new HashMap<>();
            Map<JsonNode, Double> offer = readOffer(in, AwsPrices::isEc2Candidate, reserved);
            parseEc2(offer, reserved, region, catalog);
        }
        return catalog.build();
    }

    /** The value of an attribute of a product, or "" if it has none. */
    static String attr(JsonNode product, String name) {
        return product.path("attributes").path(name).asText("");
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

    /**
     * Reads an AWS offer file in streaming and returns, for each product accepted by the filter, the product and its
     * on-demand price (USD per unit) - the files are too big to be loaded as a tree.
     */
    static Map<JsonNode, Double> readOffer(InputStream in, Predicate<JsonNode> filter) throws IOException {
        return readOffer(in, filter, null);
    }

    /**
     * Same as {@link #readOffer(InputStream, Predicate)} and, if {@code reserved} is not null, also fills it with the
     * reservation options of the accepted products (section {@code terms.Reserved}, which follows {@code OnDemand}).
     */
    static Map<JsonNode, Double> readOffer(InputStream in, Predicate<JsonNode> filter,
                                           Map<JsonNode, List<ReservationOption>> reserved) throws IOException {
        Map<String, JsonNode> products = new HashMap<>();
        Map<JsonNode, Double> priced = new LinkedHashMap<>();
        try (JsonParser parser = new JsonFactory(MAPPER).createParser(in)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    continue;
                }
                String field = parser.currentName();
                if ("products".equals(field)) {
                    readProducts(parser, filter, products);
                } else if ("OnDemand".equals(field)) {
                    readOnDemandPrices(parser, products, priced);
                } else if (reserved != null && "Reserved".equals(field)) {
                    AwsReservations.read(parser, products, reserved);
                }
            }
        }
        return priced;
    }

    /** Positioned on the "products" field: keeps, by sku, the products accepted by the filter. */
    private static void readProducts(JsonParser parser, Predicate<JsonNode> filter, Map<String, JsonNode> products)
            throws IOException {
        parser.nextToken();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String sku = parser.currentName();
            parser.nextToken();
            JsonNode product = MAPPER.readTree(parser);
            if (filter.test(product)) {
                products.put(sku, product);
            }
        }
    }

    /** Positioned on the "OnDemand" field: reads the price of the kept products and skips the rest. */
    private static void readOnDemandPrices(JsonParser parser, Map<String, JsonNode> products,
                                           Map<JsonNode, Double> priced) throws IOException {
        parser.nextToken();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String sku = parser.currentName();
            parser.nextToken();
            if (!products.containsKey(sku)) {
                parser.skipChildren();
                continue;
            }
            Double price = firstUsdPrice(MAPPER.readTree(parser));
            if (price != null) {
                priced.put(products.get(sku), price);
            }
        }
    }

    /** The first price in USD of the terms of a sku, or null if it has none. */
    static Double firstUsdPrice(JsonNode skuTerms) {
        for (JsonNode term : skuTerms) {
            for (JsonNode dim : term.path("priceDimensions")) {
                double usd = dim.path("pricePerUnit").path("USD").asDouble(-1);
                if (usd >= 0) {
                    return usd;
                }
            }
        }
        return null;
    }

    static void parseFargate(Map<JsonNode, Double> offer, String region, PriceCatalog.Builder out) {
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
            out.addContainer(new PriceCatalog.ContainerRate(PROVIDER, region, "Fargate", cpu, mem));
        }
    }

    static void parseEc2(Map<JsonNode, Double> offer, String region, PriceCatalog.Builder out) {
        parseEc2(offer, Collections.<JsonNode, List<ReservationOption>>emptyMap(), region, out);
    }

    static void parseEc2(Map<JsonNode, Double> offer, Map<JsonNode, List<ReservationOption>> reserved, String region,
                         PriceCatalog.Builder out) {
        Map<String, PriceCatalog.VmOffer> cheapest = new LinkedHashMap<>();
        for (Map.Entry<JsonNode, Double> e : offer.entrySet()) {
            JsonNode p = e.getKey();
            if (STORAGE_FAMILY.equals(p.path(PRODUCT_FAMILY).asText())) {
                out.addStorage(new PriceCatalog.StorageRate(PROVIDER, region, "EBS gp3", e.getValue()));
                continue;
            }
            String type = attr(p, "instanceType");
            PriceCatalog.VmOffer previous = cheapest.get(type);
            if (previous == null || e.getValue() < previous.hourly) {
                double vcpu = Double.parseDouble(attr(p, "vcpu"));
                double memGb = Double.parseDouble(attr(p, "memory").replace("GiB", "").replace(",", "").trim());
                cheapest.put(type, new PriceCatalog.VmOffer(PROVIDER, region, type, vcpu, memGb, e.getValue(),
                        new ArrayList<>(reserved.getOrDefault(p, Collections.<ReservationOption>emptyList()))));
            }
        }
        for (PriceCatalog.VmOffer vm : cheapest.values()) {
            out.addVm(vm);
        }
    }
}
