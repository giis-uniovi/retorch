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

import java.io.IOException;
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
        mapValues.put("${SUT-WAIT-INTERVAL}", String.valueOf(config.sutWaitInterval()));
        mapValues.put("${SUT-WAIT-LIMIT}", String.valueOf(config.sutWaitLimit()));
        mapValues.put("${PROFILING_JAR_URL_PLACEHOLDER}", config.profilingJarUrl());
        mapValues.put("${CUSTOM_USAGEPROFILE_COMMANDS}", getCustomContent(CustomHook.USAGE_PROFILE));
        mapValues.put("${AZURE_REGION_PLACEHOLDER}", config.azureRegion());
        mapValues.put("${AWS_REGION_PLACEHOLDER}", config.awsRegion());
        mapValues.put("${OPTIONAL_PRICE_PROVIDERS_COMMANDS}", priceProvidersCommands());
        mapValues.put("${CUSTOM_SETUP_TJOB_COMMANDS}", getCustomContent(CustomHook.TJOB_SETUP));
        mapValues.put("${CUSTOM_TEARDOWN_TJOB_COMMANDS}", getCustomContent(CustomHook.TJOB_TEARDOWN));
        boolean profiling = config.profiling();
        mapValues.put("${PROFILING_START_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" start \"$1\"" : "");
        mapValues.put("${PROFILING_STOP_COMMAND}", profiling ? "\"$SCRIPTS_FOLDER/resourcemonitor.sh\" stop \"$1\"" : "");
        generate(Phase.TJOB, mapValues);
    }

    /**
     * Shell lines that pass the regions of the extra price providers ({@code price-providers}) to the java call of
     * {@code generateusageprofile.sh} as {@code -Dusageprofile-regions-<id>=<regions>}, by wrapping {@code java} in a
     * function; empty (so the line disappears and the script is unchanged) when there are none.
     */
    private String priceProvidersCommands() {
        StringBuilder options = new StringBuilder();
        for (Map.Entry<String, String> provider : config.priceProviders().entrySet()) {
            options.append(" -Dusageprofile-regions-").append(provider.getKey()).append('=').append(provider.getValue());
        }
        if (options.length() == 0) {
            return "";
        }
        return "# Extra price providers (price-providers in retorchCI.properties): their regions go to the profiling tool\n"
                + "java() { command java" + options + " \"$@\"; }";
    }

    /**
     * @param firstTJobName name (lower case) of the first TJob of the plan: the images of the SUT are built with its
     *                      environment file
     */
    public void generateScriptsCOI(String firstTJobName) throws IOException {
        checkFolderExists(paths.coiScriptsDir());
        Map<String, String> mapValues = new HashMap<>();
        mapValues.put("${FIRST_TJOB_NAME}", firstTJobName);
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
     * Replaces placeholders in the template with values from the configurations map and writes the result to an output
     * file (see {@link TemplateEngine} for the rules).
     *
     * @param inputTemplate String with the path of the input file
     * @param values        the map with the different configuration values
     * @param outputFile    String with the location of the output file
     */
    public void replacePlaceholderTemplate(Map<String, String> values, String inputTemplate, Path outputFile)
            throws IOException {
        String text = TemplateEngine.render(TemplateEngine.load(inputTemplate, false), values);
        Files.write(outputFile, (text + '\n').getBytes(StandardCharsets.UTF_8));
        log.debug("File {} created from {}", outputFile, inputTemplate);
    }
}
