package giis.retorch.orchestration.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ElasticityModel {

    private String elasticityID;
    private int elasticity;
    private double elasticityCost;

    public ElasticityModel(String idElasticity) {this.elasticityID = idElasticity;}

    /**
     * Elasticity model constructor
     * @param elasticity     Integer with the maximum amount of resources that can be deployed
     * @param elasticityCost Double with the cost of one instance deployment
     * @param elasticityID   String with the ElasticityID
     */
    public ElasticityModel(@JsonProperty("elasticityID") String elasticityID,
                           @JsonProperty("elasticity") int elasticity,
                           @JsonProperty("elasticityCost") double elasticityCost) {
        this.elasticityID = elasticityID;
        this.elasticity = elasticity;
        this.elasticityCost = elasticityCost;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "elas{" + "'" + elasticityID + '\'' + ", e=" + elasticity + ", cost=" + elasticityCost + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null) ||(!obj.getClass().equals(this.getClass()))) return false;
        ElasticityModel currentType = ((ElasticityModel) obj);
        return currentType.getElasticity() == this.elasticity && currentType.getElasticityCost() == this.elasticityCost &&
                currentType.getElasticityID().equals(this.getElasticityID());
    }

    public int getElasticity() {
        return elasticity;
    }
    public double getElasticityCost() {
        return elasticityCost;
    }
    public String getElasticityID() {
        return elasticityID;
    }

    public void setElasticityID(String elasticityID) {
        this.elasticityID = elasticityID;
    }
    public void setElasticityCost(double elasticityCost) {
        this.elasticityCost = elasticityCost;
    }
    public void setElasticity(int elasticity) {
        this.elasticity = elasticity;
    }

}