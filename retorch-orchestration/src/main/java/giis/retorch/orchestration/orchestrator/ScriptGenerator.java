package giis.retorch.orchestration.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ScriptGenerator {

    private static final String DIRECTORY_TJOBS_NAME = "retorchfiles/scripts/tjoblifecycles/";
    private static final String DIRECTORY_COI_NAME = "retorchfiles/scripts/coilifecycles/";
    private static final String DIRECTORY_TEMPLATES = "generictemplates/";

    private static final String INPUT_FILE_TJOB_SETUP_TEMPLATE = DIRECTORY_TEMPLATES + "basetjob-setup.sh";
    private static final String INPUT_FILE_TJOB_TESTEXECUTION_TEMPLATE = DIRECTORY_TEMPLATES + "basetjob" + "-testexecution.sh";
    private static final String INPUT_FILE_TJOB_TEARDOWN_TEMPLATE = DIRECTORY_TEMPLATES + "basetjob-teardown.sh";
    private static final String INPUT_FILE_WAITER_TEMPLATE = DIRECTORY_TEMPLATES + "base-waitforSUT.sh";
    private static final String INPUT_FILE_WRITE_TIME_TEMPLATE = DIRECTORY_TEMPLATES + "base-writetime.sh";
    private static final String INPUT_FILE_SAVE_CONTAINER_LOGS = DIRECTORY_TEMPLATES + "base-storeContainerLogs.sh";
    private static final String INPUT_FILE_LOGGING = DIRECTORY_TEMPLATES + "base-printLog.sh";
    private static final String INPUT_FILE_COI_SETUP_TEMPLATE = DIRECTORY_TEMPLATES + "basecoi-setup.sh";
    private static final String INPUT_FILE_COI_TEARDOWN_TEMPLATE = DIRECTORY_TEMPLATES + "basecoi-teardown.sh";
    private static final String INPUT_FILE_SAVE_ALL_TJOBS_TEMPLATE = DIRECTORY_TEMPLATES + "base" + "-savetjoblifecycledata.sh";

    private static final String OUTPUT_FILE_TJOB_SETUP = DIRECTORY_TJOBS_NAME + "tjob-setup.sh";
    private static final String OUTPUT_FILE_TJOB_TESTEXECUTION = DIRECTORY_TJOBS_NAME + "tjob-testexecution.sh";
    private static final String OUTPUT_FILE_TJOB_TEARDOWN = DIRECTORY_TJOBS_NAME + "tjob-teardown.sh";
    private static final String OUTPUT_FILE_WAITER = "retorchfiles/scripts/waitforSUT.sh";
    private static final String OUTPUT_FILE_CONTAINER_LOGS = "retorchfiles/scripts/storeContainerLogs.sh";
    private static final String OUTPUT_FILE_LOGGING = "retorchfiles/scripts/printLog.sh";
    private static final String OUTPUT_FILE_WRITE_TIME = "retorchfiles/scripts/writetime.sh";
    private static final String OUTPUT_FILE_COI_SETUP = DIRECTORY_COI_NAME + "coi-setup.sh";
    private static final String OUTPUT_FILE_COI_TEARDOWN = DIRECTORY_COI_NAME + "coi-teardown.sh";
    private static final String OUTPUT_FILE_SAVE_ALL_TJOBS = "retorchfiles/scripts/savetjoblifecycledata.sh";

    private static final Logger log = LoggerFactory.getLogger(ScriptGenerator.class);
    private final Properties ciConfiguration;

    public ScriptGenerator() {
        ciConfiguration = new Properties();
        try (InputStream input = new FileInputStream("retorchfiles/configurations/retorchCI.properties")) {
            ciConfiguration.load(input);
        } catch (IOException e) {
            log.error("Not possible to load properties file due to: {} ", e.getMessage());
        }
    }

    /**
     * Generates scripts for TJob.
     */
    public void generateScriptsTJob() {
        try {
            checkFolderExists(DIRECTORY_TJOBS_NAME);
            Map<String, String> mapValues = new HashMap<>();
            mapValues.put("${PORT_FRONTEND}", ciConfiguration.getProperty("docker-frontend-port"));
            mapValues.put("${FRONTEND_DOCKER_ID}", ciConfiguration.getProperty("docker-frontend-name"));
            mapValues.put("${HTML_DOM}", ciConfiguration.getProperty("sut-wait-html"));
            mapValues.put("${CUSTOM_SETUP_COMMANDS}", getCustomContent("tjob-setup"));
            mapValues.put("${CUSTOM_TEARDOWN_COMMANDS}", getCustomContent("tjob-teardown"));

            replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_SETUP_TEMPLATE, OUTPUT_FILE_TJOB_SETUP);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_TESTEXECUTION_TEMPLATE,
                    OUTPUT_FILE_TJOB_TESTEXECUTION);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_TEARDOWN_TEMPLATE, OUTPUT_FILE_TJOB_TEARDOWN);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_WAITER_TEMPLATE, OUTPUT_FILE_WAITER);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_WRITE_TIME_TEMPLATE, OUTPUT_FILE_WRITE_TIME);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_SAVE_CONTAINER_LOGS, OUTPUT_FILE_CONTAINER_LOGS);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_LOGGING, OUTPUT_FILE_LOGGING);
        } catch (IOException e) {
            log.error("Error while generating TJOBs scripts: {}", e.getMessage());
        }
    }

    public static void checkFolderExists(String path) throws IOException {
        Path directoryPath = Paths.get(path);

        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
            log.debug("Directory created successfully.");
        } else {
            log.error("The directory already exists.");
        }
    }

    /**
     * Returns the custom content from a file based on the given phase.
     * @param phase the phase
     * @return the custom content, or an empty string if the file does not exist
     * @throws IOException if an I/O error occurs
     */
    private String getCustomContent(String phase) throws IOException {
        // Map the phase to the corresponding file path
        String urlFile;
        switch (phase) {
            case "tjob-setup":
                urlFile = "retorchfiles/customscriptscode/custom-tjob-setup";
                break;
            case "tjob-teardown":
                urlFile = "retorchfiles/customscriptscode/custom-tjob-teardown";
                break;
            default:
                urlFile = "retorchfiles/customscriptscode/custom.env";  // Default provides the env
                break;
        }
        Path filePath = Paths.get(urlFile);
        if (Files.exists(filePath)) {
            return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        }

        return "";
    }

    /**
     * Replaces placeholders in the template with values from the given map and writes the result to an output file.
     * @param inputTemplate the template file
     * @param values        the map of placeholder values
     * @param outputFile    the output file
     */
    public void replacePlaceholderTemplate(Map<String, String> values, String inputTemplate, String outputFile) {
        try (BufferedReader br = getFileFromResource(inputTemplate); BufferedWriter bw =
                new BufferedWriter(new FileWriter(outputFile))) {
            System.setProperty("line.separator", "\n");
            String line;
            while ((line = br.readLine()) != null) {
                StringBuilder sb = getStringBuilder(values, line);
                bw.write(sb.toString());
                bw.newLine();
            }
            log.info("Files successfully created.");
        } catch (IOException e) {
            log.error("Error while writing to file: {}", e.getMessage());
        }
    }

    private static StringBuilder getStringBuilder(Map<String, String> values, String line) {
        StringBuilder sb = new StringBuilder(line);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            // Check if the line contains the key before replacing to avoid unnecessary replacements
            String key = entry.getKey();
            if (sb.indexOf(key) != -1) {
                int start;
                while ((start = sb.indexOf(key)) != -1) {
                    sb.replace(start, start + key.length(), entry.getValue());
                }
            }
        }
        return sb;
    }

    private BufferedReader getFileFromResource(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream in = classLoader.getResourceAsStream(fileName);
        assert in != null;
        return new BufferedReader(new InputStreamReader(in));
    }

    /**
     * Generates scripts for COI.
     */
    public void generateScriptsCOI() {
        try {
            checkFolderExists(DIRECTORY_COI_NAME);
            Map<String, String> mapValues = new HashMap<>();
            replacePlaceholderTemplate(mapValues, INPUT_FILE_COI_SETUP_TEMPLATE, OUTPUT_FILE_COI_SETUP);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_COI_TEARDOWN_TEMPLATE, OUTPUT_FILE_COI_TEARDOWN);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_SAVE_ALL_TJOBS_TEMPLATE, OUTPUT_FILE_SAVE_ALL_TJOBS);
        } catch (IOException e) {
            log.error("Error while generating COI scripts: {}", e.getMessage());
        }
    }
}