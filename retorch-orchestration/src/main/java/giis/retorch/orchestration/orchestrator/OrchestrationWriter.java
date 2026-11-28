/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.config.CustomHook;
import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.config.RetorchPaths;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.TJob;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Edge of the orchestration: renders the pipeline ({@link JenkinsfileRenderer}, no side effects) and writes
 * everything the pipeline needs under {@code .retorch}: it cleans the scripts of a previous generation, writes the TJob
 * environment files, the scripts and, with profiling, the execution plan.
 */
public class OrchestrationWriter {

    private final RetorchPaths paths;

    public OrchestrationWriter(RetorchPaths paths) {
        this.paths = paths;
    }

    /**
     * @return the text of the Jenkinsfile (the caller decides where to write it)
     */
    public String generate(ExecutionPlan plan, RetorchConfig config) throws IOException {
        Map<Integer, LinkedList<Activity>> activities = plan.getSortedActivities();
        String pipeline = new JenkinsfileRenderer(readCustomJenkinsPostSteps()).render(plan, config);

        if (Files.exists(paths.scriptsDir())) {
            FileUtils.deleteDirectory(paths.scriptsDir().toFile());
        }
        Files.deleteIfExists(paths.executionPlanFile()); // never keep the plan of a previous generation
        EnvFileWriter envFileWriter = new EnvFileWriter(paths);
        String firstTJobName = null;
        for (LinkedList<Activity> stage : new TreeMap<>(activities).values()) {
            for (Activity activity : stage) {
                TJob tJob = activity.getTJob();
                if (!JenkinsfileRenderer.isGateway(tJob)) {
                    envFileWriter.write(JenkinsfileRenderer.environmentOf(tJob, config), tJob);
                    if (firstTJobName == null) {
                        firstTJobName = JenkinsfileRenderer.idOf(tJob);
                    }
                }
            }
        }
        if (firstTJobName == null) {
            throw new IllegalArgumentException("The plan has no TJob with test cases");
        }
        ScriptGenerator scriptGenerator = new ScriptGenerator(config, paths);
        scriptGenerator.generateScriptsTJob();
        scriptGenerator.generateScriptsCOI(firstTJobName);
        if (config.profiling()) {
            new ExecutionPlanWriter(paths).write(activities);
        }
        return pipeline;
    }

    /**
     * Steps provided by the user in {@code .retorch/customscriptscode/custom-jenkins-post}, inserted verbatim in the
     * {@code post { always { } }} block (e.g. to publish a coverage report); empty if the file does not exist.
     */
    private String readCustomJenkinsPostSteps() throws IOException {
        Path path = paths.customFile(CustomHook.JENKINS_POST);
        if (!Files.exists(path)) {
            return "";
        }
        String content = EnvFileWriter.readUnixText(path);
        return content.isEmpty() || content.endsWith("\n") ? content : content + "\n";
    }
}
