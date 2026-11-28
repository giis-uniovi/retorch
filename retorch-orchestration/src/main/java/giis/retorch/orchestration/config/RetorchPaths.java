/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Every location RETORCH reads or writes, relative to a root directory (by default the working directory). Having a
 * single object that knows the {@code .retorch} layout lets the generator run in any folder (e.g. a temporary one in
 * the tests) instead of depending on the working directory.
 */
public class RetorchPaths {

    public static final String RETORCH_DIR = ".retorch";
    public static final String CI_PROPERTIES_FILE = "retorchCI.properties";
    public static final String JENKINSFILE = "Jenkinsfile";

    private final Path root;

    /** Paths relative to the working directory. */
    public RetorchPaths() {
        this(Paths.get(""));
    }

    public RetorchPaths(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path retorchDir() {
        return root.resolve(RETORCH_DIR);
    }

    /** Folder with the resources JSON files and {@code retorchCI.properties}. */
    public Path configurationsDir() {
        return retorchDir().resolve("configurations");
    }

    public Path ciPropertiesFile() {
        return configurationsDir().resolve(CI_PROPERTIES_FILE);
    }

    /** {@code <systemName>SystemResources.json}. */
    public Path resourcesFile(String systemName) {
        return configurationsDir().resolve(systemName + "SystemResources.json");
    }

    public Path scriptsDir() {
        return retorchDir().resolve("scripts");
    }

    public Path tjobScriptsDir() {
        return scriptsDir().resolve("tjoblifecycles");
    }

    public Path coiScriptsDir() {
        return scriptsDir().resolve("coilifecycles");
    }

    /** Folder with the docker-compose {@code .env} file of every TJob. */
    public Path envFilesDir() {
        return retorchDir().resolve("envfiles");
    }

    /** Folder of the optional user hooks. */
    public Path customScriptsDir() {
        return retorchDir().resolve("customscriptscode");
    }

    /** File of a user hook inside {@link #customScriptsDir()}. */
    public Path customFile(CustomHook hook) {
        return customScriptsDir().resolve(hook.fileName());
    }

    /** The execution graph written for the usage profile. */
    public Path executionPlanFile() {
        return retorchDir().resolve("executionplan.json");
    }
}
