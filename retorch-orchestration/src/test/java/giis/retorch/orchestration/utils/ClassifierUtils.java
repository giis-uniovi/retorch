package giis.retorch.orchestration.utils;

import giis.retorch.orchestration.model.*;
import giis.retorch.orchestration.unitary.testdata.classifier.synteticpackage.SyntheticClassTwoTests;
import giis.retorch.orchestration.unitary.testdata.classifier.synteticpackage.insidepackage.SyntheticClassOneTests;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ClassifierUtils extends GenericUtils {

    public List<TestCaseEntity> getAllTestCases() {
        LinkedList<TestCaseEntity> listTestCases = getFirstClassTestCases();
        TestCaseEntity tCMFour = new TestCaseEntity("testFourH", SyntheticClassTwoTests.class);
        tCMFour.addAccessMode(getOtherAccessModeHeavyInelasticResource());
        listTestCases.add(tCMFour);
        TestCaseEntity tCMFive = new TestCaseEntity("testFiveH", SyntheticClassTwoTests.class);
        tCMFive.addAccessMode(getOtherAccessModeHeavyInelasticResource());
        listTestCases.add(tCMFive);
        TestCaseEntity tCMEight = new TestCaseEntity("testEightH", SyntheticClassTwoTests.class);
        tCMEight.addAccessMode(this.getJSONSavedInelasticAccessMode());
        listTestCases.add(tCMEight);
        return listTestCases;
    }

    public SystemEntity generateSystemPackage() {
        generateSystemResources();
        SystemEntity expectedSystem = new SystemEntity("ClassifierUnitTests");
        expectedSystem.addResourceClass(this.getHeavyInelasticResource());
        expectedSystem.addResourceClass(this.getLightElasticResource());
        expectedSystem.addResourceClass(this.getMockElasticResource());
        expectedSystem.addResourceClass(this.getJSONSavedInelasticResource());
        expectedSystem.addResourceClass(this.getJSONSavedElasticResource());
        expectedSystem.getResources().sort(Comparator.comparing(ResourceEntity::toString));
        expectedSystem.addListTestCases(getAllTestCases().stream().sorted(Comparator.comparing(TestCaseEntity::getName)).toList());
        return expectedSystem;
    }

    public SystemEntity generateSystemFromClass() {
        generateSystemResources();
        SystemEntity expectedSystem = new SystemEntity("ClassifierUnitTests");
        expectedSystem.addResourceClass(this.getHeavyInelasticResource());
        expectedSystem.addResourceClass(this.getLightElasticResource());
        expectedSystem.addResourceClass(this.getMockElasticResource());
        expectedSystem.addListTestCases(getFirstClassTestCases());
        return expectedSystem;
    }

    private LinkedList<TestCaseEntity> getFirstClassTestCases() {
        LinkedList<TestCaseEntity> listTestCases = new LinkedList<>();
        TestCaseEntity tCHOne = new TestCaseEntity("testOneH", SyntheticClassOneTests.class);
        tCHOne.addAccessMode(this.getAccessModeHeavyInElasticResource());
        tCHOne.addAccessMode(this.getAccessModeLightElasticResource());
        listTestCases.add(tCHOne);
        TestCaseEntity tCHTwo = new TestCaseEntity("testTwoH", SyntheticClassOneTests.class);
        tCHTwo.addAccessMode(this.getAccessModeHeavyInElasticResource());
        tCHTwo.addAccessMode(this.getAccessModeLightElasticResource());
        listTestCases.add(tCHTwo);
        TestCaseEntity tCHThree = new TestCaseEntity("testThreeH", SyntheticClassOneTests.class);
        tCHThree.addAccessMode(getAccessModeHeavyInElasticResource());
        tCHThree.addAccessMode(this.getMockElasticAccessMode());
        listTestCases.add(tCHThree);
        TestCaseEntity tCHSix = new TestCaseEntity("testSixH", SyntheticClassOneTests.class);
        tCHSix.addAccessMode(this.getJSONSavedInelasticAccessMode());
        tCHSix.addAccessMode(this.getJSONSavedElasticAccessMode());
        listTestCases.add(tCHSix);
        TestCaseEntity tCHSeven = new TestCaseEntity("testSevenH", SyntheticClassOneTests.class);
        tCHSeven.addAccessMode(this.getJSONSavedInelasticAccessMode());
        tCHSeven.addAccessMode(this.getJSONSavedElasticAccessMode());
        listTestCases.add(tCHSeven);
        return listTestCases;
    }

    private AccessModeEntity getJSONSavedInelasticAccessMode() {
        AccessModeEntity accessModeEntity = new AccessModeEntity(this.getAccessModeHeavyInElasticResource());
        accessModeEntity.setResource(this.getJSONSavedInelasticResource());
        return accessModeEntity;
    }

    private AccessModeEntity getJSONSavedElasticAccessMode() {
        AccessModeEntity accessModeEntity = new AccessModeEntity(this.getAccessModeLightElasticResource());
        accessModeEntity.setResource(this.getJSONSavedElasticResource());
        return accessModeEntity;
    }

    private ResourceEntity getJSONSavedInelasticResource() {
        ResourceEntity outputResource = new ResourceEntity(this.getHeavyInelasticResource());
        ElasticityModelEntity modelJSON = outputResource.getElasticityModel();
        modelJSON.setElasticityID("elasModelHeavyInElasRest");
        outputResource.setElasticityModel(modelJSON);
        outputResource.setResourceID("heavyInElasRest");
        LinkedList<String> parentsList = new LinkedList<>();
        parentsList.add("parentAllInelastic");
        outputResource.setHierarchyParent(parentsList);
        return outputResource;
    }

    private ResourceEntity getJSONSavedElasticResource() {
        ResourceEntity outputResource = new ResourceEntity(this.getLightElasticResource());
        ElasticityModelEntity modelJSON = outputResource.getElasticityModel();
        modelJSON.setElasticityID("elasModelLightElasticResource");
        outputResource.setElasticityModel(modelJSON);
        outputResource.setResourceID("lightElasticResource");
        return outputResource;
    }
}