package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.ResourceSerializer;
import giis.retorch.orchestration.model.System;
import giis.retorch.orchestration.model.*;
import giis.retorch.orchestration.testdata.integration.IntegrationClassOneTests;
import giis.retorch.orchestration.testdata.integration.IntegrationClassTwoTests;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * {@code IntegrationUtils} class provides a series of methods create {@code TestCase}s, {@code TGroup}s and {@code Activity}
 * to be used during the integration testing
 */
public class IntegrationUtils extends GenericUtils {


    private static final Logger logIntegrationUtils = LoggerFactory.getLogger(IntegrationUtils.class);
    Map<String, Resource> mapResourcesAvailable;

    public IntegrationUtils() throws IOException {
        mapResourcesAvailable = new ResourceSerializer().deserializeResources("SyntheticIntegration");
    }

    /**
     * This method returns the system who is expected as the output of the integration classifier. Is composed
     * by three different resources (Heavy-Light inelastic and Mock-Light-Medium elastic) and 16 test cases who
     * perform different resource usage
     */
    public System getExpectedSystemIntegration() {
        System outputSystem = new System("SyntheticIntegration");
        outputSystem.addResourceClass(this.getHeavyInelasticResource());
        outputSystem.addResourceClass(this.getMediumElasticResource());
        outputSystem.addResourceClass(this.getLightElasticResource());
        outputSystem.addResourceClass(this.getLightInelasticResource());
        outputSystem.addResourceClass(this.getMockElasticResource());
        outputSystem.addListTestCases(this.generateExpectedTestCasesSystem());
        return outputSystem;
    }

    private List<TestCase> generateExpectedTestCasesSystem() {
        return IntStream.rangeClosed('A', 'P')
                .mapToObj(c -> this.getTestCaseClass(String.valueOf((char) c)))
                .collect(Collectors.toList());
    }

    private TestCase getTestCaseClass(String type) {
        TestCase output;
        switch (type) {
            case "A":
                output = new TestCase("testAInelasticHeavyRElasticMediumR", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 3,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 4,
                        this.getMediumElasticResource()));
                break;
            case "B":
                output = new TestCase("testBInelasticHeavyRWElasticLightRW", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), true, 4,
                        this.getLightElasticResource()));
                break;
            case "C":
                output = new TestCase("testCInelasticLightNAElasticMediumR", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(NO_ACCESS), true, 15,
                        this.getLightInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 4,
                        this.getMediumElasticResource()));
                break;
            case "D":
                output = new TestCase("testDInelasticLightROElasticMediumRO", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 4,
                        this.getLightInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), false, 1,
                        this.getMediumElasticResource()));
                break;
            case "E":
                output = new TestCase("testEInelasticLightRWElasticLightRW", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), true, 10,
                        this.getLightInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), true, 8,
                        this.getLightElasticResource()));
                break;
            case "F":
                output = new TestCase("testFInelasticHeavyRWElasticLightRW", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getLightElasticResource()));
                break;
            case "G":
                output = new TestCase("testGTwoInelasticHeavyRWElasticLightRW", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getLightElasticResource()));
                break;
            case "H":
                output = new TestCase("testHOneInelasticHeavyRWElasticMockR", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 8,
                        this.getMockElasticResource()));
                break;
            case "I":
                output = new TestCase("testITwoInelasticHeavyRWElasticMockR", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 12,
                        this.getMockElasticResource()));
                break;
            case "J":
                output = new TestCase("testJThreeInelasticHeavyRWElasticMockR", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 6,
                        this.getMockElasticResource()));
                break;
            case "K":
                output = new TestCase("testKFourInelasticHeavyRWElasticMockR", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 15,
                        this.getMockElasticResource()));
                break;
            case "L":
                output = new TestCase("testLOnlyHIOne", IntegrationClassTwoTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                break;
            case "M":
                output = new TestCase("testMOnlyHITwo", IntegrationClassTwoTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READWRITE), false, 1,
                        this.getHeavyInelasticResource()));
                break;
            case "N":
                output = new TestCase("testNOnlyMediumElasticOne", IntegrationClassTwoTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 12,
                        this.getMediumElasticResource()));
                break;
            case "O":
                output = new TestCase("testOOnlyLightElasticTwo", IntegrationClassTwoTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 6,
                        this.getMediumElasticResource()));
                break;
            case "P":
                output = new TestCase("testPOnlyLightElasticThree", IntegrationClassTwoTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 8,
                        this.getMediumElasticResource()));
                break;
            default:
                output = new TestCase("testCaseErroneousNotExist", IntegrationClassOneTests.class);
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 3,
                        this.getHeavyInelasticResource()));
                output.addAccessMode(new AccessMode(new AccessModeTypes(READONLY), true, 4,
                        this.getMediumElasticResource()));
                logIntegrationUtils.error("ERROR: the test case doesn't exist");
        }
        return output;
    }

    public List<TGroup> getListExpectedTGroups() {
        List<TGroup> listTGroups = new LinkedList<>();

        TGroup tGroupOne = new TGroup(Arrays.asList(this.getTestCaseClass("B"), this.getTestCaseClass("F"),
                this.getTestCaseClass("G"), this.getTestCaseClass("H"), this.getTestCaseClass("I"),
                this.getTestCaseClass("J"), this.getTestCaseClass("K")),
                Arrays.asList(mapResourcesAvailable.get(HEAVY_INELASTIC_ID), mapResourcesAvailable.get(LIGHT_INELASTIC_ID)));
        listTGroups.add(tGroupOne);

        TGroup tGroupTwo = new TGroup(Collections.singletonList(this.getTestCaseClass("A")),
                Arrays.asList(mapResourcesAvailable.get(HEAVY_INELASTIC_ID), mapResourcesAvailable.get(MEDIUM_ELASTIC_ID)));
        listTGroups.add(tGroupTwo);

        TGroup tGroupThree = new TGroup(Arrays.asList(this.getTestCaseClass("H"), this.getTestCaseClass("I"),
                this.getTestCaseClass("J"), this.getTestCaseClass("K")),
                Arrays.asList(mapResourcesAvailable.get(HEAVY_INELASTIC_ID), mapResourcesAvailable.get(MEDIUM_ELASTIC_ID)));
        listTGroups.add(tGroupThree);

        TGroup tGroupFour = new TGroup(Arrays.asList(this.getTestCaseClass("H"), this.getTestCaseClass("I"),
                this.getTestCaseClass("J"), this.getTestCaseClass("K")),
                Arrays.asList(mapResourcesAvailable.get(HEAVY_INELASTIC_ID), mapResourcesAvailable.get(MOCK_ELASTIC_ID)));
        listTGroups.add(tGroupFour);

        TGroup tGroupFive = new TGroup(Arrays.asList(this.getTestCaseClass("H"), this.getTestCaseClass("I"),
                this.getTestCaseClass("J"), this.getTestCaseClass("K")), Collections.singletonList(mapResourcesAvailable.get(HEAVY_INELASTIC_ID)));
        listTGroups.add(tGroupFive);

        TGroup tGroupSix = new TGroup(Arrays.asList(this.getTestCaseClass("L"), this.getTestCaseClass("M")),
                Collections.singletonList(mapResourcesAvailable.get(HEAVY_INELASTIC_ID)));
        listTGroups.add(tGroupSix);

        TGroup tGroupSeven = new TGroup(Arrays.asList(this.getTestCaseClass("E"), this.getTestCaseClass("E")),
                Arrays.asList(mapResourcesAvailable.get(LIGHT_ELASTIC_ID), mapResourcesAvailable.get(LIGHT_INELASTIC_ID)));
        listTGroups.add(tGroupSeven);

        TGroup tGroupEight = new TGroup(Arrays.asList(this.getTestCaseClass("C"), this.getTestCaseClass("D")),
                Arrays.asList(mapResourcesAvailable.get(LIGHT_ELASTIC_ID), mapResourcesAvailable.get(MEDIUM_ELASTIC_ID)));
        listTGroups.add(tGroupEight);

        TGroup tGroupNine = new TGroup(Collections.singletonList(this.getTestCaseClass("E")),
                Collections.singletonList(mapResourcesAvailable.get(LIGHT_ELASTIC_ID)));
        listTGroups.add(tGroupNine);

        TGroup tGroupTen = new TGroup(Arrays.asList(this.getTestCaseClass("C"), this.getTestCaseClass("D")),
                Arrays.asList(mapResourcesAvailable.get(LIGHT_INELASTIC_ID), mapResourcesAvailable.get(MEDIUM_ELASTIC_ID)));
        listTGroups.add(tGroupTen);

        TGroup tGroupEleven = new TGroup(Collections.singletonList(this.getTestCaseClass("E")),
                Collections.singletonList(mapResourcesAvailable.get(LIGHT_INELASTIC_ID)));
        listTGroups.add(tGroupEleven);

        TGroup tGroupTwelve = new TGroup(Arrays.asList(this.getTestCaseClass("N"), this.getTestCaseClass("O"),
                this.getTestCaseClass("P")), Collections.singletonList(mapResourcesAvailable.get(MEDIUM_ELASTIC_ID)));
        listTGroups.add(tGroupTwelve);

        return listTGroups.stream()
                .sorted(Comparator.comparing(TGroup::toString))
                .collect(Collectors.toList());
    }

    public List<Activity> getListExpectedActivities() {
        List<Activity> listActivities = new LinkedList<>();

        TJob tJobOneC = new TJob();
        tJobOneC.addTestCase(this.getTestCaseClass("C"));
        tJobOneC.addTestCase(this.getTestCaseClass("D"));
        tJobOneC.addResource(mapResourcesAvailable.get(LIGHT_INELASTIC_ID));
        tJobOneC.addResource(mapResourcesAvailable.get(MEDIUM_ELASTIC_ID));
        Activity activityOneC = new Activity(tJobOneC);

        TJob tJobFiveOP = new TJob();
        tJobFiveOP.addTestCase(this.getTestCaseClass("N"));
        tJobFiveOP.addTestCase(this.getTestCaseClass("O"));
        tJobFiveOP.addTestCase(this.getTestCaseClass("P"));
        tJobFiveOP.addResource(mapResourcesAvailable.get(MEDIUM_ELASTIC_ID));
        Activity activityFiveOP = new Activity(tJobFiveOP);

        TJob tJobSixE = new TJob();
        tJobSixE.addTestCase(this.getTestCaseClass("E"));
        tJobSixE.addResource(mapResourcesAvailable.get(LIGHT_INELASTIC_ID));
        Activity activitySixE = new Activity(tJobSixE);
        activitySixE.addPredecessor(activityOneC);

        TJob tJobNineBFG = new TJob();
        tJobNineBFG.addTestCase(this.getTestCaseClass("B"));
        tJobNineBFG.addTestCase(this.getTestCaseClass("F"));
        tJobNineBFG.addTestCase(this.getTestCaseClass("G"));
        tJobNineBFG.addResource(mapResourcesAvailable.get(HEAVY_INELASTIC_ID));
        tJobNineBFG.addResource(mapResourcesAvailable.get(LIGHT_INELASTIC_ID));
        Activity activityNineBFG = new Activity(tJobNineBFG);

        TJob tJobThreeA = new TJob();
        tJobThreeA.addTestCase(this.getTestCaseClass("A"));
        tJobThreeA.addResource(mapResourcesAvailable.get(HEAVY_INELASTIC_ID));
        tJobThreeA.addResource(mapResourcesAvailable.get(MEDIUM_ELASTIC_ID));
        Activity activityTestA = new Activity(tJobThreeA);
        activityTestA.addPredecessor(activityNineBFG);

        TJob tJobSevenHIJK = new TJob();
        tJobSevenHIJK.addTestCase(this.getTestCaseClass("H"));
        tJobSevenHIJK.addTestCase(this.getTestCaseClass("I"));
        tJobSevenHIJK.addTestCase(this.getTestCaseClass("J"));
        tJobSevenHIJK.addTestCase(this.getTestCaseClass("K"));
        tJobSevenHIJK.addResource(mapResourcesAvailable.get(HEAVY_INELASTIC_ID));
        Activity activitySevenHIJK = new Activity(tJobSevenHIJK);
        activitySevenHIJK.addPredecessor(activityTestA);

        TJob tJobEightLM = new TJob();
        tJobEightLM.addTestCase(this.getTestCaseClass("L"));
        tJobEightLM.addTestCase(this.getTestCaseClass("M"));
        tJobEightLM.addResource(mapResourcesAvailable.get(HEAVY_INELASTIC_ID));
        Activity activityEightLM = new Activity(tJobEightLM);
        activityEightLM.addPredecessor(activitySevenHIJK);

        // Add the activities to the list
        listActivities.add(activityNineBFG);
        listActivities.add(activitySixE);
        listActivities.add(activityFiveOP);
        listActivities.add(activityOneC);
        listActivities.add(activityTestA);
        listActivities.add(activitySevenHIJK);
        listActivities.add(activityEightLM);

        // Return the activity list ordered (for comparing purposes)
        return listActivities.stream()
                .sorted(Comparator.comparing(Activity::toString))
                .collect(Collectors.toList());
    }

    public static void testStringAgainstPath(String expectedOutputPath, String actualOutput) throws IOException {
        String expectedOutput = FileUtils.readFileToString(new File(expectedOutputPath), ENCODING).replace("\r\n", "\n");
        assertEquals("The expected files differ", expectedOutput, actualOutput);
    }
}