/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.main;

import giis.retorch.profiling.usageprofile.dto.cost.CostReportDTO;
import giis.retorch.profiling.usageprofile.dto.plan.ExecutionGraphDTO;
import giis.retorch.profiling.usageprofile.dto.report.SutInfoDTO;
import giis.retorch.profiling.usageprofile.cost.CostCalculator;
import giis.retorch.profiling.usageprofile.plan.ExecutionGraphBuilder;
import giis.retorch.profiling.usageprofile.measurement.ExecutionMeasurements;
import giis.retorch.profiling.usageprofile.plan.ExecutionPlanReader;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;
import giis.retorch.profiling.usageprofile.pricing.PriceSource;
import giis.retorch.profiling.usageprofile.measurement.Timeline;
import giis.retorch.profiling.usageprofile.report.UsageProfileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code UsageProfilerToolBox} generates the usage profile of an execution: an interactive HTML usage profile with the
 * measured resource usage and the cost of every cloud deployment alternative. The entry point is
 * {@link #generate(UsageProfileRequest)}; the {@code generateUsageProfile} overloads are deprecated and delegate to it.
 */
public class UsageProfilerToolBox {

    private static final Logger log = LoggerFactory.getLogger(UsageProfilerToolBox.class);

    /**
     * Generates the usage profile from the usage file exported by the orchestration scripts of one execution
     * ({@code resourcemonitor.sh export}), with the prices and the optional execution plan of the request.
     */
    public void generate(UsageProfileRequest request) throws IOException {
        Path out = Paths.get(request.outputHtml());
        PriceCatalog catalog = request.catalog() != null ? request.catalog() : request.priceSource().load();
        ExecutionMeasurements measurements = ExecutionMeasurements.read(Paths.get(request.usageCsv()));
        Timeline timeline = measurements.toTimeline();
        Path planFile = existingPlanFile(request.executionPlanJson());
        List<ExecutionPlanReader.PlanNode> plan = planFile == null
                ? Collections.<ExecutionPlanReader.PlanNode>emptyList() : ExecutionPlanReader.read(planFile);
        CostReportDTO report = new CostCalculator().calculate(timeline, catalog,
                browserTjobs(plan, request.browserResourcePattern()));
        ExecutionGraphDTO graph = planFile == null ? null
                : ExecutionGraphBuilder.build(plan, measurements.tjobMetrics(timeline), measurements.containerUsage());
        new UsageProfileGenerator().generate(request.planName(), timeline, report, catalog, graph, SutInfoDTO.detect(), out);
        log.info("Usage profile written to {}", out);
    }

    /**
     * Generates the usage profile fetching the prices from the public price lists of Azure and AWS.
     *
     * @deprecated use {@link #generate(UsageProfileRequest)}
     */
    @Deprecated
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceSource prices)
            throws IOException {
        generate(UsageProfileRequest.builder(planName, usageCsv, outputHtml).prices(prices).build());
    }

    /**
     * Same as above, adding the execution graph read from {@code executionPlanJson}; null or a missing file means no graph.
     *
     * @deprecated use {@link #generate(UsageProfileRequest)}
     */
    @Deprecated
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceSource prices,
                                     String executionPlanJson) throws IOException {
        generate(UsageProfileRequest.builder(planName, usageCsv, outputHtml).prices(prices)
                .executionPlan(executionPlanJson).build());
    }

    /**
     * Same as the first one with an already built {@link PriceCatalog}.
     *
     * @deprecated use {@link #generate(UsageProfileRequest)}
     */
    @Deprecated
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceCatalog catalog)
            throws IOException {
        generate(UsageProfileRequest.builder(planName, usageCsv, outputHtml).prices(catalog).build());
    }

    /**
     * Same as above, adding the optional execution plan file.
     *
     * @deprecated use {@link #generate(UsageProfileRequest)}
     */
    @Deprecated
    public void generateUsageProfile(String planName, String usageCsv, String outputHtml, PriceCatalog catalog,
                                     String executionPlanJson) throws IOException {
        generate(UsageProfileRequest.builder(planName, usageCsv, outputHtml).prices(catalog)
                .executionPlan(executionPlanJson).build());
    }

    /** The execution plan file, or null (no graph) if none was given or it does not exist; an empty plan is not null. */
    private static Path existingPlanFile(String executionPlanJson) {
        if (executionPlanJson == null || executionPlanJson.isEmpty() || !Files.exists(Paths.get(executionPlanJson))) {
            log.info("No execution plan file, the usage profile will not include the execution graph");
            return null;
        }
        return Paths.get(executionPlanJson);
    }

    /** TJobs that use a browser: those with a resource whose name contains {@code pattern} (case-insensitive). */
    static Set<String> browserTjobs(List<ExecutionPlanReader.PlanNode> plan, String pattern) {
        Set<String> ids = new HashSet<>();
        String wanted = pattern.toLowerCase(Locale.ROOT);
        for (ExecutionPlanReader.PlanNode node : plan) {
            for (String resource : node.resources) {
                if (resource.toLowerCase(Locale.ROOT).contains(wanted)) {
                    ids.add(node.id);
                }
            }
        }
        return ids;
    }
}
