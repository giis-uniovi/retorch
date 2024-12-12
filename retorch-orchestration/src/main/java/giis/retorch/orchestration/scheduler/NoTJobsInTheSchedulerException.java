package giis.retorch.orchestration.scheduler;

public class NoTJobsInTheSchedulerException extends Exception {

    /**
     * Exception throw when the list of TJobs in the Scheduler is empty
     * @param errorMessage String with the message to provide additional information about the root of error
     */
    public NoTJobsInTheSchedulerException(String errorMessage) {
        super(errorMessage);
    }
}