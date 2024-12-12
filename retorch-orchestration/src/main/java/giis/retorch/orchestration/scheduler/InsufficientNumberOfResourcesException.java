package giis.retorch.orchestration.scheduler;


public class InsufficientNumberOfResourcesException extends Exception {

    /**
     * Exception that is thrown when the resources annotated are less than the access modes
     * @param errorMessage String with the message to provide additional information about the root of error *
     */
    public InsufficientNumberOfResourcesException(String errorMessage) {
        super(errorMessage);
    }
}