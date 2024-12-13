package giis.retorch.orchestration.scheduler;

import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.TJob;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
/**
 * The {@code Scheduling}  provides a structure to store the {@code Resource}s and the {@code ScheduleStruct}s  ordered.
 * The scheduling is a succession Schedule Struct that represents chronological order of the TJobs. i.e.
 *      * (0,[Tjob1,Tjob2]) (1,[Tjob3,Tjob4,Tjob5...]
 */
public class Scheduling {

    private final Map<Integer, ScheduleStruct> listMoments;//List with all the moments of the schedule
    private final List<Resource> baseResources;  //List with all the resources available in the system

    public Scheduling(List<Resource> listResources) {
        this.baseResources = listResources;
        this.listMoments = new HashMap<>();
    }

    public Map<Integer, ScheduleStruct> getListMoments() {
        return listMoments;
    }

    /**
     * Main method of the struct, given a {@code TJob}, iterates over a list of moments <0, listTJobs>, <1,listTGJobs>,
     * <2,listTJobs> in order to allocate the TJob in some struct that has enough {@code Resource} to deploy it.
     * @param tJob {@code TJob} that would be entered into the struct
     */
    public void addTJob(TJob tJob) {
        int i = 1;
        if (baseResources.containsAll(tJob.getListResourceClasses())) {
            boolean isAdded = false;
            while (!isAdded) {//This method starts to iterate from the 0 moment, searching for a gap
                //If the list with moments contains the specified key don't create a new element in the list
                if (listMoments.containsKey(i)) {
                    //If the specified moment also contains enough resources to deploy the TJob, the resources are
                    //subtracted and the TJob is added to this moment.
                    if (listMoments.get(i).getResourcesAvailable().containsAll(tJob.getListResourceClasses())) {
                        ScheduleStruct currentSchedule = listMoments.get(i);
                        //Condition is changed and in the next iteration the loop finish
                        isAdded = currentSchedule.addTJob(tJob);
                        listMoments.put(i, currentSchedule);
                    }
                } else {
                    ScheduleStruct currentSchedule = new ScheduleStruct(new LinkedList<>(baseResources));
                    isAdded = currentSchedule.addTJob(tJob);
                    listMoments.put(i, currentSchedule);
                }
                i += 1;
            }
        }
    }
}