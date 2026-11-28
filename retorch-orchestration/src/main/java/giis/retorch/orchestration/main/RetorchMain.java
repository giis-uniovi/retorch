/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.main;

import giis.retorch.orchestration.exception.RetorchException;
import giis.retorch.orchestration.generator.OrchestrationGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point for the RETORCH orchestration uber JAR.
 * Usage:
 *   java -jar retorch-orchestration-&lt;version&gt;-standalone.jar &lt;rootPackageNameTests&gt; &lt;systemName&gt; &lt;jenkinsFilePath&gt; [--profiling|--no-profiling] [--lenient]
 * Arguments:
 *   rootPackageNameTests  Root package containing the annotated test classes
 *   systemName            System name matching the &lt;systemName&gt;SystemResources.json file
 *   jenkinsFilePath       Directory path where the Jenkinsfile will be written
 *   --profiling           Optional. Measures the resource usage in the pipeline and publishes the usage profile
 *   --lenient             Optional. Invalid tests, resources and TJobs are logged and left out instead of failing
 *   --no-profiling        Optional. Generates the pipeline without profiling. Without any of the two flags, the
 *                         {@code profiling} property of retorchCI.properties is used (false by default)
 */
public class RetorchMain {

    private static final Logger log = LoggerFactory.getLogger(RetorchMain.class);

    public static void main(String[] args) {
        Boolean profiling = null;
        boolean lenient = false;
        boolean validArgs = args.length >= 3 && args.length <= 5;
        for (int i = 3; validArgs && i < args.length; i++) {
            if ("--profiling".equals(args[i])) {
                profiling = Boolean.TRUE;
            } else if ("--no-profiling".equals(args[i])) {
                profiling = Boolean.FALSE;
            } else if ("--lenient".equals(args[i])) {
                lenient = true;
            } else {
                validArgs = false;
            }
        }
        if (!validArgs) {
            log.error("Usage: java -jar retorch-orchestration-standalone.jar <rootPackageNameTests> <systemName> <jenkinsFilePath> [--profiling|--no-profiling] [--lenient]");
            log.error("  rootPackageNameTests : root package containing the annotated test classes");
            log.error("  systemName           : system name matching the <systemName>SystemResources.json file");
            log.error("  jenkinsFilePath      : directory path where the Jenkinsfile will be written");
            log.error("  --profiling          : (optional) add the resource measurement and the usage profile");
            log.error("  --no-profiling       : (optional) do not add them, overriding retorchCI.properties");
            log.error("  --lenient            : (optional) log invalid tests, resources and TJobs and leave them out instead of failing");
            System.exit(1);
        }

        String rootPackageNameTests = args[0];
        String systemName = args[1];
        String jenkinsFilePath = args[2];

        try {
            extendClasspathFromCwd();
            OrchestrationGenerator generator = new OrchestrationGenerator().withProfiling(profiling).withLenient(lenient);
            generator.generateJenkinsfile(rootPackageNameTests, systemName, jenkinsFilePath);
            log.info("Jenkinsfile and scripts generated successfully in: {}", jenkinsFilePath);
        } catch (RetorchException e) {
            log.error("Failed to generate Jenkinsfile: {}", e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            log.error("Failed to generate Jenkinsfile: {}", e.getMessage(), e);
            System.exit(2);
        }
    }

    /**
     * Adds the compiled classes from the current working directory to the thread context classloader so the
     * classifier can discover and load annotated test classes without requiring the user to manually set -cp.
     */
    private static void extendClasspathFromCwd() throws IOException {
        Path targetDir = Paths.get("target").toAbsolutePath();
        if (!Files.exists(targetDir)) {
            return;
        }
        List<URL> urls = new ArrayList<>();
        // Scan for compiled test-classes directories (handles non-standard Maven output paths)
        try (java.util.stream.Stream<Path> walk = Files.walk(targetDir, 3, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals("test-classes"))
                .forEach(p -> addUrl(urls, p));
        }
        // Add dependency JARs so that transitive dependencies (e.g. Selenium) are resolvable.
        // Scans up to depth 2 under target/ to handle non-standard Maven output directories.
        try (java.util.stream.Stream<Path> walk = Files.walk(targetDir, 2, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals("dependency"))
                .forEach(depDir -> {
                    try (java.util.stream.Stream<Path> jars = Files.walk(depDir, 1)) {
                        jars.filter(p -> p.toString().endsWith(".jar"))
                            .forEach(p -> addUrl(urls, p));
                    } catch (IOException e) {
                        log.warn("Could not scan dependency dir {}: {}", depDir, e.getMessage());
                    }
                });
        }
        if (!urls.isEmpty()) {
            URLClassLoader loader = new URLClassLoader(
                urls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader()
            );
            Thread.currentThread().setContextClassLoader(loader);
        }
    }

    private static void addUrl(List<URL> urls, Path path) {
        try {
            urls.add(path.toUri().toURL());
            log.debug("Added to classpath: {}", path);
        } catch (java.net.MalformedURLException e) {
            log.warn("Could not add {} to classpath: {}", path, e.getMessage());
        }
    }
}
