/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.generator;


import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.classifier.ResourceSerializer;
import giis.retorch.orchestration.classifier.RetorchClassifier;
import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.config.RetorchPaths;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.SystemUnderTest;
import giis.retorch.orchestration.model.TGroup;
import giis.retorch.orchestration.orchestrator.JenkinsOrchestrator;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.orchestration.scheduler.NoTGroupsInTheSchedulerException;
import giis.retorch.orchestration.scheduler.NotValidSystemException;
import giis.retorch.orchestration.scheduler.RetorchAggregator;
import giis.retorch.orchestration.scheduler.RetorchScheduler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
/**
 * The {@code OrchestrationGenerator} class provides several method that allow to generate and obtain the Execution
 * Plan and the Jenkinsfiles and scripting code, orchestrating.
 */
public class OrchestrationGenerator {

    private final RetorchPaths paths;
    private Boolean profilingOverride;

    /** Generator that reads and writes under the working directory. */
    public OrchestrationGenerator() {
        this(new RetorchPaths());
    }

    public OrchestrationGenerator(RetorchPaths paths) {
        this.paths = paths;
    }

    /**
     * Forces profiling on or off for the next generations, overriding {@code retorchCI.properties}.
     * @param profiling {@code null} (default) keeps the configured value
     */
    public OrchestrationGenerator withProfiling(Boolean profiling) {
        this.profilingOverride = profiling;
        return this;
    }

    /**
     * Main method of RETORCH  prototype. This method given root package name where tests are located and the system name,
     * Creates a {@code JenkinsOrchestrator}. The {@code JenkinsOrchestrator}. The {@code JenkinsOrchestrator}
     * generates the JenkinsFile and Scripting code in the  directory of the project specified
     *
     * @param systemName      String with the system name (must coincide with the SystemResources.json file)
     * @param rootPackageNameTests    String with root package name where tests are located
     * @param jenkinsFilePath String with the route where the Jenkinsfile will be placed
     */
    public String generateJenkinsfile(String rootPackageNameTests, String systemName, String jenkinsFilePath) throws NotValidSystemException,
            IOException, NoTGroupsInTheSchedulerException, EmptyInputException, NoFinalActivitiesException,
            ClassNotFoundException, URISyntaxException {
        JenkinsOrchestrator orchestrator = getJenkinsOrchestrator(rootPackageNameTests, systemName);
        String executionPipeline = orchestrator.generatePipeline(orchestrator.getExecutionPlan().getSortedActivities());
        if (!new File(jenkinsFilePath).exists()) {
            jenkinsFilePath = "./Jenkinsfile";
        } else {
            jenkinsFilePath += "Jenkinsfile";
        }
        Files.write(Paths.get(jenkinsFilePath), executionPipeline.getBytes(StandardCharsets.UTF_8));
        return executionPipeline;
    }

    /**
     * This method creates a {@code JenkinsOrchestrator} that returns the {@code ExecutionPlan}. The {@code ExecutionPlan}
     * can be used for debugging purposes or generate the usage profiles/estimate the different costs.
     *
     * @param systemName   String with the system name (must coincide with the SystemResources.json file )
     * @param rootPackageNameTests String with root package name where tests are located
     * @return The RETORCH Execution plan
     */
    public ExecutionPlan getExecutionPlan(String rootPackageNameTests, String systemName) throws NotValidSystemException,
            IOException, NoTGroupsInTheSchedulerException, EmptyInputException, NoFinalActivitiesException,
            ClassNotFoundException, URISyntaxException {
        JenkinsOrchestrator orchestrator = getJenkinsOrchestrator(rootPackageNameTests, systemName);
        return orchestrator.getExecutionPlan();
    }

    /**
     * This  method  with the  system name and root package name where tests are located provided as input, creates a {@code Classifier} and gets the {@code SystemUnderTest}
     * from its package. With the {@code SystemUnderTest} creates an {@code Aggregator}  who generate the {@code tGroup}s.
     * This {@code tGroup}s are given as input to the {@code Scheduler} who generates the {@code Activity} Graph.
     * Finally, creates a {@code JenkinsOrchestrator} with the {@code Activity} Graph and the system name
     *
     * @param systemName   String with the system name (must coincide with the SystemResources.json file).
     * @param rootPackageNameTests String with root package name where tests are located.
     */
    private JenkinsOrchestrator getJenkinsOrchestrator(String rootPackageNameTests, String systemName) throws
            EmptyInputException, IOException, ClassNotFoundException, URISyntaxException, NotValidSystemException,
            NoTGroupsInTheSchedulerException, NoFinalActivitiesException {
        RetorchConfig config = RetorchConfig.load(paths).withProfiling(profilingOverride);
        // the resources JSON is read once and shared by the three phases
        Map<String, Resource> resources = new ResourceSerializer(paths).deserializeResources(systemName);
        RetorchClassifier classifier = new RetorchClassifier(resources);
        SystemUnderTest currentSystem = classifier.getSystemFromPackage(systemName, rootPackageNameTests);
        RetorchAggregator aggregator = new RetorchAggregator(currentSystem, resources);
        List<TGroup> tGroupsOutputAggregator = aggregator.generateTGroups();
        RetorchScheduler scheduler = new RetorchScheduler(tGroupsOutputAggregator);
        List<Activity> listActivities = scheduler.generateActivities();
        return new JenkinsOrchestrator(listActivities, resources, config, paths);
    }
}