# Spec: `giis.retorch.orchestration.exception`

## Purpose
The errors RETORCH reports instead of silently degrading. Invalid input is never turned into a smaller pipeline: the
generation fails, and the message lists **every** problem found, not only the first.

## Public API
- `RetorchException` (checked): `getProblems()` (one entry per problem; the message is a header plus one line per problem).
  Subclasses:
  - `InvalidConfigurationException`: `retorchCI.properties` missing or without a required property, a resource with a
    `dockerImage` that is not `PLACEHOLDER;image`.
  - `InvalidSystemException`: annotated tests with wrong annotations, resources that fail validation, a system that
    fails the aggregator validation, tests that cannot be loaded. Subclasses: `classifier.EmptyInputException`,
    `scheduler.NotValidSystemException`.
  - `SchedulingException`: a TJob that cannot be placed, an invalid activity graph. Subclasses:
    `scheduler.NoTGroupsInTheSchedulerException`, `InvalidPlanException` (the activities are not a valid execution graph: cycle, predecessor outside the plan, no initial
    activity).
- `OrchestrationGenerator` declares only `RetorchException` and `IOException`.

## Lenient mode
`--lenient` (`OrchestrationGenerator.withLenient(true)`, `RetorchClassifier.lenient(true)`, constructors with a `lenient`
flag in the aggregator and scheduler) keeps the old behavior: the problems are logged and the affected tests/TJobs are
left out. It is meant for migration only.

## Tests
`ClassifierTests`, `SchedulerTests`, `WorkspaceConfigTests` (negative cases of each subclass).
