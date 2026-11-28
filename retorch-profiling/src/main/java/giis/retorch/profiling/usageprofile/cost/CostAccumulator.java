/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.cost;

import giis.retorch.profiling.usageprofile.dto.CostItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates costs by (object, tjob, phase), keeping the order in which each combination first appeared. */
final class CostAccumulator {
    private final Map<String, CostItem> items = new LinkedHashMap<>();

    void add(String object, String tjob, String phase, double cost) {
        String key = object + "|" + tjob + "|" + phase;
        CostItem previous = items.get(key);
        items.put(key, new CostItem(object, tjob, phase, (previous == null ? 0 : previous.cost) + cost));
    }

    List<CostItem> items() {
        return new ArrayList<>(items.values());
    }
}
