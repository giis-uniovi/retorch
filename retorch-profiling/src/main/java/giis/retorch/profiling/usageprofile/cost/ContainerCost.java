/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.cost;

import giis.retorch.profiling.usageprofile.dto.cost.AlternativeDTO;
import giis.retorch.profiling.usageprofile.dto.cost.CostItemDTO;
import giis.retorch.profiling.usageprofile.measurement.Load;
import giis.retorch.profiling.usageprofile.measurement.Slice;
import giis.retorch.profiling.usageprofile.measurement.Timeline;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Managed container services: pay-per-use, each TJob phase is charged the vCPU and GB it actually used per second, so
 * there is no oversubscription (the COI phases run outside the service and are not charged; disk is not charged).
 */
final class ContainerCost implements AlternativeCost {

    @Override
    public List<AlternativeDTO> calculate(CostContext context, List<AlternativeDTO> existing) {
        List<AlternativeDTO> result = new ArrayList<>();
        for (PriceCatalog.ContainerRate rate : context.catalog.containers) {
            result.add(alternative(context.timeline, rate));
        }
        return result;
    }

    private static AlternativeDTO alternative(Timeline timeline, PriceCatalog.ContainerRate rate) {
        CostAccumulator acc = new CostAccumulator();
        for (Slice s : timeline.slices) {
            for (Load l : s.loads) {
                acc.add(CostItemDTO.OBJECT_COMPUTE, l.tjob, l.phase,
                        (l.cpu * rate.vcpuHour + l.memGb * rate.gbHour) / 3600.0);
            }
        }
        return new AlternativeDTO(rate.provider + "/" + rate.region + "/" + rate.service, rate.provider,
                AlternativeDTO.KIND_CONTAINER, rate.service, rate.region, 0, acc.items());
    }
}
