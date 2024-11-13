package giis.retorch.orchestration.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class SystemEntity {

    private static final Logger logSystemClass = LoggerFactory.getLogger(SystemEntity.class);

    private final String name;
    private final LinkedList<ResourceEntity> resources;
    private final List<TestCaseEntity> testCases;

    public SystemEntity(String name) {
        this.name = name;
        this.testCases = new LinkedList<>();
        this.resources = new LinkedList<>();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null)||(!obj.getClass().equals(this.getClass()))) return false;
        SystemEntity objectToCompare = ((SystemEntity) obj);
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
        for (ResourceEntity res : getResources()){
            sb.append("\t").append(res).append("\n");
        }
        sb.append("TestCases:\n");
        for (TestCaseEntity tc : getTestCases()){
            sb.append("\t").append(tc).append("\n");
        }
        return sb.toString();
    }

    public String getName() {return name;}
    public List<ResourceEntity> getResources() {return resources;}
    public List<TestCaseEntity> getTestCases() {return testCases;}

    public void addTestCase(TestCaseEntity test) {
        this.testCases.add(test);
        this.testCases.sort(Comparator.comparing(TestCaseEntity::getName));
    }

    public void addListOfResources(List<ResourceEntity> listResources) {
        this.resources.addAll(listResources);
        this.resources.sort(Comparator.comparing(ResourceEntity::getResourceID));
    }

    public void addResourceClass(ResourceEntity resource) {
        if (!this.resources.contains(resource)) {
            this.resources.add(resource);
        } else logSystemClass.info("The resource {} is contained in the system", resource.getResourceID());
        this.resources.sort(Comparator.comparing(ResourceEntity::getResourceID));
    }

    public void addListTestCases(List<TestCaseEntity> listTestCases) {
        this.testCases.addAll(listTestCases);
        this.testCases.sort(Comparator.comparing(TestCaseEntity::getName));
    }

}