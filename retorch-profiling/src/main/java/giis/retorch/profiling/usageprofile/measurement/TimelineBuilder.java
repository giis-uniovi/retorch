/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.measurement;

import giis.retorch.profiling.usageprofile.dto.measurement.Phase;
import giis.retorch.profiling.usageprofile.measurement.UsageData.ContainerInfo;
import giis.retorch.profiling.usageprofile.measurement.UsageData.Sample;
import giis.retorch.profiling.usageprofile.measurement.UsageData.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resamples the usage data at one second; each container keeps its last sample until a newer one arrives.
 * <p>
 * Storage rule: per TJob and second, {@code sum(writable layers) + sum(volumes) + sum(images of the DISTINCT images)} of
 * the containers that exist at that second.
 */
final class TimelineBuilder {

    private final UsageData data;
    private final Map<String, int[]> cursors = new HashMap<>(); // "tjob|container" -> index of the last used sample

    private TimelineBuilder(UsageData data) {
        this.data = data;
    }

    static Timeline build(UsageData data) {
        return new TimelineBuilder(data).build();
    }

    private Timeline build() {
        double[] range = timeRange();
        double start = range[0];
        int n = (int) Math.ceil(range[1] - start) + 1;
        List<Slice> slices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            slices.add(sliceAt(start + i));
        }
        return new Timeline(start, slices);
    }

    /** First and last second covered by the TJob windows and the COI lifecycle. */
    private double[] timeRange() {
        double start = Double.POSITIVE_INFINITY;
        double end = Double.NEGATIVE_INFINITY;
        for (Window w : data.windows) {
            start = Math.min(start, w.bounds[0]);
            end = Math.max(end, w.bounds[3]);
        }
        if (data.hasCoi()) {
            start = Math.min(start, data.coiSetupStart);
            end = Math.max(end, data.coiTeardownEnd);
        }
        return new double[]{start, end};
    }

    private Slice sliceAt(double t) {
        List<Load> loads = new ArrayList<>();
        for (Window w : data.windows) {
            Load load = loadAt(w, t);
            if (load != null) {
                loads.add(load);
            }
        }
        return new Slice(coiPhaseAt(t), loads);
    }

    private String coiPhaseAt(double t) {
        if (t >= data.coiSetupStart && t < data.coiSetupEnd) {
            return Phase.COI_SETUP.label();
        }
        return t >= data.coiTeardownStart && t <= data.coiTeardownEnd ? Phase.COI_TEARDOWN.label() : null;
    }

    /** The resources used by a TJob at second {@code t}, or null if it is not running or has no samples. */
    private Load loadAt(Window w, double t) {
        String phase = w.phaseAt(t);
        Map<String, List<Sample>> byContainer = data.samples.getOrDefault(w.tjob, Collections.emptyMap());
        Map<String, ContainerInfo> infos = data.containerInfo.getOrDefault(w.tjob, Collections.emptyMap());
        if (phase == null || (byContainer.isEmpty() && infos.isEmpty())) {
            return null;
        }
        double cpu = 0;
        double memGb = 0;
        double diskGb = 0;
        for (Map.Entry<String, List<Sample>> e : byContainer.entrySet()) {
            Sample s = latestSample(w.tjob + "|" + e.getKey(), e.getValue(), t);
            if (s != null) {
                cpu += s.cpu;
                memGb += s.memGb;
                diskGb += s.diskGb;
            }
        }
        diskGb += imagesAndVolumesGb(byContainer, infos, t);
        return new Load(w.tjob, phase, cpu, memGb, diskGb);
    }

    /**
     * Storage of the volumes and the images of the containers that already exist at {@code t} (from their first
     * appearance on; like the samples they are held until the end of the TJob). A shared image counts once.
     */
    private static double imagesAndVolumesGb(Map<String, List<Sample>> byContainer, Map<String, ContainerInfo> infos,
                                             double t) {
        double gb = 0;
        Set<String> images = new HashSet<>();
        for (Map.Entry<String, ContainerInfo> e : infos.entrySet()) {
            ContainerInfo info = e.getValue();
            List<Sample> list = byContainer.get(e.getKey());
            double first = list == null || list.isEmpty() ? info.firstSeen : Math.min(info.firstSeen, list.get(0).sec);
            if (t < first) {
                continue;
            }
            gb += info.volumeGb;
            if (images.add(info.image)) {
                gb += info.imageGb;
            }
        }
        return gb;
    }

    private Sample latestSample(String key, List<Sample> list, double t) {
        int[] cur = cursors.computeIfAbsent(key, k -> new int[]{-1});
        while (cur[0] + 1 < list.size() && list.get(cur[0] + 1).sec <= t) {
            cur[0]++;
        }
        return cur[0] >= 0 ? list.get(cur[0]) : null;
    }
}
