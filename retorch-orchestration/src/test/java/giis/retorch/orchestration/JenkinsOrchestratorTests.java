package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.orchestrator.JenkinsOrchestrator;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            NoFinalActivitiesException, URISyntaxException {
        List<Activity> listComplexActivities = utils.getListComplexActivities();
        URI uri = ClassLoader.getSystemResource(".//expected_out" + "//outputTestUnitArchOrchValidComplexGraph").toURI();
        jenkinsOrchestrator = new JenkinsOrchestrator(listComplexActivities, systemName);
        Map<Integer, LinkedList<Activity>> listActivities = jenkinsOrchestrator.getExecutionPlan().getSortedActivities();
        String goodUrl = URLDecoder.decode(String.valueOf(uri), StandardCharsets.UTF_8.name()).replace("file:/", "/");
        File file = new File(goodUrl);
        String expectedOutput = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String actualOutput = jenkinsOrchestrator.generatePipeline(listActivities);

        Assert.assertEquals(expectedOutput, actualOutput);

        checkTJobEnvFiles(9);
    }

    public void checkTJobEnvFiles(int numberTJobs) throws IOException {
        String expectedOutput = EXPECTED_OUTPUT_PATH + "/orchestrationgenerator/";

        for (int i = 0; i < numberTJobs; i++) {
            String tjobEnvFile = "tjob" + (char) ('a' + (i % 26)) + ".env";
            utils.compareFiles(expectedOutput + tjobEnvFile, OUTPUT_PATH_ENVFILES + tjobEnvFile);
        }
    }

    @Test
    public void testUnitArchOrchValidSimpleGraph() throws EmptyInputException, NoFinalActivitiesException,
            URISyntaxException, IOException {
        List<Activity> listSimpleActivities = utils.getListSimpleActivities();
        jenkinsOrchestrator = new JenkinsOrchestrator(listSimpleActivities, systemName);
        Map<Integer, LinkedList<Activity>> listActivities = jenkinsOrchestrator.getExecutionPlan().getSortedActivities();
        String executionPipeline = jenkinsOrchestrator.generatePipeline(listActivities);
        String path;
        path = "expected_out/outputTestUnitArchOrchValidSimpleGraph";
        URI uri = ClassLoader.getSystemResource(path).toURI();
        String realUrl = URLDecoder.decode(String.valueOf(uri), StandardCharsets.UTF_8.name());
        String goodUrl = realUrl.replace("file:/", "/");
        File file = new File(goodUrl);
        if (!file.exists()) {
            logger.error("The file were not found: {} the URI was: {} , the URL was:  {}", path, realUrl, goodUrl);
        }
        String expectedOutput = new String(Files.readAllBytes(file.toPath())).replace("\r\n", "\n");
        Assert.assertEquals(expectedOutput, executionPipeline);
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