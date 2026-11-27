/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.ReservationOption;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the {@code terms.Reserved} section of an AWS EC2 offer file. Per sku it keeps the standard-class reservations of
 * 1 and 3 years paid "No Upfront" (hourly dimension, unit {@code Hrs}) or "All Upfront" (dimension with unit
 * {@code Quantity}: the upfront fee of the whole term). Convertible and partial-upfront terms are ignored.
 */
final class AwsReservations {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STANDARD = "standard";
    private static final String NO_UPFRONT = "No Upfront";
    private static final String ALL_UPFRONT = "All Upfront";
    private static final String UNIT = "unit";

    private AwsReservations() {
    }

    /**
     * Positioned on the "Reserved" field: for every sku of {@code products} stores its reservation options in
     * {@code out} (keyed by the product); the other skus are skipped without loading them.
     */
    static void read(JsonParser parser, Map<String, JsonNode> products, Map<JsonNode, List<ReservationOption>> out)
            throws IOException {
        parser.nextToken();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String sku = parser.currentName();
            parser.nextToken();
            if (!products.containsKey(sku)) {
                parser.skipChildren();
                continue;
            }
            List<ReservationOption> options = options(MAPPER.readTree(parser));
            if (!options.isEmpty()) {
                out.put(products.get(sku), options);
            }
        }
    }

    /** The reservation options of the terms of one sku, the cheapest effective price for each (term, payment). */
    static List<ReservationOption> options(JsonNode skuTerms) {
        List<ReservationOption> result = new ArrayList<>();
        for (JsonNode term : skuTerms) {
            ReservationOption option = option(term);
            if (option != null) {
                addCheapest(result, option);
            }
        }
        return result;
    }

    private static void addCheapest(List<ReservationOption> options, ReservationOption candidate) {
        for (int i = 0; i < options.size(); i++) {
            ReservationOption o = options.get(i);
            if (o.termYears == candidate.termYears && o.payment == candidate.payment) {
                if (candidate.effectiveHourly < o.effectiveHourly) {
                    options.set(i, candidate);
                }
                return;
            }
        }
        options.add(candidate);
    }

    /** The option of one term, or null if it is not a standard 1/3 year No Upfront or All Upfront term. */
    private static ReservationOption option(JsonNode term) {
        JsonNode attrs = term.path("termAttributes");
        int years = years(attrs.path("LeaseContractLength").asText());
        if (years == 0 || !STANDARD.equals(attrs.path("OfferingClass").asText())) {
            return null;
        }
        String purchase = attrs.path("PurchaseOption").asText();
        if (NO_UPFRONT.equals(purchase)) {
            double hourly = dimensionPrice(term, "Hrs");
            return hourly < 0 ? null : new ReservationOption(years, ReservationOption.Payment.NO_UPFRONT, hourly);
        }
        if (ALL_UPFRONT.equals(purchase)) {
            double fee = dimensionPrice(term, "Quantity");
            return fee < 0 ? null : ReservationOption.allUpfront(years, fee);
        }
        return null;
    }

    private static int years(String leaseContractLength) {
        if ("1yr".equals(leaseContractLength)) {
            return 1;
        }
        return "3yr".equals(leaseContractLength) ? 3 : 0;
    }

    /** USD of the first price dimension with the given unit, or -1 if the term has none. */
    private static double dimensionPrice(JsonNode term, String unit) {
        for (JsonNode dim : term.path("priceDimensions")) {
            if (unit.equals(dim.path(UNIT).asText())) {
                return dim.path("pricePerUnit").path("USD").asDouble(-1);
            }
        }
        return -1;
    }
}
