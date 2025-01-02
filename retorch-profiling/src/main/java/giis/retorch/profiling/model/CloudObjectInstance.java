package giis.retorch.profiling.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code CloudObjectInstance} class represents a Cloud Object Instance with a name,and a set of {@code  Capacities}.
 */
public class CloudObjectInstance {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private String name;
    private final Map<String, CapacityContracted> contractedCapacities;
    private BillingOption billingOption;
    private double startSetUp;
    private double endSetUp;
    private double startTJobExec;
    private double endTJobExec;
    private double startTearDown;
    private double endTearDown;

    public static final String SETUP_NAME = "setup";
    public static final String TESTEXECUTION_NAME = "testexec";
    public static final String TEARDOWN_NAME = "teardown";

    protected static final Set<String> LIST_TJOB_LIFECYCLE;
    static {
        LIST_TJOB_LIFECYCLE = new HashSet<>();
        LIST_TJOB_LIFECYCLE.add(SETUP_NAME);
        LIST_TJOB_LIFECYCLE.add(TESTEXECUTION_NAME);
        LIST_TJOB_LIFECYCLE.add(TEARDOWN_NAME);
    }

    public CloudObjectInstance(@JsonProperty("objectName")String name, @JsonProperty("billingOption")BillingOption option,
                               @JsonProperty("capacitiesContracted")Map<String, CapacityContracted> contractedCapacities) {
        this.name = name;
        this.contractedCapacities = contractedCapacities;
        this.billingOption=option;
    }

    public String getName() {return name;}
    public double getEndSetUp() {return endSetUp;}
    public  Map<String, CapacityContracted> getContractedCapacities() {return contractedCapacities;}
    public CapacityContracted getContractedCapacity(String name){return contractedCapacities.get(name);}
    public double getStartSetUp() {return startSetUp;}
    public double getStartTJobExec() {return startTJobExec;}
    public double getEndTJobExec() {return endTJobExec;}
    public double getStartTearDown() {return startTearDown;}
    public double getEndTearDown() {return endTearDown;}
    public List<String> getCapacityNames() {
        return this.getContractedCapacities().keySet().stream()
                .collect(Collectors.toList());
    }
    public BillingOption getBillingOption() {return billingOption;}
    public static Set<String> getTJobLifecycles(){ return LIST_TJOB_LIFECYCLE;}

    public void setName(String name) {this.name = name;}
    public void setEndSetUp(double endSetUp) {this.endSetUp = endSetUp;}
    public void setStartSetUp(double startSetUp) {this.startSetUp = startSetUp;}
    public void setStartTJobExec(double startTJobExec) {this.startTJobExec = startTJobExec;}
    public void setEndTJobExec(double endTJobExec) {this.endTJobExec = endTJobExec;}
    public void setStartTearDown(double startTearDown) {this.startTearDown = startTearDown;}
    public void setEndTearDown(double endTearDown) {this.endTearDown = endTearDown;}
    public void setBillingOption(BillingOption billingOption) {this.billingOption = billingOption;}
    public void setLifecycleTimes(double stSetUp, double endSetUp, double stExec, double endExec, double stTearDown,
                                  double endTearDown)  {
        if (stSetUp <= endSetUp && stExec <= endExec && stTearDown <= endTearDown && stSetUp < stExec && stExec < stTearDown) {
            log.debug("The times provided are correct, setting the start and end of each phase");
            this.setStartSetUp(stSetUp);
            this.setStartTJobExec(stExec);
            this.setStartTearDown(stTearDown);
            this.setEndSetUp(endSetUp);
            this.setStartTJobExec(endExec);
            this.setEndTearDown(endTearDown);
        } else {
            throw new IllegalArgumentException("One or more times provided are not correct, please review that each phase starts before the end, and are continuous");
        }
    }
}