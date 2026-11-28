/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One second of the execution: the COI phase (or null) and the loads of the TJobs running at that moment. Immutable. */
public final class Slice {
    public final String coiPhase;
    public final List<Load> loads;

    Slice(String coiPhase, List<Load> loads) {
        this.coiPhase = coiPhase;
        this.loads = Collections.unmodifiableList(new ArrayList<>(loads));
    }
}
