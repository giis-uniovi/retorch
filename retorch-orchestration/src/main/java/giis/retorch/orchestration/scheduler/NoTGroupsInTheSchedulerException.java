package giis.retorch.orchestration.scheduler;

public class NoTGroupsInTheSchedulerException extends Exception {

    /**
     * Exception that is thrown when the scheduler list of tGroups is empty
     * @param errorMessage String with the message to provide additional information about the root of error
     */
    public NoTGroupsInTheSchedulerException(String errorMessage) {
        super(errorMessage);
    }
}