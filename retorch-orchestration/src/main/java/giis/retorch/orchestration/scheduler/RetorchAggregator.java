package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.classifier.ResourceSerializer;
import giis.retorch.orchestration.model.*;

import giis.retorch.orchestration.model.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RetorchAggregator {

    private static final Logger logRetorchAggregator = LoggerFactory.getLogger(RetorchAggregator.class);
    final Marker errorMarker = MarkerFactory.getMarker("ErrorMarker");
    private final HashMap<String, TGroup> mapTGroups;
    private final System aggregatorSystem;
    private Map<String, Resource> mapAvailableResources;

    /**
     * This constructor validates the system provided to the Aggregator and deserializes the resources
     * required by the test cases.In order to make this deserialization we require a file with the resources
     * serialized in JSON, located in the folder resourceFiles and named as SystemName+"SystemResources.json"
     * @param system System provided as input that would be the basis for convert the system into  TGroups
     */
    public RetorchAggregator(System system) throws NotValidSystemException, IOException {
        this.aggregatorSystem = validateSystem(system) ? system : new System("EmptySystem ERROR");
        this.mapTGroups = new HashMap<>();
        if (validateSystem(system)) {
            ResourceSerializer serializer = new ResourceSerializer();
            this.mapAvailableResources = serializer.deserializeResources(system.getName());
        }
    }

    private List<TGroup> getListTGroups() {
        return new ArrayList<>(this.mapTGroups.values());
    }

    /**
     * Validation method that checks the system provided in order to check: the resource and test cases lists are not
     * empty, there aren't resources without
     * test case, all the test cases requirements are fulfilled and there are no duplicates in the lists
     * @param systemToCheck System object to check
     */

    public boolean validateSystem(System systemToCheck) throws NotValidSystemException {
        boolean areResourcesEmpty = true;
        boolean areTestCasesEmpty = true;
        boolean areAllResourcesRequiredByTestCase;
        boolean areAllResourcesFullFill;
        boolean thereAreNoDuplicates = true;
        String message;
        if (systemToCheck.getResources().isEmpty()) {
            message=String.format("The number of resources in the system %s is zero",
                    systemToCheck.getName());
            logRetorchAggregator.error(message);
            areResourcesEmpty = false;
        }
        if (systemToCheck.getTestCases().isEmpty()) {
            message=String.format("The number of test cases in the system %s is zero",
                    systemToCheck.getName());
            logRetorchAggregator.error(message);
            areTestCasesEmpty = false;
        }
        Set<String> duplicateTestCases = findDuplicates(systemToCheck.getTestCases());
        if (!duplicateTestCases.isEmpty()) {
            message=String.format("There are several repeated Test Cases in the System %s:%s",
                    systemToCheck.getName(), duplicateTestCases);
            logRetorchAggregator.error(message);
            thereAreNoDuplicates = false;
        }
        areAllResourcesRequiredByTestCase = isRequiredByATestCase(systemToCheck);
        areAllResourcesFullFill = isAllTestCasesFullFill(systemToCheck);

        return thereAreNoDuplicates && areResourcesEmpty && areAllResourcesFullFill && areTestCasesEmpty && areAllResourcesRequiredByTestCase;
    }

    /**
     * Support method that checks if there are any duplicates in the system
     */
    private Set<String> findDuplicates(Collection<TestCase> currentList) {
        // Initialize sets for unique and duplicate test cases
        Set<String> uniqueTestCases = new HashSet<>();
        Set<String> duplicateTestCases = new LinkedHashSet<>();

        for (TestCase testCase : currentList) { // Iterate over all test cases
            // If a test case is already in the unique set, add it to the duplicates set
            if (!uniqueTestCases.add(testCase.getName())) duplicateTestCases.add(testCase.getName());
        }
        // Return the set of duplicate test cases
        return duplicateTestCases;
    }

    /**
     * Support method that checks if there are any orphan resources (resources without test cases)
     * @param systemToCheck System on which there are the resources and test cases to validate
     */
    private boolean isRequiredByATestCase(System systemToCheck) {
        String message;

        // Get all resources in the system
        List<String> resourcesSystem =
                systemToCheck.getResources().stream().map(Resource::getResourceID).collect(Collectors.toList());
        // Iterate over all resources
        for (String resource : resourcesSystem) {
            // Assume the resource is not required by any test case
            boolean resourceRequired = false;
            // Iterate over all test cases
            for (TestCase testCase : systemToCheck.getTestCases()) {
                // Get all resources required by the test case
                List<String> resourcesAccessMode =
                        testCase.getAccessMode().stream().map(AccessMode::getResource).map(Resource::getResourceID).collect(Collectors.toList());
                // If the test case requires the resource, set resourceRequired to true and break out of the loop
                if (resourcesAccessMode.contains(resource)) {
                    resourceRequired = true;
                    break;
                }
            }
            if (!resourceRequired) { // If the resource is not required by any test case, log an error and return false
                message=String.format("Resource %s not required by any test case", resource);
                logRetorchAggregator.error(message);
                return false;
            }
        }
        return true; // If all resources are required by at least one test case, return true
    }

    /**
     * Supporting method that checks if all test cases have the required resources in the system*
     * @param systemToCheck System which remain the test cases to validate
     */
    private boolean isAllTestCasesFullFill(System systemToCheck) throws NotValidSystemException {
        // Iterate over all test cases
        for (TestCase tc : systemToCheck.getTestCases()) {
            // Check if access mode is null
            if (tc.getAccessMode() == null) {
                throw new NotValidSystemException("The number of test cases or resources of the current system is " + "zero");
            }
            // Get all resources required by the test case
            List<Resource> resourcesAccessMode =
                    tc.getAccessMode().stream().map(AccessMode::getResource).collect(Collectors.toList());
            // Check if all required resources exist in the system
            if (!systemToCheck.getResources().containsAll(resourcesAccessMode)) {
                String message=String.format("The test case  %s requires the resources: %s any of them " + "doesn't exist in the system", tc.getName(), resourcesAccessMode);
                logRetorchAggregator.error(message);
                return false;
            }
        }
        // If all test cases are fulfilled, return true
        return true;
    }

    /**
     * Method that adds a test case to the TGroups structure:  first checks if the current TGroup exist, retrieving
     * it from the structure, adding the new test case and putting it again.If the TGroup doesn't exist
     * then it creates the new TGroup, add the test case and put it in the correct position of the structure.
     * @param keyOfTGroupMap String that acts as keyOfTGroupMap (resources alphabetically ordered concatenated)
     * @param mapTGroups     Map with the tGroups that is intended to add the test cases
     * @param resources      List with the resources of this test case
     * @param testCaseToAdd  Test cases that is intended to be added
     */
    private void addTestCaseToaTGroupMap(String keyOfTGroupMap, TestCase testCaseToAdd,
                                         Map<String, TGroup> mapTGroups, List<Resource> resources) {
        TGroup tGroup = mapTGroups.getOrDefault(keyOfTGroupMap, new TGroup());
        tGroup.addTestCase(testCaseToAdd);
        if (!mapTGroups.containsKey(keyOfTGroupMap)) {
            for (Resource res : resources) {
                if(!tGroup.getTGroupResources().contains(res)) {tGroup.addResource(res);}
            }
        }
        mapTGroups.put(keyOfTGroupMap, tGroup);
    }

    /**
     * Generates a list of TGroupClass objects by iterating over the test cases and using the cartesian product over
     * its resources and replaceable.
     * The test cases are grouped according to a key that is generated with this cartesian product (the resource ids
     * in alphabetical order concatenated with some char) and put into
     * a TGroupClass that contains those resources.
     * @return A sorted list of TGroupClass objects.
     */
    public List<TGroup> generateTGroups() {
        this.aggregatorSystem.getTestCases().forEach(tc -> {
            List<Resource> idResourcesList =
                    tc.getAccessMode().stream().map(AccessMode::getResource).sorted(Comparator.comparing(Resource::getResourceID)).collect(Collectors.toList());
            List<List<Resource>> listAllResourcesRequired =
                    this.getResourceTuplesForCartesianProduct(idResourcesList);
            List<List<Resource>> cartesianProductCurrentResource = this.cartesianProduct(listAllResourcesRequired);
            cartesianProductCurrentResource.forEach(pairResources -> addTestCaseToaTGroup(tc, this.mapTGroups,
                    pairResources));
        });

        return getListTGroups().stream()
                .sorted(Comparator.comparing(TGroup::toString)).collect(Collectors.toList());
    }

    /**
     * Support method that create all the tuples (resource ids concatenated with some char) in order to perform over it
     * a cartesian product and obtain all feasible combinations of resources. Example:
     * TC 1 requires R1 and R2, that could be replaced by  R3 and R4 so, our method would return [[R3], [R2,R3,R4]] .
     * @param idResourcesList List with the resources to make the product
     */
    private List<List<Resource>> getResourceTuplesForCartesianProduct(List<Resource> idResourcesList) {
        List<List<Resource>> listAllResourcesRequired = new LinkedList<>();
        for (Resource resource : idResourcesList) {
            if (mapAvailableResources.containsKey(resource.getResourceID())) {
                LinkedList<Resource> currentList = new LinkedList<>();
                currentList.add(mapAvailableResources.get(resource.getResourceID()));
                resource.getReplaceable().stream().filter(mapAvailableResources::containsKey).map(mapAvailableResources::get).forEach(currentList::add);
                listAllResourcesRequired.add(currentList);
            } else {
                logRetorchAggregator.error(errorMarker, "The resource with id {} is not configured",
                        resource.getResourceID());
            }
        }
        return listAllResourcesRequired;
    }

    /**
     * Support method that allows us to make the cartesian product of one set of subsets. The solution is inspired
     * in the answer of the thread:
     * <a href="https://stackoverflow.com/questions/714108/cartesian-product-of-arbitrary-sets-in-java">...</a>
     * Makes a cartesian product, practical example : ( [m ,a] , [n,b]) -> (m-n,m-b,a-n,a-b)
     * @param lists Lists of Lists that are intended to make the subset operation
     */
    protected <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
        List<List<T>> resultLists = new LinkedList<>();
        if (lists.isEmpty()) {
            resultLists.add(new ArrayList<>());
        } else {
            List<T> firstList = lists.get(0);
            List<List<T>> remainingLists = cartesianProduct(lists.subList(1, lists.size()));
            for (T condition : firstList) {
                for (List<T> remainingList : remainingLists) {
                    List<T> resultList = new LinkedList<>();
                    resultList.add(condition);
                    resultList.addAll(remainingList);
                    resultLists.add(resultList);
                }
            }
        }
        return resultLists;
    }

    /**
     * Support method used to split the base TGroups according to the use of resources that the test cases do with it
     * . The method put the
     * particle READ at the end of the key if the test case makes safe and idempotent operations, or on the other
     * hand the WRITE word.
     * @param testCaseToAdd              Test case to add
     * @param resourcesRequired          Resources required by this test cases
     * @param tGroupsOrderedByAccessMode Map with the tGroups that has as key idResources alphabetically + AccessMode
     */
    public void addTestCaseToaTGroup(TestCase testCaseToAdd, Map<String, TGroup> tGroupsOrderedByAccessMode
            , List<Resource> resourcesRequired) {

        List<AccessModeTypes> accessModesTGroup =
                testCaseToAdd.getAccessMode().stream().map(AccessMode::getType).collect(Collectors.toList());
        if (accessModesTGroup.contains(new AccessModeTypes("READWRITE")) || accessModesTGroup.contains(new AccessModeTypes("WRITEONLY"))) {
            addTestCaseToaTGroupMap("WRITE-" + concatenatedResourceIdsToString(resourcesRequired), testCaseToAdd,
                    tGroupsOrderedByAccessMode, resourcesRequired);
        } else {
            addTestCaseToaTGroupMap("READ-" + concatenatedResourceIdsToString(resourcesRequired), testCaseToAdd,
                    tGroupsOrderedByAccessMode, resourcesRequired);
        }
    }

    /**
     * Support method used to order alphabetically and concatenate the resource IDs  generating the keys
     */
    private String concatenatedResourceIdsToString(List<Resource> resourcesRequired) {
        return resourcesRequired.stream().map(Resource::getResourceID).sorted().collect(Collectors.joining(" "));
    }
}