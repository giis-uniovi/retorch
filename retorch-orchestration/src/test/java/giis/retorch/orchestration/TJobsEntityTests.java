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
    public void testTJobKeepsItsIdAsStored() {
        assertEquals("tjoba", tjob.getIdTJob());
        tjob.setIdTJob("TJobB");
        assertEquals("TJobB", tjob.getIdTJob());
    }

    @Test
    public void testResourceWithoutMinimalCapacitiesIsValid() throws java.io.IOException {
        // The capacities are measured when the pipeline runs, so the resources JSON does not have to declare them
        String json = "{\"resourceID\":\"webserver\",\"hierarchyParent\":[],\"replaceable\":[],"
                + "\"resourceType\":\"LOGICAL\",\"dockerImage\":\"img\"}";
        Resource resource = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Resource.class);
        assertNotNull(resource.getMinimalCapacities());
        assertTrue(resource.getMinimalCapacities().isEmpty());

        LinkedList<Resource> resources = new LinkedList<>();
        resources.add(resource);
        assertEquals(1, new TJob("tjobx", 0, resources).getListResourceClasses().size());
    }
}
