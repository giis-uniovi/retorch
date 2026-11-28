/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.exception.InvalidPlanException;
import giis.retorch.orchestration.model.AccessMode;
import giis.retorch.orchestration.model.AccessModeTypes;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ElasticityModel;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.TestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Validation, stages and numbering of the {@code ExecutionPlan}.
 */
public class ExecutionPlanTests {

    private Activity activity(String testName) {
        Resource resource = new Resource("res");
        resource.setElasticityModel(new ElasticityModel("em", 1, 1));
        TJob tJob = new TJob("Default", 0, new LinkedList<>(Collections.singletonList(resource)));
        if (testName != null) {
            tJob.addTestCase(new TestCase(testName, ExecutionPlanTests.class, new LinkedList<>(Collections.singletonList(
                    new AccessMode(new AccessModeTypes("READONLY"), false, 1, resource)))));
        }
        return new Activity(tJob);
    }

    private Activity after(Activity predecessor, String testName) {
        Activity activity = activity(testName);
        activity.addPredecessor(predecessor);
        return activity;
    }

    @Test
    public void testStagesAndNumbering() throws Exception {
        Activity first = activity("t1");
        Activity second = after(first, "t2");
        Activity third = after(first, "t3");
        Activity gateway = activity(null);
        gateway.addPredecessor(second);
        gateway.addPredecessor(third);
        Activity last = after(gateway, "t4");
        // not in dependency order on purpose
        ExecutionPlan plan = ExecutionPlan.fromActivities("plan", Arrays.asList(last, gateway, third, second, first));

        assertEquals(Collections.singletonList(first), plan.getSortedActivities().get(0));
        assertEquals(Arrays.asList(third, second), plan.getSortedActivities().get(1));
        assertEquals(Collections.singletonList(gateway), plan.getSortedActivities().get(2));
        assertEquals(Collections.singletonList(last), plan.getSortedActivities().get(3));
        assertEquals("TJobA", first.getTJob().getIdTJob());
        assertEquals("TJobB", third.getTJob().getIdTJob());
        assertEquals("TJobC", second.getTJob().getIdTJob());
        assertEquals("GatewayActivity", gateway.getTJob().getIdTJob());
        assertEquals("TJobD", last.getTJob().getIdTJob());
        assertEquals(3, last.getTJob().getStage());
        assertEquals(4, plan.getTJobs().size());
    }

    @Test
    public void testIdentifiersBeyondTwentySixTJobs() throws Exception {
        List<Activity> activities = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            activities.add(activity("t" + i));
        }
        ExecutionPlan.fromActivities("plan", activities);
        assertEquals("TJobZ", activities.get(25).getTJob().getIdTJob());
        assertEquals("TJobAA", activities.get(26).getTJob().getIdTJob());
        assertEquals("TJobAB", activities.get(27).getTJob().getIdTJob());
    }

    @Test(expected = EmptyInputException.class)
    public void testEmptyPlanFails() throws Exception {
        ExecutionPlan.fromActivities("plan", new LinkedList<Activity>());
    }

    @Test
    public void testCycleFails() throws Exception {
        Activity start = activity("t0");
        Activity one = after(start, "t1");
        Activity two = after(one, "t2");
        one.addPredecessor(two);
        try {
            ExecutionPlan.fromActivities("plan", Arrays.asList(start, one, two));
            fail("t1 and t2 wait for each other");
        } catch (InvalidPlanException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cycle") && e.getMessage().contains("[t1]")
                    && e.getMessage().contains("[t2]"));
        }
    }

    @Test
    public void testNoInitialActivityFails() throws Exception {
        Activity one = activity("t1");
        Activity two = after(one, "t2");
        one.addPredecessor(two);
        try {
            ExecutionPlan.fromActivities("plan", Arrays.asList(one, two));
            fail("every activity has predecessors");
        } catch (InvalidPlanException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no initial activity"));
        }
    }

    @Test
    public void testUnknownPredecessorFails() throws Exception {
        Activity outsider = activity("outsider");
        Activity start = activity("t0");
        Activity dependent = after(outsider, "t1");
        try {
            ExecutionPlan.fromActivities("plan", Arrays.asList(start, dependent));
            fail("the predecessor is not in the plan");
        } catch (InvalidPlanException e) {
            assertEquals(1, e.getProblems().size());
            assertTrue(e.getMessage(), e.getMessage().contains("[t1]") && e.getMessage().contains("[outsider]"));
        }
    }
}
