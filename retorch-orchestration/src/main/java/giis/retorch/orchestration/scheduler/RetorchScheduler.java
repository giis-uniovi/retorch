package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.model.*;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code RetorchScheduler}  provides the {@code TestCase}  ordering and {@code Resource} allocation functionalities
 * to the RETORCH orchestration framework. {@code RetorchScheduler} gets as input a list of {@code TGroup} and provides
 * as output the list of {@code Activity} ordered and maximizing the usage of {@code Resource}s
 */
public class RetorchScheduler {

    private static final Logger logRetorchScheduler = LoggerFactory.getLogger(RetorchScheduler.class);
    private final List<TGroup> listTGroups;
    private LinkedList<TJob> listTJobs;
    private List<Activity> activityEntities;

    public RetorchScheduler(List<TGroup> inputTGroups) throws NoTGroupsInTheSchedulerException {
        validateTGroups(inputTGroups);
        this.listTGroups = inputTGroups;
        this.listTJobs = new LinkedList<>();
        this.activityEntities = new LinkedList<>();
    }

    /**
     * Method that validates the list of {@code TGroup}s provided to the {@code Scheduler}, checking : (1) if its empty and (2) if there
     * are repeated {@code Resource}s in the list
     * @param inputTGroups List with the {@code TGroup}s to validate
     */
    private void validateTGroups(List<TGroup> inputTGroups) throws NoTGroupsInTheSchedulerException {
        if (inputTGroups.isEmpty()) throw new NoTGroupsInTheSchedulerException("The TGroups List provided is empty");
        for (TGroup tGroup : inputTGroups) {
            if (tGroup.getTGroupResources().stream().map(Resource::getResourceID).distinct().count() != tGroup.getTGroupResources().size()) {
                logRetorchScheduler.error("There are repeated resources in one of the providedTGroups");
            }
        }
    }

    /**
     * Father method that calls the generation of {@code TJob}s for after that generate a proper scheduling taking into account
     * the restrictions in terms of elasticity and concurrency of the {@code TJob}s
     */
    public List<Activity> generateActivities() {
        generateTJobs();
        List<Resource> availableResources = this.getAllResources();
        Scheduling schedule = new Scheduling(availableResources);
        for (TJob currentTJob : this.listTJobs) {
            schedule.addTJob(currentTJob);
        }
        this.activityEntities = this.generateListActivities(schedule);
        return this.activityEntities.stream().sorted(Comparator.comparing(Activity::toString)).collect(Collectors.toList());
    }

    /**
     * This method generate a first {@code TJob} Group not optimized in order to achieve it, calls Intelligent Scheduling
     * algorithm that retrieves the best {@code TJob}s distribution of {@code TestCase}s
     */
    private void generateTJobs() {
        this.listTJobs = new LinkedList<>();
        //First Stage : Complain with the constraints creating the TJobs
        TJob currentTJob;
        int numberTJob=0;
        for (TGroup tGroup : this.listTGroups) {
            currentTJob = new TJob("TJobPriorSch"+ numberTJob,0,tGroup.getTGroupResources());
            intelligentScheduling(currentTJob, tGroup);
            if (!currentTJob.getListTestCases().isEmpty()){
                this.listTJobs.add(currentTJob);
            }
            numberTJob+=1;
        }
    }

    /**
     * Support method that get all {@code Resource}s from the list of {@code TJob}s
     */
    public List<Resource> getAllResources() {
        LinkedList<Resource> listResourcesAvailable = new LinkedList<>();
        for (TJob tJob : this.listTJobs) {
            for (Resource res : tJob.getListResourceClasses())
                if (!listResourcesAvailable.contains(res))
                    for (int i = 0; i < res.getElasticityModel().getElasticity(); i++) listResourcesAvailable.add(res);
        }
        return listResourcesAvailable;
    }

    /**
     * Method that given a schedule, creates the {@code Activity} graph with its predecessors relationships.
     * @param schedule Schedule object that contains the {@code TJob}s
     */
    private List<Activity> generateListActivities(Scheduling schedule) {
        Map<Integer, List<Activity>> mapActivities = new HashMap<>();
        Map<Integer, ScheduleStruct> mapMoments = schedule.getListMoments();
        Integer lastTime = Collections.max(mapMoments.keySet());
        int currentTime = 1;
        while (currentTime <= lastTime) {
            List<TJob> listTJobsFromMap = mapMoments.get(currentTime).getListTJobs();
            if (currentTime == 1) {
                List<Activity> currentActivityEntities = new LinkedList<>();
                for (TJob tJob : listTJobsFromMap) {
                    currentActivityEntities.add(new Activity(tJob));
                }
                mapActivities.put(currentTime, currentActivityEntities);
            } else {
                addActivityToMap(mapActivities, mapMoments, currentTime, listTJobsFromMap);
            }
            currentTime++;
        }
        List<Activity> output;
        output = new LinkedList<>();
        for (Map.Entry<Integer, List<Activity>> entry : mapActivities.entrySet()) {
            output.addAll(entry.getValue());
        }

        return output;
    }

    /**
     * In this First approach  the "Intelligent" remains only in an optimization of the cost (deploy the {@code TestCase}s
     * in the cheapest {@code Resource}s available)
     * @param currentTJob {@code TJob} to check if its {@code TestCase}s could be deployed in the cheapest way
     * @param tGroup      TGroup to check the {@code TestCase}s
     */
    private void intelligentScheduling(TJob currentTJob, TGroup tGroup) {
        for (TestCase testCaseTGroup : tGroup.getTGroupTestCases()) {
            removeTestCasesInCheaperTJobs(testCaseTGroup, currentTJob);
        }
        removeEmptyTJobs();
    }

    private void removeEmptyTJobs() {
        this.listTJobs.removeIf(tJob -> tJob.getListTestCases().isEmpty());
    }

    /**
     * Support method that adds an {@code Activity}s to the Map that represents the schedule.This method check for all time
     * moments on which is more suitable deploy the {@code TJob} and creates the {@code Activity} on it
     * @param mapActivities    Map with all the {@code Activity}s
     * @param mapMoments       Map with the {@code Activity}s ordered by moment (Integer that indexes it)
     * @param currentTime      Integer with the current moment on which we are iterating
     * @param listTJobsFromMap List of {@code TJob}s of the current moment
     */
    private void addActivityToMap(Map<Integer, List<Activity>> mapActivities,
                                  Map<Integer, ScheduleStruct> mapMoments, int currentTime,
                                  List<TJob> listTJobsFromMap) {
        List<Activity> listActivities = new LinkedList<>();
        List<Resource> resourcesAvailablePredecessors = mapMoments.get(currentTime - 1).getResourcesAvailable();
        for (TJob tJob : listTJobsFromMap) {
            List<Activity> feasibleActivitiesPredecessors;
            if (!listActivities.isEmpty()) {
                List<Activity> precededActivities =listActivities.stream()
                        .map(Activity::getListPredecessors)
                        .flatMap(List::stream).collect(Collectors.toList());
                feasibleActivitiesPredecessors =
                        (List<Activity>) CollectionUtils.subtract(mapActivities.get(currentTime - 1),
                                precededActivities);
            } else {
                feasibleActivitiesPredecessors = mapActivities.get(currentTime - 1);
            }
            Activity currentActivity = getActivityClassWithPredecessors(feasibleActivitiesPredecessors,
                    resourcesAvailablePredecessors, tJob);
            listActivities.add(currentActivity);
        }
        mapActivities.put(currentTime, listActivities);
    }

    /**
     * Support method that check if the {@code TestCase}s testCaseToAdd is contained into an expensive {@code TJob}s.If  it, is removed
     * from the expensive and added to the cheaper(provided as parameter), ignoring this {@code TestCase}s otherwise
     * @param testCaseToAdd {@code TestCase} to check
     * @param newTJob       {@code TJob} against the {@code TestCase} is checked
     */
    private void removeTestCasesInCheaperTJobs(TestCase testCaseToAdd, TJob newTJob) {
        boolean isContainedSomeWhere = false;
        for (TJob tJob : this.listTJobs) {
            boolean isContained = tJob.getListTestCases().contains(testCaseToAdd);
            if (isContained) {
                boolean isOnExpensiveTJob = tJob.getElasticityCostResources() >= newTJob.getElasticityCostResources();
                if (isOnExpensiveTJob) {
                    tJob.removeTestCase(testCaseToAdd);
                    newTJob.addTestCase(testCaseToAdd);
                }
                isContainedSomeWhere = true;
            }
        }
        if (!isContainedSomeWhere) {
            newTJob.addTestCase(testCaseToAdd);
        }
    }

    /**
     * Support method makes a set's operation, in order to get the {@code Activity}s that are not preceded for anyone ( gets
     * a list of candidate {@code Activity}s to be the predecessor)
     * @param feasibleActivitiesPredecessors List with all the {@code Activity}s of the previous iteration
     * @param resourcesAvailablePredecessor  List with all the {@code Resource}s available in the predecessor iteration
     * @param tJob                           Current {@code TJob}
     */
    private Activity getActivityClassWithPredecessors(List<Activity> feasibleActivitiesPredecessors,
                                                            List<Resource> resourcesAvailablePredecessor,
                                                            TJob tJob) {
        Collection<Resource> listComplementarySet = CollectionUtils.subtract(tJob.getListResourceClasses(),
                resourcesAvailablePredecessor);
        Activity currentActivity = new Activity(tJob);
        for (Resource resourceToCover : listComplementarySet) {
            for (Activity predecessorAct : feasibleActivitiesPredecessors) {
                if (predecessorAct.isUsingTheSameResource(resourceToCover)) {
                    currentActivity.addPredecessor(predecessorAct);
                    break;
                }
            }
        }

        return currentActivity;
    }
}