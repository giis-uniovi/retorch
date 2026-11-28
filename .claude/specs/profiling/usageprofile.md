# Spec: `giis.retorch.profiling.usageprofile` (+ `src/main/resources/usageprofile`)

## Purpose
Builds the interactive usage-profile and cost usage profile of one execution: measured CPU/memory/disk, and the cost of
every deployment alternative split by TJob, lifecycle phase and cloud object.

## Inputs
- The single usage file exported by `resourcemonitor.sh export`, columns
  `kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes;image;image_bytes;volume_bytes` (the last three are optional: a file with only the first 9 columns, as older runs produced, is still read and has no container information):
  - `kind=phase`: lifecycle phase `item` (`tjob-setup`, `tjob-testexec`, `tjob-teardown`; `COI-setup` and `coi-teardown`
    under the TJob named `COI`) of a TJob, from `start` to `end`.
  - `kind=sample`: CPU (% of one core), memory and disk (bytes; the size of the writable layer) of the container `item` of a TJob at `start`.
  - `kind=container` (optional, one per container, written the first time it is seen): `item` = container, `start` = first seen, `image` = image name, `image_bytes` = size of the image, `volume_bytes` = total size of the volumes it mounts (0 if none); missing or empty values mean none.
  - Times above 1e11 are epoch ms, otherwise seconds. A TJob without its three phases, or a file without TJobs, is
    rejected with `IOException`.
- A `PriceCatalog` (USD).
- Optionally the execution plan written by the orchestrator, `.retorch/executionplan.json`:
  `{"tjobs":[{"id","stage","resources":[],"tests":["Class#method"],"predecessors":[ids]}]}` (TJob ids lower case; unknown
  properties ignored). The profiling module has its own small classes for it (no dependency on the orchestration module).

## Components
- `ExecutionMeasurements.read(usageFile).toTimeline()`: resamples at 1 second. Each container holds its last
  sample; a sample is assigned to the phase its TJob is in at that second (setup / testexec / teardown, by start
  boundaries). `Slice.coiPhase` is `COI-setup` or `coi-teardown` while the COI lifecycle is running.
  `Timeline.peak("cpu"|"mem"|"disk")` = highest simultaneous total (cores / GB / GB).
  **Storage rule** (`Load.diskGb`): per TJob and second, `sum(writable layer of its containers) + sum(volume_bytes) + sum(image_bytes of the DISTINCT images)`, for the containers that exist at that second (from their first appearance, the earlier of its `container` row and its first sample, held until the end of the TJob like the samples). A shared image counts once per TJob. Hence the peak disk, the provisioned volume and `diskGb` include images and volumes. Without `container` rows the storage is just the writable layers (old behaviour).
  `containerUsage()` -> `Map<lower case tjob, List<ContainerUsage>>` sorted by container name, only for TJobs that have `container` rows and samples (others are absent): peak and average (mean of the samples) CPU and memory, `layerGb` = peak writable layer, `imageGb`, `volumeGb` as recorded (the image counted fully even if shared), `storageGb` = their sum.
  `tjobMetrics(timeline)` -> `Map<lower case tjob, TJobMetrics>`: `startSec` (seconds from the timeline start to the start of the TJob set-up), setup / test execution / teardown seconds (from the phase
  windows: setup start -> testexec start -> teardown start -> teardown end) and the peak CPU (cores) and memory (GB) of that
  TJob over the timeline (`null` peaks if the TJob has no samples).
- `ExecutionPlanReader.read(file)` -> `List<PlanNode>` (id, stage, resources, tests, predecessors).
- `ExecutionGraphBuilder.build(planNodes, metrics[, containerUsage])` -> `ExecutionGraph`: the plan is the source of truth for the nodes; a TJob
  of the plan without measurements keeps `null` in the six measured fields; measured TJobs not in the plan are ignored; the `containers` of a node come from `containerUsage` (empty list if none).
- `PriceCatalog`: `schemaVersion` (constant `SCHEMA_VERSION`, now 3), `vms`, `containers`, `storage`, `browsers` (`BrowserRate(provider, region, service, name, minuteUsd)`: a browser or device rented by the minute), `warnings`, `fetchedAtEpochMs`, `regionsKey`; JSON
  load/save. `VmOffer.reservations` is a list of `ReservationOption` (empty if there is no data). A file without `schemaVersion`
  loads as 0.
- `PricingFetcher.fetchOrLoad(azureRegions, awsRegions, cacheFile, maxAgeMs)`: regions are comma separated lists, blank
  disables a provider. The cache is used only if younger than `maxAgeMs`, built for the same regions **and** with the current `schemaVersion` (an old
  cache is ignored and the prices are fetched again); if every
  provider fails and a cache exists, the cache is returned with a warning; if nothing is available it throws
  `IOException`. A failing provider/region only adds a warning.
  - `PriceSource(azureRegions, awsRegions, cacheFile, maxAgeHours).load()` bundles those four settings (used by the
    toolbox and `UsageProfileMain`).
  - Azure: Retail Prices API (public). VM sizes come from a fixed list in the class (the API gives no vCPU/RAM);
    Spot, Low Priority and Windows prices are ignored. Container Instances vCPU/GB-hour; Standard SSD E10 disk -> GB-month.
    Reservations: a second query with `priceType eq 'Reservation'` for the same sizes; each item has `reservationTerm`
    (`1 Year` / `3 Years`) and `retailPrice` = the total price of the whole term (`unitOfMeasure` is still `1 Hour`), so it
    becomes an `ALL_UPFRONT` option with `effectiveHourly = retailPrice / (years * 8760)`; the cheapest per (VM, term);
    other terms and Windows products are ignored. Azure publishes no hourly-paid (no upfront) reservation in this API.
  - AWS: Price List bulk files, parsed in streaming (EC2 is ~450 MB per region): current-generation Linux shared
    on-demand instances, EBS gp3, Fargate (x86 Linux). Reservations come from the same stream (`terms.Reserved`, which
    follows `OnDemand`; class `AwsReservations`): only `OfferingClass` = `standard`, `LeaseContractLength` `1yr`/`3yr`, and
    `PurchaseOption` `No Upfront` (dimension with unit `Hrs` -> `NO_UPFRONT`, `effectiveHourly` = that rate) or `All Upfront`
    (dimension with unit `Quantity` = upfront fee -> `ALL_UPFRONT`, `effectiveHourly` = fee / (years * 8760)); Partial Upfront
    and convertible terms are ignored. Only the skus of the accepted products are loaded (low memory).
  - AWS Device Farm (only when AWS is enabled; one global offer `AWSDeviceFarm`, fetched once whatever the regions; a failure only adds a
    warning): `parseDeviceFarm` keeps the metered on-demand per-minute products with a price > 0: Windows desktop browsers Chrome, Firefox and Edge
    (`*-Windows-<browser>-automated-minute`, `BrowserRate.name` = the browser) and `Android` / `iOS` metered device minutes (`name` = `Android devices`
    / `iOS devices`); free, trial, unmetered, remote-access, Internet Explorer and monthly-slot products are ignored. Region = `regionCode` of the product.
- Pipeline: `Timeline` + `PriceCatalog` -> `CostCalculator.calculate` -> `CostReport` -> `UsageProfileGenerator` builds a
  `TimelineSeries` and a `UsageProfileData` -> Jackson JSON -> embedded in the HTML.
- DTOs (package `giis.retorch.profiling.usageprofile.dto`): immutable, public final fields, no logic besides the total.
  They are the only types exchanged between calculator and generator, and their field names are the JSON contract.
  - `CostItem(object, tjob, phase, cost)`: one billed (cloud object `compute`|`storage`, TJob, phase) combination.
  - `Alternative(id, provider, kind, label, region, hourlyPrice, items)`: one VM, container or browser-service alternative; `kind` is
    `VM`, `CONTAINER` or `BROWSER_SERVICE`; `total` is computed from the items (the sum of items always equals the total). `reservations`
    (immutable list of `ReservationOption`, empty by default) is set with `withReservations(list)`, which returns a copy.
  - `ReservationOption(termYears, payment, effectiveHourly)`: `termYears` 1 or 3, `payment` `NO_UPFRONT` | `ALL_UPFRONT`
    (enum, serialized by name), `effectiveHourly` = USD per hour really paid 24x7, running or not (hourly rate, or upfront fee
    divided by `termYears * 8760`). Factory `allUpfront(termYears, fee)`.
  - `CostReport(peakCpu, peakMemGb, peakDiskGb, durationSec, alternatives, warnings)`: result of the calculation,
    alternatives sorted by total, cheapest first.
  - `TimelineSeries(t, cpu, memGb, diskGb)`: total usage over time as parallel lists of equal length.
  - `GraphNode(id, stage, resources, tests, predecessors, setupSec, execSec, teardownSec, peakCpu, peakMemGb, containers)`: one TJob; the
    six measured fields (incl. `startSec`) are nullable `Double`; `containers` is a never-null list of `ContainerUsage` (`[]` by default).
  - `ContainerUsage(name, image, peakCpu, avgCpu, peakMemGb, avgMemGb, layerGb, imageGb, volumeGb, storageGb)`: usage of one container of a TJob (cores / GB 1024^3), `storageGb = layerGb + imageGb + volumeGb`.
  - `ExecutionGraph(nodes)`.
  - `SutInfo(name, url, branch)`: SUT and branch shown in the header; empty strings when unknown, never null. `SutInfo.detect()` /
    `detect(env, workspace)` read `GIT_URL` (else `remote "origin"` of `<workspace>/.git/config`) and `BRANCH_NAME` (else `GIT_BRANCH` without
    `origin/`, else `<workspace>/.git/HEAD`); the workspace is `WORKSPACE` or the current directory. The URL is normalised to a plain
    `https://host/org/repo` (no credentials, no `.git`, `git@host:` and `ssh://` converted; anything else, e.g. a local path, gives an empty URL);
    the name is its last segment.
  - `UsageProfileData(plan, pricesFetchedAt, report, timeline, graph[, sut])`: exactly what the page receives; `graph` is nullable, `sut` defaults to `SutInfo.none()`.
- `CostCalculator.calculate(timeline, catalog[, browserTjobs])` -> `CostReport` (peaks, duration, alternatives sorted by total, warnings):
  - VM alternatives carry the reservations of the chosen VM (`Alternative.reservations`); container alternatives have none.
  - VM alternatives: per provider+region, the cheapest 3 VMs of distinct shape that fit the peak CPU and memory;
    none fits -> warning. Per second the VM costs `hourly/3600`; COI seconds are charged to that COI phase; otherwise
    split between running TJob phases by their share of the VM (the larger of CPU and memory share, normalized when the
    sum exceeds 1) and the unused part is `(idle)` / `oversubscription`. The volume is provisioned at
    `max(1, ceil(peak disk GB))` and split the same way with its prorated GB-month price (30-day month).
  - Container alternatives (one per catalog entry): pay-per-use `cpu*vcpuHour + memGb*gbHour` per second for each TJob
    phase; no oversubscription, COI phases and disk are not charged.
  - Browser services (one alternative per `catalog.browsers` entry, `kind` `BROWSER_SERVICE`, provider = the service, e.g. `AWS Device Farm`): the SUT
    keeps running on the **cheapest alternative of the report** (its items are copied and its label is part of the label: `Chrome + AWS <vm>`),
    plus one browser session per TJob of `browserTjobs` during its `tjob-testexec` phase, billed `minuteUsd / 60` per second under the cloud object
    `browser`. The measured browser containers (Selenoid/Chrome) are not subtracted. Without `browserTjobs` (or without any other alternative) no
    browser alternative is added and a warning says so. `UsageProfilerToolBox` takes `browserTjobs` from the execution plan: TJobs with a
    resource whose name contains `browser` (case-insensitive). The projection simulator treats these alternatives as pay-per-use.
  - Cost items are unique per (cloud object, tjob, phase). The sum of items equals the total.
- `UsageProfileGenerator.generate(plan, timeline, report, catalog, graph[, sut], output)` (`graph` nullable; `UsageProfilerToolBox` passes `SutInfo.detect()`): one self-contained HTML
  (no external requests). It fills `resources/usageprofile/usageprofile.html` by replacing `/*CSS*/` with `usageprofile.css`, `/*ECHARTS*/`
  with Apache ECharts 5.6.0 (`echarts.min.js`, Apache License 2.0, its licence header is kept), `/*APP*/` with the
  `js/*.js` files joined with a newline in the order `i18n, format, html, theme, model/profile, model/simulation, model/state, model/hash, charts, views/common, views/header, views/usage, views/graph, views/panel, views/alternatives, views/table, views/compare, views/detail, views/simulator, controller, app` (constant `APP_SCRIPTS`; one
  script, one global scope), `<!--HEADER_LOGO_SVG-->` with `giis-logo.svg`, `<!--FOOTER_SVG-->` with `giis-footer.svg`, `/*FAVICON*/` with the
  Base64 of `giis-logo.svg` (page icon as a data URI) and
  `/*DATA*/` with the JSON (replaced last so user data is never scanned for placeholders). `</script` is escaped as
  `<\/script` in the inlined scripts and `</` as `<\/` in the JSON. The timeline is averaged into at most 600 points (values
  rounded to 3 decimals). The page itself (front-end: i18n es/en/fr/de, light/dark theme, scenario explorer, execution graph with
  per-TJob panel, deep links) is specified by its contract with the JSON below; it shows usage timeline (zoom), comparison of
  alternatives, breakdowns, table, price date and warnings.
- JSON contract with the scripts in `resources/usageprofile/js` (`UsageProfileData` serialized by Jackson; the page is not
  generated from it, it reads these names, so renaming a field breaks the page):
  - top level: `plan, pricesFetchedAt, durationSec, peakCpu, peakMemGb, peakDiskGb, warnings, alternatives, timeline, graph, sut`;
  - `sut`: `{name, url, branch}` (strings, possibly empty);
  - each alternative: `id, provider, kind (VM|CONTAINER|BROWSER_SERVICE), label, region, hourlyPrice, total, items, reservations`;
  - each item: `object` (`compute`|`storage`|`browser`), `tjob, phase, cost`;
  - each reservation: `termYears` (1|3), `payment` (`NO_UPFRONT`|`ALL_UPFRONT`), `effectiveHourly` (USD/hour); the list is empty
    for container alternatives and for VMs without data;
  - `timeline`: `t, cpu, memGb, diskGb` (parallel arrays of equal length);
  - `graph`: always present, `null` without an execution plan, else `{nodes:[{id, stage, resources, tests, predecessors,
    startSec, setupSec, execSec, teardownSec, peakCpu, peakMemGb, containers}]}` (the six measured fields are numbers or `null`; `startSec` is relative to the timeline start; `containers` is an array, empty without container information, of `{name, image, peakCpu, avgCpu, peakMemGb, avgMemGb, layerGb, imageGb, volumeGb, storageGb}` sorted by name).

## Front-end (`resources/usageprofile/`)
Output rule: the profile is **one self-contained HTML file** (CSS, JS, ECharts, data and both logos inlined; no external
request; the only URL is the footer link to the group site; the only `<link>` is the page icon, the GIIS logo as a data URI). A test enforces it.

Source layout (MVC, plain ES2015, no build step, IIFE namespaces; `usageprofile.html` holds the structure and the placeholders,
`usageprofile.css` the themes through CSS variables). The page is one inline script made of the files of `js/` in this order:

| Layer | File | Responsibility |
|---|---|---|
| infra | `i18n.js` (`I18n`), `format.js` (`Fmt`: formats, patterns, palettes), `html.js` (`Html`), `theme.js` (`Theme`) | translations; locale formats; `Html.t` tagged template that escapes every interpolated value (`& < > " '`), `Html.raw` for trusted fragments, `Html.join`, `Html.set` (no HTML is concatenated by hand: translations and data are escaped alike); light / dark / auto and `Theme.colors()` |
| Model | `model/profile.js` (`Profile`) | read-only queries over the JSON, no DOM and no translations: alternatives with filter, `sorted`, `cheapest`, `idle`, `label`, `costBy` / `matrix` / `costTable` by phase, TJob and cloud object, `chartRows` in USD or % of the cheapest, `ganttLayout`, `span`, `duration`, `tjobStorage`, `tjobItems`; the lists of phases, kinds and objects come from `data.meta` (names, or objects with `label` / `id` / `name`), with the previous hard-coded lists as fallback when the JSON has no `meta` |
| Model | `model/simulation.js` (`Simulation`) | the projection formulas, `compute`, `breakEvenRows`, `curves`, limits and defaults; pure |
| Model | `model/state.js` (`State`) | the only mutable state, plain data: `lang, theme, selectedAlt, node, showOver, metric, sort, cmpGroup, group, contMetric, compared` (ids in the order of the alternatives), `filters {offProviders, kind, region}` and `sim {runs, days, pattern, hours, disc, buy}`; `create(ctx)` gives `get()`, `update(patch)` that validates every value against what the page has (unknown ids, enum values and out of range numbers are ignored; `filters` and `sim` merge key by key) and returns the changed keys, `subscribe(fn)`; also `defaults(ctx)`, `sanitize`, `resolve` |
| Model | `model/hash.js` (`Hash`) | `parse(hash, ctx)` and `serialize(state, ctx)`, the only code that knows the URL format |
| View | `charts.js` (`Charts`) | ECharts builders (a fresh instance per render), shared time axis (`timeFrame`, `timeAxis`, `timeZoom`, `linkZoom`), `resize(id)`, `dispose(id)`; holds no page state |
| View | `views/common.js` (`View`) | small HTML builders and `syncSelect` / `syncSegment`, which synchronise a control without rebuilding it when its texts did not change |
| View | `views/header.js`, `usage.js`, `graph.js`, `panel.js`, `alternatives.js`, `table.js`, `compare.js`, `detail.js`, `simulator.js` (`HeaderView`, `UsageView`, `GraphView`, `PanelView`, `AlternativesView`, `TableView`, `CompareView`, `DetailView`, `SimulatorView`) | each is mounted once on its section (`mount(rootEl)`), renders from a ready view-model with `render(vm)` (and, when it helps, a partial render or `resize()`), writes only inside its own root, never reads the state or computes costs, and tags what the user can do with `data-act` |
| Controller | `controller.js` (`Controller`) | the only place where the model, the state and the translations meet: builds the view-model of each view; **delegated events**, one listener per view root (`click` and `change`; the controls carry `data-act`: `lang, theme, provider, kind, region, over, metric, sort, cmp-group, group, select-alt, pick, helper, select-node, close-panel, cont-metric`; a click on the row of an alternative selects it; the simulator form has its own debounced listener); each action becomes `State.update(patch)`; **renders only the views that depend on the keys that changed** (table `KEY_UNITS`: `selectedAlt` -> table, detail and the cost of the panel; `node` -> graph and panel; `compared` -> table and comparison; `filters` and `sort` -> alternatives and table; `lang` and `theme` -> everything; ...); keeps the selected alternative among the visible ones; `keepFocus` (focus returns to the equivalent control after a view is rebuilt), `announce` (one polite live region), Escape closes the TJob panel only when the focus is in the graph card or on the body; writes the hash after every change; applies a `hashchange`; on a resize the charts adapt at once (`Charts.resizeAll`, debounced 150 ms) and are drawn again only when the sizes of their texts change (root font size or breakpoint); starts in the order link -> one render -> events |
| bootstrap | `app.js` | `Controller.start(JSON.parse(#data))` |

Cost panel of a TJob (`PanelView`): rendered apart from the Gantt chart: selecting another alternative only rewrites its cost section
(`renderCost`; the Gantt chart is not re-created and the open containers stay open); another resource for the chart per container only
redraws that chart. The files carry the GIIS notice; ECharts and the SVG logos are third party / assets.

The page must provide:
- **Header** with a large inline GIIS logo, the title "RETORCH Resource Usage Profile" (key `app.title`, the same in every language),
  the **SUT** (name linking to its repository in a new tab, only when the URL is `https://`; `rel="noopener noreferrer"`) and the **branch**
  as pills under the title (hidden when unknown), and the language and theme selectors. **Footer**: horizontal rule and the centered group
  footer logo linking to https://giis.uniovi.es/ (same as `README.md`). The logos have no box behind them. The header logo keeps its
  original colours in both themes; the footer logo is recoloured per theme in CSS (white parts = page background, blue = `--logo-blue`,
  grey text = text colour). The page icon is the
  GIIS logo.
- **Languages** es, en, fr, de: default from `navigator.language`, remembered (`localStorage`, guarded); everything the user
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
`UsageProfileTests` (timeline from synthetic and real Jenkins data, incomplete phases, VM and container cost conservation and oversubscription, no-fit warning, Azure/AWS parsers
Device Farm parsing (only metered browsers and devices, per-minute price), browser-service alternatives (cheapest alternative + browser minutes of the TJobs given, warning without TJobs), and streaming reader, cache and regions parsing, cache with another or no `schemaVersion` ignored, Azure reservations (all upfront per term), AWS `terms.Reserved` streaming (standard only, No/All Upfront, 1/3 years), `reservations` property in the JSON of VM and container alternatives, self-contained HTML, JSON contract property names incl. `graph` null / non-null, timeline downsampled to at most 600 points, plan reader, TJob metrics, graph merge with nulls, assembled page without leftover placeholders and with both logos and the data-URI icon (the only `<link>`), `SutInfo` from the Jenkins environment (credentials removed, ssh converted) and from `.git` files, `sut` in the JSON, the scripts in order and the `app.title` key, missing plan file means no graph, 12-column file with `container` rows: peak disk includes images and volumes, shared image counted once per TJob, per-container peaks/averages/storage, `containers` JSON property and field names, 9-column file yields `containers: []`). Fixtures `testdata/usageprofile/usage-containers.csv` (12 columns, container rows), `usage.csv` and `real-run.csv` (9 columns) and `executionplan.json` (TJobs of `usage.csv` plus a gateway node). Network is never used in tests.

### JS tests (dashboard)
Not part of the default build (no Node needed for `mvn test`): they run with `mvn test -pl retorch-profiling -am -Pjs-tests`
(`exec-maven-plugin`: `npm install`, `node --test`, smoke test; Node 22 or later, Chrome or Edge) or by hand from
`retorch-profiling/src/test/js` (`npm install`, `npm test`, `node smoke.mjs [page] [--full] [--shots dir]`). Everything under
`src/test/js` is test code and carries the GIIS notice except `package.json` / lock file (data).
- `UsageProfilePageTests.testFixturePageForTheBrowserSmokeTestIsWritten` writes `target/usageprofile-smoke/usageprofile.html` from the
  fixtures `usage-containers.csv` + `executionplan.json` with a catalog of two providers, three regions, reservations, a container
  service and two browser services, a fixed SUT (`petclinic`, `main`) and plan `FixturePlan`: it is deterministic.
- `*.test.mjs` (`node --test`): pure logic, loading the scripts of `js/` in a `vm` context with browser stubs
  (`helpers/load.mjs`): formats and escaping (`format.test.mjs`), HTML escaping with `<"'&>` (`html.test.mjs`) the state (`state.test.mjs`: defaults, validation, merge, subscribers, `resolve`) and the hash (`hash.test.mjs`: `parse(serialize(state))` for every key, only non-default keys written, comparison as `all` / `none` / list with escaped ids, old keys, invalid values and bad escapes), the model queries (`profile.test.mjs`: filters, costs by phase / object / TJob, chart rows, Gantt layout with placeholder, shared image counted once, `meta` fallback) and the simulator formulas (`simulation.test.mjs`).
- `smoke.mjs`: opens the fixture page in headless Chrome / Edge through the DevTools protocol (no dependency but `axe-core`; it is
  skipped, exit 0, when there is no browser unless `SMOKE_REQUIRE=1`; `BROWSER` and `AXE_CORE` override the executables). Checks: no
  exception or `console.error`; every chart, table and the list of TJobs render; title and header; usage chart and execution graph
  have identical plot areas (x0/x1 in page pixels) before and after selecting a TJob, after zooming and at 390 px, and the zoom is
  linked both ways; the TJob panel is below the chart; the metric selector of the container chart; the keyboard focus stays on the
  control after a re-render; Escape closes the panel; a small resize only adapts the charts (they are not re-created) and the axes stay aligned after any resize; a `hashchange` applies the new link; a deep link restores the state; selecting an alternative does not re-create the Gantt chart and keeps the open containers open; every change made through the controls is written to the hash and reopening that hash restores the same page and the same hash; axe-core (WCAG 2.x A/AA and best practices) finds
  0 violations in light and dark (`--full`: also es, fr, de); no horizontal scroll on a phone. `--shots <dir>` saves
  `wide|narrow-light|dark.png` (deterministic) to compare two versions of the page byte by byte.
