package giis.retorch.profiling.datasetgeneration;

/**
 * Immutable carrier with the six lifecycle boundary timestamps derived from an average-duration CSV.
 * Used in place of the previous {@code double[]} with positional indexing.
 */
public final class LifecycleTimes {

    private final double startSetUp;
    private final double endSetUp;
    private final double startTJobExec;
    private final double endTJobExec;
    private final double startTearDown;
    private final double endTearDown;

    public LifecycleTimes(double startSetUp, double endSetUp, double startTJobExec, double endTJobExec,
                          double startTearDown, double endTearDown) {
        this.startSetUp = startSetUp;
        this.endSetUp = endSetUp;
        this.startTJobExec = startTJobExec;
        this.endTJobExec = endTJobExec;
        this.startTearDown = startTearDown;
        this.endTearDown = endTearDown;
    }

    public double getStartSetUp() {
        return startSetUp;
    }
    public double getEndSetUp() {
        return endSetUp;
    }
    public double getStartTJobExec() {
        return startTJobExec;
    }
    public double getEndTJobExec() {
        return endTJobExec;
    }
    public double getStartTearDown() {
        return startTearDown;
    }
    public double getEndTearDown() {
        return endTearDown;
    }
}