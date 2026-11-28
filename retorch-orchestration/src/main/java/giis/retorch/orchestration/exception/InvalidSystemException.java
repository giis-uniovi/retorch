/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.exception;

import java.util.List;

/**
 * Raised when the annotated tests, the resources or the system built from them are invalid.
 */
public class InvalidSystemException extends RetorchException {

    private static final long serialVersionUID = 1L;

    public InvalidSystemException(String message) {
        super(message);
    }

    public InvalidSystemException(String header, List<String> problems) {
        super(header, problems);
    }
}
