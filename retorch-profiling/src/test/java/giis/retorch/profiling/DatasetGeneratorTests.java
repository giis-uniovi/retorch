package giis.retorch.profiling;

import giis.retorch.profiling.main.UsageProfilerToolBox;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DatasetGeneratorTests  {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String outBasePath = "target/test-outputs/profiler";
    private final String inBasePath = "src/test/java/giis/retorch/profiling/testdata" + "/datasetgenerator";
    private final String expOutBasePath = "src/test/resources/expected_out";

    @Rule
    public TestName testName = new TestName();
    UsageProfilerToolBox helper ;
    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        helper = new UsageProfilerToolBox();
        log.debug("Creating the tmp directory to store methods output");
        File dir = new File(outBasePath);
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
        log.info("****** Set-up for test: {} ended ******", testName.getMethodName());
    }


    @Test
    public void testFilesGeneratorForSample() throws IOException {
        helper.generateAverageDurationCSVFile(inBasePath + "/sample",outBasePath + "/testFileGeneratorOutput.csv");
        testFilesForPath(expOutBasePath + "/testFileGeneratorOutput.csv", outBasePath + "/testFileGeneratorOutput.csv");
    }

    @Test
    public void testFilesGeneratorFor4Parallel() throws IOException {
        helper.generateAverageDurationCSVFile(inBasePath + "/4parallel",outBasePath + "/testFileGeneratorOutput4parallel.csv");
        testFilesForPath(expOutBasePath + "/testFileGeneratorOutput4parallel.csv", outBasePath + "/testFileGeneratorOutput4parallel.csv");
    }

    public static void testFilesForPath(String expectedOutputPath, String outputPath) throws IOException {
        String expectedOutput = FileUtils.readFileToString(new File(expectedOutputPath), "utf-8").replace("\r\n", "\n");
        String actualOutput = FileUtils.readFileToString(new File(outputPath), "utf-8").replace("\r\n", "\n");
        assertEquals("The avg file generated differs from expected", expectedOutput, actualOutput);

    }
}