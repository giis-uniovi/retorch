/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

/**
 * Resources used by one container (deployed resource) of a TJob. CPU in cores, memory and storage in GB (1024^3).
 * Immutable; the field names are the JSON contract.
 */
public final class ContainerUsageDTO {
    public final String name;
    /** Image name, empty when unknown. */
    public final String image;
    public final double peakCpu;
    public final double avgCpu;
    public final double peakMemGb;
    public final double avgMemGb;
    /** Peak size of the writable layer of the container. */
    public final double layerGb;
    public final double imageGb;
    public final double volumeGb;
    /** {@code layerGb + imageGb + volumeGb}. */
    public final double storageGb;

    /** @param cpuMem values in the order peak CPU, average CPU, peak memory, average memory */
    public ContainerUsageDTO(String name, String image, double[] cpuMem, double layerGb, double imageGb, double volumeGb) {
        this.name = name;
        this.image = image;
        this.peakCpu = cpuMem[0];
        this.avgCpu = cpuMem[1];
        this.peakMemGb = cpuMem[2];
        this.avgMemGb = cpuMem[3];
        this.layerGb = layerGb;
        this.imageGb = imageGb;
        this.volumeGb = volumeGb;
        this.storageGb = layerGb + imageGb + volumeGb;
    }
}
