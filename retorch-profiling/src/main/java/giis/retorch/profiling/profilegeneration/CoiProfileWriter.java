package giis.retorch.profiling.profilegeneration;

import giis.retorch.profiling.model.CloudObjectInstance;
import giis.retorch.profiling.model.ContractedCapacity;
import giis.retorch.profiling.utils.FileUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.NonNull;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static giis.retorch.profiling.utils.CsvConstants.AGGREGATION_VALUE;
import static giis.retorch.profiling.utils.CsvConstants.CAPACITY_HEADER;
import static giis.retorch.profiling.utils.CsvConstants.CSV_DATA_START_COLUMN;
import static giis.retorch.profiling.utils.CsvConstants.CSV_DELIMITER;
import static giis.retorch.profiling.utils.CsvConstants.PLAN_HEADER;
import static giis.retorch.profiling.utils.CsvConstants.TJOB_HEADER;

/**
 * Reads a raw TJob usage profile CSV and overlays the {@code ContractedCapacity} per-COI, writing the resulting
 * {@code profile_<COI>.csv}.
 */
public final class CoiProfileWriter {

    /** Reads {@code inputPath}, computes the contracted overlay for {@code coi}, and writes {@code outputPath}. */
    public void write(String inputPath, String outputPath, CloudObjectInstance coi) throws IOException {
        CsvSnapshot snapshot = readAggregatedRows(inputPath);
        Map<String, List<String>> capacityValuesByName = aggregateByCapacity(snapshot.totalsRecords);
        Map<String, List<String>> contracted = computeContractedSeries(capacityValuesByName, coi);
        writeOverlay(snapshot, outputPath, contracted);
    }

    private CsvSnapshot readAggregatedRows(String inputPath) throws IOException {
        try (FileReader fileReader = new FileReader(inputPath)) {
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader().setDelimiter(CSV_DELIMITER).setSkipHeaderRecord(true).build();
            List<CSVRecord> all = csvFormat.parse(fileReader).getRecords();
            String[] headers = all.isEmpty()
                    ? new String[0]
                    : all.get(0).getParser().getHeaderNames().toArray(new String[0]);
            List<CSVRecord> totals = all.stream()
                    .filter(r -> r.get(TJOB_HEADER).equals(AGGREGATION_VALUE))
                    .collect(Collectors.toList());
            return new CsvSnapshot(all, headers, totals);
        } catch (IOException e) {
            throw new IOException("Failed to find the profile file while creating the COI: " + inputPath, e);
        }
    }

    private Map<String, List<String>> aggregateByCapacity(List<CSVRecord> totalsRecords) {
        Map<String, List<String>> aggregated = new TreeMap<>();
        for (CSVRecord row : totalsRecords) {
            List<String> values = new ArrayList<>();
            for (int i = CSV_DATA_START_COLUMN; i < row.size(); i++) {
                values.add(row.get(i));
            }
            aggregated.merge(row.get(CAPACITY_HEADER), values, CoiProfileWriter::sumRows);
        }
        return aggregated;
    }

    private Map<String, List<String>> computeContractedSeries(Map<String, List<String>> requiredByCapacity, CloudObjectInstance coi) {
        Map<String, ContractedCapacity> coiCapacities = coi.getContractedCapacities();
        int billingTimePeriod = coi.getBillingOption().getTimePeriod();
        Map<String, List<String>> contracted = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : requiredByCapacity.entrySet()) {
            String capacityName = entry.getKey();
            ContractedCapacity capacity = coiCapacities.get(capacityName);
            if (capacity == null || capacity.getQuantity() <= 0 || capacity.getGranularity() <= 0) {
                continue;
            }
            List<String> series = getCapacitiesForBillingPeriod(entry, capacity, billingTimePeriod);
            contracted.put(capacityName, series);
        }
        return contracted;
    }

    private static @NonNull List<String> getCapacitiesForBillingPeriod(Map.Entry<String, List<String>> entry, ContractedCapacity capacity, int billingTimePeriod) {
        int slotCount = (int) Math.ceil(capacity.getQuantity() / capacity.getGranularity());
        CapacityGapTracker tracker = new CapacityGapTracker(slotCount, capacity.getGranularity(), billingTimePeriod);
        List<String> series = new ArrayList<>(entry.getValue().size());
        List<String> required = entry.getValue();
        for (int t = 0; t < required.size(); t++) {
            double demand = Double.parseDouble(required.get(t));
            int slotsUsed = (int) Math.ceil(demand / capacity.getGranularity());
            tracker.recordUsage(t, slotsUsed);
            series.add(String.format(Locale.ENGLISH, "%.1f", tracker.provisionedQuantity()));
        }
        return series;
    }

    private void writeOverlay(CsvSnapshot snapshot, String outputPath, Map<String, List<String>> contracted) throws IOException {
        FileUtils.ensureParentDir(outputPath);
        try (FileWriter out = new FileWriter(outputPath);
             CSVPrinter printer = new CSVPrinter(out,
                     CSVFormat.DEFAULT.builder().setHeader(snapshot.headerNames).setDelimiter(CSV_DELIMITER).build())) {
            String scheduling = "None";
            for (CSVRecord row : snapshot.allRecords) {
                printer.printRecord(row.stream().collect(Collectors.toList()));
                scheduling = row.get(PLAN_HEADER);
            }
            for (Map.Entry<String, List<String>> entry : contracted.entrySet()) {
                String[] header = {scheduling, AGGREGATION_VALUE, "CONTRACTED", entry.getKey()};
                printer.printRecord(ProfileGenerator.concatenateArrays(header, entry.getValue().toArray(new String[0])));
            }
        } catch (IOException e) {
            throw new IOException("The file :" + outputPath + "Cannot be opened");
        }
    }

    /** Element-wise sum of two same-sized numeric lists, formatted to one decimal. Used by {@code aggregateByCapacity}. */
    private static List<String> sumRows(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException("The arrays provided differ in size");
        }
        if (a.isEmpty()) return Collections.emptyList();
        List<String> sum = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            sum.add(String.format(Locale.ENGLISH, "%.1f",
                    Double.parseDouble(a.get(i)) + Double.parseDouble(b.get(i))));
        }
        return sum;
    }
}
