# Spec: `giis.retorch.profiling.main`

## Purpose
Entry points of the profiling module. The module only generates the usage profile as an HTML usage profile; the former
PDF/chart profile generation (dataset averaging, profile generator and plotter, COI profiles, PDF report, Cloud Object
Instance JSON model) has been removed.

## Public API
- `UsageProfilerToolBox.generateUsageProfile(planName, usageCsv, outputHtml, PriceSource)` and the overload taking a ready
  `PriceCatalog`, each also with an extra optional `executionPlanJson` argument (path of `.retorch/executionplan.json`; null or
  a file that does not exist means the usage profile has no execution graph): builds the interactive usage profile from the single usage file (see `profiling/usageprofile.md`).
  `PriceSource` holds the regions (comma separated lists), the price cache file and its maximum age in hours.
- `UsageProfileMain` (Main-Class of `retorch-profiling-<version>-cli.jar`, built by the shade plugin with the `cli`
  classifier):
  `java -jar retorch-profiling-<version>-cli.jar <planName> <usageCsv> <outputHtml> <azureRegions> <awsRegions> <priceCacheFile> [executionPlanJson]`.
  The 7th argument is optional. Wrong argument count (not 6 or 7): usage error logged, exit code 1. The price cache lifetime is 24 hours.

## Dependencies
No dependency on the other modules. Jackson, commons-csv and slf4j; `logback-classic` is a runtime dependency so the
CLI jar logs.

## Tests
`UsageProfileTests`.
