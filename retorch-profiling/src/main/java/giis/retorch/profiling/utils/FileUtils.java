package giis.retorch.profiling.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {

    private FileUtils() {
        /* This utility class should not be instantiated */
    }

    public static void ensureParentDir(String path) throws IOException {
        Path parent = Paths.get(path).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    public static void ensureDir(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
    }

    /**
     * Concatenates {@code base} and {@code child} ensuring exactly one separator between them.
     * Accepts either {@code /} or {@link File#separator} as a trailing separator on {@code base}.
     */
    public static String joinPath(String base, String child) {
        if (base == null || base.isEmpty()) {
            return child;
        }
        boolean hasSep = base.endsWith("/") || base.endsWith(File.separator);
        return hasSep ? base + child : base + "/" + child;
    }
}
