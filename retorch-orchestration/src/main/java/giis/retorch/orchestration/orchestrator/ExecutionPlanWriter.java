/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes the execution graph of the pipeline ({@code .retorch/executionplan.json}): for every TJob, its stage, the
 * resources it deploys, the tests it executes and the TJobs that must finish before it. The usage profile reads it to
 * draw the graph. TJob ids are lower case, as they appear in the pipeline and in the usage file.
 */
public class ExecutionPlanWriter {

    public static final String FILE = ".retorch/executionplan.json";

    /** One TJob of the graph. */
    public static final class Node {
        public final String id;
        public final int stage;
        public final List<String> resources;
        /** Tests as passed to Maven: {@code Class#method}. */
        public final List<String> tests;
        public final List<String> predecessors;

        Node(String id, int stage, List<String> resources, List<String> tests, List<String> predecessors) {
            this.id = id;
            this.stage = stage;
            this.resources = resources;
            this.tests = tests;
            this.predecessors = predecessors;
        }
    }

    /** The content of the file. */
    public static final class PlanFile {
        public final List<Node> tjobs;

        PlanFile(List<Node> tjobs) {
            this.tjobs = tjobs;
        }
    }

    /** Builds the graph from the activities of the scheduler, ordered by stage. */
    public PlanFile toPlanFile(Map<Integer, LinkedList<Activity>> activitiesByStage) {
        List<Node> nodes = new ArrayList<>();
        for (Map.Entry<Integer, LinkedList<Activity>> stage : new TreeMap<>(activitiesByStage).entrySet()) {
            for (Activity activity : stage.getValue()) {
                nodes.add(toNode(activity, stage.getKey()));
            }
        }
        return new PlanFile(nodes);
    }

    private static Node toNode(Activity activity, int stage) {
        TJob tjob = activity.getTJob();
        List<String> resources = new ArrayList<>();
        for (Resource resource : tjob.getListResourceClasses()) {
            resources.add(resource.getResourceID());
        }
        List<String> tests = new ArrayList<>();
        for (TestCase test : tjob.getListTestCases()) {
            tests.add(test.getTestClass().getSimpleName() + "#" + test.getName());
        }
        // the scheduler can list the same predecessor more than once: the graph needs each dependency once
        Set<String> predecessors = new LinkedHashSet<>();
        for (Activity predecessor : activity.getListPredecessors()) {
            predecessors.add(id(predecessor.getTJob()));
        }
        return new Node(id(tjob), stage, resources, tests, new ArrayList<>(predecessors));
    }

    private static String id(TJob tjob) {
        return tjob.getIdTJob().toLowerCase(Locale.ROOT);
    }

    /** Writes {@link #FILE} (creating {@code .retorch} if needed). */
    public void write(Map<Integer, LinkedList<Activity>> activitiesByStage) throws IOException {
        File file = new File(FILE);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file, toPlanFile(activitiesByStage));
    }
}
