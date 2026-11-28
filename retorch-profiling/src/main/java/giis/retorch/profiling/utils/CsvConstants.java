/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.utils;

/**
 * The {@code CsvConstants} class centralizes the column names of the usage file exported by
 * the orchestration scripts. They must match what {@code resourcemonitor.sh export} writes.
 */
public final class CsvConstants {

    private CsvConstants() {}

    public static final String TJOB_HEADER = "tjobname";

    /** Delimiter used in CSV files. */
    public static final String CSV_DELIMITER = ";";
}
