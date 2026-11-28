/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import giis.retorch.profiling.usageprofile.dto.Phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What {@link UsageCsvReader} reads from the usage file: phase windows, container rows and samples. */
final class UsageData {

    /** The phases of one TJob. */
    static final class Window {
        final String tjob;
        final double[] bounds; // setup-start, testexec-start, teardown-start, teardown-end

        Window(String tjob, double[] bounds) {
            this.tjob = tjob;
            this.bounds = bounds;
        }

        /** The label of the phase the TJob is in at second {@code t}, or null if it is not running. */
        String phaseAt(double t) {
            if (t < bounds[0] || t > bounds[3]) {
                return null;
            }
            if (t < bounds[1]) {
                return Phase.TJOB_SETUP.label();
            }
            return t < bounds[2] ? Phase.TJOB_TESTEXEC.label() : Phase.TJOB_TEARDOWN.label();
        }
    }

    /** CPU (cores), memory and writable layer (GB) of one container at one second. */
    static final class Sample {
        final double sec;
        final double cpu;
        final double memGb;
        final double diskGb;

        Sample(double sec, double cpu, double memGb, double diskGb) {
            this.sec = sec;
            this.cpu = cpu;
            this.memGb = memGb;
            this.diskGb = diskGb;
        }
    }

    /** What a {@code container} row says about a container: first-seen time, image and sizes (bytes converted to GB). */
    static final class ContainerInfo {
        final double firstSeen;
        final String image;
        final double imageGb;
        final double volumeGb;

        ContainerInfo(double firstSeen, String image, double imageGb, double volumeGb) {
            this.firstSeen = firstSeen;
            this.image = image;
            this.imageGb = imageGb;
            this.volumeGb = volumeGb;
        }
    }

    final List<Window> windows = new ArrayList<>();
    // tjob -> container -> its container row (only in files that have container rows)
    final Map<String, Map<String, ContainerInfo>> containerInfo = new HashMap<>();
    // tjob -> container -> chronologically ordered samples
    final Map<String, Map<String, List<Sample>>> samples = new HashMap<>();
    double coiSetupStart = Double.NaN;
    double coiSetupEnd = Double.NaN;
    double coiTeardownStart = Double.NaN;
    double coiTeardownEnd = Double.NaN;

    boolean hasCoi() {
        return !Double.isNaN(coiSetupStart);
    }
}
