# Spec: `giis.retorch.orchestration.classifier`

## Purpose
Resource Identification phase: turns annotated test classes into a `SystemUnderTest` (resources + test cases), and
(de)serializes `Resource` configuration JSON files.

## Public API
- `RetorchClassifier`: `getSystemFromPackage(systemName, pkg)`, `getSystemFromClass(cls)`, `validateTestCase`,
  `getTestCasesFromMethod`, `getAccessModesFromMethod`, `getRequiredResource`, `getClassMethods`.
- `ResourceSerializer`: `addResourceToSerialize`, `serializeResources`, `deserialize`,
  `deserializeResources(systemName)` -> `Map<String, Resource>` read from
  `.retorch/configurations/<systemName>SystemResources.json`.
- `EmptyInputException`: null/empty/invalid input (e.g. a missing directory) given to the classifier.

## Behavior
- A resource in the JSON file does not have to declare `minimalCapacities` (the usage is measured at run time); when
  missing it is an empty list.
- The classes of the package (and its sub-packages) are found in the classpath both when it is a directory of compiled
  classes and when it is inside a **jar** (`jar:` URL: the entries below the package folder are listed in name order).
  Nested classes (`$` in the file name) are skipped in both cases. A package that is not on the classpath fails with
  `EmptyInputException`; a jar must contain the entries of the package folder (what `jar`/Maven produce).
- Only methods carrying the RETORCH annotations become `TestCase`s; each access mode is bound to the `Resource`
  declared in the JSON file.
- The resources file path comes from `RetorchPaths` (default: relative to the working directory). `RetorchClassifier`
  accepts the resources already loaded (`RetorchClassifier(Map<String,Resource>)`) so the JSON is not read again;
  `ResourceSerializer(RetorchPaths)` for another folder.

- The annotation defaults `"None"` and `-1` (attribute not specified) are named constants of the classifier; per-method
  discovery is logged at DEBUG. Resource JSON files are read and written as UTF-8.
- **Strict by default**: wrong annotations (missing/invalid resource id, concurrency, access mode type, unknown
  resource, resources of the JSON that fail validation, replaceable resources that do not exist) make
  `getSystemFromPackage`/`getSystemFromClass` throw `InvalidSystemException` with every problem. Methods without any
  RETORCH annotation are not tests and are ignored. `lenient(true)` keeps the old behavior (log, drop the test).
- `getSystemFromPackage`/`getSystemFromClass` throw `InvalidSystemException` (superclass of `EmptyInputException`).

## Tests
`ClassifierTests` (including a package inside a jar built in a `TemporaryFolder`), `ClassifierUtils`, `OrchestrationIntegrationTests`.
