# Spec: `giis.retorch.profiling.usageprofile.measurement`

## Purpose
Reads the usage file of one execution (exported by `resourcemonitor.sh export`) and turns it into the measured
usage: a one-second timeline of CPU, memory and disk per TJob and phase, the metrics of each TJob and the usage of each
container. It knows nothing about prices, costs or the page.

## Public API
`ExecutionMeasurements` (facade) and the immutable result classes `Timeline`, `Slice`, `Load` and `TJobMetrics` (public
final fields, unmodifiable lists). `UsageCsvReader`, `UsageData`, `TimelineBuilder` and `TJobMetricsCalculator` are
package-private collaborators of the facade; nothing outside the package may depend on them.

## Input
The single usage file, columns
`kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes;image;image_bytes;volume_bytes` (the last three are
optional: a file with only the first 9 columns, as older runs produced, is still read and has no container information).
`UsageCsvReader.COLUMNS` is the list of the 12 columns (the contract with `resourcemonitor.sh`, see the fixtures below):
- `kind=phase`: lifecycle phase `item` (`tjob-setup`, `tjob-testexec`, `tjob-teardown`; `COI-setup` and `coi-teardown`
  under the TJob named `COI`) of a TJob, from `start` to `end`.
- `kind=sample`: CPU (% of one core), memory and disk (bytes; the size of the writable layer) of the container `item` of a
  TJob at `start`.
- `kind=container` (optional, one per container, written the first time it is seen): `item` = container, `start` = first
  seen, `image` = image name, `image_bytes` = size of the image, `volume_bytes` = total size of the volumes it mounts (0 if
  none); missing or empty values mean none.
- Times above 1e11 are epoch ms, otherwise seconds. A TJob without its three phases, or a file without TJobs, is rejected
  with `IOException`.

## Behaviour
- Reading is split behind the facade `ExecutionMeasurements` (`read(usageFile)`): `UsageCsvReader` (parsing and validation,
  produces `UsageData`), `TimelineBuilder` (resampling and storage rule) and `TJobMetricsCalculator` (TJob metrics and
  container usage). The constants `COI_SETUP` / `COI_TEARDOWN` are the labels of `dto.Phase`.
- `ExecutionMeasurements.read(usageFile).toTimeline()`: resamples at 1 second. Each container holds its last sample; a
  sample is assigned to the phase its TJob is in at that second (setup / testexec / teardown, by start boundaries).
  `Slice.coiPhase` is `COI-setup` or `coi-teardown` while the COI lifecycle is running. `Timeline.peak("cpu"|"mem"|"disk")` =
  highest simultaneous total (cores / GB / GB).
- **Storage rule** (`Load.diskGb`): per TJob and second, `sum(writable layer of its containers) + sum(volume_bytes) +
  sum(image_bytes of the DISTINCT images)`, for the containers that exist at that second (from their first appearance, the
  earlier of its `container` row and its first sample, held until the end of the TJob like the samples). A shared image
  counts once per TJob. Hence the peak disk, the provisioned volume and `diskGb` include images and volumes. Without
  `container` rows the storage is just the writable layers (old behaviour).
- `containerUsage()` -> `Map<lower case tjob, List<ContainerUsage>>` sorted by container name, only for TJobs that have
  `container` rows and samples (others are absent): peak and average (mean of the samples) CPU and memory, `layerGb` = peak
  writable layer, `imageGb`, `volumeGb` as recorded (the image counted fully even if shared), `storageGb` = their sum.
- `tjobMetrics(timeline)` -> `Map<lower case tjob, TJobMetrics>`: `startSec` (seconds from the timeline start to the start of
  the TJob set-up), setup / test execution / teardown seconds (from the phase windows: setup start -> testexec start ->
  teardown start -> teardown end) and the peak CPU (cores) and memory (GB) of that TJob over the timeline (`null` peaks if
  the TJob has no samples).

## Contract fixture
`retorch-profiling/src/test/resources/contract/usage.csv` is written/checked by both modules (the profiling module has no
dependency on the orchestration module): the header is exactly `UsageCsvReader.COLUMNS`, every row has all the columns
(empty cells for what the kind does not use), kinds `phase`, `sample` and `container`, COI phases under the TJob `COI`. A
change of a column means changing the fixture, this spec and both modules in the same change.

## Tests (`src/test/java/.../usageprofile/measurement`)
- `ExecutionMeasurementsTests`: timeline joins phases and resources (81 s, COI phases, peaks, two TJobs in test execution),
  real Jenkins run read from a single file (10 TJobs, 317 s), incomplete phases rejected, TJob metrics from phases and
  timeline, 12-column file with `container` rows: peak disk includes images and volumes and a shared image counts once per
  TJob, per-container peaks / averages / storage.
- `UsageContractFixtureTests`: the shared `usage.csv` has exactly the 12 columns, all rows complete, the three kinds; it is read
  with containers and COI phases.
- Fixtures under `src/test/java/giis/retorch/profiling/testdata/usageprofile`: `usage.csv` and `real-run.csv` (9 columns),
  `usage-containers.csv` (12 columns, container rows).
