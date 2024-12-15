package giis.retorch.orchestration.scheduler;

/**
 * The {@code NoTGroupsInTheSchedulerException}  is thrown in  {@code RetorchScheduler} when receives an empty list
 * of {@code TGroup}s
 */
public class NoTGroupsInTheSchedulerException extends Exception {

    public NoTGroupsInTheSchedulerException(String errorMessage) {
        super(errorMessage);
    }
}