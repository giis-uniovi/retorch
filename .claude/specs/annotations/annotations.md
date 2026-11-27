# Spec: `giis.retorch.annotations` (retorch-annotations)

## Purpose
Runtime annotations that test classes/methods use to declare the resources they need and how they access them.
They are read by reflection by the orchestration classifier.

## Public API
- `@Resource` (repeatable through `@Resources`): `resID`, `rType` (LOGICAL / PHYSICAL / COMPUTATIONAL) plus the
  resource attributes (hierarchy, replaceability, elasticity, capacities, docker image). Defaults are `"None"`.
- `@AccessMode` (repeatable through `@AccessModes`): type of access (READONLY, READWRITE...), concurrency, sharing.
- `@ElasticityModel`: elasticity id, upper bound of instances and individual cost.

## Invariants
- All annotations have `RUNTIME` retention and are `@Documented`; the classifier relies on both.
- The module has no dependency on the other modules.

## Tests
Exercised through `retorch-orchestration` (`ClassifierTests`, test data under `testdata/`).
