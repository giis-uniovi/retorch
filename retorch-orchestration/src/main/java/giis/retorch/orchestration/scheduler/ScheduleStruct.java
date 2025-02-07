package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TJob;

import java.util.LinkedList;
import java.util.List;

/**
 * The {@code ScheduleStruct}  provides a structure to store the {@code Resource}s and the  {@code TJob}s available.
 *It also provides the necessary methods to check if  {@code Resource}s and the  {@code TJob}s are already contained
 */
public class ScheduleStruct {

    private final List<Resource> resourcesAvailable;//Resources available
    private final List<TJob> listTJobs; //List with all the TJobs contained

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