/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.exception;

import java.util.List;

/**
 * Raised when the TJobs cannot be scheduled or the plan is not a valid execution graph.
 */
public class SchedulingException extends RetorchException {

    private static final long serialVersionUID = 1L;

    public SchedulingException(String message) {
        super(message);
    }

    public SchedulingException(String header, List<String> problems) {
        super(header, problems);
    }
}
