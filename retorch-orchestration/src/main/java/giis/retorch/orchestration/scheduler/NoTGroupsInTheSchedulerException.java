package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.exception.SchedulingException;

/**
 * The {@code NoTGroupsInTheSchedulerException}  is thrown in  {@code RetorchScheduler} when receives an empty list
 * of {@code TGroup}s
 */
public class NoTGroupsInTheSchedulerException extends SchedulingException {

    public NoTGroupsInTheSchedulerException(String errorMessage) {
        super(errorMessage);
    }
}