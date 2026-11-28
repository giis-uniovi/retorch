# Spec: `giis.retorch.profiling.usageprofile.plan`

## Purpose
Reads the execution plan written by the orchestrator and merges it with the measurements into the execution graph (the
Gantt-style DAG of the page).

## Input
The execution plan `.retorch/executionplan.json`:
`{"tjobs":[{"id","stage","resources":[],"tests":["Class#method"],"predecessors":[ids]}]}` (TJob ids lower case; unknown
properties ignored). The profiling module has its own small classes for it (no dependency on the orchestration module).

## Public API
`ExecutionPlanReader` (`read(file)`, `NODE_FIELDS`, the public data class `PlanNode`) and `ExecutionGraphBuilder`
(`build`). Both are public because `main.UsageProfilerToolBox` and the report tests use them.

## Behaviour
- `ExecutionPlanReader.read(file)` -> `List<PlanNode>` (id, stage, resources, tests, predecessors).
- `ExecutionGraphBuilder.build(planNodes, metrics[, containerUsage])` -> `dto.plan.ExecutionGraphDTO`: the plan is the source of truth
  for the nodes; a TJob of the plan without measurements keeps `null` in the six measured fields; measured TJobs not in the
  plan are ignored; the `containers` of a node come from `containerUsage` (empty list if none). `metrics` and
  `containerUsage` come from `measurement.ExecutionMeasurements`.
- A missing plan file means no graph (`main.UsageProfilerToolBox`); an empty plan means an empty graph.

## Contract fixture
`retorch-profiling/src/test/resources/contract/executionplan.json`, written by `orchestration.ExecutionPlanWriter` and read by
`ExecutionPlanReader`: top level only `tjobs`; every TJob has exactly `id` (lower case), `stage` (int), `resources` (names),
`tests` (`Class#method`), `predecessors` (TJob ids) (`ExecutionPlanReader.NODE_FIELDS`). The orchestration tests must check
that its writer produces exactly these fields. A change of a field means changing the fixture, this spec and both modules in
the same change.

## Tests (`src/test/java/.../usageprofile/plan`, and one integration test in `.../usageprofile`)
- `ExecutionPlanTests`: plan is read, graph merges plan and measurements and keeps nulls for unmeasured TJobs, an old file
  without `container` rows gives nodes with no containers. Fixture `testdata/usageprofile/executionplan.json` (TJobs of
  `usage.csv` plus a gateway node).
- `PlanContractFixtureTests`: the shared plan fixture has exactly the fields the reader expects and is readable.
- `usageprofile.ContractFixturesTests` (integration, spans `plan` and `measurement`): the plan fixture and the usage fixture
  describe the same TJobs.
