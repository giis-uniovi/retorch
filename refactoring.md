# RETORCH refactoring plan

Analysis of `retorch-orchestration` and `retorch-profiling` (Java and the usage-profile dashboard: HTML, CSS, JS), with
the tasks to carry out. Each task names the specs it must update (iron rule of `CLAUDE.md`). Nothing here is implemented
yet.

## Suggested order across modules

1. Safety nets: orchestration task 1 (specs aligned), profiling task 1 (JS smoke and unit tests).
2. Behaviour-preserving refactors: orchestration 2-6 (`expected_out/` byte-identical), profiling 2-4 and 8-10.
3. Dashboard MVC: profiling 5-7 (state + hash, views, controller), then 11 (HTML/layout).
4. Behaviour changes: orchestration 7-10 (fail loudly, plan, CLI, shell templates), profiling 12-14 (Java splits).
5. Cross-module contracts and larger redesigns: profiling 15, orchestration 11-12.

## retorch-orchestration

Paths are relative to `retorch-orchestration/src/main/java/giis/retorch/orchestration/` (Java) and
`retorch-orchestration/src/main/resources/generictemplates/` (templates). Line numbers refer to the current branch.

### Findings

**Bugs (wrong output or silent loss of tests)**
- B1. `hashCode()` returns `super.hashCode()` (identity) while `equals` is value based: `model/AccessMode.java:44`,
  `AccessModeTypes.java:43`, `Activity.java:65`, `ElasticityModel.java:37`, `Resource.java:84`, `System.java:42`,
  `TestCase.java:45`, `TGroup.java:48`, `TJob.java:98`. Any `HashSet`/`HashMap`/`distinct()` over them works only by
  identity (e.g. `ExecutionPlan.generateSortedActivityGraph` uses `new HashSet<>(allPassedActivities).containsAll`).
- B2. Asymmetric `equals`: `TJob.equals` (`TJob.java:101`) only checks that the other's tests are contained in this one;
  `Activity.equals` (`Activity.java:69`) uses `containsAll` one way; `TGroup.equals` and `System.equals` likewise.
  `a.equals(b) != b.equals(a)`, which breaks `CollectionUtils.subtract` in the scheduler.
- B3. `Scheduling.addTJob` (`scheduler/Scheduling.java:41`) silently drops a TJob whose resources are not all in
  `baseResources`: its tests never reach the Jenkinsfile and nothing is logged.
- B4. Invalid input degrades silently instead of failing: `RetorchClassifier` drops tests without valid access modes
  (`classifier/RetorchClassifier.java:162`, errors only logged); `RetorchAggregator` replaces an invalid system by
  `new System("EmptySystem ERROR")` (`scheduler/RetorchAggregator.java:30`) and the user gets a later, unrelated
  `NoTGroupsInTheSchedulerException`. `validateSystem` never throws for the documented cases (spec says it does).
- B5. `res.getDockerImage().split(";")` (`orchestrator/JenkinsOrchestrator.java:120`): NPE when `dockerImage` is absent,
  `ArrayIndexOutOfBoundsException` when it has no `;`. `getProperty("app-url").replace(...)` (`:76`, `:306`) NPE when
  the property is missing; a missing `retorchCI.properties` is only logged (`:48-52`).
- B6. `OrchestrationGenerator.generateJenkinsfile` (`generator/OrchestrationGenerator.java:51`) appends `"Jenkinsfile"`
  to the given path without a separator (`./out` -> `./outJenkinsfile`).
- B7. `basetjob-testexecution.sh:7` uses `set -e`, so a failing `mvn test` exits immediately: `MVN_EXIT_CODE=$?` (`:29`)
  and the second `writetime.sh` never run, the lifecycle row loses its `testexec-end` and every later column shifts
  (the profiling phases become wrong). The demonstrators hide it with `testFailureIgnore=true`.
- B8. `basetjob-setup.sh:41` ignores the exit code of `waitforSUT.sh`: when the SUT never comes up the containers are
  torn down but the tests still run. `basecoi-teardown.sh:27` passes several ids to one `--filter id=`, so volumes of
  more than one container are never collected.
- B9. Default charset: `ResourceSerializer.java:112` (`getBytes()`) and `:125` (`new String(bytes)`), and
  `ScriptGenerator.getFileFromResource` (`InputStreamReader` without charset).

**Design**
- D1. Model class `model/System.java:18` shadows `java.lang.System`; code needs `java.lang.System.getProperty/exit`.
- D2. `.retorch/...` relative paths hard-coded in five classes (`ResourceSerializer.java:29`,
  `JenkinsOrchestrator.java:30,48,153,272,328`, `ScriptGenerator.java:30-58,84,146-161`, `ExecutionPlanWriter.java:32`):
  everything depends on the working directory and cannot be tested in a temp folder.
- D3. Configuration loaded twice (`JenkinsOrchestrator.java:48`, `ScriptGenerator.java:84`) and resources JSON
  deserialized three times (`RetorchClassifier.java:112`, `RetorchAggregator.java:34`, `JenkinsOrchestrator.java:46`).
  Profiling travels as the global JVM property `retorch.profiling` (`main/RetorchMain.java:54` -> `JenkinsOrchestrator.java:54`).
- D4. Constructors do I/O (`JenkinsOrchestrator`, `RetorchAggregator`, `ExecutionPlan` sorting in the constructor).
- D5. `generatePipeline` (`JenkinsOrchestrator.java:149`) mixes text generation with side effects: deletes
  `.retorch/scripts` (`:153`), writes env files from inside `generatePipelineTJobDirective` (`:130`), scripts (`:212`)
  and `executionplan.json` (`:215`).
- D6. `IRetorchOrchestrator` exposes Jenkins details and unused parameters: `resource`/`tJobId` of
  `generateTestCaseExecutionDirective` (`IRetorchOrchestrator.java:34`; `tJobId` duplicates the TJob), `isParallel`
  (`:55`), and `getContainedResourceID` (`JenkinsOrchestrator.java:283`) computes a value nobody uses.
- D7. `ExecutionPlan(String, List<?>)` (`model/ExecutionPlan.java:26`) is two constructors in one (activities or TJobs,
  chosen by the class of element 0, unchecked casts); validation `size() <= distinctPredecessors` (`:75`) is a
  heuristic, not a DAG check; `addTJob` (`:113`) mutates ids and stages of the TJobs while sorting.
- D8. `TJob` keeps dead state now owned by profiling: phase timings and `setAvgTime` (`TJob.java:24-29`),
  `totalCapacities`/`getCapacitiesGivenTime` (`:89`, `:173`), magic default concurrency 50 (`:34`) and a logger per
  instance (`:20`). `getIdTJob()` lower-cases on read.
- D9. Sentinels `"None"` and `-1` instead of `Optional`/validation (`RetorchClassifier.java:293-317,354,363`).
- D10. `findClasses` (`RetorchClassifier.java:76`) only walks directories: a tests package inside a jar is not found.
- D11. Checked-exception lists of 7 types (`OrchestrationGenerator.java:43,65`); `RetorchMain` parses arguments by hand
  and calls `System.exit` (`RetorchMain.java:47,64`).
- D12. `basecoi-setup.sh` is SUT specific: it requires `selenium-hub`, `chrome-video`, `chrome-node` (`:75`), cleans
  `/opt/selenoid`, and builds with the fixed `tjoba.env` (`:83`), which fails if no TJob is called `tjoba`.

**Maintainability**
- M1. Jenkinsfile built with `StringBuilder` and inconsistent indentation (`"} // End Parallel"` at column 0,
  `JenkinsOrchestrator.java:200`); the expected files freeze that formatting.
- M2. `ScriptGenerator.getCustomContent` `switch` (`ScriptGenerator.java:144`) and the `${PROFILING_` line-removal rule
  (`:188`) are ad-hoc; 25 path constants for 12 templates.
- M3. Noisy INFO logs: one line per test method (`RetorchClassifier.java:268`), "Files successfully created." per
  template (`ScriptGenerator.java:194`).
- M4. Getters/setters that mutate: `Resource.getReplaceable()` sorts the list (`Resource.java:112`),
  `setMinimalCapacities` sorts its argument (`:120`).
- M5. Templates: unused `DOCKER_HOST_IP` (`base-waitforSUT.sh:11`); comment says 200 s but `WAIT_LIMIT=40` x 5 s waits
  ~205 s and "healthy state" is really an HTML grep (`:5,13`); `basecoi-setup.sh:54,56` log `$1` of a script without
  arguments; `basetjob-setup.sh:31-34` calls `writetime.sh` three times to pad the missing columns with no explanation.
- M6. Spec drift: `model.md` claims value-based `equals/hashCode`; `scheduler.md` says `validateSystem` throws and
  documents `generateActivities(List<TGroup>)` (it takes no argument); `classifier.md` lists
  `getSystemFromPackage(pkg)` (it is `(systemName, pkg)`).

### Tasks

Order: tasks 1-6 keep every file under `src/test/resources/expected_out/` byte-identical (run `mvn test -pl
retorch-orchestration -am` after each one). Tasks 7-10 change behaviour and need new tests and expected files. Tasks
11-12 are larger redesigns.

1. **Align the specs with the code (no code change).** Goal: the iron rule holds before touching anything.
   Files: `.claude/specs/orchestration/model.md`, `scheduler.md`, `classifier.md`. Steps: fix the three statements
   of M6 and note the known bugs B1-B4 as "current behaviour". Verify: review only. Risk: none.

2. **Consistent `equals`/`hashCode`.** Goal: fix B1, B2, M4. Files: every class in `model/`. Steps: implement
   symmetric `equals` (compare test-case/resource *sets*, ids, access modes) with matching `Objects.hash`; sort
   `replaceable`/`minimalCapacities` once in the constructor/setter on a copy, never in the getter; add a
   `ModelEqualityTests` (reflexive, symmetric, hash agrees, usable in `HashSet`). Verify: all tests and
   `expected_out` unchanged (the scheduler must still produce the same plan). Spec: `model.md`. Risk: medium, the
   scheduler relies on `CollectionUtils.subtract`; compare the generated Jenkinsfiles of the 4 demonstrators.

3. **Rename `model.System` to `SystemUnderTest`.** Goal: D1. Files: `model/System.java` and its users. Steps: rename,
   drop the `java.lang.System` qualifiers. Verify: build + tests. Spec: `model.md`, `classifier.md`, `scheduler.md`.
   Risk: low (public API rename; no external user other than this repo).

4. **Workspace and configuration objects.** Goal: D2, D3. Files: new `config/RetorchPaths` (root dir + every
   `.retorch` path, custom hook paths, outputs) and `config/RetorchConfig` (typed view of `retorchCI.properties`:
   `agentCIName`, `sutLocation`, `appUrl(tjob)`, `sutWaitHtml`, `testsBasePath`, `profiling`, regions, jar url; fails
   with a clear message when a required property is missing, fixing the NPEs of B5). Steps: load the resources map
   once in `OrchestrationGenerator` and pass it to classifier, aggregator and orchestrator; pass `profiling` as a
   `RetorchConfig` override from `RetorchMain` instead of the JVM property (keep reading the property for one release
   for compatibility). Verify: tests unchanged; new test that runs the generator with a `RetorchPaths` on a
   `TemporaryFolder`. Spec: new `config.md` + README row, `orchestrator.md`, `main.md`, `generator.md`,
   `classifier.md`. Risk: medium (touches every class; do it in small commits).

5. **Pure pipeline rendering, side effects at the edge.** Goal: D4, D5, D6. Files: `orchestrator/*`. Steps: split
   into `JenkinsfileRenderer` (activities -> String, no I/O), `EnvFileWriter`, `ScriptGenerator`, `ExecutionPlanWriter`
   and an `OrchestrationWriter` that cleans `.retorch/scripts` and calls them in order; simplify `IRetorchOrchestrator`
   to `String render(ExecutionPlan, RetorchConfig)` and remove `resource`, `tJobId`, `isParallel`,
   `getContainedResourceID`; constructors only store arguments. Verify: `expected_out` unchanged; a unit test of the
   renderer with no file system. Spec: `orchestrator.md`. Risk: medium.

6. **Small cleanups.** Goal: B9, M2, M3, D9 (partially). Steps: UTF-8 everywhere; replace the `switch` by a
   `Map<Hook, String>`/enum of custom hooks; one table `template -> output (-> profiling only)` instead of 25
   constants; DEBUG for per-method and per-file logs; constants for `"None"`/`-1` with a comment. Verify: tests and
   `expected_out` unchanged. Spec: `orchestrator.md` (hook table unchanged), `classifier.md`. Risk: low.

7. **Fail loudly on invalid input.** Goal: B3, B4, B5 (docker image), D11. Files: classifier, aggregator, scheduler,
   generator, main. Steps: introduce `RetorchException` (checked) with subclasses `InvalidConfigurationException`,
   `InvalidSystemException`, `SchedulingException`; collect all validation messages and throw once with all of them;
   `Scheduling.addTJob` throws when a TJob cannot be placed; validate `dockerImage` as `PLACEHOLDER;image` when the
   resources are loaded; `OrchestrationGenerator` declares only `RetorchException` + `IOException`. Option
   `--lenient` (or property) to keep the current "skip and log" behaviour if wanted. Verify: new negative tests
   (`SchedulerTests`, `ClassifierTests`: missing access mode, unknown resource, TJob that does not fit, bad docker
   image); positive `expected_out` unchanged. Spec: `classifier.md`, `scheduler.md`, `generator.md`. Risk: medium,
   demonstrators with latent errors will now fail; run `orchestrate.ps1` in the 4 demonstrators.

8. **Explicit ExecutionPlan.** Goal: D7, D8. Steps: two factories (`ExecutionPlan.fromActivities`,
   `ExecutionPlan.ofTJobs`) or drop the TJob variant if unused; real DAG validation (cycles, unknown predecessors,
   at least one initial activity) with clear messages; assign ids/stages in one explicit step (`PlanNumbering`)
   instead of inside sorting; remove from `TJob` the phase timings, `setAvgTime`, capacities and
   `getCapacitiesGivenTime` (profiling owns them; update `TJobsEntityTests`), make `getIdTJob` return the stored id
   and lower-case it where the pipeline needs it. Verify: `expected_out` unchanged. Spec: `model.md`. Risk: medium
   (the `minimalCapacities` JSON property must still be accepted and ignored).

9. **CLI.** Goal: D11. Files: `main/RetorchMain.java`. Steps: small argument parser (positional + `--profiling`,
   `--no-profiling`, `--help`), a `run(String[]) -> int` testable method, `main` only calls `System.exit(run(args))`;
   `jenkinsFilePath` resolved with `Paths.get(dir).resolve("Jenkinsfile")` (fixes B6). Verify: new `RetorchMainTests`
   (usage -> 1, failure -> 2, path handling). Spec: `main.md`, `generator.md`. Risk: low.

10. **Shell templates.** Goal: B7, B8, D12, M5. Steps: in `basetjob-testexecution.sh` capture the Maven status with
    `mvn ... || MVN_EXIT_CODE=$?` so the end time is always written; in `basetjob-setup.sh` check `waitforSUT.sh` and
    pad the missing timestamps through one documented `write_missing_times` function; fix the volume collection in
    `basecoi-teardown.sh` (loop over ids); remove `DOCKER_HOST_IP`, fix the wait comment and make
    `WAIT_LIMIT`/interval properties; move the Selenoid/Selenium checks and `/opt/selenoid` cleaning of
    `basecoi-setup.sh` to an optional hook and build the images with the env file of the first TJob of the plan
    (placeholder) instead of `tjoba.env`; drop the `$1` of the COI logs. Verify: regenerate `expected_out/` and
    `expected_out/profiling/`, then the 4 demonstrators (`orchestrate.ps1`) and one Jenkins run with a failing test
    to check the lifecycle columns. Spec: `orchestrator.md` (+ demonstrator CLAUDE/README if the hooks change). Risk:
    high (runs in CI; the demonstrators' `custom-coi-setup` may need the moved checks).

11. **Template-based Jenkinsfile.** Goal: M1. Steps: move the pipeline skeleton to `generictemplates/Jenkinsfile.tmpl`
    with stage/TJob fragments and the same placeholder engine as the scripts; normalise indentation. Verify: regenerate
    `expected_out` (formatting diff only, reviewed once) and the demonstrator Jenkinsfiles. Spec: `orchestrator.md`.
    Risk: medium (formatting change in every generated Jenkinsfile; do it in a dedicated commit).

12. **Classpath scanning from jars.** Goal: D10. Steps: when the package URL is `jar:`, enumerate entries with
    `JarFile`; skip `$` classes as today. Verify: new test with a jar built in a `TemporaryFolder`. Spec:
    `classifier.md`. Risk: low.

## retorch-profiling

### Findings

**Java (`giis.retorch.profiling`)**

- P1. `ExecutionMeasurements` (433 lines) parses the CSV, resamples the timeline, computes the per-TJob metrics and the per-container usage. Four responsibilities in one class, with nested public mutable classes (`Load`, `Slice`, `Timeline`) that the calculator depends on.
- P2. `PricingFetcher` (485 lines) mixes the HTTP access, the cache, Azure, AWS EC2/Fargate and Device Farm. `open()` is private, so the fetch code cannot be tested without network; only the `parse*` methods are tested.
- P3. `PriceCatalog` has public mutable fields and lists (`merge` mutates), while the DTOs are immutable. The two models are inconsistent.
- P4. Phase names are string literals in several places: `CsvConstants`, `ExecutionMeasurements.COI_*`, `CostCalculator.COI` / `TEST_EXECUTION` (`"tjob-testexec"`, a duplicate of `CsvConstants.TJOB_TEST_EXEC_LABEL`), `Fmt.PHASES` in JS. Kinds (`VM`, `CONTAINER`, `BROWSER_SERVICE`) and objects (`compute`, `storage`, `browser`) are duplicated in Java and JS.
- P5. `CostCalculator` has one `if`/method per alternative kind. Adding Device Farm required touching the calculator core. It should be one strategy per kind.
- P6. `UsageProfilerToolBox` has 4 `generateUsageProfile` overloads. The browser-TJob heuristic (resource name contains `browser`) is hard-coded and should be a setting.
- P7. Two cross-module contracts have no shared fixture:
  - `executionplan.json`, written by `orchestration.ExecutionPlanWriter.Node` and read by `profiling.ExecutionPlanReader.PlanNode`.
  - The usage CSV, written by `base-resourcemonitor.sh` and read by `ExecutionMeasurements`.

  Today a change on one side is only caught at run time in Jenkins.
- P8. `UsageProfileGenerator.APP_SCRIPTS` is a hand-kept list. A new JS file that is not added to it is silently missing from the page.

**Dashboard (`resources/usageprofile`: HTML, CSS and JS)**

- D1. **State is scattered**, with no single owner:
  - `App.S`;
  - `Graph.metricId`, `selectCallback`, `listKey`;
  - `Simulator.current`, `wantedBuy`, timers;
  - `Charts.instances`, `timeAxisState`;
  - `I18n` / `Theme` (localStorage).

  Any module can mutate the page.
- D2. **Deep links only go one way.**
  - `App.applyHash` reads `alt`, `node`, `metric`, ... but never writes them.
  - Only `Simulator.writeHash` writes, and only its own keys. A copied URL does not reproduce the selected alternative or TJob.
  - `hashchange` triggers a full `renderAll`.
- D3. **Init order bug.** `applyHash` with `cmp=` calls `setCompared`, which renders the table and the comparison before the first `renderAll` and before `bindControls`.
- D4. **Model, view and controller are mixed in every file.**
  - `app.js` holds the derived data (`visible`, `sorted`, `idle`, `cheapest`), the HTML strings, the events and the hash.
  - `graph.js` holds the Gantt layout (model), the chart, the TJob list, the panel, the containers section and the radio listeners.
  - `simulator.js` already separates `calc` (pure) from rendering. It is the pattern to generalise.
- D5. **HTML built by concatenation with manual `Fmt.esc`.**
  - The i18n strings are inserted unescaped in several places (`tabs`, `cmpHelpers`, `<h4>` in `graph.js`).
  - `Fmt.esc` does not escape `'`.
  - It is safe today because the translations are trusted, but it is fragile.
- D6. **Over-rendering.**
  - `select()` re-creates the Gantt chart to refresh the cost panel.
  - A language change destroys and re-creates every ECharts instance.
  - A resize re-creates every chart instead of calling `resize()` when only the size changed.
- D7. **Duplicated helpers.**
  - Cost sums appear in `app.js` (`sum`), `simulator.js` (`sumStorage`) and `graph.js` (`costRows`, `total`).
  - `el()` is defined twice.
  - The kinds list is hard-coded in `app.js`.
- D8. **DOM coupling.** About 60 ids are hard-coded across modules (`Simulator.FIELDS`, `'graphPanel'`, `'table'`, ...). The global `keydown` Escape handler depends on `graphCard`.
- D9. **i18n.** The 4 languages live in one 425-line file with silent fallback to English. For example, `kind.VM` exists only in `en`. No test checks that the 4 languages have the same keys.
- D10. **UX / layout.** The "Compare selected" card is fed by the checkboxes of the "All alternatives" table, which sits at the end of the page after the Projection. The hint says "table below", but it is three sections away.
- D11. **HTML.**
  - The static `<title>` and `<h1>` fallback ("Resource Usage Profile") differ from `app.title`.
  - The footer link opens a new tab without the "new tab" note and without `noreferrer`.
  - `<html lang="en">` is static until JS runs.
- D12. **CSS.**
  - The theme tokens are on two very long lines, which are hard to review in diffs.
  - The chart palettes live in JS (`Fmt.PHASE_COLORS`, `CYCLE_COLORS`) instead of CSS custom properties, so a theme cannot adjust them.
  - There are 3 `!important`.
  - There is no section structure (tokens / layout / components / utilities).
- D13. **No JS tests.** The pure logic has no tests: Gantt layout, cost aggregations, cheapest selection, simulator `calc` and hash parsing. The Java tests only check that the page is assembled.

### Target architecture of the dashboard (MVC)

The output stays one self-contained HTML file with one inline script and IIFE namespaces (no bundler, ES2015, Java 8 build). Files, in `APP_SCRIPTS` order:

| Layer | File | Responsibility | Must not |
|---|---|---|---|
| infra | `i18n.js`, `theme.js`, `format.js`, `html.js` (new) | Translations, theme, number/date formats; `Html.t\`...\`` tagged template that escapes every interpolation, with `Html.raw()` for trusted fragments | Know the data |
| **Model** | `model/profile.js` | Read-only queries over the JSON: `alternatives(filter)`, `sorted(alts, by)`, `cheapest(n)`, `idle(a)`, `costBy(a, 'phase'\|'tjob'\|'object')`, `label(a)`, `nodes()`, `node(id)`, `ganttLayout()`, `tjobStorage(node)` | Touch the DOM, read `I18n` |
| **Model** | `model/simulation.js` | Today's `Simulator.calc` + `project` / `breakEven`, pure | Touch the DOM |
| **Model** | `model/state.js` | The only mutable state: `{lang, theme, selectedAlt, compared, filters, showOver, metric, sort, cmpGroup, group, node, contMetric, sim: {...}}`, plus `get()`, `update(patch)` (validates, returns the changed keys) and `subscribe(fn)` | Render |
| **Model** | `model/hash.js` | `parse(hash) -> patch` and `serialize(state) -> hash` for every key; the only place that knows the URL format | |
| **View** | `charts.js` | ECharts builders (as today) plus `resize()` for every instance | Hold page state |
| **View** | `views/header.js`, `usage.js`, `graph.js`, `alternatives.js`, `compare.js`, `detail.js`, `simulator.js` | Each exports `render(vm)` (and optionally `resize()`), receives a ready view-model, writes only inside its own root element and never reads `State` | Compute costs, mutate state |
| **Controller** | `controller.js` | Delegated events (one listener per root): action -> `State.update(patch)` -> re-render only the views whose keys changed (table `key -> views`); `keepFocus`, `announce`, Escape, resize (calls `resize()`, not re-render) and hashchange; writes the hash after every change | Build HTML |
| bootstrap | `app.js` | `Controller.start(JSON.parse(#data))` | |

The view-models are built by the controller from `Model` + `State` + `I18n`/`Fmt`. That is the only place where the three meet. The JSON gets a `meta` block written by Java (`phases`, `kinds`, `objects`, in display order) so the JS stops duplicating those lists.

### Tasks (in order; each one keeps the page working)

1. **Safety net first.** Goal: be able to detect regressions of the page.
   - Add a headless-browser smoke script (the existing `verify_ui` / axe approach) as `src/test/js/smoke.mjs`. It opens a page generated from the test fixtures, checks there are no console errors, the sections render, 0 axe violations, and both time axes stay aligned.
   - Add `node --test` unit tests for pure logic (`src/test/js/*.test.mjs`), loading the IIFE files in a `vm` context.
   - Run them with `exec-maven-plugin` under a `-Pjs-tests` profile, so the Java 8 build does not require Node.
   - Specs: `profiling/usageprofile.md` (Tests section). Risk: low.
2. **Contract constants in the JSON.** Goal: remove P4 and D7.
   - Java: a `Phase` enum (label + order) replaces `CsvConstants` labels, `ExecutionMeasurements.COI_*` and the `CostCalculator` literals; kinds and objects become constants of `Alternative` / `CostItem`.
   - `UsageProfileData.meta` = `{phases, kinds, objects}`; JS reads `D.meta` and drops `Fmt.PHASES` and the `kinds` list.
   - Tests: the JSON contract test expects `meta`. Specs: usageprofile.md (JSON contract). Risk: low.
3. **`Html` tagged template + escaping audit.** Goal: D5.
   - Add `html.js`; replace the concatenations module by module; escape `'` too.
   - Verify: smoke test, plus a unit test of `Html.t` with `<"'&>`. Risk: low.
4. **Extract the Model.** Goal: D4 and D7 (no behaviour change).
   - Move `visible/sorted/idle/cheapest/label/sum` from `app.js` and `layout/span/tjobStorage/total` from `graph.js` to `model/profile.js`; move `calc` / `project` / `breakEven` from `simulator.js` to `model/simulation.js`.
   - Unit-test them with Node.
   - Update `APP_SCRIPTS` (and generate it, see task 9). Risk: low.
5. **Single state + hash both ways.** Goal: D1, D2 and D3.
   - `model/state.js` and `model/hash.js` take over `App.S`, `Graph.metricId`, the Simulator fields and `applyHash` / `writeHash`.
   - The hash is written with `history.replaceState` after every update, so every view is reproducible from a link. `hashchange` applies only the changed keys.
   - Fix the init order: parse the hash, then one render, then bind.
   - Tests: Node tests for `parse(serialize(s)) == s` and for invalid values. Specs: the deep-links paragraph lists every key. Risk: medium (deep-link behaviour).
6. **Views per section.** Goal: D4, D6 and D8.
   - Split the rendering into `views/*.js`. Each view gets its root element once (`View.mount(rootEl)`) and renders from a view-model.
   - The cost panel of the TJob becomes its own view, so selecting an alternative no longer re-creates the Gantt chart.
   - Resize calls `Charts.resize()`; only a root font-size change re-renders the charts (labels are sized in px).
   - Verify: smoke test, axe, focus kept after re-render (keyboard tests in the smoke script). Risk: medium.
7. **Controller.** Goal: D1 and D8.
   - `controller.js` with delegated listeners, the `key -> views` dirty map, `keepFocus` / `announce`, and Escape scoped to the panel. `app.js` becomes the bootstrap only.
   - Specs: front-end source layout. Risk: medium.
8. **i18n per language and key-parity test.** Goal: D9.
   - Move each language to `i18n/<lang>.json`, injected by Java under one placeholder; `i18n.js` keeps the logic.
   - A Java test fails when a language misses or adds keys compared with `en`.
   - Fill the missing `es` / `fr` / `de` keys (`kind.VM`, ...). Risk: low.
9. **Generated script list.** Goal: P8.
   - `APP_SCRIPTS` is read from `usageprofile/js/scripts.txt` (one file per line, in order). A test checks that every `.js` file under `js/` is listed exactly once. Risk: low.
10. **CSS structure and tokens.** Goal: D12.
    - Split `usageprofile.css` into commented sections (tokens, base, layout, components, charts, utilities, forced-colors), one token per line.
    - Move the phase, object and cycle palettes to `--phase-*` / `--cycle-*` custom properties read by `Theme.colors()`, with dark variants where contrast needs them.
    - Remove the `!important` unless a forced-colors rule really needs it.
    - Verify: the visual diff of the smoke screenshots in both themes is identical. Risk: low.
11. **HTML fixes and layout.** Goal: D10 and D11.
    - The static `<title>` and `<h1>` equal `app.title`. The footer link gets `noopener noreferrer` and the visually hidden "new tab" note. `lang` is set from the stored language before the first paint (tiny inline script) or left as is if a flash is acceptable.
    - Decision for the user: move the "All alternatives" table right under "Compare selected" (or give the comparison its own picker), so the checkboxes that feed it are next to it. Risk: low.
12. **Java: split `ExecutionMeasurements`.** Goal: P1.
    - `UsageCsvReader` (parsing and validation), `TimelineBuilder` (resampling and storage rule) and `TJobMetricsCalculator` (metrics and container usage).
    - `Load`, `Slice` and `Timeline` become immutable top-level classes.
    - The existing tests must pass unchanged (they are behaviour tests). Risk: medium.
13. **Java: price providers.** Goal: P2 and P3.
    - A `PriceProvider` interface (`AzurePrices`, `AwsPrices`, `DeviceFarmPrices`), a `PriceCache` and an injectable `HttpGet` (URL -> InputStream), so `fetchOrLoad` is tested with canned responses (cache hit, expired cache, provider failure with fallback).
    - `PriceCatalog` becomes immutable (builder + `merge` returning a new catalog). Bump `SCHEMA_VERSION` only if the JSON layout changes. Risk: medium.
14. **Java: cost strategies.** Goal: P5 and P6.
    - An `AlternativeCost` interface with `VmCost`, `ContainerCost` and `BrowserServiceCost`; `CostCalculator` only orchestrates and sorts.
    - `UsageProfilerToolBox` gets a single `generate(UsageProfileRequest)` (builder with plan name, usage file, output, prices, optional plan, browser resource pattern). The old overloads delegate and are deprecated.
    - The browser pattern becomes a property (`usageprofile-browser-resources`, default `browser`). Risk: low.
15. **Cross-module contract fixtures.** Goal: P7.
    - One `executionplan.json` and one usage CSV under a shared test resource (or duplicated with a test that compares the copies).
    - Orchestration tests check that `ExecutionPlanWriter` and the `resourcemonitor.sh` header produce exactly those fields and columns; profiling tests read the same files.
    - Specs: orchestration/orchestrator.md and profiling/usageprofile.md reference the shared fixtures. Risk: low.

Every task updates `.claude/specs/profiling/usageprofile.md` (and the README row when a file is added or removed), per the iron rule.
