# Spec: `giis.retorch.orchestration.main`

## Purpose
Command line entry point of the standalone (shaded) orchestration jar.

## Interface
```
java -jar retorch-orchestration-<version>-standalone.jar <rootPackageNameTests> <systemName> <jenkinsFilePath> [--profiling|--no-profiling] [--lenient] [--help]
```
- `--profiling`: the generated pipeline measures resources and publishes the usage profile.
- `--no-profiling`: it does not, even if `retorchCI.properties` has `profiling=true`.
- `--help`: prints the usage and returns 0.
- `--lenient`: invalid tests, resources and TJobs are logged and left out instead of failing the generation.
- No flag: the `profiling` property of `retorchCI.properties` decides (default `false`).
- The flag reaches the orchestrator as a profiling override of `OrchestrationGenerator.withProfiling(Boolean)` (no JVM
  system property is set; `RetorchConfig` still reads `retorch.profiling` for one release for old launchers).

## Behavior
- `RetorchMain.main` only calls `System.exit(RetorchMain.run(args))`; `run(String[])` returns the exit code and is the
  testable entry point. Options may appear anywhere among the three positional arguments.
- Exit codes: `0` success (also `--help`), `1` usage error (wrong number of positional arguments, unknown option,
  `--profiling` together with `--no-profiling`; the usage is printed), `2` the generation failed (a `RetorchException`
  logs only its message, which lists every problem; any other exception logs its stack trace).
- `run` restores the thread context classloader it extended.
- Compiled test classes and dependency jars found under `target/` are added to the classloader so the classifier can
  load the annotated tests.

## Tests
`RetorchMainTests` (exit codes, options, Jenkinsfile location).
