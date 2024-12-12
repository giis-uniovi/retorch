package giis.retorch.orchestration.model;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;


import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code ExecutionPlan} class represents a series of TJobs ordered.
 * It contains a name for the execution plan and a list of {@code TJobClass} objects with their
 * respective resources and stages.
 * @since 1.0
 */
public class ExecutionPlan {

    private String name;
    private List<TJob> tJobEntityList;
    private Map<Integer, LinkedList<Activity>> sortedActivities;
    private List<Activity> listActivities;

    public ExecutionPlan(String name, List<?> listActivities) throws NoFinalActivitiesException, EmptyInputException {
        if (listActivities == null || listActivities.isEmpty()){
            throw  new EmptyInputException("The provided list of activities is empty");
        }
        if (listActivities.get(0).getClass().equals(Activity.class)){
            this.name = name;
            validateListActivities((List<Activity>) listActivities);
            this.listActivities= (List<Activity>) listActivities;
            sortedActivities=generateSortedActivityGraph();
        }
        else{
            this.name = name;
            this.tJobEntityList = (List<TJob>) listActivities;
        }

    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public List<TJob> gettJobClassList() {
        return tJobEntityList;
    }
    public void settJobClassList(List<TJob> tJobEntityList) {
        this.tJobEntityList = tJobEntityList;
    }
    /**
     * Method that validates the list of activities. Checks that there are final and initial activities
     * @param listActivities List with the activities to check
     */
    private void validateListActivities(List<Activity> listActivities) throws EmptyInputException,
            NoFinalActivitiesException {
        if (listActivities.isEmpty()) {
            throw new EmptyInputException("The activities List is empty");
        }
        List<List<Activity>> listAllPredecessors = listActivities.stream()
                .map(Activity::getListPredecessors).collect(Collectors.toList());
        List<Activity> listWithNoRepeatedPredecessors = listAllPredecessors.stream()
                .flatMap(Collection::stream)
                .distinct().collect(Collectors.toList());
        boolean thereIsAInitialActivity = listActivities.stream().anyMatch(act -> act.getListPredecessors().isEmpty());
        if (listActivities.size() <= listWithNoRepeatedPredecessors.size() || !thereIsAInitialActivity) {
            throw new NoFinalActivitiesException(String.format(
                    "The number of different predecessors is %d and activities %d and there is %b initial activities",
                    listActivities.size(), listWithNoRepeatedPredecessors.size(), thereIsAInitialActivity));
        }
    }

    public Map<Integer, LinkedList<Activity>> getSortedActivities() {
        return this.sortedActivities;
    }
    public Map<Integer, LinkedList<Activity>> generateSortedActivityGraph() {
        HashMap<Integer, LinkedList<Activity>> setOfOrderedActivities = new HashMap<>();
        List<Activity> listOfNonSortedActivities = new LinkedList<>(this.listActivities);
        List<Activity> allPassedActivities = new LinkedList<>();
        tJobEntityList = new LinkedList<>();
        int numberOfAddedActivities = this.listActivities.size();
        int timeMoment = 0;
        while (numberOfAddedActivities > 0) {
            Iterator<Activity> iterator = listOfNonSortedActivities.iterator();
            LinkedList<Activity> currentListActivities = new LinkedList<>();
            while (iterator.hasNext()) {
                Activity currentActivityEntity = iterator.next();
                TJob currentTjob= currentActivityEntity.getTJob();
                if ((timeMoment == 0) && (currentActivityEntity.getListPredecessors().isEmpty())) {
                    numberOfAddedActivities--;
                    currentTjob.setStage(timeMoment);
                    currentTjob.setIdTJob("TJob" +tJobIdentifier.values()[tJobEntityList.size()]);
                    tJobEntityList.add(currentTjob);
                    currentListActivities.add(currentActivityEntity);

                } else {
                    if (new HashSet<>(allPassedActivities).containsAll(currentActivityEntity.getListPredecessors())) {
                        currentListActivities.add(currentActivityEntity);
                        currentTjob.setStage(timeMoment);
                        currentTjob.setIdTJob("TJob" +tJobIdentifier.values()[tJobEntityList.size()]);
                        tJobEntityList.add(currentTjob);
                        numberOfAddedActivities--;
                    }
                }
            }

            setOfOrderedActivities.put(timeMoment, currentListActivities);
            allPassedActivities.addAll(currentListActivities);
            listOfNonSortedActivities.removeAll(currentListActivities);
            timeMoment++;
        }
        return setOfOrderedActivities;
    }
    private enum tJobIdentifier {
        A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z, AA, AB, AC, AD, AE, AF, AG, AH,
        AI, AJ, AK
    }
}