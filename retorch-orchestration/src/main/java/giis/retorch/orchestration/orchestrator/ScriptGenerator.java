/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.config.CustomHook;
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
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code ScriptGenerator}  provides the methods that generate the different scripts for set-up, execute and
 * tear-down the COI and TJobs in the RETORCH orchestration generator
 */
public class ScriptGenerator {

    private static final String DIRECTORY_TEMPLATES = "generictemplates/";

    /** Folder (under {@code .retorch/scripts}) a script is written to. */
    private enum Location { SCRIPTS, TJOB_LIFECYCLES, COI_LIFECYCLES }

    /** Which of the two generation steps writes a script. */
    private enum Phase { TJOB, COI }

    /**
     * Every template with the script it produces: the single table of the generator. {@code profilingOnly} scripts are
     * only generated when profiling is enabled.
     */
    private enum Template {
        TJOB_SETUP("basetjob-setup.sh", Location.TJOB_LIFECYCLES, "tjob-setup.sh", Phase.TJOB, false),
        TJOB_TEST_EXECUTION("basetjob-testexecution.sh", Location.TJOB_LIFECYCLES, "tjob-testexecution.sh", Phase.TJOB, false),
        TJOB_TEARDOWN("basetjob-teardown.sh", Location.TJOB_LIFECYCLES, "tjob-teardown.sh", Phase.TJOB, false),
        WAIT_FOR_SUT("base-waitforSUT.sh", Location.SCRIPTS, "waitforSUT.sh", Phase.TJOB, false),
        WRITE_TIME("base-writetime.sh", Location.SCRIPTS, "writetime.sh", Phase.TJOB, false),
        RESOURCE_MONITOR("base-resourcemonitor.sh", Location.SCRIPTS, "resourcemonitor.sh", Phase.TJOB, true),
        USAGE_PROFILE("base-generateusageprofile.sh", Location.SCRIPTS, "generateusageprofile.sh", Phase.TJOB, true),
        STORE_CONTAINER_LOGS("base-storeContainerLogs.sh", Location.SCRIPTS, "storeContainerLogs.sh", Phase.TJOB, false),
        PRINT_LOG("base-printLog.sh", Location.SCRIPTS, "printLog.sh", Phase.TJOB, false),
        COI_SETUP("basecoi-setup.sh", Location.COI_LIFECYCLES, "coi-setup.sh", Phase.COI, false),
        COI_TEARDOWN("basecoi-teardown.sh", Location.COI_LIFECYCLES, "coi-teardown.sh", Phase.COI, false),
        SAVE_TJOB_LIFECYCLE_DATA("base-savetjoblifecycledata.sh", Location.SCRIPTS, "savetjoblifecycledata.sh", Phase.COI, false);

        private final String templateFile;
        private final Location location;
        private final String outputFile;
        private final Phase phase;
        private final boolean profilingOnly;

        Template(String templateFile, Location location, String outputFile, Phase phase, boolean profilingOnly) {
            this.templateFile = DIRECTORY_TEMPLATES + templateFile;
            this.location = location;
            this.outputFile = outputFile;
            this.phase = phase;
            this.profilingOnly = profilingOnly;
        }
    }

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
        checkFolderExists(paths.tjobScriptsDir());
        Map<String, String> mapValues = new HashMap<>();
        mapValues.put("${SUT-WAIT-HTML}", config.sutWaitHtml());
        mapValues.put("${PROFILING_JAR_URL_PLACEHOLDER}", config.profilingJarUrl());
        mapValues.put("${CUSTOM_USAGEPROFILE_COMMANDS}", getCustomContent(CustomHook.USAGE_PROFILE));
        mapValues.put("${AZURE_REGION_PLACEHOLDER}", config.azureRegion());
        mapValues.put("${AWS_REGION_PLACEHOLDER}", config.awsRegion());
        mapValues.put("${CUSTOM_SETUP_TJOB_COMMANDS}", getCustomContent(CustomHook.TJOB_SETUP));
        mapValues.put("${CUSTOM_TEARDOWN_TJOB_COMMANDS}", getCustomContent(CustomHook.TJOB_TEARDOWN));
        boolean profiling = config.profiling();
        mapValues.put("${PROFILING_START_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" start \"$1\"" : "");
        mapValues.put("${PROFILING_STOP_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" stop \"$1\"" : "");
        generate(Phase.TJOB, mapValues);
    }

    public void generateScriptsCOI() throws IOException {
        checkFolderExists(paths.coiScriptsDir());
        Map<String, String> mapValues = new HashMap<>();
        mapValues.put("${CUSTOM_SETUP_COI_COMMANDS}", getCustomContent(CustomHook.COI_SETUP));
        mapValues.put("${CUSTOM_TEARDOWN_COI_COMMANDS}", getCustomContent(CustomHook.COI_TEARDOWN));
        mapValues.put("${PROFILING_EXPORT_COMMAND}", config.profiling()
                ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" export" : "");
        generate(Phase.COI, mapValues);
    }

    private void generate(Phase phase, Map<String, String> mapValues) throws IOException {
        for (Template template : Template.values()) {
            if (template.phase == phase && (!template.profilingOnly || config.profiling())) {
                replacePlaceholderTemplate(mapValues, template.templateFile, outputOf(template));
            }
        }
    }

    private Path outputOf(Template template) {
        switch (template.location) {
            case TJOB_LIFECYCLES:
                return paths.tjobScriptsDir().resolve(template.outputFile);
            case COI_LIFECYCLES:
                return paths.coiScriptsDir().resolve(template.outputFile);
            default:
                return paths.scriptsDir().resolve(template.outputFile);
        }
    }

    public static void checkFolderExists(Path directoryPath) throws IOException {
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
            log.debug("Directory created successfully.");
        } else {
            log.debug("The directory {} already exists.", directoryPath);
        }
    }

    /**
     * Returns the content of a user hook ({@code .retorch/customscriptscode/}).
     *
     * @param hook the hook to read
     * @return the custom content, or an empty string if the file does not exist
     * @throws IOException if an I/O error occurs
     */
    private String getCustomContent(CustomHook hook) throws IOException {
        Path filePath = paths.customFile(hook);
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
            log.debug("File {} created from {}", outputFile, inputTemplate);
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
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
