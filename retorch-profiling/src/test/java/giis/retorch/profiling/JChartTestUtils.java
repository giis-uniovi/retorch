package giis.retorch.profiling;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.title.Title;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JChartTestUtils {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
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

        // Compare renderers
        if (!areRenderersEqual(plot1.getRenderer(), plot2.getRenderer())) {
            return false;
        }

        // Compare datasets (if any)
        if (!plot1.getDataset().equals(plot2.getDataset())) {
            return false;
        }

        return true;
    }

    private boolean areRenderersEqual(org.jfree.chart.renderer.xy.XYItemRenderer renderer1, org.jfree.chart.renderer.xy.XYItemRenderer renderer2) {
        if (renderer1 instanceof XYAreaRenderer && renderer2 instanceof XYAreaRenderer) {
            return true;  // Basic check for XYAreaRenderer equality
        }
        if (renderer1 instanceof StackedXYAreaRenderer2 && renderer2 instanceof StackedXYAreaRenderer2) {
            return true;  // Basic check for StackedXYAreaRenderer2 equality
        }

        return renderer1.equals(renderer2);
    }

    // Serialize the JFreeChart object to a file
    public void serializeChart(JFreeChart chart, String filePath) {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            // Write the chart object to the file
            out.writeObject(chart);
           log.info("Chart serialized and saved to: {}", filePath);
        } catch (IOException e) {
            log.error("Chart not able to write: {}", e.getMessage());
        }
    }

    // Deserialize the JFreeChart object from a file
    public JFreeChart deserializeChart(String filePath) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath))) {
            // Read the chart object from the file
            return (JFreeChart) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

}
