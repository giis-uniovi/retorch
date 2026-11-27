# Plan: dashboard front-end rewrite + execution graph (retorch-profiling)

Two agents work in parallel on disjoint files, against the contract below. The lead does the orchestrator part
(`.retorch/executionplan.json`, already done) and the integration.

## What the user asked for
1. Refactor the HTML for maintainability (separate files, one concern each).
2. Header with the GIIS logo and the title **"Perfil de Uso de Recursos"** (translated per language, see i18n).
3. Footer with the group footer, the same as `README.md`: a horizontal rule and the centered `giis-footer.svg` linking to
   `https://giis.uniovi.es/` (alt text `GIIS - Software Engineering Research Group, University of Oviedo`).
4. Execution graph of the tests: nodes are TJobs, clicking a node shows which tests were executed (and TJob metrics).
5. Try several combinations of providers and adjust the charts (oversubscription and so on).
6. Several languages and a light / dark theme selector.

## Contract

### Resource files (`retorch-profiling/src/main/resources/usageprofile/`)
| File | Content | Owner |
|---|---|---|
| `usageprofile.html` | page structure only; placeholders below | Front-end |
| `usageprofile.css` | all styles; themes through CSS variables | Front-end |
| `js/i18n.js`, `js/format.js`, `js/theme.js`, `js/charts.js`, `js/graph.js`, `js/app.js` | one namespace object each (`I18n`, `Fmt`, `Theme`, `Charts`, `Graph`, `App`) | Front-end |
| `echarts.min.js` | Apache ECharts 5.6.0, untouched | existing |
| `giis-logo.svg` | header logo, already provided (no XML prolog, inline-able) | existing |
| `giis-footer.svg` | footer logo, already provided | existing |

`UsageProfileGenerator` (Java agent) assembles ONE self-contained HTML by replacing in `usageprofile.html`:
- `/*CSS*/` (inside `<style>`) <- `usageprofile.css`
- `/*DATA*/` (inside `<script type="application/json" id="data">`) <- the JSON
- `/*ECHARTS*/` (inside `<script>`) <- `echarts.min.js`
- `/*APP*/` (inside `<script>`) <- the six `js/*.js` files **concatenated in this order**: `i18n, format, theme, charts, graph, app`
  (one script, one global scope; each file only adds its namespace object; `app.js` ends by calling `App.init()`)
- `<!--HEADER_LOGO_SVG-->` <- `giis-logo.svg`
- `<!--FOOTER_SVG-->` <- `giis-footer.svg`
In every inlined script `</script` is escaped as `<\/script`, and in the JSON `</` as `<\/`. Front-end code must therefore never
contain the text `</script`. No external requests (no CDN, no web fonts, no remote images).

### JSON embedded in the page (`UsageProfileData`)
Existing, unchanged: `plan, pricesFetchedAt, durationSec, peakCpu, peakMemGb, peakDiskGb, warnings[string],
alternatives[{id, provider, kind("VM"|"CONTAINER"), label, region, hourlyPrice, total, items[{object("compute"|"storage"), tjob, phase, cost}]}],
timeline{t[], cpu[], memGb[], diskGb[]}`. Phases in items: `COI-setup`, `tjob-setup`, `tjob-testexec`, `tjob-teardown`, `coi-teardown`,
`oversubscription`; `tjob` is a TJob id (lower case, e.g. `tjoba`), `COI` or `(idle)`.

New: `graph` — `null` when no execution plan is available (hide the graph card), otherwise
`{ nodes: [ { id, stage, resources[], tests[], predecessors[], setupSec, execSec, teardownSec, peakCpu, peakMemGb } ] }`
- `id` lower case TJob id (matches `items[].tjob`); `stage` int >= 0; `predecessors` are ids of other nodes (edges predecessor -> node);
- `tests` entries are `Class#method`;
- the five measured fields are numbers or `null` (TJob without measurements);
- nodes without resources and tests may exist (gateway activities).
Warnings from the backend stay in English (not translated).

### Plan file read by the Java side (`.retorch/executionplan.json`, written by the orchestrator)
`{"tjobs":[{"id":"tjoba","stage":0,"resources":["executor"],"tests":["Cls#m"],"predecessors":[]}]}`

### Command line
`UsageProfileMain planName usageCsv outputHtml azureRegions awsRegions priceCacheFile [executionPlanJson]` — the 7th argument is
optional; if absent or the file does not exist there is no graph.

## Front-end requirements (agent F)
1. Split the current single-file page into the files above. Keep every current feature working: KPIs, usage timeline with zoom,
   alternatives comparison (used vs oversubscription) with provider/type filters, click to select an alternative, breakdown charts
   (TJob x phase, phase x object, by phase, by object), table. Charts use ECharts only.
2. **Header**: the GIIS logo (inline SVG, height about 44 px) on the left of the title **"Perfil de Uso de Recursos"** (key
   `app.title`; English "Resource Usage Profile"). The logo has dark and white parts: in dark theme put it on a light rounded
   badge so it stays readable. Language and theme selectors live in the header, on the right.
3. **Footer** exactly as in the plan above (horizontal rule + centered inline `giis-footer.svg` wrapped in a link to
   https://giis.uniovi.es/ opening in a new tab with `rel="noopener"`). The footer logo is also dark: give it a white rounded
   background in both themes.
4. **i18n** (`I18n.t(key, params)`; `data-i18n` / `data-i18n-title` attributes in the HTML): languages **es, en, fr, de**; default
   from `navigator.language` (fallback `en`), remembered in `localStorage` (always inside try/catch: it may be unavailable on
   `file://`). Translate everything the user sees: title, KPIs, section titles, filters, table headers, legends, tooltips, axis
   names, graph panel, buttons, phase names (the data keys stay as they are, only labels change), number/date formatting through
   `toLocaleString(lang)`. Changing language re-renders everything without reloading.
5. **Theme**: selector Auto / Light / Dark (default Auto = `prefers-color-scheme`), remembered; implemented with
   `html[data-theme="light"|"dark"]` CSS variables; ECharts colors are read from the theme at render time and every chart is
   re-rendered when the theme changes. Both themes must look good (contrast, grid lines, tooltips).
6. **Scenario explorer** (try combinations of providers and tune the charts): in the alternatives table each row has a checkbox
   to include it in a "compare" selection (select all / none / only cheapest per provider helpers); filters by provider, type and
   region stay. Chart controls: *show oversubscription* on/off, metric *USD* or *% of the cheapest alternative*, sort by *cost* /
   *provider* / *oversubscription*, and a "compare selected" chart that stacks the cost of every selected alternative by phase (or by
   TJob; a small switch) so providers can be compared side by side. All the controls re-render without reloading.
7. **Execution graph** (card hidden if `graph` is null): an ECharts `graph` series, `layout: "none"`, nodes placed in columns by
   stage (x) and spread vertically inside a stage (y), directed edges predecessor -> node with arrows, node colour by stage,
   label = id. **Click a node** to open a side panel (not a modal) with: TJob id and stage, resources, **the list of tests executed
   (scrollable, `Class#method`)** with their count, measured setup / test execution / teardown durations and peak CPU / memory
   (show a dash for `null`), and the cost of that TJob in the currently selected alternative (sum of the items with that `tjob`,
   with a breakdown by phase). The selected node is highlighted; the panel refreshes when another alternative is selected or the
   language changes. Clicking the background closes the panel.
8. **Deep links** (needed for testing and useful for users): `App` reads `location.hash` as `#lang=es&theme=dark&node=tjobg&alt=<id>`
   and applies it at start.
9. Responsive down to about 700 px wide; keyboard accessible controls (labels, focus outline, `aria-label` on icon-only buttons).
10. Style: plain readable ES2015 (no build step, no frameworks, no TypeScript); small functions; comments only where non-obvious;
    no duplicated blocks; constants for colours and phase order in `format.js`.

How to test without the Java side: assemble the page with a throwaway Python script (kept in the scratchpad, never in the repo)
that performs the replacements above using `C:\Users\augus\AppData\Local\Temp\claude\9b566184736217ee\6101f171-fa1e-4f81-8ed5-aeb133495985\scratchpad\sample-data.json`
as the JSON (it contains 8 alternatives, a 10-node graph and real measured data), then render with headless Edge:
`msedge.exe --headless --disable-gpu --window-size=1300,2600 --virtual-time-budget=4000 --screenshot=<png> "file:///...#lang=es&theme=light&node=tjobg"`
and read the PNG. Check the console for errors with `--enable-logging=stderr --v=0` (look for `CONSOLE`/`Uncaught`). Verify es/en/fr/de,
light/dark, the graph with a node selected, and the compare chart.

## Java requirements (agent J)
Files owned: everything under `retorch-profiling/src/main/java/.../dashboard/` (incl. `dto/`), `main/`, the Java tests, and the profiling specs.
1. DTOs: `dto/GraphNode` (id, stage, resources, tests, predecessors, and nullable `Double` setupSec, execSec, teardownSec, peakCpu,
   peakMemGb), `dto/ExecutionGraph` (nodes). `UsageProfileData` gets a nullable `graph` field (constructor adds the parameter).
2. `ExecutionPlanReader` reads `.retorch/executionplan.json` (format above) with Jackson into plan nodes.
3. `ExecutionMeasurements` exposes per-TJob metrics: setup / test execution / teardown duration in seconds (from the phase windows)
   and the peak CPU (cores) and memory (GB) of that TJob over the timeline.
4. A small builder merges plan nodes and metrics into `ExecutionGraph` (TJobs of the plan without measurements keep nulls; the plan
   is the source of truth for which nodes exist).
5. `UsageProfileGenerator.generate(planName, timeline, report, catalog, graph /*nullable*/, output)` assembles the page exactly as in the
   contract (list of JS files in a constant, in the given order). Keep behavior otherwise.
6. `UsageProfilerToolBox` / `UsageProfileMain`: optional plan JSON (7th argument); wire the reader, the builder and the generator.
7. Tests (`UsageProfileTests`): plan reader, metrics, graph merge, JSON `graph` null and non-null, assembled HTML has no leftover
   placeholder, includes the title text key `app.title`, both logos inline, the six scripts in order, no `src="http`. Update specs
   `.claude/specs/profiling/usageprofile.md` and `main.md`.

## Rules for every agent
- Java 8 only on the Java side. Touch only your files. Do not edit the contract; if it is wrong, say so in the report.
- Avoid very long bash heredocs (they fail in this environment): use the Write/Edit tools. Beware of escapes when writing Java or JS
  through Python (a literal backslash-n inside a Java string must stay two characters).
- The module may not compile until both agents finish: do not run the module-wide Maven build while the other works; check Java with
  `javac` on your own files (classpath from `mvn -q -pl retorch-profiling dependency:build-classpath -Dmdep.outputFile=<scratchpad file>`,
  output classes into the scratchpad).
- Final report: what changed, what the lead must adapt, what could not be verified.
