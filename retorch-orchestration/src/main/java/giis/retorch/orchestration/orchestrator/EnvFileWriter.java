/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import giis.retorch.orchestration.config.RetorchPaths;
import giis.retorch.orchestration.model.TJob;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the docker-compose environment file ({@code .retorch/envfiles/<tjob>.env}) of a TJob: its variables sorted
 * by name, followed by the optional {@code custom.env}.
 */
public class EnvFileWriter {

    private final RetorchPaths paths;

    public EnvFileWriter(RetorchPaths paths) {
        this.paths = paths;
    }

    /**
     * @param environment placeholders and values of the TJob (see {@code JenkinsfileRenderer.environmentOf})
     * @param tJob        TJob that owns the file
     */
    public void write(Map<String, String> environment, TJob tJob) throws IOException {
        Path envFolder = paths.envFilesDir();
        Files.createDirectories(envFolder);
        StringBuilder contentEnvFile = new StringBuilder();
        contentEnvFile.append("# Environment file for the TJob: ").append(tJob.getIdTJob().toLowerCase(Locale.ROOT));
        contentEnvFile.append("\n# like ports, docker images or the tjobname itself.\n");
        for (Map.Entry<String, String> entry : new TreeMap<>(environment).entrySet()) {
            contentEnvFile.append(toEnvVarName(entry.getKey()))
                    .append("=")
                    .append(entry.getValue())
                    .append("\n");
        }
        Path customEnv = paths.customFile("custom.env");
        if (Files.exists(customEnv)) {
            contentEnvFile.append(readUnixText(customEnv));
        }
        Path envFilePath = envFolder.resolve(tJob.getIdTJob().toLowerCase(Locale.ROOT) + ".env");
        Files.write(envFilePath, contentEnvFile.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String toEnvVarName(String key) {
        return key.replaceAll("([a-z])([A-Z])", "$1_$2")
                  .replace('-', '_')
                  .toUpperCase(Locale.ROOT);
    }

    /** Content of a text file with {@code \n} line endings. */
    static String readUnixText(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace("\r", "\n");
    }
}
