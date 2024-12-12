package giis.retorch.orchestration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import giis.retorch.orchestration.model.Capacity;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.ResourceInstance;

import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * {@code TJobsEntityTests} class provides the unitary tests of the different {@code TJob} methods
 */
public class TJobsEntityTests {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    TJob tjob;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        LinkedList<Capacity> listCapacities = new LinkedList<>();
        listCapacities.add(new Capacity("memory", 4));
        listCapacities.add(new Capacity("processor", 3));
        ResourceInstance resourceOne = new ResourceInstance("webserver", listCapacities);
        LinkedList<Resource> listResources = new LinkedList<>();
        listResources.add(resourceOne);
        tjob = new TJob("tjoba", 0, listResources);

        log.info("****** Set-up for test: {} ended ******", testName.getMethodName());
    }

    @Test
    public void testTJobAvgTime() {
        assertThrows(IllegalArgumentException.class, () ->
                tjob.setAvgTime(25, 10, 11, 16, 17, 20));
        assertThrows(IllegalArgumentException.class, () ->
                tjob.setAvgTime(20, 30, 10, 16, 5, 7));
        assertThrows(IllegalArgumentException.class, () ->
                tjob.setAvgTime(30, 31, 30, 35, 40, 45));
        tjob.setAvgTime(0, 10, 11, 16, 17, 21);

        assertEquals(0, tjob.getStartSetUp(), 0);
        assertEquals(10, tjob.getEndSetUp(), 0);
        assertEquals(11, tjob.getStartExec(), 0);
        assertEquals(16, tjob.getEndExec(), 0);
        assertEquals(17, tjob.getStartTearDown(), 0);
        assertEquals(21, tjob.getEndTearDown(), 0);
    }

    @Test
    public void testLifecycleCapacities() {
        tjob.setAvgTime(2, 10, 11, 16, 17, 21);
        Capacity capacityOne = new Capacity("memory", 4);
        Capacity capacityTwo = new Capacity("processor", 3);

        Map.Entry<String, Set<Capacity>> output;
        output = tjob.getCapacitiesGivenTime(1, 0);
        assertEquals("noexec", output.getKey());
        assertTrue(output.getValue().isEmpty());
        output = tjob.getCapacitiesGivenTime(2, 0);
        assertEquals("setup", output.getKey());
        assertTrue(output.getValue().contains(capacityOne));
        assertTrue(output.getValue().contains(capacityTwo));
        output = tjob.getCapacitiesGivenTime(12, 0);
        assertEquals("testexec", output.getKey());
        assertTrue(output.getValue().contains(capacityOne));
        assertTrue(output.getValue().contains(capacityTwo));
        output = tjob.getCapacitiesGivenTime(19, 0);
        assertEquals("teardown", output.getKey());
        assertTrue(output.getValue().contains(capacityOne));
        assertTrue(output.getValue().contains(capacityTwo));
        output = tjob.getCapacitiesGivenTime(21, 0);
        assertEquals("noexec", output.getKey());
        assertTrue(output.getValue().isEmpty());
    }
}