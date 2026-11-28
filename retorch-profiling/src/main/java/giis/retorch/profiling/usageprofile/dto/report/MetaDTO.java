/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto.report;

import giis.retorch.profiling.usageprofile.dto.cost.AlternativeDTO;
import giis.retorch.profiling.usageprofile.dto.cost.CostItemDTO;
import giis.retorch.profiling.usageprofile.dto.measurement.Phase;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The vocabulary of the page JSON (the values {@code CostItemDTO.phase}, {@code AlternativeDTO.kind} and {@code CostItemDTO.object}
 * can take), so the scripts do not keep their own copy. Immutable.
 */
public final class MetaDTO {
    /** Phase labels in display order. */
    public final List<String> phases;
    /** AlternativeDTO kinds. */
    public final List<String> kinds;
    /** Cloud objects that are billed. */
    public final List<String> objects;

    private MetaDTO() {
        this.phases = Phase.labels();
        this.kinds = Collections.unmodifiableList(Arrays.asList(AlternativeDTO.KIND_VM, AlternativeDTO.KIND_CONTAINER,
                AlternativeDTO.KIND_BROWSER_SERVICE));
        this.objects = Collections.unmodifiableList(Arrays.asList(CostItemDTO.OBJECT_COMPUTE, CostItemDTO.OBJECT_STORAGE,
                CostItemDTO.OBJECT_BROWSER));
    }

    /** The only vocabulary there is. */
    public static MetaDTO standard() {
        return new MetaDTO();
    }
}
