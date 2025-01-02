package giis.retorch.profiling.model;


import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.Capacity;

import java.util.List;

/**
 * The {@code ResourceInstance} class represents a resource with a name and a list of {@code Capacity} that requires
 * for its instantiation.This class is used during the profile generation to calculate the overall {@code Capacity} that
 * are used-required.
 * It provides methods to get and set the name and capacities, and overrides the {@code equals}, {@code hashCode},
 * and {@code toString} methods for comparison and representation purposes taking into account the list of capacities.
 */
public class ResourceInstance extends Resource {


    public ResourceInstance(String name, List<Capacity> capacities) {
        this.setMinimalCapacities(capacities);
        this.setResourceID(name);
    }

}