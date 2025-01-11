package giis.retorch.orchestration.generator;


import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.classifier.RetorchClassifier;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.System;
import giis.retorch.orchestration.model.TGroup;
import giis.retorch.orchestration.orchestrator.JenkinsOrchestrator;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.orchestration.scheduler.NoTGroupsInTheSchedulerException;
import giis.retorch.orchestration.scheduler.NotValidSystemException;
import giis.retorch.orchestration.scheduler.RetorchAggregator;
import giis.retorch.orchestration.scheduler.RetorchScheduler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
/**
 * The {@code OrchestrationGenerator} class provides several method that allow to generate and obtain the Execution
 * Plan and the Jenkinsfiles and scripting code, orchestrating.
 */
public class OrchestrationGenerator {

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
        try (FileOutputStream outputStream = new FileOutputStream(jenkinsFilePath)) {
            byte[] strToBytes = executionPipeline.getBytes();
            outputStream.write(strToBytes);
        }
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
     * This  method  with the  system name and root package name where tests are located provided as input, creates a {@code Classifier} and gets the {@code System}
     * from its package. With the {@code System} creates an {@code Aggregator}  who generate the {@code tGroup}s.
     * This {@code tGroup}s are given as input to the {@code Scheduler} who generates the {@code Activity} Graph.
     * Finally, creates a {@code JenkinsOrchestrator} with the {@code Activity} Graph and the system name
     *
     * @param systemName   String with the system name (must coincide with the SystemResources.json file).
     * @param rootPackageNameTests String with root package name where tests are located.
     */
    private static JenkinsOrchestrator getJenkinsOrchestrator(String rootPackageNameTests, String systemName) throws
            EmptyInputException, IOException, ClassNotFoundException, URISyntaxException, NotValidSystemException,
            NoTGroupsInTheSchedulerException, NoFinalActivitiesException {
        RetorchClassifier classifier = new RetorchClassifier();
        System currentSystem = classifier.getSystemFromPackage(systemName, rootPackageNameTests);
        RetorchAggregator aggregator = new RetorchAggregator(currentSystem);
        List<TGroup> tGroupsOutputAggregator = aggregator.generateTGroups();
        RetorchScheduler scheduler = new RetorchScheduler(tGroupsOutputAggregator);
        List<Activity> listActivities = scheduler.generateActivities();
        return new JenkinsOrchestrator(listActivities, systemName);
    }
}