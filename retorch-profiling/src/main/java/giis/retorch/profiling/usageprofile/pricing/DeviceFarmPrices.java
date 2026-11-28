/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prices of AWS Device Farm: a single global offer (its prices do not depend on the EC2 regions being compared), so the
 * region passed to {@link #fetch(String)} is ignored. Only the metered per-minute products are kept: Windows desktop
 * browsers (Chrome, Firefox, Edge) and Android / iOS devices. Its entries are labelled with the provider {@code AWS}.
 */
public final class DeviceFarmPrices extends AbstractPriceProvider {

    static final String SERVICE = "AWS Device Farm";
    private static final Pattern BROWSER = Pattern.compile("^[A-Z0-9]+-Windows-(Chrome|Firefox|Edge)-automated-minute$");
    private static final Pattern DEVICE = Pattern.compile("^[A-Z0-9]+-(Android|iOS)-Device-Minute$");

    public DeviceFarmPrices(HttpGet http) {
        super(http, "aws-devicefarm", SERVICE, AwsPrices.PROVIDER);
    }

    /** Follows the AWS regions setting: it is enabled when AWS is. */
    @Override
    public String configId() {
        return "aws";
    }

    /** Global: one fetch, whatever the regions, and only when AWS has regions. */
    @Override
    public List<String> regionsFor(ProviderRegions config) {
        return config.regions(configId()).isEmpty() ? Collections.<String>emptyList() : Collections.singletonList("global");
    }

    @Override
    public String describe(String region) {
        return SERVICE + " prices";
    }

    @Override
    protected void collect(String region, PriceCatalog.Builder catalog) throws IOException {
        parse(readJson(AwsPrices.AWS_URL + "AWSDeviceFarm/current/index.json"), catalog);
    }

    /**
     * Adds the metered per-minute prices of the Device Farm offer. Free, trial, unmetered and remote-access products
     * and the monthly slots are ignored.
     */
    void parse(JsonNode offer, PriceCatalog.Builder out) {
        JsonNode onDemand = offer.path("terms").path("OnDemand");
        for (JsonNode product : offer.path("products")) {
            String name = rentedName(attr(product, USAGE_TYPE));
            Double price = name == null ? null : firstUsdPrice(onDemand.path(product.path("sku").asText()));
            if (price != null && price > 0) {
                out.addBrowser(browser(attr(product, "regionCode"), SERVICE, name, price));
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
