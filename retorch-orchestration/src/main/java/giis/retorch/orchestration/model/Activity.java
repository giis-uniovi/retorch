package giis.retorch.orchestration.model;

import java.util.LinkedList;
import java.util.List;

/**
 * RETORCH Activities that represents the execution of TJobs Sequence
 */
public class Activity {

    private final TJob tJob;
    private final List<Activity> listPredecessors;    //List with the activities that proceed the current one

    /**
     * Constructor only requires the TJob, and instantiates by default with no predecessors
     * @param subjectTJob TJob of the activity
     */
    public Activity(TJob subjectTJob) {
        this.tJob = subjectTJob;
        this.listPredecessors = new LinkedList<>();
    }
    /**
     * Constructor that instantiates an Activity with a single TJob and Predecessor
     * @param subjectTJob TJob of the activity
     * @param predecessor Activity that is previously executed (predecessor)
     */
    public Activity(TJob subjectTJob,Activity predecessor) {
        this.tJob = subjectTJob;
        this.listPredecessors = new LinkedList<>();
        this.listPredecessors.add(predecessor);
    }
    /**
     * Constructor that instantiates an Activity with a single TJob and Predecessor
     * @param subjectTJob TJob of the activity
     * @param listPredecessor List of Activities that preceed the current one (predecessors)
     */
    public Activity(TJob subjectTJob,List<Activity> listPredecessor) {
        this.tJob = subjectTJob;
        this.listPredecessors =listPredecessor;
    }
    /**
     * This method add a predecessor activity to the given activity,that would be used to order them into the
     * orchestration
     * @param activity activity that is the predecessor of the current one
     */
    public void addPredecessor(Activity activity) {
        this.listPredecessors.add(activity);
    }
    /**
     * Support method that checks if  the given Activity TJob is using the resource that is passed as parameter
     * @param resource ResourceClass with the resource to check
     */

    public boolean isUsingTheSameResource(Resource resource) {
        return this.tJob.getListResourceClasses().contains(resource);
    }
    @Override
    public int hashCode() {
        return super.hashCode();
    }
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!obj.getClass().equals(this.getClass())) return false;
        Activity objectToCompare = ((Activity) obj);
        return this.listPredecessors.containsAll(objectToCompare.getListPredecessors()) && this.tJob.equals(objectToCompare.getTJob());
    }
    public List<Activity> getListPredecessors() {
        return listPredecessors;
    }
    public TJob getTJob() {
        return tJob;
    }
    @Override
    public String toString() {
        return "ActivityClass {\n" + "tJob=" + tJob + ", \nlistPredecessors=" + listPredecessors + "}\n";
    }
}