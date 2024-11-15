package giis.retorch.orchestration.utils;

import giis.retorch.orchestration.model.*;
import giis.retorch.orchestration.model.System;
import giis.retorch.orchestration.testdata.synteticpackage.SyntheticClassTwoTests;
import giis.retorch.orchestration.testdata.synteticpackage.insidepackage.SyntheticClassOneTests;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ClassifierUtils extends GenericUtils {

    public List<TestCase> getAllTestCases() {
        LinkedList<TestCase> listTestCases = getFirstClassTestCases();
        TestCase tCMFour = new TestCase("testFourH", SyntheticClassTwoTests.class);
        tCMFour.addAccessMode(getOtherAccessModeHeavyInelasticResource());
        listTestCases.add(tCMFour);
        TestCase tCMFive = new TestCase("testFiveH", SyntheticClassTwoTests.class);
        tCMFive.addAccessMode(getOtherAccessModeHeavyInelasticResource());
        listTestCases.add(tCMFive);
        TestCase tCMEight = new TestCase("testEightH", SyntheticClassTwoTests.class);
        tCMEight.addAccessMode(this.getJSONSavedInelasticAccessMode());
        listTestCases.add(tCMEight);
        return listTestCases;
    }

    public System generateSystemPackage() {
        generateSystemResources();
        System expectedSystem = new System("ClassifierUnitTests");
        expectedSystem.addResourceClass(this.getHeavyInelasticResource());
        expectedSystem.addResourceClass(this.getLightElasticResource());
        expectedSystem.addResourceClass(this.getMockElasticResource());
        expectedSystem.addResourceClass(this.getJSONSavedInelasticResource());
        expectedSystem.addResourceClass(this.getJSONSavedElasticResource());
        expectedSystem.getResources().sort(Comparator.comparing(Resource::toString));
        expectedSystem.addListTestCases(getAllTestCases().stream().sorted(Comparator.comparing(TestCase::getName)).toList());
        return expectedSystem;
    }

    public System generateSystemFromClass() {
        generateSystemResources();
        System expectedSystem = new System("ClassifierUnitTests");
        expectedSystem.addResourceClass(this.getHeavyInelasticResource());
        expectedSystem.addResourceClass(this.getLightElasticResource());
        expectedSystem.addResourceClass(this.getMockElasticResource());
        expectedSystem.addListTestCases(getFirstClassTestCases());
        return expectedSystem;
    }

    private LinkedList<TestCase> getFirstClassTestCases() {
        LinkedList<TestCase> listTestCases = new LinkedList<>();
        TestCase tCHOne = new TestCase("testOneH", SyntheticClassOneTests.class);
        tCHOne.addAccessMode(this.getAccessModeHeavyInElasticResource());
        tCHOne.addAccessMode(this.getAccessModeLightElasticResource());
        listTestCases.add(tCHOne);
        TestCase tCHTwo = new TestCase("testTwoH", SyntheticClassOneTests.class);
        tCHTwo.addAccessMode(this.getAccessModeHeavyInElasticResource());
        tCHTwo.addAccessMode(this.getAccessModeLightElasticResource());
        listTestCases.add(tCHTwo);
        TestCase tCHThree = new TestCase("testThreeH", SyntheticClassOneTests.class);
        tCHThree.addAccessMode(getAccessModeHeavyInElasticResource());
        tCHThree.addAccessMode(this.getMockElasticAccessMode());
        listTestCases.add(tCHThree);
        TestCase tCHSix = new TestCase("testSixH", SyntheticClassOneTests.class);
        tCHSix.addAccessMode(this.getJSONSavedInelasticAccessMode());
        tCHSix.addAccessMode(this.getJSONSavedElasticAccessMode());
        listTestCases.add(tCHSix);
        TestCase tCHSeven = new TestCase("testSevenH", SyntheticClassOneTests.class);
        tCHSeven.addAccessMode(this.getJSONSavedInelasticAccessMode());
        tCHSeven.addAccessMode(this.getJSONSavedElasticAccessMode());
        listTestCases.add(tCHSeven);
        return listTestCases;
    }

    private AccessMode getJSONSavedInelasticAccessMode() {
        AccessMode accessMode = new AccessMode(this.getAccessModeHeavyInElasticResource());
        accessMode.setResource(this.getJSONSavedInelasticResource());
        return accessMode;
    }

    private AccessMode getJSONSavedElasticAccessMode() {
        AccessMode accessMode = new AccessMode(this.getAccessModeLightElasticResource());
        accessMode.setResource(this.getJSONSavedElasticResource());
        return accessMode;
    }

    private Resource getJSONSavedInelasticResource() {
        Resource outputResource = new Resource(this.getHeavyInelasticResource());
        ElasticityModel modelJSON = outputResource.getElasticityModel();
        modelJSON.setElasticityID("elasModelHeavyInElasRest");
        outputResource.setElasticityModel(modelJSON);
        outputResource.setResourceID("heavyInElasRest");
        LinkedList<String> parentsList = new LinkedList<>();
        parentsList.add("parentAllInelastic");
        outputResource.setHierarchyParent(parentsList);
        return outputResource;
    }

    private Resource getJSONSavedElasticResource() {
        Resource outputResource = new Resource(this.getLightElasticResource());
        ElasticityModel modelJSON = outputResource.getElasticityModel();
        modelJSON.setElasticityID("elasModelLightElasticResource");
        outputResource.setElasticityModel(modelJSON);
        outputResource.setResourceID("lightElasticResource");
        return outputResource;
    }
}