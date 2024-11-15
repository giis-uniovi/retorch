package giis.retorch.orchestration.testdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;

/**
 * This exception is thrown when the classifier receives an input that is empty or its invalid i.e: null, no exist
 * directories...
 */
public class EmptyInputException extends Exception {

    @Serial
    private static final long serialVersionUID = -6473170362328956807L;
    private static final Logger logEmptyInputException = LoggerFactory.getLogger(EmptyInputException.class);

    public EmptyInputException(String errorMessage) {
        super(errorMessage);
        logEmptyInputException.error(errorMessage);
    }
}