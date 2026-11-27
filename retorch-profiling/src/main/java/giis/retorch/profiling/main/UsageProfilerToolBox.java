/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.main;

import giis.retorch.profiling.usageprofile.CostCalculator;
import giis.retorch.profiling.usageprofile.UsageProfileGenerator;
import giis.retorch.profiling.usageprofile.ExecutionGraphBuilder;
import giis.retorch.profiling.usageprofile.ExecutionMeasurements;
import giis.retorch.profiling.usageprofile.ExecutionPlanReader;
import giis.retorch.profiling.usageprofile.PriceCatalog;
import giis.retorch.profiling.usageprofile.PriceSource;
import giis.retorch.profiling.usageprofile.dto.CostReport;
import giis.retorch.profiling.usageprofile.dto.ExecutionGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code UsageProfilerToolBox} generates the usage profile of an execution: an interactive HTML usage profile with the
 * measured resource usage and the cost of every cloud deployment alternative.
 */
public class UsageProfilerToolBox {

    private static final Logger log = LoggerFactory.getLogger(UsageProfilerToolBox.class);

    /**
     * Generates the usage profile from the usage file exported by the orchestration scripts of one execution
     * ({@code resourcemonitor.sh export}), fetching the prices from the public price lists of Azure and AWS.
     *
     * @param planName     name shown in the usage profile
     * @param usageCsv     single usage file: lifecycle phases of the TJobs and CPU, memory and disk samples
     * @param outputHtml   the usage profile to write
     * @param prices       regions to compare and local price cache
     */
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceSource prices)
            throws IOException {
        generateUsageProfile(planName, usageCsv, outputHtml, prices, null);
    }

    /**
     * Same as above, adding the execution graph read from {@code executionPlanJson}
     * ({@code .retorch/executionplan.json}); null or a missing file means no graph.
     */
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceSource prices,
                                  String executionPlanJson) throws IOException {
        generateUsageProfile(planName, usageCsv, outputHtml, prices.load(), executionPlanJson);
    }

    /** Same as the first one with an already built {@link PriceCatalog} (e.g. loaded from a file or defined by the user). */
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceCatalog catalog)
            throws IOException {
        generateUsageProfile(planName, usageCsv, outputHtml, catalog, null);
    }

    /** Same as above, adding the optional execution plan file. */
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceCatalog catalog,
                                  String executionPlanJson) throws IOException {
        Path out = Paths.get(outputHtml);
        ExecutionMeasurements measurements = ExecutionMeasurements.read(Paths.get(usageCsv));
        ExecutionMeasurements.Timeline timeline = measurements.toTimeline();
        CostReport report = new CostCalculator().calculate(timeline, catalog);
        ExecutionGraph graph = readGraph(executionPlanJson, measurements, timeline);
        new UsageProfileGenerator().generate(planName, timeline, report, catalog, graph, out);
        log.info("Usage profile written to {}", out);
    }

    private static ExecutionGraph readGraph(String executionPlanJson, ExecutionMeasurements measurements,
                                            ExecutionMeasurements.Timeline timeline) throws IOException {
        if (executionPlanJson == null || executionPlanJson.isEmpty() || !Files.exists(Paths.get(executionPlanJson))) {
            log.info("No execution plan file, the usage profile will not include the execution graph");
            return null;
        }
        return ExecutionGraphBuilder.build(ExecutionPlanReader.read(Paths.get(executionPlanJson)),
                measurements.tjobMetrics(timeline), measurements.containerUsage());
    }
}
