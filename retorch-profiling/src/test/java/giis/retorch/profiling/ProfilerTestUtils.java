package giis.retorch.profiling;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.AreaBreakType;
import giis.retorch.profiling.model.UsageProfile;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.slf4j.Logger;

import static org.junit.Assert.fail;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The {@code JChartTestUtils} class provides the necessary support methods to compare different Charts and deserialize
 * it from expected storing files.
 */
public class ProfilerTestUtils {

    private static final double DOUBLE_TOLERANCE = 1e-9;
    private static final String DATA_PREFIX = "data.";

    private final String debugPath = System.getProperty("profiler.debug.path", "target/debug/");

    public static void ensureDirectoryExists(String path, Logger log) {
        File dir = new File(path);
        if (dir.exists()) {
            log.debug("The directory already exists: {}", dir.getAbsolutePath());
            return;
        }
        try {
            boolean isCreated = dir.mkdirs();
            if (isCreated) {
                log.debug("Directory successfully created: {}", dir.getAbsolutePath());
            } else {
                log.warn("Not able to create the directory: {}", dir.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Something wrong happened creating the directory {}: {}", dir.getAbsolutePath(), e.getMessage());
            fail("The directory " + dir.getAbsolutePath() + " cannot be created");
        }
    }

    /**
     * The {@code profileComparator} method compares the {@code cloudObjectID}, {@code planName} and the numeric data
     * series of every plot of {@code actualProfile} against the expected values stored in the plain-text fixture
     * located in {@code expectedProfilePath}.
     */
    public boolean profileComparator(String expectedProfilePath, UsageProfile actualProfile, String coiName) throws IOException {

        ExpectedUsageProfile expectedProfile = readExpectedUsageProfile(expectedProfilePath);
        Map<String, Map<String, double[]>> actualData = extractProfileData(actualProfile);

        boolean matches = Objects.equals(actualProfile.getCloudObjectID(), expectedProfile.cloudObjectID)
                && Objects.equals(actualProfile.getPlanName(), expectedProfile.planName)
                && dataEquals(actualData, expectedProfile.data);

        if (!matches) {
            // Define format
            LocalDateTime now = LocalDateTime.now(); // Get current timestamp
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"); // Define format
            String formattedTimestamp = now.format(formatter); // Format the timestamp
            String filename = debugPath + "/" + coiName + "-" + formattedTimestamp + "_debug.pdf";
            createTestFailPNGReport(expectedProfile, actualProfile, actualData, filename);
            return false;
        }
        return true;

    }

    /**
     * The {@code createTestFailPNGReport} support method generates a PDF report listing the mismatching data series
     * found by {@code profileComparator}, together with the actual plots of the {@code UsageProfile}, to ease the
     * debugging of a failed comparison.
     */
    private void createTestFailPNGReport(ExpectedUsageProfile expectedProfile, UsageProfile actualProfile,
                                          Map<String, Map<String, double[]>> actualData, String pathReport) throws IOException {

        try (PdfDocument doc = new PdfDocument(new PdfWriter(pathReport, new WriterProperties().setCompressionLevel(0)))) {
            // Create layout document
            Document docLayout = new Document(doc);

            docLayout.add(new Paragraph("EXPECTED cloudObjectID=" + expectedProfile.cloudObjectID
                    + ", planName=" + expectedProfile.planName
                    + ", plots=" + expectedProfile.data.keySet()));
            docLayout.add(new Paragraph("ACTUAL   cloudObjectID=" + actualProfile.getCloudObjectID()
                    + ", planName=" + actualProfile.getPlanName()
                    + ", plots=" + actualData.keySet()));

            for (String plotKey : union(expectedProfile.data.keySet(), actualData.keySet())) {
                Map<String, double[]> expectedSeries = expectedProfile.data.getOrDefault(plotKey, Collections.emptyMap());
                Map<String, double[]> actualSeries = actualData.getOrDefault(plotKey, Collections.emptyMap());
                for (String seriesKey : union(expectedSeries.keySet(), actualSeries.keySet())) {
                    double[] expectedValues = expectedSeries.get(seriesKey);
                    double[] actualValues = actualSeries.get(seriesKey);
                    if (!almostEquals(expectedValues, actualValues)) {
                        docLayout.add(new Paragraph("MISMATCH plot=" + plotKey + ", series=" + seriesKey
                                + ", expected=" + Arrays.toString(expectedValues)
                                + ", actual=" + Arrays.toString(actualValues)));
                    }
                }
            }

            for (Map.Entry<String, JFreeChart> entry : actualProfile.getPlots().entrySet()) {
                docLayout.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                docLayout.add(new Paragraph("ACTUAL " + entry.getKey()));

                BufferedImage actualBufferedImage = entry.getValue().createBufferedImage(1200, 400);
                ByteArrayOutputStream byoutputDocument = new ByteArrayOutputStream();
                ImageIO.write(actualBufferedImage, "png", byoutputDocument);

                ImageData imageDataActual = ImageDataFactory.create(byoutputDocument.toByteArray());
                Image imageActual = new Image(imageDataActual);
                imageActual.scaleToFit(550, 300);
                docLayout.add(imageActual);
            }
            // Close the document
            docLayout.close();
        }
    }

    /**
     * The {@code extractProfileData} support method extracts the numeric data series (the Y values of every series of
     * every {@code XYDataset} of every plot) of a {@code UsageProfile} into a plain {@code Map} structure, so that it
     * can be compared and stored without relying on the Java serialization of {@code JFreeChart} objects, whose binary
     * format depends on the {@code serialVersionUID} of third-party charting classes and breaks whenever those
     * libraries are upgraded.
     *
     * @return a Map from plot key (the {@code Capacity} name) to a Map from "{@code datasetIndex.seriesName}" to the
     * series' Y values.
     */
    public static Map<String, Map<String, double[]>> extractProfileData(UsageProfile profile) {
        Map<String, Map<String, double[]>> result = new TreeMap<>();
        for (Map.Entry<String, JFreeChart> plotEntry : profile.getPlots().entrySet()) {
            Map<String, double[]> seriesMap = new TreeMap<>();
            XYPlot plot = plotEntry.getValue().getXYPlot();
            for (int datasetIndex = 0; datasetIndex < plot.getDatasetCount(); datasetIndex++) {
                XYDataset dataset = plot.getDataset(datasetIndex);
                if (dataset == null) {
                    continue;
                }
                for (int series = 0; series < dataset.getSeriesCount(); series++) {
                    String seriesName = datasetIndex + "." + dataset.getSeriesKey(series);
                    double[] values = new double[dataset.getItemCount(series)];
                    for (int item = 0; item < values.length; item++) {
                        values[item] = dataset.getYValue(series, item);
                    }
                    seriesMap.put(seriesName, values);
                }
            }
            result.put(plotEntry.getKey(), seriesMap);
        }
        return result;
    }

    /**
     * The {@code readExpectedUsageProfile} support method reads the expected {@code cloudObjectID}, {@code planName}
     * and plot data series from a plain-text properties fixture written by {@link #writeExpectedUsageProfile}.
     *
     * @param filePath String with the path of the properties file with the expected profile
     */
    public ExpectedUsageProfile readExpectedUsageProfile(String filePath) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get(filePath))) {
            properties.load(in);
        }

        ExpectedUsageProfile expectedProfile = new ExpectedUsageProfile();
        expectedProfile.cloudObjectID = properties.getProperty("cloudObjectID");
        expectedProfile.planName = properties.getProperty("planName");
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(DATA_PREFIX)) {
                String[] plotAndSeries = name.substring(DATA_PREFIX.length()).split("\\.", 2);
                String plotKey = plotAndSeries[0];
                String seriesKey = plotAndSeries[1];
                expectedProfile.data.computeIfAbsent(plotKey, k -> new TreeMap<>())
                        .put(seriesKey, parseValues(properties.getProperty(name)));
            }
        }
        return expectedProfile;
    }

    /**
     * The {@code writeExpectedUsageProfile} support method (re)generates the plain-text properties fixture expected by
     * {@link #readExpectedUsageProfile} from a {@code UsageProfile}. It can be used to regenerate the expected fixtures
     * located in {@code src/test/resources/expected_out/profiles} whenever the data they represent legitimately changes.
     */
    public void writeExpectedUsageProfile(UsageProfile profile, String filePath) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("cloudObjectID", profile.getCloudObjectID());
        properties.setProperty("planName", profile.getPlanName());
        properties.setProperty("plotKeys", String.join(",", new TreeSet<>(profile.getPlots().keySet())));

        for (Map.Entry<String, Map<String, double[]>> plotEntry : extractProfileData(profile).entrySet()) {
            for (Map.Entry<String, double[]> seriesEntry : plotEntry.getValue().entrySet()) {
                StringBuilder values = new StringBuilder();
                for (double value : seriesEntry.getValue()) {
                    if (values.length() > 0) {
                        values.append(',');
                    }
                    values.append(value);
                }
                properties.setProperty(DATA_PREFIX + plotEntry.getKey() + "." + seriesEntry.getKey(), values.toString());
            }
        }

        try (OutputStream out = Files.newOutputStream(Paths.get(filePath))) {
            properties.store(out, "Expected UsageProfile fixture generated by ProfilerTestUtils.writeExpectedUsageProfile");
        }
    }

    private static double[] parseValues(String csvValues) {
        if (csvValues.isEmpty()) {
            return new double[0];
        }
        String[] tokens = csvValues.split(",");
        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Double.parseDouble(tokens[i].trim());
        }
        return values;
    }

    private static boolean dataEquals(Map<String, Map<String, double[]>> actual, Map<String, Map<String, double[]>> expected) {
        if (!actual.keySet().equals(expected.keySet())) {
            return false;
        }
        for (String plotKey : actual.keySet()) {
            Map<String, double[]> actualSeries = actual.get(plotKey);
            Map<String, double[]> expectedSeries = expected.get(plotKey);
            if (!actualSeries.keySet().equals(expectedSeries.keySet())) {
                return false;
            }
            for (String seriesKey : actualSeries.keySet()) {
                if (!almostEquals(actualSeries.get(seriesKey), expectedSeries.get(seriesKey))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean almostEquals(double[] a, double[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > DOUBLE_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.addAll(b);
        return result;
    }

    /**
     * The {@code ExpectedUsageProfile} class holds the expected {@code cloudObjectID}, {@code planName} and plot data
     * series read from a fixture file by {@link #readExpectedUsageProfile}.
     */
    public static class ExpectedUsageProfile {
        public String cloudObjectID;
        public String planName;
        public final Map<String, Map<String, double[]>> data = new TreeMap<>();
    }
}
