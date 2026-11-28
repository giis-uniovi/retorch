package giis.retorch.orchestration.generator;


import giis.retorch.orchestration.classifier.ResourceSerializer;
import giis.retorch.orchestration.classifier.RetorchClassifier;
import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.config.RetorchPaths;
import giis.retorch.orchestration.exception.InvalidConfigurationException;
import giis.retorch.orchestration.exception.InvalidSystemException;
import giis.retorch.orchestration.exception.RetorchException;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.SystemUnderTest;
import giis.retorch.orchestration.model.TGroup;
import giis.retorch.orchestration.orchestrator.OrchestrationWriter;
import giis.retorch.orchestration.scheduler.RetorchAggregator;
import giis.retorch.orchestration.scheduler.RetorchScheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
/**
 * The {@code OrchestrationGenerator} class provides several method that allow to generate and obtain the Execution
 * Plan and the Jenkinsfiles and scripting code, orchestrating.
 */
public class OrchestrationGenerator {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationGenerator.class);

    private final RetorchPaths paths;
    private Boolean profilingOverride;
    private boolean lenient;

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
     * Lenient mode keeps the previous behavior for invalid input: tests with wrong annotations, systems that fail the
     * validation and TJobs that cannot be placed are logged and left out of the pipeline instead of failing the
     * generation. Strict (the default) reports all the problems in a {@link RetorchException}.
     */
    public OrchestrationGenerator withLenient(boolean lenientMode) {
        this.lenient = lenientMode;
        return this;
    }

    /**
     * Main method of RETORCH prototype. Given the root package name where tests are located and the system name,
     * classifies, aggregates and schedules the tests, and writes the Jenkinsfile and the scripting code in the
     * directory of the project specified
     *
     * @param systemName      String with the system name (must coincide with the SystemResources.json file)
     * @param rootPackageNameTests    String with root package name where tests are located
     * @param jenkinsFilePath String with the route where the Jenkinsfile will be placed
     */
    public String generateJenkinsfile(String rootPackageNameTests, String systemName, String jenkinsFilePath)
            throws RetorchException, IOException {
        RetorchConfig config = RetorchConfig.load(paths).withProfiling(profilingOverride);
        ExecutionPlan plan = buildExecutionPlan(rootPackageNameTests, systemName);
        String executionPipeline = new OrchestrationWriter(paths).generate(plan, config);
        Path jenkinsfile = Paths.get(jenkinsFilePath).resolve(RetorchPaths.JENKINSFILE);
        if (!Files.isDirectory(Paths.get(jenkinsFilePath))) {
            log.warn("The directory {} does not exist: the Jenkinsfile is written to ./{}", jenkinsFilePath,
                    RetorchPaths.JENKINSFILE);
            jenkinsfile = Paths.get(RetorchPaths.JENKINSFILE);
        }
        Files.write(jenkinsfile, executionPipeline.getBytes(StandardCharsets.UTF_8));
        return executionPipeline;
    }

    /**
     * Returns the {@code ExecutionPlan} without writing anything. It can be used for debugging purposes or generate the
     * usage profiles/estimate the different costs.
     *
     * @param systemName   String with the system name (must coincide with the SystemResources.json file )
     * @param rootPackageNameTests String with root package name where tests are located
     * @return The RETORCH Execution plan
     */
    public ExecutionPlan getExecutionPlan(String rootPackageNameTests, String systemName)
            throws RetorchException, IOException {
        return buildExecutionPlan(rootPackageNameTests, systemName);
    }

    /**
     * With the system name and root package name where tests are located, creates a {@code Classifier} and gets the
     * {@code SystemUnderTest} from its package. With it creates an {@code Aggregator} who generates the {@code tGroup}s,
     * that the {@code Scheduler} turns into the {@code Activity} graph of the {@code ExecutionPlan}.
     *
     * @param systemName   String with the system name (must coincide with the SystemResources.json file).
     * @param rootPackageNameTests String with root package name where tests are located.
     */
    private ExecutionPlan buildExecutionPlan(String rootPackageNameTests, String systemName)
            throws RetorchException, IOException {
        // the resources JSON is read once and shared by the classifier and the aggregator
        Map<String, Resource> resources = new ResourceSerializer(paths).deserializeResources(systemName);
        SystemUnderTest currentSystem;
        try {
            currentSystem = new RetorchClassifier(resources).lenient(lenient)
                    .getSystemFromPackage(systemName, rootPackageNameTests);
        } catch (ClassNotFoundException | URISyntaxException e) {
            throw new InvalidSystemException("The tests of the package " + rootPackageNameTests
                    + " cannot be loaded: " + e);
        }
        List<String> imageProblems = ResourceSerializer.dockerImageProblems(currentSystem.getResources());
        if (!imageProblems.isEmpty()) {
            throw new InvalidConfigurationException("Invalid resources of the system " + systemName, imageProblems);
        }
        RetorchAggregator aggregator = new RetorchAggregator(currentSystem, resources, lenient);
        List<TGroup> tGroupsOutputAggregator = aggregator.generateTGroups();
        RetorchScheduler scheduler = new RetorchScheduler(tGroupsOutputAggregator, lenient);
        List<Activity> listActivities = scheduler.generateActivities();
        return ExecutionPlan.fromActivities("defaultName", listActivities);
    }
}
