/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.classifier;

import giis.retorch.orchestration.exception.InvalidSystemException;

/**
 * The {@code EmptyInputException}  is thrown when the {@code RetorchClassifier} receives an input that is empty
 * or its invalid i.e: null, no exist directories...
 */
public class EmptyInputException extends InvalidSystemException {

    private static final long serialVersionUID = -6473170362328956807L;

    public EmptyInputException(String errorMessage) {
        super(errorMessage);
    }
}
