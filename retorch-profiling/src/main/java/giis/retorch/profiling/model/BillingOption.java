package giis.retorch.profiling.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * The {@code BillingOption} class represents how the Cloud Provider bills the  different {@code ContractedCapacity}s
 * into a {@code CloudObject}. Contains the getters and setters of its attributes: name, invoicedPrices, cloudProvider
 * and the time period.
 */
public class BillingOption {

    private String name;
    private Map<String,Double> invoicedPrices;
    private String cloudProvider;
    private int timePeriod;

    public BillingOption(@JsonProperty("billingName") String name, @JsonProperty("provider") String cloudProvider,
                         @JsonProperty("invoicedPrices") Map<String, Double> invoicedPrices,
                         @JsonProperty("timePeriod") int timePeriod) {
        this.name = name;
        this.cloudProvider = cloudProvider;
        this.invoicedPrices = invoicedPrices;
        this.timePeriod = timePeriod;
    }

    public String getName() {return name;}
    public Map<String, Double> getInvoicedPrices() {return invoicedPrices;}
    public double getInvoicedPrice(String capacity){return invoicedPrices.get(capacity);}
    public String getCloudProvider() {return cloudProvider;}
    public int getTimePeriod() {return timePeriod;}

    public void setName(String name) {this.name = name;}
    public void setCloudProvider(String cloudProvider) {this.cloudProvider = cloudProvider;}
    public void setInvoicedPrices(Map<String, Double> invoicedPrices) {this.invoicedPrices = invoicedPrices;}
    public void setTimePeriod(int timePeriod) {this.timePeriod = timePeriod;}







}
