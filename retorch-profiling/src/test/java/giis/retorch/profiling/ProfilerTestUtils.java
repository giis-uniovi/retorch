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
import com.itextpdf.svg.converter.SvgConverter;
import giis.retorch.orchestration.model.Capacity;
import giis.retorch.profiling.model.UsageProfile;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * The {@code JChartTestUtils} class provides the necessary support methods to compare different Charts and deserialize
 * it from expected storing files.
 */
public class ProfilerTestUtils {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private String debugPath= "target/debug/";
    public boolean profileComparator(String expectedProfilePath, UsageProfile actualProfile,String coiName) throws IOException {

        UsageProfile expectedProfile=deserializeUsageProfile(expectedProfilePath);
        if (!actualProfile.equals(expectedProfile)) {
            // Define format
            LocalDateTime now = LocalDateTime.now(); // Get current timestamp
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"); // Define format
            String formattedTimestamp = now.format(formatter); // Format the timestamp
            String filename = debugPath + "/" +coiName+ "-"+formattedTimestamp + "_debug.pdf";
            createTestFailPNGReport(expectedProfile, actualProfile, filename);
            return false;
        }
        return true;

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

    public void createTestFailPNGReport(UsageProfile expectedProfile, UsageProfile actualProfile, String pathReport) throws IOException {

        Map<String, JFreeChart> expectedPlots=expectedProfile.getPlots();
        Map<String, JFreeChart> actualPlots=actualProfile.getPlots();



            try (PdfDocument doc = new PdfDocument(new PdfWriter(pathReport, new WriterProperties().setCompressionLevel(0)))) {

                // Create layout document
                Document layoutdoc = new Document(doc);
                ByteArrayOutputStream baos;
                for (String key : Capacity.getCapacityNames()) {

                    JFreeChart actualChart=null;
                    JFreeChart expectedChart=null;
                    if(expectedPlots.containsKey(key)) {
                        expectedChart = expectedPlots.get(key);

                        BufferedImage expectedBufferedImage = expectedChart.createBufferedImage(1200, 400);
                        baos = new ByteArrayOutputStream();
                        ImageIO.write(expectedBufferedImage, "png", baos);
                        byte[] expectedImageBytes = baos.toByteArray();

                        ImageData imageDataExpected = ImageDataFactory.create(expectedImageBytes);
                        Image imageExpected = new Image(imageDataExpected);
                        imageExpected.setFixedPosition(20, 400);
                        imageExpected.scaleToFit(550, 300);
                        layoutdoc.add(imageExpected);

                    }

                    if(actualPlots.containsKey(key)) {
                        actualChart = actualPlots.get(key);
                        BufferedImage actualBufferedImage = actualChart.createBufferedImage(1200, 400);

                        baos = new ByteArrayOutputStream();
                        ImageIO.write(actualBufferedImage, "png", baos);
                        byte[] actualImageBytes = baos.toByteArray();

                        // Load actual image
                        ImageData imageDataActual = ImageDataFactory.create(actualImageBytes);
                        Image imageActual = new Image(imageDataActual);
                        imageActual.setFixedPosition(20, 600);
                        imageActual.scaleToFit(550, 300);
                        // Load expected image
                        layoutdoc.add(imageActual);
                    }


                if(expectedChart!=null && actualChart!=null) {
                    BufferedImage difference = getDifferenceImage(expectedChart, actualChart);

                    // Convert BufferedImage to byte array
                    baos = new ByteArrayOutputStream();
                    ImageIO.write(difference, "png", baos);
                    byte[] imageBytes = baos.toByteArray();
                    ImageData imageData = ImageDataFactory.create(imageBytes);
                    Image imageDifference = new Image(imageData);
                    imageDifference.setFixedPosition(20, 200);
                    imageDifference.scaleToFit(550, 300);
                    layoutdoc.add(imageDifference);
                }
                //Difference between images


                // Add ACTUAL label
                Paragraph actualLabel = new Paragraph("ACTUAL "+key);
                actualLabel.setFixedPosition(20, 780, 100);
                layoutdoc.add(actualLabel);
                // Add actual image

                // Add EXPECTED label
                Paragraph expectedLabel = new Paragraph("EXPECTED "+key);
                expectedLabel.setFixedPosition(20, 580, 100);
                layoutdoc.add(expectedLabel);
                // Add expected image

                // Add EXPECTED label
                Paragraph differenceLabel = new Paragraph("DIFFERENCE "+key);
                differenceLabel.setFixedPosition(20, 380, 100);
                layoutdoc.add(differenceLabel);
                // Add the difference image

                // Add paths of the images
                    layoutdoc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                }
                // Close the document
                layoutdoc.close();
            }

    }

    public static BufferedImage getDifferenceImage(JFreeChart chart1, JFreeChart chart2) {
        // convert images to pixel arrays...

        BufferedImage img1= chart1.createBufferedImage(1200, 400);
        BufferedImage img2= chart2.createBufferedImage(1200, 400);
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
     * The {@code deserializeUsageProfile} support methods that enables the deserialization of a UsageProfile from the
     * debugging file created in the {@code ProfilePlotter}.
     * @param filePath  String with the path of the JFreeChart
     */
    public UsageProfile deserializeUsageProfile(String filePath) {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(Paths.get(filePath)))) {
            // Read the chart object from the file
            return (UsageProfile) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("The object was not able to read: {}", e.getMessage());
            return null;
        }
    }
}