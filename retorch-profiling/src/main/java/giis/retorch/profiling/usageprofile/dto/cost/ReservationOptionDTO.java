/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto.cost;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A reservation (committed use) of a VM: for how many years and how it is paid, and the hourly price that is really
 * paid for the reserved VM, running or not. Immutable. It is part of the JSON contract (alternative.reservations).
 */
public final class ReservationOptionDTO {
    /** Hours of a year used to spread an upfront fee over the term. */
    public static final int HOURS_PER_YEAR = 8760;

    /** How the reservation is paid. */
    public enum Payment { NO_UPFRONT, ALL_UPFRONT }

    /** Length of the commitment in years: 1 or 3. */
    public final int termYears;
    public final Payment payment;
    /**
     * USD per hour paid 24x7: the hourly rate for {@link Payment#NO_UPFRONT}; the upfront fee divided by
     * {@code termYears * 8760} for {@link Payment#ALL_UPFRONT}.
     */
    public final double effectiveHourly;

    @JsonCreator
    public ReservationOptionDTO(@JsonProperty("termYears") int termYears, @JsonProperty("payment") Payment payment,
                             @JsonProperty("effectiveHourly") double effectiveHourly) {
        this.termYears = termYears;
        this.payment = payment;
        this.effectiveHourly = effectiveHourly;
    }

    /** An option paid in full at the start: the fee of the whole term is spread over its hours. */
    public static ReservationOptionDTO allUpfront(int termYears, double upfrontFee) {
        return new ReservationOptionDTO(termYears, Payment.ALL_UPFRONT, upfrontFee / ((double) termYears * HOURS_PER_YEAR));
    }
}
