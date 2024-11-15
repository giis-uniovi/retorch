package giis.retorch.orchestration.classifier;

import giis.retorch.annotations.AccessModes;
import giis.retorch.orchestration.model.*;
import giis.retorch.orchestration.model.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RetorchClassifier {

    private static final Logger log = LoggerFactory.getLogger(RetorchClassifier.class);

    private static final String CHORUS_ATTRIBUTES = " is not present";
    private static final String NO_EXIST = " doesn't exist";
    private final ResourceSerializer deserializer; //Serializer employed to deserialize the resources info

    private Map<String, Resource> listAllResources;

    /**
     * Empty constructor used to validate RETORCH in the test case
     */
    public RetorchClassifier() {
        deserializer = new ResourceSerializer();
    }
    /**
     * This method gets the file specified as package, in order to do this, instantiates the classLoader
     * and gets from it the File, replacing the dots by  the slash. If the file exist returns it, otherwise
     * returns two types of exception
     * @param packageName String with the package name
     * @throws IllegalStateException if the classLoader couldn't be found
     * @throws EmptyInputException   if the Package is not in the Classpath
     */

    private static File getPackageSourceDirectory(String packageName) throws EmptyInputException, URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException("ClassLoader could not be Loaded");
        }
        URL resourceURL = classLoader.getResource(packageName.replace('.', '/'));
        if (resourceURL == null) {
            throw new EmptyInputException("Package " + packageName + " not found on classpath.");
        }
        URI uri = resourceURL.toURI();
        Path path = Paths.get(uri);
        return path.toFile();
    }

    /**
     * This method retrieves given one packageName the different classes contained on it
     * @param packageName String with the package name
     * @param directory   File with the directory required
     * @return List with the different Class methods.
     * This solution is inspired by the answer of this StackOverflow thread:
     * <a href="https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection">......</a>
     */
    private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    classes.addAll(findClasses(file, packageName + "." + file.getName()));
                } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                    classes.add(Class.forName(packageName + '.' + file.getName().replace(".class", "")));
                }
            }
        } else {
            log.error("Classes of the specified package {} not found", packageName);
        }
        return classes;
    }

    /**
     * This method deserializes the information related to the Resources Attributes, gets the package as a file
     * Object, and use it to retrieve
     * the different Classes of the package. Once the classes are retrieved, are used to retrieve the different
     * Annotated Methods, resources and
     * Access Modes that conforms que system. Finally, the system created is validated in order to check if the
     * resources and test cases list are
     * correct
     * @param packageName String with the name of the package
     * @param systemName  String with the system name (must be the same as specified in the resource attributes JSON
     *                    file
     */
    public System getSystemFromPackage(String systemName, String packageName) throws EmptyInputException,
            IOException, ClassNotFoundException, URISyntaxException {
        this.listAllResources= new HashMap<>();
        for (Resource res:deserializer.deserializeResources(systemName).values()){
            if (resourceChecker(res)){
                this.listAllResources.put(res.getResourceID(),res);
            }
        }
        System systemRetorch = new System(systemName);
        File directory = getPackageSourceDirectory(packageName);
        List<Class<?>> listClassesPackage = findClasses(directory, packageName);
        for (Class<?> currentClass : listClassesPackage) {
            System temporalSystem = getSystemFromClass(systemName, currentClass);
            addUniqueResources(systemRetorch, temporalSystem);
            systemRetorch.addListTestCases(temporalSystem.getTestCases());
        }
        sortSystemClass(systemRetorch);
        return checkSystemClass(systemRetorch);
    }

    private void addUniqueResources(System systemRetorch, System temporalSystem) {
        temporalSystem.getResources().stream()
                .filter(res -> !systemRetorch.getResources().contains(res))
                .forEach(systemRetorch::addResourceClass);
    }

    private void sortSystemClass(System systemRetorch) {
        systemRetorch.getTestCases().sort(Comparator.comparing(TestCase::getName));
        systemRetorch.getResources().sort(Comparator.comparing(Resource::toString));
    }

    /**
     * This method given a Class file and a System name, retrieves the System that is on it. In order to accomplish
     * with it first
     * retrieve the class methods.With these methods, iterate over them retrieving (1)The test method ant its access
     * mode
     * (2) The resources annotated to then validate it. The test method and resources are added to the returned system
     * @param systemName    String with the system name
     * @param testCaseClass Class with the test cases annotated
     */
    public System getSystemFromClass(String systemName, Class<?> testCaseClass) throws EmptyInputException,
            IOException {
        this.listAllResources = deserializer.deserializeResources(systemName);
        System systemRetorch = new System(systemName);
        List<Method> listMethods = this.getClassMethods(testCaseClass);
        systemRetorch.addListOfResources(new ArrayList<>(listAllResources.values()));
        for (Method m : listMethods) {
            TestCase testCase = getTestCasesFromMethod(m, testCaseClass);
            if (validateTestCase(testCase)) {
                systemRetorch.addTestCase(testCase);
            }
        }
        if (listMethods.isEmpty())
            throw new EmptyInputException(String.format("No annotated methods found in class %s",
                    testCaseClass.getName()));
        return checkSystemClass(systemRetorch);
    }

    /**
     * Validation method for the test cases it checks : (1) Each resource has annotated its own access mode (2) The
     * access modes
     * remains to the annotated resources (3) The access mode is not empty
     * @param testCase       testCase to be validated
     */
    public boolean validateTestCase(TestCase testCase) {
        boolean output = true;
        String message;
        if (testCase.getAccessMode().isEmpty()) {
            output = false;
            message=String.format("The test method %s has no access mode",testCase.getName());
            log.error(message);
        }
        return output;
    }

    /**
     * Support method that retrieves the test cases of a given method. In order to accomplish it first, calls the
     * support method that retrieves
     * the access modes, for later create a new TestCaseClass object with the method name and its access modes*
     * @param currentClass  Class that belongs the method
     * @param currentMethod Test case Method
     */
    public TestCase getTestCasesFromMethod(Method currentMethod, Class<?> currentClass) {
        String methodName = currentMethod.getName();
        // Directly assign the result of getAccessModesFromMethod to listAccessModesMethod
        List<AccessMode> listAccessModesMethod = this.getAccessModesFromMethod(currentMethod);
        TestCase newTestCase = new TestCase(methodName, currentClass);
        newTestCase.setAccessMode(listAccessModesMethod);
        return newTestCase;
    }

    /**
     * Support method that given one testMethod, retrieves all the Access modes annotated on it.In order to
     * accomplish with
     * it, checks if the resource and Access mode RETORCH annotations are present, retrieving a list with them.Then
     * creates
     * the accessModes with all the information retrieved from the annotations ( resource,elasticity,sharing...)
     * @param testMethod Method to retrieve the annotations.
     */
    public List<AccessMode> getAccessModesFromMethod(Method testMethod) {
        List<AccessMode> listAccessModes = new ArrayList<>(); // Use ArrayList instead of LinkedList for better

        List<giis.retorch.annotations.AccessMode> listAccessModesTag = new ArrayList<>();  // random access performance

        if (testMethod.isAnnotationPresent(giis.retorch.annotations.AccessMode.class)) {       // Check for single annotation presence
            listAccessModesTag.add(testMethod.getAnnotation(giis.retorch.annotations.AccessMode.class));
        }
        if (testMethod.isAnnotationPresent(AccessModes.class) ) {        // Check for multiple annotations presence
            listAccessModesTag.addAll(Arrays.asList(testMethod.getAnnotation(AccessModes.class).value())); // Use
            // addAll instead of assignment to avoid overwriting previous values
           }
        for (giis.retorch.annotations.AccessMode accessTag : listAccessModesTag) {
            if (this.accessModeTagChecker(accessTag, testMethod.getName())) {
                AccessMode currentAccessMode = new AccessMode();
                currentAccessMode.setConcurrency(accessTag.concurrency());
                Resource requiredResource = getRequiredResource(accessTag.resID());
                currentAccessMode.setResource(requiredResource);
                currentAccessMode.setSharing(accessTag.sharing());
                currentAccessMode.setType(new AccessModeTypes(accessTag.accessMode()));
                listAccessModes.add(currentAccessMode);
            }
        }
        return listAccessModes;
    }

    /**
     * Support method that checks the resources annotated with the access mode
     */
    public Resource getRequiredResource(String idResource) {
        String message;
        if (listAllResources.containsKey(idResource)) {
            return listAllResources.get(idResource);
        } else {
            message=String.format("The resource %s is not tagged", idResource);
            log.info(message);
            return new Resource("Resource Not valid");
        }
    }

    /**
     * Support method that retrieves the methods from a given class. This method filters the Jacoco Test method used
     * to instrument the system and gets the coverage
     * @param testClass Class to retrieve the methods
     */
    public List<Method> getClassMethods(Class<?> testClass) {
        List<Method> methods = Arrays.stream(testClass.getDeclaredMethods())
                .sorted(Comparator.comparing(Method::toString)) // Sort methods
                .filter(meth -> !meth.getName().equals("$jacocoInit")) // Filter out methods with name "$jacocoInit"
                .toList(); // Collect results to a list
        methods.forEach(meth -> log.info("The method name its : {}", meth.getName()));
        return methods;
    }

    /**
     * Support method that validates que resources tagged checking: (1) Validates the elasticityTag (2) Checks if in
     * the non
     * serialized annotation is specified: (2.1)The hierarchy parent, (2.2)The replaceable resource and (2.3) The
     * resource id
     * for finally  (3) check if there is only there are one Hierarchy Parent
     * @param resource   ResourceClass with the resource to check
     */
    private boolean resourceChecker(Resource resource) {
        List<String> listMessages = new LinkedList<>();
        if (resource.getHierarchyParent().contains("None")) {
            listMessages.add(String.format("The Hierarchy Parent Attribute in resource %s%s",resource.getResourceID(), CHORUS_ATTRIBUTES));
        }
        if (resource.getReplaceable().size() == 1 && resource.getReplaceable().get(0).equals("None")) {
            listMessages.add(String.format("The Replaceable Attribute in resource %s%s",resource.getResourceID(), CHORUS_ATTRIBUTES));
        }
        if (resource.getResourceID().equals("None")) {
            listMessages.add(String.format("The ResourceID Attribute in the res:%s %s", resource.getResourceID(),CHORUS_ATTRIBUTES));
        }
        if (resource.getHierarchyParent().size() > 1) {
            listMessages.add(String.format("There are more than 2 HierarchyParents in resource %s and is mandatory provide " +
                    "only one ",resource.getResourceID()));
        }
        if(listMessages.isEmpty()) log.debug("No errors produced during the Resource checking");
        else{for (String message : listMessages) {log.error(message);}}

        return elasticityChecker(resource) && listMessages.isEmpty();
    }

    /**
     * Support method that checks the elasticity Model of the resource. It checks : (1) If the Resource omits one
     * of the attributes : id,elasticity or cost, (2) If the elasticity or the cost is invalid (negative)
     * @param resource   Resource  with all the attributes
     */
    private boolean elasticityChecker(Resource resource) {
        boolean isOutElasticityID = resource.getElasticityModel().getElasticityID().equals("None");
        boolean isOutElasticity = resource.getElasticityModel().getElasticity() == -1;
        boolean isOutElasticityCost = resource.getElasticityModel().getElasticityCost() == -1;
        List<String> listMessages = new LinkedList<>();
        if (isOutElasticity && isOutElasticityCost && isOutElasticityID) {
            listMessages.add(String.format("No Elasticity Annotation Specified in the resource %s", resource.getResourceID()));
            return false;
        }
        if (isOutElasticityID) {
            listMessages.add(String.format("The ElasticityId Attribute in the resource %s %s", resource.getResourceID(), CHORUS_ATTRIBUTES));
        }
        if (isOutElasticity) {
            listMessages.add(String.format("The Elasticity Attribute in the resource %s%s",resource.getResourceID(), CHORUS_ATTRIBUTES));
        } else {
            if (resource.getElasticityModel().getElasticity() <= 0) {
                listMessages.add(String.format("The Specified Elasticity  in resource %s is not valid", resource.getResourceID()));
            }
        }
        if (isOutElasticityCost) {
            listMessages.add(String.format("The Elasticity Cost Attribute in resource  %s%s", resource.getResourceID(), CHORUS_ATTRIBUTES));
        } else {
            if (resource.getElasticityModel().getElasticityCost() < 0) {
                listMessages.add(String.format("The Specified Elasticity Cost in resource %s is not valid", resource.getResourceID()));
            }
        }
        if(listMessages.isEmpty()) log.debug("No errors produced during the ElasticityMode checking");
        else{for (String message : listMessages) {log.error(message);}}

        return listMessages.isEmpty();
    }

    /**
     * Support method that checks the Access mode of the resource. It checks : (1) If the Tag doesn't have
     * one of the attributes: IdResource, concurrency,Access Mode Type or sharing,
     * (2) If the sharing attribute its contradictory  with the concurrency
     * @param accessModeTag AccessMode tag
     * @param methodName    String with the method name
     */
    private boolean accessModeTagChecker(giis.retorch.annotations.AccessMode accessModeTag, String methodName) {
        List<String> listMessages = new LinkedList<>();

        checkConcurrency(accessModeTag, methodName, listMessages);
        if ("None".equals(accessModeTag.resID())) {
            listMessages.add(String.format("The ResourceID Attribute in method %s%s", methodName, CHORUS_ATTRIBUTES));
        }
        checkAccessModeType(accessModeTag, methodName, listMessages);

        if (listMessages.isEmpty()) {
            log.debug("No errors produced during the AccessModeTag checking");
        } else {
            for (String message: listMessages) {log.error(message);}
        }

        return listMessages.isEmpty();
    }

    private void checkConcurrency(giis.retorch.annotations.AccessMode accessModeTag, String methodName, List<String> listMessages) {
        if (accessModeTag.concurrency() == -1) {
            listMessages.add(String.format("The Concurrency Attribute in method %s%s", methodName, CHORUS_ATTRIBUTES));
        }

        if (accessModeTag.sharing() && accessModeTag.concurrency() < 2) {
            listMessages.add(String.format("Invalid Concurrency in method %s: shareable resource with concurrency < 2", methodName));
        } else if (!accessModeTag.sharing() && accessModeTag.concurrency() > 1) {
            listMessages.add(String.format("Invalid Concurrency in method %s: non-shareable resource with concurrency > 1", methodName));
        }
    }

    private void checkAccessModeType(giis.retorch.annotations.AccessMode accessModeTag, String methodName, List<String> listMessages) {
        if (!AccessModeTypes.isValidAccessMode(accessModeTag.accessMode())) {
            String message = accessModeTag.accessMode().equals("NOASSIGNED")
                    ? String.format("The AccessModeType in method %s is not specified", methodName)
                    : String.format("The AccessModeType in method %s%s", methodName, NO_EXIST);
            listMessages.add(message);
        }
    }

    /**
     * Support method that validates the System provided as parameter. It checks  that the replaceable resources exists
     * @param systemToCheck System to check tag
     */
    private System checkSystemClass(System systemToCheck) {
        System systemOutput = new System(systemToCheck.getName());
        List<String> idResourcesList = listAllResources.values().stream()
                .map(Resource::getResourceID)
                .toList();
        systemToCheck.getResources().stream()
                .filter(res -> idResourcesList.containsAll(res.getReplaceable()))
                .forEach(systemOutput::addResourceClass);
        systemToCheck.getResources().stream()
                .filter(res -> !idResourcesList.containsAll(res.getReplaceable()))
                .forEach(res -> log.error("The Replaceable of resource {}{}", res.getResourceID(), NO_EXIST));
        systemOutput.addListTestCases(systemToCheck.getTestCases());

        return systemOutput;
    }
}