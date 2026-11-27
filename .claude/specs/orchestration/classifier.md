# Spec: `giis.retorch.orchestration.classifier`

## Purpose
Resource Identification phase: turns annotated test classes into a `System` (resources + test cases), and
(de)serializes `Resource` configuration JSON files.

## Public API
- `RetorchClassifier`: `getSystemFromPackage(pkg)`, `getSystemFromClass(cls)`, `validateTestCase`,
  `getTestCasesFromMethod`, `getAccessModesFromMethod`, `getRequiredResource`, `getClassMethods`.
- `ResourceSerializer`: `addResourceToSerialize`, `serializeResources`, `deserialize`,
  `deserializeResources(systemName)` -> `Map<String, Resource>` read from
  `.retorch/configurations/<systemName>SystemResources.json`.
- `EmptyInputException`: null/empty/invalid input (e.g. a missing directory) given to the classifier.

## Behavior
- A resource in the JSON file does not have to declare `minimalCapacities` (the usage is measured at run time); when
  missing it is an empty list.
- Only methods carrying the RETORCH annotations become `TestCase`s; each access mode is bound to the `Resource`
  declared in the JSON file.
- The resources file path is relative to the working directory.

## Tests
`ClassifierTests`, `ClassifierUtils`, `OrchestrationIntegrationTests`.
