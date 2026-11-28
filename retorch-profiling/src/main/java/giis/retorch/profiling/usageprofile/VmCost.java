/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.dto.Alternative;
import giis.retorch.profiling.usageprofile.dto.CostItem;
import giis.retorch.profiling.usageprofile.dto.Phase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Virtual machines: per provider and region, the cheapest 3 VMs of distinct shape that fit the peak CPU and memory. Per
 * second the VM costs {@code hourly/3600}. During a COI phase it is charged to that phase; otherwise it is split between the
 * running TJob phases in proportion to the share of the VM they use (the dominant of CPU and memory) and the unused
 * remainder is the oversubscription. The volume is provisioned at {@code max(1, ceil(peak disk GB))} and split the same
 * way with its prorated GB-month price.
 */
final class VmCost implements AlternativeCost {

    static final String COI = "COI";
    static final String IDLE = "(idle)";
    static final String OVERSUBSCRIPTION = Phase.OVERSUBSCRIPTION.label();
    private static final int VMS_PER_PROVIDER = 3;
    private static final double SECONDS_PER_MONTH = 30.0 * 86400;

    @Override
    public List<Alternative> calculate(CostContext context, List<Alternative> existing) {
        List<Alternative> result = new ArrayList<>();
        double provisionedGb = Math.max(1, Math.ceil(context.peakDiskGb));
        for (String key : providerRegions(context.catalog)) {
            String[] providerRegion = key.split("\\|");
            result.addAll(forProviderRegion(context, providerRegion[0], providerRegion[1], provisionedGb));
        }
        return result;
    }

    private static Set<String> providerRegions(PriceCatalog catalog) {
        Set<String> providerRegions = new LinkedHashSet<>();
        for (PriceCatalog.VmOffer vm : catalog.vms) {
            providerRegions.add(vm.provider + "|" + vm.region);
        }
        return providerRegions;
    }

    private static List<Alternative> forProviderRegion(CostContext context, String provider, String region,
                                                       double provisionedGb) {
        List<PriceCatalog.VmOffer> offers = cheapestFittingShapes(context.catalog, provider, region, context.peakCpu,
                context.peakMemGb);
        if (offers.isEmpty()) {
            context.warnings.add("No " + provider + " VM in " + region + " is large enough for the peak usage ("
                    + CostContext.fmt(context.peakCpu) + " vCPU, " + CostContext.fmt(context.peakMemGb) + " GB)");
        }
        PriceCatalog.StorageRate disk = cheapestStorage(context.catalog, provider, region);
        List<Alternative> result = new ArrayList<>();
        for (PriceCatalog.VmOffer vm : offers) {
            result.add(alternative(context.timeline, vm, disk, provisionedGb));
        }
        return result;
    }

    /** The cheapest VMs of distinct shape of one provider and region that fit the peak (possibly none). */
    private static List<PriceCatalog.VmOffer> cheapestFittingShapes(PriceCatalog catalog, String provider,
                                                                    String region, double peakCpu, double peakMemGb) {
        List<PriceCatalog.VmOffer> fitting = new ArrayList<>();
        for (PriceCatalog.VmOffer vm : catalog.vms) {
            if (vm.provider.equals(provider) && vm.region.equals(region)
                    && vm.vcpu >= peakCpu && vm.memGb >= peakMemGb) {
                fitting.add(vm);
            }
        }
        fitting.sort(Comparator.comparingDouble(v -> v.hourly));
        List<PriceCatalog.VmOffer> chosen = new ArrayList<>();
        Set<String> shapes = new HashSet<>();
        for (PriceCatalog.VmOffer vm : fitting) {
            if (chosen.size() == VMS_PER_PROVIDER) {
                break;
            }
            if (shapes.add(vm.vcpu + "/" + vm.memGb)) {
                chosen.add(vm);
            }
        }
        return chosen;
    }

    private static PriceCatalog.StorageRate cheapestStorage(PriceCatalog catalog, String provider, String region) {
        PriceCatalog.StorageRate best = null;
        for (PriceCatalog.StorageRate s : catalog.storage) {
            if (s.provider.equals(provider) && s.region.equals(region) && (best == null || s.gbMonth < best.gbMonth)) {
                best = s;
            }
        }
        return best;
    }

    private static Alternative alternative(Timeline timeline, PriceCatalog.VmOffer vm, PriceCatalog.StorageRate disk,
                                           double provisionedGb) {
        CostAccumulator acc = new CostAccumulator();
        double vmSecond = vm.hourly / 3600.0;
        double diskSecond = disk == null ? 0 : disk.gbMonth * provisionedGb / SECONDS_PER_MONTH;
        for (Slice s : timeline.slices) {
            if (s.coiPhase != null) {
                acc.add(CostItem.OBJECT_COMPUTE, COI, s.coiPhase, vmSecond);
                acc.add(CostItem.OBJECT_STORAGE, COI, s.coiPhase, diskSecond);
            } else {
                split(acc, CostItem.OBJECT_COMPUTE, s, vmSecond, l -> Math.max(l.cpu / vm.vcpu, l.memGb / vm.memGb));
                split(acc, CostItem.OBJECT_STORAGE, s, diskSecond, l -> l.diskGb / provisionedGb);
            }
        }
        return new Alternative(vm.provider + "/" + vm.region + "/" + vm.name, vm.provider, Alternative.KIND_VM,
                vm.name + " (" + CostContext.fmt(vm.vcpu) + " vCPU, " + CostContext.fmt(vm.memGb) + " GB)", vm.region,
                vm.hourly, acc.items()).withReservations(vm.reservations);
    }

    /** Splits {@code cost} between the loads by their share of the capacity; what is not used is oversubscription. */
    private static void split(CostAccumulator acc, String object, Slice slice, double cost,
                              ToDoubleFunction<Load> share) {
        double total = 0;
        for (Load l : slice.loads) {
            total += share.applyAsDouble(l);
        }
        double divisor = Math.max(1.0, total);
        for (Load l : slice.loads) {
            acc.add(object, l.tjob, l.phase, cost * share.applyAsDouble(l) / divisor);
        }
        acc.add(object, IDLE, OVERSUBSCRIPTION, cost * Math.max(0.0, 1.0 - total));
    }
}
