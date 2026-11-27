/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One deployment alternative (a VM or a managed container service) and how its cost splits. Immutable. */
public final class Alternative {
    public static final String KIND_VM = "VM";
    public static final String KIND_CONTAINER = "CONTAINER";

    public final String id;
    public final String provider;
    /** {@link #KIND_VM} or {@link #KIND_CONTAINER}. */
    public final String kind;
    public final String label;
    public final String region;
    /** Price per hour of a VM; 0 for pay-per-use services. */
    public final double hourlyPrice;
    /** Sum of the cost of all the items. */
    public final double total;
    public final List<CostItem> items;
    /** Reservations of the VM (1 and 3 years); empty for container services and VMs without data. */
    public final List<ReservationOption> reservations;

    public Alternative(String id, String provider, String kind, String label, String region, double hourlyPrice,
                       List<CostItem> items) {
        this.id = id;
        this.provider = provider;
        this.kind = kind;
        this.label = label;
        this.region = region;
        this.hourlyPrice = hourlyPrice;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        double sum = 0;
        for (CostItem item : items) {
            sum += item.cost;
        }
        this.total = sum;
        this.reservations = Collections.emptyList();
    }

    private Alternative(Alternative base, List<ReservationOption> reservations) {
        this.id = base.id;
        this.provider = base.provider;
        this.kind = base.kind;
        this.label = base.label;
        this.region = base.region;
        this.hourlyPrice = base.hourlyPrice;
        this.items = base.items;
        this.total = base.total;
        this.reservations = Collections.unmodifiableList(new ArrayList<>(reservations));
    }

    /** A copy of this alternative that carries the given reservations (the original is not modified). */
    public Alternative withReservations(List<ReservationOption> reservations) {
        return new Alternative(this, reservations);
    }
}
