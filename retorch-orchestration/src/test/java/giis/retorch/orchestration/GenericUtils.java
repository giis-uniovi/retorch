package giis.retorch.orchestration;

import giis.retorch.orchestration.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

/**
 * {@code GenericUtils} class provides a series of methods that creates different types of Resources, Access Modes and
 * ElasticityModels for being used during the integration and unitary testing of the aggregator orchestrator and
 * classifier. This class is extended and overridden by {@code IntegrationUtils},{@code OrchestratorUtils},
 * {@code SchedulerUtils} and {@code ClassifierUtils}
 */
public class GenericUtils {

    protected static final String READONLY = "READONLY";
    protected static final String READWRITE = "READWRITE";
    protected static final String NO_ACCESS = "NOACCESS";
    protected static final String MOCK_ELASTIC_ID = "mockElasticResource";
    protected static final String MEDIUM_ELASTIC_ID = "mediumElasticResource";
    protected static final String LIGHT_ELASTIC_ID = "lightElasticResource";
    protected static final String HEAVY_INELASTIC_ID = "heavyInElasticResource";
    protected static final String LIGHT_INELASTIC_ID = "lightInElasticResource";
    protected static final String MEDIUM_INELASTIC_ID = "MedInElasRest";
    protected static final String DOCKER_IMAGE = "someplaceholder2[IMG:]someimage";
    protected static final String PARENT_ELASTIC = "elasticParent";
    protected static final String PARENT_INELASTIC = "parentAllInelastic";
    protected static final String ENCODING = "utf-8";


    public AccessMode getAccessModeHeavyInElasticResource() {
        return new AccessMode(new AccessModeTypes(READWRITE), false, 1, this.getHeavyInelasticResource());
    }

    public AccessMode getOtherAccessModeHeavyInelasticResource() {
        return new AccessMode(new AccessModeTypes(READWRITE), false, 1, this.getHeavyInelasticResource());
    }

    public AccessMode getAccessModeLightElasticResource() {
        return new AccessMode(new AccessModeTypes(READWRITE), true, 4, this.getLightElasticResource());
    }

    public AccessMode getMockElasticAccessMode() {
        return new AccessMode(new AccessModeTypes(READONLY), true, 4, this.getMockElasticResource());
    }

    public AccessMode getMediumElasticAccessMode() {
        return new AccessMode(new AccessModeTypes(READWRITE), false, 1, this.getMediumElasticResource());
    }

    public AccessMode getMediumInelasticAccessMode() {
        return new AccessMode(new AccessModeTypes(READWRITE),false,1,this.getMediumInelasticResource());
    }

    public ElasticityModel getElasticityModelLightElasticResource() {
        return new ElasticityModel("elasModelLightElasticResource", 35, 15.0);
    }

    public ElasticityModel getElasticityModelMediumInElastic() {
        return new ElasticityModel("elasModelMedInElastic", 2, 35.0);
    }

    public ElasticityModel getElasticityModelMockResource() {
        return new ElasticityModel("elasModelmockElasticResource", 50, 0);
    }

    public ElasticityModel getElasticityModelHeavyInelasticResource() {
        return new ElasticityModel("elasModelHeavyInElasRest", 1, 50.0);
    }

    public Resource getLightElasticResource() {
        return new Resource(LIGHT_ELASTIC_ID, Collections.singletonList(PARENT_ELASTIC), Collections.singletonList(LIGHT_INELASTIC_ID),
                getElasticityModelLightElasticResource(), Resource.type.LOGICAL, Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 1),
                new Capacity(Capacity.PROCESSOR_NAME, 0.5)), DOCKER_IMAGE);
    }

    public Resource getMockElasticResource() {
        return new Resource(MOCK_ELASTIC_ID, Collections.singletonList(PARENT_ELASTIC),
                Arrays.asList(LIGHT_ELASTIC_ID, HEAVY_INELASTIC_ID, MEDIUM_ELASTIC_ID, LIGHT_INELASTIC_ID),
                this.getElasticityModelMockResource(), Resource.type.LOGICAL, Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 0.2),
                new Capacity(Capacity.PROCESSOR_NAME, 0.5)), DOCKER_IMAGE);
    }

    public Resource getMediumInelasticResource() {
        return new Resource(MEDIUM_INELASTIC_ID, Collections.singletonList(PARENT_INELASTIC),
                Collections.singletonList(HEAVY_INELASTIC_ID), this.getElasticityModelMediumInElastic(),
                Resource.type.LOGICAL, new LinkedList<>(), DOCKER_IMAGE);
    }

    public Resource getMediumElasticResource() {
        ElasticityModel mediumElasModel = new ElasticityModel("elasModelMediumElasticResource", 10, 35.0);

        return new Resource(MEDIUM_ELASTIC_ID, Collections.singletonList(PARENT_ELASTIC), new LinkedList<>(), mediumElasModel,
                Resource.type.LOGICAL, Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 2.5),
                new Capacity(Capacity.PROCESSOR_NAME, 2)), DOCKER_IMAGE);
    }

    public Resource getHeavyInelasticResource() {
        return new Resource(HEAVY_INELASTIC_ID, Collections.singletonList(PARENT_INELASTIC), new LinkedList<>(), getElasticityModelHeavyInelasticResource(),
                Resource.type.LOGICAL, Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 4.0),
                new Capacity(Capacity.PROCESSOR_NAME, 0.6)), DOCKER_IMAGE);
    }

    public Resource getLightInelasticResource() {
        return new Resource(LIGHT_INELASTIC_ID, Collections.singletonList(PARENT_INELASTIC), Collections.singletonList(LIGHT_ELASTIC_ID),
                new ElasticityModel("elasModelLightInElasticResource",1,50), Resource.type.LOGICAL,
                Arrays.asList(new Capacity(Capacity.MEMORY_NAME, 1), new Capacity(Capacity.PROCESSOR_NAME, 0.5)),
                DOCKER_IMAGE);
    }

}