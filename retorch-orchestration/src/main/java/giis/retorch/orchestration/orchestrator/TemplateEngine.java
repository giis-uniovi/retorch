/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

/**
 * The placeholder engine shared by the scripts and the Jenkinsfile. A template is text with {@code ${KEY}} tokens that
 * are replaced, line by line, by the values of a map. Two rules apply on top of the plain replacement:
 * <ul>
 * <li>A line that only holds a token whose name starts with {@code PROFILING_} or {@code OPTIONAL_} is removed when the
 * value is empty, so the disabled feature leaves no trace.</li>
 * <li>A line that only holds a token and is indented gives that indentation to every line of a multi-line value, so
 * fragments are written once with relative indentation. A token at column 0 inserts the value verbatim.</li>
 * </ul>
 */
final class TemplateEngine {

    private static final String[] OPTIONAL_PREFIXES = {"${PROFILING_", "${OPTIONAL_"};

    private TemplateEngine() {
    }

    /**
     * Reads a classpath resource as UTF-8 with {@code \n} line endings and without the last line break.
     *
     * @param stripHeader remove the leading {@code //} comment lines (the copyright notice of the Jenkinsfile
     *                    templates, which must not reach the generated pipeline)
     */
    static String load(String resourceName, boolean stripHeader) {
        try (InputStream in = TemplateEngine.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Template resource not found on classpath: " + resourceName);
            }
            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String text = scanner.hasNext() ? scanner.next() : "";
            text = text.replace("\r\n", "\n").replace("\r", "\n");
            if (stripHeader) {
                while (text.startsWith("//")) {
                    int end = text.indexOf('\n');
                    text = end < 0 ? "" : text.substring(end + 1);
                }
            }
            return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        } catch (IOException e) {
            throw new IllegalStateException("Template resource cannot be read: " + resourceName, e);
        }
    }

    /**
     * @param template text without the last line break (see {@link #load})
     * @param values   token (with the {@code ${ }}) to value
     * @return the rendered text, also without the last line break
     */
    static String render(String template, Map<String, String> values) {
        String[] lines = template.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (String line : lines) {
            String rendered = renderLine(line, values);
            if (rendered == null) {
                continue;
            }
            if (!first) {
                out.append('\n');
            }
            out.append(rendered);
            first = false;
        }
        return out.toString();
    }

    /** The rendered line (it may contain line breaks) or null when the line must disappear. */
    private static String renderLine(String line, Map<String, String> values) {
        String trimmed = line.trim();
        int indentLength = 0;
        while (indentLength < line.length() && Character.isWhitespace(line.charAt(indentLength))) {
            indentLength++;
        }
        String indentation = line.substring(0, indentLength);
        String value = values.get(trimmed);
        if (value != null && !indentation.isEmpty() && !value.isEmpty() && value.indexOf('\n') >= 0) {
            return indentLines(value, indentation);
        }
        StringBuilder sb = new StringBuilder(line);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            int start;
            while ((start = sb.indexOf(key)) != -1) {
                sb.replace(start, start + key.length(), entry.getValue());
            }
        }
        if (sb.toString().trim().isEmpty() && isOptionalToken(trimmed)) {
            return null; // a disabled optional block leaves no trace in the output
        }
        return sb.toString();
    }

    private static boolean isOptionalToken(String trimmedLine) {
        for (String prefix : OPTIONAL_PREFIXES) {
            if (trimmedLine.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String indentLines(String value, String indentation) {
        StringBuilder sb = new StringBuilder();
        String[] lines = value.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (!lines[i].isEmpty()) {
                sb.append(indentation).append(lines[i]);
            }
        }
        return sb.toString();
    }
}
