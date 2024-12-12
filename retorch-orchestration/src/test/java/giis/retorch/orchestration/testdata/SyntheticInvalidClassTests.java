package giis.retorch.orchestration.testdata;

import giis.retorch.annotations.AccessMode;
import org.junit.Assert;
import org.junit.Test;

public class SyntheticInvalidClassTests {

    /**
     * Attributes omitted: elasticityModel and resource Type
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testZeroLoosingAttributes() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Attributes omitted: hierarchy parent, resource id and replaceable resources
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testOneLoosingAttributes() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Invalid value of elasticity and elasticity cost.
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testValidatesElasticityValuesElasticityAndElasticityCost() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Attributes omitted: resource id, and concurrency
     */
    @AccessMode(accessMode = "READWRITE")
    @Test
    public void testTwoAttributesLoosingAccessMode() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Attributes omitted: type of access mode and sharing
     */
    @AccessMode(resID = "heavyInvalidInElasRest", concurrency = 1)
    @Test
    public void testThreeAttributesLoosingAccessMode() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Three errors:  sharing=false with a concurrency of >1, no valid access mode and sharing true && concurrency<2
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 4)
    @AccessMode(concurrency = 1, sharing = true, resID = "mockInvalidElasticResource", accessMode =
            "InventedAccessMode")
    @Test
    public void validationAccessModeAttributes() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Resource annotation no provided
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testDifferentNumberAnnotationsResource() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Error: Resource that doesn't exist in AccessMode^
     */
    @AccessMode(resID = "ResourceNotExist", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testResourceThatNotExist() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Not annotated resource (but exist)
     */
    @AccessMode(resID = "lightElasticResource", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void resourceThatExistButNotAnnotated() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    @Test
    public void resourceWithoutAccessMode() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Provides a "good resource" for after match against it the non-consistent ones
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testResourcesDifferentReplaceableGoodResource() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Method with a different resource than the last specified
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testResourcesDifferentReplaceableBadResource() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testElasticityModelDifferentThanSpecified() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Two hierarchy parents provided
     */
    @AccessMode(resID = "heavyInvalidInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testResourceWithTwoHierarchyParents() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }

    /**
     * Replaceable resource doesn't exist
     */
    @AccessMode(resID = "OtherHeavyInElasRest", accessMode = "READWRITE", concurrency = 1)
    @Test
    public void testResourceWithBadReplaceable() {
        Assert.assertTrue(true);// Only  for analysis purposes
    }
}