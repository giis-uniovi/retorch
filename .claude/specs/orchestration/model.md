# Spec: `giis.retorch.orchestration.model`

## Purpose
Domain entities of RETORCH. Plain data classes (Jackson-friendly).

## Entities
`Resource`, `ElasticityModel`, `Capacity` (valid names: memory, processor, slots, storage; an unknown name falls back
to the default), `AccessMode`/`AccessModeTypes`, `TestCase`, `TGroup` (test cases that can run together), `TJob`
(id, stage, test cases, resources; no timings or capacities: those are measured at run time and belong to the usage
profile; `getIdTJob()` returns the id as stored, the pipeline uses it in lower case), `Activity` (TJob + predecessors = execution order),
`ExecutionPlan` (built with `ExecutionPlan.fromActivities(name, activities)`; `getSortedActivities()` groups the
activities by stage, `getTJobs()` lists the TJobs with test cases), `ResourceInstance`, `SystemUnderTest`
(resources + test cases of the system under test; named so it does not shadow `java.lang.System`).

## Invariants
- `minimalCapacities` of a `Resource` is **optional** (absent or null in the JSON -> empty list, never null). The real `setReplaceable(null)` and `setMinimalCapacities(null)` also give an empty list, never null.
  CPU/memory/disk usage is measured while the pipeline runs (see `orchestration/orchestrator.md`); the property is only
  accepted for compatibility and nothing in the orchestration uses it (`TJob` no longer sums capacities). Existing JSON
  files that declare it keep working.
- `TJob.getListTJobLifecyclesNames()` defines the lifecycle phases of a TJob; the lifecycle CSV columns written by the
  scripts and the profiling phases (`profiling.utils.CsvConstants`) must stay aligned with it.
- Serialized property names (`@JsonProperty`) are part of the JSON configuration format: renaming them breaks users'
  `*SystemResources.json` files.

## Execution plan
- `fromActivities` fails with `EmptyInputException` when there are no activities and with `InvalidPlanException` (all
  problems listed) when a predecessor is not one of the given activities (compared by identity), when no activity is
  initial, or when activities form a cycle. Stages are explicit: an activity is in the stage after the highest stage of
  its predecessors (0 without predecessors); activities keep the order of the input list within a stage.
- Numbering is one explicit step (`PlanNumbering`), the only place where the plan changes its TJobs: it sets their stage
  and gives the TJobs with test cases the ids `TJobA, TJobB, ..., TJobZ, TJobAA...` in stage order (`GatewayActivity` for
  the ones without test cases).
- The old constructor for a list of TJobs and the setters were removed.

## Equality
- `equals`/`hashCode` are value based, consistent and symmetric: collections of test cases, resources and
  predecessors are compared as sets (order is irrelevant). `Activity` compares its TJob and the TJobs of its
  predecessors (shallow, so cyclic graphs cannot recurse). The identity `Resource` hash used to make
  `CollectionUtils.subtract` in the scheduler work by identity; it now works by value, so a TJob only depends on
  predecessors for resources that are really unavailable (e.g. elastic resources with free instances).
- Getters never mutate: `Resource` sorts `replaceable` (case insensitive) and `minimalCapacities` (by name) on a copy
  when they are set.

## Tests
`ExecutionPlanTests` (stages, numbering, invalid graphs), `ModelEqualityTests` (reflexive, symmetric, hash consistent, HashSet), `TJobsEntityTests` (including a resource without capacities), plus every test that builds entities (`SchedulerTests`, `JenkinsOrchestratorTests`).
