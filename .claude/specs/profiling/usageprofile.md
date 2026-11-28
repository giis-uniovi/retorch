# Spec: `giis.retorch.profiling.usageprofile` (+ `src/main/resources/usageprofile`)

## Purpose
Builds the interactive usage-profile and cost usage profile of one execution: measured CPU/memory/disk, and the cost of
every deployment alternative split by TJob, lifecycle phase and cloud object. This file specifies the **front-end** (the page
and its scripts under `src/main/resources/usageprofile`). The Java side is split by functionality, one spec per package:

| Package | Spec | What it does |
|---|---|---|
| `usageprofile.measurement` | [measurement.md](measurement.md) | reads the usage CSV, resamples it into a timeline, per-TJob and per-container metrics |
| `usageprofile.pricing` | [pricing.md](pricing.md) | price catalog and the providers (Azure, AWS, Device Farm), cache |
| `usageprofile.cost` | [cost.md](cost.md) | cost of every alternative (VM, container service, browser service) |
| `usageprofile.plan` | [plan.md](plan.md) | execution plan reader and execution graph |
| `usageprofile.report` | [report.md](report.md) | assembles the self-contained HTML; **JSON contract** between Java and the page |
| `usageprofile.dto` | [dto.md](dto.md) | immutable data exchanged between the packages and written as the page JSON |

The page reads the JSON documented in [report.md](report.md); renaming a field there breaks the page.

## Front-end (`resources/usageprofile/`)
Output rule: the profile is **one self-contained HTML file** (CSS, JS, ECharts, data and both logos inlined; no external
request; the only URL is the footer link to the group site; the only `<link>` is the page icon, the GIIS logo as a data URI). A test enforces it.

Source layout (MVC, plain ES2015, no build step, IIFE namespaces; `usageprofile.html` holds the structure and the placeholders,
`usageprofile.css` the themes through CSS variables). The page is one inline script made of the files of `js/` in this order:

| Layer | File | Responsibility |
|---|---|---|
| infra | `i18n.js` (`I18n`: logic only, `load(texts)`; the texts are in `i18n/<lang>.json`, flat `key: text`, one per language, inlined by the generator), `format.js` (`Fmt`: formats, patterns, palettes), `html.js` (`Html`), `theme.js` (`Theme`) | translations; locale formats; `Html.t` tagged template that escapes every interpolated value (`& < > " '`), `Html.raw` for trusted fragments, `Html.join`, `Html.set` (no HTML is concatenated by hand: translations and data are escaped alike); light / dark / auto and `Theme.colors()` |
| Model | `model/profile.js` (`Profile`) | read-only queries over the JSON, no DOM and no translations: alternatives with filter, `sorted`, `cheapest`, `idle`, `label`, `costBy` / `matrix` / `costTable` by phase, TJob and cloud object, `chartRows` in USD or % of the cheapest, `ganttLayout`, `span`, `duration`, `tjobStorage`, `tjobItems`; the lists of phases, kinds and objects come from `data.meta` (names, or objects with `label` / `id` / `name`), with the previous hard-coded lists as fallback when the JSON has no `meta` |
| Model | `model/simulation.js` (`Simulation`) | the projection formulas, `compute`, `breakEvenRows`, `curves`, limits and defaults; pure |
| Model | `model/state.js` (`State`) | the only mutable state, plain data: `lang, theme, selectedAlt, node, showOver, metric, sort, cmpGroup, group, contMetric, compared` (ids in the order of the alternatives), `filters {offProviders, kind, region}` and `sim {runs, days, pattern, hours, disc, buy}`; `create(ctx)` gives `get()`, `update(patch)` that validates every value against what the page has (unknown ids, enum values and out of range numbers are ignored; `filters` and `sim` merge key by key) and returns the changed keys, `subscribe(fn)`; also `defaults(ctx)`, `sanitize`, `resolve` |
| Model | `model/hash.js` (`Hash`) | `parse(hash, ctx)` and `serialize(state, ctx)`, the only code that knows the URL format |
| View | `charts.js` (`Charts`) | ECharts builders (a fresh instance per render), shared time axis (`timeFrame`, `timeAxis`, `timeZoom`, `linkZoom`), `resize(id)`, `dispose(id)`; holds no page state |
| View | `views/common.js` (`View`) | small HTML builders and `syncSelect` / `syncSegment`, which synchronise a control without rebuilding it when its texts did not change |
| View | `views/header.js`, `usage.js`, `graph.js`, `panel.js`, `alternatives.js`, `table.js`, `compare.js`, `detail.js`, `simulator.js` (`HeaderView`, `UsageView`, `GraphView`, `PanelView`, `AlternativesView`, `TableView`, `CompareView`, `DetailView`, `SimulatorView`) | each is mounted once on its section (`mount(rootEl)`), renders from a ready view-model with `render(vm)` (and, when it helps, a partial render or `resize()`), writes only inside its own root, never reads the state or computes costs, and tags what the user can do with `data-act` |
| Controller | `controller.js` (`Controller`) | the only place where the model, the state and the translations meet: builds the view-model of each view; **delegated events**, one listener per view root (`click` and `change`; the controls carry `data-act`: `lang, theme, provider, kind, region, over, metric, sort, cmp-group, group, select-alt, pick, helper, select-node, close-panel, cont-metric`; a click on the row of an alternative selects it; the simulator form has its own debounced listener); each action becomes `State.update(patch)`; **renders only the views that depend on the keys that changed** (table `KEY_UNITS`: `selectedAlt` -> table, detail and the cost of the panel; `node` -> graph and panel; `compared` -> table and comparison; `filters` and `sort` -> alternatives and table; `lang` and `theme` -> everything; ...); keeps the selected alternative among the visible ones; `keepFocus` (focus returns to the equivalent control after a view is rebuilt), `announce` (one polite live region), Escape closes the TJob panel only when the focus is in the graph card or on the body; writes the hash after every change; applies a `hashchange`; on a resize the charts adapt at once (`Charts.resizeAll`, debounced 150 ms) and are drawn again only when the sizes of their texts change (root font size or breakpoint); starts in the order link -> one render -> events |
| bootstrap | `app.js` | `Controller.start(JSON.parse(#data))` |

CSS (`usageprofile.css`): one file in cascade layers declared as `@layer tokens, base, layout, components, charts, responsive, utilities, forced`, each a commented section (a later layer wins whatever the selectors, so the stylesheet has **no `!important`**; the container queries live in `responsive`, `.vh` and `.contents` in `utilities`, the Windows high-contrast rules in `forced`). **Tokens**: colours and sizes as custom properties on `:root` and `html[data-theme="dark"]`, one per line. The **chart palettes are tokens too** (`--phase-<phase>`, `--object-<object>`, `--series-<used|over|cpu|mem|disk>`, `--cycle-0..9`) and `Theme.colors()` reads them (cached until the theme changes) into `{phase, object, series, cycle}`, so a theme can adjust them; every palette colour keeps 3:1 against the card in both themes (WCAG 1.4.11), which needed lighter dark variants of `--cycle-3, 4, 6, 7, 8` only. Light theme and every state of the dark theme that does not use those five colours render pixel for pixel as before the split.

Cost panel of a TJob (`PanelView`): rendered apart from the Gantt chart: selecting another alternative only rewrites its cost section
(`renderCost`; the Gantt chart is not re-created and the open containers stay open); another resource for the chart per container only
redraws that chart. The files carry the GIIS notice; ECharts and the SVG logos are third party / assets.

The page must provide:
- **Order of the sections**: header; warnings and key figures; measured usage; execution graph (when there is a plan); alternatives (filters, options and chart); **compare selected alternatives, followed directly by the table of all the alternatives** (its checkboxes feed the comparison, so they sit next to it; the hint of the comparison says so in every language); breakdown of the selected alternative; projection; footer. The static `<title>` and `<h1>` of the HTML are `app.title` ("RETORCH Resource Usage Profile"), so the page is right before the script runs; `<html lang>` is `en` until the script sets the language of the user (the script runs at the end of the body, before the first paint, so no flash is expected and no second inline script is used).
- **Header** with a large inline GIIS logo, the title "RETORCH Resource Usage Profile" (key `app.title`, the same in every language),
  the **SUT** (name linking to its repository in a new tab, only when the URL is `https://`; `rel="noopener noreferrer"`) and the **branch**
  as pills under the title (hidden when unknown), and the language and theme selectors. **Footer**: horizontal rule and the centered group
  footer logo linking to https://giis.uniovi.es/ (same as `README.md`), which opens in a new tab with `rel="noopener noreferrer"` and a visually hidden text (name of the group and "opens in a new tab", translated) that gives the link its accessible name. The logos have no box behind them. The header logo keeps its
  original colours in both themes; the footer logo is recoloured per theme in CSS (white parts = page background, blue = `--logo-blue`,
  grey text = text colour). The page icon is the
  GIIS logo.
- **Languages** es, en, fr, de: texts in `i18n/<lang>.json`; every language has **exactly the keys of English** (nothing missing, nothing extra, no empty text, the same `{placeholders}`: a Java test and a JS test fail otherwise; a missing key would silently show English); default from `navigator.language`, remembered (`localStorage`, guarded); everything the user
  sees is translated and changing language re-renders without reloading. Backend warnings stay in English.
- **Theme** Auto / Light / Dark (default Auto = `prefers-color-scheme`), remembered; every chart re-renders on change.
- **Scenario explorer**: filters by provider, type and region; checkbox per alternative (select all / none / the 5 cheapest
  overall) feeding a "compare selected" chart that **starts preloaded with the 5 cheapest alternatives** (any other can be ticked or unticked; the type filter only lists the kinds present, including browser services) stacked by phase or by TJob; controls to show or hide oversubscription,
  metric USD or % of the cheapest, and sort by cost, provider or oversubscription. Click a bar or row to select an
  alternative and see its breakdown (TJob x phase, phase x cloud object, by phase, by object).
- **Execution graph** (hidden when `graph` is null): a Gantt-style DAG, x = time in seconds, one row per TJob ordered by
  stage; each TJob is a bar whose **length is proportional to its duration** and is painted in three segments proportional
  to its setup, test execution and teardown times (the phase colours used elsewhere); dependencies are not drawn (the panel lists
  the predecessors); TJobs without measurements are a dashed placeholder marked "no data". Rows are tall and the chart always uses
  the full width. Clicking a bar opens a panel **below the chart** (hidden until a TJob is selected, with a hint) with the TJob's
  stage, predecessors, resources, **the list of tests executed (`Class#method`)**, measured durations, peak CPU and memory, and its
  cost in the selected alternative by phase; it refreshes when the alternative or the language changes.
- **Shared time axis**: the usage chart and the execution graph always show the same time range (the longest of the two, from 0),
  have identical left and right plot margins (`Charts.timeFrame`), so the same x pixel is the same instant in both, and share the
  zoom window (zooming or moving one slider moves the other). Both x axes are value axes in seconds (`Charts.timeAxis`).
- **Per-container analysis** in the side panel of the selected TJob ("Resources of this TJob", from
  `graph.nodes[].containers`): one collapsed native `<details>` per container (summary: name and CPU / memory / disk peaks; body: image and
  CPU, memory (peak and average), writable layer, image, volumes and total storage), plus the storage of the whole TJob (layers +
  volumes + images, a shared image counted once) and the "chart per container" with a radio group to choose the resource:
  **storage** (stacked: layer, image, volumes), **memory** or **CPU** (grouped bars: peak and average). The chart is always shown
  (never collapsed), right after "Tests executed" and before "Measurements" (absent when the TJob has no containers). The chosen
  resource is kept while moving between TJobs. Peaks of different containers are not additive, so the
  panel labels the sum of the peaks apart from the measured peak of the TJob. Empty state when `containers` is empty or absent.
  The disk figures of the page include images and volumes when the file records them.
- **Projection simulator** (section "Projection", driven by the data already in the page): inputs runs per day, days per month,
  pattern (`perRun` = only while the suite runs, `always` = VM on 24x7 = 730 h/month, `hours` = VM on N hours per day), extra
  discount % on the on-demand compute price and the purchase option (on-demand or one of the reservations found in
  `alternatives[].reservations`; reservations are 24x7 commitments, so they apply to `always` and `hours`). Per alternative, with
  `perRunCost = alt.total`, `storageRun` the storage items and `T = durationSec`: `perRun` monthly = runs x days x (compute x
  (1 - discount) + storage); `always` = hourly x (1 - discount) x 730 + storage x 730 h (reserved: effective hourly x 730 + the same
  storage); `hours` = hourly x (1 - discount) x hours x days + storage x 730 h. Container services are always pay per use.
  A VM in `always`/`hours` reports its utilisation (busy hours per day / available hours); over 100 % it is marked "does not
  fit" and is not ranked as cheapest. Outputs: a results table (monthly and annual cost), the **break-even** runs per day from
  which reserving is cheaper than paying per execution (`reserved monthly / (days x perRunCost)`, compared with the maximum
  runs per day the VM can run, `floor(86400 / T)`), and a chart of monthly cost against runs per day (the four cheapest
  alternatives, on-demand per run, on-demand always-on and reserved always-on). Inputs are validated (clamped, `aria-invalid`)
  and the formulas are pure functions (`Simulation.calc`). Without reservations only on-demand is offered.
- **Deep links** through `location.hash`, **read and written for every key of the state** (`model/hash.js`): `lang=es|en|fr|de`, `theme=auto|light|dark`, `alt=<alternative id>`, `node=<tjob id>`, `over=0|1`, `metric=usd|pct`, `sort=cost|provider|over`, `cmpgroup=phase|tjob`, `cmp=all|none|cheapest` or `cmpids=<id>,<id>...` (the compared alternatives; `,` and `%` inside an id are escaped as `%2C` / `%25`), `group=phase|object` (tab of the breakdown), `cont=storage|mem|cpu` (chart per container), `noprov=<provider>,...` (hidden providers), `kind=<kind>`, `region=<region>`, `runs=`, `days=`, `pattern=perRun|always|hours`, `hours=`, `disc=`, `buy=<termYears>-<payment>|od`. Invalid or unknown values are ignored. A key is written only when it differs from the default state (the language of the browser, theme auto, the cheapest alternative selected, the 5 cheapest compared, no filter, simulator defaults); `parse(serialize(state))` gives the state back. After every change the page rewrites the hash with `history.replaceState` (no new history entry, no `hashchange`), so a copied URL reproduces the selected alternative and TJob, the comparison, the filters and the simulator. A `hashchange` (the user edits or follows a link) takes what the link says and the default for what it does not (the user's language and theme stay) and re-renders. A language or theme given by a link is applied but not remembered. Start order: read the link, **one** render, then bind the events (the link never renders half a page).
  The rules apply to `parse(hash, ctx)` with `ctx = {langs, themes, altIds, nodeIds, providers, kinds, regions, cheapestIds, buyKeys, lang, defaultLang}`.

## Tests
The Java tests of each package are listed in its own spec; the page assembly tests are in [report.md](report.md).

### JS tests (dashboard)
Not part of the default build (no Node needed for `mvn test`): they run with `mvn test -pl retorch-profiling -am -Pjs-tests`
(`exec-maven-plugin`: `npm install`, `node --test`, smoke test; Node 22 or later, Chrome or Edge) or by hand from
`retorch-profiling/src/test/js` (`npm install`, `npm test`, `node smoke.mjs [page] [--full] [--shots dir]`). Everything under
`src/test/js` is test code and carries the GIIS notice except `package.json` / lock file (data).
- `UsageProfilePageTests.testFixturePageForTheBrowserSmokeTestIsWritten` writes `target/usageprofile-smoke/usageprofile.html` from the
  fixtures `usage-containers.csv` + `executionplan.json` with a catalog of two providers, three regions, reservations, a container
  service and two browser services, a fixed SUT (`petclinic`, `main`) and plan `FixturePlan`: it is deterministic.
- `*.test.mjs` (`node --test`): pure logic, loading the scripts of `js/` in a `vm` context with browser stubs
  (`helpers/load.mjs`): formats and escaping (`format.test.mjs`), translations (`i18n.test.mjs`: key, placeholder and text parity of the four languages, every literal key used by the scripts and the page exists in English, fallback to English and then to the key), the theme and the stylesheet (`theme.test.mjs`: `Theme.colors()` reads the palettes from the custom properties and caches them, the palettes Theme reads are the ones the CSS declares, every palette colour keeps 3:1 against the card in light and dark, tokens one per line, no `!important`, the eight layers exist), HTML escaping with `<"'&>` (`html.test.mjs`) the state (`state.test.mjs`: defaults, validation, merge, subscribers, `resolve`) and the hash (`hash.test.mjs`: `parse(serialize(state))` for every key, only non-default keys written, comparison as `all` / `none` / list with escaped ids, old keys, invalid values and bad escapes), the model queries (`profile.test.mjs`: filters, costs by phase / object / TJob, chart rows, Gantt layout with placeholder, shared image counted once, `meta` fallback) and the simulator formulas (`simulation.test.mjs`).
- `smoke.mjs`: opens the fixture page in headless Chrome / Edge through the DevTools protocol (no dependency but `axe-core`; it is
  skipped, exit 0, when there is no browser unless `SMOKE_REQUIRE=1`; `BROWSER` and `AXE_CORE` override the executables). Checks: no
  exception or `console.error`; every chart, table and the list of TJobs render; title and header; usage chart and execution graph
  have identical plot areas (x0/x1 in page pixels) before and after selecting a TJob, after zooming and at 390 px, and the zoom is
  linked both ways; the TJob panel is below the chart; the metric selector of the container chart; the keyboard focus stays on the
  control after a re-render; Escape closes the panel; a small resize only adapts the charts (they are not re-created) and the axes stay aligned after any resize; a `hashchange` applies the new link; the static title and h1 are `app.title`, the table of all the alternatives is right below the comparison, the footer link has `noopener noreferrer` and the new-tab note; a deep link restores the state; selecting an alternative does not re-create the Gantt chart and keeps the open containers open; every change made through the controls is written to the hash and reopening that hash restores the same page and the same hash; axe-core (WCAG 2.x A/AA and best practices) finds
  0 violations in light and dark (`--full`: also es, fr, de); no horizontal scroll on a phone. `--shots <dir>` saves
  `wide|narrow-light|dark.png` (deterministic) to compare two versions of the page byte by byte.
