# Spec: `giis.retorch.profiling.utils`

## Purpose
Constants shared by the profiling module.

## Public API
- `CsvConstants`: the `tjobname` column name and the `;` delimiter. They must match what `resourcemonitor.sh export` writes
  (see `orchestration/orchestrator.md`). The lifecycle phase labels (`COI-setup`, `tjob-setup`, `tjob-testexec`, `tjob-teardown`,
  `coi-teardown`) are no longer here: they are the `Phase` enum of `profiling/dto-measurement.md`.
