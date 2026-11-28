/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import giis.retorch.orchestration.model.SystemUnderTest;
import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.exception.InvalidSystemException;
import giis.retorch.orchestration.classifier.RetorchClassifier;
import giis.retorch.orchestration.testdata.SyntheticInvalidClassTests;
import giis.retorch.orchestration.testdata.synteticpackage.insidepackage.SyntheticClassOneTests;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
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
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
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
    public void testUnitArchIResValidPackage() throws Exception {
        String packageName = "giis.retorch.orchestration.testdata.synteticpackage";
        SystemUnderTest currentSystem = classifier.getSystemFromPackage(SYSTEM_NAME, packageName);
        SystemUnderTest expectedSystem = utilsClassifier.generateSystemPackage();
        assertEquals(expectedSystem.toString(), currentSystem.toString());
    }

    @Test
    public void testUnitArchIResValidClass() throws Exception {
        Class<?> testClass = SyntheticClassOneTests.class;
        SystemUnderTest system = classifier.getSystemFromClass(SYSTEM_NAME, testClass);
        SystemUnderTest expectedSystem = utilsClassifier.generateSystemFromClass();
        assertEquals(expectedSystem.toString(), system.toString());
    }

    @Test(expected = EmptyInputException.class)
    public void testUnitArchIResInvalidEmptyPackage() throws Exception {
        String packageName = "retorch.examples.architecture.aggregator.emptypackage";
        classifier.getSystemFromPackage(SYSTEM_NAME, packageName);
    }

    @Test
    public void testUnitArchIResInvalidClassFailsListingEveryProblem() throws Exception {
        try {
            classifier.getSystemFromClass(SYSTEM_NAME, SyntheticInvalidClassTests.class);
            org.junit.Assert.fail("the annotations are invalid");
        } catch (InvalidSystemException e) {
            assertTrue(e.getProblems().size() > 1);
            assertTrue(e.getProblems().contains("The AccessModeType in method testThreeAttributesLoosingAccessMode is not specified"));
            assertTrue(e.getProblems().contains("The Concurrency Attribute in method testTwoAttributesLoosingAccessMode is not present"));
            assertTrue(e.getMessage().contains("validationAccessModeAttributes"));
        }
    }

    @Test
    public void testUnitArchIResMethodsWithoutRetorchAnnotationsAreNotTests() throws Exception {
        // the valid class has helper methods that are not annotated: they are not errors
        classifier.getSystemFromClass(SYSTEM_NAME, SyntheticClassOneTests.class);
    }

    @Test
    public void testUnitArchIResInvalidClass() throws Exception {
        Class<?> invalidClass = SyntheticInvalidClassTests.class;
        classifier.lenient(true).getSystemFromClass(SYSTEM_NAME, invalidClass);
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

    private static final String SYNTHETIC_PACKAGE = "giis.retorch.orchestration.testdata.synteticpackage";

    private void addToJar(JarOutputStream out, File directory, String entryPrefix) throws IOException {
        File[] files = directory.listFiles();
        for (File file : files) {
            String name = entryPrefix + file.getName();
            if (file.isDirectory()) {
                out.putNextEntry(new JarEntry(name + "/"));
                out.closeEntry();
                addToJar(out, file, name + "/");
            } else {
                out.putNextEntry(new JarEntry(name));
                out.write(Files.readAllBytes(file.toPath()));
                out.closeEntry();
            }
        }
    }

    @Test
    public void testUnitArchIResValidPackageInsideAJar() throws Exception {
        File jar = tmp.newFile("tests.jar");
        String packagePath = SYNTHETIC_PACKAGE.replace('.', '/');
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            String entryPrefix = "";
            for (String folder : packagePath.split("/")) {
                entryPrefix += folder + "/";
                out.putNextEntry(new JarEntry(entryPrefix));
                out.closeEntry();
            }
            addToJar(out, new File("target/test-classes/" + packagePath), entryPrefix);
        }
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // the parent hides the package, so the classes can only come from the jar
        ClassLoader hidingParent = new ClassLoader(original) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(SYNTHETIC_PACKAGE)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }

            @Override
            public URL getResource(String name) {
                return name.startsWith(packagePath) ? null : super.getResource(name);
            }
        };
        try (URLClassLoader jarLoader = new URLClassLoader(new URL[] {jar.toURI().toURL()}, hidingParent)) {
            Thread.currentThread().setContextClassLoader(jarLoader);
            SystemUnderTest currentSystem = classifier.getSystemFromPackage(SYSTEM_NAME, SYNTHETIC_PACKAGE);
            assertEquals(utilsClassifier.generateSystemPackage().toString(), currentSystem.toString());
            assertTrue(currentSystem.getTestCases().get(0).getTestClass().getClassLoader() == jarLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test(expected = EmptyInputException.class)
    public void testUnitArchIResUnknownPackageFails() throws Exception {
        classifier.getSystemFromPackage(SYSTEM_NAME, SYNTHETIC_PACKAGE + ".doesnotexist");
    }
}
