# Spec: `giis.retorch.profiling.main`

## Purpose
Entry points of the profiling module. The module only generates the usage profile as an HTML usage profile; the former
PDF/chart profile generation (dataset averaging, profile generator and plotter, COI profiles, PDF report, Cloud Object
Instance JSON model) has been removed.

## Public API
- `UsageProfilerToolBox.generate(UsageProfileRequest)`: the single entry point; builds the interactive usage profile from the single usage file (see `profiling/report.md`, `profiling/cost.md` and `profiling/measurement.md`).
  `UsageProfileRequest` (immutable, `UsageProfileRequest.builder(planName, usageCsv, outputHtml)`) carries the prices (`prices(PriceSource)` or
  `prices(PriceCatalog)`; one of them is required, else `IllegalStateException`), the optional `executionPlan(path)` (path of `.retorch/executionplan.json`;
  null or a file that does not exist means the usage profile has no execution graph, while an existing file with an empty plan gives a graph without nodes) and the
  `browserResourcePattern` (case-insensitive text a resource name of the plan contains to make its TJob a browser user; default `browser`, taken from the system
  property `usageprofile-browser-resources` when the builder is created, overridable with `browserResourcePattern(...)`, blank = default).
  `PriceSource` holds the regions (comma separated lists; `PriceSource.builder().regions(providerId, ...)` for any registered price provider), the price cache file and its maximum age in hours.
  The four `generateUsageProfile(...)` overloads (PriceSource or PriceCatalog, with or without `executionPlanJson`) are `@Deprecated` and delegate to `generate`.
- `UsageProfileMain` (Main-Class of `retorch-profiling-<version>-cli.jar`, built by the shade plugin with the `cli`
  classifier):
  `java -jar retorch-profiling-<version>-cli.jar <planName> <usageCsv> <outputHtml> <azureRegions> <awsRegions> <priceCacheFile> [executionPlanJson]`.
  It builds a `UsageProfileRequest`. The 7th argument is optional. Wrong argument count (not 6 or 7): usage error logged, exit code 1. The price cache lifetime is 24 hours.

## Dependencies
No dependency on the other modules. Jackson, commons-csv and slf4j; `logback-classic` is a runtime dependency so the
CLI jar logs.

## Tests
`UsageProfileRequestTests` (default and property of the browser pattern, request without prices rejected, browser TJobs by pattern, missing plan file = no graph vs empty plan = empty graph).
The end-to-end paths of the toolbox are covered by `report.UsageProfileGeneratorTests`.
