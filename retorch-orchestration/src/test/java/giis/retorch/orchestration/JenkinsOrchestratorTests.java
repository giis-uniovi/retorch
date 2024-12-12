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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * {@code JenkinsOrchestratorTests} class provides the tests that validates the generation of scripting code and
 * the different scripts of the {@code JenkinsOrchestrator} component.
 */
public class JenkinsOrchestratorTests {

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
        String expOutputFolder = "src/test/resources/expected_out/scriptler/";
        String actualOutputFolder = "retorchfiles/scripts/";
        compareFiles(expOutputFolder + "tjob-teardown.sh", actualOutputFolder + "tjoblifecycles/tjob-teardown.sh");
        compareFiles(expOutputFolder + "tjob-setup.sh", actualOutputFolder + "tjoblifecycles/tjob-setup.sh");
        compareFiles(expOutputFolder + "tjob-testexecution.sh", actualOutputFolder + "tjoblifecycles/tjob" +
                "-testexecution.sh");
        compareFiles(expOutputFolder + "waitforSUT.sh", actualOutputFolder + "waitforSUT.sh");
        compareFiles(expOutputFolder + "writetime.sh", actualOutputFolder + "writetime.sh");
        compareFiles(expOutputFolder + "coi-setup.sh", actualOutputFolder + "coilifecycles/coi-setup.sh");
        compareFiles(expOutputFolder + "coi-teardown.sh", actualOutputFolder + "coilifecycles/coi-teardown.sh");
        compareFiles(expOutputFolder + "savetjoblifecycledata.sh", actualOutputFolder + "savetjoblifecycledata.sh");
        compareFiles(expOutputFolder + "storeContainerLogs.sh", actualOutputFolder + "storeContainerLogs.sh");
        compareFiles(expOutputFolder + "printLog.sh", actualOutputFolder + "printLog.sh");
    }

    public void compareFiles(String pathExpected, String pathobtained) throws IOException {
        String expectedContent = readFileContent(pathExpected);//Read two file content
        String obtainedContent = readFileContent(pathobtained);
        Assert.assertEquals(expectedContent, obtainedContent);//Compare two file content
    }

    private String readFileContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes);
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