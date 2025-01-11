package giis.retorch.orchestration.testdata.integration;

import giis.retorch.annotations.AccessMode;
import org.junit.Assert;
import org.junit.Test;

public class IntegrationClassTwoTests {

    @AccessMode(resID = "heavyInElasticResource", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testLOnlyHIOne() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
    @AccessMode(resID = "heavyInElasticResource", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testMOnlyHITwo() {Assert.assertTrue(true);// Only  for analysis purposes
    }
    @AccessMode(sharing = true, concurrency = 12, accessMode = "READONLY", resID = "mediumElasticResource")
    @Test
    public void testNOnlyMediumElasticOne() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
    @AccessMode(sharing = true, concurrency = 6, accessMode = "READONLY", resID = "mediumElasticResource")
    @Test
    public void testOOnlyLightElasticTwo() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
    @AccessMode(sharing = true, concurrency = 8, accessMode = "READONLY", resID = "mediumElasticResource")
    @Test
    public void testPOnlyLightElasticThree() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
}