# Spec: `giis.retorch.orchestration.scheduler`

## Purpose
Aggregates test cases into `TGroup`s and schedules them into ordered `Activity`s that maximize resource usage.

## Public API
- `RetorchAggregator`: `validateSystem` (non-empty lists, no resource without test cases, all requirements fulfilled,
  no duplicates; returns a boolean and logs every reason). The constructor **throws `NotValidSystemException` listing
  every problem** when the system is invalid; with `lenient = true` it only logs and replaces the system by
  `SystemUnderTest("EmptySystem ERROR")` (old behavior), `generateTGroups`, `addTestCaseToaTGroup`.
- `RetorchAggregator(system[, resources])`: the resources by id can be given by the caller instead of being read again.
- `RetorchScheduler`: `generateActivities()` (works on the TGroups given to the scheduler; throws `SchedulingException`) -> ordered activities (throws
  `NoTGroupsInTheSchedulerException` on empty input), `getAllResources`.
- `Scheduling` / `ScheduleStruct`: chronological moments (stage -> TJobs and available resources).

## Invariants
- TJobs of the same stage may run in parallel; a resource's access-mode concurrency and elasticity bounds are never
  exceeded inside a stage.
- Output is deterministic for the same input (the expected Jenkinsfiles in tests depend on it).

## Failing loudly
- `Scheduling.addTJob` returns `false` for a TJob whose resources are not all in the base resources;
  `RetorchScheduler.generateActivities()` then throws `SchedulingException` listing every TJob that cannot be placed
  (`new RetorchScheduler(tGroups, true)` logs a warning and leaves them out instead).

## Tests
`SchedulerTests`, `SchedulerUtils`.
