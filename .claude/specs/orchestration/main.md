# Spec: `giis.retorch.orchestration.main`

## Purpose
Command line entry point of the standalone (shaded) orchestration jar.

## Interface
```
java -jar retorch-orchestration-<version>-standalone.jar <rootPackageNameTests> <systemName> <jenkinsFilePath> [--profiling|--no-profiling]
```
- `--profiling`: the generated pipeline measures resources and publishes the usage profile.
- `--no-profiling`: it does not, even if `retorchCI.properties` has `profiling=true`.
- No flag: the `profiling` property of `retorchCI.properties` decides (default `false`).
- The flag reaches the orchestrator through the JVM system property `retorch.profiling` (`true`/`false`).

## Behavior
- Wrong number of arguments or an unknown 4th argument: usage message and exit code 1.
- Any generation failure: exit code 2.
- Compiled test classes and dependency jars found under `target/` are added to the classloader so the classifier can
  load the annotated tests.
