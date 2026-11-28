/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.ReservationOptionDTO;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prices of Azure from the public Retail Prices API: VMs (with their reservations), Container Instances and the
 * Standard SSD E10 disk. Azure gives no vCPU/RAM, so the VM sizes considered are a fixed list.
 */
public final class AzurePrices implements PriceProvider {

    static final String PROVIDER = "Azure";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AZURE_URL = "https://prices.azure.com/api/retail/prices?$filter=";
    private static final double E10_DISK_GB = 128;
    private static final String RETAIL_PRICE = "retailPrice";
    private static final String UNIT_OF_MEASURE = "unitOfMeasure";
    private static final String METER_NAME = "meterName";
    private static final String VM_SERVICE = "serviceName eq 'Virtual Machines' and ";
    private static final String HOUR = "1 Hour";

    /** name, vCPU, GB. */
    private static final Object[][] VM_SIZES = {
            {"Standard_B2s", 2, 4}, {"Standard_B2ms", 2, 8}, {"Standard_B4ms", 4, 16},
            {"Standard_D2s_v5", 2, 8}, {"Standard_D4s_v5", 4, 16}, {"Standard_D8s_v5", 8, 32},
            {"Standard_D16s_v5", 16, 64}, {"Standard_D32s_v5", 32, 128},
            {"Standard_E2s_v5", 2, 16}, {"Standard_E4s_v5", 4, 32}, {"Standard_E8s_v5", 8, 64},
            {"Standard_F2s_v2", 2, 4}, {"Standard_F4s_v2", 4, 8}, {"Standard_F8s_v2", 8, 16},
            {"Standard_F16s_v2", 16, 32}};

    private final HttpGet http;

    public AzurePrices(HttpGet http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "azure";
    }

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public PriceCatalog fetch(String region) throws IOException {
        PriceCatalog.Builder catalog = PriceCatalog.builder();
        List<String> filters = new ArrayList<>();
        for (Object[] size : VM_SIZES) {
            filters.add("armSkuName eq '" + size[0] + "'");
        }
        String names = String.join(" or ", filters);
        String regionFilter = "armRegionName eq '" + region + "' and ";
        String base = regionFilter + "priceType eq 'Consumption' and ";
        parseVms(items(VM_SERVICE + base + "(" + names + ")"), region, catalog);
        parseReservations(items(VM_SERVICE + regionFilter + "priceType eq 'Reservation' and (" + names + ")"),
                region, catalog);
        parseContainers(items("serviceName eq 'Container Instances' and " + base + "skuName eq 'Standard'"),
                region, catalog);
        parseDisks(items("serviceName eq 'Storage' and " + base
                + "productName eq 'Standard SSD Managed Disks' and skuName eq 'E10 LRS'"), region, catalog);
        return catalog.build();
    }

    private JsonNode[] items(String filter) throws IOException {
        List<JsonNode> items = new ArrayList<>();
        String url = AZURE_URL + URLEncoder.encode(filter, "UTF-8").replace("+", "%20");
        while (url != null && !url.isEmpty()) {
            try (InputStream in = http.open(url)) {
                JsonNode page = MAPPER.readTree(in);
                for (JsonNode item : page.path("Items")) {
                    items.add(item);
                }
                url = page.path("NextPageLink").asText(null);
            }
        }
        return items.toArray(new JsonNode[0]);
    }

    static void parseVms(JsonNode[] items, String region, PriceCatalog.Builder out) {
        Map<String, double[]> sizes = new HashMap<>();
        for (Object[] s : VM_SIZES) {
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
            double[] size = sizes.get(e.getKey());
            out.addVm(new PriceCatalog.VmOffer(PROVIDER, region, e.getKey(), size[0], size[1], e.getValue()));
        }
    }

    /**
     * Adds to the VMs already in {@code out} their reservations. Azure gives the total price of the whole term in
     * {@code retailPrice} ("1 Year" / "3 Years" in {@code reservationTerm}), so it is an all-upfront option; the cheapest
     * price of each term wins.
     */
    static void parseReservations(JsonNode[] items, String region, PriceCatalog.Builder out) {
        Map<String, Double> bestFee = new HashMap<>();
        List<PriceCatalog.VmOffer> current = out.vms();
        Set<String> known = new HashSet<>();
        for (PriceCatalog.VmOffer vm : current) {
            if (PROVIDER.equals(vm.provider) && region.equals(vm.region)) {
                known.add(vm.name);
            }
        }
        for (JsonNode item : items) {
            int years = termYears(item.path("reservationTerm").asText());
            String name = item.path("armSkuName").asText();
            if (years > 0 && known.contains(name) && !item.path("productName").asText().contains("Windows")) {
                bestFee.merge(name + "|" + years, item.path(RETAIL_PRICE).asDouble(), Math::min);
            }
        }
        List<PriceCatalog.VmOffer> updated = new ArrayList<>();
        for (PriceCatalog.VmOffer vm : current) {
            updated.add(known.contains(vm.name) && PROVIDER.equals(vm.provider) && region.equals(vm.region)
                    ? vm.withReservations(reservationsOf(vm, bestFee)) : vm);
        }
        out.replaceVms(updated);
    }

    private static List<ReservationOptionDTO> reservationsOf(PriceCatalog.VmOffer vm, Map<String, Double> bestFee) {
        List<ReservationOptionDTO> result = new ArrayList<>(vm.reservations);
        for (Map.Entry<String, Double> e : bestFee.entrySet()) {
            String[] key = e.getKey().split("\\|");
            if (key[0].equals(vm.name)) {
                result.add(ReservationOptionDTO.allUpfront(Integer.parseInt(key[1]), e.getValue()));
            }
        }
        result.sort((a, b) -> Integer.compare(a.termYears, b.termYears));
        return result;
    }

    private static int termYears(String reservationTerm) {
        if ("1 Year".equals(reservationTerm)) {
            return 1;
        }
        return "3 Years".equals(reservationTerm) ? 3 : 0;
    }

    static void parseContainers(JsonNode[] items, String region, PriceCatalog.Builder out) {
        double cpu = Double.NaN;
        double mem = Double.NaN;
        for (JsonNode item : items) {
            String meter = item.path(METER_NAME).asText();
            if ("Standard vCPU Duration".equals(meter)) {
                cpu = item.path(RETAIL_PRICE).asDouble();
            } else if ("Standard Memory Duration".equals(meter)) {
                mem = item.path(RETAIL_PRICE).asDouble();
            }
        }
        if (!Double.isNaN(cpu) && !Double.isNaN(mem)) {
            out.addContainer(new PriceCatalog.ContainerRate(PROVIDER, region, "Container Instances", cpu, mem));
        }
    }

    static void parseDisks(JsonNode[] items, String region, PriceCatalog.Builder out) {
        for (JsonNode item : items) {
            if (item.path(METER_NAME).asText().endsWith(" Disk") && item.path(UNIT_OF_MEASURE).asText().contains("Month")) {
                out.addStorage(new PriceCatalog.StorageRate(PROVIDER, region, "Standard SSD (E10)",
                        item.path(RETAIL_PRICE).asDouble() / E10_DISK_GB));
                return;
            }
        }
    }
}
