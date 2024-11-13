package giis.retorch.orchestration.utils;

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

    public ElasticityModelEntity getElasticityModelMockResource() {
        ElasticityModelEntity expectedElasticityModelMock = new ElasticityModelEntity("elasticityIDMockElastic");
        expectedElasticityModelMock.setElasticity(50);
        expectedElasticityModelMock.setElasticityCost(0);
        return expectedElasticityModelMock;
    }

    public AccessModeEntity getAccessModeHeavyInElasticResource() {
        AccessModeEntity expectedAccessModeHeavy = new AccessModeEntity();
        expectedAccessModeHeavy.setSharing(false);
        expectedAccessModeHeavy.setConcurrency(1);
        expectedAccessModeHeavy.setResource(this.getHeavyInelasticResource());
        expectedAccessModeHeavy.setAccessMode(new AccessModeTypesEntity(READWRITE));
        return expectedAccessModeHeavy;
    }

    public AccessModeEntity getOtherAccessModeHeavyInelasticResource() {
        AccessModeEntity expectedAccessModeHeavy = new AccessModeEntity(this.getAccessModeHeavyInElasticResource());
        expectedAccessModeHeavy.setSharing(false);
        expectedAccessModeHeavy.setConcurrency(1);
        expectedAccessModeHeavy.setResource(this.getHeavyInelasticResource());
        expectedAccessModeHeavy.setAccessMode(new AccessModeTypesEntity(READWRITE));
        return expectedAccessModeHeavy;
    }

    public AccessModeEntity getAccessModeLightElasticResource() {
        AccessModeEntity expectedAccessModeHeavy = new AccessModeEntity();
        expectedAccessModeHeavy.setSharing(true);
        expectedAccessModeHeavy.setConcurrency(4);
        expectedAccessModeHeavy.setResource(this.getLightElasticResource());
        expectedAccessModeHeavy.setAccessMode(new AccessModeTypesEntity(READWRITE));
        return expectedAccessModeHeavy;
    }

    public ResourceEntity getLightElasticResource() {
        ResourceEntity lightElasticResource = new ResourceEntity(LIGHT_ELASTIC_ID);
        lightElasticResource.setElasticityModel(getElasticityModelLightElasticResource());
        List<CapacityEntity> requiredCapacities = new LinkedList<>();
        requiredCapacities.add(new CapacityEntity("memory", 1));
        requiredCapacities.add(new CapacityEntity("processor", 0.5));
        lightElasticResource.setMinimalCapacities(requiredCapacities);
        return lightElasticResource;
    }

    public ElasticityModelEntity getElasticityModelLightElasticResource() {
        ElasticityModelEntity expectedElasticityModelMySQL = new ElasticityModelEntity("elasModelLightElasticResource");
        expectedElasticityModelMySQL.setElasticity(35);
        expectedElasticityModelMySQL.setElasticityCost(15.0);
        return expectedElasticityModelMySQL;
    }

    public AccessModeEntity getMockElasticAccessMode() {
        AccessModeEntity expectedAccessModeHeavy = new AccessModeEntity();
        expectedAccessModeHeavy.setSharing(true);
        expectedAccessModeHeavy.setConcurrency(4);
        expectedAccessModeHeavy.setResource(this.getMockElasticResource());
        expectedAccessModeHeavy.setAccessMode(new AccessModeTypesEntity(READONLY));
        return expectedAccessModeHeavy;
    }

    public ResourceEntity getMockElasticResource() {
        ResourceEntity mockElasticResource = new ResourceEntity(LIGHT_ELASTIC_ID);
        mockElasticResource.setResourceID("mockElasticResource");
        ElasticityModelEntity modelMock = new ElasticityModelEntity("elasModelmockElasticResource", 50, 0.0);
        mockElasticResource.setElasticityModel(modelMock);
        List<CapacityEntity> requiredCapacities = new LinkedList<>();
        requiredCapacities.add(new CapacityEntity("memory", 0.5));
        requiredCapacities.add(new CapacityEntity("processor", 2.0));
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

    public ResourceEntity getHeavyInelasticResource() {
        ResourceEntity heavyInelasticResource = new ResourceEntity(HEAVY_INELASTIC_ID);
        heavyInelasticResource.addHierarchyParent("parentAllInelastic");
        heavyInelasticResource.setElasticityModel(getElasticityModelHeavyInelasticResource());
        heavyInelasticResource.setResourceType(ResourceEntity.type.LOGICAL);
        List<CapacityEntity> requiredCapacities = new LinkedList<>();
        requiredCapacities.add(new CapacityEntity("memory", 4.0));
        requiredCapacities.add(new CapacityEntity("processor", 0.6));
        heavyInelasticResource.setMinimalCapacities(requiredCapacities);
        return heavyInelasticResource;
    }

    public ElasticityModelEntity getElasticityModelHeavyInelasticResource() {
        ElasticityModelEntity expectedElasticityModelHeavy = new ElasticityModelEntity("elasModelHeavyInElasRest");
        expectedElasticityModelHeavy.setElasticity(1);
        expectedElasticityModelHeavy.setElasticityCost(50.0);
        return expectedElasticityModelHeavy;
    }
}