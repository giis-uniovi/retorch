# Package specifications

Specs are grouped by module in `orchestration/`, `profiling/` and `annotations/`, with one file per Java package
(the orchestrator and usage profile ones also cover the resources they own).
They describe **what the package must do**, not how it is written. See the iron rule in `CLAUDE.md`: every change to
a package must be reflected in its spec **in the same change**.

| Module | Package | Spec |
|---|---|---|
| retorch-annotations | `giis.retorch.annotations` | [annotations/annotations.md](annotations/annotations.md) |
| retorch-orchestration | `giis.retorch.orchestration.classifier` | [orchestration/classifier.md](orchestration/classifier.md) |
| retorch-orchestration | `giis.retorch.orchestration.generator` | [orchestration/generator.md](orchestration/generator.md) |
| retorch-orchestration | `giis.retorch.orchestration.main` | [orchestration/main.md](orchestration/main.md) |
| retorch-orchestration | `giis.retorch.orchestration.model` | [orchestration/model.md](orchestration/model.md) |
| retorch-orchestration | `giis.retorch.orchestration.orchestrator` (+ `resources/generictemplates`) | [orchestration/orchestrator.md](orchestration/orchestrator.md) |
| retorch-orchestration | `giis.retorch.orchestration.scheduler` | [orchestration/scheduler.md](orchestration/scheduler.md) |
| retorch-profiling | `giis.retorch.profiling.usageprofile` (+ `resources/usageprofile`) | [profiling/usageprofile.md](profiling/usageprofile.md) |
| retorch-profiling | `giis.retorch.profiling.main` | [profiling/main.md](profiling/main.md) |
| retorch-profiling | `giis.retorch.profiling.utils` | [profiling/utils.md](profiling/utils.md) |

A new package needs a new file in its module folder and a new row in this table.
