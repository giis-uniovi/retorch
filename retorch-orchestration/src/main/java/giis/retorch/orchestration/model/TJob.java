package giis.retorch.orchestration.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code TJob} class represents a TJob with a name, stage, the {@code TestCase}s it executes and the set of
 * {@code Resource} they require. Timings and capacities are measured at run time and belong to the usage profile.
 */
public class TJob {

    /** Upper bound used when no access mode or resource limits the concurrency or the elasticity of a TJob. */
    public static final int DEFAULT_MAX_CONCURRENCY = 50;

    private int stage;
    private final List<TestCase> listTestCases;
    private final List<Resource> listResourceEntities;
    private String idTJob = "Default";
    private String intraTJobSchedule = "SequentialScheduling";
    private int tJobConcurrency = DEFAULT_MAX_CONCURRENCY;
    private double elasticityCostResources;

    public static final String LIFECYCLE_SETUP_NAME = "setup";
    public static final String LIFECYCLE_TESTEXECUTION_NAME = "testexec";
    public static final String LIFECYCLE_TEARDOWN_NAME = "teardown";

    protected static final Set<String> LIST_TJOB_LIFECYCLE;
    static {
        LIST_TJOB_LIFECYCLE = new HashSet<>();
        LIST_TJOB_LIFECYCLE.add(LIFECYCLE_SETUP_NAME);
        LIST_TJOB_LIFECYCLE.add(LIFECYCLE_TESTEXECUTION_NAME);
        LIST_TJOB_LIFECYCLE.add(LIFECYCLE_TEARDOWN_NAME);
    }

    /**
     * Default constructor of the TJob. The list of test cases and resources are instantiated and the concurrency
     * is set to a default value. By default, the intra-schedule is set to Sequential, and is being checked when a
     * new test
     * case is added.
     */
    public TJob() {
        this.listResourceEntities = new LinkedList<>();
        this.listTestCases = new LinkedList<>();
    }

    public TJob(String idTJob, int stage, List<Resource> resourceInstances) {
        this.listTestCases = new LinkedList<>();
        this.idTJob = idTJob;
        this.listResourceEntities = resourceInstances;
        this.stage = stage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(new HashSet<>(listTestCases), new HashSet<>(listResourceEntities), intraTJobSchedule);
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null) || (!obj.getClass().equals(this.getClass())))  return false;

        TJob objectToCompare = ((TJob) obj);
        return new HashSet<>(this.getListTestCases()).equals(new HashSet<>(objectToCompare.getListTestCases())) &&
                new HashSet<>(this.getListResourceClasses()).equals(new HashSet<>(objectToCompare.getListResourceClasses())) &&
                this.getIntraTJobSchedule().equals(objectToCompare.getIntraTJobSchedule());
    }

    @Override
    public String toString() {
        List<String> testCasesNames = listTestCases.stream().map(TestCase::getName).collect(Collectors.toList());
        List<String> resourceNames =
                listResourceEntities.stream().map(Resource::getResourceID).collect(Collectors.toList());
        return "TJobClass [\n" + "listTc:{\n" + testCasesNames + "}, listRes " + resourceNames + ", intraSchedule '" +
                intraTJobSchedule + '\'' + ", con " + tJobConcurrency + ']';
    }

    public static Set<String> getListTJobLifecyclesNames() {return LIST_TJOB_LIFECYCLE;}
    public static List<String> getListTJobLifecyclesWithDesiredOrder() {
        LinkedList<String> desiredDataOrder = new LinkedList<>();
        desiredDataOrder.add(TJob.LIFECYCLE_SETUP_NAME);
        desiredDataOrder.add(TJob.LIFECYCLE_TESTEXECUTION_NAME);
        desiredDataOrder.add(TJob.LIFECYCLE_TEARDOWN_NAME);
        return desiredDataOrder;
    }

    public double getElasticityCostResources() {return elasticityCostResources;}
    /** The identifier as stored ({@code TJobA}, {@code GatewayActivity}); the pipeline uses it in lower case. */
    public String getIdTJob() {return idTJob;}
    public String getIntraTJobSchedule() {return intraTJobSchedule;}
    public List<Resource> getListResourceClasses() {return listResourceEntities;}
    public List<TestCase> getListTestCases() {
        return listTestCases;
    }
    public int getStage() {return stage;}

    public void setIdTJob(String id) {this.idTJob = id;}
    public void setStage(int stage) {this.stage = stage;}
    public boolean addTestCase(TestCase subject) {
        if (listTestCases.contains(subject)) return false;
        listTestCases.add(subject);
        this.updateIntraTestCaseSchedule();

        return true;
    }

    /**
     * Support method that updates the intra-Schedule of the given TJob. Iterates over all the TJob test cases in order
     * to check if any of the test case requires a sequential access mode. In the case that this kind of test case exist
     * the TJob is updated with a sequential scheduling and a single concurrency. If there are no sequential test cases
     * the schedule remains as parallel and the concurrency is setter to the lower access mode value.
     */
    public void updateIntraTestCaseSchedule() {
        boolean thereAreAtLeastOneSequential = false;
        for (TestCase subject : this.listTestCases) {
            for (AccessMode subjectAccessMode : subject.getAccessMode()) {
                if (!subjectAccessMode.getSharing()) thereAreAtLeastOneSequential = true;
                //  Checks if the given concurrency is lower than stored (if its lower overwrite the value)
                if (subjectAccessMode.getConcurrency() < this.tJobConcurrency) this.tJobConcurrency = subjectAccessMode.getConcurrency();
            }
            if (thereAreAtLeastOneSequential) {this.intraTJobSchedule = "SequentialScheduling";}
            else {this.intraTJobSchedule = "ParallelScheduling";}
        }
    }

    /**
     * This method add a lis of test cases updating the intra- schedule after it.
     * @param testCases List with the test cases  to be added
     */
    public void addListTestCases(List<TestCase> testCases) {
        this.listTestCases.addAll(testCases);
        this.updateIntraTestCaseSchedule();
    }

    public void addListResources(List<Resource> listResources) {
        for (Resource res : listResources) this.addResource(res);
    }
    /**
     * This method add a new resource to the current TJob. First check that the resource is not contained in the
     * TJob for after add it and increase the total elasticity cost of the TJob.
     */
    public boolean addResource(Resource subject) {
        if (listResourceEntities.contains(subject)) {
            return false;
        }
        listResourceEntities.add(subject);
        elasticityCostResources += subject.getElasticityModel().getElasticityCost();

        return true;
    }

    public void removeTestCase(TestCase tc) {
        this.listTestCases.remove(tc);
        this.updateIntraTestCaseSchedule();
    }
    /**
     * Support method, search between the elasticities of the Resources and returns the minimal value
     */
    public int getMinimalElasticity() {
        int minimalElasticity = DEFAULT_MAX_CONCURRENCY;
        for (Resource res : this.getListResourceClasses()) {
            int currentElasticity = res.getElasticityModel().getElasticity();
            if (currentElasticity < minimalElasticity) minimalElasticity = currentElasticity;
        }

        return minimalElasticity;
    }

    /**
     * Gets the minimal concurrency of the given TJob
     */
    public int getMinimalConcurrency() {
        int minimalConcurrency = DEFAULT_MAX_CONCURRENCY;
        for (TestCase tc : this.getListTestCases()) {
            for (AccessMode acc : tc.getAccessMode()) {
                int concurrency = acc.getConcurrency();
                if (concurrency < minimalConcurrency) minimalConcurrency = concurrency;
            }
        }

        return minimalConcurrency;
    }
}