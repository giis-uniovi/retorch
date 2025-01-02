package giis.retorch.profiling.datasetgeneration;

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

/**
 * The {@code DatasetGenerator} class contains the necessary methods that enable the processing of the execution data
 * files in order to create the  average dataset with duration of each {@code TJob} and {@code CloudObject} lifecycle
 * phase
 */
public class DatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatasetGenerator.class);
    private static final String TJOB_HEADER = "tjobname";
    private static final String STAGE_HEADER = "stage";
    private static final String COI_SETUP_LABEL = "COI-setup";
    private static final String TJOB_SETUP_LABEL = "tjob-setup";
    private static final String TJOB_TEST_EXEC_LABEL = "tjob-testexec";
    private static final String TJOB_TEARDOWN_LABEL = "tjob-teardown";
    private static final String COI_TEARDOWN_LABEL = "coi-teardown";
    private static final String END_SUFIX = "-end";
    private static final String START_SUFIX = "-start";

    private static final List<String> LIFECYCLES = Arrays.asList(
            COI_SETUP_LABEL,
            TJOB_SETUP_LABEL,
            TJOB_TEST_EXEC_LABEL,
            TJOB_TEARDOWN_LABEL,
            COI_TEARDOWN_LABEL
    );


    /**
     * Generates a list of {@code DataTuple} representing average times of several CSV files located in a directory.
     * Each CSV file is expected to have data in the format defined by the provided headers.
     * @param path The path where CSV files are located.
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
        String[] tableHeaders = {TJOB_HEADER, STAGE_HEADER, COI_SETUP_LABEL + START_SUFIX,
                COI_SETUP_LABEL + END_SUFIX, TJOB_SETUP_LABEL + START_SUFIX,
                TJOB_SETUP_LABEL + END_SUFIX, TJOB_TEST_EXEC_LABEL + START_SUFIX, TJOB_TEST_EXEC_LABEL + END_SUFIX,
                TJOB_TEARDOWN_LABEL + START_SUFIX,
                TJOB_TEARDOWN_LABEL + END_SUFIX, COI_TEARDOWN_LABEL + START_SUFIX, COI_TEARDOWN_LABEL + END_SUFIX};
        // Parse each .csv file
        for (File file : csvFiles) {
            try (FileReader fileReader = new FileReader(file)) {
                CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                        .setHeader(tableHeaders).setDelimiter(";")
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
     * Finds all CSV files within the specified directory.
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
     * Given a csv csvRecord from a file generated in the CI system with the TJob and COI data, generates its
     * corresponding {@code DataTuple}
     *
     * @param csvRecord CSV record belonging to a TJob.
     * @return {@code DataTuple} with the csvRecord data.
     */
    private DataTuple generateDataTupleFromRecord(CSVRecord csvRecord) {
        DataTuple tuple = new DataTuple(csvRecord.get(TJOB_HEADER), Integer.parseInt(csvRecord.get(STAGE_HEADER)));
        for (String lifecycle : LIFECYCLES) {
            double start = Double.parseDouble(csvRecord.get(lifecycle + START_SUFIX));
            double end = Double.parseDouble(csvRecord.get(lifecycle + END_SUFIX));
            tuple.putLifeCycleDuration(lifecycle, end - start);
        }

        return tuple;
    }

    /**
     * Aggregates a list of {@code DataTuple} objects by merging their lifecycle durations
     * using {@link #mergeTuples(DataTuple, DataTuple)} method.
     *
     * @param allDataTuples the list of {@code DataTuple} objects to be aggregated
     * @return a map where the keys are {@code idTJob} values and the values are the aggregated {@code DataTuple}
     * objects
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
     * @param aggregatedTuples a map where the keys are {@code idTJob} values and the values are the aggregated
     *                         {@code DataTuple} objects
     * @param fileCount        the number of files used in the aggregation process
     * @return a list of {@code DataTuple} objects with averaged lifecycle durations
     */
    private List<DataTuple> calculateAverageDurations(Map<String, DataTuple> aggregatedTuples, int fileCount) {
        List<DataTuple> resultList = new ArrayList<>();
        aggregatedTuples.values().forEach(tuple -> {
            Map<String, Double> lifecycleDurations = tuple.getLifecycleDuration();
            lifecycleDurations.replaceAll((key, value) -> value / fileCount);
            tuple.setLifecycleDuration(lifecycleDurations);
            resultList.add(tuple);
        });
        return resultList;
    }

    /**
     * Merges the lifecycle durations of two {@code DataTuple} objects.
     * <p>For each key in the first tuple's lifecycle duration map, it adds the corresponding key value in the tuple's
     * map.
     * If a key from aggregatedTuple's map does not exist in the tuple's map, it will be added .
     * The result is stored in back into aggregatedTuple {@code DataTuple} and returned.
     *
     * @param aggregatedTuple the first {@code DataTuple} whose lifecycle durations will be merged and updated
     * @param tuple           the second {@code DataTuple} whose lifecycle durations will be used to update
     *                        aggregatedTuple
     * @return aggregatedTuple {@code DataTuple} with merged lifecycle durations
     */
    private DataTuple mergeTuples(DataTuple aggregatedTuple, DataTuple tuple) {
        Map<String, Double> durations1 = aggregatedTuple.getLifecycleDuration();
        Map<String, Double> durations2 = tuple.getLifecycleDuration();

        durations1.forEach((key, value) -> durations1.merge(key, durations2.get(key), Double::sum));
        aggregatedTuple.setLifecycleDuration(durations1);

        return aggregatedTuple;
    }

    /**
     * Generates and populates a CSV file with the average relative duration of all the {@code TJob} belonging to a
     * certain plan.
     * <p>Gets as input an ordered list of {@code DataTuple} which have the average duration of each {@code TJob} and
     * calculates the start of each Execution Plan stage. With the different starting points, intercalate 1 second
     * between the {@code TJob} and COI lifecycles in order to ease representation and visualization.
     *
     * @param listTuples The list of {@code DataTuple} containing time data.
     * @param outputPath The path where the generated CSV file will be saved.
     */
    public void createCSVAvgFromListDataTuplesOrdered(List<DataTuple> listTuples, String outputPath) {
        if (listTuples.isEmpty()) {
            log.error("No data tuples provided for CSV creation");
            return;
        }
        String[] headers = {
                TJOB_HEADER, STAGE_HEADER, COI_SETUP_LABEL + START_SUFIX, COI_SETUP_LABEL + END_SUFIX,
                TJOB_SETUP_LABEL + START_SUFIX, TJOB_SETUP_LABEL + END_SUFIX, TJOB_TEST_EXEC_LABEL + START_SUFIX,
                TJOB_TEST_EXEC_LABEL + END_SUFIX, TJOB_TEARDOWN_LABEL + START_SUFIX, TJOB_TEARDOWN_LABEL + END_SUFIX,
                COI_TEARDOWN_LABEL + START_SUFIX, COI_TEARDOWN_LABEL + END_SUFIX
        };
        Map<Integer, Double> startingStages = calculateStartingStages(listTuples);

        try (FileWriter out = new FileWriter(outputPath);
             CSVPrinter printer = new CSVPrinter(out,
                     CSVFormat.DEFAULT.builder().setHeader(headers).setDelimiter(";").build())) {
            for (DataTuple tuple : listTuples) {
                Map<String, Double> durations = tuple.getLifecycleDuration();
                Double stageStartTime = startingStages.get(tuple.getStage());
                Double lastJobEndTime = startingStages.get(Collections.max(startingStages.entrySet(),
                        Map.Entry.comparingByValue()).getKey());
                printer.printRecord(
                        tuple.getIdTJob(),
                        tuple.getStage(),
                        "0.0",
                        String.format(Locale.ENGLISH, "%.1f", durations.get(COI_SETUP_LABEL)),
                        String.format(Locale.ENGLISH, "%.1f", stageStartTime),
                        String.format(Locale.ENGLISH, "%.1f", stageStartTime + durations.get(TJOB_SETUP_LABEL)),
                        String.format(Locale.ENGLISH, "%.1f", stageStartTime + durations.get(TJOB_SETUP_LABEL) + 1),
                        String.format(Locale.ENGLISH, "%.1f",
                                stageStartTime + durations.get(TJOB_SETUP_LABEL) + 1 + durations.get(TJOB_TEST_EXEC_LABEL)),
                        String.format(Locale.ENGLISH, "%.1f",
                                stageStartTime + durations.get(TJOB_SETUP_LABEL) + 1 + durations.get(TJOB_TEST_EXEC_LABEL) + 1),
                        String.format(Locale.ENGLISH, "%.1f",
                                stageStartTime + durations.get(TJOB_SETUP_LABEL) + 1 + durations.get(TJOB_TEST_EXEC_LABEL) + 1 + durations.get(TJOB_TEARDOWN_LABEL)),
                        String.format(Locale.ENGLISH, "%.1f", lastJobEndTime),
                        String.format(Locale.ENGLISH, "%.1f", lastJobEndTime + durations.get(COI_TEARDOWN_LABEL)));
            }
        } catch (IOException e) {
            log.error("Error writing CSV file: {}", e.getMessage());
        }
    }

    /**
     * Calculates the starting relative time for each stage based on the provided list of {@code DataTuple} ordered.
     * This method considers the duration of the longer {@code TJob} of each stage and the extra seconds between the
     * different stages. Creates a list of starting times for each stage.
     *
     * @param listTuples The list of DataTuples containing lifecycle duration data.
     * @return A map containing starting stages for each stage.
     */
    private Map<Integer, Double> calculateStartingStages(List<DataTuple> listTuples) {
        Map<Integer, Double> startingStages = new HashMap<>();
        startingStages.put(0, listTuples.get(0).getLifecycleDuration().get(COI_SETUP_LABEL) + 1);

        for (DataTuple tuple : listTuples) {
            double durationCurrentTJob = tuple.getLifecycleDuration().get(TJOB_SETUP_LABEL) + 1 +
                    tuple.getLifecycleDuration().get(TJOB_TEST_EXEC_LABEL) + 1 +
                    tuple.getLifecycleDuration().get(TJOB_TEARDOWN_LABEL);
            startingStages.merge(tuple.getStage() + 1,
                    durationCurrentTJob + startingStages.get(tuple.getStage()) + 1,
                    Math::max);
        }

        return startingStages;
    }
}