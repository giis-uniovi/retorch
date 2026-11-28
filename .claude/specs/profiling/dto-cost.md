# Spec: `giis.retorch.profiling.usageprofile.dto.cost`

## Purpose
The result of the cost calculation: what every deployment alternative costs and how the cost splits. Data classes of the DTO family (see [dto.md](dto.md) for the rules every DTO follows: immutable, public final fields,
name ends in `DTO`, field names are the JSON contract read by the page, see [report.md](report.md)). Produced and used by
[cost.md](cost.md).

## Types
- `CostItemDTO(object, tjob, phase, cost)`: one billed (cloud object `compute`|`storage`|`browser`, TJob, phase) combination.
- `AlternativeDTO(id, provider, kind, label, region, hourlyPrice, items)`: one VM, container or browser-service alternative; `kind`
  is `VM`, `CONTAINER` or `BROWSER_SERVICE`; `total` is computed from the items (the sum of items always equals the total).
  `reservations` (immutable list of `ReservationOptionDTO`, empty by default) is set with `withReservations(list)`, which returns a
  copy.
- `ReservationOptionDTO(termYears, payment, effectiveHourly)`: `termYears` 1 or 3, `payment` `NO_UPFRONT` | `ALL_UPFRONT` (enum,
  serialized by name), `effectiveHourly` = USD per hour really paid 24x7, running or not (hourly rate, or upfront fee divided
  by `termYears * 8760`). Factory `allUpfront(termYears, fee)`.
- `CostReportDTO(peakCpu, peakMemGb, peakDiskGb, durationSec, alternatives, warnings)`: result of the calculation, alternatives
  sorted by total, cheapest first.

## Tests
No own tests: the classes are exercised by the tests of the packages that use them, and the JSON field names by `usageprofile.report.UsageProfileGeneratorTests`.
