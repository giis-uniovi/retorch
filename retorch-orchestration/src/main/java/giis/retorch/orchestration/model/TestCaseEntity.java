package giis.retorch.orchestration.model;

import java.util.LinkedList;
import java.util.List;

public class TestCaseEntity {

    private final String name;
    private final Class<?> testClass;
    private List<AccessModeEntity> accessMode;  //List with the access modes (one access mode for each resource)

    /**
     * RETORCH Test Case has the test name, its class and the access mode that performs over the resource
     * @param nameTestCase  String with the test case name
     * @param classTestCase Class that contains the test case
     */
    public TestCaseEntity(String nameTestCase, Class<?> classTestCase) {
        this.name = nameTestCase;
        this.testClass = classTestCase;
        this.accessMode = new LinkedList<>();
    }

    public Class<?> getTestClass() {
        return testClass;
    }
    public void addAccessMode(AccessModeEntity accessMode) {
        this.accessMode.add(accessMode);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null) || (!obj.getClass().equals(this.getClass()))) return false;

        TestCaseEntity objectToCompare = ((TestCaseEntity) obj);
        boolean conditionOne = objectToCompare.getAccessMode().equals(this.accessMode);
        boolean conditionTwo = objectToCompare.getName().equals(this.name);
        return conditionOne && conditionTwo;
    }

    @Override
    public String toString() {
        return "tc{" + "'" + name + '\'' + ", " + accessMode + '}';
    }

    public List<AccessModeEntity> getAccessMode() {
        return accessMode;
    }
    public String getName() {
        return name;
    }

    public void setAccessMode(List<AccessModeEntity> accessMode) {
        this.accessMode = accessMode;
    }

}