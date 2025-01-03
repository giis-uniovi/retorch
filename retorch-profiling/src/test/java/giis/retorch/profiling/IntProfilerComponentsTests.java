package giis.retorch.profiling;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.svg.converter.SvgConverter;
import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.profiling.profilegeneration.ProfileGenerator;
import giis.retorch.profiling.profilegeneration.ProfilePlotter;
import org.jfree.chart.JFreeChart;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class IntProfilerComponentsTests {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String outBasePath = "target/test-outputs/profiler";
    private final String inBasePath = "src/test/java/giis/retorch/profiling/testdata/profilegen";
    private final String expOutBasePath = "src/test/resources/expected_out/profiles";
    private final String debugOutBasePath = "target/debug";
    private ProfileGeneratorUtils utils;
    private ProfileGenerator generator;
    private ProfilePlotter plotter;
    private JChartTestUtils chartUtil;
    private final String outputProfilePath = outBasePath + "/profiles";

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() {
        log.info("****** Running test: {} ******", testName.getMethodName());
        log.debug("Creating the tmp directory to store methods output");
        this.utils = new ProfileGeneratorUtils();
        this.generator = new ProfileGenerator();
        chartUtil = new JChartTestUtils();
        List<File> listFiles = new LinkedList<>();
        listFiles.add(new File(outBasePath));
        listFiles.add(new File(outBasePath + "/profiles"));
        listFiles.add(new File(debugOutBasePath));
        for (File dir : listFiles) {
            if (dir.exists()) {
                log.debug("The directory already exists: {}", dir.getAbsolutePath());
            } else {
                try {
                    boolean isCreated = dir.mkdirs();
                    if (isCreated) {
                        log.debug("Directory successfully created: {}", dir.getAbsolutePath());
                    } else {
                        log.warn("Not able to create the directory: {}", dir.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.error("Something wrong happened creating the directory {} the exception stacktrace is:\n {}", dir.getAbsolutePath(), e.getStackTrace());
                    fail("The directory" + dir.getAbsolutePath() + "cannot be created");
                }
            }
        }
        log.info("****** Set-up for test: {} ended ******", testName.getMethodName());
    }

    @Test
    public void testIntegrationProfilePlotterVM() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = utils.generateExecutionPlan();

        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_vm.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_vm.csv", outBasePath + "/profileIntegrationCOI_VM.csv", utils.generateVMCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_VM.csv");

        plotter.generateTotalTJobUsageProfileCharts(outputProfilePath, plan.getName() + "-vm");
        assertTrue("The VM memory profiles are not equal", profileComparator(outputProfilePath + "/" + plan.getName() + "-vm-memory.png", expOutBasePath + "/" + plan.getName() + "-vm-memory.png", debugOutBasePath));
        assertTrue("The VM processor profiles are not equal", profileComparator(outputProfilePath + "/" + plan.getName() + "-vm-processor.png", expOutBasePath + "/" + plan.getName() + "-vm-processor.png", debugOutBasePath));
        assertTrue("The VM storage profiles are not equal", profileComparator(outputProfilePath + "/" + plan.getName() + "-vm-storage.png", expOutBasePath + "/" + plan.getName() + "-vm-storage.png", debugOutBasePath));
    }

    @Test
    public void testIntegrationProfilePlotterContainer() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = utils.generateExecutionPlan();
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_int_container.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_int_container.csv", outBasePath + "/profileIntegrationCOI_container.csv", utils.generateContainersCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_container.csv");
        plotter.generateTotalTJobUsageProfileCharts(outBasePath + "/profiles", plan.getName() + "-containers");

        assertTrue("The Containers memory profiles are not equal", profileComparator(outputProfilePath + "/" + plan.getName() + "-containers-memory.png", expOutBasePath + "/" + plan.getName() + "-containers-memory.png", debugOutBasePath));
        assertTrue("The Containers processor profiles are not equal", profileComparator(outputProfilePath + "/" + plan.getName() + "-containers-processor.png", expOutBasePath + "/" + plan.getName() + "-containers-processor.png", debugOutBasePath));
        assertTrue("The Containers storage profiles are not equal", profileComparator(outputProfilePath + "/" + plan.getName() + "-containers-storage.png", expOutBasePath + "/" + plan.getName() + "-containers-storage.png", debugOutBasePath));

    }

    @Test
    public void testIntegrationProfilePlotterServices() throws IOException, NoFinalActivitiesException, EmptyInputException {
        ExecutionPlan plan = utils.generateExecutionPlan();
        generator.generateExecutionPlanCapacitiesUsage(plan, inBasePath + "/imp_avg_dataset.csv", outBasePath + "/output_profile_int_services.csv", 3600, 4);
        generator.generateCOIContractedCapacities(outBasePath + "/output_profile_int_services.csv", outBasePath + "/profileIntegrationCOI_services.csv", utils.generateBrowserServiceCloudObjectInstances());
        plotter = new ProfilePlotter(outBasePath + "/profileIntegrationCOI_services.csv");
        plotter.generateTotalTJobUsageProfileCharts(outBasePath + "/profiles", plan.getName() + "-services");
        assertTrue("The Services slots profiles are not equal", profileComparator(outputProfilePath + "/" + plan.getName() + "-services-slots.png", expOutBasePath + "/" + plan.getName() + "-services-slots.png", debugOutBasePath));
    }

    public boolean profileComparator(String expectedProfilePath, String actualProfilePath, String debugPath) throws IOException {
        if (!testImagesAreEqual(expectedProfilePath, actualProfilePath)) {
            String filename = debugPath + "/" + (expectedProfilePath + actualProfilePath).hashCode() + "_debug.pdf";
            log.error("The svg files expected: {} actual {} dont match, see more details in the merged file: {} ", expectedProfilePath, actualProfilePath, filename);
            createTestFailPNGReport(expectedProfilePath, actualProfilePath, filename);
            return false;
        }
        return true;

    }

    public boolean testImagesAreEqual(String actual, String expected) {
        JFreeChart chartActual=chartUtil.deserializeChart(actual+".serialize");
        JFreeChart chartExpected=chartUtil.deserializeChart(expected+".serialize");

        return chartUtil.areChartsEqual(chartActual,chartExpected);
    }

    public void createTestFailSVGReport(String pathExpected, String pathActual, String pathReport) throws IOException {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pathReport, new WriterProperties().setCompressionLevel(0)))) {
            Image imageActual = SvgConverter.convertToImage(Files.newInputStream(Paths.get(pathActual)), doc);
            imageActual.setFixedPosition(20, 600);
            imageActual.scaleToFit(550, 300);
            Image imageExpected = SvgConverter.convertToImage(Files.newInputStream(Paths.get(pathExpected)), doc);
            imageExpected.setFixedPosition(20, 400);
            imageExpected.scaleToFit(550, 300);
            Document layoutdoc = new Document(doc);
            // Add ACTUAL label
            Paragraph actualLabel = new Paragraph("ACTUAL");
            actualLabel.setFixedPosition(20, 780, 100);
            layoutdoc.add(actualLabel);
            //Add image actual
            layoutdoc.add(imageActual);
            // Add EXPECTED label
            Paragraph expectedLabel = new Paragraph("EXPECTED");
            expectedLabel.setFixedPosition(20, 580, 100);
            layoutdoc.add(expectedLabel);
            //Add the expected label
            layoutdoc.add(imageExpected);
            //Add the paths of the images
            Paragraph expectedPath = new Paragraph("EXPECTED, stored in: " + pathExpected);
            expectedPath.setFixedPosition(20, 250, 550);
            layoutdoc.add(expectedPath);
            // Add ACTUAL label
            Paragraph actualPath = new Paragraph("ACTUAL, stored in: " + pathActual);
            actualPath.setFixedPosition(20, 300, 550);
            layoutdoc.add(actualPath);
            layoutdoc.close();
        }
    }

    public void createTestFailPNGReport(String pathExpected, String pathActual, String pathReport) throws IOException {

        try (PdfDocument doc = new PdfDocument(new PdfWriter(pathReport, new WriterProperties().setCompressionLevel(0)))) {
            // Load actual image
            ImageData imageDataActual = ImageDataFactory.create(pathActual);
            Image imageActual = new Image(imageDataActual);
            imageActual.setFixedPosition(20, 600);
            imageActual.scaleToFit(550, 300);
            // Load expected image
            ImageData imageDataExpected = ImageDataFactory.create(pathExpected);
            Image imageExpected = new Image(imageDataExpected);
            imageExpected.setFixedPosition(20, 400);
            imageExpected.scaleToFit(550, 300);
            //Difference between images
            BufferedImage difference = getDifferenceImage(
                    ImageIO.read(new File(pathActual)),
                    ImageIO.read(new File(pathExpected)));
            // Convert BufferedImage to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(difference, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            ImageData imageData = ImageDataFactory.create(imageBytes);
            Image imageDifference = new Image(imageData);
            imageDifference.setFixedPosition(20, 200);
            imageDifference.scaleToFit(550, 300);
            // Create layout document
            Document layoutdoc = new Document(doc);
            // Add ACTUAL label
            Paragraph actualLabel = new Paragraph("ACTUAL");
            actualLabel.setFixedPosition(20, 780, 100);
            layoutdoc.add(actualLabel);
            // Add actual image
            layoutdoc.add(imageActual);
            // Add EXPECTED label
            Paragraph expectedLabel = new Paragraph("EXPECTED");
            expectedLabel.setFixedPosition(20, 580, 100);
            layoutdoc.add(expectedLabel);
            // Add expected image
            layoutdoc.add(imageExpected);
            // Add EXPECTED label
            Paragraph differenceLabel = new Paragraph("DIFFERENCE");
            differenceLabel.setFixedPosition(20, 380, 100);
            layoutdoc.add(differenceLabel);
            // Add the difference image
            layoutdoc.add(imageDifference);
            // Add paths of the images
            Paragraph expectedPath = new Paragraph("EXPECTED, stored in: " + pathExpected);
            expectedPath.setFixedPosition(20, 50, 550);
            layoutdoc.add(expectedPath);
            Paragraph actualPath = new Paragraph("ACTUAL, stored in: " + pathActual);
            actualPath.setFixedPosition(20, 100, 550);
            layoutdoc.add(actualPath);
            // Close the document
            layoutdoc.close();
        }
    }

    public static BufferedImage getDifferenceImage(BufferedImage img1, BufferedImage img2) {
        // convert images to pixel arrays...
        final int w = img1.getWidth(), h = img1.getHeight(), highlight = Color.MAGENTA.getRGB();
        final int[] p1 = img1.getRGB(0, 0, w, h, null, 0, w);
        final int[] p2 = img2.getRGB(0, 0, w, h, null, 0, w);
        // compare img1 to img2, pixel by pixel. If different, highlight img1's pixel...
        for (int i = 0; i < p1.length; i++) {
            if (p1[i] != p2[i]) {
                p1[i] = highlight;
            }
        }
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, p1, 0, w);
        return out;
    }
}