package giis.retorch.orchestration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The {@code Capacity} class represents a capacity of with a name and a quantity.
 * It provides methods to add to the quantity and to get and set the name and quantity. The capacity names that
 * are supported are specified in {@code CapacityTypes}: memory, processor,slots and storage.
 * If the provided name is not in the list of valid capacities, it defaults to "Wrong Capacity".
 */
public class CapacityEntity {

    private static final Logger log = LoggerFactory.getLogger(CapacityEntity.class);
    public static final String MEMORY_NAME = "memory";
    public static final String PROCESSOR_NAME = "processor";
    public static final String SLOTS_NAME = "slots";
    public static final String STORAGE_NAME = "storage";

    private String name;
    private double quantity;

    protected static final Set<String> LIST_CAPACITIES = new HashSet<>();
    static {Collections.addAll(LIST_CAPACITIES, MEMORY_NAME, PROCESSOR_NAME, SLOTS_NAME, STORAGE_NAME);}

    public CapacityEntity(@JsonProperty("name") String name, @JsonProperty("quantity") double quantity) {
        if (LIST_CAPACITIES.contains(name)) {
            setName(name);
        } else {
            log.error("The capacity: {} is not valid", name);
            setName("Wrong Capacity");
        }
        setQuantity(quantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, quantity);
    }

    @Override
    public String toString() {
        return "{"+"name:"+ getName() + ", "+"quantity:" + getQuantity() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapacityEntity that = (CapacityEntity) o;
        return Objects.equals(this.getName(), that.getName()) && Objects.equals(this.getQuantity(), that.getQuantity());
    }

    public String getName() {
        return name;
    }
    public double getQuantity() {return quantity;}
    public static Set<String> getCapacityNames(){return LIST_CAPACITIES;}

    public void setName(String name) {
        this.name = name;
    }
    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public void addQuantity(double number) {
        this.quantity += number;
    }
}