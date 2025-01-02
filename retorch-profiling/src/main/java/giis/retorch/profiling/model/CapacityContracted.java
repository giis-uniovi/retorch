package giis.retorch.profiling.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import giis.retorch.orchestration.model.Capacity;

/**
 * The {@code Capacity} class represents a capacity of with a name and a quantity.
 * It provides methods to add to the quantity and to get and set the name and quantity. The capacity names that
 * are supported are specified in {@code CapacityTypes}: memory, processor,slots and storage.
 * If the provided name is not in the list of valid capacities, it defaults to "Wrong Capacity".
 */
public class CapacityContracted extends Capacity {

    private double granularity;

    public CapacityContracted(@JsonProperty("capacityName")String name, @JsonProperty("quantity")double quantity, @JsonProperty("granularity")double granularity) {
    super(name,quantity);
        this.granularity=granularity;
    }

    public double getGranularity() {return granularity;}
    public void setGranularity(double granularity) {this.granularity = granularity;}

    @Override
    public int hashCode() {
        return Objects.hash(this.getName(),this.getQuantity(),this.getQuantity());
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapacityContracted that = (CapacityContracted) o;
        return Objects.equals(this.getName(), that.getName()) && Objects.equals(this.getQuantity(),
                that.getQuantity()) && Objects.equals(this.granularity, that.getGranularity());
    }


}