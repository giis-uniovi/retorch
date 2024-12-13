package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.ResourceSerializer;
import giis.retorch.orchestration.model.System;
import giis.retorch.orchestration.model.*;
import giis.retorch.orchestration.testdata.AggregatorClassTests;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code SchedulerUtils} class provides several methods that create ordered sets of {@code TJob}s and
 * {@code TGroup}s, as well as the {@code Resource}s and {@code AccessMode}s required during the
 * {@code SchedulerTests} unitary tests
 */
public class SchedulerUtils extends GenericUtils {

    @Override
    public Resource getMockElasticResource() {
        Resource mockElasticResource = new Resource(this.getMediumElasticResource());
        mockElasticResource.addReplaceableResource(LIGHT_ELASTIC_ID);
        mockElasticResource.addReplaceableResource(MOCK_ELASTIC_ID);

        return mockElasticResource;
    }

    public Resource getSingleSchedulerTestCaseResource() {
        ElasticityModel otherResourceModel = getElasticityModelMockResource();
        otherResourceModel.setElasticityCost(5);

        return new Resource("resOneTC", new LinkedList<>(), new LinkedList<>(),
                otherResourceModel, Resource.type.LOGICAL, Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 2),
                new Capacity(Capacity.PROCESSOR_NAME, 1)), DOCKER_IMAGE);
    }

    public AccessMode getNewMockElasticAccessMode() {
        AccessMode expectedAccessModeMySQLMedium = new AccessMode(this.getMockElasticAccessMode());
        expectedAccessModeMySQLMedium.setResource(this.getMockElasticResource());

        return expectedAccessModeMySQLMedium;
    }

    public AccessMode getSchedulerMediumElasticAccessMode() {
        return new AccessMode(new AccessModeTypes(READWRITE), false, 1, this.getMediumSchedulerElasticResource());
    }

    public AccessMode getSchedulerMediumInelasticAccessMode() {
        return new AccessMode(new AccessModeTypes(READWRITE), false, 1, this.getMediumSchedulerInelasticResource());
    }

    public AccessMode getSchedulerSingleTestCaseAccessMode() {
        return new AccessMode(
                new AccessModeTypes("READ"), false, 10, this.getSingleSchedulerTestCaseResource());
    }

    private TestCase getSchedulerTestCaseClassWithOneResource(String name, AccessMode accessModeFirstResource) {
        TestCase tCHSeven = new TestCase(name, AggregatorClassTests.class);
        tCHSeven.addAccessMode(accessModeFirstResource);

        return tCHSeven;
    }

    public System generateSystemAggregator() {
        return new System("AggregatorUnitTests", this.aggregatorTestClasses(),
                Arrays.asList(this.getLightElasticResource(),
                        this.getMockElasticResource(),
                        this.getMediumElasticResource(),
                        this.getHeavyInelasticResource(),
                        this.getMediumInelasticResource()));
    }

    public List<TestCase> aggregatorTestClasses() {
        List<TestCase> listTestCases = new LinkedList<>();
        //T1 could be in Medium-Light-Mock-Elastic Resource with RO and in HeavyInelastic with RW
        TestCase tCHOne = new TestCase("tOneAgg", AggregatorClassTests.class, Arrays.asList(this.getNewMockElasticAccessMode(),
                this.getAccessModeHeavyInElasticResource()));
        listTestCases.add(tCHOne);
        //T2 could be in Medium-Light-Mock-Elastic Resource with NOACCESS and in HeavyInelastic with READONLY
        TestCase tCHTwo = new TestCase("tTwoAgg", AggregatorClassTests.class);
        AccessMode accessModifiedMock = this.getNewMockElasticAccessMode();
        accessModifiedMock.setType(new AccessModeTypes(NO_ACCESS));
        tCHTwo.addAccessMode(accessModifiedMock);
        AccessMode accessModifiedHeavyInElasticResource = this.getAccessModeHeavyInElasticResource();
        accessModifiedHeavyInElasticResource.setType(new AccessModeTypes((READONLY)));
        tCHTwo.addAccessMode(accessModifiedHeavyInElasticResource);
        listTestCases.add(tCHTwo);
        //T3 could be in Medium-Light-Mock-Elastic Resource with READONLY and in Heavy-Medium-Inelastic with READWRITE
        TestCase tCHThree = new TestCase("tThreeAgg", AggregatorClassTests.class, Arrays.asList(this.getAccessModeLightElasticResource(),
                this.getMediumInelasticAccessMode()));
        listTestCases.add(tCHThree);
        //T4 could be in Medium-Light-Mock-Elastic Resource with READWRITE and in Heavy-Inelastic with READWRITE
        TestCase tCHFour = new TestCase("tFourAgg", AggregatorClassTests.class, Arrays.asList(this.getMediumElasticAccessMode(),
                this.getAccessModeHeavyInElasticResource()));
        listTestCases.add(tCHFour);
        //T5 could be in Light-Mock-Elastic Resource with READWRITE and in Heavy-Medium-Inelastic with READWRITE
        TestCase tCHFive = new TestCase("tFiveAgg", AggregatorClassTests.class, Arrays.asList(this.getAccessModeLightElasticResource(),
                this.getMediumInelasticAccessMode()));
        listTestCases.add(tCHFive);
        //T6 could be in Light-Mock-Elastic Resource with READWRITE and in Heavy-Inelastic with READWRITE
        TestCase tCHSix = new TestCase("tSixAgg", AggregatorClassTests.class, Arrays.asList(this.getAccessModeLightElasticResource(),
                this.getAccessModeHeavyInElasticResource()));
        listTestCases.add(tCHSix);
        //T7 could be in Light-Mock-Elastic Resource with READWRITE and in Heavy-Medium-Inelastic with READWRITE
        TestCase tCHSeven = new TestCase("tSevenAgg", AggregatorClassTests.class, Arrays.asList(this.getNewMockElasticAccessMode(),
                this.getMediumInelasticAccessMode()));
        listTestCases.add(tCHSeven);

        return listTestCases;
    }

    public List<TGroup> getExpectedTGroups() throws IOException {
        ResourceSerializer serializer = new ResourceSerializer();
        Map<String, Resource> listAvailableResources = serializer.deserializeResources("AggregatorUnitTests");
        List<TestCase> listTestCases = aggregatorTestClasses();

        TGroup tGroupOne = new TGroup(Collections.singletonList(listTestCases.get(1)),
                Arrays.asList(listAvailableResources.get(HEAVY_INELASTIC_ID), listAvailableResources.get(LIGHT_ELASTIC_ID)));
        TGroup tGroupTwo = new TGroup(Collections.singletonList(listTestCases.get(6)),
                Arrays.asList(listAvailableResources.get(MEDIUM_INELASTIC_ID), listAvailableResources.get(MOCK_ELASTIC_ID)));
        TGroup tGroupThree = new TGroup(Arrays.asList(listTestCases.get(2), listTestCases.get(4), listTestCases.get(6)),
                Arrays.asList(listAvailableResources.get(MEDIUM_INELASTIC_ID), listAvailableResources.get(LIGHT_ELASTIC_ID)));
        TGroup tGroupFour = new TGroup(Arrays.asList(listTestCases.get(0), listTestCases.get(6)),
                Arrays.asList(listAvailableResources.get(HEAVY_INELASTIC_ID), listAvailableResources.get(MOCK_ELASTIC_ID)));
        TGroup tGroupFive = new TGroup(Arrays.asList(listTestCases.get(2), listTestCases.get(4), listTestCases.get(6)),
                Arrays.asList(listAvailableResources.get(MEDIUM_INELASTIC_ID), listAvailableResources.get(MEDIUM_ELASTIC_ID)));
        TGroup tGroupSix = new TGroup(Collections.singletonList(listTestCases.get(1)),
                Arrays.asList(listAvailableResources.get(HEAVY_INELASTIC_ID), listAvailableResources.get(MOCK_ELASTIC_ID)));
        List<TestCase> listTGroupSeven = Stream.concat(Stream.of(listTestCases.get(0)),
                listTestCases.subList(2, 7).stream()).collect(Collectors.toList());
        TGroup tGroupSeven = new TGroup(listTGroupSeven,
                Arrays.asList(listAvailableResources.get(HEAVY_INELASTIC_ID), listAvailableResources.get(MEDIUM_ELASTIC_ID)));
        TGroup tGroupEight = new TGroup(Collections.singletonList(listTestCases.get(1)),
                Arrays.asList(listAvailableResources.get(HEAVY_INELASTIC_ID), listAvailableResources.get(MEDIUM_ELASTIC_ID)));
        List<TestCase> listTGroupNine = Stream.concat(Stream.of(listTestCases.get(0)), listTestCases.subList(2, 7).stream()
        ).collect(Collectors.toList());
        TGroup tGroupNine = new TGroup(listTGroupNine, Arrays.asList(listAvailableResources.get(HEAVY_INELASTIC_ID),
                listAvailableResources.get(LIGHT_ELASTIC_ID)));

        return new ArrayList<>(Arrays.asList(tGroupOne, tGroupTwo, tGroupThree, tGroupFour, tGroupFive, tGroupSix,
                tGroupSeven, tGroupEight, tGroupNine)).stream().sorted(Comparator.comparing(TGroup::toString)).
                collect(Collectors.toList());
    }

    public System getInvalidSystemAggregator() {
        System outputSystem = new System("InvalidSystem");
        TestCase tcWithNoResource = new TestCase("TCNoRequired", AggregatorClassTests.class);
        Resource inventedResource = getLightElasticResource();
        inventedResource.setResourceID("InventedResourceID");
        AccessMode accessResourceThatNotExist = getAccessModeLightElasticResource();
        accessResourceThatNotExist.setResource(inventedResource);
        tcWithNoResource.addAccessMode(accessResourceThatNotExist);
        outputSystem.addListTestCases(this.aggregatorTestClasses());
        //Resource that doesn't exist
        outputSystem.addTestCase(tcWithNoResource);
        //Repeated test case
        outputSystem.addTestCase(new TestCase("tSevenAgg", AggregatorClassTests.class, Arrays.asList(this.getNewMockElasticAccessMode(),
                this.getMediumInelasticAccessMode())));
        outputSystem.addListOfResources(Arrays.asList(this.getLightElasticResource(), this.getMockElasticResource(),
                this.getMediumElasticResource(), this.getHeavyInelasticResource(), this.getMediumInelasticResource(),
                this.getMediumInelasticResource()));

        return outputSystem;
    }


    public List<TGroup> getExpectedSchedulerTGroups() {
        List<TestCase> listTestCases = schedulerTestCases();

        TGroup tGroupOne = new TGroup(listTestCases.stream().limit(6).collect(Collectors.toList()),
                Arrays.asList(this.getHeavyInelasticResource(), this.getMediumSchedulerElasticResource()));
        TGroup tGroupTwo = new TGroup(new ArrayList<>(Arrays.asList(listTestCases.get(0), listTestCases.get(4), listTestCases.get(5))),
                Arrays.asList(this.getHeavyInelasticResource(), this.getNewSchedulerLightElasticResource()));
        TGroup tGroupThree = new TGroup(new ArrayList<>(listTestCases.subList(2, 6)),
                Arrays.asList(this.getMediumSchedulerInelasticResource(), this.getMediumSchedulerElasticResource()));
        TGroup tGroupFour = new TGroup(new ArrayList<>(listTestCases.subList(4, 6)),
                Arrays.asList(this.getMediumSchedulerInelasticResource(), this.getNewSchedulerLightElasticResource()));
        TGroup tGroupFive = new TGroup(new ArrayList<>(listTestCases.subList(6, 8)),
                Arrays.asList(this.getHeavyInelasticResource(), this.getNewSchedulerLightElasticResource()));
        TGroup tGroupSix = new TGroup(new ArrayList<>(listTestCases.subList(6, 8)),
                Arrays.asList(this.getHeavyInelasticResource(), this.getMediumSchedulerElasticResource()));
        TGroup tGroupSeven = new TGroup(Collections.singletonList(listTestCases.get(8)),
                Arrays.asList(this.getSingleSchedulerTestCaseResource(), this.getMediumSchedulerElasticResource()));
        TGroup tGroupEight = new TGroup(new ArrayList<>(listTestCases.subList(9, 12)),
                Collections.singletonList(this.getNewSchedulerLightElasticResource()));
        TGroup tGroupNine = new TGroup(new ArrayList<>(listTestCases.subList(9, 12)),
                Collections.singletonList(this.getMediumSchedulerElasticResource()));

        return Arrays.asList(tGroupFour, tGroupFive, tGroupSeven, tGroupThree, tGroupNine, tGroupEight, tGroupOne, tGroupSix, tGroupTwo);
    }

    public List<TestCase> schedulerTestCases() {

        List<TestCase> listTestCases = new LinkedList<>();

        AccessMode readOnlyAccessModeMediumInelastic = this.getAccessModeHeavyInElasticResource();
        readOnlyAccessModeMediumInelastic.setType(new AccessModeTypes(READONLY));
        AccessMode readOnlyAccessModeLightElastic = this.getAccessModeLightElasticResource();
        readOnlyAccessModeLightElastic.setType(new AccessModeTypes(READONLY));

        listTestCases.add(new TestCase("tOneSch", AggregatorClassTests.class, Arrays.asList(this.getAccessModeLightElasticResource(), this.getAccessModeHeavyInElasticResource())));
        listTestCases.add(new TestCase("tTwoSch", AggregatorClassTests.class, Arrays.asList(this.getSchedulerMediumElasticAccessMode(), this.getAccessModeHeavyInElasticResource())));
        listTestCases.add(new TestCase("tThreeSch", AggregatorClassTests.class, Arrays.asList(this.getSchedulerMediumElasticAccessMode(), this.getSchedulerMediumInelasticAccessMode())));
        listTestCases.add(new TestCase("tFourSch", AggregatorClassTests.class, Arrays.asList(this.getSchedulerMediumElasticAccessMode(), this.getSchedulerMediumInelasticAccessMode())));
        listTestCases.add(new TestCase("tFiveSch", AggregatorClassTests.class, Arrays.asList(this.getAccessModeLightElasticResource(), this.getSchedulerMediumInelasticAccessMode())));
        listTestCases.add(new TestCase("tSixSch", AggregatorClassTests.class, Arrays.asList(this.getAccessModeLightElasticResource(), this.getSchedulerMediumInelasticAccessMode())));
        listTestCases.add(new TestCase("tSevenSch", AggregatorClassTests.class, Arrays.asList(readOnlyAccessModeLightElastic, readOnlyAccessModeMediumInelastic)));
        listTestCases.add(new TestCase("tEightSch", AggregatorClassTests.class, Arrays.asList(readOnlyAccessModeLightElastic, readOnlyAccessModeMediumInelastic)));
        listTestCases.add(new TestCase("tNineSch", AggregatorClassTests.class, Arrays.asList(this.getSchedulerMediumElasticAccessMode(), this.getSchedulerSingleTestCaseAccessMode())));
        listTestCases.add(getSchedulerTestCaseClassWithOneResource("tTenSch", this.getAccessModeLightElasticResource()));
        listTestCases.add(getSchedulerTestCaseClassWithOneResource("tElevenSch", this.getAccessModeLightElasticResource()));
        listTestCases.add(getSchedulerTestCaseClassWithOneResource("tTwelveSch", this.getAccessModeLightElasticResource()));

        return listTestCases;
    }

    public Resource getMediumSchedulerElasticResource() {
        Resource expectedMediumElasticResource = new Resource("medElasRes");
        ElasticityModel mediumElasModel = getElasticityModelMockResource();
        mediumElasModel.setElasticityCost(5);
        expectedMediumElasticResource.addReplaceableResource("heavyInElasRest");
        expectedMediumElasticResource.setElasticityModel(mediumElasModel);
        List<Capacity> requiredCapacities = new LinkedList<>();
        requiredCapacities.add(new Capacity(Capacity.MEMORY_NAME, 2));
        requiredCapacities.add(new Capacity(Capacity.PROCESSOR_NAME, 1));
        expectedMediumElasticResource.setMinimalCapacities(requiredCapacities);

        return expectedMediumElasticResource;
    }

    public Resource getNewSchedulerLightElasticResource() {
        Resource resLight = new Resource(this.getLightElasticResource());
        resLight.addReplaceableResource(MEDIUM_INELASTIC_ID);
        resLight.setMinimalCapacities(Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 1),
                new Capacity(Capacity.PROCESSOR_NAME, 0.5)));

        return resLight;
    }

    public Resource getMediumSchedulerInelasticResource() {
        Resource expectedMediumInelasticResource = new Resource(MEDIUM_INELASTIC_ID);
        expectedMediumInelasticResource.addHierarchyParent(PARENT_INELASTIC);
        expectedMediumInelasticResource.addReplaceableResource(HEAVY_INELASTIC_ID);
        ElasticityModel expectedElasticityModelMedium = new ElasticityModel("MedInElasticityModel");
        expectedElasticityModelMedium.setElasticity(2);
        expectedElasticityModelMedium.setElasticityCost(35.0);
        expectedMediumInelasticResource.setElasticityModel(expectedElasticityModelMedium);
        expectedMediumInelasticResource.setMinimalCapacities(Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 2),
                new Capacity(Capacity.PROCESSOR_NAME, 1)));

        return expectedMediumInelasticResource;
    }


    public List<TGroup> getNoRepeatedExpectedTGroups() {
        List<TestCase> listTestCases = schedulerTestCases();

        TGroup tGroupOne = new TGroup(new ArrayList<>(listTestCases.subList(0, 6)),
                Arrays.asList(this.getHeavyInelasticResource(), this.getMediumSchedulerElasticResource()));

        TGroup tGroupFive = new TGroup(new ArrayList<>(listTestCases.subList(6, 8)),
                Arrays.asList(this.getHeavyInelasticResource(), this.getNewSchedulerLightElasticResource()));

        TGroup tGroupSeven = new TGroup(Collections.singletonList(listTestCases.get(8)),
                Arrays.asList(this.getSingleSchedulerTestCaseResource(), this.getMediumSchedulerElasticResource()));

        TGroup tGroupEight = new TGroup(new ArrayList<>(listTestCases.subList(9, 12)),
                Collections.singletonList(this.getNewSchedulerLightElasticResource()));

        return Arrays.asList(tGroupFive, tGroupSeven, tGroupEight, tGroupOne);
    }

    public List<TGroup> getInvalidTGroups() {
        List<TestCase> listTestCases = schedulerTestCases();

        TGroup tGroupOne = new TGroup(new ArrayList<>(listTestCases.subList(0, 6)),
                Arrays.asList(this.getHeavyInelasticResource(), this.getMediumSchedulerElasticResource(), this.getMediumSchedulerElasticResource()));

        return Collections.singletonList(tGroupOne);
    }

    public List<Activity> getExpectedActivities() {
        List<Activity> currentActivities = new LinkedList<>();
        List<TestCase> testCases = this.schedulerTestCases();
        TJob tJobOne = new TJob();
        tJobOne.addTestCase(testCases.get(8));
        tJobOne.addResource(this.getSingleSchedulerTestCaseResource());
        tJobOne.addResource(this.getMediumSchedulerElasticResource());
        Activity activityOne = new Activity(tJobOne);
        currentActivities.add(activityOne);
        TJob tJobThree = new TJob();
        tJobThree.addTestCase(testCases.get(10));
        tJobThree.addTestCase(testCases.get(9));
        tJobThree.addTestCase(testCases.get(11));
        tJobThree.addResource(this.getLightElasticResource());
        Activity activityThree = new Activity(tJobThree);
        currentActivities.add(activityThree);
        TJob tJobFour = new TJob();
        tJobFour.addTestCase(testCases.get(3));
        tJobFour.addTestCase(testCases.get(2));
        tJobFour.addTestCase(testCases.get(1));
        tJobFour.addResource(this.getHeavyInelasticResource());
        tJobFour.addResource(this.getMediumSchedulerElasticResource());
        Activity activityFour = new Activity(tJobFour);
        currentActivities.add(activityFour);
        TJob tJobFive = new TJob();
        tJobFive.addTestCase(testCases.get(7));
        tJobFive.addTestCase(testCases.get(6));
        tJobFive.addResource(this.getHeavyInelasticResource());
        tJobFive.addResource(this.getMediumSchedulerElasticResource());
        Activity activityFive = new Activity(tJobFive);
        activityFive.addPredecessor(activityFour);
        activityFive.addPredecessor(activityOne);
        currentActivities.add(activityFive);
        TJob tJobTwo = new TJob();
        tJobTwo.addTestCase(testCases.get(4));
        tJobTwo.addTestCase(testCases.get(0));
        tJobTwo.addTestCase(testCases.get(5));
        tJobTwo.addResource(this.getHeavyInelasticResource());
        tJobTwo.addResource(this.getLightElasticResource());
        Activity activityTwo = new Activity(tJobTwo);
        activityTwo.addPredecessor(activityFive);
        currentActivities.add(activityTwo);

        return currentActivities;
    }

    public List<Activity> getExpectedNonRepeatedActivities() {
        List<Activity> currentActivities = new LinkedList<>();
        List<TestCase> testCases = this.schedulerTestCases();
        TJob tJobOne = new TJob();
        tJobOne.addTestCase(testCases.get(8));
        tJobOne.addResource(this.getSingleSchedulerTestCaseResource());
        tJobOne.addResource(this.getMediumSchedulerElasticResource());
        Activity activityOne = new Activity(tJobOne);
        TJob tJobTwo = new TJob();
        tJobTwo.addTestCase(testCases.get(7));
        tJobTwo.addTestCase(testCases.get(6));
        tJobTwo.addResource(this.getHeavyInelasticResource());
        tJobTwo.addResource(this.getNewSchedulerLightElasticResource());
        Activity activityTwo = new Activity(tJobTwo);
        TJob tJobThree = new TJob();
        tJobThree.addTestCase(testCases.get(10));
        tJobThree.addTestCase(testCases.get(9));
        tJobThree.addTestCase(testCases.get(11));
        tJobThree.addResource(this.getNewSchedulerLightElasticResource());
        Activity activityThree = new Activity(tJobThree);
        TJob tJobFour = new TJob();
        tJobFour.addTestCase(testCases.get(4));
        tJobFour.addTestCase(testCases.get(3));
        tJobFour.addTestCase(testCases.get(5));
        tJobFour.addTestCase(testCases.get(2));
        tJobFour.addTestCase(testCases.get(1));
        tJobFour.addTestCase(testCases.get(0));
        tJobFour.addResource(this.getHeavyInelasticResource());
        tJobFour.addResource(this.getMediumSchedulerElasticResource());
        Activity activityFour = new Activity(tJobFour);
        activityFour.addPredecessor(activityOne);
        activityFour.addPredecessor(activityTwo);
        currentActivities.add(activityTwo);
        currentActivities.add(activityOne);
        currentActivities.add(activityThree);
        currentActivities.add(activityFour);

        return currentActivities.stream().sorted(Comparator.comparing(Activity::toString)).collect(Collectors.toList());
    }
}
