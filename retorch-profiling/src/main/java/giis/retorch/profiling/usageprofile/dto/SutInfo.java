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
public final class SutInfo {
    private static final String REMOTE_ORIGIN = "[remote \"origin\"]";
    private static final String HEAD_PREFIX = "ref: refs/heads/";

    public final String name;
    public final String url;
    public final String branch;

    public SutInfo(String name, String url, String branch) {
        this.name = name == null ? "" : name;
        this.url = url == null ? "" : url;
        this.branch = branch == null ? "" : branch;
    }

    public static SutInfo none() {
        return new SutInfo("", "", "");
    }

    /** From the process environment and the workspace (the {@code WORKSPACE} variable or the current directory). */
    public static SutInfo detect() {
        Map<String, String> env = java.lang.System.getenv();
        String workspace = env.get("WORKSPACE");
        return detect(env, Paths.get(workspace == null || workspace.isEmpty() ? "." : workspace));
    }

    public static SutInfo detect(Map<String, String> env, Path workspace) {
        String url = normalizeUrl(first(env.get("GIT_URL"), remoteUrl(workspace.resolve(".git").resolve("config"))));
        String branch = first(env.get("BRANCH_NAME"), stripRemote(env.get("GIT_BRANCH")), headBranch(workspace.resolve(".git").resolve("HEAD")));
        return new SutInfo(nameOf(url), url, branch);
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
        if (u.matches("^[\\w.-]+@[\\w.-]+:.+")) {
            u = "https://" + u.substring(u.indexOf('@') + 1).replaceFirst(":", "/");
        }
        u = u.replaceFirst("^(ssh|git|http|https)://", "https://");
        if (!u.startsWith("https://")) {
            return "";
        }
        String rest = u.substring("https://".length());
        int slash = rest.indexOf('/');
        String host = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "" : rest.substring(slash);
        host = host.substring(host.lastIndexOf('@') + 1).replaceFirst(":\\d+$", "");
        return "https://" + host + path.replaceFirst("(\\.git)?/*$", "");
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
