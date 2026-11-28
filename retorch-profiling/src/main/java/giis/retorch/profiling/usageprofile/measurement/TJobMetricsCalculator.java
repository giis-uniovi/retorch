/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import giis.retorch.profiling.usageprofile.measurement.UsageData.ContainerInfo;
import giis.retorch.profiling.usageprofile.measurement.UsageData.Sample;
import giis.retorch.profiling.usageprofile.measurement.UsageData.Window;
import giis.retorch.profiling.usageprofile.dto.ContainerUsageDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Per-TJob metrics (phase durations, peaks) and per-container usage computed from the usage data. */
final class TJobMetricsCalculator {

    private TJobMetricsCalculator() {
    }

    /**
     * Metrics of every TJob of the usage file by lower case TJob name: setup, test execution and teardown durations
     * (from the phase windows) and the highest CPU / memory the TJob used in {@code timeline}.
     */
    static Map<String, TJobMetrics> metrics(UsageData data, Timeline timeline) {
        Map<String, double[]> peaks = new HashMap<>(); // tjob represented as [cpu, mem]
        for (Slice slice : timeline.slices) {
            for (Load l : slice.loads) {
                double[] peak = peaks.computeIfAbsent(l.tjob, k -> new double[2]);
                peak[0] = Math.max(peak[0], l.cpu);
                peak[1] = Math.max(peak[1], l.memGb);
            }
        }
        Map<String, TJobMetrics> result = new HashMap<>();
        for (Window w : data.windows) {
            double[] peak = peaks.get(w.tjob);
            result.put(w.tjob.toLowerCase(Locale.ROOT),
                    new TJobMetrics(w.bounds, timeline.startSec, peak == null ? null : peak[0], peak == null ? null : peak[1]));
        }
        return result;
    }

    /**
     * Usage of every container of every TJob that has container rows and samples, by lower case TJob name, sorted by
     * container name (CPU in cores, memory and storage in GB). Other TJobs are not in the map.
     */
    static Map<String, List<ContainerUsageDTO>> containerUsage(UsageData data) {
        Map<String, List<ContainerUsageDTO>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, ContainerInfo>> e : data.containerInfo.entrySet()) {
            Map<String, List<Sample>> byContainer = data.samples.getOrDefault(e.getKey(), Collections.emptyMap());
            if (byContainer.isEmpty()) {
                continue;
            }
            Set<String> names = new TreeSet<>(e.getValue().keySet());
            names.addAll(byContainer.keySet());
            List<ContainerUsageDTO> usage = new ArrayList<>();
            for (String name : names) {
                usage.add(usageOf(name, e.getValue().get(name), byContainer.get(name)));
            }
            result.put(e.getKey().toLowerCase(Locale.ROOT), usage);
        }
        return result;
    }

    private static ContainerUsageDTO usageOf(String name, ContainerInfo info, List<Sample> list) {
        double[] stats = new double[4]; // peak cpu, avg cpu, peak mem, avg mem
        double layer = 0;
        if (list != null) {
            for (Sample s : list) {
                stats[0] = Math.max(stats[0], s.cpu);
                stats[1] += s.cpu / list.size();
                stats[2] = Math.max(stats[2], s.memGb);
                stats[3] += s.memGb / list.size();
                layer = Math.max(layer, s.diskGb);
            }
        }
        return new ContainerUsageDTO(name, info == null ? "" : info.image, stats, layer,
                info == null ? 0 : info.imageGb, info == null ? 0 : info.volumeGb);
    }
}
