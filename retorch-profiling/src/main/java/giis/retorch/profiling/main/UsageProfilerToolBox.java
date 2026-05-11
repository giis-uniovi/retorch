package giis.retorch.profiling.main;

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
    public void generateAverageDurationCSVFile(String inputPath, String outputPath) {
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
     * @param outputPath     Folder where per-COI contracted-capacity CSVs and chart PNG images will be written.
     * @param planName       Name of the {@code ExecutionPlan}, used to label the generated charts.
     * @throws IOException if the configuration file cannot be read or a profile CSV cannot be written.
     */
    public void generateCOIUsageProfiles(String systemName, String profileCsvPath,
                                          String outputPath, String planName) throws IOException {
        log.debug("Loading Cloud Object Instances for system: {}", systemName);
        List<CloudObjectInstance> cloudObjectInstances =
                new COISerializer().deserializeCloudObjectInstances(systemName);
        ProfileGenerator profileGenerator = new ProfileGenerator();
        List<UsageProfile> profiles = new ArrayList<>();
        for (CloudObjectInstance coi : cloudObjectInstances) {
            log.debug("Generating usage profile for COI: {}", coi.getName());
            String coiProfilePath = outputPath + "profile_" + coi.getName() + ".csv";
            profileGenerator.generateCOIContractedCapacities(profileCsvPath, coiProfilePath, coi);
            ProfilePlotter plotter = new ProfilePlotter(coiProfilePath);
            plotter.generateTotalTJobUsageProfileCharts(outputPath, planName, coi.getName());
            profiles.add(plotter.getUsageProfile());
        }
        new UsageProfileReportGenerator().generateReport(profiles, outputPath, planName);
    }
}
