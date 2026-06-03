package giis.retorch.profiling.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import giis.retorch.profiling.datasetgeneration.DataTuple;
import giis.retorch.profiling.datasetgeneration.DatasetGenerator;
import giis.retorch.profiling.datasetgeneration.LifecycleTimes;
import giis.retorch.profiling.datasetgeneration.LifecycleTimesReader;
import giis.retorch.profiling.model.CloudObjectInstance;
import giis.retorch.profiling.model.UsageProfile;
import giis.retorch.profiling.profilegeneration.CoiProfileWriter;
import giis.retorch.profiling.profilegeneration.ProfileGenerator;
import giis.retorch.profiling.profilegeneration.ProfilePlotter;
import giis.retorch.profiling.report.UsageProfileReportGenerator;
import giis.retorch.profiling.utils.COISerializer;
import giis.retorch.profiling.utils.FileUtils;
import giis.retorch.orchestration.model.ExecutionPlan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        String profileCsvPath = FileUtils.joinPath(outputPath, "profile.csv");

        ProfileGenerator profileGenerator = new ProfileGenerator();
        profileGenerator.generateExecutionPlanCapacitiesUsage(plan, avgCsvPath, profileCsvPath, windowSize, executionCount);

        generateCOIUsageProfiles(systemName, profileCsvPath, avgCsvPath, outputPath, plan.getName());
    }

    /**
     * Loads the {@code CloudObjectInstance}s from {@code .retorch/infra/<systemName>CloudObjectInstances.json}
     * and, for each instance, overlays the contracted-capacity rows onto the raw TJob usage profile and
     * generates the corresponding Usage Profile charts in the output folder.
     */
    private void generateCOIUsageProfiles(String systemName, String profileCsvPath, String avgCsvPath,
                                          String outputPath, String planName) throws IOException {
        log.debug("Loading Cloud Object Instances for system: {}", systemName);
        List<CloudObjectInstance> cloudObjectInstances =
                new COISerializer().deserializeCloudObjectInstances(systemName);
        LifecycleTimes times = LifecycleTimesReader.readFromAverageCsv(avgCsvPath);
        CoiProfileWriter coiProfileWriter = new CoiProfileWriter();
        List<UsageProfile> profiles = new ArrayList<>();
        String imagesPath = FileUtils.joinPath(outputPath, "images/");
        String profilesPath = FileUtils.joinPath(outputPath, "profiles/");
        for (CloudObjectInstance coi : cloudObjectInstances) {
            log.debug("Generating usage profile for COI: {}", coi.getName());
            coi.setLifecycleTimes(times.getStartSetUp(), times.getEndSetUp(),
                    times.getStartTJobExec(), times.getEndTJobExec(),
                    times.getStartTearDown(), times.getEndTearDown());
            String coiProfilePath = FileUtils.joinPath(outputPath, "profile_" + coi.getName() + ".csv");
            coiProfileWriter.write(profileCsvPath, coiProfilePath, coi);
            ProfilePlotter plotter = new ProfilePlotter(coiProfilePath);
            plotter.generateTotalTJobUsageProfileCharts(imagesPath, profilesPath, planName, coi.getName());
            profiles.add(plotter.getUsageProfile());
        }
        new UsageProfileReportGenerator().generateReport(profiles, outputPath, planName);
    }
}