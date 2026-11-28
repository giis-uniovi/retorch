# Spec: `giis.retorch.profiling.usageprofile.cost`

## Purpose
Computes what the measured execution would cost on every deployment alternative (VM, container service, browser service),
split by TJob, lifecycle phase and cloud object.

## Inputs and output
`CostCalculator.calculate(timeline, catalog[, browserTjobs])` -> `dto.cost.CostReportDTO` (peaks, duration, alternatives sorted by
total, cheapest first, warnings). Input: a `measurement.Timeline`, a `pricing.PriceCatalog` and optionally the (upper case)
ids of the TJobs that drive a browser.

## Public API
Only `CostCalculator` (with the labels of the phases and objects it exposes as constants: `COMPUTE`, `COI`,
`OVERSUBSCRIPTION`, ...). `AlternativeCost` (the strategy interface), `VmCost`, `ContainerCost`, `BrowserServiceCost`,
`CostContext` and `CostAccumulator` are package-private.

## Behaviour
- `CostCalculator` only orchestrates: one `AlternativeCost` strategy per kind (`VmCost`, `ContainerCost`,
  `BrowserServiceCost`, sharing a `CostContext` and a `CostAccumulator`); the browser strategy runs last, over the already
  sorted alternatives. A new kind of alternative is a new strategy.
- VM alternatives carry the reservations of the chosen VM (`AlternativeDTO.reservations`); container alternatives have none.
- VM alternatives: per provider+region, the cheapest 3 VMs of distinct shape that fit the peak CPU and memory; none fits ->
  warning. Per second the VM costs `hourly/3600`; COI seconds are charged to that COI phase; otherwise split between running
  TJob phases by their share of the VM (the larger of CPU and memory share, normalized when the sum exceeds 1) and the
  unused part is `(idle)` / `oversubscription`. The volume is provisioned at `max(1, ceil(peak disk GB))` and split the same
  way with its prorated GB-month price (30-day month).
- Container alternatives (one per catalog entry): pay-per-use `cpu*vcpuHour + memGb*gbHour` per second for each TJob phase; no
  oversubscription, COI phases and disk are not charged.
- Browser services (one alternative per `catalog.browsers` entry, `kind` `BROWSER_SERVICE`, provider = the service, e.g.
  `AWS Device Farm`): the SUT keeps running on the **cheapest alternative of the report** (its items are copied and its label
  is part of the label: `Chrome + AWS <vm>`), plus one browser session per TJob of `browserTjobs` during its `tjob-testexec`
  phase, billed `minuteUsd / 60` per second under the cloud object `browser`. The measured browser containers
  (Selenoid/Chrome) are not subtracted. Without `browserTjobs` (or without any other alternative) no browser alternative is
  added and a warning says so ("Browser services not calculated"). `main.UsageProfilerToolBox` takes `browserTjobs` from the
  execution plan: TJobs with a resource whose name contains the browser pattern (case-insensitive; default `browser`,
  property `usageprofile-browser-resources`, see `profiling/main.md`). The projection simulator treats these alternatives as
  pay-per-use.
- Cost items are unique per (cloud object, tjob, phase). The sum of items equals the total.

## Tests (`src/test/java/.../usageprofile/cost`)
`CostCalculatorTests`: VM cost fully distributed with oversubscription and COI seconds, container cost is pay per use (no COI,
no oversubscription), no VM fits produces a warning, browser-service alternative = cheapest alternative + browser minutes of
the TJobs given (and the warning without TJobs).
