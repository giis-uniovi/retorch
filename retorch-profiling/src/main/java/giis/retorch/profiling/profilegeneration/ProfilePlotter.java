package giis.retorch.profiling.profilegeneration;

import giis.retorch.orchestration.model.TJob;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.batik.dom.GenericDOMImplementation;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.XYSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;

public class ProfilePlotter {

    private static final Logger log = LoggerFactory.getLogger(ProfilePlotter.class);
    List<CSVRecord> csvProfileRecords;
    private static final String CAPACITY_HEADER = "capacity";
    private static final String LIFECYCLE_HEADER = "lifecyclephase";
    public ProfilePlotter(String path) {
        try (FileReader fileReader = new FileReader(path)) {
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setDelimiter(";").setHeader()
                    .setSkipHeaderRecord(true)
                    .build();
            csvProfileRecords = csvFormat.parse(fileReader).getRecords();
        } catch (IOException e) {
            log.error("Error opening the file: {}", e.getMessage());
        }
    }

    public void generateTotalTJobUsageProfileCharts(String outputFolder, String planName) {
        Map<String, DefaultTableXYDataset> capacitiesData = new HashMap<>();
        Map<String, DefaultTableXYDataset> contractedCapacitiesData = new HashMap<>();

        loadCapacitiesData(capacitiesData,contractedCapacitiesData);
        Map<String, XYPlot> mapPlotsUsage = generateXYCloudObjectPlots(contractedCapacitiesData);
        createAndSaveGraphs(outputFolder, planName, capacitiesData, mapPlotsUsage);
    }

    private void loadCapacitiesData(Map<String, DefaultTableXYDataset> capacitiesData, Map<String, DefaultTableXYDataset> contractedCapacitiesData) {
        for (CSVRecord lifecycleRecord : csvProfileRecords) {
            if (lifecycleRecord.get("tjobname").equals("TOTAL")) {
                boolean isContracted = lifecycleRecord.get(LIFECYCLE_HEADER).equals("CONTRACTED");
                String capacityHeader = lifecycleRecord.get(CAPACITY_HEADER);
                Map<String, DefaultTableXYDataset> targetMap = isContracted ? contractedCapacitiesData : capacitiesData;

                DefaultTableXYDataset dataset = targetMap.getOrDefault(capacityHeader, new DefaultTableXYDataset());
                XYSeries series = new XYSeries(lifecycleRecord.get(LIFECYCLE_HEADER), false, false);

                for (int i = 0; i < lifecycleRecord.size() - 4; i++) {
                    series.add(i, Double.valueOf(lifecycleRecord.get(String.valueOf(i))));
                }
                dataset.addSeries(series);
                targetMap.put(capacityHeader, dataset);
            }
        }
    }

    private void createAndSaveGraphs(String outputFolder, String planName, Map<String, DefaultTableXYDataset> capacitiesData, Map<String, XYPlot> mapPlotsUsage) {
        for (Map.Entry<String, DefaultTableXYDataset> capacity : capacitiesData.entrySet()) {
            DefaultTableXYDataset orderedDataset = reorderSeries(capacity.getValue(), TJob.getListTJobLifecyclesWithDesiredOrder());

            JFreeChart chart = ChartFactory.createStackedXYAreaChart(
                    "Usage Profile " + capacity.getKey(),
                    "Time",
                    capacity.getKey(),
                    orderedDataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );
            // Remove chart background
            chart.setBackgroundPaint(null);
            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(null);
            plot.setDomainPannable(true);
            plot.setRangePannable(true);

            // Customizing the decimal separator
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setDecimalSeparator('.'); // Set comma as decimal separator
            DecimalFormat decimalFormat = new DecimalFormat("0.0", symbols);

            // Set the custom format to the domain axis (X-axis)
            NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
            domainAxis.setNumberFormatOverride(decimalFormat);

            // Set the custom format to the range axis (Y-axis)
            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setNumberFormatOverride(decimalFormat);


            StackedXYAreaRenderer2 renderer = getTJobUsageProfileRenderer();
            plot.setRenderer(renderer);
            if (mapPlotsUsage.containsKey(capacity.getKey()) ) {
                XYPlot plot2 = mapPlotsUsage.get(capacity.getKey());
                plot2.setBackgroundPaint(null);
                plot.setDataset(1, plot2.getDataset());
                plot.setRenderer(1, plot2.getRenderer());
                String format="png";
                saveChartAsFormat(chart, outputFolder + "/" + planName + "-" + capacity.getKey() + "."+format,format, 1200, 400);
            }
        }
    }

    public Map<String, XYPlot> generateXYCloudObjectPlots(Map<String, DefaultTableXYDataset> mapCapacitiesCloudObject) {
        HashMap<String, XYPlot> mapcapacities = new HashMap<>();
        for (Map.Entry<String, DefaultTableXYDataset> capacity : mapCapacitiesCloudObject.entrySet()) {

            JFreeChart chart = ChartFactory.createStackedXYAreaChart(
                    "Usage Profile " + capacity.getKey(),
                    "Time",
                    "Contracted " + capacity.getKey(),
                    capacity.getValue(),
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );
            XYAreaRenderer renderer = new XYAreaRenderer();
            renderer.setSeriesPaint(0, new Color(135, 206, 250, 100)); // Customize transparency for series 0
            // Remove chart background
            chart.setBackgroundPaint(null);
            chart.getXYPlot().setRenderer(renderer);
            mapcapacities.put(capacity.getKey(), chart.getXYPlot());
        }

        return mapcapacities;
    }
    private static StackedXYAreaRenderer2 getTJobUsageProfileRenderer() {
        StackedXYAreaRenderer2 renderer = new StackedXYAreaRenderer2();
        renderer.setOutline(true); // Display outline
        renderer.setSeriesOutlineStroke(0, new BasicStroke(2.0f)); // Customize outline thickness for series 0
        renderer.setSeriesOutlineStroke(1, new BasicStroke(2.0f)); // Customize outline thickness for series 1
        renderer.setSeriesOutlineStroke(2, new BasicStroke(2.0f)); // Customize outline thickness for series 2
        renderer.setSeriesOutlinePaint(0, Color.BLACK); // Customize outline color for series 0
        renderer.setSeriesOutlinePaint(1, Color.RED); // Customize outline color for series 1
        renderer.setSeriesOutlinePaint(2, Color.BLUE); // Customize outline color for series 2
        renderer.setSeriesPaint(0, new Color(255, 255, 0, 255)); // Customize transparency for series 0
        renderer.setSeriesPaint(2, new Color(76, 40, 130, 255)); // Customize transparency for series 1
        renderer.setSeriesPaint(1, new Color(228, 130, 75, 255)); // Customize transparency for series 2

        return renderer;
    }
    // Helper method to reorder series in dataset
    private DefaultTableXYDataset reorderSeries(DefaultTableXYDataset dataset, List<String> desiredOrder) {
        DefaultTableXYDataset reorderedDataset = new DefaultTableXYDataset();
        for (String phase : desiredOrder) {
            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                XYSeries series = dataset.getSeries(i);
                if (series.getKey().equals(phase)) {
                    reorderedDataset.addSeries(series);
                    break;
                }
            }
        }
        return reorderedDataset;
    }
    private static void saveChartAsFormat(JFreeChart chart, String filePath,String format, int width, int height) {
        if ("png".equalsIgnoreCase(format)) {
            BufferedImage bufferedImage = chart.createBufferedImage(width, height);
            File file = new File(filePath);
            try {
                serializeChart(chart,filePath+".serialized");
                ImageIO.write(bufferedImage, "png", file);
            } catch (IOException e) {
                log.error("Error saving chart as PNG: {}" , e.getMessage());

            }

        } else if ("svg".equalsIgnoreCase(format)) {
            DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
            Document document = domImpl.createDocument(null, "svg", null);
            SVGGraphics2D svgGenerator = new SVGGraphics2D(document);
            svgGenerator.setSVGCanvasSize(new Dimension(width, height));
            chart.draw(svgGenerator, new Rectangle(width, height));
            try (FileWriter out = new FileWriter(filePath)) {
                svgGenerator.stream(out, true);
            } catch (IOException e) {
                log.error("Error saving chart as SVG:{} " , e.getMessage());
            }


        } else {
            log.error("Unsupported format: {}", format);
        }
    }
    public static void serializeChart(JFreeChart chart, String filePath) {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            // Write the chart object to the file
            out.writeObject(chart);
            log.info("Chart serialized and saved to: {}" , filePath);
        } catch (IOException e) {
            log.error("Error saving chart:  {}" , e.getMessage());

    }


}

}