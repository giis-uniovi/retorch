# RETORCH

Multi-module Maven project (Java 8): `retorch-annotations`, `retorch-orchestration`, `retorch-profiling`.

## IRON RULE: specs are always in sync with the code

Specs are grouped by module and every package has its own file: `.claude/specs/<module>/<package>.md`
(index: `.claude/specs/README.md`).

**Any change to a package — behavior, public API, file formats, configuration properties, generated scripts or
Jenkinsfile, CLI arguments, resources, or its tests — MUST update that package's spec in the same change.**

- Before editing a package, read its spec; if the code and the spec disagree, tell the user before continuing.
- A change is not finished until the spec reflects it. Do not report work as done otherwise.
- A new package needs a new file in its module folder and a new row in `.claude/specs/README.md`; a removed package
  loses both.
- Changes that touch several packages update every affected spec.
- Specs describe what the package must do (contract, inputs/outputs, invariants, tests), not line-by-line code.

## File header
Every source file that can carry a comment starts with this notice (in English; after the shebang in shell scripts, before
`package` in Java, as the first comment in CSS/JS/HTML/XML):

    Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
    All intellectual property rights on this file belong to GIIS.

Add it to every new Java, shell template, CSS, JS, HTML and `pom.xml` file. Not for third-party files (e.g.
`echarts.min.js`, the SVG logos), data (JSON, CSV, env, properties) or `.github/workflows`. Generated scripts under
`expected_out/` follow their templates, so regenerate them when a template changes.

## Working notes
- Build and test: `mvn test` from the root (`-pl retorch-orchestration,retorch-profiling -am` for the two modules).
- Tests compare generated scripts and Jenkinsfiles with files under `retorch-orchestration/src/test/resources/expected_out/`
  (default mode) and `.../expected_out/profiling/` (profiling mode). Changing a template means updating both.
- Java 8 only: no `var`, `List.of`, records.
- Do not modify `.github/workflows` unless asked.
