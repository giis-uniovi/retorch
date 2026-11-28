# Spec: `giis.retorch.orchestration.config`

## Purpose
Where RETORCH reads and writes (`RetorchPaths`) and the typed view of `retorchCI.properties` (`RetorchConfig`), so no
other class hard-codes a `.retorch/...` path or reads a property by name.

## Public API
- `RetorchPaths(Path root)` (default: the working directory): `retorchDir`, `configurationsDir`, `ciPropertiesFile`,
  `resourcesFile(systemName)`, `scriptsDir`, `tjobScriptsDir` (`scripts/tjoblifecycles`), `coiScriptsDir`
  (`scripts/coilifecycles`), `envFilesDir`, `customScriptsDir`, `executionPlanFile`.
- `CustomHook` enum: the optional user hooks and their file in `.retorch/customscriptscode/` (`COI_SETUP`, `COI_TEARDOWN`,
  `TJOB_SETUP`, `TJOB_TEARDOWN`, `USAGE_PROFILE`, `JENKINS_POST`, `ENV`); the table is in `orchestration/orchestrator.md`.
- `RetorchConfig`: `load(paths)` (throws `InvalidConfigurationException` if the file does not exist or required properties are missing, listing all of them; `IOException` if it cannot be read), `validate()`, `withProfiling(Boolean)`
  (copy with the flag forced; `null` keeps it), `agentCIName`, `sutLocation`, `appUrl(tJobName)` (replaces
  `$TJOB_NAME` by the lower case name), `sutWaitHtml`, `testsBasePath` (all required), `sutWaitInterval` (default 5) and `sutWaitLimit` (default 40), positive integers, `profiling` (default `false`),
  `profilingJarUrl`, `azureRegion`, `awsRegion` (defaults in `orchestration/orchestrator.md`) and `priceProviders()` (property
  `price-providers`, optional: ordered map provider id -> comma separated regions parsed from `id=regions` pairs separated by `;`,
  e.g. `oracle=global;ibm=us-south,eu-de`; blanks ignored; empty map by default; ids `[a-z][a-z0-9-]*` and regions made of letters, digits,
  `-`, `_`, `.` only because they are written in a shell script; anything else is an `IllegalStateException` naming the property and the
  file, reported by `validate()` as a problem).

## Behavior
- A required property missing when the file is loaded fails with `InvalidConfigurationException` listing all of them.
  A `RetorchConfig` built by hand without validating throws `IllegalStateException("The required property '<name>' is
  missing in <source>")` from the getter.
- Profiling precedence: explicit `withProfiling` (CLI) > deprecated JVM system property `retorch.profiling` (still read
  by `load` for one release) > `profiling` in the file.
- The layout under the root is the one documented in `orchestration/orchestrator.md`; with the default root every path is
  relative to the working directory, as before.

## Tests
`WorkspaceConfigTests` (paths layout, generator running in a temporary folder with the same pipeline as
`expected_out/outputTestIntegration`, profiling override, missing property and missing file messages, `price-providers` parsing, invalid values rejected, and the generated `generateusageprofile.sh` with and without it).
