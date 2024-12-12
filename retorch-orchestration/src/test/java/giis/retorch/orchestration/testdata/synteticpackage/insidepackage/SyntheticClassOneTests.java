package giis.retorch.orchestration.testdata.synteticpackage.insidepackage;

import giis.retorch.annotations.AccessMode;
import org.junit.Assert;
import org.junit.Test;

public class SyntheticClassOneTests {

    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @AccessMode(resID = "lightElasticResource", accessMode = "READWRITE", concurrency = 4, sharing = true)
    @Test
    public void testOneH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @AccessMode(sharing = true, concurrency = 4, accessMode = "READWRITE", resID = "lightElasticResource")
    @Test
    public void testTwoH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @AccessMode(concurrency = 4, sharing = true, resID = "mockElasticResource", accessMode = "READONLY")
    @Test
    public void testThreeH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * This test case omits: type and parent in the first resource, elasticity cost and elasticity in the second one
     */
    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @AccessMode(sharing = true, concurrency = 4, accessMode = "READWRITE", resID = "lightElasticResource")
    @Test
    public void testSixH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Only specifying the minimal resources required
     */
    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @AccessMode(concurrency = 4, sharing = true, resID = "lightElasticResource", accessMode = "READWRITE")
    @Test
    public void testSevenH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
}