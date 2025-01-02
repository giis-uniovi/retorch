package giis.retorch.profiling.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import giis.retorch.orchestration.model.Capacity;

/**
 * The {@code CapacityContracted} class extends the {@code Capacity} class adding the granularity required to
 * create the UsageProfile representations.
 */
public class ContractedCapacity extends Capacity {

    private double granularity;

    public ContractedCapacity(@JsonProperty("capacityName")String name, @JsonProperty("quantity")double quantity, @JsonProperty("granularity")double granularity) {
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
        ContractedCapacity that = (ContractedCapacity) o;
        return Objects.equals(this.getName(), that.getName()) && Objects.equals(this.getQuantity(),
                that.getQuantity()) && Objects.equals(this.granularity, that.getGranularity());
    }


}