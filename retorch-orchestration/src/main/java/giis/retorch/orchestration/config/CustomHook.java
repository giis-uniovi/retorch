/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.config;

/**
 * The optional user hooks, files of {@code .retorch/customscriptscode}. Their content is inserted in the generated
 * scripts, in the Jenkinsfile or in the TJob environment files (see {@code orchestration/orchestrator.md}).
 */
public enum CustomHook {
    COI_SETUP("custom-coi-setup"),
    COI_TEARDOWN("custom-coi-teardown"),
    TJOB_SETUP("custom-tjob-setup"),
    TJOB_TEARDOWN("custom-tjob-teardown"),
    USAGE_PROFILE("custom-usageprofile"),
    JENKINS_POST("custom-jenkins-post"),
    ENV("custom.env");

    private final String fileName;

    CustomHook(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }
}
