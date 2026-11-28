/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The vocabulary of the page JSON (the values {@code CostItem.phase}, {@code Alternative.kind} and {@code CostItem.object}
 * can take), so the scripts do not keep their own copy. Immutable.
 */
public final class Meta {
    /** Phase labels in display order. */
    public final List<String> phases;
    /** Alternative kinds. */
    public final List<String> kinds;
    /** Cloud objects that are billed. */
    public final List<String> objects;

    private Meta() {
        this.phases = Phase.labels();
        this.kinds = Collections.unmodifiableList(Arrays.asList(Alternative.KIND_VM, Alternative.KIND_CONTAINER,
                Alternative.KIND_BROWSER_SERVICE));
        this.objects = Collections.unmodifiableList(Arrays.asList(CostItem.OBJECT_COMPUTE, CostItem.OBJECT_STORAGE,
                CostItem.OBJECT_BROWSER));
    }

    /** The only vocabulary there is. */
    public static Meta standard() {
        return new Meta();
    }
}
