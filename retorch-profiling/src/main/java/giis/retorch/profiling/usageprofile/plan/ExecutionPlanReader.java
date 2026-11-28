/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.plan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the execution plan written by the orchestrator ({@code .retorch/executionplan.json}):
 * {@code {"tjobs":[{"id","stage","resources","tests","predecessors"}]}}. The profiling module does not depend on the
 * orchestration module, so it has its own small classes for that file.
 */
public final class ExecutionPlanReader {

    /** The properties of every TJob of the file, the contract with the orchestration module (extra ones are ignored). */
    public static final List<String> NODE_FIELDS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList("id", "stage", "resources", "tests", "predecessors"));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExecutionPlanReader() {
    }

    /** A TJob of the plan. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanNode {
        public String id;
        public int stage;
        public List<String> resources = new ArrayList<>();
        public List<String> tests = new ArrayList<>();
        public List<String> predecessors = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PlanFile {
        public List<PlanNode> tjobs = new ArrayList<>();
    }

    /** The TJobs of the plan file, in file order; an empty list if the file has none. */
    public static List<PlanNode> read(Path planJson) throws IOException {
        PlanFile file = MAPPER.readValue(planJson.toFile(), PlanFile.class);
        return file.tjobs == null ? new ArrayList<PlanNode>() : file.tjobs;
    }
}
