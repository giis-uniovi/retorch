package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.classifier.ResourceSerializer;
import giis.retorch.orchestration.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
/**
 * The {@code JenkinsOrchestrator} class provides several method that enable the derivation from a {@code ExecutionPlan}
 * into the Jenkinsfile and the different scripting code to make the  {@code Resources} set-up and tear-down as
 * well as the {@code TestCase}s execution scripts.
 */
public class JenkinsOrchestrator implements IRetorchOrchestrator {

    private static final String TJOB_BASE_PATH = "testsBasePath";
    private static final String ENVIRONMENT_FOLDER_NAME = "retorchfiles/envfiles/";
    private static final Logger log = LoggerFactory.getLogger(JenkinsOrchestrator.class);
    private static final String SH_COMMAND = "          sh '";
    private final Properties ciConfiguration;
    int tJobCounter;
    int poolPorts = 5000;
    String frontendPort = "";
    HashMap<String, String> portsSUTs;
    private ExecutionPlan executionPlan;
    private final Map<String, Resource> listAllResources;
    ResourceSerializer deserializer;
    /**
     * Default constructor, gets a list of {@code Activity} and deserialize the ResourceJSON.json and retorchCI properties
     * the files, filling the different structures that would be used by the JenkinsOrchestrator
     * @param activityEntityListFromScheduler List of {@code Activity} that would be the input of the {@code JenkinsOrchestrator}
     */
    public JenkinsOrchestrator(List<Activity> activityEntityListFromScheduler, String systemName) throws EmptyInputException,
            NoFinalActivitiesException, IOException {
        deserializer= new ResourceSerializer();
        this.executionPlan = new ExecutionPlan("defaultName", activityEntityListFromScheduler);
        this.listAllResources = deserializer.deserializeResources(systemName);
        ciConfiguration = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("retorchfiles/configurations/retorchCI.properties"))) {
            ciConfiguration.load(input);
        } catch (IOException e) {
            log.error("Not possible to load properties file due to: {} ", e.getMessage());
        }
        this.tJobCounter = 1;
        portsSUTs = new HashMap<>();
    }

    @Override
    public ExecutionPlan getExecutionPlan() {
        return this.executionPlan;
    }

    @Override
    public void setExecutionPlan(ExecutionPlan plan) {
        this.executionPlan = plan;
    }

    @Override
    public String generateTestCaseExecutionDirective(TJob tJobWithTestCases, String uriResource, int port,
                                                     String resource, String tJobId, int stage) {
        List<TestCase> testCases = tJobWithTestCases.getListTestCases();
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(SH_COMMAND).append("$SCRIPTS_FOLDER/tjoblifecycles/tjob-testexecution.sh");
        String url = ciConfiguration.getProperty("external-binded-port").isEmpty() ? ciConfiguration.getProperty("docker-frontend-name") :
                ciConfiguration.getProperty("external-frontend-url");
        strBuilder.append(" ").append(tJobId.toLowerCase(Locale.ROOT)).append(" ").append(stage).append(" ")
                .append(url).append(" ").append(frontendPort).append(" \"");
        boolean firstElement = true;
        String testClassName;
        for (TestCase testcase : testCases) {
            if (firstElement) {
                firstElement = false;
            } else {
                strBuilder.append(",");
            }
            testClassName = testcase.getTestClass().getSimpleName();
            strBuilder.append(testClassName).append("#").append(testcase.getName());
        }
        strBuilder.append("\"'\n");
        return strBuilder.toString();
    }

    @Override
    public String generateResourceDeploymentDirective(TJob tJobWithTestCases, boolean disposal, Map<String,
            String> configurations, int stage) {
        StringBuilder strBuilder = new StringBuilder();
        if (disposal) {
            strBuilder.append(SH_COMMAND).append("$SCRIPTS_FOLDER/tjoblifecycles/tjob-teardown.sh").append(" ")
                    .append(tJobWithTestCases.getIdTJob().toLowerCase(Locale.ROOT)).append(" ").append(stage).append("'\n");
        } else {
            strBuilder.append(SH_COMMAND).append("$SCRIPTS_FOLDER/tjoblifecycles/tjob-setup.sh").append(" ")
                    .append(tJobWithTestCases.getIdTJob().toLowerCase(Locale.ROOT)).append(" ").append(stage).append("'\n");
        }
        return strBuilder.toString();
    }

    @Override
    public String generatePipelineTJobDirective(TJob tJobWithTestCases, boolean isParallel, int stage) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        if (tJobWithTestCases.getListResourceClasses().isEmpty() && tJobWithTestCases.getListTestCases().isEmpty()) {
            stringBuilder.append("        echo 'Gateway Activity'\n");
        } else {
            Map<String, String> mapWithPlaceHoldersAndImages = new HashMap<>();
            StringBuilder resourceIDBuilder = new StringBuilder();
            for (Resource res : tJobWithTestCases.getListResourceClasses()) {
                resourceIDBuilder.append(res.getResourceID()).append(" ");
                String [] placeHolderWithImage=res.getDockerImage().split("[IMG:]");
                mapWithPlaceHoldersAndImages.put(placeHolderWithImage[0], placeHolderWithImage[1]);
            }
            this.tJobCounter++;

            stringBuilder.append("        stage('")
                    .append(tJobWithTestCases.getIdTJob())
                    .append(" IdResource: ")
                    .append(resourceIDBuilder)
                    .append("') {\n          steps {\n");

            Map<String, String> mapData = this.getMapWithInformation(mapWithPlaceHoldersAndImages, tJobWithTestCases);
            generateDockerComposeEnvironmentFiles(mapData, tJobWithTestCases);

            stringBuilder.append("            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {\n    ")
                    .append(generateResourceDeploymentDirective(tJobWithTestCases, false, mapData, stage)).append("    ")
                    .append(generateTestCaseExecutionDirective(
                            tJobWithTestCases,
                            String.valueOf(mapData.get("mainuri")),
                            Integer.parseInt(portsSUTs.get(tJobWithTestCases.getIdTJob().toLowerCase(Locale.ROOT))),
                            this.getContainedResourceID(tJobWithTestCases),
                            tJobWithTestCases.getIdTJob(),
                            stage))
                    .append("            }// EndExecutionStageError").append(tJobWithTestCases.getIdTJob()).append("\n  ")
                    .append(generateResourceDeploymentDirective(tJobWithTestCases, true, mapData, stage))
                    .append("          }// EndSteps").append(tJobWithTestCases.getIdTJob()).append("\n")
                    .append("        }// EndStage").append(tJobWithTestCases.getIdTJob());

        }
        return stringBuilder.toString();
    }

    @Override
    public String generatePipeline(Map<Integer, LinkedList<Activity>> listActivities) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        Integer maxStagesValue = Collections.max(listActivities.keySet());
        //Remove scripts directory to avoid
        if (Files.exists(Paths.get("retorchfiles/scripts"))) {
            FileUtils.deleteDirectory(new File("retorchfiles/scripts"));
        }
        ScriptGenerator scriptler = new ScriptGenerator();
        stringBuilder.append("pipeline {\n")
                .append("  agent {label '").append(this.ciConfiguration.getProperty("agentCIName")).append("'}\n")
                .append("  environment {\n")
                .append("    SELENOID_PRESENT = \"TRUE\"\n")
                .append("    SUT_LOCATION = \"").append(ciConfiguration.getProperty("sut-location")).append("\"\n")
                .append("    SCRIPTS_FOLDER = \"$WORKSPACE/retorchfiles/scripts\"\n")
                .append("  } // EndEnvironment\n")
                .append("  options {\n")
                .append("    disableConcurrentBuilds()\n")
                .append("  } // EndPipOptions\n")
                .append("  stages {\n");

        String steps = "        steps {\n";

        stringBuilder.append("    stage('Clean Workspace') {\n")
                .append(steps)
                .append("            cleanWs()\n")
                .append("        } // EndStepsCleanWS\n")
                .append("    } // EndStageCleanWS\n");

        stringBuilder.append("    stage('Clone Project') {\n")
                .append(steps)
                .append("            checkout scm\n")
                .append("        } // EndStepsCloneProject\n")
                .append("    } // EndStageCloneProject\n");

        stringBuilder.append("    stage('SETUP-Infrastructure') {\n")
                .append(steps)
                .append("            sh 'chmod +x -R $SCRIPTS_FOLDER'\n")
                .append("            sh '$SCRIPTS_FOLDER/coilifecycles/coi-setup.sh'\n")
                .append("        } // EndStepsSETUPINF\n")
                .append("    } // EndStageSETUPInf\n");

        for (int currentStage = 0; currentStage <= maxStagesValue; currentStage++) {
            LinkedList<Activity> currentActivities = listActivities.get(currentStage);
            stringBuilder.append("    stage('Stage ").append(currentStage).append("') {\n");
            stringBuilder.append("      failFast false\n      parallel {\n");
            for (Activity activity : currentActivities) {
                stringBuilder.append(this.generatePipelineTJobDirective(activity.getTJob(),
                                currentActivities.size() != 1, currentStage))
                        .append("\n");
            }
            stringBuilder.append("      } // End Parallel\n");
            stringBuilder.append("    } // End Stage\n");
        }

        generateInfrastructureTearDown(stringBuilder);
        stringBuilder.append("} // EndStagesPipeline\n")
                .append(this.generateDisposingDirective())
                .append("} // EndPipeline \n");

        scriptler.generateScriptsTJob();
        scriptler.generateScriptsCOI();

        return stringBuilder.toString();
    }

    private static void generateInfrastructureTearDown(StringBuilder stringBuilder) {
        stringBuilder.append("stage('TEARDOWN-Infrastructure') {\n")
                .append("      failFast false\n")
                .append("      steps {\n")
                .append("          sh '$SCRIPTS_FOLDER/coilifecycles/coi-teardown.sh'\n")
                .append("      } // EndStepsTearDownInf\n")
                .append("} // EndStageTearDown\n");
    }


    /**
     * Method that generates the disposing commands required to check that all the {@code Resource}s are released and the
     * storage is cleaned
     */
    private String generateDisposingDirective() {
        log.debug("Executing the disposing directive");
        return "post {\n"
                + "    always {\n"
                + "        archiveArtifacts artifacts: 'artifacts/*.csv', onlyIfSuccessful: true\n"
                + "        archiveArtifacts artifacts: 'target/testlogs/**/*.*', onlyIfSuccessful: false\n"
                + "        archiveArtifacts artifacts: 'target/containerlogs/**/*.*', onlyIfSuccessful: false\n"
                + "    }// EndAlways\n"
                + "} // EndPostActions\n";
    }

    /**
     * Support method that checks if in the pipeline is contained the {@code Resource} required by the {@code TJob}
     */
    private String getContainedResourceID(TJob tJobWithTestCases) {
        String containedResource = "generic";
        for (Resource resourceTJob : tJobWithTestCases.getListResourceClasses()) {
            if (this.listAllResources.containsKey(resourceTJob.getResourceID())) {
                containedResource = resourceTJob.getResourceID();
            }
        }
        return containedResource;
    }

    /**
     * Method that given the map with all the DockerImages and ports,creates a customized map with the necessary
     * information for the given {@code TJob}
     *
     * @param mapWithDockerImages Map with the placeholders and the images
     * @param tJobWithTestCases {@code TJob} to put into the
     */
    public Map<String, String> getMapWithInformation(Map<String, String> mapWithDockerImages, TJob tJobWithTestCases) {
        Map<String, String> mapData = new HashMap<>();

        mapData.put("tjobname", tJobWithTestCases.getIdTJob().toLowerCase(Locale.ROOT));
        mapData.put(TJOB_BASE_PATH, ciConfiguration.getProperty(TJOB_BASE_PATH));
        mapData.putAll(mapWithDockerImages);

        if (ciConfiguration.getProperty("external-binded-port").isEmpty()) {
            frontendPort = ciConfiguration.getProperty("docker-frontend-port");
            mapData.put("frontend_port", frontendPort);
        } else {
            mapData.put("frontend_port", String.valueOf(poolPorts));
            frontendPort = String.valueOf(poolPorts);
            poolPorts += 1;
        }
        portsSUTs.put(tJobWithTestCases.getIdTJob().toLowerCase(Locale.ROOT), String.valueOf(frontendPort));
        String[] listPortsId = new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"};
        for (String s : listPortsId) {
            mapData.put("retorchport" + s, String.valueOf(poolPorts));
            poolPorts += 1;
        }
        return mapData;
    }

    /**
     * Method that given a Map with the placeholders values, replace in the template them in order to create
     * the different environment (.env) docker-compose.yml files (one for each TJob)
     *
     * @param mapProperties Dictionary with the different placeholders values
     */
    public void generateDockerComposeEnvironmentFiles(Map<String, String> mapProperties, TJob tJobWithTestCases) throws IOException {
        String resourcePath = ENVIRONMENT_FOLDER_NAME + File.separator;
        Files.createDirectories(Paths.get(resourcePath));
        StringBuilder contentEnvFile = new StringBuilder();
        contentEnvFile.append("# Environment file for the TJob: ").append(tJobWithTestCases.getIdTJob().toLowerCase(Locale.ROOT));
        contentEnvFile.append("like ports, docker images or the tjobname itself.\n");
        try {
            for (Map.Entry<String, String> entry : mapProperties.entrySet()) {
                contentEnvFile.append(entry.getKey())
                        .append("=")
                        .append(entry.getValue())
                        .append("\n");
            }
            if (Files.exists(Paths.get("retorchfiles/customscriptscode/custom.env"))) {
                contentEnvFile.append(readFileContent("retorchfiles/customscriptscode/custom.env"));
            }
            Path envFilePath = Paths.get(resourcePath + tJobWithTestCases.getIdTJob().toLowerCase(Locale.ROOT) +
                    ".env");
            Files.write(envFilePath, Collections.singleton(contentEnvFile.toString()));
        } finally {
            if (contentEnvFile.length() == 0) {
                log.error("The Stream may not be opened");
            }

        }
    }

    private String readFileContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes);
    }

}