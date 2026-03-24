package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.orchestrator.JenkinsOrchestrator;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * {@code JenkinsOrchestratorTests} class provides the tests that validates the generation of scripting code and
 * the different scripts of the {@code JenkinsOrchestrator} component.
 */
public class JenkinsOrchestratorTests {

    protected static final String EXPECTED_OUTPUT_PATH = "src/test/resources/expected_out/";
    protected static final String OUTPUT_PATH_ENVFILES = "retorchfiles/envfiles/";

    private final Logger logger = LoggerFactory.getLogger(JenkinsOrchestratorTests.class);
    private JenkinsOrchestrator jenkinsOrchestrator;
    private OrchestratorUtils utils;
    private final String systemName = "AggregatorUnitTests";

    @Before
    public void setUp() {
        utils = new OrchestratorUtils();
    }

    @Test
    public void testUnitArchOrchValidComplexGraph() throws IOException, EmptyInputException,
            NoFinalActivitiesException {
        String expectedOutputPath = EXPECTED_OUTPUT_PATH + "/outputTestUnitArchOrchValidComplexGraph";
        List<Activity> listComplexActivities = utils.getListComplexActivities();

        jenkinsOrchestrator = new JenkinsOrchestrator(listComplexActivities, systemName);
        Map<Integer, LinkedList<Activity>> listActivities = jenkinsOrchestrator.getExecutionPlan().getSortedActivities();
        String actualOutput = jenkinsOrchestrator.generatePipeline(listActivities);

        utils.compareOutputToFile(expectedOutputPath, actualOutput);
        logger.debug("Starting to check the env files");
        checkTJobEnvFiles(10);
    }

    public void checkTJobEnvFiles(int numberTJobs) throws IOException {
        String expectedOutputPath = EXPECTED_OUTPUT_PATH + "/orchestrationgenerator/";

        for (int i = 0; i < numberTJobs; i++) {
            String tjobEnvFile = "tjob" + (char) ('a' + (i % 26)) + ".env";
            utils.compareFiles(expectedOutputPath + tjobEnvFile, OUTPUT_PATH_ENVFILES + tjobEnvFile);
        }
    }

    @Test
    public void testUnitArchOrchValidSimpleGraph() throws EmptyInputException, NoFinalActivitiesException,
            IOException {
        String expectedOutputPath = EXPECTED_OUTPUT_PATH + "/outputTestUnitArchOrchValidSimpleGraph";

        List<Activity> listSimpleActivities = utils.getListSimpleActivities();
        jenkinsOrchestrator = new JenkinsOrchestrator(listSimpleActivities, systemName);
        Map<Integer, LinkedList<Activity>> listActivities = jenkinsOrchestrator.getExecutionPlan().getSortedActivities();
        String executionPipeline = jenkinsOrchestrator.generatePipeline(listActivities);

        utils.compareOutputToFile(expectedOutputPath, executionPipeline);
        //Checking that the scripts were created correctly:
        checkTJobEnvFiles(5);
        checkScriptsGenerated();
    }
    public void checkScriptsGenerated() throws IOException {
        String expOutputFolder = "src/test/resources/expected_out/scriptler/";
        String actualOutputFolder = "retorchfiles/scripts/";
        utils.compareFiles(expOutputFolder + "tjob-teardown.sh", actualOutputFolder + "tjoblifecycles/tjob-teardown.sh");
        utils.compareFiles(expOutputFolder + "tjob-setup.sh", actualOutputFolder + "tjoblifecycles/tjob-setup.sh");
        utils.compareFiles(expOutputFolder + "tjob-testexecution.sh", actualOutputFolder + "tjoblifecycles/tjob" +
                "-testexecution.sh");
        utils.compareFiles(expOutputFolder + "storeContainerLogs.sh", actualOutputFolder + "storeContainerLogs.sh");
        utils.compareFiles(expOutputFolder + "waitforSUT.sh", actualOutputFolder + "waitforSUT.sh");
        utils.compareFiles(expOutputFolder + "writetime.sh", actualOutputFolder + "writetime.sh");
        utils.compareFiles(expOutputFolder + "coi-setup.sh", actualOutputFolder + "coilifecycles/coi-setup.sh");
        utils.compareFiles(expOutputFolder + "coi-teardown.sh", actualOutputFolder + "coilifecycles/coi-teardown.sh");
        utils.compareFiles(expOutputFolder + "savetjoblifecycledata.sh", actualOutputFolder + "savetjoblifecycledata.sh");
        utils.compareFiles(expOutputFolder + "storeContainerLogs.sh", actualOutputFolder + "storeContainerLogs.sh");
        utils.compareFiles(expOutputFolder + "printLog.sh", actualOutputFolder + "printLog.sh");

    }

    @Test(expected = EmptyInputException.class)
    public void testUnitArchOrchInvalidNoActivities() throws EmptyInputException, NoFinalActivitiesException,
            IOException {
        jenkinsOrchestrator = new JenkinsOrchestrator(new LinkedList<>(), systemName);
    }

    @Test(expected = NoFinalActivitiesException.class)
    public void testUnitArchOrchInvalidNoInitialAndFinalActivities() throws EmptyInputException,
            NoFinalActivitiesException, IOException {
        List<Activity> listSimpleActivities = utils.getNotValidActivities();
        jenkinsOrchestrator = new JenkinsOrchestrator(listSimpleActivities, systemName);
    }

}