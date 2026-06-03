package giis.retorch.profiling.model;

import org.jfree.chart.JFreeChart;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code UsageProfile} class represents the usage profile created, with its plots and the Cloud Object Instances used
 * during the calculation.
 */
public class UsageProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, JFreeChart> plots;
    private String cloudObjectID;
    private String planName;

    public UsageProfile(String cloudObjectID, String planName) {
        this.cloudObjectID = cloudObjectID;
        this.planName = planName;
        this.plots = new HashMap<>();
    }

    public String getPlanName() {return planName;}
    public Map<String, JFreeChart> getPlots() {return plots;}
    public String getCloudObjectID() {return cloudObjectID;}

    public void setPlanName(String planName) {this.planName = planName;}
    public void setPlots(Map<String, JFreeChart> plots) {this.plots = plots;}
    public void setCloudObjectID(String cloudObjectID) {this.cloudObjectID = cloudObjectID;}

    public void addPlot(String key, JFreeChart plot) {
        plots.put(key, plot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cloudObjectID, planName, plots == null ? 0 : plots.keySet());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UsageProfile)) return false;
        UsageProfile other = (UsageProfile) obj;
        return Objects.equals(cloudObjectID, other.cloudObjectID)
                && Objects.equals(planName, other.planName)
                && Objects.equals(plotKeys(), other.plotKeys());
    }

    private java.util.Set<String> plotKeys() {
        return plots == null ? java.util.Collections.<String>emptySet() : plots.keySet();
    }
}
