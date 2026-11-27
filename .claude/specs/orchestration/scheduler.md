# Spec: `giis.retorch.orchestration.scheduler`

## Purpose
Aggregates test cases into `TGroup`s and schedules them into ordered `Activity`s that maximize resource usage.

## Public API
- `RetorchAggregator`: `validateSystem` (non-empty lists, no resource without test cases, all requirements fulfilled,
  no duplicates; throws `NotValidSystemException`), `generateTGroups`, `addTestCaseToaTGroup`.
- `RetorchScheduler`: `generateActivities(List<TGroup>)` -> ordered activities (throws
  `NoTGroupsInTheSchedulerException` on empty input), `getAllResources`.
- `Scheduling` / `ScheduleStruct`: chronological moments (stage -> TJobs and available resources).

## Invariants
- TJobs of the same stage may run in parallel; a resource's access-mode concurrency and elasticity bounds are never
  exceeded inside a stage.
- Output is deterministic for the same input (the expected Jenkinsfiles in tests depend on it).

## Tests
`SchedulerTests`, `SchedulerUtils`.
