# Spec: `giis.retorch.profiling.utils`

## Purpose
Constants shared by the profiling module.

## Public API
- `CsvConstants`: the `tjobname` column name, the lifecycle phase labels (`COI-setup`, `tjob-setup`, `tjob-testexec`,
  `tjob-teardown`, `coi-teardown`) and the `;` delimiter. They must match what `resourcemonitor.sh export` writes
  (see `orchestration/orchestrator.md`).
