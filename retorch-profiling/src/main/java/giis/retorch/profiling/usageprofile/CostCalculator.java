/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.ExecutionMeasurements.Load;
import giis.retorch.profiling.usageprofile.ExecutionMeasurements.Slice;
import giis.retorch.profiling.usageprofile.ExecutionMeasurements.Timeline;
import giis.retorch.profiling.usageprofile.dto.Alternative;
import giis.retorch.profiling.usageprofile.dto.CostItem;
import giis.retorch.profiling.usageprofile.dto.CostReport;
import giis.retorch.profiling.usageprofile.dto.Phase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Computes, for every deployment alternative (VMs of the different providers and managed container services), what
 * the measured execution would cost and how that cost is split between the TJob phases and the cloud objects.
 * <p>
 * Cost model. Every second of the execution is a cost slice:
 * <ul>
 *   <li>VM: the slice costs {@code hourly/3600}. During a COI phase it is charged to that phase. Otherwise it is split
 *   between the running TJob phases in proportion to the share of the VM they use (the dominant of CPU and memory),
 *   and the unused remainder is the <b>oversubscription</b>.</li>
 *   <li>Volume: same rule, over the provisioned GB (the disk peak) and the prorated GB-month price.</li>
 *   <li>Container service: pay-per-use, each TJob phase is charged the vCPU and GB it actually used, so there is no
 *   oversubscription (the COI phases run outside the service and are not charged).</li>
 *   <li>Browser service (e.g. AWS Device Farm): the SUT keeps running on the cheapest of the alternatives above and
 *   every TJob that uses a browser rents one browser session during its test execution phase, billed per minute.</li>
 * </ul>
 */
public class CostCalculator {

    public static final String COMPUTE = CostItem.OBJECT_COMPUTE;
    public static final String STORAGE = CostItem.OBJECT_STORAGE;
    public static final String BROWSER = CostItem.OBJECT_BROWSER;
    private static final String TEST_EXECUTION = Phase.TJOB_TESTEXEC.label();
    public static final String COI = "COI";
    public static final String IDLE = "(idle)";
    public static final String OVERSUBSCRIPTION = Phase.OVERSUBSCRIPTION.label();
    private static final int VMS_PER_PROVIDER = 3;
    private static final double SECONDS_PER_MONTH = 30.0 * 86400;

    /** Aggregates costs by (object, tjob, phase), keeping the order in which each combination first appeared. */
    private static final class Accumulator {
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

    public CostReport calculate(Timeline timeline, PriceCatalog catalog) {
        return calculate(timeline, catalog, Collections.<String>emptySet());
    }

    /**
     * Same as above; {@code browserTjobs} are the (case-insensitive) ids of the TJobs that use a browser, which
     * the browser services of the catalog are charged for.
     */
    public CostReport calculate(Timeline timeline, PriceCatalog catalog, Set<String> browserTjobs) {
        double peakCpu = timeline.peak("cpu");
        double peakMemGb = timeline.peak("mem");
        double peakDiskGb = timeline.peak("disk");
        List<String> warnings = new ArrayList<>(catalog.warnings);
        List<Alternative> alternatives = new ArrayList<>();
        double provisionedGb = Math.max(1, Math.ceil(peakDiskGb));

        Set<String> providerRegions = new LinkedHashSet<>();
        for (PriceCatalog.VmOffer vm : catalog.vms) {
            providerRegions.add(vm.provider + "|" + vm.region);
        }
        for (String key : providerRegions) {
            String[] providerRegion = key.split("\\|");
            List<PriceCatalog.VmOffer> offers = cheapestFittingShapes(catalog, providerRegion[0], providerRegion[1],
                    peakCpu, peakMemGb);
            if (offers.isEmpty()) {
                warnings.add("No " + providerRegion[0] + " VM in " + providerRegion[1]
                        + " is large enough for the peak usage (" + fmt(peakCpu) + " vCPU, " + fmt(peakMemGb)
                        + " GB)");
            }
            PriceCatalog.StorageRate disk = cheapestStorage(catalog, providerRegion[0], providerRegion[1]);
            for (PriceCatalog.VmOffer vm : offers) {
                alternatives.add(vmAlternative(timeline, vm, disk, provisionedGb));
            }
        }
        for (PriceCatalog.ContainerRate rate : catalog.containers) {
            alternatives.add(containerAlternative(timeline, rate));
        }
        alternatives.sort(Comparator.comparingDouble((Alternative a) -> a.total));
        addBrowserServices(timeline, catalog, browserTjobs, alternatives, warnings);
        return new CostReport(peakCpu, peakMemGb, peakDiskGb, timeline.slices.size(), alternatives, warnings);
    }

    /** Adds, for every browser service, the cheapest alternative above plus the browser minutes of the TJobs. */
    private static void addBrowserServices(Timeline timeline, PriceCatalog catalog, Set<String> browserTjobs,
                                           List<Alternative> alternatives, List<String> warnings) {
        if (catalog.browsers.isEmpty()) {
            return;
        }
        if (browserTjobs.isEmpty() || alternatives.isEmpty()) {
            warnings.add("Browser services not calculated: no TJob of the execution plan uses a browser"
                    + " (or there is no execution plan)");
            return;
        }
        Set<String> browsers = new HashSet<>();
        for (String id : browserTjobs) {
            browsers.add(id.toLowerCase());
        }
        Alternative base = alternatives.get(0);
        for (PriceCatalog.BrowserRate rate : catalog.browsers) {
            Accumulator acc = new Accumulator();
            for (CostItem item : base.items) {
                acc.add(item.object, item.tjob, item.phase, item.cost);
            }
            for (Slice s : timeline.slices) {
                for (Load l : s.loads) {
                    if (TEST_EXECUTION.equals(l.phase) && browsers.contains(l.tjob.toLowerCase())) {
                        acc.add(BROWSER, l.tjob, l.phase, rate.minuteUsd / 60.0);
                    }
                }
            }
            alternatives.add(new Alternative(rate.provider + "/" + rate.region + "/" + rate.service + "/" + rate.name
                    + "+" + base.id, rate.service, Alternative.KIND_BROWSER_SERVICE,
                    rate.name + " + " + base.provider + " " + base.label, base.region, 0, acc.items()));
        }
        alternatives.sort(Comparator.comparingDouble((Alternative a) -> a.total));
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

    private static Alternative vmAlternative(Timeline timeline, PriceCatalog.VmOffer vm,
                                             PriceCatalog.StorageRate disk, double provisionedGb) {
        Accumulator acc = new Accumulator();
        double vmSecond = vm.hourly / 3600.0;
        double diskSecond = disk == null ? 0 : disk.gbMonth * provisionedGb / SECONDS_PER_MONTH;
        for (Slice s : timeline.slices) {
            if (s.coiPhase != null) {
                acc.add(COMPUTE, COI, s.coiPhase, vmSecond);
                acc.add(STORAGE, COI, s.coiPhase, diskSecond);
            } else {
                split(acc, COMPUTE, s, vmSecond, l -> Math.max(l.cpu / vm.vcpu, l.memGb / vm.memGb));
                split(acc, STORAGE, s, diskSecond, l -> l.diskGb / provisionedGb);
            }
        }
        return new Alternative(vm.provider + "/" + vm.region + "/" + vm.name, vm.provider, Alternative.KIND_VM,
                vm.name + " (" + fmt(vm.vcpu) + " vCPU, " + fmt(vm.memGb) + " GB)", vm.region, vm.hourly,
                acc.items()).withReservations(vm.reservations);
    }

    /** Splits {@code cost} between the loads by their share of the capacity; what is not used is oversubscription. */
    private static void split(Accumulator acc, String object, Slice slice, double cost, ToDoubleFunction<Load> share) {
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

    private static Alternative containerAlternative(Timeline timeline, PriceCatalog.ContainerRate rate) {
        Accumulator acc = new Accumulator();
        for (Slice s : timeline.slices) {
            for (Load l : s.loads) {
                acc.add(COMPUTE, l.tjob, l.phase, (l.cpu * rate.vcpuHour + l.memGb * rate.gbHour) / 3600.0);
            }
        }
        return new Alternative(rate.provider + "/" + rate.region + "/" + rate.service, rate.provider,
                Alternative.KIND_CONTAINER, rate.service, rate.region, 0, acc.items());
    }

    /** Two decimals at most, without trailing zeros: 2.0 -> "2", 1.456 -> "1.46". */
    private static String fmt(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
