/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The {@code ScriptGenerator}  provides the methods that generate the different scripts for set-up, execute and
 * tear-down the COI and TJobs in the RETORCH orchestration generator
 */
public class ScriptGenerator {

    private static final String DIRECTORY_TJOBS_NAME = ".retorch/scripts/tjoblifecycles/";
    private static final String DIRECTORY_COI_NAME = ".retorch/scripts/coilifecycles/";
    private static final String DIRECTORY_TEMPLATES = "generictemplates/";

    private static final String INPUT_FILE_TJOB_SETUP_TEMPLATE = DIRECTORY_TEMPLATES + "basetjob-setup.sh";
    private static final String INPUT_FILE_TJOB_TESTEXECUTION_TEMPLATE = DIRECTORY_TEMPLATES + "basetjob" + "-testexecution.sh";
    private static final String INPUT_FILE_TJOB_TEARDOWN_TEMPLATE = DIRECTORY_TEMPLATES + "basetjob-teardown.sh";
    private static final String INPUT_FILE_WAITER_TEMPLATE = DIRECTORY_TEMPLATES + "base-waitforSUT.sh";
    private static final String INPUT_FILE_WRITE_TIME_TEMPLATE = DIRECTORY_TEMPLATES + "base-writetime.sh";
    private static final String INPUT_FILE_RESOURCE_MONITOR = DIRECTORY_TEMPLATES + "base-resourcemonitor.sh";
    private static final String INPUT_FILE_USAGE_PROFILE = DIRECTORY_TEMPLATES + "base-generateusageprofile.sh";
    private static final String INPUT_FILE_SAVE_CONTAINER_LOGS = DIRECTORY_TEMPLATES + "base-storeContainerLogs.sh";
    private static final String INPUT_FILE_LOGGING = DIRECTORY_TEMPLATES + "base-printLog.sh";
    private static final String INPUT_FILE_COI_SETUP_TEMPLATE = DIRECTORY_TEMPLATES + "basecoi-setup.sh";
    private static final String INPUT_FILE_COI_TEARDOWN_TEMPLATE = DIRECTORY_TEMPLATES + "basecoi-teardown.sh";
    private static final String INPUT_FILE_SAVE_ALL_TJOBS_TEMPLATE = DIRECTORY_TEMPLATES + "base" + "-savetjoblifecycledata.sh";

    private static final String OUTPUT_FILE_TJOB_SETUP = DIRECTORY_TJOBS_NAME + "tjob-setup.sh";
    private static final String OUTPUT_FILE_TJOB_TESTEXECUTION = DIRECTORY_TJOBS_NAME + "tjob-testexecution.sh";
    private static final String OUTPUT_FILE_TJOB_TEARDOWN = DIRECTORY_TJOBS_NAME + "tjob-teardown.sh";
    private static final String OUTPUT_FILE_WAITER = ".retorch/scripts/waitforSUT.sh";
    private static final String OUTPUT_FILE_RESOURCE_MONITOR = ".retorch/scripts/resourcemonitor.sh";
    private static final String OUTPUT_FILE_USAGE_PROFILE = ".retorch/scripts/generateusageprofile.sh";
    private static final String OUTPUT_FILE_CONTAINER_LOGS = ".retorch/scripts/storeContainerLogs.sh";
    private static final String OUTPUT_FILE_LOGGING = ".retorch/scripts/printLog.sh";
    private static final String OUTPUT_FILE_WRITE_TIME = ".retorch/scripts/writetime.sh";
    private static final String OUTPUT_FILE_COI_SETUP = DIRECTORY_COI_NAME + "coi-setup.sh";
    private static final String OUTPUT_FILE_COI_TEARDOWN = DIRECTORY_COI_NAME + "coi-teardown.sh";
    private static final String OUTPUT_FILE_SAVE_ALL_TJOBS = ".retorch/scripts/savetjoblifecycledata.sh";

    /** Where the profiling jar is downloaded from when the project does not prepare it (e.g. a released jar). */
    private static final String DEFAULT_PROFILING_JAR_URL =
            "https://github.com/giis-uniovi/retorch/releases/latest/download/retorch-profiling-cli.jar";

    private static final Logger log = LoggerFactory.getLogger(ScriptGenerator.class);
    private final Properties ciConfiguration;

    /** Property of retorchCI.properties that enables the resource measurement and the usage profile. */
    public static final String PROFILING_PROPERTY = "profiling";

    public static boolean isProfilingEnabled(Properties ciConfiguration) {
        return Boolean.parseBoolean(ciConfiguration.getProperty(PROFILING_PROPERTY, "false").trim());
    }

    public ScriptGenerator(Properties ciConfiguration) {
        this.ciConfiguration = ciConfiguration;
    }

    public ScriptGenerator() {
        this(loadProperties());
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get(".retorch/configurations/retorchCI.properties"))) {
            props.load(input);
        } catch (IOException e) {
            log.error("Not possible to load properties file due to: {} ", e.getMessage());
        }
        return props;
    }

    /**
     * This method generates all the set-up, execution and disposing scripts required to execute the {@code ExecutionPlan}
     * in the continuous integration system.
     */
    public void generateScriptsTJob() throws IOException {
        checkFolderExists(DIRECTORY_TJOBS_NAME);
        Map<String, String> mapValues = new HashMap<>();
        mapValues.put("${SUT-WAIT-HTML}", ciConfiguration.getProperty("sut-wait-html"));
        mapValues.put("${PROFILING_JAR_URL_PLACEHOLDER}",
                ciConfiguration.getProperty("profiling-jar-url", DEFAULT_PROFILING_JAR_URL));
        mapValues.put("${CUSTOM_USAGEPROFILE_COMMANDS}", getCustomContent("usageprofile"));
        mapValues.put("${AZURE_REGION_PLACEHOLDER}", ciConfiguration.getProperty("azure-region", "westeurope"));
        mapValues.put("${AWS_REGION_PLACEHOLDER}", ciConfiguration.getProperty("aws-region", "eu-west-1"));
        mapValues.put("${CUSTOM_SETUP_TJOB_COMMANDS}", getCustomContent("tjob-setup"));
        mapValues.put("${CUSTOM_TEARDOWN_TJOB_COMMANDS}", getCustomContent("tjob-teardown"));
        boolean profiling = isProfilingEnabled(ciConfiguration);
        mapValues.put("${PROFILING_START_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" start \"$1\"" : "");
        mapValues.put("${PROFILING_STOP_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" stop \"$1\"" : "");
        replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_SETUP_TEMPLATE, OUTPUT_FILE_TJOB_SETUP);
        replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_TESTEXECUTION_TEMPLATE, OUTPUT_FILE_TJOB_TESTEXECUTION);
        replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_TEARDOWN_TEMPLATE, OUTPUT_FILE_TJOB_TEARDOWN);
        replacePlaceholderTemplate(mapValues, INPUT_FILE_WAITER_TEMPLATE, OUTPUT_FILE_WAITER);
        replacePlaceholderTemplate(mapValues, INPUT_FILE_WRITE_TIME_TEMPLATE, OUTPUT_FILE_WRITE_TIME);
        if (profiling) {
            replacePlaceholderTemplate(mapValues, INPUT_FILE_RESOURCE_MONITOR, OUTPUT_FILE_RESOURCE_MONITOR);
            replacePlaceholderTemplate(mapValues, INPUT_FILE_USAGE_PROFILE, OUTPUT_FILE_USAGE_PROFILE);
        }
        replacePlaceholderTemplate(mapValues, INPUT_FILE_SAVE_CONTAINER_LOGS, OUTPUT_FILE_CONTAINER_LOGS);
        replacePlaceholderTemplate(mapValues, INPUT_FILE_LOGGING, OUTPUT_FILE_LOGGING);
    }

    public static void checkFolderExists(String path) throws IOException {
        Path directoryPath = Paths.get(path);

        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
            log.debug("Directory created successfully.");
        } else {
            log.debug("The directory {} already exists.", path);
        }
    }

    /**
     * Returns the custom content from a file  located in .retorch/customscriptscode/ based on the given phase.
     *
     * @param phase contains the phase to provide the custom file
     * @return the custom content, or an empty string if the file does not exist
     * @throws IOException if an I/O error occurs
     */
    private String getCustomContent(String phase) throws IOException {
        // Map the phase to the corresponding file path
        String urlFile;
        switch (phase) {
            case "tjob-setup":
                urlFile = ".retorch/customscriptscode/custom-tjob-setup";
                break;
            case "tjob-teardown":
                urlFile = ".retorch/customscriptscode/custom-tjob-teardown";
                break;
            case "coi-setup":
                urlFile = ".retorch/customscriptscode/custom-coi-setup";
                break;
            case "coi-teardown":
                urlFile = ".retorch/customscriptscode/custom-coi-teardown";
                break;
            case "usageprofile":
                urlFile = ".retorch/customscriptscode/custom-usageprofile";
                break;
            default:
                urlFile = ".retorch/customscriptscode/custom.env";  // Default provides the env
                break;
        }
        Path filePath = Paths.get(urlFile);
        if (Files.exists(filePath)) {
            return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
        }

        return "";
    }

    /**
     * Replaces placeholders in the template with values from the configurations map and writes the result to an output file.
     *
     * @param inputTemplate String with the path of the input file
     * @param values        the map with the different configuration values
     * @param outputFile    String with the location of the output file
     */
    public void replacePlaceholderTemplate(Map<String, String> values, String inputTemplate, String outputFile)
            throws IOException {
        try (BufferedReader br = getFileFromResource(inputTemplate);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                     Files.newOutputStream(Paths.get(outputFile)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                StringBuilder sb = getStringBuilder(values, line);
                if (line.trim().startsWith("${PROFILING_") && sb.length() == 0) {
                    continue; // a profiling command that is disabled leaves no trace in the script
                }
                bw.write(sb.toString());
                bw.write("\n");
            }
            log.info("Files successfully created.");
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
        if (in == null) {
            throw new IllegalStateException("Template resource not found on classpath: " + fileName);
        }
        return new BufferedReader(new InputStreamReader(in));
    }

    public void generateScriptsCOI() throws IOException {
        checkFolderExists(DIRECTORY_COI_NAME);
        Map<String, String> mapValues = new HashMap<>();
        mapValues.put("${CUSTOM_SETUP_COI_COMMANDS}", getCustomContent("coi-setup"));
        mapValues.put("${CUSTOM_TEARDOWN_COI_COMMANDS}", getCustomContent("coi-teardown"));
        mapValues.put("${PROFILING_EXPORT_COMMAND}", isProfilingEnabled(ciConfiguration)
                ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" export" : "");
        replacePlaceholderTemplate(mapValues, INPUT_FILE_COI_SETUP_TEMPLATE, OUTPUT_FILE_COI_SETUP);
        replacePlaceholderTemplate(mapValues, INPUT_FILE_COI_TEARDOWN_TEMPLATE, OUTPUT_FILE_COI_TEARDOWN);
        replacePlaceholderTemplate(mapValues, INPUT_FILE_SAVE_ALL_TJOBS_TEMPLATE, OUTPUT_FILE_SAVE_ALL_TJOBS);
    }
}