package giis.retorch.profiling.report;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import giis.retorch.profiling.model.UsageProfile;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The {@code UsageProfileReportGenerator} class generates a PDF report that consolidates the Usage Profile
 * charts for all {@code CloudObjectInstance}s into a single document.
 */
public class UsageProfileReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(UsageProfileReportGenerator.class);

    private static final int CHART_RENDER_WIDTH  = 1200;
    private static final int CHART_RENDER_HEIGHT = 400;
    private static final float CHART_PDF_WIDTH   = 500f;
    private static final float CHART_PDF_HEIGHT  = 270f;

    private static final float FONT_TITLE    = 20f;
    private static final float FONT_SUBTITLE = 14f;
    private static final float FONT_CAPACITY = 11f;

    /**
     * Generates a PDF report named {@code <planName>-UsageProfile.pdf} in {@code outputPath}, containing
     * one section per {@code CloudObjectInstance} with its capacity Usage Profile charts.
     *
     * @param profiles   list of {@code UsageProfile}s — one per COI, in the order they were generated.
     * @param outputPath folder where the PDF will be written.
     * @param planName   name of the {@code ExecutionPlan}, used as the report title and file name prefix.
     * @throws IOException if the PDF cannot be written.
     */
    public void generateReport(List<UsageProfile> profiles, String outputPath, String planName) throws IOException {
        String reportPath = outputPath + planName + "-UsageProfile.pdf";
        new File(outputPath).mkdirs();
        log.info("Generating Usage Profile PDF report: {}", reportPath);

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(reportPath));
             Document doc = new Document(pdf, PageSize.A4)) {

            addCoverPage(doc, planName);

            for (UsageProfile profile : profiles) {
                if (profile == null || profile.getPlots().isEmpty()) {
                    log.warn("Skipping empty or null UsageProfile");
                    continue;
                }
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                addCOISection(doc, profile);
            }
        }
        log.info("Usage Profile PDF report saved to: {}", reportPath);
    }

    private void addCoverPage(Document doc, String planName) {
        doc.add(new Paragraph("Usage Profile Report")
                .setFontSize(FONT_TITLE)
                .simulateBold()
                .setMarginTop(200f)
                .setHorizontalAlignment(HorizontalAlignment.CENTER));
        doc.add(new Paragraph("Execution Plan: " + planName)
                .setFontSize(FONT_SUBTITLE)
                .setHorizontalAlignment(HorizontalAlignment.CENTER));
    }

    private void addCOISection(Document doc, UsageProfile profile) throws IOException {
        doc.add(new Paragraph("Cloud Object Instance: " + profile.getCloudObjectID())
                .setFontSize(FONT_SUBTITLE)
                .simulateBold()
                .setMarginBottom(10f));

        for (Map.Entry<String, JFreeChart> entry : profile.getPlots().entrySet()) {
            doc.add(new Paragraph(entry.getKey())
                    .setFontSize(FONT_CAPACITY)
                    .setMarginBottom(4f));
            doc.add(renderChart(entry.getValue()));
        }
    }

    private Image renderChart(JFreeChart chart) throws IOException {
        BufferedImage bufferedImage = chart.createBufferedImage(CHART_RENDER_WIDTH, CHART_RENDER_HEIGHT);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        ImageData imageData = ImageDataFactory.create(baos.toByteArray());
        return new Image(imageData).scaleToFit(CHART_PDF_WIDTH, CHART_PDF_HEIGHT).setMarginBottom(8f);
    }
}
