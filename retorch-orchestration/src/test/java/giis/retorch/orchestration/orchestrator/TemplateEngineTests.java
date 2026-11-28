/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.orchestrator;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The placeholder engine shared by the scripts and the Jenkinsfile.
 */
public class TemplateEngineTests {

    private Map<String, String> values(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    public void testInlineReplacementOfEveryOccurrence() {
        assertEquals("a-1-b-1", TemplateEngine.render("a-${X}-b-${X}", values("${X}", "1")));
    }

    @Test
    public void testIndentedTokenIndentsEveryLineOfTheValue() {
        assertEquals("  begin\n    one\n    two\n\n    three\n  end",
                TemplateEngine.render("  begin\n    ${BLOCK}\n  end", values("${BLOCK}", "one\ntwo\n\nthree")));
    }

    @Test
    public void testTokenAtColumnZeroIsInsertedVerbatim() {
        assertEquals("x\n  keeps\n    its indentation\ny",
                TemplateEngine.render("x\n${BLOCK}\ny", values("${BLOCK}", "  keeps\n    its indentation")));
    }

    @Test
    public void testEmptyOptionalLinesDisappearButOtherEmptyLinesStay() {
        assertEquals("a\n\nb", TemplateEngine.render("a\n  ${PROFILING_X}\n\n${OPTIONAL_Y}\nb",
                values("${PROFILING_X}", "", "${OPTIONAL_Y}", "")));
        assertEquals("a\n\nb", TemplateEngine.render("a\n${CUSTOM}\nb", values("${CUSTOM}", "")));
    }

    @Test
    public void testUnknownTokensAreKept() {
        assertEquals("echo ${BUILD_NUMBER} 1", TemplateEngine.render("echo ${BUILD_NUMBER} ${X}", values("${X}", "1")));
    }

    @Test
    public void testLoadStripsTheHeaderAndTheLastLineBreak() {
        String text = TemplateEngine.load("generictemplates/Jenkinsfile.tmpl", true);
        assertTrue(text, text.startsWith("pipeline {\n  agent {label '${AGENT_LABEL}'}"));
        assertTrue(text, text.endsWith("} // EndPipeline"));
        assertTrue(TemplateEngine.load("generictemplates/Jenkinsfile.tmpl", false).startsWith("// Copyright"));
    }
}
