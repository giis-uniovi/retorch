package giis.retorch.orchestration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import giis.retorch.orchestration.model.System;
import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.classifier.RetorchClassifier;
import giis.retorch.orchestration.testdata.SyntheticInvalidClassTests;
import giis.retorch.orchestration.testdata.synteticpackage.insidepackage.SyntheticClassOneTests;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * {@code ClassifierTests} class provides the unitary tests of the different {@code RetorchClassifier} methods
 * */
public class ClassifierTests {

    private static final String SYSTEM_NAME = "ClassifierUnitTests";
    private final Appender<ILoggingEvent> appender = mock(Appender.class);  //use Mockito's ArgumentCaptor to capture all logging
    // events in your test case
    private RetorchClassifier classifier;   //mock the Appender of your Logger object
    private ClassifierUtils utilsClassifier;

    @Before
    public void setUp() {
        //Not sure if it's the correct fix, I ve added both loggers to the appended. I am not sure if exist something
        // like ROOT log in sfl-4j, but it works
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RetorchClassifier.class);
        logger.addAppender(appender);
        classifier = new RetorchClassifier();
        utilsClassifier = new ClassifierUtils();
    }

    @Test
    public void testUnitArchIResValidPackage() throws EmptyInputException, IOException, ClassNotFoundException,
            URISyntaxException {
        String packageName = "giis.retorch.orchestration.testdata.synteticpackage";
        System currentSystem = classifier.getSystemFromPackage(SYSTEM_NAME, packageName);
        System expectedSystem = utilsClassifier.generateSystemPackage();
        assertEquals(expectedSystem.toString(), currentSystem.toString());
    }

    @Test
    public void testUnitArchIResValidClass() throws EmptyInputException, IOException {
        Class<?> testClass = SyntheticClassOneTests.class;
        System system = classifier.getSystemFromClass(SYSTEM_NAME, testClass);
        System expectedSystem = utilsClassifier.generateSystemFromClass();
        assertEquals(expectedSystem.toString(), system.toString());
    }

    @Test(expected = EmptyInputException.class)
    public void testUnitArchIResInvalidEmptyPackage() throws EmptyInputException, IOException, ClassNotFoundException
            , URISyntaxException {
        String packageName = "retorch.examples.architecture.aggregator.emptypackage";
        classifier.getSystemFromPackage(SYSTEM_NAME, packageName);
        utilsClassifier.generateSystemFromClass();
    }

    @Test
    public void testUnitArchIResInvalidClass() throws EmptyInputException, IOException {
        Class<?> invalidClass = SyntheticInvalidClassTests.class;
        classifier.getSystemFromClass(SYSTEM_NAME, invalidClass);
        ArgumentCaptor<ch.qos.logback.classic.spi.LoggingEvent> argument =
                ArgumentCaptor.forClass(ch.qos.logback.classic.spi.LoggingEvent.class);
        verify(appender, atLeast(25)).doAppend(argument.capture());
        List<LoggingEvent> listEvents = argument.getAllValues();
        List<String> listMessages = listEvents.stream().map(LoggingEvent::getMessage).collect(Collectors.toList());
        // Omission of attributes in the accessMode
        assertTrue(listMessages.contains("The AccessModeType in method testThreeAttributesLoosingAccessMode is not specified")); // Check that the omitted AccessMode is detected
        assertTrue(listMessages.contains("The Concurrency Attribute in method testTwoAttributesLoosingAccessMode is not present")); // Check that the concurrency omitted is detected
        assertTrue(listMessages.contains("The ResourceID Attribute in method testTwoAttributesLoosingAccessMode is not present")); // Check that the resourceId omitted is detected
        // Validation of the attributes
        assertTrue(listMessages.contains("The AccessModeType in method validationAccessModeAttributes doesn't exist")); // AccessMode Invalid
        assertTrue(listMessages.contains("Invalid Concurrency in method validationAccessModeAttributes: non-shareable resource with concurrency > 1")); // Concurrency Invalid sharing=true && <2 and sharing
        assertTrue(listMessages.contains("Invalid Concurrency in method validationAccessModeAttributes: shareable resource with concurrency < 2")); // Concurrency Invalid
    }
}