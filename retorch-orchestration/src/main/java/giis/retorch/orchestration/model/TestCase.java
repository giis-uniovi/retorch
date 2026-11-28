package giis.retorch.orchestration.model;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

/**
 * The {@code TestCase} class represents a test case that compose the End-to-End test suite. It has a name, its class
 * and the list of {@code AccessMode} performed over the different {@code Resource}
 */
public class TestCase {

    private final String name;
    private final Class<?> testClass;
    private List<AccessMode> accessMode;  //List with the access modes (one access mode for each resource)

    /**
     * RETORCH Test Case has the test name, its class and the access mode that performs over the resource
     * @param nameTestCase  String with the test case name
     * @param classTestCase Class that contains the test case
     */
    public TestCase(String nameTestCase, Class<?> classTestCase) {
        this.name = nameTestCase;
        this.testClass = classTestCase;
        this.accessMode = new LinkedList<>();
    }
    public TestCase(String nameTestCase, Class<?> classTestCase,List<AccessMode> accessMode) {
        this.name = nameTestCase;
        this.testClass = classTestCase;
        this.accessMode = accessMode;
    }

    public Class<?> getTestClass() {
        return testClass;
    }
    public void addAccessMode(AccessMode accessMode) {
        this.accessMode.add(accessMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, asSet(accessMode));
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null) || (!obj.getClass().equals(this.getClass()))) return false;

        TestCase objectToCompare = ((TestCase) obj);
        boolean conditionOne = asSet(objectToCompare.getAccessMode()).equals(asSet(this.accessMode));
        boolean conditionTwo = Objects.equals(objectToCompare.getName(), this.name);
        return conditionOne && conditionTwo;
    }

    private static Set<AccessMode> asSet(List<AccessMode> list) {
        return list == null ? Collections.<AccessMode>emptySet() : new HashSet<>(list);
    }

    @Override
    public String toString() {
        return "tc{" + "'" + name + '\'' + ", " + accessMode + '}';
    }

    public List<AccessMode> getAccessMode() {
        return accessMode;
    }
    public String getName() {
        return name;
    }

    public void setAccessMode(List<AccessMode> accessMode) {
        this.accessMode = accessMode;
    }

}