/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Which system under test (SUT) and branch an execution belongs to, shown in the header of the usage profile. Taken
 * from the Jenkins environment ({@code GIT_URL}, {@code BRANCH_NAME}/{@code GIT_BRANCH}) or, when missing, from the git
 * metadata of the workspace. Every value is an empty string when unknown. The URL is always a plain {@code https://}
 * address without credentials (so it is safe to embed in the page); the field names are the JSON contract.
 */
public final class SutInfoDTO {
    private static final String REMOTE_ORIGIN = "[remote \"origin\"]";
    private static final String HEAD_PREFIX = "ref: refs/heads/";
    private static final String HTTPS = "https://";
    private static final String[] SCHEMES = {"ssh://", "git://", "http://", HTTPS};

    public final String name;
    public final String url;
    public final String branch;

    public SutInfoDTO(String name, String url, String branch) {
        this.name = name == null ? "" : name;
        this.url = url == null ? "" : url;
        this.branch = branch == null ? "" : branch;
    }

    public static SutInfoDTO none() {
        return new SutInfoDTO("", "", "");
    }

    /** From the process environment and the workspace (the {@code WORKSPACE} variable or the current directory). */
    public static SutInfoDTO detect() {
        Map<String, String> env = java.lang.System.getenv();
        String workspace = env.get("WORKSPACE");
        return detect(env, Paths.get(workspace == null || workspace.isEmpty() ? "." : workspace));
    }

    public static SutInfoDTO detect(Map<String, String> env, Path workspace) {
        String url = normalizeUrl(first(env.get("GIT_URL"), remoteUrl(workspace.resolve(".git").resolve("config"))));
        String branch = first(env.get("BRANCH_NAME"), stripRemote(env.get("GIT_BRANCH")), headBranch(workspace.resolve(".git").resolve("HEAD")));
        return new SutInfoDTO(nameOf(url), url, branch);
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String stripRemote(String branch) {
        return branch != null && branch.startsWith("origin/") ? branch.substring("origin/".length()) : branch;
    }

    /** {@code git@host:org/repo.git}, {@code ssh://git@host/org/repo.git} and {@code https://user:token@host/org/repo.git} -> {@code https://host/org/repo}. */
    static String normalizeUrl(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String u = raw.trim();
        if (isScpLike(u)) {
            u = HTTPS + u.substring(u.indexOf('@') + 1).replaceFirst(":", "/");
        }
        u = withHttps(u);
        if (u == null) {
            return "";
        }
        String rest = u.substring(HTTPS.length());
        int slash = rest.indexOf('/');
        String host = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "" : rest.substring(slash);
        host = stripPort(host.substring(host.lastIndexOf('@') + 1));
        return HTTPS + host + stripGitSuffix(path);
    }

    /** {@code user@host:path}: word characters, dots and dashes around the {@code @}, then a colon and something else. */
    private static boolean isScpLike(String u) {
        int at = u.indexOf('@');
        int colon = at < 0 ? -1 : u.indexOf(':', at + 1);
        return at > 0 && colon > at + 1 && colon < u.length() - 1
                && isWordDotDash(u, 0, at) && isWordDotDash(u, at + 1, colon);
    }

    private static boolean isWordDotDash(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            boolean word = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
            if (!word && c != '.' && c != '-') {
                return false;
            }
        }
        return true;
    }

    /** The URL with its scheme (ssh, git, http or https) replaced by https, or null if it has another scheme or none. */
    private static String withHttps(String u) {
        for (String scheme : SCHEMES) {
            if (u.startsWith(scheme)) {
                return HTTPS + u.substring(scheme.length());
            }
        }
        return null;
    }

    /** Removes a trailing {@code :digits}. */
    private static String stripPort(String host) {
        int colon = host.lastIndexOf(':');
        if (colon < 0 || colon == host.length() - 1) {
            return host;
        }
        for (int i = colon + 1; i < host.length(); i++) {
            if (host.charAt(i) < '0' || host.charAt(i) > '9') {
                return host;
            }
        }
        return host.substring(0, colon);
    }

    /** Removes the trailing slashes and then a {@code .git} suffix. */
    private static String stripGitSuffix(String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        String p = path.substring(0, end);
        return p.endsWith(".git") ? p.substring(0, p.length() - ".git".length()) : p;
    }

    private static String nameOf(String url) {
        return url.isEmpty() ? "" : url.substring(url.lastIndexOf('/') + 1);
    }

    private static String remoteUrl(Path gitConfig) {
        List<String> lines = readLines(gitConfig);
        boolean inOrigin = false;
        for (String line : lines) {
            String l = line.trim();
            if (l.startsWith("[")) {
                inOrigin = l.equals(REMOTE_ORIGIN);
            } else if (inOrigin && l.startsWith("url")) {
                return l.substring(l.indexOf('=') + 1).trim();
            }
        }
        return "";
    }

    private static String headBranch(Path head) {
        for (String line : readLines(head)) {
            if (line.startsWith(HEAD_PREFIX)) {
                return line.substring(HEAD_PREFIX.length()).trim();
            }
        }
        return "";
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : java.util.Collections.<String>emptyList();
        } catch (IOException e) {
            return java.util.Collections.emptyList();
        }
    }
}
