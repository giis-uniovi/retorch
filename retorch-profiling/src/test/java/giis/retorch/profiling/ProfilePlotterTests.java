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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ProfilePlotterTests {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final String outBasePath = "target/test-outputs/profiler/profiles";
    private final String inBasePath = "src/test/java/giis/retorch/profiling/testdata/profilegen";

    private ProfileGeneratorUtils utils;
    private ProfilePlotter plotter;
    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        log.debug("Creating the tmp directory to store methods output");
        File dir = new File(outBasePath);
        this.utils = new ProfileGeneratorUtils();

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
    public void testProfilePlotter() throws NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = utils.generateExecutionPlan();
        plotter=new ProfilePlotter(inBasePath+"/imp_usageprofileplotter.csv");
        utils.generateVMCloudObjectInstances();
        plotter.generateTotalTJobUsageProfileCharts(outBasePath,plan.getName());
        assertEquals(12, plan.gettJobClassList().size());
    }

    
}
