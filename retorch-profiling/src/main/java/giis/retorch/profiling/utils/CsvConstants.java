/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.utils;

/**
 * The {@code CsvConstants} class centralizes the column names and lifecycle phase labels of the usage file exported by
 * the orchestration scripts. They must match what {@code resourcemonitor.sh export} writes.
 */
public final class CsvConstants {

    private CsvConstants() {}

    public static final String TJOB_HEADER = "tjobname";

    public static final String COI_SETUP_LABEL      = "COI-setup";
    public static final String TJOB_SETUP_LABEL     = "tjob-setup";
    public static final String TJOB_TEST_EXEC_LABEL = "tjob-testexec";
    public static final String TJOB_TEARDOWN_LABEL  = "tjob-teardown";
    public static final String COI_TEARDOWN_LABEL   = "coi-teardown";

    /** Delimiter used in CSV files. */
    public static final String CSV_DELIMITER = ";";
}
