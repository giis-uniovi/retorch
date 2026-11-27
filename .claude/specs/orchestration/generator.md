# Spec: `giis.retorch.orchestration.generator`

## Purpose
Facade of the orchestration pipeline: classifier -> aggregator -> scheduler -> orchestrator.

## Public API
- `OrchestrationGenerator.generateJenkinsfile(rootPackageNameTests, systemName, jenkinsFilePath)`: runs the whole
  pipeline, writes `Jenkinsfile` (into `jenkinsFilePath`, or `./Jenkinsfile` if that directory does not exist), the
  scripts under `.retorch/scripts` and the TJob env files, and returns the pipeline text.
- `OrchestrationGenerator.getExecutionPlan(rootPackageNameTests, systemName)`: the `ExecutionPlan` only (used by
  the profiling module).

## Behavior
- Profiling is not a parameter here: `JenkinsOrchestrator` decides it (property `profiling` in
  `retorchCI.properties`, overridable by the `retorch.profiling` JVM system property set by `RetorchMain`).
  See `orchestration/orchestrator.md`.
- Errors of the intermediate steps propagate as the checked exceptions of the signature.

## Tests
`OrchestrationIntegrationTests`.
