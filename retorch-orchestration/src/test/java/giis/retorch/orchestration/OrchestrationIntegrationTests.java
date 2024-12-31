package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.classifier.RetorchClassifier;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.System;
import giis.retorch.orchestration.model.TGroup;
import giis.retorch.orchestration.orchestrator.JenkinsOrchestrator;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.orchestration.scheduler.NoTGroupsInTheSchedulerException;
import giis.retorch.orchestration.scheduler.NotValidSystemException;
import giis.retorch.orchestration.scheduler.RetorchAggregator;
import giis.retorch.orchestration.scheduler.RetorchScheduler;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static giis.retorch.orchestration.IntegrationUtils.testStringAgainstPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code OrchestrationIntegrationTests}  class provides the integration test that departing from annotated test cases with
 * {@code AccessMode} and the ResourceConfiguration.json and CIConfiguration.json files, creates a Jenkinsfile.
 * The test method validates that the Jenkinsfile structure is as expected.
 */
public class OrchestrationIntegrationTests {

    protected static final String EXPECTED_OUTPUT_PATH = "src/test/resources/expected_out/";
    protected static final String OUTPUT_PATH_ENVFILES = "retorchfiles/envfiles/";
    private RetorchClassifier classifier;
    private IntegrationUtils utils;
    Logger loggerTesIntegration = LoggerFactory.getLogger(OrchestrationIntegrationTests.class);

    @Before
    public void setUp() {
        try {
            utils = new IntegrationUtils();
        } catch (IOException e) {
            loggerTesIntegration.error("The integration utils cant be loaded");
        }
        classifier = new RetorchClassifier();
    }

    @Test
    public void testIntegrationRetorchArchitecture() throws EmptyInputException, NotValidSystemException,
            NoTGroupsInTheSchedulerException, NoFinalActivitiesException, IOException, ClassNotFoundException,
            URISyntaxException {
        String packageName = "giis.retorch.orchestration.testdata.integration";
        System currentSystem = classifier.getSystemFromPackage("SyntheticIntegration", packageName);
        System expectedSystem = utils.getExpectedSystemIntegration();
        assertEquals(expectedSystem.toString(), currentSystem.toString());
        RetorchAggregator aggregator = new RetorchAggregator(currentSystem);
        List<TGroup> tGroupsOutputAggregator = aggregator.generateTGroups();
        List<TGroup> expectedTGroups = utils.getListExpectedTGroups();
        assertTrue(tGroupsOutputAggregator.containsAll(expectedTGroups));
        RetorchScheduler scheduler = new RetorchScheduler(tGroupsOutputAggregator);
        List<Activity> listActivities = scheduler.generateActivities();
        List<Activity> listExpectedActivities =
                utils.getListExpectedActivities().stream().sorted(Comparator.comparing(Activity::toString)).collect(Collectors.toList());
        assertEquals(listExpectedActivities.toString(), listActivities.toString());
        JenkinsOrchestrator orchestrator = new JenkinsOrchestrator(listActivities, "SyntheticIntegration");
        Map<Integer, LinkedList<Activity>> listActivitiesScheduledOrchestrator =
                orchestrator.getExecutionPlan().getSortedActivities();
        String executionPipeline = orchestrator.generatePipeline(listActivitiesScheduledOrchestrator);
        testStringAgainstPath(EXPECTED_OUTPUT_PATH+"outputTestIntegration", executionPipeline);
        checkTJobEnvFiles(7);
    }

    public void checkTJobEnvFiles(int numberTJobs) throws IOException {
        String expectedOutput = EXPECTED_OUTPUT_PATH + "/orchestrationgenerator/";
        for (int i = 0; i < numberTJobs; i++) {
            String tjobEnvFile = "tjob" + (char) ('a' + (i % 26)) + ".env";
            loggerTesIntegration.debug("Checking TJob env file: {} ", tjobEnvFile);
            utils.compareFiles(expectedOutput + "int-" + tjobEnvFile, OUTPUT_PATH_ENVFILES + tjobEnvFile);
        }
    }

}