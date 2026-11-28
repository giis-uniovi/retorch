# Spec: `giis.retorch.profiling.usageprofile.dto`

## Purpose
The data exchanged between the cost calculation, the plan merge and the page assembly. Immutable, public final fields, no
logic besides the total. They are the only types shared by the other `usageprofile` packages, and **their field names are the
JSON contract** read by the page (see `profiling/report.md`). Jackson serialisation relies on the constructors and
`@JsonProperty` annotations: do not change them without changing the contract.

## Types
- `CostItem(object, tjob, phase, cost)`: one billed (cloud object `compute`|`storage`|`browser`, TJob, phase) combination.
- `Alternative(id, provider, kind, label, region, hourlyPrice, items)`: one VM, container or browser-service alternative; `kind`
  is `VM`, `CONTAINER` or `BROWSER_SERVICE`; `total` is computed from the items (the sum of items always equals the total).
  `reservations` (immutable list of `ReservationOption`, empty by default) is set with `withReservations(list)`, which returns a
  copy.
- `ReservationOption(termYears, payment, effectiveHourly)`: `termYears` 1 or 3, `payment` `NO_UPFRONT` | `ALL_UPFRONT` (enum,
  serialized by name), `effectiveHourly` = USD per hour really paid 24x7, running or not (hourly rate, or upfront fee divided
  by `termYears * 8760`). Factory `allUpfront(termYears, fee)`.
- `CostReport(peakCpu, peakMemGb, peakDiskGb, durationSec, alternatives, warnings)`: result of the calculation, alternatives
  sorted by total, cheapest first.
- `TimelineSeries(t, cpu, memGb, diskGb)`: total usage over time as parallel lists of equal length.
- `GraphNode(id, stage, resources, tests, predecessors, setupSec, execSec, teardownSec, peakCpu, peakMemGb, containers)`: one
  TJob; the six measured fields (incl. `startSec`) are nullable `Double`; `containers` is a never-null list of `ContainerUsage`
  (`[]` by default).
- `ContainerUsage(name, image, peakCpu, avgCpu, peakMemGb, avgMemGb, layerGb, imageGb, volumeGb, storageGb)`: usage of one
  container of a TJob (cores / GB 1024^3), `storageGb = layerGb + imageGb + volumeGb`.
- `ExecutionGraph(nodes)`.
- `SutInfo(name, url, branch)`: SUT and branch shown in the header; empty strings when unknown, never null. `SutInfo.detect()` /
  `detect(env, workspace)` read `GIT_URL` (else `remote "origin"` of `<workspace>/.git/config`) and `BRANCH_NAME` (else
  `GIT_BRANCH` without `origin/`, else `<workspace>/.git/HEAD`); the workspace is `WORKSPACE` or the current directory. The URL
  is normalised to a plain `https://host/org/repo` (no credentials, no `.git`, `git@host:` and `ssh://` converted; anything
  else, e.g. a local path, gives an empty URL); the name is its last segment.
- `Phase` (enum: `label()`, `order()`, `isCoi()`): the lifecycle phases in display order; the label is the exact string of the
  usage file and of `CostItem.phase`. Constants of the kinds (`Alternative.KIND_*`) and of the objects (`CostItem.OBJECT_*`).
- `Meta` (`phases, kinds, objects`): built from them, always `Meta.standard()`.
- `UsageProfileData(plan, pricesFetchedAt, report, timeline, graph[, sut])`: exactly what the page receives; `graph` is
  nullable, `sut` defaults to `SutInfo.none()`; `meta` is always set.

## Tests (`src/test/java/.../usageprofile/dto`)
`SutInfoTests`: SUT and branch from the Jenkins environment (credentials removed, ssh converted) and from the `.git` files of the
workspace; unknown values empty, a local path is not turned into a link. The JSON field names are checked by
`report.UsageProfileGeneratorTests`.
