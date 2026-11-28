/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.orchestrator.JenkinsfileRenderer;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The renderer is a pure function: the plan and an in-memory configuration give the Jenkinsfile, with no file system.
 */
public class JenkinsfileRendererTests {

    private static final String EXPECTED_OUTPUT_PATH = "src/test/resources/expected_out/";

    private RetorchConfig config(boolean profiling) {
        Properties props = new Properties();
        props.setProperty("agentCIName", "any");
        props.setProperty("sut-location", "$WORKSPACE");
        props.setProperty("app-url", "\"http://full-teaching-$TJOB_NAME:5000\"");
        props.setProperty("profiling", String.valueOf(profiling));
        return new RetorchConfig(props, "in memory");
    }

    @Test
    public void testRenderMatchesTheExpectedPipeline() throws IOException, EmptyInputException,
            NoFinalActivitiesException {
        ExecutionPlan plan = new ExecutionPlan("plan", new OrchestratorUtils().getListSimpleActivities());
        String pipeline = new JenkinsfileRenderer().render(plan, config(false));
        new OrchestratorUtils().compareOutputToFile(EXPECTED_OUTPUT_PATH + "outputTestUnitArchOrchValidSimpleGraph",
                pipeline);
        assertFalse(pipeline.contains("Usage-Profile"));
    }

    @Test
    public void testRenderWithProfilingAndCustomPostSteps() throws EmptyInputException, NoFinalActivitiesException {
        ExecutionPlan plan = new ExecutionPlan("plan", new OrchestratorUtils().getListSimpleActivities());
        String pipeline = new JenkinsfileRenderer("        junit 'x.xml'\n").render(plan, config(true));
        assertTrue(pipeline.contains("stage('Usage-Profile')"));
        assertTrue(pipeline.contains("        junit 'x.xml'\n        archiveArtifacts artifacts: 'target/testlogs"));
    }

    @Test(expected = IllegalStateException.class)
    public void testRenderFailsWithAMissingRequiredProperty() throws EmptyInputException, NoFinalActivitiesException {
        ExecutionPlan plan = new ExecutionPlan("plan", new OrchestratorUtils().getListSimpleActivities());
        new JenkinsfileRenderer().render(plan, new RetorchConfig(new Properties(), "in memory"));
    }
}
