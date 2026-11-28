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
 *   java -jar retorch-orchestration-&lt;version&gt;-standalone.jar &lt;rootPackageNameTests&gt; &lt;systemName&gt; &lt;jenkinsFilePath&gt; [--profiling|--no-profiling] [--lenient] [--help]
 * Arguments:
 *   rootPackageNameTests  Root package containing the annotated test classes
 *   systemName            System name matching the &lt;systemName&gt;SystemResources.json file
 *   jenkinsFilePath       Directory path where the Jenkinsfile will be written
 *   --profiling           Optional. Measures the resource usage in the pipeline and publishes the usage profile
 *   --no-profiling        Optional. Generates the pipeline without profiling. Without any of the two flags, the
 *                         {@code profiling} property of retorchCI.properties is used (false by default)
 *   --lenient             Optional. Invalid tests, resources and TJobs are logged and left out instead of failing
 *   --help                Prints the usage and exits with 0
 * Exit codes: 0 success, 1 wrong arguments, 2 the generation failed.
 */
public class RetorchMain {

    public static final int EXIT_OK = 0;
    public static final int EXIT_USAGE = 1;
    public static final int EXIT_FAILURE = 2;

    private static final Logger log = LoggerFactory.getLogger(RetorchMain.class);

    /** The parsed command line. */
    static final class Arguments {
        String rootPackageNameTests;
        String systemName;
        String jenkinsFilePath;
        Boolean profiling;
        boolean lenient;
        boolean help;

        /**
         * @throws IllegalArgumentException with the reason if the arguments are not valid
         */
        static Arguments parse(String[] args) {
            Arguments parsed = new Arguments();
            List<String> positional = new ArrayList<>();
            for (String arg : args) {
                switch (arg) {
                    case "--help":
                        parsed.help = true;
                        break;
                    case "--lenient":
                        parsed.lenient = true;
                        break;
                    case "--profiling":
                    case "--no-profiling":
                        Boolean value = Boolean.valueOf("--profiling".equals(arg));
                        if (parsed.profiling != null && !parsed.profiling.equals(value)) {
                            throw new IllegalArgumentException("--profiling and --no-profiling cannot be used together");
                        }
                        parsed.profiling = value;
                        break;
                    default:
                        if (arg.startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option " + arg);
                        }
                        positional.add(arg);
                        break;
                }
            }
            if (parsed.help) {
                return parsed;
            }
            if (positional.size() != 3) {
                throw new IllegalArgumentException("Expected 3 arguments (rootPackageNameTests, systemName, "
                        + "jenkinsFilePath) but got " + positional.size());
            }
            parsed.rootPackageNameTests = positional.get(0);
            parsed.systemName = positional.get(1);
            parsed.jenkinsFilePath = positional.get(2);
            return parsed;
        }
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Runs the generator for the given command line arguments.
     *
     * @return the exit code: {@link #EXIT_OK}, {@link #EXIT_USAGE} or {@link #EXIT_FAILURE}
     */
    public static int run(String[] args) {
        Arguments arguments;
        try {
            arguments = Arguments.parse(args);
        } catch (IllegalArgumentException e) {
            log.error("{}", e.getMessage());
            logUsage();
            return EXIT_USAGE;
        }
        if (arguments.help) {
            logUsage();
            return EXIT_OK;
        }

        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try {
            extendClasspathFromCwd();
            new OrchestrationGenerator().withProfiling(arguments.profiling).withLenient(arguments.lenient)
                    .generateJenkinsfile(arguments.rootPackageNameTests, arguments.systemName,
                            arguments.jenkinsFilePath);
            log.info("Jenkinsfile and scripts generated successfully in: {}", arguments.jenkinsFilePath);
            return EXIT_OK;
        } catch (RetorchException e) {
            log.error("Failed to generate Jenkinsfile: {}", e.getMessage());
            return EXIT_FAILURE;
        } catch (Exception e) {
            log.error("Failed to generate Jenkinsfile: {}", e.getMessage(), e);
            return EXIT_FAILURE;
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private static void logUsage() {
        log.error("Usage: java -jar retorch-orchestration-standalone.jar <rootPackageNameTests> <systemName> <jenkinsFilePath> [--profiling|--no-profiling] [--lenient] [--help]");
        log.error("  rootPackageNameTests : root package containing the annotated test classes");
        log.error("  systemName           : system name matching the <systemName>SystemResources.json file");
        log.error("  jenkinsFilePath      : directory path where the Jenkinsfile will be written");
        log.error("  --profiling          : (optional) add the resource measurement and the usage profile");
        log.error("  --no-profiling       : (optional) do not add them, overriding retorchCI.properties");
        log.error("  --lenient            : (optional) log invalid tests, resources and TJobs and leave them out instead of failing");
        log.error("  --help               : print this usage");
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
