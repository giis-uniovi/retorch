package giis.retorch.profiling.model;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class UsageProfile implements Serializable {

    private Map<String, JFreeChart> plots;
    private String cloudObjectID;
    private String planName;

    public UsageProfile(String cloudObjectID, String planName) {
        this.cloudObjectID = cloudObjectID;
        this.planName = planName;
        this.plots = new HashMap<>();
    }

    public UsageProfile(Map<String, JFreeChart> plots) {
        this.plots = plots;
    }
    public UsageProfile() {
        plots = new HashMap<>();
    }

    public String getPlanName() {return planName;}
    public Map<String, JFreeChart> getPlots() {return plots;}
    public String getCloudObjectID() {return cloudObjectID;}

    public void setPlanName(String planName) {this.planName = planName;}
    public void setPlots(Map<String, JFreeChart> plots) {this.plots = plots;}
    public void setCloudObjectID(String cloudObjectID) {this.cloudObjectID = cloudObjectID;}

    public void addPlot(String key,JFreeChart plot) {
        plots.put(key, plot);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UsageProfile) {
            UsageProfile other = (UsageProfile) obj;
            if (other.getPlots().size()==this.getPlots().size()==other.getPlots().keySet().equals(this.getPlots().keySet())) {
                Map<String, JFreeChart> otherPlots = other.getPlots();
                for (Map.Entry<String, JFreeChart> entry : this.getPlots().entrySet()) {
                    if(!areChartsEqual(entry.getValue(), otherPlots.get(entry.getKey()))){
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
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
        String title1 = chart1.getTitle().getText();
        String title2 = chart2.getTitle().getText();
        if (!title1.equals(title2)) {
            return false;
        }
        // Compare the XYPlots (Main visual representation of the chart)
        XYPlot plot1 = (XYPlot) chart1.getPlot();
        XYPlot plot2 = (XYPlot) chart2.getPlot();
        return areXYPlotsEqual(plot1, plot2);// If all checks passed
    }
    /**
     * The {@code arePlotsEqual} support methods compare two XYPlots by means look if their axis, domain, background
     * renderers and datasets are the same
     * @param plot1  First plot for being compared
     * @param plot2  Second plot for comparing
     */
    public static boolean areXYPlotsEqual(XYPlot plot1, XYPlot plot2) {
        // Compare datasets
        XYDataset dataset1 = plot1.getDataset();
        XYDataset dataset2 = plot2.getDataset();

        if (!areDatasetsEqual(dataset1, dataset2)) {
            return false;
        }
        if (!plot1.getRenderer().equals(plot2.getRenderer())) {
            return false;
        }
        if (!plot1.getDomainAxis().getLabel().equals(plot2.getDomainAxis().getLabel())) {
            return false;
        }
        return plot1.getRangeAxis().getLabel().equals(plot2.getRangeAxis().getLabel());
    }

    private static boolean areDatasetsEqual(XYDataset dataset1, XYDataset dataset2) {
        if (dataset1.getSeriesCount() != dataset2.getSeriesCount()) {
            return false;
        }

        for (int i = 0; i < dataset1.getSeriesCount(); i++) {
            int itemCount1 = dataset1.getItemCount(i);
            int itemCount2 = dataset2.getItemCount(i);

            if (itemCount1 != itemCount2) {
                return false;
            }

            for (int j = 0; j < itemCount1; j++) {
                double x1 = dataset1.getXValue(i, j);
                double y1 = dataset1.getYValue(i, j);
                double x2 = dataset2.getXValue(i, j);
                double y2 = dataset2.getYValue(i, j);

                if (Double.compare(x1, x2) != 0 || Double.compare(y1, y2) != 0) {
                    return false;
                }
            }
        }

        return true;
    }
}
