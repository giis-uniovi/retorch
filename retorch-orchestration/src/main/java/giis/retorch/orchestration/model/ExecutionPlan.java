package giis.retorch.orchestration.model;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.exception.InvalidPlanException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code ExecutionPlan} class represents the execution of a series of TJobs: a validated graph of
 * {@code Activity} (a TJob and the activities that must finish before it) grouped by stage. Build it with
 * {@link #fromActivities(String, List)}, which validates the graph and numbers its TJobs.
 */
public final class ExecutionPlan {

    private final String name;
    private final List<TJob> tJobs;
    private final Map<Integer, LinkedList<Activity>> sortedActivities;

    private ExecutionPlan(String name, List<TJob> tJobs, Map<Integer, LinkedList<Activity>> sortedActivities) {
        this.name = name;
        this.tJobs = tJobs;
        this.sortedActivities = sortedActivities;
    }

    /**
     * Validates the activities, groups them by stage (an activity is in the stage after the last of its predecessors)
     * and gives the TJobs their stage and identifier (see {@link PlanNumbering}).
     *
     * @throws EmptyInputException  if there are no activities
     * @throws InvalidPlanException if the graph is not valid: a predecessor that is not in the list, a cycle or no
     *                              activity without predecessors; the message lists every problem
     */
    public static ExecutionPlan fromActivities(String name, List<Activity> activities)
            throws EmptyInputException, InvalidPlanException {
        if (activities == null || activities.isEmpty()) {
            throw new EmptyInputException("The provided list of activities is empty");
        }
        validate(activities);
        Map<Integer, LinkedList<Activity>> sorted = sortByStage(activities);
        List<TJob> tJobs = PlanNumbering.assign(sorted);
        return new ExecutionPlan(name, tJobs, sorted);
    }

    public String getName() {
        return name;
    }

    /** The TJobs that execute test cases (gateway activities excluded), in numbering order. */
    public List<TJob> getTJobs() {
        return Collections.unmodifiableList(tJobs);
    }

    /** The activities by stage, starting at 0. */
    public Map<Integer, LinkedList<Activity>> getSortedActivities() {
        return this.sortedActivities;
    }

    /**
     * Checks that the activities form a DAG with at least one initial activity. Activities are compared by identity:
     * a predecessor must be one of the listed instances.
     */
    private static void validate(List<Activity> activities) throws InvalidPlanException {
        Set<Activity> known = identitySet(activities);
        List<String> problems = unknownPredecessors(activities, known);
        if (!hasInitialActivity(activities)) {
            problems.add("There is no initial activity (every activity has predecessors)");
        }
        if (problems.isEmpty()) {
            List<Activity> unreachable = unreachableActivities(activities, known);
            if (!unreachable.isEmpty()) {
                problems.add("The activities form a cycle or depend on one: " + describeAll(unreachable));
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidPlanException("The execution plan is not valid", problems);
        }
    }

    private static Set<Activity> identitySet(List<Activity> activities) {
        Set<Activity> set = Collections.newSetFromMap(new IdentityHashMap<Activity, Boolean>());
        set.addAll(activities);
        return set;
    }

    private static boolean hasInitialActivity(List<Activity> activities) {
        for (Activity activity : activities) {
            if (activity.getListPredecessors().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** One message for every predecessor that is not one of the listed activities. */
    private static List<String> unknownPredecessors(List<Activity> activities, Set<Activity> known) {
        List<String> problems = new ArrayList<>();
        for (Activity activity : activities) {
            for (Activity predecessor : activity.getListPredecessors()) {
                if (!known.contains(predecessor)) {
                    problems.add(String.format("%s has the predecessor %s that is not part of the plan",
                            describe(activity), describe(predecessor)));
                }
            }
        }
        return problems;
    }

    private static String describeAll(List<Activity> activities) {
        StringBuilder text = new StringBuilder();
        for (Activity activity : activities) {
            text.append(text.length() == 0 ? "" : ", ").append(describe(activity));
        }
        return text.toString();
    }

    /** Activities that can never start: those on a cycle or that depend on one. */
    private static List<Activity> unreachableActivities(List<Activity> activities, Set<Activity> known) {
        Map<Activity, Integer> stage = computeStages(activities, known);
        List<Activity> unreachable = new ArrayList<>();
        for (Activity activity : activities) {
            if (!stage.containsKey(activity)) {
                unreachable.add(activity);
            }
        }
        return unreachable;
    }

    /**
     * The stage of every activity that can start: 0 without predecessors, otherwise one more than the highest stage of
     * its predecessors. Activities on a cycle (or depending on one) get no stage.
     */
    private static Map<Activity, Integer> computeStages(List<Activity> activities, Set<Activity> known) {
        Map<Activity, Integer> stage = new IdentityHashMap<>();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Activity activity : activities) {
                if (!stage.containsKey(activity)) {
                    Integer level = levelOf(activity, stage, known);
                    if (level != null) {
                        stage.put(activity, level);
                        progress = true;
                    }
                }
            }
        }
        return stage;
    }

    /** One more than the highest stage of the predecessors, or null while one of them has no stage yet. */
    private static Integer levelOf(Activity activity, Map<Activity, Integer> stage, Set<Activity> known) {
        int level = 0;
        for (Activity predecessor : activity.getListPredecessors()) {
            Integer predecessorStage = known.contains(predecessor) ? stage.get(predecessor) : null;
            if (predecessorStage == null) {
                return null;
            }
            level = Math.max(level, predecessorStage + 1);
        }
        return level;
    }

    private static Map<Integer, LinkedList<Activity>> sortByStage(List<Activity> activities) {
        Map<Activity, Integer> stage = computeStages(activities, identitySet(activities));
        Map<Integer, LinkedList<Activity>> byStage = new HashMap<>();
        for (Activity activity : activities) {
            int level = stage.get(activity);
            byStage.computeIfAbsent(level, k -> new LinkedList<>()).add(activity);
        }
        return byStage;
    }

    private static String describe(Activity activity) {
        TJob tJob = activity.getTJob();
        if (tJob.getListTestCases().isEmpty()) {
            return "the gateway activity of the resources " + resourceIds(tJob);
        }
        List<String> tests = new ArrayList<>();
        for (TestCase testCase : tJob.getListTestCases()) {
            tests.add(testCase.getName());
        }
        return "the activity with the tests " + tests;
    }

    private static List<String> resourceIds(TJob tJob) {
        List<String> ids = new ArrayList<>();
        for (Resource resource : tJob.getListResourceClasses()) {
            ids.add(resource.getResourceID());
        }
        return ids;
    }
}
