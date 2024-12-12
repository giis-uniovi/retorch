package giis.retorch.orchestration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * The {@code Resource} class represents a Resource required by one or more {@code TestCases}. Provides the different
 * Resource attributes: identifier, hierarchy Parent, {@code Resource} that can replace it, the minimal {@code Capacity}
 * required,the {@code ElasticityModel}, the type and its docker image.
 */
public class Resource {

    public enum type {PHYSICAL, LOGICAL, COMPUTATIONAL}

    private final Logger logResourceClass = LoggerFactory.getLogger(Resource.class);

    Marker errorMarker = MarkerFactory.getMarker("Error");
    private String resourceID;
    private List<String> hierarchyParent;
    private List<String> replaceable;
    private List<Capacity> minimalCapacities;
    private ElasticityModel elasticityModel;
    private type resourceType = type.LOGICAL;
    private String dockerImage;

    public Resource(String resourceID) {
        super();
        this.resourceID = resourceID;
        this.replaceable = new LinkedList<>();
        this.hierarchyParent = new LinkedList<>();
    }

    public Resource() {}

    /**
     * Resource constructor
     * @param resourceID      String with the resource identifier
     * @param elasticityModel Resource Elasticity model
     * @param replaceable     List with the ids of those resources that could replace the resource
     * @param resourceType    type of the resource (LOGICAL, PHYSICAL or COMPUTATIONAL)
     * @param hierarchyParent String with the  resource hierarchical parent
     * @param minimalCapacities List with the minimal {@link Capacity}  required by the resource
     * @param dockerImage String with the Resource docker image name
     */
    public Resource(@JsonProperty("resourceId") String resourceID,
                    @JsonProperty("hierarchyParent") List<String> hierarchyParent,
                    @JsonProperty("replaceable") List<String> replaceable,
                    @JsonProperty("elasticityModel") ElasticityModel elasticityModel,
                    @JsonProperty("resourceType") type resourceType,
                    @JsonProperty("minimalCapacities")List<Capacity> minimalCapacities,
                    @JsonProperty("dockerImage") String dockerImage) {
        this.resourceID = resourceID;
        this.hierarchyParent = hierarchyParent;
        this.replaceable = replaceable;
        this.elasticityModel = elasticityModel;
        this.resourceType = resourceType;
        this.minimalCapacities = minimalCapacities;
        this.dockerImage=dockerImage;
    }

    public Resource(Resource resource) {
        this.dockerImage = resource.dockerImage;
        this.elasticityModel = resource.getElasticityModel();
        this.errorMarker = resource.errorMarker;
        this.hierarchyParent = new LinkedList<>();
        this.hierarchyParent.addAll(resource.getHierarchyParent());
        this.minimalCapacities = resource.getMinimalCapacities();
        this.replaceable = new LinkedList<>();
        this.replaceable.addAll(resource.getReplaceable());
        this.resourceID = resource.getResourceID();
        this.resourceType = resource.getResourceType();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "res{" + this.getResourceID() + '\'' + ", p=" + this.getHierarchyParent() + ", r=" + this.getReplaceable() + ", " + this.getElasticityModel() + ", " +
                this.getResourceType()+","+this.getMinimalCapacities()+ "}";
    }

    @Override
    public boolean equals(Object obj) {
        String message;
        if (obj == null || !obj.getClass().equals(this.getClass())) return false;
        Resource resClass = (Resource) obj;
        if (!this.elasticityModel.equals(resClass.elasticityModel)) {
            if (this.elasticityModel.getElasticityID().equals(resClass.elasticityModel.getElasticityID())){
                message=String.format("The elasticityModel of the resources with the same identifier " + "%s differ", resClass.getResourceID());
                logResourceClass.error(message);}
            return false;
        }
        if ((!containsAllReplaceableElements(this.replaceable, resClass.getReplaceable())) ||
                (!this.hierarchyParent.equals(resClass.hierarchyParent)) ||
                (!this.getResourceType().equals(resClass.getResourceType()))) return false;
        return this.getResourceID().equals(resClass.getResourceID());
    }

    public String getDockerImage() {return dockerImage;}
    public ElasticityModel getElasticityModel() {return elasticityModel;}
    public List<String> getHierarchyParent() {return hierarchyParent;}
    public List<Capacity> getMinimalCapacities() {return minimalCapacities;}
    public String getResourceID() {return resourceID;}
    public type getResourceType() {return resourceType;}
    public List<String> getReplaceable() {
        this.replaceable.sort(String::compareToIgnoreCase);
        return replaceable;
    }

    public void setDockerImage(String dockerImage) {this.dockerImage = dockerImage;}
    public void setElasticityModel(ElasticityModel elasticityModel) {this.elasticityModel = elasticityModel;}
    public void setHierarchyParent(List<String> hierarchyParent) {this.hierarchyParent = hierarchyParent;}
    public void setMinimalCapacities(List<Capacity> minimalCapacities) {
        minimalCapacities.sort(Comparator.comparing(Capacity::getName));
        this.minimalCapacities = minimalCapacities;
    }
    public void setReplaceable(List<String> replaceable) {this.replaceable = replaceable;}
    public void setResourceID(String resourceID) {this.resourceID = resourceID;}
    public void setResourceType(type resourceType) {this.resourceType = resourceType;}

    public void addHierarchyParent(String idResource) {
        this.hierarchyParent.add(idResource);
    }

    public void addReplaceableResource(String resourceID) {
        this.replaceable.add(resourceID);
    }

    public boolean containsAllReplaceableElements(List<String> listOne, List<String> listTwo) {
        if (listOne.size() != listTwo.size())  return false;
        for (String currentRes : listOne) {
            if (!listTwo.contains(currentRes)) return false;
        }
        return true;
    }

}