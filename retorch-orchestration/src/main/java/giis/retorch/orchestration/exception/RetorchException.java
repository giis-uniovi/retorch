/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.orchestration.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Root of the errors RETORCH reports instead of silently degrading: the input (configuration, annotated tests,
 * resources) or the plan is invalid. When several things are wrong, the message lists all of them.
 */
public class RetorchException extends Exception {

    private static final long serialVersionUID = 4113726592019263415L;

    private final transient List<String> problems;

    public RetorchException(String message) {
        super(message);
        this.problems = Collections.singletonList(message);
    }

    /**
     * @param header   what failed
     * @param problems every individual problem found, one per line in the message
     */
    public RetorchException(String header, List<String> problems) {
        super(summary(header, problems));
        this.problems = new ArrayList<>(problems);
    }

    /** Individual problems (one element when the exception was built with a plain message). */
    public List<String> getProblems() {
        return Collections.unmodifiableList(problems);
    }

    static String summary(String header, List<String> problems) {
        StringBuilder sb = new StringBuilder(header).append(" (").append(problems.size()).append(" problem")
                .append(problems.size() == 1 ? "" : "s").append("):");
        for (String problem : problems) {
            sb.append("\n  - ").append(problem);
        }
        return sb.toString();
    }
}
