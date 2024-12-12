package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.model.*;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.partition; //TO-DO Review if Commons collections4 method do the same and remove guava

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
     * Method that validates the list of TGroups provided to the scheduler, checking : (1) if its empty and (2) if there
     * are repeated resources in the provided resources
     * @param inputTGroups List with the TGroups to validate
     */
    private void validateTGroups(List<TGroup> inputTGroups) throws NoTGroupsInTheSchedulerException {
        if (inputTGroups.isEmpty()) throw new NoTGroupsInTheSchedulerException("The TGroups List provided is empty");
        for (TGroup tGroup : inputTGroups) {
            if (tGroup.getTGroupResources().stream().map(Resource::getResourceID).distinct().count() != tGroup.getTGroupResources().size()) {
                logRetorchScheduler.error("There are repeated resources in one of the providedTGroups");
            }
        }
    }

    public List<TJob> maximizeUtilizationOfGeneratedTJobs() throws InsufficientNumberOfResourcesException,
            NoTJobsInTheSchedulerException {
        if (listTJobs.isEmpty())
            throw new NoTJobsInTheSchedulerException("You must invoke first the function generateTJobs(), the " +
                    "current" + " list is empty");
        LinkedList<TJob> listNewTJobs = new LinkedList<>();

        for (TJob currentTJob : this.listTJobs) {
            int numberOfResources = currentTJob.getMinimalElasticity();
            if (numberOfResources > 1) {
                int numberOfPartitions;
                numberOfPartitions = getNumberOfPartitions(currentTJob, numberOfResources);
                //Split the TJob in numberOfResources TJobs
                final List<List<TestCase>> splitTestCases = partition(currentTJob.getListTestCases(),
                        numberOfPartitions);
                for (List<TestCase> currentTestCases : splitTestCases) {
                    TJob newTJob = new TJob();
                    newTJob.addListTestCases(currentTestCases);
                    //  In order to avoid unnecessary splits we change the elasticity of the resource
                    for (Resource res : currentTJob.getListResourceClasses()) {
                        ElasticityModel currentElasticityModel = res.getElasticityModel();
                        currentElasticityModel.setElasticity(1);
                        res.setElasticityModel(currentElasticityModel);
                        newTJob.addResource(res);
                    }
                    listNewTJobs.add(newTJob);
                }
            } else {
                listNewTJobs.add(currentTJob);
            }
        }
        this.listTJobs = listNewTJobs;

        return listNewTJobs;
    }

    private int getNumberOfPartitions(TJob currentTJob, int numberOfResources) throws InsufficientNumberOfResourcesException {
        int numberOfPartitions;
        int maxConcurrencyTJob = currentTJob.getMinimalConcurrency();
        if (currentTJob.getIntraTJobSchedule().equals("SequentialScheduling")) {
            numberOfPartitions = (currentTJob.getListTestCases().size() / (numberOfResources));
        } else {
            numberOfPartitions = 1;
            while ((currentTJob.getListTestCases().size() / (numberOfPartitions)) > maxConcurrencyTJob)
                numberOfPartitions++;
            if (numberOfPartitions > numberOfResources)
                throw new InsufficientNumberOfResourcesException("The number of resources available is not enough");
        }

        return numberOfPartitions;
    }

    /**
     * Father method that calls the generation of TJobs for after that generate a proper scheduling taking into account
     * the restrictions in terms of elasticity and concurrency of the TJobs
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
     * This method generate a first TJobs Group not optimized in order to achieve it, calls Intelligent Scheduling
     * algorithm that retrieves the best tJob distribution of the test cases
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
     * Support method that get all resources from the list of TJobs
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
     * Method that given a schedule, creates the activity graph with its predecessors relationships.
     * @param schedule Schedule object that contains the TJobs
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
     * In this First approach  the "Intelligent" remains only in an optimization of the cost (deploy the test cases
     * in the cheapest resources available)
     * @param currentTJob TJob to check if its test cases could be deployed in the cheapest way
     * @param tGroup      TGroup to check the test cases
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
     * Support method that adds an activity to the Map that represents the schedule.This method check for all time
     * moments
     * on which is more suitable deploy the TJob and creates the activity on it
     * @param mapActivities    Map with all the activities
     * @param mapMoments       Map with the activities ordered by moment (Integer that indexes it)
     * @param currentTime      Integer with the current moment on which we are iterating
     * @param listTJobsFromMap List of TJobs of the current moment
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
     * Support method that check if the testCase testCaseToAdd is contained into an expensive TJob.If  it, is removed
     * from the
     * expensive and added to the cheaper(provided as parameter), ignoring this test case otherwise
     * @param testCaseToAdd Test case to check
     * @param newTJob       TJob against the test case is checked
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
     * Support method makes a set's operation, in order to get the Activities that are not preceded for anyone ( gets
     * a list of candidate activities to be the predecessor)
     * @param feasibleActivitiesPredecessors List with all the activities of the previous iteration
     * @param resourcesAvailablePredecessor  List with all the resources available in the predecessor iteration
     * @param tJob                           Current TJob
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