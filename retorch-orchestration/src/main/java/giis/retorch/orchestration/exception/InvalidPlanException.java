/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.exception;

import java.util.List;

/**
 * Raised when the activities do not form a valid execution graph: a cycle, a predecessor that is not part of the
 * plan, or no activity that can start the execution.
 */
public class InvalidPlanException extends SchedulingException {

    private static final long serialVersionUID = 1L;

    public InvalidPlanException(String message) {
        super(message);
    }

    public InvalidPlanException(String header, List<String> problems) {
        super(header, problems);
    }
}
