/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto.cost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One deployment alternative (a VM or a managed container service) and how its cost splits. Immutable. */
public final class AlternativeDTO {
    public static final String KIND_VM = "VM";
    public static final String KIND_CONTAINER = "CONTAINER";
    /** The SUT runs on the cheapest compute alternative and the browsers are rented by the minute. */
    public static final String KIND_BROWSER_SERVICE = "BROWSER_SERVICE";

    public final String id;
    public final String provider;
    /** {@link #KIND_VM}, {@link #KIND_CONTAINER} or {@link #KIND_BROWSER_SERVICE}. */
    public final String kind;
    public final String label;
    public final String region;
    /** Price per hour of a VM; 0 for pay-per-use services. */
    public final double hourlyPrice;
    /** Sum of the cost of all the items. */
    public final double total;
    public final List<CostItemDTO> items;
    /** Reservations of the VM (1 and 3 years); empty for container services and VMs without data. */
    public final List<ReservationOptionDTO> reservations;

    public AlternativeDTO(String id, String provider, String kind, String label, String region, double hourlyPrice,
                       List<CostItemDTO> items) {
        this.id = id;
        this.provider = provider;
        this.kind = kind;
        this.label = label;
        this.region = region;
        this.hourlyPrice = hourlyPrice;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        double sum = 0;
        for (CostItemDTO item : items) {
            sum += item.cost;
        }
        this.total = sum;
        this.reservations = Collections.emptyList();
    }

    private AlternativeDTO(AlternativeDTO base, List<ReservationOptionDTO> reservations) {
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
    public AlternativeDTO withReservations(List<ReservationOptionDTO> reservations) {
        return new AlternativeDTO(this, reservations);
    }
}
