package giis.retorch.profiling.datasetgeneration;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;

import static giis.retorch.profiling.utils.CsvConstants.COI_SETUP_LABEL;
import static giis.retorch.profiling.utils.CsvConstants.COI_TEARDOWN_LABEL;
import static giis.retorch.profiling.utils.CsvConstants.CSV_DELIMITER;
import static giis.retorch.profiling.utils.CsvConstants.END_SUFFIX;
import static giis.retorch.profiling.utils.CsvConstants.START_SUFFIX;
import static giis.retorch.profiling.utils.CsvConstants.TJOB_SETUP_LABEL;
import static giis.retorch.profiling.utils.CsvConstants.TJOB_TEARDOWN_LABEL;
import static giis.retorch.profiling.utils.CsvConstants.avgCsvHeaders;

/**
 * Reads an average-duration CSV (the output of {@code DatasetGenerator.createCSVAvgFromListDataTuplesOrdered})
 * and derives the consolidated lifecycle boundary timestamps used by downstream COI overlays.
 */
public final class LifecycleTimesReader {

    private LifecycleTimesReader() {}

    /**
     * Returns the min/max boundaries across all rows of {@code avgCsvPath}:
     * earliest {@code COI-setup} start, latest {@code COI-setup} end, earliest {@code tjob-setup} start,
     * latest {@code tjob-teardown} end, earliest {@code coi-teardown} start, and latest {@code coi-teardown} end.
     */
    public static LifecycleTimes readFromAverageCsv(String avgCsvPath) throws IOException {
        double endSetUp = 0.0;
        double startTJobExec = Double.MAX_VALUE;
        double endTJobExec = 0.0;
        double startTearDown = Double.MAX_VALUE;
        double endTearDown = 0.0;
        try (FileReader reader = new FileReader(avgCsvPath)) {
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(avgCsvHeaders()).setDelimiter(CSV_DELIMITER).setSkipHeaderRecord(true).build();
            for (CSVRecord row : csvFormat.parse(reader)) {
                endSetUp      = Math.max(endSetUp,      Double.parseDouble(row.get(COI_SETUP_LABEL + END_SUFFIX)));
                startTJobExec = Math.min(startTJobExec, Double.parseDouble(row.get(TJOB_SETUP_LABEL + START_SUFFIX)));
                endTJobExec   = Math.max(endTJobExec,   Double.parseDouble(row.get(TJOB_TEARDOWN_LABEL + END_SUFFIX)));
                startTearDown = Math.min(startTearDown, Double.parseDouble(row.get(COI_TEARDOWN_LABEL + START_SUFFIX)));
                endTearDown   = Math.max(endTearDown,   Double.parseDouble(row.get(COI_TEARDOWN_LABEL + END_SUFFIX)));
            }
        }
        return new LifecycleTimes(
                0.0,
                endSetUp,
                startTJobExec == Double.MAX_VALUE ? 0.0 : startTJobExec,
                endTJobExec,
                startTearDown == Double.MAX_VALUE ? 0.0 : startTearDown,
                endTearDown);
    }
}
