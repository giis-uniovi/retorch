package giis.retorch.orchestration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.System;
import giis.retorch.orchestration.model.TGroup;
import giis.retorch.orchestration.scheduler.NoTGroupsInTheSchedulerException;
import giis.retorch.orchestration.scheduler.NotValidSystemException;
import giis.retorch.orchestration.scheduler.RetorchAggregator;
import giis.retorch.orchestration.scheduler.RetorchScheduler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@code SchedulerTests} class provides the unitary tests of the different {@code RetorchScheduler} and
 * {@code RetorchAggregator} methods
 */
public class SchedulerTests {

    private final Appender<ILoggingEvent> appender = mock(Appender.class);
    private RetorchAggregator aggregator;
    private RetorchScheduler scheduler;
    private SchedulerUtils utils;
    private System baseSystem;

    @Before
    public void setUp() {
        Logger loggerAggregator = (Logger) LoggerFactory.getLogger(RetorchAggregator.class);
        Logger loggerScheduler = (Logger) LoggerFactory.getLogger(RetorchScheduler.class);
        loggerAggregator.addAppender(appender);
        loggerScheduler.addAppender(appender);
        utils = new SchedulerUtils();
    }

    @Test
    public void testUnitArchIAggValidTGroup() throws NotValidSystemException, IOException {
        baseSystem = utils.generateSystemAggregator();
        aggregator = new RetorchAggregator(baseSystem);
        List<TGroup> tGroupsOutputAggregator = aggregator.generateTGroups();
        List<TGroup> expectedTGroups = utils.getExpectedTGroups();
        Assert.assertEquals(expectedTGroups, tGroupsOutputAggregator);
    }

    @Test
    public void testUnitArchAggInvalidTGroup() throws NotValidSystemException, IOException {
        baseSystem = utils.getInvalidSystemAggregator();
        aggregator = new RetorchAggregator(baseSystem);
        ArgumentCaptor<LoggingEvent> argument = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(appender, atLeast(2)).doAppend(argument.capture());
        List<LoggingEvent> listEvents = argument.getAllValues();
        List<String> listMessages = listEvents.stream().map(LoggingEvent::getMessage).collect(Collectors.toList());
        //Checks that all the resources provided are required by a test case and  that all test cases have the required resources in the system
        assertEquals("The test case  TCNoRequired requires the resources: [res{InventedResourceID', p=[elasticParent], r=[lightInElasticResource], elas{'elasModelLightElasticResource', e=35, cost=15.0}, LOGICAL,[{name:memory, quantity:1.0}, {name:processor, quantity:0.5}]}] any of them doesn't exist in the system", listMessages.get(1));
        //Checks a repeated resource (invalid)
        assertEquals("There are several repeated Test Cases in the System " +
                "InvalidSystem:[tSevenAgg]", listMessages.get(0));
    }

    @Test
    public void testUnitArchAggInvalidEmptyTGroups() throws NotValidSystemException, IOException {
        baseSystem = utils.generateSystemAggregator();
        aggregator = new RetorchAggregator(new System("EmptySystem"));
        ArgumentCaptor<LoggingEvent> argument = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(appender, atLeast(2)).doAppend(argument.capture());
        List<LoggingEvent> listEvents = argument.getAllValues();
        List<String> listMessages = listEvents.stream().map(LoggingEvent::getMessage).collect(Collectors.toList());
        //Check that the omitted AccessMode is detected and the omission of attributes in the accessMode
        assertEquals(4, listMessages.size());
        assertEquals("The number of resources in the system EmptySystem is zero", listMessages.get(0));
        assertEquals("The number of test cases in the system EmptySystem is zero", listMessages.get(1));
    }

    @Test
    public void testUnitArchSchValidTGroupRepeated() throws NoTGroupsInTheSchedulerException {
        List<TGroup> initialTGroups = utils.getExpectedSchedulerTGroups();
        scheduler = new RetorchScheduler(initialTGroups);
        List<Activity> listActivities = scheduler.generateActivities();
        List<Activity> expectedActivities =
                utils.getExpectedActivities().stream().sorted(Comparator.comparing(Activity::toString)).collect(Collectors.toList());
        assertEquals(expectedActivities.toString(), listActivities.toString());
    }

    @Test
    public void testUnitArchSchValidTGroupNoRepeated() throws NoTGroupsInTheSchedulerException {
        List<TGroup> initialTGroups = utils.getNoRepeatedExpectedTGroups();
        scheduler = new RetorchScheduler(initialTGroups);
        List<Activity> listActivities = scheduler.generateActivities();
        List<Activity> expectedActivities = utils.getExpectedNonRepeatedActivities();
        assertEquals(expectedActivities, listActivities);
    }

    @Test(expected = NoTGroupsInTheSchedulerException.class)
    public void testUnitArchSchInvalidEmptyTGroup() throws NoTGroupsInTheSchedulerException {
        scheduler = new RetorchScheduler(new LinkedList<>());
        scheduler.generateActivities();
    }

    @Test
    public void testUnitArchSchInvalidDuplicatedResources() throws NoTGroupsInTheSchedulerException {
        List<TGroup> initialTGroups = utils.getInvalidTGroups();
        scheduler = new RetorchScheduler(initialTGroups);
        ArgumentCaptor<LoggingEvent> argument = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(appender, atLeast(1)).doAppend(argument.capture());
        List<LoggingEvent> listEvents = argument.getAllValues();
        List<String> listMessages = listEvents.stream().map(LoggingEvent::getMessage).collect(Collectors.toList());
        Assert.assertEquals("There are repeated resources in one of the providedTGroups", listMessages.get(0));
    }
}