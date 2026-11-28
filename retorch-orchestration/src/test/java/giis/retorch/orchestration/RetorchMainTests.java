/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration;

import giis.retorch.orchestration.main.RetorchMain;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The command line: exit codes and the place of the Jenkinsfile.
 */
public class RetorchMainTests {

    private static final String PACKAGE = "giis.retorch.orchestration.testdata.integration";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testWrongNumberOfArgumentsIsAUsageError() {
        assertEquals(RetorchMain.EXIT_USAGE, RetorchMain.run(new String[0]));
        assertEquals(RetorchMain.EXIT_USAGE, RetorchMain.run(new String[] {"a", "b"}));
        assertEquals(RetorchMain.EXIT_USAGE, RetorchMain.run(new String[] {"a", "b", "c", "d"}));
    }

    @Test
    public void testUnknownOrContradictoryOptionsAreAUsageError() {
        assertEquals(RetorchMain.EXIT_USAGE, RetorchMain.run(new String[] {"a", "b", "c", "--verbose"}));
        assertEquals(RetorchMain.EXIT_USAGE,
                RetorchMain.run(new String[] {"a", "b", "c", "--profiling", "--no-profiling"}));
    }

    @Test
    public void testHelpIsNotAnError() {
        assertEquals(RetorchMain.EXIT_OK, RetorchMain.run(new String[] {"--help"}));
    }

    @Test
    public void testGenerationFailureIsExitCodeTwo() throws Exception {
        String out = tmp.newFolder("out").getPath();
        assertEquals(RetorchMain.EXIT_FAILURE,
                RetorchMain.run(new String[] {"package.that.does.not.exist", "SyntheticIntegration", out}));
        assertEquals(RetorchMain.EXIT_FAILURE, RetorchMain.run(new String[] {PACKAGE, "NoSuchSystem", out}));
    }

    @Test
    public void testJenkinsfileIsWrittenInsideTheGivenDirectory() throws Exception {
        File out = tmp.newFolder("out");
        // with and without the trailing separator the file is out/Jenkinsfile (it used to be outJenkinsfile)
        assertEquals(RetorchMain.EXIT_OK,
                RetorchMain.run(new String[] {PACKAGE, "SyntheticIntegration", out.getPath(), "--no-profiling"}));
        File jenkinsfile = new File(out, "Jenkinsfile");
        assertTrue(jenkinsfile.exists());
        String expected = FileUtils.readFileToString(
                new File(OrchestrationIntegrationTests.EXPECTED_OUTPUT_PATH + "outputTestIntegration"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(expected, FileUtils.readFileToString(jenkinsfile, StandardCharsets.UTF_8));
        assertTrue(jenkinsfile.delete());
        assertEquals(RetorchMain.EXIT_OK,
                RetorchMain.run(new String[] {"--lenient", PACKAGE, "SyntheticIntegration",
                        out.getPath() + File.separator}));
        assertTrue(jenkinsfile.exists());
        assertTrue(!new File(out.getParentFile(), "outJenkinsfile").exists());
    }
}
