# Spec: `giis.retorch.profiling.usageprofile.report`

## Purpose
Assembles the usage profile of one execution: the measured usage, the cost report and the execution graph are turned into the JSON
that the page reads, and that JSON plus the page resources become **one self-contained HTML file**. The page itself (front-end) is
specified in [usageprofile.md](usageprofile.md); the page resources stay in `src/main/resources/usageprofile` and are loaded by
absolute classpath name (`usageprofile/...`), independent of the Java package.

## Public API
- `UsageProfileGenerator` (the only public class): `generate(planName, timeline, report, catalog, graph[, sut], output)`. Its caller is
  `main.UsageProfilerToolBox`, which passes `SutInfoDTO.detect()`. Package-private: `toJson`, `appScripts()`, `LANGUAGES`, `SCRIPT_LIST`.

## Pipeline
`measurement.Timeline` + `pricing.PriceCatalog` -> `cost.CostCalculator.calculate` -> `dto.cost.CostReportDTO`; the generator builds a
`dto.measurement.TimelineSeriesDTO` and a `dto.report.UsageProfileDTO` (with the optional `dto.plan.ExecutionGraphDTO`, nullable, and the `SutInfoDTO`) -> Jackson JSON
embedded in the HTML.

## Page assembly
- `generate(plan, timeline, report, catalog, graph[, sut], output)` (`graph` nullable; `UsageProfilerToolBox` passes `SutInfoDTO.detect()`): one self-contained HTML
  (no external requests). It fills `resources/usageprofile/usageprofile.html` by replacing `/*CSS*/` with `usageprofile.css`, `/*ECHARTS*/`
  with Apache ECharts 5.6.0 (`echarts.min.js`, Apache License 2.0, its licence header is kept), `/*APP*/` with the
  scripts listed in `js/scripts.txt` (one file name per line, relative to `js/`, in the order they must run; blank lines and `#` comments ignored; `UsageProfileGenerator.appScripts()`), joined with a newline: one
  script, one global scope. A new `.js` file must be added to that list: a test fails when a `.js` under `js/` is missing or listed twice, or a listed file does not exist (the list is `i18n, format, html, theme, icons, model/profile, model/simulation, model/state, model/hash, charts, views/common, views/header, views/usage, views/graph, views/panel, views/alternatives, views/table, views/compare, views/detail, views/simulator, controller, app`), `/*I18N*/` with the translations (one JSON object `{"es":{...},"en":{...},"fr":{...},"de":{...}}` built from `resources/usageprofile/i18n/<lang>.json` for the languages of `UsageProfileGenerator.LANGUAGES`, `</` escaped as `<\/`; the page reads it from `<script type="application/json" id="i18n">`), `/*ICONS*/` with the icons of the providers (one JSON object `{"<provider label>":"<svg>"}`, `</` escaped as `<\/`, read by the page from `<script type="application/json" id="icons">`; only the providers present in the alternatives of the report, so unused icons do not enlarge the page; the mapping provider label -> file is `resources/usageprofile/providers/icons.txt`, one `Provider label=file.svg` per line, blank lines and `#` comments ignored, e.g. `AWS=aws.svg`, `AWS Device Farm=aws.svg`, `Azure=azure.svg`, `GCP=gcp.svg`, `Oracle Cloud=oracle.svg`, `IBM Cloud=ibm.svg`; a provider without a line has no icon; the XML prolog and DOCTYPE of the svg are removed and its line breaks collapsed; replaced before `/*DATA*/` like `/*I18N*/`; **new provider icon: 1. add `providers/<id>.svg`, 2. add a line to `icons.txt`**), `<!--HEADER_LOGO_SVG-->` with `giis-logo.svg`, `<!--FOOTER_SVG-->` with `giis-footer.svg`, `/*FAVICON*/` with the
  Base64 of `giis-logo.svg` (page icon as a data URI) and
  `/*DATA*/` with the JSON (replaced last so user data is never scanned for placeholders). `</script` is escaped as
  `<\/script` in the inlined scripts and `</` as `<\/` in the JSON. The timeline is averaged into at most 600 points (values
  rounded to 3 decimals). The page itself (front-end: i18n es/en/fr/de, light/dark theme, scenario explorer, execution graph with
  per-TJob panel, deep links) is specified by its contract with the JSON below; it shows usage timeline (zoom), comparison of
  alternatives, breakdowns, table, price date and warnings.

## JSON contract
- JSON contract with the scripts in `resources/usageprofile/js` (`UsageProfileDTO` serialized by Jackson; the page is not
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


## Tests
- `UsageProfileGeneratorTests`: JSON contract names (including `meta`, `graph` null and non-null), timeline downsampled to at most 600 points,
  `reservations` in the JSON, `containers` in the graph nodes, assembled page without leftover placeholders and with both logos and the data-URI
  icon (the only `<link>`), scripts inlined in the order of `scripts.txt`, missing plan file means no graph, one self-contained file, `sut` in the JSON.
- `UsageProfilePageTests`: the fixture page for the smoke test; every `.js` under `js/` is listed exactly once in `scripts.txt`, in order, and every listed file exists; every language file has exactly the keys, non-empty texts and placeholders of English, and the files are the listed languages; the page carries the icons of exactly the providers of its alternatives under `/*ICONS*/` (none left unreplaced), `icons.txt` only references existing svg files that are well-formed XML and every svg of the folder is used; the page carries the translations of every language under the `/*I18N*/` placeholder and none is left unreplaced). Shared helpers are in `usageprofile.TestFixtures`.
- The network is never used in tests.
