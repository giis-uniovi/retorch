/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
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