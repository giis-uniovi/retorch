# Spec: `giis.retorch.profiling.usageprofile.dto`

## Purpose
The data exchanged between the cost calculation, the plan merge and the page assembly. Immutable, public final fields, no
logic besides the total. They are the only types shared by the other `usageprofile` packages, and **their field names are the
JSON contract** read by the page (see `profiling/report.md`). Jackson serialisation relies on the constructors and
`@JsonProperty` annotations: do not change them without changing the contract.

**Naming:** every data class of this package ends in `DTO` (`AlternativeDTO`, `ContainerUsageDTO`, `CostItemDTO`, `CostReportDTO`,
`ExecutionGraphDTO`, `GraphNodeDTO`, `MetaDTO`, `ReservationOptionDTO`, `SutInfoDTO`, `TimelineSeriesDTO`, `UsageProfileDTO` - the root of the
page JSON, formerly `UsageProfileData`). The suffix is not part of the JSON: field names are unchanged. `Phase` is an enum, not a data
class, and keeps its name. A new data class here must end in `DTO`.

## Subpackages (one per functionality, like `usageprofile`)
| Package | Spec | DTOs |
|---|---|---|
| `dto.cost` | [dto-cost.md](dto-cost.md) | `CostItemDTO`, `AlternativeDTO`, `ReservationOptionDTO`, `CostReportDTO` |
| `dto.measurement` | [dto-measurement.md](dto-measurement.md) | `TimelineSeriesDTO`, `ContainerUsageDTO`, `Phase` (enum) |
| `dto.plan` | [dto-plan.md](dto-plan.md) | `GraphNodeDTO`, `ExecutionGraphDTO` |
| `dto.report` | [dto-report.md](dto-report.md) | `SutInfoDTO`, `MetaDTO`, `UsageProfileDTO` (the root of the page JSON) |

The `dto` package itself has no classes: it only groups the four subpackages. A new data class goes to the subpackage of the
functionality that produces it.
