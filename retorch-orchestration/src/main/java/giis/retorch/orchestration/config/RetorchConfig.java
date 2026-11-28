/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Typed, read-only view of {@code retorchCI.properties}. Required properties fail with a message that names the
 * property and the file, instead of a {@code NullPointerException} later on.
 */
public class RetorchConfig {

    /** Property that enables the resource measurement and the usage profile. */
    public static final String PROFILING_PROPERTY = "profiling";
    /**
     * Deprecated way of overriding {@code profiling} (the CLI now passes it with {@link #withProfiling(Boolean)});
     * still read for one release so existing launchers keep working.
     */
    public static final String PROFILING_SYSTEM_PROPERTY = "retorch.profiling";

    /** Where the profiling jar is downloaded from when the project does not prepare it. */
    public static final String DEFAULT_PROFILING_JAR_URL =
            "https://github.com/giis-uniovi/retorch/releases/latest/download/retorch-profiling-cli.jar";

    private static final String TJOB_NAME_TOKEN = "$TJOB_NAME";

    private final Properties properties;
    private final String source;

    public RetorchConfig(Properties properties, String source) {
        this.properties = properties;
        this.source = source;
    }

    /**
     * Loads {@code retorchCI.properties}; the deprecated system property {@code retorch.profiling} overrides the file.
     *
     * @throws IOException with a clear message if the file is missing or unreadable
     */
    public static RetorchConfig load(RetorchPaths paths) throws IOException {
        Path file = paths.ciPropertiesFile();
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            props.load(input);
        } catch (NoSuchFileException e) {
            throw new IOException("The configuration file " + file + " does not exist", e);
        }
        RetorchConfig config = new RetorchConfig(props, file.toString());
        String legacyOverride = System.getProperty(PROFILING_SYSTEM_PROPERTY);
        return legacyOverride == null ? config : config.withProfiling(Boolean.valueOf(legacyOverride));
    }

    /** A copy with the profiling flag forced to {@code profiling}; {@code null} keeps the configured value. */
    public RetorchConfig withProfiling(Boolean profiling) {
        if (profiling == null) {
            return this;
        }
        Properties copy = new Properties();
        copy.putAll(properties);
        copy.setProperty(PROFILING_PROPERTY, String.valueOf(profiling));
        return new RetorchConfig(copy, source);
    }

    public String agentCIName() {
        return required("agentCIName");
    }

    public String sutLocation() {
        return required("sut-location");
    }

    /** The {@code app-url} property with {@code $TJOB_NAME} replaced by the (lower case) TJob name. */
    public String appUrl(String tJobName) {
        return required("app-url").replace(TJOB_NAME_TOKEN, tJobName.toLowerCase(Locale.ROOT));
    }

    /** Text the SUT home page contains once it is up (used by {@code waitforSUT.sh}). */
    public String sutWaitHtml() {
        return required("sut-wait-html");
    }

    public String testsBasePath() {
        return required("testsBasePath");
    }

    public boolean profiling() {
        return Boolean.parseBoolean(properties.getProperty(PROFILING_PROPERTY, "false").trim());
    }

    public String profilingJarUrl() {
        return properties.getProperty("profiling-jar-url", DEFAULT_PROFILING_JAR_URL);
    }

    public String azureRegion() {
        return properties.getProperty("azure-region", "westeurope");
    }

    public String awsRegion() {
        return properties.getProperty("aws-region", "eu-west-1");
    }

    private String required(String name) {
        String value = properties.getProperty(name);
        if (value == null) {
            throw new IllegalStateException("The required property '" + name + "' is missing in " + source);
        }
        return value;
    }
}
