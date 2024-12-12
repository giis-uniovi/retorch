package giis.retorch.orchestration.testdata.synteticpackage;

import giis.retorch.annotations.AccessMode;
import org.junit.Assert;
import org.junit.Test;

public class SyntheticClassTwoTests {

    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testFourH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testFiveH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
    @AccessMode(resID = "heavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testEightH() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
}