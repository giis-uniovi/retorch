/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

/** The cost of one (cloud object, TJob, lifecycle phase) combination of an {@link AlternativeDTO}. */
public final class CostItemDTO {
    public static final String OBJECT_COMPUTE = "compute";
    public static final String OBJECT_STORAGE = "storage";
    /** A browser or device rented by the minute. */
    public static final String OBJECT_BROWSER = "browser";

    /** The cloud object that is billed: {@link #OBJECT_COMPUTE}, {@link #OBJECT_STORAGE} or {@link #OBJECT_BROWSER}. */
    public final String object;
    /** The TJob charged, or {@code COI} / {@code (idle)} for the cost that belongs to no TJob. */
    public final String tjob;
    /** The lifecycle phase charged, or {@code oversubscription} for the capacity paid but not used. */
    public final String phase;
    public final double cost;

    public CostItemDTO(String object, String tjob, String phase, double cost) {
        this.object = object;
        this.tjob = tjob;
        this.phase = phase;
        this.cost = cost;
    }
}
