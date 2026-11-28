package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.config.RetorchPaths;
import giis.retorch.orchestration.exception.InvalidPlanException;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.orchestrator.OrchestrationWriter;
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
 * the different scripts of the {@code OrchestrationWriter} component.
 */
public class JenkinsOrchestratorTests {

    private static final String EXPECTED_OUTPUT_PATH = "src/test/resources/expected_out/";
    private static final String OUTPUT_PATH_ENVFILES = ".retorch/envfiles/";

    private final Logger logger = LoggerFactory.getLogger(JenkinsOrchestratorTests.class);
    private ExecutionPlan plan;

    /** Renders the plan and writes the scripts and env files under the working directory. */
    private String generate(ExecutionPlan planToGenerate) throws Exception {
        RetorchPaths paths = new RetorchPaths();
        return new OrchestrationWriter(paths).generate(planToGenerate, RetorchConfig.load(paths));
    }
    private OrchestratorUtils utils;

    @Before
    public void setUp() {
        utils = new OrchestratorUtils();
    }

    @Test
    public void testUnitArchOrchExecutionPlanGraphHasValidDependencies() throws Exception {
        plan = ExecutionPlan.fromActivities("defaultName", utils.getListComplexActivities());
        Map<Integer, LinkedList<Activity>> activities = plan.getSortedActivities();

        giis.retorch.orchestration.orchestrator.ExecutionPlanWriter.PlanFile planFile =
                new giis.retorch.orchestration.orchestrator.ExecutionPlanWriter().toPlanFile(activities);

        java.util.Map<String, Integer> stageById = new java.util.HashMap<>();
        for (giis.retorch.orchestration.orchestrator.ExecutionPlanWriter.Node node : planFile.tjobs) {
            stageById.put(node.id, node.stage);
        }
        org.junit.Assert.assertEquals(activities.values().stream().mapToInt(List::size).sum(), planFile.tjobs.size());
        for (giis.retorch.orchestration.orchestrator.ExecutionPlanWriter.Node node : planFile.tjobs) {
            org.junit.Assert.assertEquals(node.id, node.id.toLowerCase(java.util.Locale.ROOT));
            org.junit.Assert.assertEquals("each dependency appears once", node.predecessors.size(),
                    new java.util.HashSet<>(node.predecessors).size());
            for (String predecessor : node.predecessors) {
                org.junit.Assert.assertTrue("unknown predecessor " + predecessor, stageById.containsKey(predecessor));
                org.junit.Assert.assertTrue("a predecessor must run in an earlier stage",
                        stageById.get(predecessor) < node.stage);
            }
        }
    }

    @Test
    public void testUnitArchOrchValidComplexGraph() throws Exception {
        String expectedOutputPath = EXPECTED_OUTPUT_PATH + "/outputTestUnitArchOrchValidComplexGraph";
        List<Activity> listComplexActivities = utils.getListComplexActivities();

        plan = ExecutionPlan.fromActivities("defaultName", listComplexActivities);
        String actualOutput = generate(plan);

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
    public void testUnitArchOrchValidSimpleGraph() throws Exception {
        String expectedOutputPath = EXPECTED_OUTPUT_PATH + "/outputTestUnitArchOrchValidSimpleGraph";

        List<Activity> listSimpleActivities = utils.getListSimpleActivities();
        plan = ExecutionPlan.fromActivities("defaultName", listSimpleActivities);
        String executionPipeline = generate(plan);

        utils.compareOutputToFile(expectedOutputPath, executionPipeline);
        //Checking that the scripts were created correctly:
        checkTJobEnvFiles(5);
        checkScriptsGenerated();
    }
    public void checkScriptsGenerated() throws IOException {
        String expOutputFolder = "src/test/resources/expected_out/scriptler/";
        String actualOutputFolder = ".retorch/scripts/";
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

    @Test
    public void testUnitArchOrchProfilingOptionAddsMeasurementAndUsageProfile() throws Exception {
        System.setProperty("retorch.profiling", "true");
        try {
            List<Activity> listSimpleActivities = utils.getListSimpleActivities();
            plan = ExecutionPlan.fromActivities("defaultName", listSimpleActivities);
            String executionPipeline = generate(plan);

            String expFolder = EXPECTED_OUTPUT_PATH + "/profiling/";
            String actualFolder = ".retorch/scripts/";
            utils.compareOutputToFile(expFolder + "outputTestUnitArchOrchValidSimpleGraph", executionPipeline);
            utils.compareFiles(expFolder + "tjob-setup.sh", actualFolder + "tjoblifecycles/tjob-setup.sh");
            utils.compareFiles(expFolder + "tjob-teardown.sh", actualFolder + "tjoblifecycles/tjob-teardown.sh");
            utils.compareFiles(expFolder + "savetjoblifecycledata.sh", actualFolder + "savetjoblifecycledata.sh");
            utils.compareFiles(expFolder + "resourcemonitor.sh", actualFolder + "resourcemonitor.sh");
            utils.compareFiles(expFolder + "generateusageprofile.sh", actualFolder + "generateusageprofile.sh");
            utils.compareFiles(expFolder + "executionplan.json", ".retorch/executionplan.json");
        } finally {
            System.clearProperty("retorch.profiling");
        }
    }

    @Test
    public void testUnitArchOrchWithoutProfilingGeneratesNoMeasurementNorUsageProfile() throws Exception {
        plan = ExecutionPlan.fromActivities("defaultName", utils.getListSimpleActivities());
        String executionPipeline = generate(plan);
        org.junit.Assert.assertFalse(executionPipeline.contains("Usage-Profile"));
        org.junit.Assert.assertFalse(executionPipeline.contains("publishHTML"));
        org.junit.Assert.assertFalse(java.nio.file.Files.exists(java.nio.file.Paths.get(".retorch/scripts/resourcemonitor.sh")));
        org.junit.Assert.assertFalse(java.nio.file.Files.exists(java.nio.file.Paths.get(".retorch/scripts/generateusageprofile.sh")));
        org.junit.Assert.assertFalse(java.nio.file.Files.exists(java.nio.file.Paths.get(".retorch/executionplan.json")));
    }

    @Test
    public void testUnitArchOrchCustomCoiTearDownAndJenkinsPostHooks() throws Exception {
        java.nio.file.Path coiTearDown = java.nio.file.Paths.get(".retorch/customscriptscode/custom-coi-teardown");
        java.nio.file.Path jenkinsPost = java.nio.file.Paths.get(".retorch/customscriptscode/custom-jenkins-post");
        java.nio.file.Files.write(coiTearDown, "echo coverage-report".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(jenkinsPost, "      archiveArtifacts artifacts: 'coverage/*.xml'"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            plan = ExecutionPlan.fromActivities("defaultName", utils.getListSimpleActivities());
            String pipeline = generate(plan);

            String coiScript = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(".retorch/scripts/coilifecycles/coi-teardown.sh")), java.nio.charset.StandardCharsets.UTF_8);
            org.junit.Assert.assertTrue(coiScript.contains("echo coverage-report"));
            org.junit.Assert.assertTrue(pipeline.contains("      archiveArtifacts artifacts: 'coverage/*.xml'\n      archiveArtifacts artifacts: 'target/testlogs"));
        } finally {
            java.nio.file.Files.deleteIfExists(coiTearDown);
            java.nio.file.Files.deleteIfExists(jenkinsPost);
        }
    }

    @Test
    public void testUnitArchOrchCustomTjobTearDownRunsBeforeTheContainersAreRemoved() throws Exception {
        plan = ExecutionPlan.fromActivities("defaultName", utils.getListSimpleActivities());
        generate(plan);
        String script = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(".retorch/scripts/tjoblifecycles/tjob-teardown.sh")), java.nio.charset.StandardCharsets.UTF_8);
        org.junit.Assert.assertTrue(script.indexOf("Start executing custom commands") < script.indexOf("down --volumes"));
    }

    @Test
    public void testUnitArchOrchCustomUsageProfileHookIsInsertedInTheScript() throws Exception {
        java.nio.file.Path hook = java.nio.file.Paths.get(".retorch/customscriptscode/custom-usageprofile");
        java.nio.file.Files.write(hook, "echo preparing-the-profiling-jar".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.setProperty("retorch.profiling", "true");
        try {
            plan = ExecutionPlan.fromActivities("defaultName", utils.getListSimpleActivities());
            generate(plan);
            String script = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(".retorch/scripts/generateusageprofile.sh")), java.nio.charset.StandardCharsets.UTF_8);
            org.junit.Assert.assertTrue(script.contains("echo preparing-the-profiling-jar"));
            org.junit.Assert.assertTrue("the hook runs before the jar is looked for",
                    script.indexOf("preparing-the-profiling-jar") < script.indexOf("Downloading the profiling tool"));
        } finally {
            System.clearProperty("retorch.profiling");
            java.nio.file.Files.deleteIfExists(hook);
        }
    }

    @Test(expected = EmptyInputException.class)
    public void testUnitArchOrchInvalidNoActivities() throws Exception {
        plan = ExecutionPlan.fromActivities("defaultName", new LinkedList<>());
    }

    @Test(expected = InvalidPlanException.class)
    public void testUnitArchOrchInvalidNoInitialAndFinalActivities() throws Exception {
        List<Activity> listSimpleActivities = utils.getNotValidActivities();
        plan = ExecutionPlan.fromActivities("defaultName", listSimpleActivities);
    }

}