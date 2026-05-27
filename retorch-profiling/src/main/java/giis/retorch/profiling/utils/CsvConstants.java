package giis.retorch.profiling.utils;

/**
 * The {@code CsvConstants} class centralizes the CSV header names and lifecycle phase labels shared across the
 * {@code DatasetGenerator}, {@code ProfileGenerator} and {@code ProfilePlotter} components.
 */
public final class CsvConstants {

    private CsvConstants() {}

    public static final String TJOB_HEADER       = "tjobname";
    public static final String STAGE_HEADER      = "stage";
    public static final String CAPACITY_HEADER   = "capacity";
    public static final String LIFECYCLE_HEADER  = "lifecyclephase";
    public static final String PLAN_HEADER       = "executionplan";
    public static final String AGGREGATION_VALUE = "TOTAL";

    public static final String COI_SETUP_LABEL      = "COI-setup";
    public static final String TJOB_SETUP_LABEL     = "tjob-setup";
    public static final String TJOB_TEST_EXEC_LABEL = "tjob-testexec";
    public static final String TJOB_TEARDOWN_LABEL  = "tjob-teardown";
    public static final String COI_TEARDOWN_LABEL   = "coi-teardown";

    public static final String START_SUFFIX = "-start";
    public static final String END_SUFFIX   = "-end";

    /** Delimiter used in CSV files. */
    public static final String CSV_DELIMITER = ";";

    /** Number of fixed header columns (executionplan, tjobname, lifecyclephase, capacity) before time-series data. */
    public static final int CSV_DATA_START_COLUMN = 4;

    private static final String[] AVG_CSV_HEADERS = {
            TJOB_HEADER, STAGE_HEADER,
            COI_SETUP_LABEL + START_SUFFIX,      COI_SETUP_LABEL + END_SUFFIX,
            TJOB_SETUP_LABEL + START_SUFFIX,     TJOB_SETUP_LABEL + END_SUFFIX,
            TJOB_TEST_EXEC_LABEL + START_SUFFIX, TJOB_TEST_EXEC_LABEL + END_SUFFIX,
            TJOB_TEARDOWN_LABEL + START_SUFFIX,  TJOB_TEARDOWN_LABEL + END_SUFFIX,
            COI_TEARDOWN_LABEL + START_SUFFIX,   COI_TEARDOWN_LABEL + END_SUFFIX
    };

    private static final String[] RAW_PROFILE_FIXED_HEADERS = {
            PLAN_HEADER, TJOB_HEADER, LIFECYCLE_HEADER, CAPACITY_HEADER
    };

    /** Returns a defensive copy of the headers of the average lifecycle duration CSV. */
    public static String[] avgCsvHeaders() {
        return AVG_CSV_HEADERS.clone();
    }

    /** Returns a defensive copy of the fixed columns that precede the time-series in a raw profile CSV. */
    public static String[] rawProfileFixedHeaders() {
        return RAW_PROFILE_FIXED_HEADERS.clone();
    }
}
