package giis.retorch.orchestration.orchestrator;

/**
 * The {@code NoFinalActivitiesException}  is thrown in  {@code RetorchOrchestrator} when all activities of the
 * graph are preceded for something.
 */
public class NoFinalActivitiesException extends Exception {

    public NoFinalActivitiesException(String errorMessage) {
        super(errorMessage);
    }
}