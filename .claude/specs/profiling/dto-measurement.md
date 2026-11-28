# Spec: `giis.retorch.profiling.usageprofile.dto.measurement`

## Purpose
The measured usage of the execution (total usage over time, usage per container) and the lifecycle phases. Data classes of the DTO family (see [dto.md](dto.md) for the rules every DTO follows: immutable, public final fields,
name ends in `DTO`, field names are the JSON contract read by the page, see [report.md](report.md)). Produced and used by
[measurement.md](measurement.md).

## Types
- `TimelineSeriesDTO(t, cpu, memGb, diskGb)`: total usage over time as parallel lists of equal length.
- `ContainerUsageDTO(name, image, peakCpu, avgCpu, peakMemGb, avgMemGb, layerGb, imageGb, volumeGb, storageGb)`: usage of one
  container of a TJob (cores / GB 1024^3), `storageGb = layerGb + imageGb + volumeGb`.
- `Phase` (enum: `label()`, `order()`, `isCoi()`): the lifecycle phases in display order; the label is the exact string of the
  usage file and of `CostItemDTO.phase`. Constants of the kinds (`AlternativeDTO.KIND_*`) and of the objects (`CostItemDTO.OBJECT_*`).

## Tests
No own tests: the classes are exercised by the tests of the packages that use them, and the JSON field names by `usageprofile.report.UsageProfileGeneratorTests`.
