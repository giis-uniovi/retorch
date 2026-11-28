/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration;

import giis.retorch.orchestration.model.AccessMode;
import giis.retorch.orchestration.model.AccessModeTypes;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.Capacity;
import giis.retorch.orchestration.model.ElasticityModel;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TGroup;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.TestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Value based equality of the model: reflexive, symmetric, hash consistent and usable in hash collections.
 */
public class ModelEqualityTests {

    private Resource resource(String id, String... replaceable) {
        Resource res = new Resource(id);
        res.setElasticityModel(new ElasticityModel("em", 2, 1.5));
        for (String rep : replaceable) res.addReplaceableResource(rep);
        return res;
    }

    private TestCase testCase(String name, Resource res) {
        return new TestCase(name, ModelEqualityTests.class,
                new LinkedList<>(Collections.singletonList(
                        new AccessMode(new AccessModeTypes("READONLY"), true, 3, res))));
    }

    private void assertEqualAndSameHash(Object one, Object other) {
        assertTrue(one.equals(one));
        assertTrue(one.equals(other));
        assertTrue(other.equals(one));
        assertEquals(one.hashCode(), other.hashCode());
        Set<Object> set = new HashSet<>();
        set.add(one);
        assertTrue(set.contains(other));
        set.add(other);
        assertEquals(1, set.size());
    }

    @Test
    public void testResourceEqualsAndHash() {
        assertEqualAndSameHash(resource("r1", "b", "a"), resource("r1", "a", "b"));
        assertNotEquals(resource("r1"), resource("r2"));
        assertNotEquals(resource("r1", "a"), resource("r1"));
    }

    @Test
    public void testResourceGettersDoNotMutate() {
        List<String> given = new ArrayList<>(Arrays.asList("b", "a"));
        Resource res = new Resource("r1");
        res.setReplaceable(given);
        assertEquals(Arrays.asList("a", "b"), res.getReplaceable());
        assertEquals(Arrays.asList("b", "a"), given);
        List<Capacity> capacities = new ArrayList<>(Arrays.asList(new Capacity("memory", 1), new Capacity("any", 1)));
        res.setMinimalCapacities(capacities);
        assertEquals("memory", capacities.get(0).getName());
    }

    @Test
    public void testAccessModeAndTestCase() {
        assertEqualAndSameHash(testCase("t1", resource("r1")), testCase("t1", resource("r1")));
        assertNotEquals(testCase("t1", resource("r1")), testCase("t2", resource("r1")));
        assertNotEquals(testCase("t1", resource("r1")), testCase("t1", resource("r2")));
        assertEqualAndSameHash(new AccessModeTypes("READONLY"), new AccessModeTypes("READONLY"));
    }

    @Test
    public void testTGroupIsSymmetric() {
        TGroup small = new TGroup(new LinkedList<>(Collections.singletonList(testCase("t1", resource("r1")))),
                new LinkedList<>(Collections.singletonList(resource("r1"))));
        TGroup big = new TGroup(new LinkedList<>(Arrays.asList(testCase("t1", resource("r1")),
                testCase("t2", resource("r1")))), new LinkedList<>(Collections.singletonList(resource("r1"))));
        assertFalse(small.equals(big));
        assertFalse(big.equals(small));
        TGroup same = new TGroup(new LinkedList<>(Collections.singletonList(testCase("t1", resource("r1")))),
                new LinkedList<>(Collections.singletonList(resource("r1"))));
        assertEqualAndSameHash(small, same);
    }

    private TJob tJob(String... testNames) {
        Resource res = resource("r1");
        TJob job = new TJob("TJobA", 1, new LinkedList<Resource>());
        job.addResource(res);
        for (String name : testNames) job.addTestCase(testCase(name, res));
        return job;
    }

    @Test
    public void testTJobAndActivityAreSymmetric() {
        TJob small = tJob("t1");
        TJob big = tJob("t1", "t2");
        assertFalse(small.equals(big));
        assertFalse(big.equals(small));
        assertEqualAndSameHash(tJob("t1", "t2"), tJob("t2", "t1"));
        Activity first = new Activity(small, new Activity(tJob("t9")));
        Activity second = new Activity(tJob("t1"), new Activity(tJob("t9")));
        assertEqualAndSameHash(first, second);
        assertFalse(first.equals(new Activity(tJob("t1"))));
        assertFalse(new Activity(tJob("t1")).equals(first));
    }
}
