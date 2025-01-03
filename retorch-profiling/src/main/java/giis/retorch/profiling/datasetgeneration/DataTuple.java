package giis.retorch.profiling.datasetgeneration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code DataTuple} class represents a data structure that holds an ID for a {@code TJob}, its stage,
 * and a map {@code TJob} with the lifecycle durations. This class it's a necessary DTO enabler to calculate the average
 * datasets, allowing to store the duration of the different executions and its corresponding stage
 */
public class DataTuple implements Serializable {

    String idTJob;
    int stage;
    private Map<String, Double> lifecycleDuration;

    public DataTuple(String idTJob, int stage) {
        this.idTJob = idTJob;
        this.stage = stage;
        this.lifecycleDuration = new HashMap<>();
    }

    public String getIdTJob() {
        return idTJob;
    }
    public void setIdTJob(String idTJob) {
        this.idTJob = idTJob;
    }
    public int getStage() {
        return stage;
    }
    public void setStage(int stage) {
        this.stage = stage;
    }

    public void putLifeCycleDuration(String lifecycle, Double duration) {
        getLifecycleDuration().put(lifecycle, duration);
    }
    public Map<String, Double> getLifecycleDuration() {
        return lifecycleDuration;
    }
    public void setLifecycleDuration(Map<String, Double> lifecycleDuration) {
        this.lifecycleDuration = lifecycleDuration;
    }
}