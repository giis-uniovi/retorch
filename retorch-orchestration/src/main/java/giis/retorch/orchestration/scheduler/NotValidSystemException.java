package giis.retorch.orchestration.scheduler;

public class NotValidSystemException extends Exception {

    /**
     * Exception that is thrown when the System has the list of test cases or resources empty
     * @param errorMessage String with the message to provide additional information about the root of error
     */
    public NotValidSystemException(String errorMessage) {
        super(errorMessage);
    }
}