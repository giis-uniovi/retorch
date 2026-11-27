/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

/** The cost of one (cloud object, TJob, lifecycle phase) combination of an {@link Alternative}. */
public final class CostItem {
    /** The cloud object that is billed: {@code compute} or {@code storage}. */
    public final String object;
    /** The TJob charged, or {@code COI} / {@code (idle)} for the cost that belongs to no TJob. */
    public final String tjob;
    /** The lifecycle phase charged, or {@code oversubscription} for the capacity paid but not used. */
    public final String phase;
    public final double cost;

    public CostItem(String object, String tjob, String phase, double cost) {
        this.object = object;
        this.tjob = tjob;
        this.phase = phase;
        this.cost = cost;
    }
}
