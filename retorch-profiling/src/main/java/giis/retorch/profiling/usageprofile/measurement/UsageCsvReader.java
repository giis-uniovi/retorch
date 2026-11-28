/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import giis.retorch.profiling.usageprofile.dto.measurement.Phase;
import giis.retorch.profiling.usageprofile.measurement.UsageData.ContainerInfo;
import giis.retorch.profiling.usageprofile.measurement.UsageData.Sample;
import giis.retorch.profiling.usageprofile.measurement.UsageData.Window;
import giis.retorch.profiling.utils.CsvConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses and validates the single usage file exported by the pipeline. Columns:
 * {@code kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes[;image;image_bytes;volume_bytes]}
 * (the last three are optional, older files do not have them). A {@code phase} row gives the
 * start and end of a lifecycle phase ({@code item}) of a TJob (the COI phases belong to the TJob named
 * {@code COI}); a {@code sample} row gives the CPU, memory and disk of a container ({@code item}) at {@code start}.
 * A {@code container} row (optional) gives, at its {@code start} (the first time the container was seen), the image name
 * and size and the size of the volumes of the container {@code item}. Times are epoch seconds or milliseconds.
 */
final class UsageCsvReader {

    /** TJob name the COI lifecycle phases are exported under. */
    static final String COI_TJOB = "COI";
    private static final String COL_START = "start";
    private static final String COL_ITEM = "item";
    private static final String COL_IMAGE = "image";
    private static final String COL_IMAGE_BYTES = "image_bytes";
    private static final String COL_VOLUME_BYTES = "volume_bytes";
    /** The columns of the file written by {@code resourcemonitor.sh export}; the last three are optional when reading. */
    static final List<String> COLUMNS = java.util.Collections.unmodifiableList(java.util.Arrays.asList("kind",
            CsvConstants.TJOB_HEADER, "stage", COL_ITEM, COL_START, "end", "cpu_percent", "mem_bytes", "disk_bytes", COL_IMAGE,
            COL_IMAGE_BYTES, COL_VOLUME_BYTES));
    private static final double MILLIS_THRESHOLD = 1e11; // epoch seconds are ~1.7e9, epoch ms ~1.7e12
    private static final double GB = 1024.0 * 1024 * 1024;
    private static final String KIND_PHASE = "phase";
    private static final String KIND_CONTAINER = "container";

    private final UsageData data = new UsageData();

    private UsageCsvReader() {
    }

    static UsageData read(Path usageProfileCsv) throws IOException {
        UsageCsvReader reader = new UsageCsvReader();
        Map<String, Map<String, double[]>> phases = new LinkedHashMap<>();
        try (Reader r = Files.newBufferedReader(usageProfileCsv, StandardCharsets.UTF_8)) {
            for (CSVRecord rec : format().parse(r)) {
                reader.addRecord(rec, phases);
            }
        }
        reader.buildWindows(phases, usageProfileCsv);
        for (Map<String, List<Sample>> byContainer : reader.data.samples.values()) {
            for (List<Sample> list : byContainer.values()) {
                list.sort((x, y) -> Double.compare(x.sec, y.sec));
            }
        }
        return reader.data;
    }

    private void addRecord(CSVRecord rec, Map<String, Map<String, double[]>> phases) {
        String kind = rec.get("kind");
        if (KIND_PHASE.equals(kind)) {
            phases.computeIfAbsent(rec.get(CsvConstants.TJOB_HEADER), k -> new HashMap<>())
                    .put(rec.get(COL_ITEM), new double[]{seconds(rec.get(COL_START)), seconds(rec.get("end"))});
        } else if (KIND_CONTAINER.equals(kind)) {
            addContainer(rec);
        } else {
            addSample(rec);
        }
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
        data.samples.computeIfAbsent(rec.get(CsvConstants.TJOB_HEADER), k -> new HashMap<>())
                .computeIfAbsent(rec.get(COL_ITEM), k -> new ArrayList<>()).add(s);
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
        data.containerInfo.computeIfAbsent(rec.get(CsvConstants.TJOB_HEADER), k -> new HashMap<>())
                .merge(rec.get(COL_ITEM), info, (a, b) -> a.firstSeen <= b.firstSeen ? a : b);
    }

    private void buildWindows(Map<String, Map<String, double[]>> phases, Path source) throws IOException {
        Map<String, double[]> coi = phases.remove(COI_TJOB);
        String coiSetup = Phase.COI_SETUP.label();
        String coiTeardown = Phase.COI_TEARDOWN.label();
        if (coi != null && coi.containsKey(coiSetup) && coi.containsKey(coiTeardown)) {
            data.coiSetupStart = coi.get(coiSetup)[0];
            data.coiSetupEnd = coi.get(coiSetup)[1];
            data.coiTeardownStart = coi.get(coiTeardown)[0];
            data.coiTeardownEnd = coi.get(coiTeardown)[1];
        }
        for (Map.Entry<String, Map<String, double[]>> e : phases.entrySet()) {
            double[] setup = e.getValue().get(Phase.TJOB_SETUP.label());
            double[] exec = e.getValue().get(Phase.TJOB_TESTEXEC.label());
            double[] teardown = e.getValue().get(Phase.TJOB_TEARDOWN.label());
            if (setup == null || exec == null || teardown == null) {
                throw new IOException("Incomplete lifecycle phases for " + e.getKey() + " in " + source);
            }
            data.windows.add(new Window(e.getKey(), new double[]{setup[0], exec[0], teardown[0], teardown[1]}));
        }
        if (data.windows.isEmpty()) {
            throw new IOException("No TJob lifecycle phases found in " + source);
        }
    }
}
