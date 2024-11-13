package giis.retorch.orchestration.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The {@code TJobClass} class represents a TJob with a name, stage, and a set of {@code  ResourceClass}.
 * When the TJob is created, it calculates the total amount of {@code Capacity} that is used by the list of
 * {@code ResourceClass} required
 */
public class TJobEntity {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private  Set<CapacityEntity> totalCapacities;
    private  int stage;
    private double startSetUp;
    private double endSetUp;
    private double startExec;
    private double endExec;
    private double startTearDown;
    private double endTearDown;
    private  List<TestCaseEntity> listTestCases;
    private  List<ResourceEntity> listResourceEntities;
    private String idTJob = "Default";
    private String intraTJobSchedule = "SequentialScheduling";
    private Integer tJobConcurrency=50;
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
    public TJobEntity() {
        this.listResourceEntities = new LinkedList<>();
        this.listTestCases = new LinkedList<>();
    }
    public TJobEntity(String idTJob, int stage, List<ResourceEntity> resourceInstances) {
        //Attributes required by the tool
        this.listTestCases=new LinkedList<>();
        //Attributes required for the cost model
        this.idTJob = idTJob;
        this.listResourceEntities = resourceInstances;
        this.stage = stage;
        CapacityEntity memory = new CapacityEntity(CapacityEntity.MEMORY_NAME, 0);
        CapacityEntity processors = new CapacityEntity(CapacityEntity.PROCESSOR_NAME, 0);
        CapacityEntity slots = new CapacityEntity(CapacityEntity.SLOTS_NAME, 0);
        CapacityEntity storage = new CapacityEntity(CapacityEntity.STORAGE_NAME, 0);
        for (ResourceEntity resource : resourceInstances) {
            for (CapacityEntity capacityEntity : resource.getMinimalCapacities())
                switch (capacityEntity.getName()) {
                    case CapacityEntity.PROCESSOR_NAME:
                        processors.addQuantity(capacityEntity.getQuantity());
                        break;
                    case CapacityEntity.MEMORY_NAME:
                        memory.addQuantity(capacityEntity.getQuantity());
                        break;
                    case CapacityEntity.STORAGE_NAME:
                        storage.addQuantity(capacityEntity.getQuantity());
                        break;
                    case CapacityEntity.SLOTS_NAME:
                        slots.addQuantity(capacityEntity.getQuantity());
                        break;
                    default:
                        log.warn("Capacity {} not found", capacityEntity.getName());
                        break;
                }
        }
        totalCapacities = new HashSet<>();
        totalCapacities.add(memory);
        totalCapacities.add(processors);
        totalCapacities.add(slots);
        totalCapacities.add(storage);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null) || (!obj.getClass().equals(this.getClass())))  return false;

        TJobEntity objectToCompare = ((TJobEntity) obj);
        for (TestCaseEntity tc : objectToCompare.getListTestCases()) {
            if (!this.getListTestCases().contains(tc)) {return false;}
        }
        return this.getListResourceClasses().equals(objectToCompare.getListResourceClasses()) &&
                this.getIntraTJobSchedule().equals(objectToCompare.getIntraTJobSchedule());
    }

    @Override
    public String toString() {
        List<String> testCasesNames = listTestCases.stream().map(TestCaseEntity::getName).toList();
        List<String> resourceNames =
                listResourceEntities.stream().map(ResourceEntity::getResourceID).toList();
        return "TJobClass [\n" + "listTc:{\n" + testCasesNames + "}, listRes " + resourceNames + ", intraSchedule '" +
                intraTJobSchedule + '\'' + ", con " + tJobConcurrency + ']';
    }

    public static Set<String> getListTJobLifecyclesNames() {return LIST_TJOB_LIFECYCLE;}
    public static List<String> getListTJobLifecyclesWithDesiredOrder() {
        LinkedList<String> desiredDataOrder = new LinkedList<>();
        desiredDataOrder.add(TJobEntity.LIFECYCLE_SETUP_NAME);
        desiredDataOrder.add(TJobEntity.LIFECYCLE_TESTEXECUTION_NAME);
        desiredDataOrder.add(TJobEntity.LIFECYCLE_TEARDOWN_NAME);
        return desiredDataOrder;
    }

    public Set<CapacityEntity> getTotalCapacities() {return totalCapacities;}
    public List<String> getCapacityNames() {return this.getTotalCapacities().stream().map(CapacityEntity::getName).toList();}
    public double getElasticityCostResources() {return elasticityCostResources;}
    public double getEndExec() {return endExec;}
    public double getEndSetUp() {return endSetUp;}
    public double getEndTearDown() {return endTearDown;}
    public String getIdTJob() {return idTJob.toLowerCase(Locale.ENGLISH);}
    public String getIntraTJobSchedule() {return intraTJobSchedule;}
    public List<ResourceEntity> getListResourceClasses() {return listResourceEntities;}
    public List<TestCaseEntity> getListTestCases() {
        return listTestCases;
    }
    public int getStage() {return stage;}
    public double getStartExec() {return startExec;}
    public double getStartSetUp() {return startSetUp;}
    public double getStartTearDown() {return startTearDown;}

    public void setEndExec(double endExec) {this.endExec = endExec;}
    public void setEndSetUp(double endSetUp) {this.endSetUp = endSetUp;}
    public void setEndTearDown(double endTearDown) {this.endTearDown = endTearDown;}
    public void setIdTJob(String id) {this.idTJob = id;}
    public void setStage(int stage) {this.stage = stage;}
    public void setStartExec(double startExec) {this.startExec = startExec;}
    public void setStartSetUp(double startSetUp) {this.startSetUp = startSetUp;}
    public void setStartTearDown(double startTearDown) {this.startTearDown = startTearDown;}
    public void setAvgTime(double stSetUp, double endSetUp, double stExec, double endExec, double stTearDown,
                           double endTearDown) {
        if (stSetUp <= endSetUp && stExec <= endExec && stTearDown <= endTearDown && stSetUp < stExec && stExec < stTearDown) {
            log.debug("The times provided are correct, setting the start and end of each phase");
            this.setStartSetUp(stSetUp);
            this.setStartExec(stExec);
            this.setStartTearDown(stTearDown);

            this.setEndSetUp(endSetUp);
            this.setEndExec(endExec);
            this.setEndTearDown(endTearDown);
        } else {
            throw new IllegalArgumentException("One or more times provided are not correct, please review that each " +
                    "phase starts before the end, and are continuous");
        }
    }

    public Map.Entry<String, Set<CapacityEntity>> getCapacitiesGivenTime(double currentTime, double startExecutionTime) {
        double adjustedStartSetUp = this.getStartSetUp() + startExecutionTime;
        double adjustedEndSetUp = this.getEndSetUp() + startExecutionTime;
        double adjustedStartExec = this.getStartExec() + startExecutionTime;
        double adjustedEndExec = this.getEndExec() + startExecutionTime;
        double adjustedStartTearDown = this.getStartTearDown() + startExecutionTime;
        double adjustedEndTearDown = this.getEndTearDown() + startExecutionTime;

        if (currentTime >= adjustedStartSetUp && currentTime < adjustedEndSetUp) {
            return new AbstractMap.SimpleEntry<>(LIFECYCLE_SETUP_NAME, this.getTotalCapacities());
        }
        if (currentTime >= adjustedStartExec && currentTime < adjustedEndExec) {
            return new AbstractMap.SimpleEntry<>(LIFECYCLE_TESTEXECUTION_NAME, this.getTotalCapacities());
        }
        if (currentTime >= adjustedStartTearDown && currentTime < adjustedEndTearDown) {
            return new AbstractMap.SimpleEntry<>(LIFECYCLE_TEARDOWN_NAME, this.getTotalCapacities());
        }

        return new AbstractMap.SimpleEntry<>("noexec", Collections.emptySet());
    }

    public boolean addTestCase(TestCaseEntity subject) {
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
        for (TestCaseEntity subject : this.listTestCases) {
            for (AccessModeEntity subjectAccessMode : subject.getAccessMode()) {
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
    public void addListTestCases(List<TestCaseEntity> testCases) {
        this.listTestCases.addAll(testCases);
        this.updateIntraTestCaseSchedule();
    }

    public void addListResources(List<ResourceEntity> listResources) {
        for (ResourceEntity res : listResources) this.addResource(res);
    }
    /**
     * This method add a new resource to the current TJob. First check that the resource is not contained in the
     * TJob for after add it and increase the total elasticity cost of the TJob.
     */
    public boolean addResource(ResourceEntity subject) {
        if (listResourceEntities.contains(subject)) {
            return false;
        }
        listResourceEntities.add(subject);
        elasticityCostResources += subject.getElasticityModel().getElasticityCost();

        return true;
    }

    public void removeTestCase(TestCaseEntity tc) {
        this.listTestCases.remove(tc);
        this.updateIntraTestCaseSchedule();
    }
    /**
     * Support method, search between the elasticities of the Resources and returns the minimal value
     */
    public int getMinimalElasticity() {
        int minimalElasticity = 50;
        for (ResourceEntity res : this.getListResourceClasses()) {
            int currentElasticity = res.getElasticityModel().getElasticity();
            if (currentElasticity < minimalElasticity) minimalElasticity = currentElasticity;
        }

        return minimalElasticity;
    }

    /**
     * Gets the minimal concurrency of the given TJob
     */
    public int getMinimalConcurrency() {
        int minimalConcurrency = 50;
        for (TestCaseEntity tc : this.getListTestCases()) {
            for (AccessModeEntity acc : tc.getAccessMode()) {
                int concurrency = acc.getConcurrency();
                if (concurrency < minimalConcurrency) minimalConcurrency = concurrency;
            }
        }

        return minimalConcurrency;
    }
}