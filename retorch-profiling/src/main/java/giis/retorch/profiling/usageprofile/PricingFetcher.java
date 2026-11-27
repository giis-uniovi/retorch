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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Builds a {@link PriceCatalog} from the public price lists of the cloud providers (no credentials required):
 * the Azure Retail Prices API and the AWS Price List bulk files. The catalog is cached on disk, because the AWS EC2
 * file weighs hundreds of MB and is parsed in streaming. A provider that cannot be reached only adds a warning.
 */
public class PricingFetcher {

    private static final Logger log = LoggerFactory.getLogger(PricingFetcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AZURE_URL = "https://prices.azure.com/api/retail/prices?$filter=";
    private static final String AWS_URL = "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/";
    private static final Pattern FARGATE_USAGE = Pattern.compile("^[A-Z0-9]+-Fargate-(vCPU-Hours:perCPU|GB-Hours)$");
    private static final double AZURE_E10_DISK_GB = 128;
    private static final String PROVIDER_AZURE = "Azure";
    private static final String PROVIDER_AWS = "AWS";
    private static final String RETAIL_PRICE = "retailPrice";
    private static final String UNIT_OF_MEASURE = "unitOfMeasure";
    private static final String AZURE_VM_SERVICE = "serviceName eq 'Virtual Machines' and ";
    private static final String HOUR = "1 Hour";

    /** Azure does not publish vCPU/memory in the price API, so the sizes considered are listed here: name, vCPU, GB. */
    private static final Object[][] AZURE_VM_SIZES = {
            {"Standard_B2s", 2, 4}, {"Standard_B2ms", 2, 8}, {"Standard_B4ms", 4, 16},
            {"Standard_D2s_v5", 2, 8}, {"Standard_D4s_v5", 4, 16}, {"Standard_D8s_v5", 8, 32},
            {"Standard_D16s_v5", 16, 64}, {"Standard_D32s_v5", 32, 128},
            {"Standard_E2s_v5", 2, 16}, {"Standard_E4s_v5", 4, 32}, {"Standard_E8s_v5", 8, 64},
            {"Standard_F2s_v2", 2, 4}, {"Standard_F4s_v2", 4, 8}, {"Standard_F8s_v2", 8, 16},
            {"Standard_F16s_v2", 16, 32}};

    private static final int TIMEOUT_MS = 120_000;

    /**
     * Returns the cached catalog when it is younger than {@code maxAgeMs}; otherwise downloads the prices again and
     * refreshes the cache. If every provider fails but an old cache exists, the old cache is used with a warning.
     */
    public PriceCatalog fetchOrLoad(String azureRegions, String awsRegions, File cache, long maxAgeMs) throws IOException {
        String regionsKey = "azure=" + String.join(",", split(azureRegions)) + ";aws=" + String.join(",", split(awsRegions));
        PriceCatalog cached = loadCache(cache, regionsKey);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtEpochMs < maxAgeMs) {
            log.info("Using the cached prices from {}", cache);
            return cached;
        }
        PriceCatalog fresh = download(azureRegions, awsRegions, regionsKey);
        if (!fresh.vms.isEmpty() || !fresh.containers.isEmpty()) {
            fresh.save(cache);
            return fresh;
        }
        if (cached == null) {
            throw new IOException("No prices could be obtained: " + fresh.warnings);
        }
        cached.warnings.add("Prices could not be refreshed, using the cache from " + cached.fetchedAtEpochMs);
        cached.warnings.addAll(fresh.warnings);
        return cached;
    }

    /** The cached catalog, or null if there is none or it was built for other regions. */
    private static PriceCatalog loadCache(File cache, String regionsKey) throws IOException {
        if (!cache.exists()) {
            return null;
        }
        PriceCatalog cached = PriceCatalog.load(cache);
        if (cached.schemaVersion != PriceCatalog.SCHEMA_VERSION) {
            log.info("The cached prices have an old format (version {}), ignoring the cache", cached.schemaVersion);
            return null;
        }
        if (regionsKey.equals(cached.regionsKey)) {
            return cached;
        }
        log.info("The cached prices were fetched for other regions, ignoring the cache");
        return null;
    }

    private PriceCatalog download(String azureRegions, String awsRegions, String regionsKey) {
        PriceCatalog fresh = new PriceCatalog();
        fresh.fetchedAtEpochMs = System.currentTimeMillis();
        fresh.regionsKey = regionsKey;
        fetchRegions(fresh, PROVIDER_AZURE, split(azureRegions), this::fetchAzure);
        fetchRegions(fresh, PROVIDER_AWS, split(awsRegions), this::fetchAws);
        return fresh;
    }

    /** Fetches the prices of the regions of a provider; a region that fails only adds a warning to the target. */
    private static void fetchRegions(PriceCatalog target, String provider, List<String> regions, RegionFetcher fetcher) {
        for (String region : regions) {
            try {
                target.merge(fetcher.fetch(region));
            } catch (IOException | RuntimeException e) {
                target.warnings.add(provider + " prices for " + region + " not available: " + e.getMessage());
            }
        }
    }

    private interface RegionFetcher {
        PriceCatalog fetch(String region) throws IOException;
    }

    /** "westeurope, northeurope" -> [westeurope, northeurope]; blanks are ignored (a provider can be disabled). */
    static List<String> split(String regions) {
        List<String> result = new ArrayList<>();
        for (String r : regions == null ? new String[0] : regions.split(",")) {
            if (!r.trim().isEmpty()) {
                result.add(r.trim());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ Azure

    public PriceCatalog fetchAzure(String region) throws IOException {
        PriceCatalog catalog = new PriceCatalog();
        StringBuilder names = new StringBuilder();
        for (Object[] size : AZURE_VM_SIZES) {
            names.append(names.length() == 0 ? "" : " or ").append("armSkuName eq '").append(size[0]).append("'");
        }
        String regionFilter = "armRegionName eq '" + region + "' and ";
        String base = regionFilter + "priceType eq 'Consumption' and ";
        parseAzureVms(azureItems(AZURE_VM_SERVICE + base + "(" + names + ")"), region, catalog);
        parseAzureReservations(azureItems(AZURE_VM_SERVICE + regionFilter + "priceType eq 'Reservation' and ("
                + names + ")"), region, catalog);
        parseAzureContainers(azureItems("serviceName eq 'Container Instances' and " + base + "skuName eq 'Standard'"),
                region, catalog);
        parseAzureDisks(azureItems("serviceName eq 'Storage' and " + base
                + "productName eq 'Standard SSD Managed Disks' and skuName eq 'E10 LRS'"), region, catalog);
        return catalog;
    }

    private JsonNode[] azureItems(String filter) throws IOException {
        java.util.List<JsonNode> items = new java.util.ArrayList<>();
        String url = AZURE_URL + URLEncoder.encode(filter, "UTF-8").replace("+", "%20");
        while (url != null && !url.isEmpty()) {
            try (InputStream in = open(url)) {
                JsonNode page = MAPPER.readTree(in);
                for (JsonNode item : page.path("Items")) {
                    items.add(item);
                }
                url = page.path("NextPageLink").asText(null);
            }
        }
        return items.toArray(new JsonNode[0]);
    }

    static void parseAzureVms(JsonNode[] items, String region, PriceCatalog out) {
        Map<String, double[]> sizes = new HashMap<>();
        for (Object[] s : AZURE_VM_SIZES) {
            sizes.put((String) s[0], new double[]{(Integer) s[1], (Integer) s[2]});
        }
        Map<String, Double> bestPrice = new LinkedHashMap<>();
        for (JsonNode item : items) {
            String sku = item.path("skuName").asText();
            String product = item.path("productName").asText();
            if (sku.contains("Spot") || sku.contains("Low Priority") || product.contains("Windows")
                    || !HOUR.equals(item.path(UNIT_OF_MEASURE).asText())) {
                continue;
            }
            String name = item.path("armSkuName").asText();
            if (sizes.containsKey(name)) {
                bestPrice.merge(name, item.path(RETAIL_PRICE).asDouble(), Math::min);
            }
        }
        for (Map.Entry<String, Double> e : bestPrice.entrySet()) {
            PriceCatalog.VmOffer vm = new PriceCatalog.VmOffer();
            vm.provider = PROVIDER_AZURE;
            vm.region = region;
            vm.name = e.getKey();
            vm.vcpu = sizes.get(e.getKey())[0];
            vm.memGb = sizes.get(e.getKey())[1];
            vm.hourly = e.getValue();
            out.vms.add(vm);
        }
    }

    /**
     * Adds to the VMs already in {@code out} their reservations. Azure gives the total price of the whole term in
     * {@code retailPrice} ("1 Year" / "3 Years" in {@code reservationTerm}), so it is an all-upfront option; the cheapest
     * price of each term wins.
     */
    static void parseAzureReservations(JsonNode[] items, String region, PriceCatalog out) {
        Map<String, PriceCatalog.VmOffer> byName = new HashMap<>();
        for (PriceCatalog.VmOffer vm : out.vms) {
            if (PROVIDER_AZURE.equals(vm.provider) && region.equals(vm.region)) {
                byName.put(vm.name, vm);
            }
        }
        Map<String, Double> bestFee = new HashMap<>();
        for (JsonNode item : items) {
            int years = azureTermYears(item.path("reservationTerm").asText());
            String name = item.path("armSkuName").asText();
            if (years > 0 && byName.containsKey(name) && !item.path("productName").asText().contains("Windows")) {
                bestFee.merge(name + "|" + years, item.path(RETAIL_PRICE).asDouble(), Math::min);
            }
        }
        for (Map.Entry<String, Double> e : bestFee.entrySet()) {
            String[] key = e.getKey().split("\\|");
            byName.get(key[0]).reservations.add(ReservationOption.allUpfront(Integer.parseInt(key[1]), e.getValue()));
        }
        for (PriceCatalog.VmOffer vm : byName.values()) {
            vm.reservations.sort((a, b) -> Integer.compare(a.termYears, b.termYears));
        }
    }

    private static int azureTermYears(String reservationTerm) {
        if ("1 Year".equals(reservationTerm)) {
            return 1;
        }
        return "3 Years".equals(reservationTerm) ? 3 : 0;
    }

    static void parseAzureContainers(JsonNode[] items, String region, PriceCatalog out) {
        double cpu = Double.NaN;
        double mem = Double.NaN;
        for (JsonNode item : items) {
            String meter = item.path("meterName").asText();
            if ("Standard vCPU Duration".equals(meter)) {
                cpu = item.path(RETAIL_PRICE).asDouble();
            } else if ("Standard Memory Duration".equals(meter)) {
                mem = item.path(RETAIL_PRICE).asDouble();
            }
        }
        if (!Double.isNaN(cpu) && !Double.isNaN(mem)) {
            PriceCatalog.ContainerRate rate = new PriceCatalog.ContainerRate();
            rate.provider = PROVIDER_AZURE;
            rate.region = region;
            rate.service = "Container Instances";
            rate.vcpuHour = cpu;
            rate.gbHour = mem;
            out.containers.add(rate);
        }
    }

    static void parseAzureDisks(JsonNode[] items, String region, PriceCatalog out) {
        for (JsonNode item : items) {
            if (item.path("meterName").asText().endsWith(" Disk") && item.path(UNIT_OF_MEASURE).asText().contains("Month")) {
                PriceCatalog.StorageRate rate = new PriceCatalog.StorageRate();
                rate.provider = PROVIDER_AZURE;
                rate.region = region;
                rate.name = "Standard SSD (E10)";
                rate.gbMonth = item.path(RETAIL_PRICE).asDouble() / AZURE_E10_DISK_GB;
                out.storage.add(rate);
                return;
            }
        }
    }

    // -------------------------------------------------------------------- AWS

    public PriceCatalog fetchAws(String region) throws IOException {
        PriceCatalog catalog = new PriceCatalog();
        try (InputStream in = open(AWS_URL + "AmazonECS/current/index.json")) {
            parseAwsFargate(readOffer(in, p -> FARGATE_USAGE.matcher(attr(p, "usagetype")).matches()
                    && region.equals(attr(p, "regionCode"))), region, catalog);
        }
        log.info("Downloading the AWS EC2 price list for {}, this can take a few minutes", region);
        try (InputStream in = open(AWS_URL + "AmazonEC2/current/" + region + "/index.json")) {
            Map<JsonNode, List<ReservationOption>> reserved = new HashMap<>();
            Map<JsonNode, Double> offer = readOffer(in, PricingFetcher::isEc2Candidate, reserved);
            parseAwsEc2(offer, reserved, region, catalog);
        }
        return catalog;
    }

    private static String attr(JsonNode product, String name) {
        return product.path("attributes").path(name).asText("");
    }

    private static boolean isEc2Candidate(JsonNode p) {
        String family = p.path("productFamily").asText();
        if ("Compute Instance".equals(family)) {
            return "Linux".equals(attr(p, "operatingSystem")) && "Shared".equals(attr(p, "tenancy"))
                    && "NA".equals(attr(p, "preInstalledSw")) && "Used".equals(attr(p, "capacitystatus"))
                    && "Yes".equals(attr(p, "currentGeneration"));
        }
        return "Storage".equals(family) && "gp3".equals(attr(p, "volumeApiName"));
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
                if (parser.currentToken() == JsonToken.FIELD_NAME && "products".equals(parser.currentName())) {
                    readProducts(parser, filter, products);
                } else if (parser.currentToken() == JsonToken.FIELD_NAME && "OnDemand".equals(parser.currentName())) {
                    readOnDemandPrices(parser, products, priced);
                } else if (reserved != null && parser.currentToken() == JsonToken.FIELD_NAME
                        && "Reserved".equals(parser.currentName())) {
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

    private static Double firstUsdPrice(JsonNode skuTerms) {
        for (JsonNode term : skuTerms) {
            for (JsonNode dim : term.path("priceDimensions")) {
                double usd = dim.path("pricePerUnit").path("USD").asDouble(-1);
                if (usd >= 0) return usd;
            }
        }
        return null;
    }

    static void parseAwsFargate(Map<JsonNode, Double> offer, String region, PriceCatalog out) {
        double cpu = Double.NaN;
        double mem = Double.NaN;
        for (Map.Entry<JsonNode, Double> e : offer.entrySet()) {
            if (attr(e.getKey(), "usagetype").endsWith("vCPU-Hours:perCPU")) {
                cpu = e.getValue();
            } else {
                mem = e.getValue();
            }
        }
        if (!Double.isNaN(cpu) && !Double.isNaN(mem)) {
            PriceCatalog.ContainerRate rate = new PriceCatalog.ContainerRate();
            rate.provider = PROVIDER_AWS;
            rate.region = region;
            rate.service = "Fargate";
            rate.vcpuHour = cpu;
            rate.gbHour = mem;
            out.containers.add(rate);
        }
    }

    static void parseAwsEc2(Map<JsonNode, Double> offer, String region, PriceCatalog out) {
        parseAwsEc2(offer, Collections.<JsonNode, List<ReservationOption>>emptyMap(), region, out);
    }

    static void parseAwsEc2(Map<JsonNode, Double> offer, Map<JsonNode, List<ReservationOption>> reserved, String region,
                            PriceCatalog out) {
        Map<String, PriceCatalog.VmOffer> cheapest = new LinkedHashMap<>();
        for (Map.Entry<JsonNode, Double> e : offer.entrySet()) {
            JsonNode p = e.getKey();
            if ("Storage".equals(p.path("productFamily").asText())) {
                PriceCatalog.StorageRate rate = new PriceCatalog.StorageRate();
                rate.provider = PROVIDER_AWS;
                rate.region = region;
                rate.name = "EBS gp3";
                rate.gbMonth = e.getValue();
                out.storage.add(rate);
                continue;
            }
            String type = attr(p, "instanceType");
            PriceCatalog.VmOffer previous = cheapest.get(type);
            if (previous == null || e.getValue() < previous.hourly) {
                PriceCatalog.VmOffer vm = new PriceCatalog.VmOffer();
                vm.provider = PROVIDER_AWS;
                vm.region = region;
                vm.name = type;
                vm.vcpu = Double.parseDouble(attr(p, "vcpu"));
                vm.memGb = Double.parseDouble(attr(p, "memory").replace("GiB", "").replace(",", "").trim());
                vm.hourly = e.getValue();
                vm.reservations = new ArrayList<>(reserved.getOrDefault(p, Collections.<ReservationOption>emptyList()));
                cheapest.put(type, vm);
            }
        }
        out.vms.addAll(cheapest.values());
    }

    private InputStream open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("Accept", "application/json");
        if (c.getResponseCode() != 200) {
            throw new IOException("HTTP " + c.getResponseCode() + " from " + url.split("\\?")[0]);
        }
        return c.getInputStream();
    }
}
