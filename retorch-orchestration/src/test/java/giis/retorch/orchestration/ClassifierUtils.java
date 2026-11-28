/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration;

import giis.retorch.orchestration.model.*;
import giis.retorch.orchestration.model.SystemUnderTest;
import giis.retorch.orchestration.testdata.synteticpackage.SyntheticClassTwoTests;
import giis.retorch.orchestration.testdata.synteticpackage.insidepackage.SyntheticClassOneTests;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code ClassifierUtils} class extends {@code GenericUtils}  providing a series of utils that generates
 * a {@code SystemUnderTest} with its Lists of {@code TestCase} and {@code Resource} for being used
 * in the {@code ClassifierTests} class
 */
public class ClassifierUtils extends GenericUtils {

    @Override
    public Resource getLightElasticResource() {
        Resource resource = super.getLightElasticResource();
        resource.setReplaceable(new LinkedList<>());
        return resource;
    }

    @Override
    public Resource getMockElasticResource() {
        Resource resource = super.getMockElasticResource();
        resource.setReplaceable(Arrays.asList(HEAVY_INELASTIC_ID,LIGHT_ELASTIC_ID));
        return resource;
    }

    public List<TestCase> getAllTestCases() {
        return Stream.concat(
                getFirstClassTestCases().stream(),
                Stream.of(
                        new TestCase("testFourH", SyntheticClassTwoTests.class, Collections.singletonList(getOtherAccessModeHeavyInelasticResource())),
                        new TestCase("testFiveH", SyntheticClassTwoTests.class, Collections.singletonList(getOtherAccessModeHeavyInelasticResource())),
                        new TestCase("testEightH", SyntheticClassTwoTests.class, Collections.singletonList(getOtherAccessModeHeavyInelasticResource()))
                )
        ).collect(Collectors.toList());
    }

    public SystemUnderTest generateSystemPackage() {
        SystemUnderTest expectedSystem = new SystemUnderTest("ClassifierUnitTests");
        expectedSystem.addResourceClass(this.getHeavyInelasticResource());
        expectedSystem.addResourceClass(this.getLightElasticResource());
        expectedSystem.addResourceClass(this.getMockElasticResource());
        expectedSystem.addResourceClass(this.getHeavyInelasticResource());
        expectedSystem.addResourceClass(this.getLightElasticResource());
        expectedSystem.addListTestCases(getAllTestCases().stream().sorted(Comparator.comparing(TestCase::getName)).collect(Collectors.toList()));
        return expectedSystem;
    }

    public SystemUnderTest generateSystemFromClass() {
        return new SystemUnderTest("ClassifierUnitTests", getFirstClassTestCases(), Arrays.asList(this.getHeavyInelasticResource(), this.getLightElasticResource(), this.getMockElasticResource()));
    }

    private List<TestCase> getFirstClassTestCases() {

        TestCase tCHOne = new TestCase("testOneH", SyntheticClassOneTests.class, Arrays.asList(
                this.getAccessModeHeavyInElasticResource(), this.getAccessModeLightElasticResource()));

        TestCase tCHTwo = new TestCase("testTwoH", SyntheticClassOneTests.class,
                Arrays.asList(this.getAccessModeHeavyInElasticResource(), this.getAccessModeLightElasticResource()));

        TestCase tCHThree = new TestCase("testThreeH", SyntheticClassOneTests.class, Arrays.asList(
                this.getAccessModeHeavyInElasticResource(), this.getMockElasticAccessMode()));

        TestCase tCHSix = new TestCase("testSixH", SyntheticClassOneTests.class, Arrays.asList(
                this.getAccessModeHeavyInElasticResource(), this.getAccessModeLightElasticResource()));

        TestCase tCHSeven = new TestCase("testSevenH", SyntheticClassOneTests.class, Arrays.asList(
                this.getAccessModeHeavyInElasticResource(), this.getAccessModeLightElasticResource()));

        return new LinkedList<>(Arrays.asList(tCHOne, tCHThree, tCHTwo, tCHSix, tCHSeven));
    }
}