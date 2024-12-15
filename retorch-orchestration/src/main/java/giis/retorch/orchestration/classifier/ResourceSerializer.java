package giis.retorch.orchestration.classifier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import giis.retorch.orchestration.model.ElasticityModel;
import giis.retorch.orchestration.model.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * The {@code ResourceSerializer} class provides utilities for managing Resource configurations
 * in the RETORCH orchestration generator. Supports the serialization of Resources into JSON files and
 * its deserialization back into Java objects, enabling the retrieval of Resources attributes from Resources
 * JSON configuration files.
 */
public class ResourceSerializer {

    private final Logger logSerializer = LoggerFactory.getLogger(ResourceSerializer.class);
    private static final String FOLDER_RESOURCES = "retorchfiles/configurations/";//Base path of the resource files

    final HashMap<String, Resource> resourcesToSerialize;//Map with the dictionary of resources to serialize
    final ObjectMapper mapper;

    /**
     * RETORCH Serializer provides utils to deserialize the configuration files, and create them from scratch with the
     * RETORCH entities that they want to serialize in these files.
     */
    public ResourceSerializer() {
        resourcesToSerialize = new HashMap<>();
        mapper = new ObjectMapper();
    }

    /**
     * Support method that given an identification, elasticity cost, elasticity, type and hierarchy parent, adds to
     * the mapper
     * this resource in order to after that serialize it into a resources file
     *
     * @param hierarchyParent String with the hierarchy parent id
     * @param elasticity      Integer with the resource elasticity
     * @param elasticityCost  Double with the elasticity cost
     * @param resourceType    String with the resource type
     * @param resourceId      String with the resource ID
     */
    public void addResourceToSerialize(String resourceId, double elasticityCost, int elasticity, String resourceType,
                                       String hierarchyParent) {
        ElasticityModel elasModel = new ElasticityModel("elasModel" + resourceId);
        elasModel.setElasticityCost(elasticityCost);
        elasModel.setElasticity(elasticity);
        resourcesToSerialize.put(resourceId, new Resource(resourceId,
                new LinkedList<>(Collections.singletonList(hierarchyParent)), new LinkedList<>(), elasModel,
                getResourceTypeFromAnnotation(resourceType), new LinkedList<>(), "someimage"));
    }

    /**
     * Support method that given a String  returns the resourceType that it represents.
     *
     * @param type String with the type i.e. LOGICAL,COMPUTATIONAL or PHYSICAL
     */
    private Resource.type getResourceTypeFromAnnotation(String type) {
        try {
            return Resource.type.valueOf(type);
        } catch (IllegalArgumentException e) {
            return Resource.type.LOGICAL; // Default to LOGICAL if type is not recognized
        }
    }

    /**
     * Support method that adds a resource to the dictionary to serialize without hierarchy  parent
     *
     * @param elasticity     Integer with the resource elasticity
     * @param elasticityCost Double with the elasticity cost
     * @param resourceType   String with the resource type
     * @param resourceId     String with the resource ID
     */
    public void addResourceToSerialize(String resourceId, double elasticityCost, int elasticity, String resourceType) {
        ElasticityModel elasModel = new ElasticityModel("elasModel" + resourceId);
        elasModel.setElasticityCost(elasticityCost);
        elasModel.setElasticity(elasticity);
        resourcesToSerialize.put(resourceId, new Resource(resourceId, new LinkedList<>(), new LinkedList<>(),
                elasModel, getResourceTypeFromAnnotation(resourceType), new LinkedList<>(), "someImage"));
    }

    /**
     * Support method used to give a System name, serialize the resources stored into the mapper struct.
     *
     * @param name String with the system name
     */
    public void serializeResources(String name) throws IOException {
        Files.createDirectories(Paths.get(FOLDER_RESOURCES));
        writeSerializationToFile(FOLDER_RESOURCES + name + "SystemResources.json",
                mapper.writeValueAsString(resourcesToSerialize));
    }

    /**
     * Writes the given string to a file at the specified path.
     *
     * @param filePath the path of the file to write to
     * @param output   the string to write to the file
     */
    private void writeSerializationToFile(String filePath, String output) {
        try {
            Path path = Paths.get(filePath);
            byte[] strToBytes = output.getBytes();
            Files.write(path, strToBytes);
        } catch (IOException e) {
            logSerializer.error("The path {} doesn't exist", filePath);
        }
    }

    /**
     * Deserializes the JSON content from the given file into an object of the specified type.
     *
     * @param filePath the path of the file to deserialize
     * @param typeRef  the type of the object to deserialize to
     * @return the deserialized object
     * @throws IOException if an I/O error occurs reading from the file or a malformed or unmappable byte sequence is
     *                     read
     */
    public <T> T deserialize(String filePath, TypeReference<T> typeRef) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(filePath)));
        return mapper.readValue(json, typeRef);
    }

    /**
     * Deserializes the SystemResources.json file in the /retorchresources folder into a Map of ResourceClass objects.
     *
     * @param name the name of the SystemResources.json file to deserialize
     * @return the deserialized Map of ResourceClass objects
     * @throws IOException if an I/O error occurs reading from the file or a malformed or unmappable byte sequence is
     *                     read
     */
    public Map<String, Resource> deserializeResources(String name) throws IOException {
        return deserialize(FOLDER_RESOURCES + name + "SystemResources.json", new TypeReference<Map<String, Resource>>() {
        });
    }
}