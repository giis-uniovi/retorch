package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.exception.InvalidSystemException;

/**
 * The {@code NotValidSystemException}  is raised when the {@code SystemUnderTest} has the list of {@code TestCase} or {@code Resource} empty
 */
public class NotValidSystemException extends InvalidSystemException {

    public NotValidSystemException(String errorMessage) {
        super(errorMessage);
    }

    public NotValidSystemException(String header, java.util.List<String> problems) {
        super(header, problems);
    }
}