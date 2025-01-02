package giis.retorch.profiling;



import java.util.*;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.Capacity;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.model.Resource;
import giis.retorch.orchestration.model.ResourceInstance;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.profiling.model.BillingOption;
import giis.retorch.profiling.model.CloudObjectInstance;
import giis.retorch.profiling.model.CapacityContracted;

public class ProfileGeneratorUtils {


    public ExecutionPlan generateExecutionPlan() throws NoFinalActivitiesException, EmptyInputException {
        //Webserver Resource
        LinkedList<Capacity> listwebservCapacities = new LinkedList<>();
        listwebservCapacities.add(new Capacity(Capacity.MEMORY_NAME, 1.465));
        listwebservCapacities.add(new Capacity(Capacity.PROCESSOR_NAME, 0.5));
        listwebservCapacities.add(new Capacity(Capacity.STORAGE_NAME, 0.92));
        ResourceInstance webserver = new ResourceInstance("webserver", listwebservCapacities);
        //Database Resource
        LinkedList<Capacity> listDbCapacities = new LinkedList<>();
        listDbCapacities.add(new Capacity(Capacity.MEMORY_NAME, 0.2929));
        listDbCapacities.add(new Capacity(Capacity.PROCESSOR_NAME, 0.2));
        listDbCapacities.add(new Capacity(Capacity.STORAGE_NAME, 0.5));
        ResourceInstance database = new ResourceInstance("database", listDbCapacities);
        //Openvidu Resource
        LinkedList<Capacity> listOpenviduCapacities = new LinkedList<>();
        listOpenviduCapacities.add(new Capacity(Capacity.MEMORY_NAME, 8));
        listOpenviduCapacities.add(new Capacity(Capacity.PROCESSOR_NAME, 2));
        listOpenviduCapacities.add(new Capacity(Capacity.STORAGE_NAME, 0.88));
        ResourceInstance openvidu = new ResourceInstance("openvidu", listOpenviduCapacities);
        //MockOpenvidu Resource
        LinkedList<Capacity> listMockOpenviduCapacities = new LinkedList<>();
        listMockOpenviduCapacities.add(new Capacity(Capacity.MEMORY_NAME, 0.048));
        listMockOpenviduCapacities.add(new Capacity(Capacity.PROCESSOR_NAME, 0.25));
        listMockOpenviduCapacities.add(new Capacity(Capacity.STORAGE_NAME, 0.31));
        ResourceInstance mockOpenvidu = new ResourceInstance("mockopenvidu", listMockOpenviduCapacities);
        //Executor resource
        LinkedList<Capacity> listExecutorCapacities = new LinkedList<>();
        listExecutorCapacities.add(new Capacity(Capacity.MEMORY_NAME, 0.14));
        listExecutorCapacities.add(new Capacity(Capacity.PROCESSOR_NAME, 0.25));
        listExecutorCapacities.add(new Capacity(Capacity.STORAGE_NAME, 0.49));
        ResourceInstance executor = new ResourceInstance("executor", listExecutorCapacities);
        //WebBrowser resource
        LinkedList<Capacity> listBrowserCapacities = new LinkedList<>();
        listBrowserCapacities.add(new Capacity(Capacity.MEMORY_NAME, 1));
        listBrowserCapacities.add(new Capacity(Capacity.PROCESSOR_NAME, 0.5));
        listBrowserCapacities.add(new Capacity(Capacity.STORAGE_NAME, 1.16));
        listBrowserCapacities.add(new Capacity(Capacity.SLOTS_NAME, 1));

        ResourceInstance browser = new ResourceInstance("webbrowser", listBrowserCapacities);
        //Initialize the different list of resources for each TJob
        List<Resource> listResourcesTJobC = new LinkedList<>();
        listResourcesTJobC.add(webserver);
        listResourcesTJobC.add(database);
        listResourcesTJobC.add(mockOpenvidu);
        listResourcesTJobC.add(executor);
        listResourcesTJobC.add(browser);

        List<Resource> listResourcesTJobE = new LinkedList<>();
        listResourcesTJobE.add(webserver);
        listResourcesTJobE.add(database);
        listResourcesTJobE.add(openvidu);
        listResourcesTJobE.add(executor);
        listResourcesTJobE.add(browser);
        listResourcesTJobE.add(browser);
        //Initialize the list of TJobs of the Plan
        List<TJob> listTJobs = new LinkedList<>();
        listTJobs.add(new TJob("tjobc", 0, listResourcesTJobC));
        listTJobs.add(new TJob("tjobd", 0, listResourcesTJobC));
        listTJobs.add(new TJob("tjobe", 0, listResourcesTJobE));
        listTJobs.add(new TJob("tjobf", 0, listResourcesTJobC));
        listTJobs.add(new TJob("tjobg", 0, listResourcesTJobC));

        listTJobs.add(new TJob("tjobh", 0, listResourcesTJobC));
        listTJobs.add(new TJob("tjobi", 0, listResourcesTJobC));
        listTJobs.add(new TJob("tjobj", 0, listResourcesTJobC));
        listTJobs.add(new TJob("tjobk", 0, listResourcesTJobC));
        listTJobs.add(new TJob("tjobl", 0, listResourcesTJobC));

        listTJobs.add(new TJob("tjobm", 0, listResourcesTJobE));
        listTJobs.add(new TJob("tjobn", 0, listResourcesTJobC));
        //Create the plan with the list of TJobs and return it
        return new ExecutionPlan("RETORCH Schedulling", listTJobs);
    }
    public CloudObjectInstance generateVMCloudObjectInstances() {
        Map<String, CapacityContracted> listVMCapacities = new HashMap<>();
        listVMCapacities.put(Capacity.MEMORY_NAME, new CapacityContracted(Capacity.MEMORY_NAME, 32, 32));
        listVMCapacities.put(Capacity.PROCESSOR_NAME, new CapacityContracted(Capacity.PROCESSOR_NAME, 12,
                12));
        listVMCapacities.put(Capacity.STORAGE_NAME, new CapacityContracted(Capacity.STORAGE_NAME, 32, 32));
        listVMCapacities.put(Capacity.SLOTS_NAME, new CapacityContracted(Capacity.SLOTS_NAME, 8, 1));

        HashMap<String, Double> capacityprices = new HashMap<>();
        capacityprices.put(Capacity.MEMORY_NAME, 0.5);
        capacityprices.put(Capacity.PROCESSOR_NAME, 0.5);
        capacityprices.put(Capacity.STORAGE_NAME, 0.5);
        capacityprices.put(Capacity.SLOTS_NAME, 1.20);
        BillingOption option = new BillingOption("As-you-go", "Azure", capacityprices, 3600);

        CloudObjectInstance objectInstance = new CloudObjectInstance("AzureVM", option, listVMCapacities);
        objectInstance.setLifecycleTimes(0, 3, 4, 661, 3580, 3600);
        return objectInstance;
    }

    public CloudObjectInstance generateContainersCloudObjectInstances() {
        Map<String, CapacityContracted> listVMCapacities = new HashMap<>();
        listVMCapacities.put(Capacity.MEMORY_NAME, new CapacityContracted(Capacity.MEMORY_NAME, 32, 0.1));
        listVMCapacities.put(Capacity.PROCESSOR_NAME, new CapacityContracted(Capacity.PROCESSOR_NAME, 12,
                0.1));
        listVMCapacities.put(Capacity.STORAGE_NAME, new CapacityContracted(Capacity.STORAGE_NAME, 32, 0.1));
        listVMCapacities.put(Capacity.SLOTS_NAME, new CapacityContracted(Capacity.SLOTS_NAME, 0, 0));

        HashMap<String, Double> capacityprices = new HashMap<>();
        capacityprices.put(Capacity.MEMORY_NAME, 0.5);
        capacityprices.put(Capacity.PROCESSOR_NAME, 0.5);
        capacityprices.put(Capacity.STORAGE_NAME, 0.5);
        capacityprices.put(Capacity.SLOTS_NAME, 0.0);
        BillingOption option = new BillingOption("As-you-go", "Azure", capacityprices, 1);

        CloudObjectInstance objectInstance = new CloudObjectInstance("Azure Containers", option, listVMCapacities);
        objectInstance.setLifecycleTimes(0, 3, 4, 661, 3580, 3600);
        return objectInstance;
    }
    public CloudObjectInstance generateBrowserServiceCloudObjectInstances() {
        Map<String, CapacityContracted> listVMCapacities = new HashMap<>();
        listVMCapacities.put(Capacity.SLOTS_NAME, new CapacityContracted(Capacity.SLOTS_NAME, 8, 1));
        listVMCapacities.put(Capacity.MEMORY_NAME, new CapacityContracted(Capacity.MEMORY_NAME, 0, 0));
        listVMCapacities.put(Capacity.PROCESSOR_NAME, new CapacityContracted(Capacity.PROCESSOR_NAME, 0,
                0));
        listVMCapacities.put(Capacity.STORAGE_NAME, new CapacityContracted(Capacity.STORAGE_NAME, 0, 0));

        HashMap<String, Double> capacityprices = new HashMap<>();
        capacityprices.put(Capacity.SLOTS_NAME, 1.20);
        BillingOption option = new BillingOption("As-you-go", "Azure", capacityprices, 60);

        CloudObjectInstance objectInstance = new CloudObjectInstance("Selenoid", option, listVMCapacities);
        objectInstance.setLifecycleTimes(0, 3, 4, 661, 3580, 3600);
        return objectInstance;
    }


}