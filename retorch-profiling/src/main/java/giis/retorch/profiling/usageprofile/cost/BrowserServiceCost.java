/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.cost;

import giis.retorch.profiling.usageprofile.measurement.Load;
import giis.retorch.profiling.usageprofile.measurement.Slice;
import giis.retorch.profiling.usageprofile.dto.AlternativeDTO;
import giis.retorch.profiling.usageprofile.dto.CostItemDTO;
import giis.retorch.profiling.usageprofile.dto.Phase;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Browser services (e.g. AWS Device Farm): the SUT keeps running on the cheapest of the other alternatives and every TJob
 * that uses a browser rents one browser session during its test execution phase, billed per minute. Without TJobs that use
 * a browser, or without another alternative, nothing is added and a warning says so.
 */
final class BrowserServiceCost implements AlternativeCost {

    private static final String TEST_EXECUTION = Phase.TJOB_TESTEXEC.label();

    @Override
    public List<AlternativeDTO> calculate(CostContext context, List<AlternativeDTO> existing) {
        if (context.catalog.browsers.isEmpty()) {
            return Collections.emptyList();
        }
        if (context.browserTjobs.isEmpty() || existing.isEmpty()) {
            context.warnings.add("Browser services not calculated: no TJob of the execution plan uses a browser"
                    + " (or there is no execution plan)");
            return Collections.emptyList();
        }
        AlternativeDTO base = existing.get(0);
        List<AlternativeDTO> result = new ArrayList<>();
        for (PriceCatalog.BrowserRate rate : context.catalog.browsers) {
            result.add(alternative(context, base, rate));
        }
        return result;
    }

    private static AlternativeDTO alternative(CostContext context, AlternativeDTO base, PriceCatalog.BrowserRate rate) {
        CostAccumulator acc = new CostAccumulator();
        for (CostItemDTO item : base.items) {
            acc.add(item.object, item.tjob, item.phase, item.cost);
        }
        for (Slice s : context.timeline.slices) {
            for (Load l : s.loads) {
                if (TEST_EXECUTION.equals(l.phase) && context.browserTjobs.contains(l.tjob.toLowerCase(Locale.ROOT))) {
                    acc.add(CostItemDTO.OBJECT_BROWSER, l.tjob, l.phase, rate.minuteUsd / 60.0);
                }
            }
        }
        return new AlternativeDTO(rate.provider + "/" + rate.region + "/" + rate.service + "/" + rate.name
                + "+" + base.id, rate.service, AlternativeDTO.KIND_BROWSER_SERVICE,
                rate.name + " + " + base.provider + " " + base.label, base.region, 0, acc.items());
    }
}
