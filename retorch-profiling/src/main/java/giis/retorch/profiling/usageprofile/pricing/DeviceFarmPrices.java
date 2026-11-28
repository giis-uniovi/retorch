/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prices of AWS Device Farm: a single global offer (its prices do not depend on the EC2 regions being compared), so the
 * region passed to {@link #fetch(String)} is ignored. Only the metered per-minute products are kept: Windows desktop
 * browsers (Chrome, Firefox, Edge) and Android / iOS devices.
 */
public final class DeviceFarmPrices implements PriceProvider {

    static final String SERVICE = "AWS Device Farm";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern BROWSER = Pattern.compile("^[A-Z0-9]+-Windows-(Chrome|Firefox|Edge)-automated-minute$");
    private static final Pattern DEVICE = Pattern.compile("^[A-Z0-9]+-(Android|iOS)-Device-Minute$");

    private final HttpGet http;

    public DeviceFarmPrices(HttpGet http) {
        this.http = http;
    }

    @Override
    public String name() {
        return SERVICE;
    }

    @Override
    public String describe(String region) {
        return SERVICE + " prices";
    }

    @Override
    public PriceCatalog fetch(String region) throws IOException {
        PriceCatalog.Builder catalog = PriceCatalog.builder();
        try (InputStream in = http.open(AwsPrices.AWS_URL + "AWSDeviceFarm/current/index.json")) {
            parse(MAPPER.readTree(in), catalog);
        }
        return catalog.build();
    }

    /**
     * Adds the metered per-minute prices of the Device Farm offer. Free, trial, unmetered and remote-access products
     * and the monthly slots are ignored.
     */
    static void parse(JsonNode offer, PriceCatalog.Builder out) {
        JsonNode onDemand = offer.path("terms").path("OnDemand");
        for (JsonNode product : offer.path("products")) {
            String name = rentedName(AwsPrices.attr(product, AwsPrices.USAGE_TYPE));
            Double price = name == null ? null : AwsPrices.firstUsdPrice(onDemand.path(product.path("sku").asText()));
            if (price != null && price > 0) {
                out.addBrowser(new PriceCatalog.BrowserRate(AwsPrices.PROVIDER, AwsPrices.attr(product, "regionCode"),
                        SERVICE, name, price));
            }
        }
    }

    /** "Chrome" for a Windows browser product, "Android devices" / "iOS devices" for a device product, else null. */
    private static String rentedName(String usageType) {
        Matcher browser = BROWSER.matcher(usageType);
        if (browser.matches()) {
            return browser.group(1);
        }
        Matcher device = DEVICE.matcher(usageType);
        return device.matches() ? device.group(1) + " devices" : null;
    }
}
