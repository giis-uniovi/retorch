package giis.retorch.profiling.model;


import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.Capacity;

import java.util.List;

/**
 * The {@code ResourceInstance} class represents a {@code Resource} with a name and a list of {@code Capacity} that requires
 * for its instantiation.This class is used during the Usage Profile generation to calculate the overall {@code Capacity} that
 * are used-required.
 */
public class ResourceInstance extends Resource {

    public ResourceInstance(String name, List<Capacity> capacities) {
        this.setMinimalCapacities(capacities);
        this.setResourceID(name);
    }

}