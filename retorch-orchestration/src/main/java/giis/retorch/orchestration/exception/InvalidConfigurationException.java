/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.exception;

import java.util.List;

/**
 * Raised when retorchCI.properties or the resources JSON are missing or wrong.
 */
public class InvalidConfigurationException extends RetorchException {

    private static final long serialVersionUID = 1L;

    public InvalidConfigurationException(String message) {
        super(message);
    }

    public InvalidConfigurationException(String header, List<String> problems) {
        super(header, problems);
    }
}
