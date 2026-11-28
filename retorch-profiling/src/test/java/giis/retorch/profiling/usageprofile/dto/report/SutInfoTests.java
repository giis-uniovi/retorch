/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.dto.report;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SutInfoTests {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testSutAndBranchComeFromTheJenkinsEnvironmentWithoutCredentials() {
        Map<String, String> env = new HashMap<>();
        env.put("GIT_URL", "https://user:secret-token@github.com/giis-uniovi/retorch-st-petclinic.git");
        env.put("BRANCH_NAME", "feature-x");
        SutInfoDTO sut = SutInfoDTO.detect(env, tmp.getRoot().toPath());
        assertEquals("retorch-st-petclinic", sut.name);
        assertEquals("https://github.com/giis-uniovi/retorch-st-petclinic", sut.url);
        assertEquals("feature-x", sut.branch);

        env.clear();
        env.put("GIT_URL", "git@github.com:giis-uniovi/retorch-st-fullteaching.git");
        env.put("GIT_BRANCH", "origin/main");
        sut = SutInfoDTO.detect(env, tmp.getRoot().toPath());
        assertEquals("https://github.com/giis-uniovi/retorch-st-fullteaching", sut.url);
        assertEquals("main", sut.branch);
    }

    @Test
    public void testSutAndBranchComeFromTheWorkspaceGitFilesWhenThereIsNoEnvironment() throws IOException {
        File git = tmp.newFolder("ws", ".git");
        Files.write(new File(git, "config").toPath(), ("[core]\n\tbare = false\n[remote \"origin\"]\n\turl = ssh://git@github.com:22/giis-uniovi/shop.git\n"
                + "\tfetch = +refs/heads/*:refs/remotes/origin/*\n").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(git, "HEAD").toPath(), "ref: refs/heads/dev\n".getBytes(StandardCharsets.UTF_8));
        SutInfoDTO sut = SutInfoDTO.detect(new HashMap<String, String>(), git.getParentFile().toPath());
        assertEquals("shop", sut.name);
        assertEquals("https://github.com/giis-uniovi/shop", sut.url);
        assertEquals("dev", sut.branch);
        // unknown values are empty, never null, and a local path is not turned into a link
        SutInfoDTO none = SutInfoDTO.detect(new HashMap<String, String>(), tmp.newFolder("empty").toPath());
        assertEquals("", none.name + none.url + none.branch);
        assertEquals("", new SutInfoDTO("x", "/local/path", null).branch);
    }
}
