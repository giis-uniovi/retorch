package giis.retorch.orchestration.scheduler;

/**
 * The {@code NotValidSystemException}  is raised when the {@code System} has the list of {@code TestCase} or {@code Resource} empty
 */
public class NotValidSystemException extends Exception {

    public NotValidSystemException(String errorMessage) {
        super(errorMessage);
    }
}