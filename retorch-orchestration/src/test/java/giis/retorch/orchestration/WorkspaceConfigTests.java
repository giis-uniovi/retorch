/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration;

import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.config.RetorchPaths;
import giis.retorch.orchestration.exception.InvalidConfigurationException;
import giis.retorch.orchestration.generator.OrchestrationGenerator;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The workspace ({@code RetorchPaths}) and the typed configuration ({@code RetorchConfig}): the generator can run in
 * any folder and a bad configuration fails with a clear message.
 */
public class WorkspaceConfigTests {

    private static final String SYSTEM = "SyntheticIntegration";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testPathsLayout() {
        RetorchPaths paths = new RetorchPaths(Paths.get("ws"));
        assertEquals(Paths.get("ws", ".retorch", "configurations", "XSystemResources.json"), paths.resourcesFile("X"));
        assertEquals(Paths.get("ws", ".retorch", "scripts", "tjoblifecycles"), paths.tjobScriptsDir());
        assertEquals(Paths.get("ws", ".retorch", "customscriptscode", "custom.env"), paths.customFile(giis.retorch.orchestration.config.CustomHook.ENV));
        assertEquals(Paths.get("ws", ".retorch", "executionplan.json"), paths.executionPlanFile());
    }

    @Test
    public void testGeneratorRunsInAnotherFolder() throws Exception {
        Path workspace = tmp.newFolder("workspace").toPath();
        FileUtils.copyDirectory(new File(".retorch/configurations"),
                workspace.resolve(".retorch/configurations").toFile());
        FileUtils.copyDirectory(new File(".retorch/customscriptscode"),
                workspace.resolve(".retorch/customscriptscode").toFile());
        File out = tmp.newFolder("out");

        String pipeline = new OrchestrationGenerator(new RetorchPaths(workspace))
                .generateJenkinsfile("giis.retorch.orchestration.testdata.integration", SYSTEM,
                        out.getPath() + File.separator);

        IntegrationUtils.testStringAgainstPath(OrchestrationIntegrationTests.EXPECTED_OUTPUT_PATH
                + "outputTestIntegration", pipeline);
        assertTrue(Files.exists(workspace.resolve(".retorch/scripts/tjoblifecycles/tjob-setup.sh")));
        assertTrue(Files.exists(workspace.resolve(".retorch/envfiles/tjoba.env")));
        assertTrue(new File(out, "Jenkinsfile").exists());
        assertFalse(Files.exists(workspace.resolve(".retorch/executionplan.json")));
    }

    @Test
    public void testProfilingOverrideFromTheGenerator() throws Exception {
        Path workspace = tmp.newFolder("workspace").toPath();
        FileUtils.copyDirectory(new File(".retorch/configurations"),
                workspace.resolve(".retorch/configurations").toFile());
        new OrchestrationGenerator(new RetorchPaths(workspace)).withProfiling(true)
                .generateJenkinsfile("giis.retorch.orchestration.testdata.integration", SYSTEM,
                        tmp.newFolder("out").getPath() + File.separator);
        assertTrue(Files.exists(workspace.resolve(".retorch/executionplan.json")));
        assertTrue(Files.exists(workspace.resolve(".retorch/scripts/resourcemonitor.sh")));
    }

    @Test
    public void testMissingPropertyNamesTheProperty() {
        Properties props = new Properties();
        props.setProperty("agentCIName", "any");
        RetorchConfig config = new RetorchConfig(props, "somefile.properties");
        assertEquals("any", config.agentCIName());
        try {
            config.appUrl("tjoba");
            fail("app-url is required");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'app-url'")
                    && e.getMessage().contains("somefile.properties"));
        }
    }

    @Test
    public void testAppUrlAndProfilingOverride() {
        Properties props = new Properties();
        props.setProperty("app-url", "http://sut-$TJOB_NAME:80");
        props.setProperty("profiling", "false");
        RetorchConfig config = new RetorchConfig(props, "x");
        assertEquals("http://sut-tjoba:80", config.appUrl("TJobA"));
        assertFalse(config.profiling());
        assertTrue(config.withProfiling(true).profiling());
        assertFalse("the original is not modified", config.profiling());
        assertFalse(config.withProfiling(null).profiling());
    }

    @Test
    public void testMissingConfigurationFile() throws Exception {
        try {
            RetorchConfig.load(new RetorchPaths(tmp.newFolder("empty").toPath()));
            fail("the file does not exist");
        } catch (InvalidConfigurationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("retorchCI.properties"));
        }
    }

    @Test
    public void testLoadListsAllTheMissingProperties() throws Exception {
        Path workspace = tmp.newFolder("incomplete").toPath();
        Files.createDirectories(workspace.resolve(".retorch/configurations"));
        Files.write(workspace.resolve(".retorch/configurations/retorchCI.properties"),
                ("agentCIName=any" + System.lineSeparator() + "sut-location=x").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            RetorchConfig.load(new RetorchPaths(workspace));
            fail("three required properties are missing");
        } catch (InvalidConfigurationException e) {
            assertEquals(3, e.getProblems().size());
            assertTrue(e.getMessage(), e.getMessage().contains("'app-url'")
                    && e.getMessage().contains("'sut-wait-html'") && e.getMessage().contains("'testsBasePath'"));
        }
    }

    @Test
    public void testInvalidDockerImageFailsTheGeneration() throws Exception {
        Path workspace = tmp.newFolder("workspace").toPath();
        FileUtils.copyDirectory(new File(".retorch/configurations"),
                workspace.resolve(".retorch/configurations").toFile());
        Path json = workspace.resolve(".retorch/configurations/" + SYSTEM + "SystemResources.json");
        String content = new String(Files.readAllBytes(json), StandardCharsets.UTF_8);
        Files.write(json, content.replace("someplaceholder2;heavyInElasticResource.someImage", "noplaceholder")
                .getBytes(StandardCharsets.UTF_8));
        try {
            new OrchestrationGenerator(new RetorchPaths(workspace))
                    .generateJenkinsfile("giis.retorch.orchestration.testdata.integration", SYSTEM,
                            tmp.newFolder("out").getPath() + File.separator);
            fail("a docker image without placeholder is invalid");
        } catch (InvalidConfigurationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("heavyInElasticResource")
                    && e.getMessage().contains("noplaceholder"));
        }
    }
}
