/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.ReservationOptionDTO;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template Method base of the price providers: it holds everything that is not specific to a price list. A concrete
 * provider only declares its identity (constructor) and implements {@link #collect(String, PriceCatalog.Builder)} with its
 * own parsing; the plumbing is here:
 * <ul>
 *   <li>the catalog is built around {@link #collect} ({@link #fetch(String)} is final);</li>
 *   <li>HTTP + JSON: {@link #readJson(String)}, {@link #pages(String, String, String)} (paged APIs), {@link #open(String)};</li>
 *   <li>streaming of the big AWS-style offer files: {@link #readOffer(InputStream, Predicate)} and helpers
 *       ({@link #attr(JsonNode, String)}, {@link #firstUsdPrice(JsonNode)});</li>
 *   <li>safe number parsing ({@link #number(String, double)}) and reservation helpers ({@link #termYears(String)},
 *       {@link #addCheapest(List, ReservationOptionDTO)}, {@link #sortByTerm(List)});</li>
 *   <li>factories of catalog entries labelled with this provider ({@link #vm}, {@link #container}, {@link #storage},
 *       {@link #browser}).</li>
 * </ul>
 * The HTTP access is the injected {@link HttpGet}, so a provider is tested with canned responses.
 */
public abstract class AbstractPriceProvider implements PriceProvider {

    /** Attribute of an AWS product that tells what is billed, e.g. {@code EU-Fargate-GB-Hours}. */
    static final String USAGE_TYPE = "usagetype";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TERM = Pattern.compile("^(\\d+)\\s*(?:years?|yr)$", Pattern.CASE_INSENSITIVE);

    private final HttpGet http;
    private final String id;
    private final String name;
    private final String catalogProvider;

    /**
     * @param http            the HTTP access
     * @param id              unique id (key of its regions), e.g. {@code azure}
     * @param name            name used in the warnings, e.g. {@code Azure}
     * @param catalogProvider the provider label of the entries of the catalog, e.g. {@code AWS} (Device Farm: not its own name)
     */
    protected AbstractPriceProvider(HttpGet http, String id, String name, String catalogProvider) {
        this.http = http;
        this.id = id;
        this.name = name;
        this.catalogProvider = catalogProvider;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    /** Template method: the prices of {@code region} collected by the subclass into a new catalog. */
    @Override
    public final PriceCatalog fetch(String region) throws IOException {
        PriceCatalog.Builder out = PriceCatalog.builder();
        collect(region, out);
        return out.build();
    }

    /** The provider specific part: downloads what it needs (with the helpers below) and adds the entries to {@code out}. */
    protected abstract void collect(String region, PriceCatalog.Builder out) throws IOException;

    // ---- HTTP and JSON

    /** Opens a URL; the caller closes the stream. */
    protected final InputStream open(String url) throws IOException {
        return http.open(url);
    }

    /** The JSON document at a URL. */
    protected final JsonNode readJson(String url) throws IOException {
        try (InputStream in = http.open(url)) {
            return MAPPER.readTree(in);
        }
    }

    /**
     * Follows a paged API: collects the elements of {@code itemsField} of every page, starting at {@code firstUrl} and
     * following the URL in {@code nextLinkField} until it is missing or empty.
     */
    protected final List<JsonNode> pages(String firstUrl, String itemsField, String nextLinkField) throws IOException {
        List<JsonNode> items = new ArrayList<>();
        String url = firstUrl;
        while (url != null && !url.isEmpty()) {
            JsonNode page = readJson(url);
            for (JsonNode item : page.path(itemsField)) {
                items.add(item);
            }
            url = page.path(nextLinkField).asText(null);
        }
        return items;
    }

    // ---- streaming of AWS-style offer files (sections products / terms.OnDemand / terms.Reserved)

    /** The value of an attribute of a product, or "" if it has none. */
    static String attr(JsonNode product, String name) {
        return product.path("attributes").path(name).asText("");
    }

    /**
     * Reads an offer file in streaming and returns, for each product accepted by the filter, the product and its
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
                                           Map<JsonNode, List<ReservationOptionDTO>> reserved) throws IOException {
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

    // ---- numbers and reservations

    /** A number from a text such as {@code "8 GiB"} or {@code "1,024 GiB"} (unit and thousands separators dropped), or {@code fallback}. */
    static double number(String text, double fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.replace("GiB", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 1 or 3 for {@code "1 Year"}, {@code "3 Years"}, {@code "1yr"}, {@code "3yr"}; 0 for any other term. */
    static int termYears(String term) {
        Matcher m = TERM.matcher(term == null ? "" : term.trim());
        if (!m.matches()) {
            return 0;
        }
        int years = Integer.parseInt(m.group(1));
        return years == 1 || years == 3 ? years : 0;
    }

    /** Adds the option unless there is already a cheaper one for the same (term, payment); a dearer one is replaced. */
    static void addCheapest(List<ReservationOptionDTO> options, ReservationOptionDTO candidate) {
        for (int i = 0; i < options.size(); i++) {
            ReservationOptionDTO o = options.get(i);
            if (o.termYears == candidate.termYears && o.payment == candidate.payment) {
                if (candidate.effectiveHourly < o.effectiveHourly) {
                    options.set(i, candidate);
                }
                return;
            }
        }
        options.add(candidate);
    }

    /** Sorts reservation options by term (stable). */
    static void sortByTerm(List<ReservationOptionDTO> options) {
        options.sort((a, b) -> Integer.compare(a.termYears, b.termYears));
    }

    // ---- catalog entries of this provider

    /** A VM of this provider, with its reservations (may be null or empty). */
    protected final PriceCatalog.VmOffer vm(String region, String vmName, double vcpu, double memGb, double hourly,
                                            List<ReservationOptionDTO> reservations) {
        return new PriceCatalog.VmOffer(catalogProvider, region, vmName, vcpu, memGb, hourly, reservations);
    }

    /** A VM of this provider without reservations. */
    protected final PriceCatalog.VmOffer vm(String region, String vmName, double vcpu, double memGb, double hourly) {
        return vm(region, vmName, vcpu, memGb, hourly, null);
    }

    protected final PriceCatalog.ContainerRate container(String region, String service, double vcpuHour, double gbHour) {
        return new PriceCatalog.ContainerRate(catalogProvider, region, service, vcpuHour, gbHour);
    }

    protected final PriceCatalog.StorageRate storage(String region, String service, double gbMonth) {
        return new PriceCatalog.StorageRate(catalogProvider, region, service, gbMonth);
    }

    protected final PriceCatalog.BrowserRate browser(String region, String service, String rentedName, double minuteUsd) {
        return new PriceCatalog.BrowserRate(catalogProvider, region, service, rentedName, minuteUsd);
    }
}
