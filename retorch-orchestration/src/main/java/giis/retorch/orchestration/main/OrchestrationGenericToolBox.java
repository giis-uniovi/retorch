package giis.retorch.orchestration.main;


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

public class OrchestrationGenericToolBox {

    /**
     * Main method of RETORCH initial prototype. This method given a package route with the test cases and the system
     * name
     * creates a classifier and gets the system from its package. With this system creates an aggregator who generate
     * the
     * tGroups. This tGroups are given as input to the Scheduler who generates the activity Graph. Finally, with the
     * Graph
     * and the orchestrator, the JenkinsFile is generated with the execution directives in the root directory of the
     * project.
     * @param systemName   String with the system name (must coincide with the resource-info.json file )
     * @param packageRoute String with the package route of the test cases
     */
    public String generateJenkinsfile(String packageRoute, String systemName,String jenkinsFilePath) throws NotValidSystemException,
            IOException, NoTGroupsInTheSchedulerException, EmptyInputException, NoFinalActivitiesException,
            ClassNotFoundException, URISyntaxException {
        JenkinsOrchestrator orchestrator = getGenericOrchestrator(packageRoute, systemName);
        //This maps represents the activities with a current temporal sequence, <Time,Group of activities to deploy>
        //In this variable is stored the Jenkinsfile in order to save it into a file.
        String executionPipeline = orchestrator.generatePipeline(orchestrator.getExecutionPlan().getSortedActivities());
        if (!new File(jenkinsFilePath).exists()) {
            jenkinsFilePath = "./Jenkinsfile";
        }
        else{
            jenkinsFilePath+="Jenkinsfile";
        }
        try (FileOutputStream outputStream = new FileOutputStream(jenkinsFilePath)) {
            byte[] strToBytes = executionPipeline.getBytes();
            outputStream.write(strToBytes);
        }
        return executionPipeline;
    }
    public ExecutionPlan getExecutionPlan(String packageRoute, String systemName) throws NotValidSystemException,
            IOException, NoTGroupsInTheSchedulerException, EmptyInputException, NoFinalActivitiesException,
            ClassNotFoundException, URISyntaxException {
        JenkinsOrchestrator orchestrator = getGenericOrchestrator(packageRoute, systemName);
        //This maps represents the activities with a current temporal sequence, <Time,Group of activities to deploy>
        //In this variable is stored the Jenkinsfile in order to save it into a file.
       return orchestrator.getExecutionPlan();
    }

    private static JenkinsOrchestrator getGenericOrchestrator(String packageRoute, String systemName) throws EmptyInputException, IOException, ClassNotFoundException, URISyntaxException, NotValidSystemException, NoTGroupsInTheSchedulerException, NoFinalActivitiesException {
        RetorchClassifier classifier = new RetorchClassifier();
        System currentSystem = classifier.getSystemFromPackage(systemName, packageRoute);
        RetorchAggregator aggregator = new RetorchAggregator(currentSystem);
        List<TGroup> tGroupsOutputAggregator = aggregator.generateTGroups();
        RetorchScheduler scheduler = new RetorchScheduler(tGroupsOutputAggregator);
        List<Activity> listActivities = scheduler.generateActivities();
        return new JenkinsOrchestrator(listActivities,systemName);
    }
}