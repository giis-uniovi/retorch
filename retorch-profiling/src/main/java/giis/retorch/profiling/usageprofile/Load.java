/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

/** Resources consumed, at one second, by the containers of one TJob in a given phase. Immutable. */
public final class Load {
    public final String tjob;
    public final String phase;
    public final double cpu;
    public final double memGb;
    public final double diskGb;

    Load(String tjob, String phase, double cpu, double memGb, double diskGb) {
        this.tjob = tjob;
        this.phase = phase;
        this.cpu = cpu;
        this.memGb = memGb;
        this.diskGb = diskGb;
    }
}
