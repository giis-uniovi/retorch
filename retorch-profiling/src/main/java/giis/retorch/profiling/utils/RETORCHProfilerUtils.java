package giis.retorch.profiling.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import giis.retorch.profiling.model.CloudObjectInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class RETORCHProfilerUtils {

    private static final Logger logSerializer = LoggerFactory.getLogger(RETORCHProfilerUtils.class);
    private static final String FOLDER_RESOURCES = "retorchfiles/resources/";//Base path of the resource files
    final ObjectMapper mapper;

    /**
     * RETORCH Serializer provides utils to deserialize the configuration files, and create them from scratch with the
     * RETORCH entities that they want to serialize in these files.
     */
    public RETORCHProfilerUtils() {
        mapper = new ObjectMapper();
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

    public List<CloudObjectInstance> deserializeCloudObjectInstances(String systemName) throws IOException {
        logSerializer.debug("Deserializing the Cloud Object Instances placed in the file:{}{}{}",FOLDER_RESOURCES ,systemName , "CloudObjectInstances.json");
        return deserialize(FOLDER_RESOURCES + systemName + "CloudObjectInstances.json", new  TypeReference<List<CloudObjectInstance>>() {
        });
    }

}