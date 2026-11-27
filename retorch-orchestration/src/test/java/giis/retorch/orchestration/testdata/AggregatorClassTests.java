/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.testdata;


import giis.retorch.annotations.AccessMode;
import org.junit.Assert;
import org.junit.Test;

public class AggregatorClassTests {

    @AccessMode(resID = "id", accessMode = "READWRITE", concurrency = 1)
    @AccessMode(resID = "otherId", accessMode = "READONLY", concurrency = 5, sharing = true)
    @Test
    public void testOneH() {
        Assert.assertTrue(true);// Only for analysis purposes
    }
}