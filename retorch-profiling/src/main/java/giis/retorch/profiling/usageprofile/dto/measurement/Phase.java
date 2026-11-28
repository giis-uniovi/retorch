/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto.measurement;

import giis.retorch.profiling.usageprofile.dto.cost.CostItemDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lifecycle phases a cost or a load can belong to, in display order. The label is the exact string written in the
 * usage file and in the JSON of the page ({@code CostItemDTO.phase}). {@link #OVERSUBSCRIPTION} is not a lifecycle phase
 * but the capacity paid and not used, shown as one more phase.
 */
public enum Phase {
    COI_SETUP("COI-setup", true),
    TJOB_SETUP("tjob-setup", false),
    TJOB_TESTEXEC("tjob-testexec", false),
    TJOB_TEARDOWN("tjob-teardown", false),
    COI_TEARDOWN("coi-teardown", true),
    OVERSUBSCRIPTION("oversubscription", false);

    private final String label;
    private final boolean coi;

    Phase(String label, boolean coi) {
        this.label = label;
        this.coi = coi;
    }

    /** The string used in the usage file and in the JSON. */
    public String label() {
        return label;
    }

    /** Position in the display order (0 based). */
    public int order() {
        return ordinal();
    }

    /** True for the phases of the COI lifecycle (set-up and tear-down of the infrastructure). */
    public boolean isCoi() {
        return coi;
    }

    /** The phase with this label, or null if there is none. */
    public static Phase fromLabel(String label) {
        for (Phase p : values()) {
            if (p.label.equals(label)) {
                return p;
            }
        }
        return null;
    }

    /** The labels of all the phases in display order. */
    public static List<String> labels() {
        List<String> labels = new ArrayList<>();
        for (Phase p : values()) {
            labels.add(p.label);
        }
        return Collections.unmodifiableList(labels);
    }
}
