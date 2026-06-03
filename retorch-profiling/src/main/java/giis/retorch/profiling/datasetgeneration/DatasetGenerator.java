package giis.retorch.profiling.datasetgeneration;

import giis.retorch.profiling.utils.FileUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static giis.retorch.profiling.utils.CsvConstants.*;

/**
 * The {@code DatasetGenerator} class contains the necessary methods that enable the processing of the execution data
 * files in order to create the  average dataset with duration of each {@code TJob} and {@code CloudObject} lifecycle
 * phases
 */
public class DatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatasetGenerator.class);

    private static final int STAGE_GAP_SECONDS = 1;

    private static final List<String> LIFECYCLES = Arrays.asList(
            COI_SETUP_LABEL,
            TJOB_SETUP_LABEL,
            TJOB_TEST_EXEC_LABEL,
            TJOB_TEARDOWN_LABEL,
            COI_TEARDOWN_LABEL
    );

    /**
     * Generates a list of {@code DataTuple} representing average times of the CSV files located in the directory
     * provided as parameter.
     * Each CSV file is expected to have data in the format defined by the provided headers.
     *
     * @param path The path where CSV file is located.
     * @return A list of DataTuples representing average times, sorted first by stage and second by TJob name.
     */
    public List<DataTuple> generateListTuplesAvgTimes(String path) {
        log.info("Generating the avg file using the .csv files from the path: {}", path);
        // Get all .csv files in the directory
        List<File> csvFiles = findCSVFiles(path);
        if (csvFiles.isEmpty()) {
            log.warn("No .csv files found in the path: {}", path);
            return Collections.emptyList();
        }

        List<DataTuple> allDataTuples = new ArrayList<>();
        // Parsing of the CSV file
        for (File file : csvFiles) {
            try (FileReader fileReader = new FileReader(file)) {
                CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                        .setHeader(avgCsvHeaders()).setDelimiter(CSV_DELIMITER)
                        .setSkipHeaderRecord(true)
                        .build();

                for (CSVRecord singleRecord : csvFormat.parse(fileReader)) {
                    allDataTuples.add(generateDataTupleFromRecord(singleRecord));
                }
            } catch (IOException e) {
                log.error("Error reading file {}: {}", file.getName(), e.getMessage());
            }
        }
        // Aggregate tuples
        Map<String, DataTuple> aggregatedTuples = aggregateTuples(allDataTuples);
        // Compute average durations
        List<DataTuple> avgTuples = calculateAverageDurations(aggregatedTuples, csvFiles.size());
        // Sort the tuples
        avgTuples.sort(Comparator.comparing(DataTuple::getStage).thenComparing(DataTuple::getIdTJob));
        log.info("Finished aggregating and calculating averages for all data");

        return avgTuples;
    }

    /**
     * Find and retrieve a list of files with all CSV files within the specified directory.
     *
     * @param directoryPath The path to the directory where CSV files are to be found.
     * @return A list of File objects representing the CSV found.
     */
    private static List<File> findCSVFiles(String directoryPath) {
        File directory = new File(directoryPath);

        if (!directory.isDirectory()) {
            log.warn("The provided path is not a directory: {}", directoryPath);
            return Collections.emptyList();
        }
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
        if (files == null) {
            log.warn("Error listing files in directory: {}", directoryPath);
            return Collections.emptyList();
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .collect(Collectors.toList());
    }

    /**
     * Given a csv {@code csvRecord} from a file generated in the CI system with the TJob and COI data, generates it's
     * corresponding {@code DataTuple}
     *
     * @param csvRecord {@code csvRecord} belonging to a TJob.
     * @return {@code DataTuple} with the csvRecord data.
     */
    private DataTuple generateDataTupleFromRecord(CSVRecord csvRecord) {
        DataTuple tuple = new DataTuple(csvRecord.get(TJOB_HEADER), Integer.parseInt(csvRecord.get(STAGE_HEADER)));
        for (String lifecycle : LIFECYCLES) {
            double start = Double.parseDouble(csvRecord.get(lifecycle + START_SUFFIX));
            double end = Double.parseDouble(csvRecord.get(lifecycle + END_SUFFIX));
            tuple.putLifeCycleDuration(lifecycle, end - start);
        }

        return tuple;
    }

    /**
     * Aggregates a list of {@code DataTuple} objects by merging their lifecycle durations
     * using {@link #mergeTuples(DataTuple, DataTuple)} method.
     *
     * @param allDataTuples the list of {@code DataTuple} objects to be aggregated
     * @return a map with keys {@code idTJob} values the aggregated {@code DataTuple}
     */
    private Map<String, DataTuple> aggregateTuples(List<DataTuple> allDataTuples) {
        Map<String, DataTuple> aggregatedTuples = new HashMap<>();
        for (DataTuple tuple : allDataTuples) {
            aggregatedTuples.merge(tuple.getIdTJob(), tuple, this::mergeTuples);
        }
        return aggregatedTuples;
    }

    /**
     * Calculates the average lifecycle durations for each {@code DataTuple} in the given map.
     * <p>This method takes a map of aggregated {@code DataTuple} objects and a number of files, then
     * calculates the average duration for each lifecycle event by dividing the aggregated duration by
     * the file count.
     *
     * @param aggregatedTuples a map with keys {@code idTJob} and values the aggregated {@code DataTuple}.
     * @param fileCount        the number of files used in the aggregation process
     * @return a list of {@code DataTuple} objects with averaged lifecycle durations
     */
    private List<DataTuple> calculateAverageDurations(Map<String, DataTuple> aggregatedTuples, int fileCount) {
        List<DataTuple> resultList = new ArrayList<>();
        aggregatedTuples.values().forEach(tuple -> {
            tuple.getLifecycleDuration().replaceAll((key, value) -> value / fileCount);
            resultList.add(tuple);
        });
        return resultList;
    }

    /**
     * Updates aggregatedTuple by merging it with another {@code DataTuple}'s lifecycle durations map. New keys are added,
     * and the modified aggregatedTuple {@code DataTuple} is returned.
     *
     * @param aggregatedTuple the first {@code DataTuple} whose lifecycle durations will be merged and updated
     * @param tuple           the second {@code DataTuple} whose lifecycle durations will be used to update
     *                        {@code aggregatedTuple}
     * @return aggregatedTuple {@code DataTuple} with merged lifecycle durations
     */
    private DataTuple mergeTuples(DataTuple aggregatedTuple, DataTuple tuple) {
        Map<String, Double> durations1 = aggregatedTuple.getLifecycleDuration();
        tuple.getLifecycleDuration().forEach((key, value) -> durations1.merge(key, value, Double::sum));
        return aggregatedTuple;
    }

    /**
     * Generates and populates a CSV file with the average relative duration of all the {@code TJob} belonging to a
     * certain plan.
     * <p>Gets as input an ordered list of {@code DataTuple} which have the average duration of each {@code TJob} and
     * calculates the start of each Execution Plan stage. With the different starting points, intercalate {@link #STAGE_GAP_SECONDS} seconds
     * between the {@code TJob} and COI lifecycles in order to ease representation and visualization.
     *
     * @param listTuples The list of {@code DataTuple} containing time data.
     * @param outputPath The path where the generated CSV file will be saved.
     */
    public void createCSVAvgFromListDataTuplesOrdered(List<DataTuple> listTuples, String outputPath) throws IOException {
        if (listTuples.isEmpty()) {
            log.error("No data tuples provided for CSV creation");
            return;
        }
        Map<Integer, Double> startingStages = calculateStartingStages(listTuples);
        double lastJobEndTime = Collections.max(startingStages.values());

        FileUtils.ensureParentDir(outputPath);
        try (FileWriter out = new FileWriter(outputPath);
             CSVPrinter printer = new CSVPrinter(out,
                     CSVFormat.DEFAULT.builder().setHeader(avgCsvHeaders()).setDelimiter(CSV_DELIMITER).build())) {
            for (DataTuple tuple : listTuples) {
                printer.printRecord(buildAvgCsvRow(tuple, startingStages.get(tuple.getStage()), lastJobEndTime));
            }
        } catch (IOException e) {
            throw new IOException("Error writing CSV file: " + outputPath, e);
        }
    }

    /**
     * Builds a single row of the avg-duration CSV. The 10 time values are produced by aggregating accumulatively and,
     * intercalating {@link #STAGE_GAP_SECONDS} between TJob lifecycle phases for visualization.
     */
    private static List<Object> buildAvgCsvRow(DataTuple tuple, double stageStartTime, double lastJobEndTime) {
        Map<String, Double> durations = tuple.getLifecycleDuration();
        double[] timeline = new double[10];
        timeline[0] = 0.0;                                                   // COI setup start
        timeline[1] = durations.get(COI_SETUP_LABEL);                        // COI setup end
        timeline[2] = stageStartTime;                                        // TJob setup start
        timeline[3] = timeline[2] + durations.get(TJOB_SETUP_LABEL);         // TJob setup end
        timeline[4] = timeline[3] + STAGE_GAP_SECONDS;                       // TJob test-exec start
        timeline[5] = timeline[4] + durations.get(TJOB_TEST_EXEC_LABEL);     // TJob test-exec end
        timeline[6] = timeline[5] + STAGE_GAP_SECONDS;                       // TJob teardown start
        timeline[7] = timeline[6] + durations.get(TJOB_TEARDOWN_LABEL);      // TJob teardown end
        timeline[8] = lastJobEndTime;                                        // COI teardown start
        timeline[9] = timeline[8] + durations.get(COI_TEARDOWN_LABEL);       // COI teardown end

        List<Object> row = new ArrayList<>(2 + timeline.length);
        row.add(tuple.getIdTJob());
        row.add(tuple.getStage());
        for (double t : timeline) {
            row.add(String.format(Locale.ENGLISH, "%.1f", t));
        }
        return row;
    }

    /**
     * Calculates the starting relative time for each stage based on the provided list of {@code DataTuple} ordered.
     * This method considers the duration of the longer {@code TJob} of each stage and the extra seconds between the
     * different stages, for creating a list of starting times for each stage.
     *
     * @param listTuples The list of {@code DataTuples} containing lifecycle duration data.
     * @return A map containing starting times for each stage.
     */
    private Map<Integer, Double> calculateStartingStages(List<DataTuple> listTuples) {
        if (listTuples == null || listTuples.isEmpty()) {
            throw new IllegalArgumentException("listTuples must not be null or empty");
        }
        Map<Integer, Double> startingStages = new HashMap<>();
        startingStages.put(0, listTuples.get(0).getLifecycleDuration().get(COI_SETUP_LABEL) + STAGE_GAP_SECONDS);

        for (DataTuple tuple : listTuples) {
            double durationCurrentTJob = tuple.getLifecycleDuration().get(TJOB_SETUP_LABEL) + STAGE_GAP_SECONDS +
                    tuple.getLifecycleDuration().get(TJOB_TEST_EXEC_LABEL) + STAGE_GAP_SECONDS +
                    tuple.getLifecycleDuration().get(TJOB_TEARDOWN_LABEL);
            startingStages.merge(tuple.getStage() + 1,
                    durationCurrentTJob + startingStages.get(tuple.getStage()) + STAGE_GAP_SECONDS,
                    Math::max);
        }

        return startingStages;
    }
}
