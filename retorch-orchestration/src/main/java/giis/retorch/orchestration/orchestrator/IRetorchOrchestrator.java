package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.TJob;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

/**
 * The {@code RetorchOrchestrator}  interface provides the blueprint with the methods that other Orchestrators must implement
 * to generate the Pipelining code necessary to deploy the {@code ExecutionPlan} in a certain Continuous Integration System.
 * e.g. Jenkins, GitHub Actions, Gitlab CI/CD.
 */
public interface IRetorchOrchestrator {

    ExecutionPlan getExecutionPlan();
    void setExecutionPlan(ExecutionPlan plan);

    /**
     * Generates the String with the test execution directive to be included in the corresponding stage in the pipelining
     * code of the workflow
     *
     * @param tJobWithTestCases {@code TJob} to be executed
     * @param port              Integer with the SUT port
     * @param uriResource       String with the SUT URI
     * @param resource          String with the {@code Resource} identifier
     * @param tJobId            String with the {@code TJob} identifier
     */
    String generateTestCaseExecutionDirective(TJob tJobWithTestCases, String uriResource, int port,
                                              String resource, String tJobId, int stage);

    /**
     * Given a TJob and a list of configurations create the set-up and disposal commands to be included into the
     * pipelining code of the workflow
     *
     * @param tJobWithTestCases {@code TJob} that contains the {@code Resource}s to be deployed
     * @param configurations    Map with the different parameters  as i.e. the base path, ports, URIs or the {@code TJob} name
     * @param disposal          boolean that is used to create a set-up or dispose directive
     */
    String generateResourceDeploymentDirective(TJob tJobWithTestCases, boolean disposal,
                                               Map<String, String> configurations, int stage);

    /**
     * Given a {@code TJob}, this method create the whole directive required to deploy and wait for the {@code Resource}, execute the
     * {@code TestCase}s and dispose the {@code Resource} when the  test execution its finished
     *
     * @param tJobWithTestCases {@code TJob} with the {@code Resource}s and the {@code TestCase}s to execute
     * @param isParallel        Boolean that creates a Parallel step or a sequential execution depending on the
     *                          intra-Schedule
     */
    String generatePipelineTJobDirective(TJob tJobWithTestCases, boolean isParallel, int stage) throws IOException;

    /**
     * Method that generate the whole pipeline, using the  {@code generatePipelineTJobDirective},
     * {@code generateResourceDeploymentDirective} and { @code generateTestCaseExecutionDirective}
     * given a sequence of {@code Activity} ordered.
     *
     * @param listActivities List with the {@code Activity} who represent the sequence of {@code TJob} executed
     */
    String generatePipeline(Map<Integer, LinkedList<Activity>> listActivities) throws IOException;

}