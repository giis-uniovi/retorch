package giis.retorch.profiling;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.profiling.profilegeneration.ProfileGenerator;
import giis.retorch.profiling.profilegeneration.ProfilePlotter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * The {@code IntProfilerComponentsTests} class contains the integration tests between the three components: the average
 * {@code  DatasetGenerator}, {@code ProfileGenerator} and the {@code ProfilePlotter}. The outputs are validated against
 * the serialized expected Plots, and a pdf with the debugging (if it fails) is generated
 */
public class IntProfilerComponentsTests {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String outBasePath = "target/test-outputs/profiler";
    private final String inBasePath = "src/test/java/giis/retorch/profiling/testdata/profilegen";
    private final String expOutBasePath = "src/test/resources/expected_out/profiles";
    private final String debugOutBasePath = "target/debug";
    private ProfilerDataGenerationUtils dataGenerationUtils;
    private ProfileGenerator generator;
    private ProfilePlotter plotter;
    private ProfilerTestUtils utils;
    private final String outputProfilePath = outBasePath + "/profiles/";

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        this.utils = new ProfilerTestUtils();
        this.dataGenerationUtils = new ProfilerDataGenerationUtils();
        this.generator = new ProfileGenerator();
        ProfilerTestUtils.ensureDirectoryExists(outBasePath, log);
        ProfilerTestUtils.ensureDirectoryExists(outBasePath + "/profiles", log);
        ProfilerTestUtils.ensureDirectoryExists(debugOutBasePath, log);
        log.info("****** Set-up for test: {} ended ******", testName.getMethodName());
    }

    @Test
    public void testIntegrationProfilePlotterVM() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = dataGenerationUtils.generateExecutionPlan();
        plan.setName("int"+plan.getName());
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_vm.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_vm.csv", outBasePath + "/profileIntegrationCOI_VM.csv", dataGenerationUtils.generateVMCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_VM.csv");

        plotter.generateTotalTJobUsageProfileCharts(outputProfilePath, plan.getName() ,"vm");

        assertTrue("The VM UsageProfile are not equal, check the debugging file located in: "+debugOutBasePath, utils.profileComparator(expOutBasePath + "/" + plan.getName() + "-vm-UsageProfile.serialized", plotter.getUsageProfile(), "vm"));

    }

    @Test
    public void testIntegrationProfilePlotterContainer() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = dataGenerationUtils.generateExecutionPlan();
        plan.setName("int"+plan.getName());
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_int_container.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_int_container.csv", outBasePath + "/profileIntegrationCOI_container.csv", dataGenerationUtils.generateContainersCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_container.csv");
        plotter.generateTotalTJobUsageProfileCharts(outBasePath + "/profiles", plan.getName() , "containers");

        assertTrue("The Containers UsageProfile are not equal, check the debugging file located in: "+debugOutBasePath, utils.profileComparator(expOutBasePath + "/" + plan.getName() + "-containers-UsageProfile.serialized", plotter.getUsageProfile(), "containers"));

    }

    @Test
    public void testIntegrationProfilePlotterServices() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = dataGenerationUtils.generateExecutionPlan();
        plan.setName("int"+plan.getName());
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_int_services.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_int_services.csv", outBasePath + "/profileIntegrationCOI_services.csv", dataGenerationUtils.generateBrowserServiceCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_services.csv");
        plotter.generateTotalTJobUsageProfileCharts(outBasePath + "/profiles", plan.getName() ,"services");
        assertTrue("The Containers UsageProfile are not equal, check the debugging file located in: "+debugOutBasePath, utils.profileComparator(expOutBasePath + "/" + plan.getName() + "-services-UsageProfile.serialized", plotter.getUsageProfile(),"services"));

    }
}