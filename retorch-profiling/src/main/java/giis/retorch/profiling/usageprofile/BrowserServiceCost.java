/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.measurement.Load;
import giis.retorch.profiling.usageprofile.measurement.Slice;
import giis.retorch.profiling.usageprofile.dto.Alternative;
import giis.retorch.profiling.usageprofile.dto.CostItem;
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
    public List<Alternative> calculate(CostContext context, List<Alternative> existing) {
        if (context.catalog.browsers.isEmpty()) {
            return Collections.emptyList();
        }
        if (context.browserTjobs.isEmpty() || existing.isEmpty()) {
            context.warnings.add("Browser services not calculated: no TJob of the execution plan uses a browser"
                    + " (or there is no execution plan)");
            return Collections.emptyList();
        }
        Alternative base = existing.get(0);
        List<Alternative> result = new ArrayList<>();
        for (PriceCatalog.BrowserRate rate : context.catalog.browsers) {
            result.add(alternative(context, base, rate));
        }
        return result;
    }

    private static Alternative alternative(CostContext context, Alternative base, PriceCatalog.BrowserRate rate) {
        CostAccumulator acc = new CostAccumulator();
        for (CostItem item : base.items) {
            acc.add(item.object, item.tjob, item.phase, item.cost);
        }
        for (Slice s : context.timeline.slices) {
            for (Load l : s.loads) {
                if (TEST_EXECUTION.equals(l.phase) && context.browserTjobs.contains(l.tjob.toLowerCase(Locale.ROOT))) {
                    acc.add(CostItem.OBJECT_BROWSER, l.tjob, l.phase, rate.minuteUsd / 60.0);
                }
            }
        }
        return new Alternative(rate.provider + "/" + rate.region + "/" + rate.service + "/" + rate.name
                + "+" + base.id, rate.service, Alternative.KIND_BROWSER_SERVICE,
                rate.name + " + " + base.provider + " " + base.label, base.region, 0, acc.items());
    }
}
