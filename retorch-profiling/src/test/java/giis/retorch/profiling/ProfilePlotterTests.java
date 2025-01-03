package giis.retorch.profiling;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.profiling.profilegeneration.ProfilePlotter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

/**
 * The {@code ProfileGeneratorTests} class contains the unitary tests of the generation of the UsageProfile plots
 */
public class ProfilePlotterTests {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String outBasePath = "target/test-outputs/profiler/profiles";
    private final String inBasePath = "src/test/java/giis/retorch/profiling/testdata/profilegen";
    private final String expOutBasePath = "src/test/resources/expected_out/profiles";
    private final String debugOutBasePath = "target/debug";

    private ProfilerDataGenerationUtils dataGenerationUtils;
    private ProfilerTestUtils utils;
    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        log.debug("Creating the tmp directory to store methods output");
        File dir = new File(outBasePath);
        this.dataGenerationUtils = new ProfilerDataGenerationUtils();
        this.utils = new ProfilerTestUtils();
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
                log.error("Something wrong happened creating the directory {} the exception stacktrace is:\n {}",
                        dir.getAbsolutePath(), e.getStackTrace());
                fail("The directory" + dir.getAbsolutePath() + "cannot be created");
            }
        }
        log.info("****** Set-up for test: {} ended ******", testName.getMethodName());
    }

    @Test
    public void testProfilePlotter() throws NoFinalActivitiesException, EmptyInputException, IOException {
        ExecutionPlan plan = dataGenerationUtils.generateExecutionPlan();
        ProfilePlotter plotter = new ProfilePlotter(inBasePath + "/imp_usageprofileplotter.csv");
        dataGenerationUtils.generateVMCloudObjectInstances();
        plotter.generateTotalTJobUsageProfileCharts(outBasePath,plan.getName());
        assertEquals(12, plan.gettJobClassList().size());

        assertTrue("The  memory profiles are not equal", utils.profileComparator(outBasePath + "/"+"unit_" + plan.getName() + "-memory.png", expOutBasePath + "/" + plan.getName() + "-memory.png", debugOutBasePath));
        assertTrue("The  processor profiles are not equal", utils.profileComparator(outBasePath + "/"+"unit_"  + plan.getName() + "-processor.png", expOutBasePath + "/" + plan.getName() + "-processor.png", debugOutBasePath));
        assertTrue("The  storage profiles are not equal", utils.profileComparator(outBasePath + "/" +"unit_" + plan.getName() + "-storage.png", expOutBasePath + "/" + plan.getName() + "-storage.png", debugOutBasePath));
    }
}