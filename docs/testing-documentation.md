# FrameLESS testing documentation

| Field | Value |
| --- | --- |
| Status | Living team document |
| Current revision | 2026-11-02 |
| Historical baseline | [`testing-documentation-iter2.md`](testing-documentation-iter2.md) |

This page is the maintainable entry point for the testing plan and measured
results. The Iteration 2 document remains an immutable historical snapshot; do
not edit old counts to look current.

## 1. Governance

The team uses this evidence chain:

`reviewed product intent → behavioral test spec → executable tests → CI artifact → dated result`

For non-trivial behavior, the author updates a file in `specs/testing/` before or
with implementation. A human reviews intent and oracles. Automated tools may
then generate tests and diagnose failures under the authority rules in
`AGENTS.md`. The PR author owns the change's tests; the reviewer checks the
specification link and whether assertions express the intended contract.

CI runs for every pull request to `main` and every push to `main`. A required job
must pass before merge. A live smoke or AI evaluation is never represented as a
deterministic unit-test result.

## 2. Deterministic software testing

| Layer | Framework and boundary | Primary purpose |
| --- | --- | --- |
| Django | Django TestCase/SimpleTestCase, unittest.mock, coverage.py | API, persistence, validation, search, analysis adapters, failures |
| Web | Vitest, ESLint, TypeScript/Vite build | request contracts, client logic, type/build regressions |
| Android | JUnit 4, Robolectric, JaCoCo, Android lint | parsing, repositories, ViewModels, state and pure UI logic |
| Cross-client | Shared JSON fixtures and API contract tests | detect response/schema drift |
| Device/manual | Compose instrumentation, emulator and physical device | rendering, gestures, navigation, release-environment smoke |

The GitHub workflow is `.github/workflows/verify.yml`. The canonical local
commands are listed in `AGENTS.md`; CI additionally enforces the existing
backend and Android coverage gates and uploads reports for 14 days.

### Current feature traceability

| Behavioral unit | Specification | State |
| --- | --- | --- |
| Article search v1 | [`specs/testing/article-search.spec.md`](../specs/testing/article-search.spec.md) | Implemented with explicit remaining UI/race-condition gaps |
| News app acceptance | [`specs/testing/acceptance-v1.spec.md`](../specs/testing/acceptance-v1.spec.md) | Proposed; team freeze required for Iteration 3 |

### Result recording template

Append a dated row after running a release candidate; never replace an older
failure or change its revision.

| Date | Commit/build | Environment | Commands or procedure | Result | Evidence / open failure |
| --- | --- | --- | --- | --- | --- |
| YYYY-MM-DD | commit / APK / deployment | local / CI / device | exact command or UAT ID | pass/fail | run, report, screenshot, or issue |

## 3. AI module evaluation plan

Deterministic pipeline tests establish schemas, grounding checks, and failure
handling; they do not establish editorial accuracy. The 30 reviewed files in
`server/fixtures/gold_v5/` are the candidate Iteration 3 evaluation set. Before
the first formal measurement, the team must review and freeze the exact files,
their source references, annotation ownership, model/prompt version, decoding
settings, and evaluation command. Once frozen, a case may not be edited or
removed; additions receive a new case ID, date, and reason.

The formal evaluation must separate at least these questions:

| Measure | Assertion method | Proposed pre-run gate |
| --- | --- | ---: |
| Fixture and offset validity | deterministic validation against stored article text | 100% |
| Finding precision | human decision: displayed phrase truly satisfies an allowed expression-difference rule | ≥85% |
| Category correctness | human decision: assigned A–G labels fit the reviewed rule set | ≥80% |
| Comparison grounding | target passage exists and supports the stated comparison | ≥90% |
| Unsupported-output rate | human decision: output lacks sufficient stored evidence | ≤5% |

These thresholds are **proposed**, not measured results. The team must approve
them before the evaluation run, then preserve them even if the run fails. Exact
string equality is not the sole oracle for semantic explanations. At least two
reviewers should label ambiguous cases independently, record disagreements, and
resolve them using the written rubric rather than model output.

The evaluation record must include case-set revision, model/provider, prompt and
pipeline version, temperature/seed where supported, exact command, timestamp,
per-case outcomes, aggregate metrics, and failure analysis. Credentials, full
prompts containing licensed article text, and complete article bodies stay out
of public artifacts.

## 4. Acceptance testing plan

The five proposed stable user stories and concrete procedures are in
`specs/testing/acceptance-v1.spec.md`. Freeze them with a team-reviewed commit for
Iteration 3. From Iteration 4 onward, record per-story pass/fail, tester, date,
client/build, backend revision, database seed, deviations, and evidence here.

## 5. Review cadence

- **Every feature PR:** specification link, focused tests, regression test for a
  bug, and green affected suites.
- **Every merge candidate:** all required CI jobs, coverage gates, and unresolved
  `Tip` gaps reviewed for release risk.
- **Every iteration:** rerun on the exact submission commit; append counts and
  evidence; review acceptance and AI-evaluation freezes without rewriting history.
- **Before a live demo or release:** Web smoke, installed Android smoke, API and
  database revision recorded together.

## 6. Iteration result log

No new result is claimed by this governance change. Use the template in §2 after
the current pull request's CI completes. Iteration 2 evidence and known gaps are
preserved in the historical baseline linked above.
