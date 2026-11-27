# Refactor plan: dashboard generation (retorch-profiling, package `giis.retorch.profiling.usageprofile`)

## Problem
- `UsageProfileGenerator` builds the JSON of the page by hand with Jackson `ObjectNode`/`ArrayNode` (string keys everywhere,
  no types).
- `CostCalculator` returns mutable nested classes (`Report`, `Alternative` with a `Map<String, Item>` keyed by a
  concatenated string, `Item` with a mutable `cost`) that the generator then walks.
- No type describes "what the page receives", so the contract with `resources/usageprofile/usageprofile.html` only lives in
  the generator code.

## Target design
Immutable DTOs in `giis.retorch.profiling.usageprofile.dto` (**already created, they are the shared contract, do not change
their public fields**):

| DTO | Role |
|---|---|
| `CostItem(object, tjob, phase, cost)` | one billed (cloud object, TJob, phase) combination |
| `Alternative(id, provider, kind, label, region, hourlyPrice, items)` | one VM / container alternative; `total` is computed from the items; `KIND_VM`, `KIND_CONTAINER` |
| `CostReport(peakCpu, peakMemGb, peakDiskGb, durationSec, alternatives, warnings)` | output of the cost calculation, alternatives sorted cheapest first |
| `TimelineSeries(t, cpu, memGb, diskGb)` | downsampled total usage over time |
| `UsageProfileData(plan, pricesFetchedAt, report, timeline)` | exactly the JSON embedded in the page |

Pipeline after the refactor:
`ExecutionMeasurements.Timeline` + `PriceCatalog` -> `CostCalculator.calculate` -> `CostReport`
-> `UsageProfileGenerator` builds `TimelineSeries` + `UsageProfileData` -> Jackson `writeValueAsString` -> HTML.

**JSON compatibility is a hard requirement**: the serialized property names and structure must stay exactly what the
current page reads (`plan, pricesFetchedAt, durationSec, peakCpu, peakMemGb, peakDiskGb, warnings,
alternatives[{id, provider, kind, label, region, hourlyPrice, total, items[{object, tjob, phase, cost}]}],
timeline{t, cpu, memGb, diskGb}`). `usageprofile.html` is not modified.

## Work packages (disjoint files, run in parallel)
| Agent | Owns (only these files) | Task |
|---|---|---|
| A | `usageprofile/CostCalculator.java` | `calculate` returns `CostReport`. Delete the nested `Item`/`Alternative`/`Report`. Build the items with a small private accumulator (e.g. a `LinkedHashMap<String,double[]>`/inner helper) and create the immutable `Alternative`/`CostItem` at the end. Same results as today (cost model unchanged); simplify the code where possible (shorter methods, fewer parameters, no duplicated blocks). |
| B | `usageprofile/UsageProfileGenerator.java` (+ new `usageprofile/TimelineSampler.java` if useful) | `generate(String planName, Timeline timeline, CostReport report, PriceCatalog catalog, Path output)`. Build `TimelineSeries` (average into at most 600 points, 3-decimal rounding, as today) and `UsageProfileData`; serialize with Jackson (no `ObjectNode`). Keep the HTML templating (placeholders `/*CHARTJS*/`, `/*DATA*/`, `</` escaping). Expose a package-private `static String toJson(UsageProfileData)`. |
| C | `src/test/.../dashboard/UsageProfileTests.java`, `.claude/specs/profiling/usageprofile.md` | Adapt the tests to the new API (`CostReport`, `Alternative.items` is now a list of `CostItem`; keep every assertion's meaning). Add a JSON-contract test: generate a dashboard and check that the embedded JSON has exactly the property names listed above (this protects `usageprofile.html`). Update the spec (components section: DTOs and pipeline). |
| Lead (not delegated) | `main/UsageProfilerToolBox.java`, integration, full test run | wire `CostReport` through the toolbox, compile, run all tests, fix mismatches between the three pieces. |

## Rules for every agent
- Java 8 only (no `var`, `List.of`, records). Follow the existing style. Do not add dependencies.
- Touch only the files you own. Do not edit the DTOs; if the contract seems wrong, say so in your final report instead.
- Do not run the module-wide Maven build while the others are still working (it cannot compile until all packages are
  done). To check your own file use `javac` on it with the module sources on the source path.
- Behavior must not change: same costs, same JSON. Per the repo iron rule (`CLAUDE.md`) specs must reflect the change
  (agent C does it).
- Final report: what changed, anything the lead must adapt, anything you could not verify.

## Done when
`mvn -pl retorch-profiling -am test` is green, the JSON-contract test passes, a dashboard generated from the real run
(`testdata/usageprofile/real-run.csv`) renders as before, and the spec matches the code.
