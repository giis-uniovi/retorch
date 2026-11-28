# Spec: `giis.retorch.profiling.usageprofile.dto.plan`

## Purpose
The execution graph of the TJobs (stages, tests, predecessors, measured figures). Data classes of the DTO family (see [dto.md](dto.md) for the rules every DTO follows: immutable, public final fields,
name ends in `DTO`, field names are the JSON contract read by the page, see [report.md](report.md)). Produced and used by
[plan.md](plan.md).

## Types
- `GraphNodeDTO(id, stage, resources, tests, predecessors, setupSec, execSec, teardownSec, peakCpu, peakMemGb, containers)`: one
  TJob; the six measured fields (incl. `startSec`) are nullable `Double`; `containers` is a never-null list of `ContainerUsageDTO`
  (`[]` by default).
- `ExecutionGraphDTO(nodes)`.

## Tests
No own tests: the classes are exercised by the tests of the packages that use them, and the JSON field names by `usageprofile.report.UsageProfileGeneratorTests`.
