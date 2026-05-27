package giis.retorch.profiling.profilegeneration;


import giis.retorch.profiling.utils.FileUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.Capacity;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static giis.retorch.orchestration.model.Capacity.getCapacityNames;
import static giis.retorch.profiling.utils.CsvConstants.*;

/**
 * The {@code ProfileGenerator} class provides the necessary methods to generate the dataset with the use of
 * the {@code ContractedCapacity} by the different {@code ResourceInstances} of an {@code ExecutionPlan}
 * during a certain time window.
 * <p>
 * <strong>Internal API — call via {@code UsageProfilerToolBox}.</strong> This class is public only for
 * historic reasons; it is not part of the supported public surface.
 */
public class ProfileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProfileGenerator.class);

    private static final int EXECUTION_GAP_SECONDS = 5;

    /**
     * The {@code generateExecutionPlanCapacitiesUsage} method generates the comma-separated values file with the usage
     * of {@code ContractedCapacity}s done by the {@code ExecutionPlan}. Requires the input with the average dataset
     * created with the {@code DatasetGenerator} methods, an {@code ExecutionPlan}, the location of the output csv file
     * and the number of executions and the window time that desires to be considered.
     *
     * @param plan Execution Plan retrieved with the orchestration generator tool.
     * @param pathAvgDurationPlan String with the path of the average duration file.
     * @param outputPath  String with the path where the output csv file will be placed.
     * @param windowTime Window of time to be considered.
     * @param executions  Number of executions to be calculated.
     *
     */
    public void generateExecutionPlanCapacitiesUsage(ExecutionPlan plan, String pathAvgDurationPlan, String outputPath,
                                                     double windowTime, int executions) throws IOException {
        List<TJob> listTJobs = plan.gettJobClassList();
        listTJobs.sort(Comparator.comparing(TJob::getStage).thenComparing(TJob::getIdTJob));
        loadAvgLifecyclesTimeIntoTJob(listTJobs, pathAvgDurationPlan);
        double longerTJob = findLongerTJob(listTJobs);
        double requiredTime = executions * longerTJob + executions * EXECUTION_GAP_SECONDS;
        if (requiredTime > windowTime) {
            throw new IllegalArgumentException(String.format(
                    "Required time (%.1f) exceeds window (%.1f): executions=%d, longerTJob=%.1f, gap=%d",
                    requiredTime, windowTime, executions, longerTJob, EXECUTION_GAP_SECONDS));
        }
        Map<CapacityKey, double[]> mapWithJobsProfile = generateEmptyMapOfCapacities(listTJobs, windowTime);
        fulfillMapOfCapacities(mapWithJobsProfile, listTJobs, windowTime, executions);
        generateUsageProfileRawCsvFile(listTJobs, mapWithJobsProfile, outputPath, plan.getName(), windowTime);
    }
    /**
     * The {@code loadAvgLifecyclesTimeIntoTJob} method populates the {@code ExecutionPlan} {@code TJob} list with the times
     * calculated in the average file
     *
     * @param listTJobsWithoutTimes List with the TJobs without lifecycle duration.
     * @param pathAvgDurationPlan  Path where the avg file is placed.
     */
    private void loadAvgLifecyclesTimeIntoTJob(List<TJob> listTJobsWithoutTimes, String pathAvgDurationPlan) throws IOException {
        Map<String, TJob> byId = new HashMap<>();
        for (TJob tjob : listTJobsWithoutTimes) {
            byId.put(tjob.getIdTJob(), tjob);
        }
        boolean found = false;
        try (FileReader fileReader = new FileReader(pathAvgDurationPlan)) {
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(avgCsvHeaders()).setDelimiter(CSV_DELIMITER).setSkipHeaderRecord(true).build();
            for (CSVRecord singleRecord : csvFormat.parse(fileReader)) {
                TJob tjob = byId.get(singleRecord.get(TJOB_HEADER));
                if (tjob != null) {
                    tjob.setAvgTime(Double.parseDouble(singleRecord.get(TJOB_SETUP_LABEL + START_SUFFIX)),
                            Double.parseDouble(singleRecord.get(TJOB_SETUP_LABEL + END_SUFFIX)),
                            Double.parseDouble(singleRecord.get(TJOB_TEST_EXEC_LABEL + START_SUFFIX)),
                            Double.parseDouble(singleRecord.get(TJOB_TEST_EXEC_LABEL + END_SUFFIX)),
                            Double.parseDouble(singleRecord.get(TJOB_TEARDOWN_LABEL + START_SUFFIX)),
                            Double.parseDouble(singleRecord.get(TJOB_TEARDOWN_LABEL + END_SUFFIX)));
                    found = true;
                }
            }
        }
        if (!found) {
            log.warn("No matching TJobs found in avg duration file: {}", pathAvgDurationPlan);
        }
    }

    private double findLongerTJob(List<TJob> tJobList) {
        return tJobList.stream().mapToDouble(TJob::getEndTearDown).max().orElse(0);}

    /**
     * The {@code generateEmptyMapOfCapacities} method generates the skeleton of the UsageProfile creating a Map with the
     * different {@code ContractedCapacity} and {@code TJob} {@code CloudObjectInstances} lifecycles as keys and an
     * empty list of capacities.
     *
     * @param tJobList List with the TJobs.
     * @param windowTime  the time Window.
     */
    private Map<CapacityKey, double[]> generateEmptyMapOfCapacities(List<TJob> tJobList, double windowTime) {
        Map<CapacityKey, double[]> outputMap = new TreeMap<>();
        int windowSize = (int) Math.ceil(windowTime);
        for (TJob tjob : tJobList) {
            if (!checkIfContainsTJob(tjob.getIdTJob(), outputMap)) {
                for (String capacityName : tjob.getCapacityNames()) {
                    outputMap.put(new CapacityKey(tjob.getIdTJob(), TJob.LIFECYCLE_SETUP_NAME, capacityName),
                            new double[windowSize]);
                    outputMap.put(new CapacityKey(tjob.getIdTJob(), TJob.LIFECYCLE_TESTEXECUTION_NAME, capacityName),
                            new double[windowSize]);
                    outputMap.put(new CapacityKey(tjob.getIdTJob(), TJob.LIFECYCLE_TEARDOWN_NAME, capacityName),
                            new double[windowSize]);
                }
            }
        }

        return outputMap;
    }

    /**
     * The {@code fulfillMapOfCapacities} populates the Map of {@code ContractedCapacity}  with the {@code TJob}
     * {@code Capacity} requirements, according to the number of Executions specified.
     *
     * @param mapWithCapacitiesTJob Map with the amount of Capacities.
     * @param tJobList List with the populated {@code TJobs}.
     * @param nExecutions Number of Executions of the {@code ExecutionPlan}.
     */
    private void fulfillMapOfCapacities(Map<CapacityKey, double[]> mapWithCapacitiesTJob, List<TJob> tJobList,
                                        double window, int nExecutions) {
        double longerTJob = findLongerTJob(tJobList);
        double[] startPoint = getStartingPointExecutions(longerTJob, nExecutions);
        for (TJob tJob : tJobList) {
            for (int time = 0; time < window; time++) {
                double timeInitial = getStartTime(startPoint, time);
                Map.Entry<String, Set<Capacity>> setCapacitiesGivenTime = tJob.getCapacitiesGivenTime(time,
                        timeInitial);
                for (Capacity cap : setCapacitiesGivenTime.getValue()) {
                    CapacityKey key = new CapacityKey(tJob.getIdTJob(), setCapacitiesGivenTime.getKey(), cap.getName());
                    double[] listCapacities = mapWithCapacitiesTJob.get(key);
                    listCapacities[time] = cap.getQuantity();
                }
            }
        }
    }
    /**
     * The {@code generateUsageProfileRawCsvFile} method generates the raw comma-separated files with the {@code ContractedCapacity}
     * required by the {@code TJob} during the different Executions of the  {@code ExecutionPlan}.
     *
     * @param mapWithCapacitiesTJob Map with the amount of Capacities.
     * @param tJobList List with the populated {@code TJobs}.
     * @param outputPath Location where the file would be placed.
     * @param planName String with the {@code ExecutionPlan} name.
     * @param window Double with the window time calculated.
     */
    private void generateUsageProfileRawCsvFile(List<TJob> tJobList, Map<CapacityKey, double[]> mapWithCapacitiesTJob,
                                                String outputPath, String planName, double window) throws IOException {
        int windowInt = (int) Math.ceil(window);
        String[] intStringArray = Arrays.stream(IntStream.range(0, windowInt).toArray()).mapToObj(String::valueOf).toArray(String[]::new);
        String[] headers = concatenateArrays(rawProfileFixedHeaders(), intStringArray);
        FileUtils.ensureParentDir(outputPath);
        try (FileWriter out = new FileWriter(outputPath); CSVPrinter printer = new CSVPrinter(out,
                CSVFormat.DEFAULT.builder().setHeader(headers).setDelimiter(CSV_DELIMITER).build())) {
            addTJobCapacitiesUsed(tJobList, mapWithCapacitiesTJob, printer, planName);
            addTotalCapacitiesUsed(mapWithCapacitiesTJob, planName, windowInt, printer);
        }
    }

    private boolean checkIfContainsTJob(String tJobName, Map<CapacityKey, double[]> mapTimes) {
        for (CapacityKey key : mapTimes.keySet()) {
            if (key.getTJobId().equals(tJobName)) {return true;}}

        return false;
    }
    /**
     {@code getStartingPointExecutions } Calculates where start the different {@code ExecutionPlan } executions
     */
    private double[] getStartingPointExecutions(double testSuiteDuration, int executions) {
        double[] arrayStartPointsExecutions = new double[executions];
        double total = 0;
        arrayStartPointsExecutions[0] = 0;
        for (int i = 1; i < executions; i++) {
            total += testSuiteDuration + EXECUTION_GAP_SECONDS;
            arrayStartPointsExecutions[i] = total;
        }

        return arrayStartPointsExecutions;
    }
    private double getStartTime(double[] allStartTimes, int currentTime) {
        if (allStartTimes == null || allStartTimes.length == 0) {
            return 0;
        }
        double output = allStartTimes[0];
        for (double allStartTime : allStartTimes) {
            if (allStartTime <= currentTime) {
                output = allStartTime;
            }
        }

        return output;
    }

    static <T> T[] concatenateArrays(T[] a, T[] b) {
        int length = a.length + b.length;
        T[] result = Arrays.copyOf(a, length);
        System.arraycopy(b, 0, result, a.length, b.length);

        return result;
    }
    /**
     {@code addTJobCapacitiesUsed } aggregates all the {@code Capacity } used into the different TJobs individually
     @param  tJobList List with all the {@code TJob }
     @param  mapWithCapacitiesTJob Map with the {@code ContractedCapacity} used by the TJob
     @param  printer CSV printer for output used
     @param  planName String with the {@code ExecutionPlan} name
     */
    private static void addTJobCapacitiesUsed(List<TJob> tJobList, Map<CapacityKey, double[]> mapWithCapacitiesTJob,
                                              CSVPrinter printer, String planName) throws IOException {
        for (TJob e : tJobList) {
            for (String phase : TJob.getListTJobLifecyclesNames()) {
                for (String capacity : e.getCapacityNames()) {
                    String[] firstCols = {planName, e.getIdTJob(), phase, capacity};
                    double[] capacitiesArray = mapWithCapacitiesTJob.get(new CapacityKey(e.getIdTJob(), phase, capacity));
                    String[] capacitiesUsedStringArray =
                            Arrays.stream(capacitiesArray).mapToObj(d -> String.format(Locale.ENGLISH, "%.1f", d)).toArray(String[]::new);
                    capacitiesUsedStringArray = concatenateArrays(firstCols, capacitiesUsedStringArray);
                    printer.printRecord(capacitiesUsedStringArray);
                }
            }
        }
    }

    /**
     {@code addTotalCapacitiesUsed } aggregates to the CSV file the total aggregated {@code ContractedCapacity } used
     in the different lifecycles during the {@code ExecutionPlan }

     @param  mapWithCapacitiesTJob Map with the {@code ContractedCapacity} used by the TJob
     @param  planName String with the {@code ExecutionPlan } name
     @param timeWindow Time window considered
     @param  printer CSV printer for output used
     */
    private static void addTotalCapacitiesUsed(Map<CapacityKey, double[]> mapWithCapacitiesTJob, String planName,
                                               int timeWindow, CSVPrinter printer) throws IOException {
        for (String capacity : getCapacityNames()) {
            for (String phase : TJob.getListTJobLifecyclesNames()) {
                String[] firstCols = {planName, AGGREGATION_VALUE, phase, capacity};
                double[] capacitiesArray = new double[timeWindow];
                for (Map.Entry<CapacityKey, double[]> mapEntry : mapWithCapacitiesTJob.entrySet()) {
                    CapacityKey key = mapEntry.getKey();
                    if (key.getLifecycle().equals(phase) && key.getCapacity().equals(capacity)) {
                        IntStream.range(0, capacitiesArray.length).forEach(i -> capacitiesArray[i] += mapEntry.getValue()[i]);
                    }
                }
                String[] capacitiesUsedStringArray =
                        Arrays.stream(capacitiesArray).mapToObj(d -> String.format(Locale.ENGLISH, "%.1f", d)).toArray(String[]::new);
                capacitiesUsedStringArray = concatenateArrays(firstCols, capacitiesUsedStringArray);
                printer.printRecord(capacitiesUsedStringArray);
            }
        }
    }

}
