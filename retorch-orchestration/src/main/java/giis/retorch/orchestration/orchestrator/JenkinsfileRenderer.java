/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.classifier.ResourceSerializer;
import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.TestCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code JenkinsfileRenderer} class derives the Jenkinsfile text from an {@code ExecutionPlan}. It is a pure
 * function of its arguments: it never touches the file system (see {@link OrchestrationWriter} for the side effects).
 */
public class JenkinsfileRenderer implements IRetorchOrchestrator {

    static final String TJOB_BASE_PATH = "testsBasePath";
    static final String APP_URL_PROPERTY = "app-url";
    private static final String SH_COMMAND = "          sh '";

    private final String customJenkinsPostSteps;

    /** Renderer without custom steps in the {@code post { always { } }} block. */
    public JenkinsfileRenderer() {
        this("");
    }

    /**
     * @param customJenkinsPostSteps Jenkins steps inserted verbatim in {@code post { always { } }}, each line ending
     *                               with a line feed (empty for none)
     */
    public JenkinsfileRenderer(String customJenkinsPostSteps) {
        this.customJenkinsPostSteps = customJenkinsPostSteps;
    }

    @Override
    public String render(ExecutionPlan plan, RetorchConfig config) {
        Map<Integer, LinkedList<Activity>> listActivities = plan.getSortedActivities();
        StringBuilder stringBuilder = new StringBuilder();
        Integer maxStagesValue = Collections.max(listActivities.keySet());
        stringBuilder.append("pipeline {\n")
                .append("  agent {label '").append(config.agentCIName()).append("'}\n")
                .append("  environment {\n")
                .append("    SELENOID_PRESENT = \"TRUE\"\n")
                .append("    SUT_LOCATION = \"").append(config.sutLocation()).append("\"\n")
                .append("    SCRIPTS_FOLDER = \"$WORKSPACE/.retorch/scripts\"\n")
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
                stringBuilder.append(this.renderTJob(activity.getTJob(), currentStage, config))
                        .append("\n");
            }
            stringBuilder.append("} // End Parallel\n");
            stringBuilder.append("} // End Stage\n");
        }

        generateInfrastructureTearDown(stringBuilder);
        if (config.profiling()) {
            generateUsageProfile(stringBuilder);
        }
        stringBuilder.append("} // EndStagesPipeline\n")
                .append(this.generateDisposingDirective(config.profiling()))
                .append("} // EndPipeline\n");
        return stringBuilder.toString();
    }

    /** The identifier of the TJob as it appears in the pipeline, the env files and the usage file: lower case. */
    public static String idOf(TJob tJob) {
        return tJob.getIdTJob().toLowerCase(Locale.ROOT);
    }

    /** A gateway activity joins the branches of the plan: it has neither resources nor test cases. */
    public static boolean isGateway(TJob tJob) {
        return tJob.getListResourceClasses().isEmpty() && tJob.getListTestCases().isEmpty();
    }

    /**
     * Whole directive of a {@code TJob}: deploy and wait for the {@code Resource}s, execute the {@code TestCase}s and
     * dispose the {@code Resource}s when the test execution is finished.
     */
    String renderTJob(TJob tJob, int stage, RetorchConfig config) {
        StringBuilder stringBuilder = new StringBuilder();
        if (isGateway(tJob)) {
            stringBuilder.append("        echo 'Gateway Activity'\n");
        } else {
            StringBuilder resourceIDBuilder = new StringBuilder();
            for (Resource res : tJob.getListResourceClasses()) {
                resourceIDBuilder.append(res.getResourceID()).append(" ");
            }
            String id = idOf(tJob);
            stringBuilder.append("        stage('")
                    .append(id)
                    .append(" IdResource: ")
                    .append(resourceIDBuilder)
                    .append("') {\n          steps {\n");

            String appUrl = config.appUrl(id);
            stringBuilder.append("            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {\n    ")
                    .append(generateResourceDeploymentDirective(tJob, false, appUrl, stage)).append("    ")
                    .append(generateTestCaseExecutionDirective(tJob, appUrl, stage))
                    .append("            }// EndExecutionStageError").append(id).append("\n  ")
                    .append(generateResourceDeploymentDirective(tJob, true, appUrl, stage))
                    .append("          }// EndSteps").append(id).append("\n")
                    .append("        }// EndStage").append(id);
        }
        return stringBuilder.toString();
    }

    /** The command that executes the test cases of the TJob. */
    String generateTestCaseExecutionDirective(TJob tJob, String appUrl, int stage) {
        StringBuilder strBuilder = new StringBuilder();
        String tJobId = idOf(tJob);
        strBuilder.append(SH_COMMAND).append("$SCRIPTS_FOLDER/tjoblifecycles/tjob-testexecution.sh");
        strBuilder.append(" ").append(tJobId).append(" ").append(stage).append(" ")
                .append(appUrl).append(" \"");

        boolean firstElement = true;
        for (TestCase testcase : tJob.getListTestCases()) {
            if (firstElement) {
                firstElement = false;
            } else {
                strBuilder.append(",");
            }
            strBuilder.append(testcase.getTestClass().getSimpleName()).append("#").append(testcase.getName());
        }
        strBuilder.append("\"'\n");
        return strBuilder.toString();
    }

    /** The command that sets up (or, with {@code disposal}, tears down) the resources of the TJob. */
    String generateResourceDeploymentDirective(TJob tJob, boolean disposal, String appUrl, int stage) {
        StringBuilder strBuilder = new StringBuilder();
        String tJobId = idOf(tJob);
        if (disposal) {
            strBuilder.append(SH_COMMAND).append("$SCRIPTS_FOLDER/tjoblifecycles/tjob-teardown.sh").append(" ")
                    .append(tJobId).append(" ").append(stage).append("'\n");
        } else {
            strBuilder.append(SH_COMMAND).append("$SCRIPTS_FOLDER/tjoblifecycles/tjob-setup.sh").append(" ")
                    .append(tJobId).append(" ").append(stage).append(" ").append(appUrl).append("'\n");
        }
        return strBuilder.toString();
    }

    /**
     * Placeholders and values of the environment ({@code .env}) file of a TJob: its name, the tests base path, the
     * docker image of every resource ({@code PLACEHOLDER;image}) and the application URL.
     */
    public static Map<String, String> environmentOf(TJob tJob, RetorchConfig config) {
        Map<String, String> placeHoldersAndImages = new HashMap<>();
        List<String> imageProblems = ResourceSerializer.dockerImageProblems(tJob.getListResourceClasses());
        if (!imageProblems.isEmpty()) {
            throw new IllegalStateException(imageProblems.get(0));
        }
        for (Resource res : tJob.getListResourceClasses()) {
            String[] placeHolderWithImage = res.getDockerImage().split(";");
            placeHoldersAndImages.put(placeHolderWithImage[0], placeHolderWithImage[1]);
        }
        String tJobNameLower = idOf(tJob);
        Map<String, String> mapData = new HashMap<>();
        mapData.put("tjob_name", tJobNameLower);
        mapData.put(TJOB_BASE_PATH, config.testsBasePath());
        mapData.putAll(placeHoldersAndImages);
        mapData.put(APP_URL_PROPERTY, config.appUrl(tJobNameLower));
        return mapData;
    }

    /**
     * Stage that builds the usage profile (usage and cost) from the measured resources. A failure here marks the stage as
     * unstable but never fails the build.
     */
    private static void generateUsageProfile(StringBuilder stringBuilder) {
        stringBuilder.append("    stage('Usage-Profile') {\n")
                .append("      steps {\n")
                .append("          catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {\n")
                .append("              sh '$SCRIPTS_FOLDER/generateusageprofile.sh'\n")
                .append("          }\n")
                .append("      } // EndStepsUsageProfile\n")
                .append("    } // EndStageUsageProfile\n");
    }

    private static void generateInfrastructureTearDown(StringBuilder stringBuilder) {
        stringBuilder.append("    stage('TEARDOWN-Infrastructure') {\n")
                .append("      steps {\n")
                .append("          sh '$SCRIPTS_FOLDER/coilifecycles/coi-teardown.sh'\n")
                .append("      } // EndStepsTearDownInf\n")
                .append("    } // EndStageTearDown\n");
    }

    /**
     * The {@code post} block: archives the results and checks that all the {@code Resource}s are released
     */
    private String generateDisposingDirective(boolean profiling) {
        String usageProfilePublication = profiling
                ? "        archiveArtifacts artifacts: 'artifacts/usageprofile/*.html', allowEmptyArchive: true\n"
                + "        publishHTML(target: [reportDir: 'artifacts/usageprofile', reportFiles: 'usageprofile.html', "
                + "reportName: 'RETORCH Usage Profile', keepAll: true, alwaysLinkToLastBuild: true, "
                + "allowMissing: true])\n"
                : "";
        return "post {\n"
                + "    always {\n"
                + "        archiveArtifacts artifacts: 'artifacts/*.csv', onlyIfSuccessful: true\n"
                + usageProfilePublication
                + customJenkinsPostSteps
                + "        archiveArtifacts artifacts: 'target/testlogs/**/*.*', onlyIfSuccessful: false\n"
                + "        archiveArtifacts artifacts: 'target/containerlogs/**/*.*', onlyIfSuccessful: false\n"
                + "    }// EndAlways\n"
                + "} // EndPostActions\n";
    }
}
