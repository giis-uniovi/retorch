package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TJob;

import java.util.LinkedList;
import java.util.List;

/**
 * This structure is used to maintain an updated list of the free resources available at each time moment and
 * the list of TJobs that are in the current time moment.For each TJob added, its resources are subtracted
 * of the total amount of resources available
 */
public class ScheduleStruct {

    private final List<Resource> resourcesAvailable;//Resources available
    private final List<TJob> listTJobs; //List with all the TJobs contained

    /**
     * Default constructor
     * @param listResources List of ResourceClass with all available resources in each time moment
     */
    public ScheduleStruct(List<Resource> listResources) {
        this.resourcesAvailable = listResources;
        this.listTJobs = new LinkedList<>();
    }

    public List<Resource> getResourcesAvailable() {
        return resourcesAvailable;
    }
    public List<TJob> getListTJobs() {
        return listTJobs;
    }
    /**
     * This method add a TJob to the current struct. First checks if the current struct has enough resources for this
     * TJob. If there are enough resources, the resources required by the TJob are removed of the available resource
     * list
     * and the method returns true.
     * If there aren't enough resources the method returns false
     * @param tJob TJob to be added
     */
    public boolean addTJob(TJob tJob) {
        List<Resource> resourcesRequiredByTJob = tJob.getListResourceClasses();
        if (resourcesAvailable.containsAll(resourcesRequiredByTJob)) {
            removeListResources(resourcesRequiredByTJob, resourcesAvailable);
            listTJobs.add(tJob);
            return true;
        } else {
            return false;
        }
    }
    /**
     * Support method that given a list of resources, remove them of the Struct available resources.
     */
    public void removeListResources(List<Resource> elementsToDelete, List<Resource> listToBeDeleted) {
        for (Resource resToBeDeleted : elementsToDelete) {
            int i = 0;
            boolean isDeleted = false;
            while (i < listToBeDeleted.size() && !isDeleted) {
                if (listToBeDeleted.get(i).equals(resToBeDeleted)) {
                    listToBeDeleted.remove(i);
                    isDeleted = true;
                }
                i++;
            }
        }
    }
}