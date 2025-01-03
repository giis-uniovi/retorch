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
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.Title;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
/**
 * The {@code JChartTestUtils} class provides the necessary support methods to compare different Charts and deserialize
 * it from expected storing files.
 */
public class ProfilerTestUtils {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

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
        JFreeChart chartActual=deserializeChart(actual+".serialize");
        JFreeChart chartExpected=deserializeChart(expected+".serialize");

        return areChartsEqual(chartActual,chartExpected);
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

    /**
     * The {@code areChartsEqual} compares two JFreeChart by means its title, legends and plots.
     */
    public boolean areChartsEqual(JFreeChart chart1, JFreeChart chart2) {
        if (chart1 == chart2) {
            return true;  // Both are the same instance
        }
        if (chart1 == null || chart2 == null) {
            return false;  // One of them is null
        }
        // Compare titles
        Title title1 = chart1.getTitle();
        Title title2 = chart2.getTitle();
        if (!title1.equals(title2)) {
            return false;
        }
        // Compare the XYPlots (Main visual representation of the chart)
        XYPlot plot1 = (XYPlot) chart1.getPlot();
        XYPlot plot2 = (XYPlot) chart2.getPlot();
        return arePlotsEqual(plot1, plot2);// If all checks passed
    }
    /**
     * The {@code arePlotsEqual} support methods compare two XYPlots by means look if their axis, domain, background
     * renderers and datasets are the same
     * @param plot1  First plot for being compared
     * @param plot2  Second plot for comparing
     */
    private boolean arePlotsEqual(XYPlot plot1, XYPlot plot2) {
        // Compare background paint
        if (!plot1.getBackgroundPaint().equals(plot2.getBackgroundPaint())) {
            return false;
        }
        // Compare domain axis settings (X-axis)
        if (!plot1.getDomainAxis().equals(plot2.getDomainAxis())) {
            return false;
        }
        // Compare range axis settings (Y-axis)
        if (!plot1.getRangeAxis().equals(plot2.getRangeAxis())) {
            return false;
        }
        if (!areRenderersEqual(plot1.getRenderer(), plot2.getRenderer())) {
            return false;
        }
        // Compare datasets (if any)
        return plot1.getDataset().equals(plot2.getDataset());
    }
    /**
     * The {@code areRenderersEqual} support methods compare if two renderers are the same
     * @param renderer1  First renderer for being compared
     * @param renderer2  Second renderer for comparing
     */
    private boolean areRenderersEqual(XYItemRenderer renderer1, XYItemRenderer renderer2) {
        if (renderer1 instanceof XYAreaRenderer && renderer2 instanceof XYAreaRenderer) {
            return true;  // Basic check for XYAreaRenderer equality
        }
        if (renderer1 instanceof StackedXYAreaRenderer2 && renderer2 instanceof StackedXYAreaRenderer2) {
            return true;  // Basic check for StackedXYAreaRenderer2 equality
        }

        return renderer1.equals(renderer2);
    }

    /**
     * The {@code deserializeChart} support methods that enables the deserialization of a JFreeChart from the
     * debugging file created in the {@code ProfilePlotter}.
     * @param filePath  String with the path of the JFreeChart
     */
    public JFreeChart deserializeChart(String filePath) {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(Paths.get(filePath)))) {
            // Read the chart object from the file
            return (JFreeChart) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("The object was not able to read: {}", e.getMessage());
            return null;
        }
    }
}