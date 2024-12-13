package giis.retorch.orchestration.model;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * The {@code TGroup} class represents a set of  {@code TestCase}s that make a compatible use of {@code Resources}s
 * that can be executed together.
 */
public class TGroup {

    private final List<TestCase> testCases;
    private final List<Resource> resources;

    public TGroup() {
        this.testCases = new LinkedList<>();
        this.resources = new LinkedList<>();
    }

    public TGroup(List<TestCase> testCases, List<Resource> resources) {
        testCases.sort(Comparator.comparing(TestCase::getName));
        this.testCases = testCases;
        this.resources = resources;
    }

    /**
     * Add a {@code TestCase} to the current  {@code TGroup} and sort the list of  {@code TestCase} with the newest one.
     * This sorting is performed to avoid the undesirable effect with the comparator and have in all machines the
     * same toString representation.
     * @param member  {@code TestCase} to add.
     */
    public void addTestCase(TestCase member) {
        this.testCases.add(member);
        this.testCases.sort(Comparator.comparing(TestCase::getName));
    }

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