/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.dto.ContainerUsage;
import giis.retorch.profiling.utils.CsvConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Joins the two files exported by the orchestration scripts: the lifecycle timestamps of every TJob
 * ({@code lifecycletimingsexec*.csv}) and the resource samples ({@code resourceusage-*.csv}), and resamples them into
 * a one-second {@link Timeline} where every instant knows which TJob phase each measured load belongs to.
 */
public class ExecutionMeasurements {

    public static final String COI_SETUP = CsvConstants.COI_SETUP_LABEL;
    public static final String COI_TEARDOWN = CsvConstants.COI_TEARDOWN_LABEL;
    private static final double MILLIS_THRESHOLD = 1e11; // epoch seconds are ~1.7e9, epoch ms ~1.7e12
    private static final double GB = 1024.0 * 1024 * 1024;
    private static final String KIND_PHASE = "phase";
    private static final String KIND_CONTAINER = "container";
    private static final String COL_START = "start";
    private static final String COL_IMAGE = "image";
    private static final String COL_IMAGE_BYTES = "image_bytes";
    private static final String COL_VOLUME_BYTES = "volume_bytes";
    /** TJob name the COI lifecycle phases are exported under. */
    public static final String COI_TJOB = "COI";

    /** Resources consumed, at one second, by the containers of one TJob in a given phase. */
    public static class Load {
        public final String tjob;
        public final String phase;
        public double cpu;
        public double memGb;
        public double diskGb;

        Load(String tjob, String phase) {
            this.tjob = tjob;
            this.phase = phase;
        }
    }

    /** One second of the execution: the COI phase (or null) and the loads of the TJobs running at that moment. */
    public static class Slice {
        public String coiPhase;
        public final List<Load> loads = new ArrayList<>();
    }

    /** The resampled execution; {@code slices.get(i)} describes the second {@code startSec + i}. */
    public static class Timeline {
        public final double startSec;
        public final List<Slice> slices;

        Timeline(double startSec, List<Slice> slices) {
            this.startSec = startSec;
            this.slices = slices;
        }

        /** Highest simultaneous consumption of {@code resource} ("cpu" in cores, "mem" or "disk" in GB). */
        public double peak(String resource) {
            double peak = 0;
            for (Slice s : slices) {
                double sum = 0;
                for (Load l : s.loads) {
                    sum += value(l, resource);
                }
                peak = Math.max(peak, sum);
            }
            return peak;
        }
    }

    /** Duration of the phases (seconds) and peak usage of one TJob; the peaks are null if the TJob has no samples. */
    public static class TJobMetrics {
        /** Seconds from the start of the timeline to the start of the TJob set-up. */
        public final double startSec;
        public final double setupSec;
        public final double execSec;
        public final double teardownSec;
        public final Double peakCpu;
        public final Double peakMemGb;

        TJobMetrics(double[] bounds, double timelineStart, Double peakCpu, Double peakMemGb) {
            this.startSec = bounds[0] - timelineStart;
            this.setupSec = bounds[1] - bounds[0];
            this.execSec = bounds[2] - bounds[1];
            this.teardownSec = bounds[3] - bounds[2];
            this.peakCpu = peakCpu;
            this.peakMemGb = peakMemGb;
        }

        /** Values in the order start, setup, exec, teardown, peak CPU, peak memory. */
        public Double[] toArray() {
            return new Double[]{startSec, setupSec, execSec, teardownSec, peakCpu, peakMemGb};
        }
    }

    static double value(Load l, String resource) {
        switch (resource) {
            case "cpu": return l.cpu;
            case "mem": return l.memGb;
            default: return l.diskGb;
        }
    }

    private static class Window {
        final String tjob;
        final double[] bounds; // setup-start, testexec-start, teardown-start, teardown-end

        Window(String tjob, double[] bounds) {
            this.tjob = tjob;
            this.bounds = bounds;
        }

        String phaseAt(double t) {
            if (t < bounds[0] || t > bounds[3]) return null;
            if (t < bounds[1]) return CsvConstants.TJOB_SETUP_LABEL;
            if (t < bounds[2]) return CsvConstants.TJOB_TEST_EXEC_LABEL;
            return CsvConstants.TJOB_TEARDOWN_LABEL;
        }
    }

    private static class Sample {
        final double sec;
        final double cpu;
        final double memGb;
        final double diskGb;

        Sample(double sec, double cpu, double memGb, double diskGb) {
            this.sec = sec;
            this.cpu = cpu;
            this.memGb = memGb;
            this.diskGb = diskGb;
        }
    }

    /** What a {@code container} row says about a container: first-seen time, image and sizes (bytes converted to GB). */
    private static class ContainerInfo {
        final double firstSeen;
        final String image;
        final double imageGb;
        final double volumeGb;

        ContainerInfo(double firstSeen, String image, double imageGb, double volumeGb) {
            this.firstSeen = firstSeen;
            this.image = image;
            this.imageGb = imageGb;
            this.volumeGb = volumeGb;
        }
    }

    private final List<Window> windows = new ArrayList<>();
    // tjob -> container -> its container row (only in files that have container rows)
    private final Map<String, Map<String, ContainerInfo>> containerInfo = new HashMap<>();
    // tjob -> container -> chronologically ordered samples
    private final Map<String, Map<String, List<Sample>>> samples = new HashMap<>();
    private double coiSetupStart = Double.NaN;
    private double coiSetupEnd = Double.NaN;
    private double coiTeardownStart = Double.NaN;
    private double coiTeardownEnd = Double.NaN;

    /**
     * Reads the single usage file exported by the pipeline. Columns:
     * {@code kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes[;image;image_bytes;volume_bytes]}
     * (the last three are optional, older files do not have them). A {@code phase} row gives the
     * start and end of a lifecycle phase ({@code item}) of a TJob (the COI phases belong to the TJob named
     * {@code COI}); a {@code sample} row gives the CPU, memory and disk of a container ({@code item}) at {@code start}.
     * A {@code container} row (optional) gives, at its {@code start} (the first time the container was seen), the image name
     * and size and the size of the volumes of the container {@code item}. Times are epoch seconds or milliseconds.
     */
    public static ExecutionMeasurements read(Path usageProfileCsv) throws IOException {
        ExecutionMeasurements m = new ExecutionMeasurements();
        Map<String, Map<String, double[]>> phases = new LinkedHashMap<>();
        try (Reader r = Files.newBufferedReader(usageProfileCsv, StandardCharsets.UTF_8)) {
            for (CSVRecord rec : format().parse(r)) {
                String kind = rec.get("kind");
                if (KIND_PHASE.equals(kind)) {
                    phases.computeIfAbsent(rec.get(CsvConstants.TJOB_HEADER), k -> new HashMap<>())
                            .put(rec.get("item"), new double[]{seconds(rec.get(COL_START)), seconds(rec.get("end"))});
                } else if (KIND_CONTAINER.equals(kind)) {
                    m.addContainer(rec);
                } else {
                    m.addSample(rec);
                }
            }
        }
        m.buildWindows(phases, usageProfileCsv);
        for (Map<String, List<Sample>> byContainer : m.samples.values()) {
            for (List<Sample> list : byContainer.values()) {
                list.sort((x, y) -> Double.compare(x.sec, y.sec));
            }
        }
        return m;
    }

    private static CSVFormat format() {
        return CSVFormat.DEFAULT.builder().setDelimiter(CsvConstants.CSV_DELIMITER).setHeader()
                .setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build();
    }

    private static double seconds(String value) {
        double v = Double.parseDouble(value);
        return v > MILLIS_THRESHOLD ? v / 1000.0 : v;
    }

    private void addSample(CSVRecord rec) {
        Sample s = new Sample(seconds(rec.get(COL_START)), Double.parseDouble(rec.get("cpu_percent")) / 100.0,
                Double.parseDouble(rec.get("mem_bytes")) / GB, Double.parseDouble(rec.get("disk_bytes")) / GB);
        List<Sample> list = samples.computeIfAbsent(rec.get(CsvConstants.TJOB_HEADER), k -> new HashMap<>())
                .computeIfAbsent(rec.get("item"), k -> new ArrayList<>());
        list.add(s);
    }

    /** The value of an optional column, or null if the file has no such column or the value is empty. */
    private static String optional(CSVRecord rec, String column) {
        if (!rec.isMapped(column) || !rec.isSet(column) || rec.get(column).isEmpty()) {
            return null;
        }
        return rec.get(column);
    }

    private static double optionalGb(CSVRecord rec, String column) {
        String v = optional(rec, column);
        return v == null ? 0 : Double.parseDouble(v) / GB;
    }

    private void addContainer(CSVRecord rec) {
        String start = optional(rec, COL_START);
        String image = optional(rec, COL_IMAGE);
        ContainerInfo info = new ContainerInfo(start == null ? Double.NEGATIVE_INFINITY : seconds(start),
                image == null ? "" : image, optionalGb(rec, COL_IMAGE_BYTES), optionalGb(rec, COL_VOLUME_BYTES));
        containerInfo.computeIfAbsent(rec.get(CsvConstants.TJOB_HEADER), k -> new HashMap<>())
                .merge(rec.get("item"), info, (a, b) -> a.firstSeen <= b.firstSeen ? a : b);
    }

    private void buildWindows(Map<String, Map<String, double[]>> phases, Path source) throws IOException {
        Map<String, double[]> coi = phases.remove(COI_TJOB);
        if (coi != null && coi.containsKey(COI_SETUP) && coi.containsKey(COI_TEARDOWN)) {
            coiSetupStart = coi.get(COI_SETUP)[0];
            coiSetupEnd = coi.get(COI_SETUP)[1];
            coiTeardownStart = coi.get(COI_TEARDOWN)[0];
            coiTeardownEnd = coi.get(COI_TEARDOWN)[1];
        }
        for (Map.Entry<String, Map<String, double[]>> e : phases.entrySet()) {
            double[] setup = e.getValue().get(CsvConstants.TJOB_SETUP_LABEL);
            double[] exec = e.getValue().get(CsvConstants.TJOB_TEST_EXEC_LABEL);
            double[] teardown = e.getValue().get(CsvConstants.TJOB_TEARDOWN_LABEL);
            if (setup == null || exec == null || teardown == null) {
                throw new IOException("Incomplete lifecycle phases for " + e.getKey() + " in " + source);
            }
            windows.add(new Window(e.getKey(), new double[]{setup[0], exec[0], teardown[0], teardown[1]}));
        }
        if (windows.isEmpty()) {
            throw new IOException("No TJob lifecycle phases found in " + source);
        }
    }

    /** Resamples the execution at one second; each container keeps its last sample until a newer one arrives. */
    public Timeline toTimeline() {
        double[] range = timeRange();
        double start = range[0];
        int n = (int) Math.ceil(range[1] - start) + 1;
        List<Slice> slices = new ArrayList<>(n);
        Map<String, int[]> cursors = new HashMap<>(); // "tjob|container" -> index of the last used sample
        for (int i = 0; i < n; i++) {
            slices.add(sliceAt(start + i, cursors));
        }
        return new Timeline(start, slices);
    }

    /**
     * Metrics of every TJob of the usage file by lower case TJob name: setup, test execution and teardown durations
     * (from the phase windows) and the highest CPU / memory the TJob used in {@code timeline}.
     */
    public Map<String, TJobMetrics> tjobMetrics(Timeline timeline) {
        Map<String, double[]> peaks = new HashMap<>(); // tjob represented as [cpu, mem]
        for (Slice slice : timeline.slices) {
            for (Load l : slice.loads) {
                double[] peak = peaks.computeIfAbsent(l.tjob, k -> new double[2]);
                peak[0] = Math.max(peak[0], l.cpu);
                peak[1] = Math.max(peak[1], l.memGb);
            }
        }
        Map<String, TJobMetrics> result = new HashMap<>();
        for (Window w : windows) {
            double[] peak = peaks.get(w.tjob);
            result.put(w.tjob.toLowerCase(Locale.ROOT),
                    new TJobMetrics(w.bounds, timeline.startSec, peak == null ? null : peak[0], peak == null ? null : peak[1]));
        }
        return result;
    }

    /** First and last second covered by the TJob windows and the COI lifecycle. */
    private double[] timeRange() {
        double start = Double.POSITIVE_INFINITY;
        double end = Double.NEGATIVE_INFINITY;
        for (Window w : windows) {
            start = Math.min(start, w.bounds[0]);
            end = Math.max(end, w.bounds[3]);
        }
        if (!Double.isNaN(coiSetupStart)) {
            start = Math.min(start, coiSetupStart);
            end = Math.max(end, coiTeardownEnd);
        }
        return new double[]{start, end};
    }

    private Slice sliceAt(double t, Map<String, int[]> cursors) {
        Slice slice = new Slice();
        slice.coiPhase = coiPhaseAt(t);
        for (Window w : windows) {
            Load load = loadAt(w, t, cursors);
            if (load != null) {
                slice.loads.add(load);
            }
        }
        return slice;
    }

    private String coiPhaseAt(double t) {
        if (t >= coiSetupStart && t < coiSetupEnd) {
            return COI_SETUP;
        }
        return t >= coiTeardownStart && t <= coiTeardownEnd ? COI_TEARDOWN : null;
    }

    /** The resources used by a TJob at second {@code t}, or null if it is not running or has no samples. */
    private Load loadAt(Window w, double t, Map<String, int[]> cursors) {
        String phase = w.phaseAt(t);
        Map<String, List<Sample>> byContainer = samples.getOrDefault(w.tjob, Collections.emptyMap());
        Map<String, ContainerInfo> infos = containerInfo.getOrDefault(w.tjob, Collections.emptyMap());
        if (phase == null || (byContainer.isEmpty() && infos.isEmpty())) {
            return null;
        }
        Load load = new Load(w.tjob, phase);
        for (Map.Entry<String, List<Sample>> e : byContainer.entrySet()) {
            Sample s = latestSample(cursors, w.tjob + "|" + e.getKey(), e.getValue(), t);
            if (s != null) {
                load.cpu += s.cpu;
                load.memGb += s.memGb;
                load.diskGb += s.diskGb;
            }
        }
        addImagesAndVolumes(load, byContainer, infos, t);
        return load;
    }

    /**
     * Adds to the storage of {@code load} the volumes and the images of the containers that already exist at {@code t}
     * (from their first appearance on; like the samples they are held until the end of the TJob). A shared image counts once.
     */
    private static void addImagesAndVolumes(Load load, Map<String, List<Sample>> byContainer,
                                            Map<String, ContainerInfo> infos, double t) {
        Set<String> images = new HashSet<>();
        for (Map.Entry<String, ContainerInfo> e : infos.entrySet()) {
            ContainerInfo info = e.getValue();
            List<Sample> list = byContainer.get(e.getKey());
            double first = list == null || list.isEmpty() ? info.firstSeen : Math.min(info.firstSeen, list.get(0).sec);
            if (t < first) {
                continue;
            }
            load.diskGb += info.volumeGb;
            if (images.add(info.image)) {
                load.diskGb += info.imageGb;
            }
        }
    }

    /**
     * Usage of every container of every TJob that has container rows and samples, by lower case TJob name, sorted by
     * container name (CPU in cores, memory and storage in GB). Other TJobs are not in the map.
     */
    public Map<String, List<ContainerUsage>> containerUsage() {
        Map<String, List<ContainerUsage>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, ContainerInfo>> e : containerInfo.entrySet()) {
            Map<String, List<Sample>> byContainer = samples.getOrDefault(e.getKey(), Collections.emptyMap());
            if (byContainer.isEmpty()) {
                continue;
            }
            Set<String> names = new TreeSet<>(e.getValue().keySet());
            names.addAll(byContainer.keySet());
            List<ContainerUsage> usage = new ArrayList<>();
            for (String name : names) {
                usage.add(usageOf(name, e.getValue().get(name), byContainer.get(name)));
            }
            result.put(e.getKey().toLowerCase(Locale.ROOT), usage);
        }
        return result;
    }

    private static ContainerUsage usageOf(String name, ContainerInfo info, List<Sample> list) {
        double[] stats = new double[4]; // peak cpu, avg cpu, peak mem, avg mem
        double layer = 0;
        if (list != null) {
            for (Sample s : list) {
                stats[0] = Math.max(stats[0], s.cpu);
                stats[1] += s.cpu / list.size();
                stats[2] = Math.max(stats[2], s.memGb);
                stats[3] += s.memGb / list.size();
                layer = Math.max(layer, s.diskGb);
            }
        }
        return new ContainerUsage(name, info == null ? "" : info.image, stats, layer,
                info == null ? 0 : info.imageGb, info == null ? 0 : info.volumeGb);
    }

    private static Sample latestSample(Map<String, int[]> cursors, String key, List<Sample> list, double t) {
        int[] cur = cursors.computeIfAbsent(key, k -> new int[]{-1});
        while (cur[0] + 1 < list.size() && list.get(cur[0] + 1).sec <= t) {
            cur[0]++;
        }
        return cur[0] >= 0 ? list.get(cur[0]) : null;
    }
}
