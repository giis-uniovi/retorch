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

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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
    private final String outputProfilePath = outBasePath + "/profiles";

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        log.debug("Creating the tmp directory to store methods output");
        this.utils=new ProfilerTestUtils();
        this.dataGenerationUtils = new ProfilerDataGenerationUtils();
        this.generator = new ProfileGenerator();
        List<File> listFiles = new LinkedList<>();
        listFiles.add(new File(outBasePath));
        listFiles.add(new File(outBasePath + "/profiles"));
        listFiles.add(new File(debugOutBasePath));
        for (File dir : listFiles) {
            if (dir.exists()) {
                log.debug("The directory already exists: {}", dir.getAbsolutePath());
            } else {
                try {
                    boolean isCreated = dir.mkdirs();
                    if (isCreated) {
                        log.debug("Directory successfully created: {}", dir.getAbsolutePath());
                    } else {
                        log.warn("Not able to create the directory: {}", dir.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.error("Something wrong happened creating the directory {} the exception stacktrace is:\n {}", dir.getAbsolutePath(), e.getStackTrace());
                    fail("The directory" + dir.getAbsolutePath() + "cannot be created");
                }
            }
        }
        log.info("****** Set-up for test: {} ended ******", testName.getMethodName());
    }

    @Test
    public void testIntegrationProfilePlotterVM() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = dataGenerationUtils.generateExecutionPlan();

        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_vm.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_vm.csv", outBasePath + "/profileIntegrationCOI_VM.csv", dataGenerationUtils.generateVMCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_VM.csv");

        plotter.generateTotalTJobUsageProfileCharts(outputProfilePath, plan.getName() + "-vm");
        assertTrue("The VM memory profiles are not equal", utils.profileComparator(outputProfilePath + "/" + plan.getName() + "-vm-memory.png", expOutBasePath + "/" + plan.getName() + "-vm-memory.png", debugOutBasePath));
        assertTrue("The VM processor profiles are not equal", utils.profileComparator(outputProfilePath + "/" + plan.getName() + "-vm-processor.png", expOutBasePath + "/" + plan.getName() + "-vm-processor.png", debugOutBasePath));
        assertTrue("The VM storage profiles are not equal", utils.profileComparator(outputProfilePath + "/" + plan.getName() + "-vm-storage.png", expOutBasePath + "/" + plan.getName() + "-vm-storage.png", debugOutBasePath));
    }

    @Test
    public void testIntegrationProfilePlotterContainer() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = dataGenerationUtils.generateExecutionPlan();
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_int_container.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_int_container.csv", outBasePath + "/profileIntegrationCOI_container.csv", dataGenerationUtils.generateContainersCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_container.csv");
        plotter.generateTotalTJobUsageProfileCharts(outBasePath + "/profiles", plan.getName() + "-containers");

        assertTrue("The Containers memory profiles are not equal", utils.profileComparator(outputProfilePath + "/" + plan.getName() + "-containers-memory.png", expOutBasePath + "/" + plan.getName() + "-containers-memory.png", debugOutBasePath));
        assertTrue("The Containers processor profiles are not equal", utils.profileComparator(outputProfilePath + "/" + plan.getName() + "-containers-processor.png", expOutBasePath + "/" + plan.getName() + "-containers-processor.png", debugOutBasePath));
        assertTrue("The Containers storage profiles are not equal", utils.profileComparator(outputProfilePath + "/" + plan.getName() + "-containers-storage.png", expOutBasePath + "/" + plan.getName() + "-containers-storage.png", debugOutBasePath));

    }

    @Test
    public void testIntegrationProfilePlotterServices() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = dataGenerationUtils.generateExecutionPlan();
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_int_services.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_int_services.csv", outBasePath + "/profileIntegrationCOI_services.csv", dataGenerationUtils.generateBrowserServiceCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_services.csv");
        plotter.generateTotalTJobUsageProfileCharts(outBasePath + "/profiles", plan.getName() + "-services");
        assertTrue("The Services slots profiles are not equal", utils.profileComparator(outputProfilePath + "/" + plan.getName() + "-services-slots.png", expOutBasePath + "/" + plan.getName() + "-services-slots.png", debugOutBasePath));
    }


}