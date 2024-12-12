package giis.retorch.orchestration.model;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * RETORCH TGroup contains test cases that have a compatible resource usage and the resources that they require
 */
public class TGroup {

    private final List<TestCase> testCases;
    private final List<Resource> resources;

    /**
     * Default Constructor on which the tGroup is initialized empty and fulfill of test cases.
     */
    public TGroup() {
        this.testCases = new LinkedList<>();
        this.resources = new LinkedList<>();
    }

    /**
     * Default Constructor on which the tGroup is initialized empty and fulfill of test cases.
     */
    public TGroup(List<TestCase> testCases, List<Resource> resources) {
        testCases.sort(Comparator.comparing(TestCase::getName));
        this.testCases = testCases;
        this.resources = resources;


    }
    /**
     * This method add a test case to the current tGroup and sort the list of test cases with the newest one.
     * This sorting is performed to avoid the undesirable effect with the comparator and have in all machines the
     * same toString representation.
     */
    public void addTestCase(TestCase member) {
        this.testCases.add(member);
        this.testCases.sort(Comparator.comparing(TestCase::getName));
    }
    /**
     * Method that add a resource to the given TGroup
     * @param res ResourceClass with the resource to add
     */
    public void addResource(Resource res) {
        this.resources.add(res);
    }
    @Override
    public int hashCode() {
        return super.hashCode();
    }
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!obj.getClass().equals(this.getClass())) return false;
        TGroup currentType = ((TGroup) obj);
        boolean containsAllTestCases = this.testCases.containsAll(currentType.getTGroupTestCases());
        boolean containsAllResources = this.resources.containsAll(currentType.getTGroupResources());
        return containsAllTestCases && containsAllResources;
    }
    public List<TestCase> getTGroupTestCases() {
        return testCases;
    }
    public List<Resource> getTGroupResources() {
        return resources;
    }
    /**
     * Method used to debug the code, serialize the TGroups allowing to see with the debugger the resources
     * and the test cases that are included into the TGroup
     */
    @Override
    public String toString() {
        StringBuilder strBuilder = new StringBuilder();

        strBuilder.append("tGroup: [\nResources{\n");
        for (Resource res : resources) {
            strBuilder.append(res.getResourceID());
            strBuilder.append("\n ");
        }
        strBuilder.append("},\ntestCases{\n");
        for (TestCase tc : testCases) {
            strBuilder.append(tc.getName());
            strBuilder.append(" \n");
        }
        strBuilder.append("} ]\n");

        return strBuilder.toString();
    }
}