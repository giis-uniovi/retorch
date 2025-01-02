package giis.retorch.orchestration.model;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;


import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code ExecutionPlan} class represents a series of TJobs ordered.
 * It contains a name for the {@code ExecutionPlan} and a list of {@code TJobClass} objects with their
 * respective {@code Resource}s and their sequentiation through a list of  {@code Activity}.
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
    public List<TJob> gettJobClassList() {
        return tJobEntityList;
    }
    public Map<Integer, LinkedList<Activity>> getSortedActivities() {
        return this.sortedActivities;
    }

    public void setName(String name) {
        this.name = name;
    }
    public void settJobClassList(List<TJob> tJobEntityList) {
        this.tJobEntityList = tJobEntityList;
    }

    /**
     * Method that validates the list of {@code Activity}. Checks that there are final and initial {@code Activity}
     * @param listActivities List with the {@code Activity} to check
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

    /**
     * Method that generates the sorted {@code Activity} graph.
     */
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
                if ((timeMoment == 0) && (currentActivityEntity.getListPredecessors().isEmpty()) || new HashSet<>(allPassedActivities).containsAll(currentActivityEntity.getListPredecessors())) {
                    addTJob(currentActivityEntity, timeMoment);
                    currentListActivities.add(currentActivityEntity);
                    numberOfAddedActivities--;
                }
            }

            setOfOrderedActivities.put(timeMoment, currentListActivities);
            allPassedActivities.addAll(currentListActivities);
            listOfNonSortedActivities.removeAll(currentListActivities);
            timeMoment++;
        }
        return setOfOrderedActivities;
    }
    public void addTJob(Activity activity, int timeMoment) {
        activity.getTJob().setStage(timeMoment);
        activity.getTJob().setIdTJob(!activity.getTJob().getListTestCases().isEmpty()?"TJob" + getIdentifier(tJobEntityList.size()):"GatewayActivity");
        if (!activity.getTJob().getListTestCases().isEmpty()) {tJobEntityList.add(activity.getTJob());}
    }
    /**
     * Generates an alphabetical identifier for a {@code TJob} according to the number of TJob:
     * e.g. A,B,C..., AA,AB,AC... BA,BB,BC...
     */
    public static String getIdentifier(int index) {
        StringBuilder result = new StringBuilder();
        while (index >= 0) {
            // Calculate the character to append
            result.insert(0, (char) ('A' + (index % 26)));
            // Decrease the index and move to the next "digit"
            index = index / 26 - 1;
        }
        return result.toString();
    }
}