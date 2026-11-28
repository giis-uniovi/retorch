/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.ExecutionMeasurements.Load;
import giis.retorch.profiling.usageprofile.ExecutionMeasurements.Slice;
import giis.retorch.profiling.usageprofile.ExecutionMeasurements.Timeline;
import giis.retorch.profiling.usageprofile.dto.CostReport;
import giis.retorch.profiling.usageprofile.dto.SutInfo;
import giis.retorch.profiling.usageprofile.dto.UsageProfileData;
import giis.retorch.profiling.usageprofile.dto.ExecutionGraph;
import giis.retorch.profiling.usageprofile.dto.TimelineSeries;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Writes the interactive usage profile: a single self-contained HTML file (data and Apache ECharts are inlined, so
 * it opens offline from any browser) with the measured usage and the cost of every deployment alternative.
 */
public class UsageProfileGenerator {

    private static final int MAX_TIMELINE_POINTS = 600;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIR = "usageprofile/";
    private static final String SCRIPT_END = "</script";
    private static final String SCRIPT_END_ESCAPED = "<\\/script";
    /** The page scripts, concatenated in this order into one script (one global scope). */
    static final List<String> APP_SCRIPTS = Collections.unmodifiableList(Arrays.asList(
            DIR + "js/i18n.js", DIR + "js/format.js", DIR + "js/html.js", DIR + "js/theme.js", DIR + "js/model/profile.js",
            DIR + "js/model/simulation.js", DIR + "js/model/state.js", DIR + "js/model/hash.js", DIR + "js/charts.js",
            DIR + "js/views/common.js", DIR + "js/views/header.js", DIR + "js/views/usage.js", DIR + "js/views/graph.js",
            DIR + "js/views/panel.js", DIR + "js/views/alternatives.js", DIR + "js/views/table.js", DIR + "js/views/compare.js",
            DIR + "js/views/detail.js", DIR + "js/views/simulator.js", DIR + "js/controller.js", DIR + "js/app.js"));

    /**
     * @param planName name shown in the usage profile title
     * @param timeline measured usage of the execution
     * @param report   cost of every alternative for that execution
     * @param catalog  prices used (only its date is shown)
     * @param graph    execution graph of the TJobs, or null when there is no execution plan
     * @param output   file to write, e.g. {@code usageprofile.html}
     */
    public void generate(String planName, Timeline timeline, CostReport report, PriceCatalog catalog,
                         ExecutionGraph graph, Path output) throws IOException {
        generate(planName, timeline, report, catalog, graph, SutInfo.none(), output);
    }

    /** Same as above, adding the SUT and branch shown in the header ({@link SutInfo#none()} when unknown). */
    public void generate(String planName, Timeline timeline, CostReport report, PriceCatalog catalog,
                         ExecutionGraph graph, SutInfo sut, Path output) throws IOException {
        UsageProfileData data = new UsageProfileData(planName, catalog.fetchedAtEpochMs, report, sample(timeline), graph, sut);
        // the JSON goes last: it is user data and must never be scanned for placeholders
        String html = resource(DIR + "usageprofile.html")
                .replace("/*CSS*/", resource(DIR + "usageprofile.css"))
                .replace("<!--HEADER_LOGO_SVG-->", resource(DIR + "giis-logo.svg"))
                .replace("<!--FOOTER_SVG-->", resource(DIR + "giis-footer.svg"))
                .replace("/*FAVICON*/", favicon())
                .replace("/*ECHARTS*/", inlineScript(resource(DIR + "echarts.min.js")))
                .replace("/*APP*/", inlineScript(appScripts()))
                .replace("/*DATA*/", toJson(data).replace("</", "<\\/"));
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, html.getBytes(StandardCharsets.UTF_8));
    }

    private static String inlineScript(String script) {
        return script.replace(SCRIPT_END, SCRIPT_END_ESCAPED);
    }

    private static String appScripts() throws IOException {
        StringBuilder all = new StringBuilder();
        for (String name : APP_SCRIPTS) {
            all.append(resource(name)).append('\n');
        }
        return all.toString();
    }

    static String toJson(UsageProfileData data) throws IOException {
        return MAPPER.writeValueAsString(data);
    }

    /** Total CPU/memory/disk per second, averaged into at most {@value #MAX_TIMELINE_POINTS} points. */
    private static TimelineSeries sample(Timeline timeline) {
        List<Slice> slices = timeline.slices;
        int bucket = Math.max(1, (int) Math.ceil(slices.size() / (double) MAX_TIMELINE_POINTS));
        List<Integer> t = new ArrayList<>();
        List<Double> cpu = new ArrayList<>();
        List<Double> mem = new ArrayList<>();
        List<Double> disk = new ArrayList<>();
        for (int from = 0; from < slices.size(); from += bucket) {
            int to = Math.min(slices.size(), from + bucket);
            double c = 0;
            double m = 0;
            double d = 0;
            for (Slice slice : slices.subList(from, to)) {
                for (Load l : slice.loads) {
                    c += l.cpu;
                    m += l.memGb;
                    d += l.diskGb;
                }
            }
            int n = to - from;
            t.add(from);
            cpu.add(round(c / n));
            mem.add(round(m / n));
            disk.add(round(d / n));
        }
        return new TimelineSeries(t, cpu, mem, disk);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** The GIIS logo as the page icon: Base64 of the SVG, embedded in a data URI so the page needs no other file. */
    private static String favicon() throws IOException {
        return Base64.getEncoder().encodeToString(resource(DIR + "giis-logo.svg").getBytes(StandardCharsets.UTF_8));
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = UsageProfileGenerator.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Resource not found on the classpath: " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
