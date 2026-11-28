/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.cost;

import giis.retorch.profiling.usageprofile.measurement.Load;
import giis.retorch.profiling.usageprofile.measurement.Slice;
import giis.retorch.profiling.usageprofile.measurement.Timeline;
import giis.retorch.profiling.usageprofile.dto.Alternative;
import giis.retorch.profiling.usageprofile.dto.CostItem;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Managed container services: pay-per-use, each TJob phase is charged the vCPU and GB it actually used per second, so
 * there is no oversubscription (the COI phases run outside the service and are not charged; disk is not charged).
 */
final class ContainerCost implements AlternativeCost {

    @Override
    public List<Alternative> calculate(CostContext context, List<Alternative> existing) {
        List<Alternative> result = new ArrayList<>();
        for (PriceCatalog.ContainerRate rate : context.catalog.containers) {
            result.add(alternative(context.timeline, rate));
        }
        return result;
    }

    private static Alternative alternative(Timeline timeline, PriceCatalog.ContainerRate rate) {
        CostAccumulator acc = new CostAccumulator();
        for (Slice s : timeline.slices) {
            for (Load l : s.loads) {
                acc.add(CostItem.OBJECT_COMPUTE, l.tjob, l.phase,
                        (l.cpu * rate.vcpuHour + l.memGb * rate.gbHour) / 3600.0);
            }
        }
        return new Alternative(rate.provider + "/" + rate.region + "/" + rate.service, rate.provider,
                Alternative.KIND_CONTAINER, rate.service, rate.region, 0, acc.items());
    }
}
