package giis.retorch.profiling.profilegeneration;


import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import giis.retorch.profiling.model.CloudObjectInstance;
import giis.retorch.profiling.model.CapacityContracted;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.Capacity;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static giis.retorch.profiling.model.CapacityContracted.getCapacityNames;

public class ProfileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProfileGenerator.class);
    private static final String TJOB_HEADER = "tjobname";
    private static final String CAPACITY_HEADER = "capacity";
    private static final String PLAN_HEADER = "executionplan";
    private static final String STAGE_HEADER = "stage";
    private static final String LIFECYCLE_HEADER="lifecyclephase";
    private static final String COI_LABEL="coi-";
    private static final String COI_SETUP_LABEL = COI_LABEL + CloudObjectInstance.SETUP_NAME;
    private static final String COI_TEARDOWN_LABEL = COI_LABEL + CloudObjectInstance.TEARDOWN_NAME;
    private static final String TJOB_LABEL="tjob-";
    private static final String TJOB_SETUP_LABEL = TJOB_LABEL + TJob.LIFECYCLE_SETUP_NAME;
    private static final String TJOB_TEST_EXEC_LABEL = TJOB_LABEL + TJob.LIFECYCLE_TESTEXECUTION_NAME;
    private static final String TJOB_TEARDOWN_LABEL = TJOB_LABEL + TJob.LIFECYCLE_TEARDOWN_NAME;
    private static final String END_SUFFIX = "-end";
    private static final String START_SUFFIX = "-start";
    private static final String AGGREGATION_VALUE = "TOTAL";


    public void generateResourceProfile(ExecutionPlan plan, String pathAvgDurationPlan, String outputPath,
                                        double windowTime, int executions) {
        List<TJob> listTJobs = plan.gettJobClassList();
        listTJobs.sort(Comparator.comparing(TJob::getStage).thenComparing(TJob::getIdTJob));
        loadAvgTimes(listTJobs, pathAvgDurationPlan);
        double longerTJob = findLongerTJob(listTJobs);
        validateNumberExecutionsAccordingWindow(executions, longerTJob, windowTime);
        Map<String, double[]> mapWithJobsProfile = generateEmptyTJobProfile(listTJobs, windowTime);
        fullFillProfileTJobAvgData(mapWithJobsProfile, listTJobs, windowTime, executions);
        generateProfileUsageTJobsCSV(listTJobs, mapWithJobsProfile, outputPath, plan.getName(), windowTime);
    }

    private void loadAvgTimes(List<TJob> listWithoutTimes, String pathAvgDurationPlan) {
        String[] tableHeaders = {TJOB_HEADER, STAGE_HEADER, COI_SETUP_LABEL + START_SUFFIX,
                COI_SETUP_LABEL + END_SUFFIX, TJOB_SETUP_LABEL + START_SUFFIX, TJOB_SETUP_LABEL + END_SUFFIX,
                TJOB_TEST_EXEC_LABEL + START_SUFFIX, TJOB_TEST_EXEC_LABEL + END_SUFFIX,
                TJOB_TEARDOWN_LABEL + START_SUFFIX, TJOB_TEARDOWN_LABEL + END_SUFFIX, COI_TEARDOWN_LABEL + START_SUFFIX,
                COI_TEARDOWN_LABEL + END_SUFFIX};

        try (FileReader fileReader = new FileReader(pathAvgDurationPlan)) {
            CSVFormat csvFormat =
                    CSVFormat.DEFAULT.builder().setHeader(tableHeaders).setDelimiter(";").setSkipHeaderRecord(true).build();
            for (CSVRecord singleRecord : csvFormat.parse(fileReader)) {
                String idTJob = singleRecord.get(TJOB_HEADER);
                for (TJob tjob : listWithoutTimes) {
                    if (tjob.getIdTJob().equals(idTJob)) {
                        tjob.setAvgTime(Double.parseDouble(singleRecord.get(TJOB_SETUP_LABEL + START_SUFFIX)),
                                Double.parseDouble(singleRecord.get(TJOB_SETUP_LABEL + END_SUFFIX)),
                                Double.parseDouble(singleRecord.get(TJOB_TEST_EXEC_LABEL + START_SUFFIX)),
                                Double.parseDouble(singleRecord.get(TJOB_TEST_EXEC_LABEL + END_SUFFIX)),
                                Double.parseDouble(singleRecord.get(TJOB_TEARDOWN_LABEL + START_SUFFIX)),
                                Double.parseDouble(singleRecord.get(TJOB_TEARDOWN_LABEL + END_SUFFIX)));
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error opening the file: {}", e.getMessage());
        }
    }

    private double findLongerTJob(List<TJob> tJobList) {
        double longTjob = 0;
        for (TJob tjob : tJobList) {
            if (tjob.getEndTearDown() > longTjob) {longTjob = tjob.getEndTearDown();}
        }

        return longTjob;
    }

    private void validateNumberExecutionsAccordingWindow(int executions, double testSuiteDuration, double window) {
        if ((executions * testSuiteDuration + executions * 5) > window) {
            throw new IllegalArgumentException("The number of executions is longer than the window");
        }
    }

    private Map<String, double[]> generateEmptyTJobProfile(List<TJob> tJobList, double windowTime) {
        HashMap<String, double[]> outputMap = new HashMap<>();
        for (TJob tjob : tJobList) {
            if (!checkIfContainsTJob(tjob.getIdTJob(), outputMap)) {
                List<String> listCapacityNames = tjob.getCapacityNames();
                for (String capacityName : listCapacityNames) {
                    outputMap.put(tjob.getIdTJob() + "-" + TJob.LIFECYCLE_SETUP_NAME + "-" + capacityName,
                            new double[(int) Math.ceil(windowTime)]);
                    outputMap.put(tjob.getIdTJob() + "-" + TJob.LIFECYCLE_TESTEXECUTION_NAME + "-" + capacityName,
                            new double[(int) Math.ceil(windowTime)]);
                    outputMap.put(tjob.getIdTJob() + "-" + TJob.LIFECYCLE_TEARDOWN_NAME + "-" + capacityName,
                            new double[(int) Math.ceil(windowTime)]);
                }
            }
        }

        return outputMap;
    }

    private void fullFillProfileTJobAvgData(Map<String, double[]> mapWithCapacitiesTJob, List<TJob> tJobList,
                                            double window, int nExecutions) {
        double longerTJob = findLongerTJob(tJobList);
        double[] startPoint = getStartingPointExecutions(longerTJob, nExecutions);
        for (TJob tJob : tJobList) {
            for (int time = 0; time < window; time++) {
                double timeInitial = getStartTime(startPoint, time);
                Map.Entry<String, Set<Capacity>> setCapacitiesGivenTime = tJob.getCapacitiesGivenTime(time,
                        timeInitial);
                for (Capacity cap : setCapacitiesGivenTime.getValue()) {
                    String key = tJob.getIdTJob() + "-" + setCapacitiesGivenTime.getKey() + "-" + cap.getName();
                    double[] listCapacities = mapWithCapacitiesTJob.get(key);
                    listCapacities[time] = cap.getQuantity();
                    mapWithCapacitiesTJob.put(key, listCapacities);
                }
            }
        }
    }

    private void generateProfileUsageTJobsCSV(List<TJob> tJobList, Map<String, double[]> mapWithCapacitiesTJob,
                                              String outputPath, String planName, double window) {
        int windowInt = (int) Math.ceil(window);
        String[] intStringArray = Arrays.stream(IntStream.range(0, windowInt).toArray()).mapToObj(String::valueOf).toArray(String[]::new);
        String[] headers = {PLAN_HEADER, TJOB_HEADER, LIFECYCLE_HEADER, CAPACITY_HEADER};
        headers = concatenateArrays(headers, intStringArray);
        try (FileWriter out = new FileWriter(outputPath); CSVPrinter printer = new CSVPrinter(out,
                CSVFormat.DEFAULT.builder().setHeader(headers).setDelimiter(";").build())) {
            addTJobCapacitiesUsed(tJobList, mapWithCapacitiesTJob, printer);
            addTotalCapacitiesUsed(mapWithCapacitiesTJob, planName, windowInt, printer);
        } catch (IOException e) {
            log.error("Error writing CSV file: {}", e.getMessage());
        }
    }

    private boolean checkIfContainsTJob(String tJobName, Map<String, double[]> mapTimes) {
        for (Map.Entry<String, double[]> entry : mapTimes.entrySet()) {
            if (entry.getKey().contains(tJobName)) {return true;}}

        return false;
    }

    private double[] getStartingPointExecutions(double testSuiteDuration, int executions) {
        double[] arrayStartPointsExecutions = new double[executions];
        double total = 0;
        arrayStartPointsExecutions[0] = 0;
        for (int i = 1; i < executions; i++) {
            total += testSuiteDuration + 5;
            arrayStartPointsExecutions[i] = total;
        }

        return arrayStartPointsExecutions;
    }
    private double getStartTime(double[] allStartTimes, int currentTime) {
        double output = 0;
        for (int i = 0; i < allStartTimes.length - 1; i++) {
            if (allStartTimes[i] <= currentTime) {
                output = allStartTimes[i];
            }
        }

        return output;
    }

    public static <T> T[] concatenateArrays(T[] a, T[] b) {
        int length = a.length + b.length;
        T[] result = Arrays.copyOf(a, length);
        System.arraycopy(b, 0, result, a.length, b.length);

        return result;
    }
    private static void addTJobCapacitiesUsed(List<TJob> tJobList, Map<String, double[]> mapWithCapacitiesTJob,
                                              CSVPrinter printer) throws IOException {
        for (TJob e : tJobList) {
            for (String phase : TJob.getListTJobLifecyclesNames()) {
                for (String capacity : e.getCapacityNames()) {
                    String[] firstCols = {"OneScheduling", e.getIdTJob(), phase, capacity};
                    double[] capacitiesArray = mapWithCapacitiesTJob.get(e.getIdTJob() + "-" + phase + "-" + capacity);
                    String[] capacitiesUsedStringArray =
                            Arrays.stream(capacitiesArray).mapToObj(d -> String.format(Locale.ENGLISH, "%.1f", d)).toArray(String[]::new);
                    capacitiesUsedStringArray = concatenateArrays(firstCols, capacitiesUsedStringArray);
                    printer.printRecord(capacitiesUsedStringArray);
                }
            }
        }
    }
    private static void addTotalCapacitiesUsed(Map<String, double[]> mapWithCapacitiesTJob, String planName,
                                               int timeWindow, CSVPrinter printer) throws IOException {
        for (String capacity : getCapacityNames()) {
            for (String phase : TJob.getListTJobLifecyclesNames()) {
                String[] firstCols = {planName, AGGREGATION_VALUE, phase, capacity};
                double[] capacitiesArray = new double[timeWindow];
                for (Map.Entry<String, double[]> mapEntry : mapWithCapacitiesTJob.entrySet()) {
                    if (mapEntry.getKey().contains(phase) && mapEntry.getKey().contains(capacity)) {
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

    public void generateCOIContractedCapacities(String inputPath, String outputPath, CloudObjectInstance coi) throws IOException {
        Map<String, CapacityContracted> coiCapacities = coi.getContractedCapacities();
        List<CSVRecord> listRecords = new ArrayList<>();
        String[] headerNames = new String[0];
        List<CSVRecord> tuplesToTreatCSV = new LinkedList<>();

        try (FileReader fileReader = new FileReader(inputPath)) {
            CSVFormat csvFormat =
                    CSVFormat.DEFAULT.builder().setHeader().setDelimiter(";").setSkipHeaderRecord(true).build();
            listRecords = csvFormat.parse(fileReader).getRecords();
            headerNames = listRecords.get(0).getParser().getHeaderNames().toArray(new String[0]);
            tuplesToTreatCSV =
                    listRecords.stream()
                            .filter(csvRecord -> csvRecord.get(TJOB_HEADER).equals(AGGREGATION_VALUE))
                            .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("Failed to find the profile file while creating the COI");
        }
        HashMap<String, ArrayList<String>> mapPriorCalculate = aggregateLifecycleCapacities(tuplesToTreatCSV);
        HashMap<String, ArrayList<String>> outputMap = new HashMap<>();
        for (Map.Entry<String, ArrayList<String>> entry : mapPriorCalculate.entrySet()) {
            String capacityName = entry.getKey();
            CapacityContracted currentCapacity = coiCapacities.get(capacityName);
            if (currentCapacity.getQuantity() > 0) {
                int amountGaps = (int) Math.ceil(currentCapacity.getQuantity() / currentCapacity.getGranularity());
                Triplet<Integer, Integer, Integer>[] mapCapacitiesUsed = new Triplet[amountGaps];
                ArrayList<String> currentValuesContracted = new ArrayList<>();
                for (int i = 0; i < entry.getValue().size(); i++) {
                    double capacityRequired = Double.parseDouble(entry.getValue().get(i));
                    currentValuesContracted.add(String.format(Locale.ENGLISH, "%.1f",
                            getProvisionedCapacityGivenTime(i, currentCapacity, capacityRequired, coi,
                                    mapCapacitiesUsed)));
                }
                String keyMap = "ExecutionPlanEx" + "-" + coi.getName() + "-" + capacityName;
                outputMap.put(keyMap, currentValuesContracted);
            }
        }
        generateNewProfileDatasetWithCapacitiesUsed(listRecords, headerNames, outputPath, outputMap);
    }

    private HashMap<String, ArrayList<String>> aggregateLifecycleCapacities(List<CSVRecord> tuplesToTreatCSV) {
        HashMap<String, ArrayList<String>> mapPriorCalculate = new HashMap<>();
        for (CSVRecord tupleCSV : tuplesToTreatCSV) {
            ArrayList<String> values = new ArrayList<>();
            for (int i = 4; i < tupleCSV.size(); i++) {
                values.add(tupleCSV.get(i));
            }
            if (mapPriorCalculate.containsKey(tupleCSV.get(CAPACITY_HEADER))) {
                mapPriorCalculate.compute(tupleCSV.get(CAPACITY_HEADER),
                        (k, oldTuple) -> (ArrayList<String>) aggregateArraylists(oldTuple, values));
            } else {
                mapPriorCalculate.put(tupleCSV.get(CAPACITY_HEADER), values);
            }
        }

        return mapPriorCalculate;
    }

    public void generateNewProfileDatasetWithCapacitiesUsed(List<CSVRecord> listRecords, String[] headerNames,
                                                            String outputPath,
                                                            Map<String, ArrayList<String>> outputMap) throws IOException {
        try (FileWriter out = new FileWriter(outputPath); CSVPrinter printer = new CSVPrinter(out,
                CSVFormat.DEFAULT.builder().setHeader(headerNames).setDelimiter(";").build())) {
            String scheduling = "None";
            for (CSVRecord recordCapacity : listRecords) {
                printer.printRecord(recordCapacity.stream().collect(Collectors.toList()));
                scheduling = recordCapacity.get(PLAN_HEADER);
            }
            for (Map.Entry<String, ArrayList<String>> entry : outputMap.entrySet()) {
                String[] splitValues = entry.getKey().split("-");
                String[] arrayheader = new String[]{scheduling, AGGREGATION_VALUE, "CONTRACTED", splitValues[2]};
                printer.printRecord(concatenateArrays(arrayheader,
                        entry.getValue().toArray(entry.getValue().toArray(new String[0]))));
            }
        } catch (IOException e) {
            throw new IOException("The file :" + outputPath + "Cannot be opened");}
    }

    public double getProvisionedCapacityGivenTime(int currentTime, CapacityContracted capacity, double capacityValue,
                                                  CloudObjectInstance coi,
                                                  Triplet<Integer, Integer, Integer>[] mapCapacitiesUsed) {
        int numberOfUsedGaps = (int) Math.ceil(capacityValue / capacity.getGranularity());
        if (numberOfUsedGaps>=mapCapacitiesUsed.length){
            numberOfUsedGaps=mapCapacitiesUsed.length;
        }
        // Update used gaps
        for (int i = 0; i < numberOfUsedGaps; i++) {
            if (mapCapacitiesUsed[i] == null) {
                mapCapacitiesUsed[i] = new Triplet<>(currentTime, currentTime, currentTime);
            } else {
                mapCapacitiesUsed[i] = mapCapacitiesUsed[i].setAt2(currentTime);
                if (mapCapacitiesUsed[i].getValue1() - mapCapacitiesUsed[i].getValue2() >= coi.getBillingOption().getTimePeriod()) {
                    mapCapacitiesUsed[i] = mapCapacitiesUsed[i].setAt1(currentTime);
                }
            }
        }
        // Clear unused gaps
        for (int i = numberOfUsedGaps; i < mapCapacitiesUsed.length; i++) {
            if (isUnusedGap(currentTime, mapCapacitiesUsed[i], coi.getBillingOption().getTimePeriod())) {
                mapCapacitiesUsed[i] = null;
            }
        }
        // Calculate provisioned capacity
        long numberOfEmptyGaps = Arrays.stream(mapCapacitiesUsed).filter(Objects::isNull).count();
        return capacity.getGranularity() * (mapCapacitiesUsed.length - numberOfEmptyGaps);
    }
    private boolean isUnusedGap(int currentTime, Triplet<Integer, Integer, Integer> gap, int timePeriod) {
        return gap != null && (currentTime - gap.getValue1() >= timePeriod) && (currentTime != gap.getValue2());
    }

    public List<String> aggregateArraylists(List<String> firstRecord, List<String> secondRecord) {
        ArrayList<String> aggregatedList;
        if (firstRecord.size() == secondRecord.size()) {
            aggregatedList = new ArrayList<>();
            // Sum the different elements elements
            for (int i = 0; i < firstRecord.size(); i++) {
                aggregatedList.add(String.format(Locale.ENGLISH, "%.1f",
                        Double.parseDouble(firstRecord.get(i)) + Double.parseDouble(secondRecord.get(i))));
            }
        } else {
            throw new IllegalArgumentException("The arrays provided differ in size");
        }
        return aggregatedList;
    }
}