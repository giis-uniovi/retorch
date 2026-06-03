package giis.retorch.orchestration.main;

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
 *   java -jar retorch-orchestration-&lt;version&gt;-standalone.jar &lt;rootPackageNameTests&gt; &lt;systemName&gt; &lt;jenkinsFilePath&gt;
 * Arguments:
 *   rootPackageNameTests  Root package containing the annotated test classes
 *   systemName            System name matching the &lt;systemName&gt;SystemResources.json file
 *   jenkinsFilePath       Directory path where the Jenkinsfile will be written
 */
public class RetorchMain {

    private static final Logger log = LoggerFactory.getLogger(RetorchMain.class);

    public static void main(String[] args) {
        if (args.length != 3) {
            log.error("Usage: java -jar retorch-orchestration-standalone.jar <rootPackageNameTests> <systemName> <jenkinsFilePath>");
            log.error("  rootPackageNameTests : root package containing the annotated test classes");
            log.error("  systemName           : system name matching the <systemName>SystemResources.json file");
            log.error("  jenkinsFilePath      : directory path where the Jenkinsfile will be written");
            java.lang.System.exit(1);
        }

        String rootPackageNameTests = args[0];
        String systemName = args[1];
        String jenkinsFilePath = args[2];

        try {
            extendClasspathFromCwd();
            OrchestrationGenerator generator = new OrchestrationGenerator();
            generator.generateJenkinsfile(rootPackageNameTests, systemName, jenkinsFilePath);
            log.info("Jenkinsfile and scripts generated successfully in: {}", jenkinsFilePath);
        } catch (Exception e) {
            log.error("Failed to generate Jenkinsfile: {}", e.getMessage(), e);
            java.lang.System.exit(2);
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
