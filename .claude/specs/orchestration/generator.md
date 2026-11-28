# Spec: `giis.retorch.orchestration.generator`

## Purpose
Facade of the orchestration pipeline: classifier -> aggregator -> scheduler -> `OrchestrationWriter`.

## Public API
- `OrchestrationGenerator()` (working directory) or `OrchestrationGenerator(RetorchPaths)` (any folder);
  `withProfiling(Boolean)` forces profiling on/off (`null` keeps `retorchCI.properties`); `withLenient(boolean)`.
- `OrchestrationGenerator.generateJenkinsfile(rootPackageNameTests, systemName, jenkinsFilePath)`: runs the whole
  pipeline, writes `Jenkinsfile` (into `jenkinsFilePath`, or `./Jenkinsfile` if that directory does not exist), the
  scripts under `.retorch/scripts` and the TJob env files, and returns the pipeline text.
- `OrchestrationGenerator.getExecutionPlan(rootPackageNameTests, systemName)`: the `ExecutionPlan` only (used by
  the profiling module).

## Behavior
- Profiling is decided by `RetorchConfig` (property `profiling` in `retorchCI.properties`, overridden by
  `withProfiling`, which `RetorchMain` calls for `--profiling/--no-profiling`). See `orchestration/orchestrator.md`.
- The configuration is loaded once and the resources JSON is deserialized once; the same map is given to the classifier
  and the aggregator. `getExecutionPlan` loads no configuration and writes nothing. A missing `retorchCI.properties` fails with an `IOException` naming the file.
- Strict by default: `generateJenkinsfile` and `getExecutionPlan` declare only `RetorchException` (see
  `orchestration/exception.md`) and `IOException`; test classes that cannot be loaded become `InvalidSystemException`;
  resources used by the system whose `dockerImage` is not `PLACEHOLDER;image` fail as `InvalidConfigurationException`.
  `withLenient(true)` logs and skips invalid tests, systems and TJobs instead (old behavior).

## Tests
`OrchestrationIntegrationTests`.
