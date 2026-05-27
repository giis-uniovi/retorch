package giis.retorch.profiling.profilegeneration;

import org.apache.commons.csv.CSVRecord;

import java.util.List;

/**
 * Carries the parsed CSV state — full record list, headers, and the rows aggregated under TOTAL.
 */
public class CsvSnapshot {
    final List<CSVRecord> allRecords;
    final String[] headerNames;
    final List<CSVRecord> totalsRecords;

    CsvSnapshot(List<CSVRecord> all, String[] headers, List<CSVRecord> totals) {
        this.allRecords = all;
        this.headerNames = headers;
        this.totalsRecords = totals;
    }
}
