/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import giis.retorch.profiling.usageprofile.dto.cost.AlternativeDTO;
import giis.retorch.profiling.usageprofile.dto.cost.CostItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.measurement.ExecutionMeasurements;
import giis.retorch.profiling.usageprofile.measurement.Timeline;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/** Data and small helpers shared by the tests of the {@code usageprofile} packages (test code only). */
public final class TestFixtures {

    /** Folder with the sample usage files and the execution plan. */
    public static final String DATA = "src/test/java/giis/retorch/profiling/testdata/usageprofile/";
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private TestFixtures() {
    }

    /** A file of the fixtures shared with the orchestration module ({@code src/test/resources/contract}). */
    public static Path fixture(String name) throws URISyntaxException {
        return Paths.get(TestFixtures.class.getClassLoader().getResource("contract/" + name).toURI());
    }

    /** The timeline of {@code usage.csv} (two TJobs, 81 seconds). */
    public static Timeline timeline() throws IOException {
        return ExecutionMeasurements.read(Paths.get(DATA + "usage.csv")).toTimeline();
    }

    /** {@code usage-containers.csv}: the 12-column file with {@code container} rows. */
    public static ExecutionMeasurements containers() throws IOException {
        return ExecutionMeasurements.read(Paths.get(DATA + "usage-containers.csv"));
    }

    public static PriceCatalog.VmOffer vm(String provider, String name, double vcpu, double mem, double hourly) {
        return new PriceCatalog.VmOffer(provider, "r1", name, vcpu, mem, hourly);
    }

    public static PriceCatalog smallCatalog() {
        return smallCatalogBuilder().build();
    }

    public static PriceCatalog.Builder smallCatalogBuilder() {
        return PriceCatalog.builder().fetchedAt(1700000000000L)
                .addVm(vm("P", "big", 4, 8, 3.6))
                .addContainer(new PriceCatalog.ContainerRate("P", "r1", "Serverless", 1, 1))
                .addStorage(new PriceCatalog.StorageRate("P", "r1", "ssd", 0.1));
    }

    public static boolean hasItem(AlternativeDTO alt, String object, String tjob, String phase) {
        for (CostItemDTO i : alt.items) {
            if (object.equals(i.object) && tjob.equals(i.tjob) && phase.equals(i.phase)) return true;
        }
        return false;
    }

    public static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asText());
        }
        return out;
    }

    public static Set<String> names(JsonNode node) {
        Set<String> result = new HashSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) result.add(it.next());
        return result;
    }

    public static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    /** The JSON embedded in a generated page (the first value after {@code {"plan":}). */
    public static JsonNode embeddedJson(String html) throws IOException {
        int start = html.indexOf("{\"plan\":");
        assertTrue(start >= 0);
        return MAPPER.readTree(html.substring(start)); // reads the first value, ignores what follows
    }

    public static String html(Path out) throws IOException {
        return new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
    }
}
