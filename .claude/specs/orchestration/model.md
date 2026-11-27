# Spec: `giis.retorch.orchestration.model`

## Purpose
Domain entities of RETORCH. Plain data classes (Jackson-friendly) with value-based `equals`/`hashCode`.

## Entities
`Resource`, `ElasticityModel`, `Capacity` (valid names: memory, processor, slots, storage; an unknown name falls back
to the default), `AccessMode`/`AccessModeTypes`, `TestCase`, `TGroup` (test cases that can run together), `TJob`
(name, stage, resources; computes its total capacities), `Activity` (TJob + predecessors = execution order),
`ExecutionPlan` (named, ordered TJobs; `getSortedActivities()` groups them by stage), `ResourceInstance`, `System`
(resources + test cases of the system under test).

## Invariants
- `minimalCapacities` of a `Resource` is **optional** (absent or null in the JSON -> empty list, never null). The real
  CPU/memory/disk usage is measured while the pipeline runs (see `orchestration/orchestrator.md`); the capacities are
  only an estimate kept in `TJob.getTotalCapacities()` (all zero when nothing is declared). Existing JSON files that
  declare it keep working.
- `TJob.getListTJobLifecyclesNames()` defines the lifecycle phases of a TJob; the lifecycle CSV columns written by the
  scripts and the profiling phases (`profiling.utils.CsvConstants`) must stay aligned with it.
- Serialized property names (`@JsonProperty`) are part of the JSON configuration format: renaming them breaks users'
  `*SystemResources.json` files.

## Tests
`TJobsEntityTests` (including a resource without capacities), plus every test that builds entities (`SchedulerTests`, `JenkinsOrchestratorTests`).
