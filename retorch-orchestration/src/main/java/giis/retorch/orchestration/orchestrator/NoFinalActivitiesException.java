package giis.retorch.orchestration.orchestrator;

public class NoFinalActivitiesException extends Exception {

    /**
     * Exception thrown when all the activities of the graph are preceded for someone
     * @param errorMessage String with the message to provide additional information
     *                     about the root of error
     */
    public NoFinalActivitiesException(String errorMessage) {
        super(errorMessage);
    }
}