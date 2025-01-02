package giis.retorch.profiling.main;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import giis.retorch.profiling.datasetgeneration.DataTuple;
import giis.retorch.profiling.datasetgeneration.DatasetGenerator;
import giis.retorch.profiling.model.CloudObjectInstance;
import giis.retorch.profiling.model.UsageProfile;
import giis.retorch.profiling.profilegeneration.ProfileGenerator;
import giis.retorch.profiling.profilegeneration.ProfilePlotter;
import giis.retorch.profiling.report.UsageProfileReportGenerator;
import giis.retorch.profiling.utils.COISerializer;
import giis.retorch.orchestration.model.ExecutionPlan;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static giis.retorch.profiling.utils.CsvConstants.*;

/**
 * {@code UsageProfilerToolBox} provides the main methods to generate the different Usage Profiles.
 */
public class UsageProfilerToolBox {

    private final DatasetGenerator generator;
    private static final Logger log = LoggerFactory.getLogger(UsageProfilerToolBox.class);

    public UsageProfilerToolBox() {
        generator = new DatasetGenerator();
    }

    /**
     * Reads the execution data CSV files from {@code inputPath}, computes the average lifecycle duration
     * across all executions, and writes the result to {@code outputPath}.
     * <p>
     * Example usage:
     * <pre>
     *     UsageProfilerToolBox usageProfiler = new UsageProfilerToolBox();
     *     usageProfiler.generateAverageDurationCSVFile("/home/datasets", "/averagedurationfile.csv");
     * </pre>
     *
     * @param inputPath  The path where execution data CSV files are located.
     * @param outputPath The path where the average duration CSV file will be written.
     */
    public void generateAverageDurationCSVFile(String inputPath, String outputPath) throws IOException {
        log.debug("Generating the DataTuples");
        List<DataTuple> listTuples = generator.generateListTuplesAvgTimes(inputPath);
        log.debug("Creating the CSV file from the DataTuples");
        generator.createCSVAvgFromListDataTuplesOrdered(listTuples, outputPath);
    }

    /**
     * Loads the {@code CloudObjectInstance}s from {@code .retorch/infra/<systemName>CloudObjectInstances.json}
     * and, for each instance, overlays the contracted-capacity rows onto the raw TJob usage profile and
     * generates the corresponding Usage Profile charts in the output folder.
     * <p>
     * Example usage:
     * <pre>
     *     UsageProfilerToolBox usageProfiler = new UsageProfilerToolBox();
     *     usageProfiler.generateCOIUsageProfiles("FullTeaching", "./output/profile.csv",
     *             "./output/", plan.getName());
     * </pre>
     *
     * @param systemName     Name of the system — used to locate {@code <systemName>CloudObjectInstances.json}.
     * @param profileCsvPath Path to the raw TJob usage profile CSV (output of
     *                       {@link ProfileGenerator#generateExecutionPlanCapacitiesUsage}).
     * @param avgCsvPath     Path to the average lifecycle duration CSV (output of
     *                       {@link #generateAverageDurationCSVFile}), used to derive the COI lifecycle times.
     * @param outputPath     Folder where per-COI contracted-capacity CSVs and chart PNG images will be written.
     * @param planName       Name of the {@code ExecutionPlan}, used to label the generated charts.
     * @throws IOException if the configuration file cannot be read or a profile CSV cannot be written.
     */
    public void generateCOIUsageProfiles(String systemName, String profileCsvPath, String avgCsvPath,
                                           String outputPath, String planName) throws IOException {
        log.debug("Loading Cloud Object Instances for system: {}", systemName);
        List<CloudObjectInstance> cloudObjectInstances =
                new COISerializer().deserializeCloudObjectInstances(systemName);
        double[] times = readLifecycleTimesFromCsv(avgCsvPath);
        ProfileGenerator profileGenerator = new ProfileGenerator();
        List<UsageProfile> profiles = new ArrayList<>();
        String sep = outputPath.endsWith("/") || outputPath.endsWith(java.io.File.separator) ? "" : "/";
        String imagesPath = outputPath + sep + "images/";
        String profilesPath = outputPath + sep + "profiles/";
        for (CloudObjectInstance coi : cloudObjectInstances) {
            log.debug("Generating usage profile for COI: {}", coi.getName());
            coi.setLifecycleTimes(times[0], times[1], times[2], times[3], times[4], times[5]);
            String coiProfilePath = outputPath + sep + "profile_" + coi.getName() + ".csv";
            profileGenerator.writeCOIContractedCapacitiesCSV(profileCsvPath, coiProfilePath, coi);
            ProfilePlotter plotter = new ProfilePlotter(coiProfilePath);
            plotter.generateTotalTJobUsageProfileCharts(imagesPath, profilesPath, planName, coi.getName());
            profiles.add(plotter.getUsageProfile());
        }
        new UsageProfileReportGenerator().generateReport(profiles, outputPath, planName);
    }

    /**
     * Generates the raw TJob capacity-usage profile and then overlays contracted capacities
     * to generate COI usage profiles and charts.
     *
     * @param plan           The execution plan.
     * @param systemName     Name of the system.
     * @param avgCsvPath     Path to the average duration CSV file.
     * @param outputPath     Folder where profiles and charts will be written.
     * @param windowSize     Time window for the profile.
     * @param executionCount Number of executions to consider.
     * @throws IOException if an I/O error occurs.
     */
    public void generateProfiles(ExecutionPlan plan, String systemName, String avgCsvPath, String outputPath,
                                 double windowSize, int executionCount) throws IOException {
        String sep = outputPath.endsWith("/") || outputPath.endsWith(java.io.File.separator) ? "" : "/";
        String profileCsvPath = outputPath + sep + "profile.csv";

        ProfileGenerator profileGenerator = new ProfileGenerator();
        profileGenerator.generateExecutionPlanCapacitiesUsage(plan, avgCsvPath, profileCsvPath, windowSize, executionCount);

        generateCOIUsageProfiles(systemName, profileCsvPath, avgCsvPath, outputPath, plan.getName());
    }


    private double[] readLifecycleTimesFromCsv(String avgCsvPath) throws IOException {
        String[] headers = {TJOB_HEADER, STAGE_HEADER,
                COI_SETUP_LABEL + START_SUFFIX, COI_SETUP_LABEL + END_SUFFIX,
                TJOB_SETUP_LABEL + START_SUFFIX, TJOB_SETUP_LABEL + END_SUFFIX,
                TJOB_TEST_EXEC_LABEL + START_SUFFIX, TJOB_TEST_EXEC_LABEL + END_SUFFIX,
                TJOB_TEARDOWN_LABEL + START_SUFFIX, TJOB_TEARDOWN_LABEL + END_SUFFIX,
                COI_TEARDOWN_LABEL + START_SUFFIX, COI_TEARDOWN_LABEL + END_SUFFIX};
        double startSetUp = 0.0;
        double endSetUp = 0.0;
        double startTJobExec = Double.MAX_VALUE;
        double endTJobExec = 0.0;
        double startTearDown = Double.MAX_VALUE;
        double endTearDown = 0.0;
        try (FileReader reader = new FileReader(avgCsvPath)) {
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(headers).setDelimiter(CSV_DELIMITER).setSkipHeaderRecord(true).build();
            for (CSVRecord csvRecord : csvFormat.parse(reader)) {
                endSetUp      = Math.max(endSetUp,   Double.parseDouble(csvRecord.get(COI_SETUP_LABEL + END_SUFFIX)));
                startTJobExec = Math.min(startTJobExec, Double.parseDouble(csvRecord.get(TJOB_SETUP_LABEL + START_SUFFIX)));
                endTJobExec   = Math.max(endTJobExec,   Double.parseDouble(csvRecord.get(TJOB_TEARDOWN_LABEL + END_SUFFIX)));
                startTearDown = Math.min(startTearDown, Double.parseDouble(csvRecord.get(COI_TEARDOWN_LABEL + START_SUFFIX)));
                endTearDown   = Math.max(endTearDown,   Double.parseDouble(csvRecord.get(COI_TEARDOWN_LABEL + END_SUFFIX)));
            }
        }
        return new double[]{startSetUp, endSetUp,
                startTJobExec == Double.MAX_VALUE ? 0.0 : startTJobExec,
                endTJobExec, startTearDown == Double.MAX_VALUE ? 0.0 : startTearDown, endTearDown};
    }
}
