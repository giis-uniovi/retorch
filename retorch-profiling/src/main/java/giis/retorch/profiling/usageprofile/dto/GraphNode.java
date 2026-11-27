/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One TJob of the execution graph, as the usage profile receives it. The measured fields are {@code null} when the TJob has no
 * measurements. Immutable; the field names are the JSON contract.
 */
public final class GraphNode {
    public final String id;
    public final int stage;
    public final List<String> resources;
    /** Tests executed, as {@code Class#method}. */
    public final List<String> tests;
    /** Ids of the TJobs that must finish before this one. */
    public final List<String> predecessors;
    /** Seconds from the start of the execution to the start of the TJob set-up. */
    public final Double startSec;
    public final Double setupSec;
    public final Double execSec;
    public final Double teardownSec;
    /** Highest CPU (cores) used by the TJob. */
    public final Double peakCpu;
    /** Highest memory (GB) used by the TJob. */
    public final Double peakMemGb;
    /** Usage per container, sorted by name; empty when there is no container information. Never null. */
    public final List<ContainerUsage> containers;

    /** Plan data of a node plus its (nullable) measured values and no container information. */
    public GraphNode(String id, int stage, List<String> resources, List<String> tests, List<String> predecessors,
                     Double[] measured) {
        this(id, stage, resources, tests, predecessors, measured, null);
    }

    /** Plan data of a node plus its (nullable) measured values and its containers ({@code null} means none). */
    public GraphNode(String id, int stage, List<String> resources, List<String> tests, List<String> predecessors,
                     Double[] measured, List<ContainerUsage> containers) {
        this.id = id;
        this.stage = stage;
        this.resources = resources;
        this.tests = tests;
        this.predecessors = predecessors;
        this.startSec = measured[0];
        this.setupSec = measured[1];
        this.execSec = measured[2];
        this.teardownSec = measured[3];
        this.peakCpu = measured[4];
        this.peakMemGb = measured[5];
        this.containers = containers == null ? Collections.<ContainerUsage>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(containers));
    }
}
