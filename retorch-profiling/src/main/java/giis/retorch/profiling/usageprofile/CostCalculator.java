/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.measurement.Timeline;
import giis.retorch.profiling.usageprofile.dto.Alternative;
import giis.retorch.profiling.usageprofile.dto.CostItem;
import giis.retorch.profiling.usageprofile.dto.CostReport;
import giis.retorch.profiling.usageprofile.dto.Phase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Computes, for every deployment alternative (VMs of the different providers, managed container services and browser
 * services), what the measured execution would cost and how that cost is split between the TJob phases and the cloud
 * objects. It only orchestrates: each kind of alternative is an {@link AlternativeCost} strategy ({@link VmCost},
 * {@link ContainerCost}, {@link BrowserServiceCost}) and the result is sorted by total, cheapest first.
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
    public static final String COI = VmCost.COI;
    public static final String IDLE = VmCost.IDLE;
    public static final String OVERSUBSCRIPTION = Phase.OVERSUBSCRIPTION.label();

    private static final Comparator<Alternative> BY_TOTAL = Comparator.comparingDouble((Alternative a) -> a.total);
    private final AlternativeCost vms = new VmCost();
    private final AlternativeCost containers = new ContainerCost();
    private final AlternativeCost browserServices = new BrowserServiceCost();

    public CostReport calculate(Timeline timeline, PriceCatalog catalog) {
        return calculate(timeline, catalog, Collections.<String>emptySet());
    }

    /**
     * Same as above; {@code browserTjobs} are the (case-insensitive) ids of the TJobs that use a browser, which
     * the browser services of the catalog are charged for.
     */
    public CostReport calculate(Timeline timeline, PriceCatalog catalog, Set<String> browserTjobs) {
        CostContext context = new CostContext(timeline, catalog, lowerCase(browserTjobs));
        List<Alternative> alternatives = new ArrayList<>();
        alternatives.addAll(vms.calculate(context, alternatives));
        alternatives.addAll(containers.calculate(context, alternatives));
        alternatives.sort(BY_TOTAL);
        alternatives.addAll(browserServices.calculate(context, alternatives));
        alternatives.sort(BY_TOTAL);
        return new CostReport(context.peakCpu, context.peakMemGb, context.peakDiskGb, timeline.slices.size(),
                alternatives, context.warnings);
    }

    private static Set<String> lowerCase(Set<String> ids) {
        Set<String> result = new HashSet<>();
        for (String id : ids) {
            result.add(id.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
