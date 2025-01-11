package giis.retorch.orchestration.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * The {@code System} class represents the whole RETORCH System composed by the Lists of {@code Resources} and {@code TestCase}
 */
public class System {

    private static final Logger logSystemClass = LoggerFactory.getLogger(System.class);

    private final String name;
    private final List<Resource> resources;
    private final List<TestCase> testCases;

    public System(String name) {
        this.name = name;
        this.testCases = new LinkedList<>();
        this.resources = new LinkedList<>();
    }

    public System(String name, List<TestCase> testCases, List<Resource> resources) {
        this.name = name;
        resources.sort(Comparator.comparing(Resource::getResourceID));
        testCases.sort(Comparator.comparing(TestCase::getName));
        this.testCases = testCases;
        this.resources = resources;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null)||(!obj.getClass().equals(this.getClass()))) return false;
        System objectToCompare = ((System) obj);
        if (!objectToCompare.getName().equals(this.name)) return false;
        boolean containAllResources = new HashSet<>(objectToCompare.getResources()).containsAll(this.getResources());
        boolean containsAllTestCases = new HashSet<>(objectToCompare.getTestCases()).containsAll(this.getTestCases());
        return containAllResources && containsAllTestCases;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("System ID: ").append(getName()).append("\n");
        sb.append("Resources:\n");
        for (Resource res : getResources()){
            sb.append("\t").append(res).append("\n");
        }
        sb.append("TestCases:\n");
        for (TestCase tc : getTestCases()){
            sb.append("\t").append(tc).append("\n");
        }
        return sb.toString();
    }

    public String getName() {return name;}
    public List<Resource> getResources() {return resources;}
    public List<TestCase> getTestCases() {return testCases;}

    public void addTestCase(TestCase test) {
        this.testCases.add(test);
        this.testCases.sort(Comparator.comparing(TestCase::getName));
    }

    public void addListOfResources(List<Resource> listResources) {
        this.resources.addAll(listResources);
        this.resources.sort(Comparator.comparing(Resource::getResourceID));
    }

    public void addResourceClass(Resource resource) {
        if (!this.resources.contains(resource)) {
            this.resources.add(resource);
        } else logSystemClass.info("The resource {} is contained in the system", resource.getResourceID());
        this.resources.sort(Comparator.comparing(Resource::getResourceID));
    }

    public void addListTestCases(List<TestCase> listTestCases) {
        this.testCases.addAll(listTestCases);
        this.testCases.sort(Comparator.comparing(TestCase::getName));
    }

}