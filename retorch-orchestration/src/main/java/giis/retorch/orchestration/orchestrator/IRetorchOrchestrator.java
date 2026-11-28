/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.model.ExecutionPlan;

/**
 * The {@code IRetorchOrchestrator} interface is the blueprint of the renderers that turn an {@code ExecutionPlan}
 * into the pipelining code of a certain Continuous Integration System (e.g. Jenkins, GitHub Actions, Gitlab CI/CD).
 * A renderer is a pure function: it does not read or write files.
 */
public interface IRetorchOrchestrator {

    /**
     * Renders the whole pipeline of the plan.
     *
     * @param plan   plan with the activities sorted by stage
     * @param config the CI configuration (agent, SUT location, application URL, profiling)
     * @return the text of the pipeline file
     */
    String render(ExecutionPlan plan, RetorchConfig config);
}
