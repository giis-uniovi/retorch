package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.TJob;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

/**
 * Common interface for the different orchestrator of the tool. The implementation is prepared to include a new
 * orchestrator
 * as i.e. one ElasTest Orchestration Toolbox Orchestrator that also uses Jenkins to optimize the test case execution
 * using
 * the platform.
 */
public interface RetorchOrchestrator {

    /**
     * Getter of the Orchestrator execution plan that are passed as parameter into the constructor or set in the method
     */
    ExecutionPlan getExecutionPlan();

    /**
     * Setter of the Orchestrator activities list that are used to generate the Jenkins pipelined
     * @param plan execution plan to reemplace
     */
    void setExecutionPlan(ExecutionPlan plan);

    /**
     * This method generates the execution directive for a given TJob.
     * @param tJobWithTestCases TJob to be executed
     * @param port              Integer with the SUT port
     * @param uriResource       String with the SUT URI
     * @param resource          String with the resource identifier
     * @param tJobId            String with the TJobIdentifier
     */
    String generateTestCaseExecutionDirective(TJob tJobWithTestCases, String uriResource, int port,
                                              String resource, String tJobId, int stage);

    /**
     * Given a TJob and a list of configurations create the set-up and dispose commands depending on a boolean value
     * @param tJobWithTestCases TJob that contains the resources to be deployed
     * @param configurations    Map with the different parameters  as i.e. the base path or the TJob name
     * @param disposal          boolean if its true create the disposal command and the set-up in the opposite case
     */
    String generateResourceDeploymentDirective(TJob tJobWithTestCases, boolean disposal,
                                               Map<String, String> configurations, int stage);

    /**
     * Given a TJob, this method create the whole directive required to deploy and wait for the resource, execute the
     * test cases and dispose the resource when the execution its finished
     * @param tJobWithTestCases TJob with the resources and the test cases to execute
     * @param isParallel        Boolean that creates a Parallel step or a sequential execution depending on the
     *                          intra-Schedule
     */
    String generatePipelineTJobDirective(TJob tJobWithTestCases, boolean isParallel, int stage) throws IOException;

    /**
     * Method that generate the whole Pipeline code given a sequence of activities ordered.
     * @param listActivities List with the activities who represent the sequence of test case execution
     */
    String generatePipeline(Map<Integer, LinkedList<Activity>> listActivities) throws IOException;

}