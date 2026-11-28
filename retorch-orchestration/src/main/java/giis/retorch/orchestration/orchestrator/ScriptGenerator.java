/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.config.RetorchConfig;
import giis.retorch.orchestration.config.RetorchPaths;
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

/**
 * The {@code ScriptGenerator}  provides the methods that generate the different scripts for set-up, execute and
 * tear-down the COI and TJobs in the RETORCH orchestration generator
 */
public class ScriptGenerator {

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


    private static final Logger log = LoggerFactory.getLogger(ScriptGenerator.class);
    private final RetorchConfig config;
    private final RetorchPaths paths;

    public ScriptGenerator(RetorchConfig config, RetorchPaths paths) {
        this.config = config;
        this.paths = paths;
    }

    /**
     * This method generates all the set-up, execution and disposing scripts required to execute the {@code ExecutionPlan}
     * in the continuous integration system.
     */
    public void generateScriptsTJob() throws IOException {
        Path tjobDir = paths.tjobScriptsDir();
        Path scriptsDir = paths.scriptsDir();
        checkFolderExists(tjobDir);
        Map<String, String> mapValues = new HashMap<>();
        mapValues.put("${SUT-WAIT-HTML}", config.sutWaitHtml());
        mapValues.put("${PROFILING_JAR_URL_PLACEHOLDER}",
                config.profilingJarUrl());
        mapValues.put("${CUSTOM_USAGEPROFILE_COMMANDS}", getCustomContent("usageprofile"));
        mapValues.put("${AZURE_REGION_PLACEHOLDER}", config.azureRegion());
        mapValues.put("${AWS_REGION_PLACEHOLDER}", config.awsRegion());
        mapValues.put("${CUSTOM_SETUP_TJOB_COMMANDS}", getCustomContent("tjob-setup"));
        mapValues.put("${CUSTOM_TEARDOWN_TJOB_COMMANDS}", getCustomContent("tjob-teardown"));
        boolean profiling = config.profiling();
        mapValues.put("${PROFILING_START_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" start \"$1\"" : "");
        mapValues.put("${PROFILING_STOP_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" stop \"$1\"" : "");
        replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_SETUP_TEMPLATE, tjobDir.resolve("tjob-setup.sh"));
        replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_TESTEXECUTION_TEMPLATE, tjobDir.resolve("tjob-testexecution.sh"));
        replacePlaceholderTemplate(mapValues, INPUT_FILE_TJOB_TEARDOWN_TEMPLATE, tjobDir.resolve("tjob-teardown.sh"));
        replacePlaceholderTemplate(mapValues, INPUT_FILE_WAITER_TEMPLATE, scriptsDir.resolve("waitforSUT.sh"));
        replacePlaceholderTemplate(mapValues, INPUT_FILE_WRITE_TIME_TEMPLATE, scriptsDir.resolve("writetime.sh"));
        if (profiling) {
            replacePlaceholderTemplate(mapValues, INPUT_FILE_RESOURCE_MONITOR, scriptsDir.resolve("resourcemonitor.sh"));
            replacePlaceholderTemplate(mapValues, INPUT_FILE_USAGE_PROFILE, scriptsDir.resolve("generateusageprofile.sh"));
        }
        replacePlaceholderTemplate(mapValues, INPUT_FILE_SAVE_CONTAINER_LOGS, scriptsDir.resolve("storeContainerLogs.sh"));
        replacePlaceholderTemplate(mapValues, INPUT_FILE_LOGGING, scriptsDir.resolve("printLog.sh"));
    }

    public static void checkFolderExists(Path directoryPath) throws IOException {
        String path = directoryPath.toString();

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
        // Map the phase to the corresponding file
        String urlFile;
        switch (phase) {
            case "tjob-setup":
                urlFile = "custom-tjob-setup";
                break;
            case "tjob-teardown":
                urlFile = "custom-tjob-teardown";
                break;
            case "coi-setup":
                urlFile = "custom-coi-setup";
                break;
            case "coi-teardown":
                urlFile = "custom-coi-teardown";
                break;
            case "usageprofile":
                urlFile = "custom-usageprofile";
                break;
            default:
                urlFile = "custom.env";  // Default provides the env
                break;
        }
        Path filePath = paths.customFile(urlFile);
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
    public void replacePlaceholderTemplate(Map<String, String> values, String inputTemplate, Path outputFile)
            throws IOException {
        try (BufferedReader br = getFileFromResource(inputTemplate);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                     Files.newOutputStream(outputFile), StandardCharsets.UTF_8))) {
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
        Path coiDir = paths.coiScriptsDir();
        checkFolderExists(coiDir);
        Map<String, String> mapValues = new HashMap<>();
        mapValues.put("${CUSTOM_SETUP_COI_COMMANDS}", getCustomContent("coi-setup"));
        mapValues.put("${CUSTOM_TEARDOWN_COI_COMMANDS}", getCustomContent("coi-teardown"));
        mapValues.put("${PROFILING_EXPORT_COMMAND}", config.profiling()
                ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" export" : "");
        replacePlaceholderTemplate(mapValues, INPUT_FILE_COI_SETUP_TEMPLATE, coiDir.resolve("coi-setup.sh"));
        replacePlaceholderTemplate(mapValues, INPUT_FILE_COI_TEARDOWN_TEMPLATE, coiDir.resolve("coi-teardown.sh"));
        replacePlaceholderTemplate(mapValues, INPUT_FILE_SAVE_ALL_TJOBS_TEMPLATE, paths.scriptsDir().resolve("savetjoblifecycledata.sh"));
    }
}