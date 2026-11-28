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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The {@code JenkinsfileRenderer} class derives the Jenkinsfile text from an {@code ExecutionPlan}. The pipeline is
 * written once in the {@code generictemplates/Jenkinsfile*.tmpl} templates (skeleton, stage, TJob...) and filled with
 * the same placeholder engine as the scripts. It is a pure function of its arguments: it only reads the templates from
 * the classpath and never touches the file system (see {@link OrchestrationWriter} for the side effects).
 */
public class JenkinsfileRenderer implements IRetorchOrchestrator {

    static final String TJOB_BASE_PATH = "testsBasePath";
    static final String APP_URL_PROPERTY = "app-url";
    private static final String TEMPLATES = "generictemplates/";

    private final String customJenkinsPostSteps;

    /** Renderer without custom steps in the {@code post { always { } }} block. */
    public JenkinsfileRenderer() {
        this("");
    }

    /**
     * @param customJenkinsPostSteps Jenkins steps inserted verbatim (not indented) in {@code post { always { } }}
     *                               (empty for none)
     */
    public JenkinsfileRenderer(String customJenkinsPostSteps) {
        this.customJenkinsPostSteps = customJenkinsPostSteps;
    }

    @Override
    public String render(ExecutionPlan plan, RetorchConfig config) {
        Map<String, String> values = new HashMap<>();
        values.put("${AGENT_LABEL}", config.agentCIName());
        values.put("${SUT_LOCATION}", config.sutLocation());
        values.put("${STAGES}", renderStages(plan, config));
        values.put("${PROFILING_USAGE_PROFILE_STAGE}", config.profiling() ? template("Jenkinsfile-usageprofile.tmpl") : "");
        values.put("${PROFILING_PUBLISH_STEPS}", config.profiling() ? template("Jenkinsfile-publish.tmpl") : "");
        values.put("${OPTIONAL_CUSTOM_POST_STEPS}", stripLastLineBreak(customJenkinsPostSteps));
        return TemplateEngine.render(template("Jenkinsfile.tmpl"), values) + "\n";
    }

    private String renderStages(ExecutionPlan plan, RetorchConfig config) {
        StringBuilder stages = new StringBuilder();
        for (Map.Entry<Integer, LinkedList<Activity>> stage : new TreeMap<>(plan.getSortedActivities()).entrySet()) {
            StringBuilder tJobs = new StringBuilder();
            for (Activity activity : stage.getValue()) {
                if (tJobs.length() > 0) {
                    tJobs.append('\n');
                }
                tJobs.append(renderTJob(activity.getTJob(), stage.getKey(), config));
            }
            Map<String, String> values = new HashMap<>();
            values.put("${STAGE}", String.valueOf(stage.getKey()));
            values.put("${TJOBS}", tJobs.toString());
            if (stages.length() > 0) {
                stages.append('\n');
            }
            stages.append(TemplateEngine.render(template("Jenkinsfile-stage.tmpl"), values));
        }
        return stages.toString();
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
        if (isGateway(tJob)) {
            return template("Jenkinsfile-gateway.tmpl");
        }
        String id = idOf(tJob);
        StringBuilder resourceIds = new StringBuilder();
        for (Resource res : tJob.getListResourceClasses()) {
            resourceIds.append(res.getResourceID()).append(" ");
        }
        StringBuilder tests = new StringBuilder();
        for (TestCase testcase : tJob.getListTestCases()) {
            if (tests.length() > 0) {
                tests.append(",");
            }
            tests.append(testcase.getTestClass().getSimpleName()).append("#").append(testcase.getName());
        }
        Map<String, String> values = new HashMap<>();
        values.put("${TJOB}", id);
        values.put("${STAGE}", String.valueOf(stage));
        values.put("${RESOURCES}", resourceIds.toString());
        values.put("${APP_URL}", config.appUrl(id));
        values.put("${TESTS}", tests.toString());
        return TemplateEngine.render(template("Jenkinsfile-tjob.tmpl"), values);
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

    private static String template(String name) {
        return TemplateEngine.load(TEMPLATES + name, name.startsWith("Jenkinsfile"));
    }

    private static String stripLastLineBreak(String text) {
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }
}
