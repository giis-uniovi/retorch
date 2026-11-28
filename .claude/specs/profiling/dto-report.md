# Spec: `giis.retorch.profiling.usageprofile.dto.report`

## Purpose
What the page receives: the root of the JSON and the header data (SUT, branch). Data classes of the DTO family (see [dto.md](dto.md) for the rules every DTO follows: immutable, public final fields,
name ends in `DTO`, field names are the JSON contract read by the page, see [report.md](report.md)). Produced and used by
[report.md](report.md).

## Types
- `SutInfoDTO(name, url, branch)`: SUT and branch shown in the header; empty strings when unknown, never null. `SutInfoDTO.detect()` /
  `detect(env, workspace)` read `GIT_URL` (else `remote "origin"` of `<workspace>/.git/config`) and `BRANCH_NAME` (else
  `GIT_BRANCH` without `origin/`, else `<workspace>/.git/HEAD`); the workspace is `WORKSPACE` or the current directory. The URL
  is normalised to a plain `https://host/org/repo` (no credentials, no `.git`, `git@host:` and `ssh://` converted; anything
  else, e.g. a local path, gives an empty URL); the name is its last segment.
- `MetaDTO` (`phases, kinds, objects`): built from them, always `MetaDTO.standard()`.
- `UsageProfileDTO(plan, pricesFetchedAt, report, timeline, graph[, sut])`: exactly what the page receives; `graph` is
  nullable, `sut` defaults to `SutInfoDTO.none()`; `meta` is always set.

## Tests
`SutInfoTests` (`src/test/java/.../usageprofile/dto/report`): SUT and branch from the Jenkins environment (credentials removed, ssh converted) and from the `.git` files of the
workspace; unknown values empty, a local path is not turned into a link. The JSON field names are checked by
`usageprofile.report.UsageProfileGeneratorTests`.
