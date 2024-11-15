package giis.retorch.orchestration;

import giis.retorch.orchestration.model.*;

import java.util.LinkedList;
import java.util.List;

public class GenericUtils {

    protected static final String READONLY = "READONLY";
    protected static final String READWRITE = "READWRITE";
    protected static final String MOCK_ELASTIC_ID = "mockElasticResource";
    protected static final String MEDIUM_ELASTIC_ID = "mediumElasticResource";
    protected static final String LIGHT_ELASTIC_ID = "lightElasticResource";
    protected static final String HEAVY_INELASTIC_ID = "heavyInElasRest";

    public ElasticityModel getElasticityModelMockResource() {
        ElasticityModel expectedElasticityModelMock = new ElasticityModel("elasticityIDMockElastic");
        expectedElasticityModelMock.setElasticity(50);
        expectedElasticityModelMock.setElasticityCost(0);
        return expectedElasticityModelMock;
    }

    public AccessMode getAccessModeHeavyInElasticResource() {
        AccessMode expectedAccessModeHeavy = new AccessMode();
        expectedAccessModeHeavy.setSharing(false);
        expectedAccessModeHeavy.setConcurrency(1);
        expectedAccessModeHeavy.setResource(this.getHeavyInelasticResource());
        expectedAccessModeHeavy.setType(new AccessModeTypes(READWRITE));
        return expectedAccessModeHeavy;
    }

    public AccessMode getOtherAccessModeHeavyInelasticResource() {
        AccessMode expectedAccessModeHeavy = new AccessMode(this.getAccessModeHeavyInElasticResource());
        expectedAccessModeHeavy.setSharing(false);
        expectedAccessModeHeavy.setConcurrency(1);
        expectedAccessModeHeavy.setResource(this.getHeavyInelasticResource());
        expectedAccessModeHeavy.setType(new AccessModeTypes(READWRITE));
        return expectedAccessModeHeavy;
    }

    public AccessMode getAccessModeLightElasticResource() {
        AccessMode expectedAccessModeHeavy = new AccessMode();
        expectedAccessModeHeavy.setSharing(true);
        expectedAccessModeHeavy.setConcurrency(4);
        expectedAccessModeHeavy.setResource(this.getLightElasticResource());
        expectedAccessModeHeavy.setType(new AccessModeTypes(READWRITE));
        return expectedAccessModeHeavy;
    }

    public Resource getLightElasticResource() {
        Resource lightElasticResource = new Resource(LIGHT_ELASTIC_ID);
        lightElasticResource.setElasticityModel(getElasticityModelLightElasticResource());
        List<Capacity> requiredCapacities = new LinkedList<>();
        requiredCapacities.add(new Capacity("memory", 1));
        requiredCapacities.add(new Capacity("processor", 0.5));
        lightElasticResource.setMinimalCapacities(requiredCapacities);
        return lightElasticResource;
    }

    public ElasticityModel getElasticityModelLightElasticResource() {
        ElasticityModel expectedElasticityModelMySQL = new ElasticityModel("elasModelLightElasticResource");
        expectedElasticityModelMySQL.setElasticity(35);
        expectedElasticityModelMySQL.setElasticityCost(15.0);
        return expectedElasticityModelMySQL;
    }

    public AccessMode getMockElasticAccessMode() {
        AccessMode expectedAccessModeHeavy = new AccessMode();
        expectedAccessModeHeavy.setSharing(true);
        expectedAccessModeHeavy.setConcurrency(4);
        expectedAccessModeHeavy.setResource(this.getMockElasticResource());
        expectedAccessModeHeavy.setType(new AccessModeTypes(READONLY));
        return expectedAccessModeHeavy;
    }

    public Resource getMockElasticResource() {
        Resource mockElasticResource = new Resource(LIGHT_ELASTIC_ID);
        mockElasticResource.setResourceID("mockElasticResource");
        ElasticityModel modelMock = new ElasticityModel("elasModelmockElasticResource", 50, 0.0);
        mockElasticResource.setElasticityModel(modelMock);
        List<Capacity> requiredCapacities = new LinkedList<>();
        requiredCapacities.add(new Capacity("memory", 0.5));
        requiredCapacities.add(new Capacity("processor", 2.0));
        mockElasticResource.setMinimalCapacities(requiredCapacities);
        mockElasticResource.addReplaceableResource(LIGHT_ELASTIC_ID);
        mockElasticResource.addReplaceableResource(HEAVY_INELASTIC_ID);
        return mockElasticResource;
    }

    public void generateSystemResources() {
        getHeavyInelasticResource();
        getMockElasticResource();
        getLightElasticResource();
    }

    public Resource getHeavyInelasticResource() {
        Resource heavyInelasticResource = new Resource(HEAVY_INELASTIC_ID);
        heavyInelasticResource.addHierarchyParent("parentAllInelastic");
        heavyInelasticResource.setElasticityModel(getElasticityModelHeavyInelasticResource());
        heavyInelasticResource.setResourceType(Resource.type.LOGICAL);
        List<Capacity> requiredCapacities = new LinkedList<>();
        requiredCapacities.add(new Capacity("memory", 4.0));
        requiredCapacities.add(new Capacity("processor", 0.6));
        heavyInelasticResource.setMinimalCapacities(requiredCapacities);
        return heavyInelasticResource;
    }

    public ElasticityModel getElasticityModelHeavyInelasticResource() {
        ElasticityModel expectedElasticityModelHeavy = new ElasticityModel("elasModelHeavyInElasRest");
        expectedElasticityModelHeavy.setElasticity(1);
        expectedElasticityModelHeavy.setElasticityCost(50.0);
        return expectedElasticityModelHeavy;
    }
}