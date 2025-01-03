package giis.retorch.profiling;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.profiling.profilegeneration.ProfileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * The {@code ProfileGeneratorTests} class contains the unitary tests of the generation of raw UsageProfile datasets
 */
public class ProfileGeneratorTests  {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String outBasePath ="target/test-outputs/profiler/profilegen";
    private final String inBasePath =  "src/test/java/giis/retorch/profiling/testdata/profilegen";
    private final String expOutBasePath = "src/test/resources/expected_out";

    private ProfilerDataGenerationUtils utils;
    private ProfileGenerator generator;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        log.debug("Creating the tmp directory to store methods output");
        File dir = new File(outBasePath);
        utils = new ProfilerDataGenerationUtils();
        generator = new ProfileGenerator();
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
    public void testProfileTJobGenerator() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = utils.generateExecutionPlan();
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile.csv", 3600, 5);
        testFilesForPath(expOutBasePath + "/exp_out_profile.csv", outBasePath + "/output_profile.csv");
        assertEquals(12, plan.gettJobClassList().size());
    }

    @Test
    public void testProfileCOIGeneratorVM() throws IOException {
        generator.generateCOIContractedCapacities(inBasePath + "/imp_tjobusageprofile.csv", outBasePath + "/profiledataCOIVM.csv", utils.generateVMCloudObjectInstances());
        testFilesForPath(expOutBasePath + "/expected_profiledataCOIVM.csv", outBasePath + "/profiledataCOIVM.csv");
    }

    @Test
    public void testProfileCOIGeneratorContainers() throws IOException {
        generator.generateCOIContractedCapacities(inBasePath + "/imp_tjobusageprofile.csv", outBasePath + "/profiledataCOIContainers.csv", utils.generateContainersCloudObjectInstances());
        testFilesForPath(expOutBasePath + "/expected_profiledataCOIContainers.csv", outBasePath + "/profiledataCOIContainers.csv");
    }

    @Test
    public void testProfileCOIGeneratorBrowsers() throws IOException {
        generator.generateCOIContractedCapacities(inBasePath + "/ìmp_tjobusageprofilewithslots.csv", outBasePath + "/profiledataCOIServices.csv", utils.generateBrowserServiceCloudObjectInstances());
        testFilesForPath(expOutBasePath + "/expected_profiledataCOIServices.csv", outBasePath + "/profiledataCOIServices.csv");
    }
    public static void testFilesForPath(String expectedOutputPath, String outputPath) throws IOException {
        String expectedOutput = FileUtils.readFileToString(new File(expectedOutputPath), "utf-8").replace("\r\n", "\n");
        String actualOutput = FileUtils.readFileToString(new File(outputPath), "utf-8").replace("\r\n", "\n");
        assertEquals("The avg file generated differs from expected", expectedOutput, actualOutput);

    }
}