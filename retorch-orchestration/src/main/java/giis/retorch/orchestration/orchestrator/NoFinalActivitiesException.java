/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.exception.SchedulingException;

/**
 * The {@code NoFinalActivitiesException}  is thrown in  {@code RetorchOrchestrator} when all activities of the
 * graph are preceded for something.
 */
public class NoFinalActivitiesException extends SchedulingException {

    public NoFinalActivitiesException(String errorMessage) {
        super(errorMessage);
    }
}