# Plan: usage per container, images and volumes in the storage, per-resource analysis of a TJob

Requirement (plan.md): know how many resources (memory, processor, storage) each deployed container (resource) consumes; clicking
a TJob shows the analysis of each resource; the size of the images counts as storage used (besides the volumes); everything is
saved in the CSV and shown in the usage profile.

## Contract

### Usage file (written by `resourcemonitor.sh export`, read by the profiling module)
Header (12 columns, `;` separated):
`kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes;image;image_bytes;volume_bytes`
- `phase` rows: as before (the last six columns are empty).
- `sample` rows: `item` = container name, `disk_bytes` = size of the **writable layer** of the container at that instant; `image`,
  `image_bytes`, `volume_bytes` empty.
- **`container` rows (new), one per container of a TJob, written the first time the container is seen**: `kind=container`,
  `tjobname`, `item` = container name, `start` = instant first seen, `image` = image name, `image_bytes` = size in bytes of that
  image, `volume_bytes` = total size in bytes of the named/anonymous volumes mounted by the container (0 if none); cpu/mem/disk empty.
- Older files with only the first 9 columns (no `container` rows) must keep working: no container information, storage = writable layers.

### Storage used by a TJob at an instant
`sum(writable layer of its containers) + sum(volume_bytes of its containers) + sum(image_bytes over the DISTINCT images of its containers)`,
for the containers that exist at that instant (from their first appearance until their last sample). Images are counted once per
distinct image inside a TJob even if two containers share it. This storage feeds the existing cost model (the peak disk, the
provisioned volume, `diskGb` in the timeline), so the peak disk of the profile now includes images and volumes.

### JSON (`graph.nodes[]`) gets `containers`
`containers`: list (empty when the file has no container information or the TJob has no measurements), sorted by name:
`{ name, image, peakCpu, avgCpu, peakMemGb, avgMemGb, layerGb, imageGb, volumeGb, storageGb }`
- `peakCpu`/`avgCpu` in cores (cpu_percent / 100), peak and average over the container's samples; `peakMemGb`/`avgMemGb` likewise (GB = 1024^3);
- `layerGb` = peak writable layer, `imageGb`, `volumeGb` as recorded, `storageGb = layerGb + imageGb + volumeGb` (the image counted fully
  in each container row, even when shared; the TJob-level storage dedupes shared images as explained above);
- `image` = image name (string, may be empty).
Existing JSON properties do not change.

## Work packages
| Agent | Owns | Task |
|---|---|---|
| J3 (Java) | `retorch-profiling/src/main/java/**`, Java tests and test data, `.claude/specs/profiling/*.md` | reader, storage accounting, per-container usage, JSON `containers` |
| Lead | `retorch-orchestration/**` (scripts, tests, spec), README | `resourcemonitor.sh` changes (container rows, image and volume sizes) |
| F2 (front-end, later) | `retorch-profiling/src/main/resources/usageprofile/**` | per-container analysis in the TJob side panel |

## J3 requirements (Java 8, Sonar-clean: methods < 15 cognitive complexity, no duplicated literals, <= 7 parameters, no System.out)
1. `ExecutionMeasurements.read` accepts the 12-column file and the old 9-column file (use `isMapped`/`isSet` on the optional columns;
   missing or empty values mean "none"). New `container` rows: store name, image, image bytes, volume bytes and first-seen time per TJob.
2. Storage accounting in the timeline exactly as in the contract (a container exists from its first appearance until its last sample,
   held like the CPU/memory samples). Without `container` rows nothing changes (old behaviour).
3. Per-TJob container usage: new `dto/ContainerUsage` (immutable, public final fields as in the contract) and
   `dto/GraphNode.containers` (list, never null, `[]` by default). `ExecutionMeasurements` exposes it; `ExecutionGraphBuilder` merges it.
4. Tests (no network): a new fixture with container rows (keep the existing 9-column fixtures to prove compatibility); the peak disk
   includes images and volumes; shared image counted once per TJob; per-container peaks and averages; the JSON `containers` property and
   its field names; an old-format file still yields `containers: []`.
5. Update `.claude/specs/profiling/usageprofile.md` (inputs, storage rule, DTOs, JSON contract, tests) per the iron rule.
6. Do not touch `src/main/resources`, the orchestration module or git. You may run `mvn -q -pl retorch-profiling -am test`.
Tools: Write/Edit for file contents (long bash heredocs lose backslashes here); beware of Java string escapes written through Python.
Final report (short): what changed, final API, what you could not verify.
