# Spec: `giis.retorch.orchestration.scheduler`

## Purpose
Aggregates test cases into `TGroup`s and schedules them into ordered `Activity`s that maximize resource usage.

## Public API
- `RetorchAggregator`: `validateSystem` (non-empty lists, no resource without test cases, all requirements fulfilled,
  no duplicates; returns a boolean and logs the reason; it does not throw for those cases; the constructor replaces an
  invalid system by `SystemUnderTest("EmptySystem ERROR")`), `generateTGroups`, `addTestCaseToaTGroup`.
- `RetorchScheduler`: `generateActivities()` (works on the TGroups given to the scheduler) -> ordered activities (throws
  `NoTGroupsInTheSchedulerException` on empty input), `getAllResources`.
- `Scheduling` / `ScheduleStruct`: chronological moments (stage -> TJobs and available resources).

## Invariants
- TJobs of the same stage may run in parallel; a resource's access-mode concurrency and elasticity bounds are never
  exceeded inside a stage.
- Output is deterministic for the same input (the expected Jenkinsfiles in tests depend on it).

## Current behavior (known issues, to be fixed by the refactoring plan)
- `Scheduling.addTJob` silently drops a TJob whose resources are not all in the base resources (its tests never reach
  the pipeline and nothing is logged).
- An invalid system degrades into a later `NoTGroupsInTheSchedulerException`.

## Tests
`SchedulerTests`, `SchedulerUtils`.
