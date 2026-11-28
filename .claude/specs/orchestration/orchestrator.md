# Spec: `giis.retorch.orchestration.orchestrator` (+ `src/main/resources/generictemplates`)

## Purpose
Turns the ordered `Activity`s into a Jenkins declarative pipeline, the per-TJob `.env` files and the shell scripts the
pipeline runs.

## Public API
- `IRetorchOrchestrator.render(ExecutionPlan, RetorchConfig)` -> pipeline text. `JenkinsfileRenderer(customPostSteps)` is the
  Jenkins implementation: a **pure function** of its arguments (it only reads the templates from the classpath, never
  the project files), constructors only store their arguments. It also
  offers `environmentOf(TJob, RetorchConfig)` (placeholders and values of the TJob `.env`) and `isGateway(TJob)`.
- `OrchestrationWriter(RetorchPaths).generate(plan, config)`: the edge with the side effects, in this order: render,
  delete `.retorch/scripts` and any previous `executionplan.json`, write the `.env` file of every non-gateway TJob
  (`EnvFileWriter`), generate the scripts (`ScriptGenerator`) and, with profiling, the execution plan
  (`ExecutionPlanWriter`). Returns the Jenkinsfile text; where to write it is up to the caller
  (`OrchestrationGenerator`). It reads the optional `custom-jenkins-post` hook and gives it to the renderer.
- `ScriptGenerator(RetorchConfig, RetorchPaths)`: `generateScriptsTJob()`, `generateScriptsCOI()`,
  `replacePlaceholderTemplate`. One internal table maps every template to its output (folder, name, step, profiling
  only); all text files are read and written as UTF-8. Per-file generation is logged at DEBUG.
- `ExecutionPlanWriter(RetorchPaths)`, `EnvFileWriter(RetorchPaths)`.
- Every path comes from `RetorchPaths` and every property from `RetorchConfig` (see `orchestration/config.md`).
- The renderer throws `IllegalStateException` for a missing required property of a config that was not validated, and for
  a resource whose docker image is not `PLACEHOLDER;image` (the generator validates both earlier and reports them as
  `InvalidConfigurationException`).

## Configuration (`.retorch/configurations/retorchCI.properties`)
| Property | Meaning | Default |
|---|---|---|
| `agentCIName`, `sut-location`, `app-url`, `sut-wait-html`, `testsBasePath` | CI agent and SUT | required |
| `sut-wait-interval`, `sut-wait-limit` | seconds between two checks of the SUT and number of checks before `waitforSUT.sh` gives up (limit x interval seconds, 200 s by default); positive integers | `5`, `40` |
| `profiling` | `true` adds resource measurement + usage profile to the pipeline | `false` |
| `profiling-jar-url` | where `generateusageprofile.sh` downloads `retorch-profiling-cli.jar` from when `$HOME/.retorch/retorch-profiling-cli.jar` does not exist | GitHub `releases/latest/download/retorch-profiling-cli.jar` |
| `azure-region`, `aws-region` | comma separated cloud regions whose prices are compared; empty disables the provider | `westeurope`, `eu-west-1` |
| `price-providers` | optional extra (opt-in) price providers of the usage profile: `id=regions` pairs separated by `;`, e.g. `oracle=global;ibm=us-south,eu-de` (ids and regions: see `profiling/pricing.md`) | none |

The `--profiling/--no-profiling` flag of `RetorchMain` overrides `profiling` (passed as a `RetorchConfig` override; the JVM
system property `retorch.profiling` is still read for one release, deprecated). A required property that is missing
fails with a message naming it (`orchestration/config.md`). In the pipeline, the environment variables `AZURE_REGIONS` / `AWS_REGIONS` override the two region
properties without regenerating.

## Custom hooks (`.retorch/customscriptscode/`, all optional; the `CustomHook` enum)
| File | Inserted in | When it runs |
|---|---|---|
| `custom-coi-setup` | `coi-setup.sh` | during the COI set-up |
| `custom-tjob-setup` | `tjob-setup.sh` | before the TJob containers are started |
| `custom-tjob-teardown` | `tjob-teardown.sh` | **before** `docker compose down`, so the containers are still running (e.g. to dump coverage data); the working directory is the workspace |
| `custom-coi-teardown` | `coi-teardown.sh` | after the TJob containers are removed, before the COI tear-down timestamps are written (e.g. to build a coverage report) |
| `custom-jenkins-post` | Jenkinsfile `post { always { } }` | Jenkins steps inserted verbatim (e.g. `publishHTML`, `archiveArtifacts`) |
| `custom-usageprofile` | `generateusageprofile.sh` (profiling only) | before the profiling jar is looked for; leaves the jar in `$PROFILING_JAR` (e.g. a temporary build from source) |
| `custom.env` | every `<tjob>.env` | appended to the docker-compose env files |

## Templates and placeholder engine
The pipeline lives in `generictemplates/Jenkinsfile.tmpl` (skeleton), `Jenkinsfile-stage.tmpl` (one `Stage N` with its
`parallel` block), `Jenkinsfile-tjob.tmpl`, `Jenkinsfile-gateway.tmpl`, `Jenkinsfile-usageprofile.tmpl` and
`Jenkinsfile-publish.tmpl`; the leading `//` copyright lines of these templates are removed on load and never reach the
Jenkinsfile. A gateway activity is rendered as a real declarative stage (`stage('Gateway Activity') { steps { echo ... } }`), because only
`stage` is valid inside `parallel {}`. The scripts and the Jenkinsfile share one engine (`TemplateEngine`): `${KEY}` tokens replaced line by line;
a line that only holds a `${PROFILING_*}` or `${OPTIONAL_*}` token disappears when its value is empty; a token alone
on an indented line gives its indentation to every line of a multi-line value (fragments are written once, with relative
indentation), while a token at column 0 inserts the value verbatim (used for the user hooks).

Generated Jenkinsfile format: 2 spaces per level, every block closes at the indentation of its opening line; the comments
(`// EndStage...`) and their texts are part of the contract of the tests. A project that uses `custom-jenkins-post`
should indent its steps with 6 spaces (it is inserted verbatim into `post { always { } }`).

## Generated Jenkinsfile
Stages in order: Clean Workspace, Clone Project, SETUP-Infrastructure, one `Stage N` per scheduler stage (TJobs in
`parallel`), TEARDOWN-Infrastructure, and **only with profiling** `Usage-Profile`. The usage profile stage runs
`generateusageprofile.sh` inside `catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE')`: it can never fail the
build. The `post { always }` block archives `artifacts/*.csv` and logs; **only with profiling** it also archives
`artifacts/usageprofile/*.html` and calls `publishHTML` (`reportDir: 'artifacts/usageprofile'`, `reportFiles: 'usageprofile.html'` (report name "RETORCH Usage Profile", needs the HTML Publisher
plugin; the Jenkins CSP must allow inline scripts for the usage profile to be interactive).

## Generated scripts (`.retorch/scripts`, from `generictemplates/base*.sh`)
Always: `printLog.sh`, `waitforSUT.sh`, `writetime.sh`, `storeContainerLogs.sh`, `savetjoblifecycledata.sh`,
`tjoblifecycles/tjob-{setup,testexecution,teardown}.sh`, `coilifecycles/coi-{setup,teardown}.sh`.
Script contracts (lifecycle CSV columns `setup-start, setup-end, testexec-start, testexec-end, teardown-start, teardown-end`
must always be complete):
- `tjob-testexecution.sh` captures the Maven status with `mvn ... || MVN_EXIT_CODE=$?`: failing tests never skip the
  `testexec-end` timestamp; it exits 1 after writing it.
- `tjob-setup.sh` writes `setup-start`; if `docker compose up` fails **or `waitforSUT.sh` fails** it calls the documented
  `write_missing_times` (setup-end, testexec-start, testexec-end) and exits 1, so the tests do not run against a dead
  SUT and `tjob-teardown.sh` still fills the last two columns.
- `waitforSUT.sh` checks the page at the URL for `sut-wait-html` `sut-wait-limit` times, `sut-wait-interval` seconds apart,
  then stores the container logs, tears the TJob down and exits 1.
- `coi-teardown.sh` collects the volumes of **every** `tjob*` container (one `docker inspect` per container) before
  removing the containers, then removes those volumes.
- `coi-setup.sh` is generic: system information, directories, the `custom-coi-setup` hook and building the SUT images with
  the env file of the **first TJob of the plan** (`generateScriptsCOI(firstTJobName)`). The Selenium/Selenoid checks
  (`selenium-hub`, `chrome-video`, `chrome-node` running) and the cleaning of `/opt/selenoid` were removed from the
  template: a project that needs them puts them in `custom-coi-setup`, e.g.
  `for c in selenium-hub chrome-video chrome-node; do [ -n "$(docker ps --filter "name=$c" --filter status=running -q)" ] || { echo "$c is not running"; exit 1; }; done`
  and `find /opt/selenoid/video -mindepth 1 -maxdepth 1 -mtime +15 -exec rm -rf {} \;`.

Only with profiling: `resourcemonitor.sh`, `generateusageprofile.sh`. `generateusageprofile.sh` runs `$HOME/.retorch/retorch-profiling-cli.jar` on the usage file and writes `artifacts/usageprofile/usageprofile.html`. If the jar does not exist it is downloaded from `profiling-jar-url`. Before that check it inserts the optional `custom-usageprofile` snippet, where a project can prepare the jar by other means; the demonstrators use it as a **temporary** solution (clone the repository, build the profiling module with `-Dmaven.test.skip=true` and copy the jar) until a release publishes the jar. Deleting that file is all that is needed to switch to the download. The snippet is inserted in the script, so it may use `fail` and `$PROFILING_JAR` and must not `exit` on success.

Placeholders: `${KEY}` tokens are replaced line by line. A line that contains only a `${PROFILING_*}` placeholder
whose value is empty is **removed**, so the profiling-off scripts are byte-identical to the pre-profiling ones.
Profiling placeholders: `PROFILING_START_COMMAND` (tjob-setup), `PROFILING_STOP_COMMAND` (tjob-teardown),
`PROFILING_EXPORT_COMMAND` (savetjoblifecycledata), `PROFILING_JAR_URL_PLACEHOLDER`, `AZURE_REGION_PLACEHOLDER`,
`AWS_REGION_PLACEHOLDER` (generateusageprofile). The optional `OPTIONAL_PRICE_PROVIDERS_COMMANDS` token of
`generateusageprofile.sh` (on its own line just before the `java -jar` call) is empty unless `price-providers` is set, so without it the
script is byte-identical to before; with it the line becomes a comment plus `java() { command java -Dusageprofile-regions-<id>=<regions> ... "$@"; }`,
so the java call receives the regions of each provider as system properties read by `UsageProfileMain` (`profiling/main.md`).

### Execution graph file (profiling on)
`OrchestrationWriter` also writes `.retorch/executionplan.json` (`ExecutionPlanWriter`), the graph of the execution that the
usage profile draws: `{"tjobs":[{"id","stage","resources":[],"tests":[],"predecessors":[]}]}`, one node per TJob in stage
order. `id` is the lower case TJob name used by the pipeline and the usage file; `tests` are `Class#method` as passed to
Maven; `predecessors` are the ids of the TJobs that must finish first (always in an earlier stage). Gateway activities
(no resources, no tests) are included. Any plan file from a previous generation is deleted first, so with profiling off no
plan file remains. `generateusageprofile.sh` passes this file to the profiling tool as an optional last argument when the
project has it (`$WORKSPACE/.retorch/executionplan.json`, versioned with the project).

### Measurement contract (profiling on)
- `resourcemonitor.sh start <tjob>` samples, every `RETORCH_MONITOR_INTERVAL` seconds (default 5), the containers of the
  compose project `<tjob>` with `docker stats` / `docker ps -s`, into
  `retorchcostestimationdata/exec$BUILD_NUMBER/resources-<tjob>.csv`; `stop` kills the sampler.
- `resourcemonitor.sh export` writes the **single** output `artifacts/usageprofile-<branch>-<build>.csv`, with columns
  `kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes;image;image_bytes;volume_bytes` (12 columns;
  `phase` rows: lifecycle phases of every TJob, and of the COI under the name `COI`, taken from the lifecycle file;
  `sample` rows: one per container and instant, CPU as % of one core, memory in bytes and `disk_bytes` = size of the
  writable layer of the container; `container` rows: one per container, written the first time the sampler sees it,
  with its `image`, the size in bytes of that image (`docker image inspect`) and the total size of the volumes it
  mounts (`docker inspect` + `docker system df -v`, 0 if none)). The storage used by a TJob is therefore the writable
  layers plus the volumes plus the images (see `profiling/measurement.md`). The lifecycle file written by `savetjoblifecycledata.sh` is consumed
  and removed, so no other `artifacts/*.csv` of the execution remains.
- `basecoi-teardown.sh` must append the COI tear-down timestamps to `COI.data` **before** running
  `savetjoblifecycledata.sh`.

## Tests
`TemplateEngineTests`, `JenkinsfileRendererTests` (pure rendering, no file system), `WorkspaceConfigTests` (generation in a temporary workspace, including `price-providers` forwarded to `generateusageprofile.sh` only when configured). `JenkinsOrchestratorTests` (runs `OrchestrationWriter`): default mode against `expected_out/` (`scriptler/`, `outputTest*`); profiling mode against
`expected_out/profiling/`; profiling off generates no profiling script and no usage profile stage.
