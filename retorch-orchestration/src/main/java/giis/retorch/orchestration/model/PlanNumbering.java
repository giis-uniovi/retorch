/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The step that gives every TJob of a plan its stage and its identifier. It is the only place where an
 * {@code ExecutionPlan} changes its TJobs: the TJobs with test cases are named {@code TJobA, TJobB...} in stage order
 * (and in the order of the activities inside a stage); the ones without test cases are {@code GatewayActivity}.
 */
final class PlanNumbering {

    static final String GATEWAY_ID = "GatewayActivity";

    private PlanNumbering() {
    }

    /**
     * @param activitiesByStage activities grouped by the stage they belong to
     * @return the TJobs that execute test cases, in numbering order
     */
    static List<TJob> assign(Map<Integer, LinkedList<Activity>> activitiesByStage) {
        List<TJob> tJobs = new ArrayList<>();
        for (Map.Entry<Integer, LinkedList<Activity>> stage : new TreeMap<>(activitiesByStage).entrySet()) {
            for (Activity activity : stage.getValue()) {
                TJob tJob = activity.getTJob();
                tJob.setStage(stage.getKey());
                if (tJob.getListTestCases().isEmpty()) {
                    tJob.setIdTJob(GATEWAY_ID);
                } else {
                    tJob.setIdTJob("TJob" + getIdentifier(tJobs.size()));
                    tJobs.add(tJob);
                }
            }
        }
        return tJobs;
    }

    /**
     * Generates an alphabetical identifier for a {@code TJob} according to the number of TJob:
     * e.g. A,B,C..., AA,AB,AC... BA,BB,BC...
     */
    static String getIdentifier(int index) {
        StringBuilder result = new StringBuilder();
        int remaining = index;
        while (remaining >= 0) {
            result.insert(0, (char) ('A' + (remaining % 26)));
            remaining = remaining / 26 - 1;
        }
        return result.toString();
    }
}
