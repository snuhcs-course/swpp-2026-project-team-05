# FrameLESS testing documentation

| Field | Value |
| --- | --- |
| Status | Living team document |
| Current revision | 2026-11-06 |
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
| Saved articles v1 | [`specs/testing/saved-articles.spec.md`](../specs/testing/saved-articles.spec.md) | Implemented with explicit remaining UI/security gaps |
| Following sources v1 | [`specs/testing/following-sources.spec.md`](../specs/testing/following-sources.spec.md) | Implemented with explicit remaining navigation/concurrency gaps |
| Reader analysis hierarchy | [`specs/testing/reader-analysis-ui.spec.md`](../specs/testing/reader-analysis-ui.spec.md) | Implemented from reviewed HE feedback; remaining device back-navigation gap is explicit |
| URL-analysis preview | [`specs/testing/url-analysis-mock.spec.md`](../specs/testing/url-analysis-mock.spec.md) | Implemented as a client-only mock; no upload or analysis is claimed |
| Evaluation feed opening order | [`specs/testing/feed-opening-order.spec.md`](../specs/testing/feed-opening-order.spec.md) | Implemented as an isolated, temporary evaluation-build behavior |
| News app acceptance | [`specs/testing/acceptance-v1.spec.md`](../specs/testing/acceptance-v1.spec.md) | Six stories frozen for Iteration 3 against `ab8d9bf` |
| Iteration 3 Android release smoke | [`specs/testing/iteration-3-release-smoke.spec.md`](../specs/testing/iteration-3-release-smoke.spec.md) | Passed 6/6 on `ff16cd1`; exact API structure matched 6/6 |

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

The six stable user stories and concrete procedures are frozen in
`specs/testing/acceptance-v1.spec.md` against `main` commit `ab8d9bf`. From
Iteration 4 onward, record per-story pass/fail, tester, date, client/build,
backend revision, database seed, deviations, and evidence here. The smaller
Iteration 3 release smoke below is integration evidence, not a claim that formal
UAT results were completed early.

## 5. Heuristic evaluation follow-up

The Iteration 3 implementation addressed five reproducible usability findings
in the Android client, including two small recovery and input refinements:

| Observed issue | Change | Evidence |
| --- | --- | --- |
| Latest and Following looked like independent checkboxes and suggested that both could be active. | Replaced the ambiguous interaction with mutually exclusive feed-scope selection. | `85ac777`, PR #75 |
| Selecting a marked phrase exposed too much comparison UI while the user was still reading, and the expanded view was difficult to dismiss predictably. | Added a compact in-context preview before the full comparison view and hardened close, drag, and navigation behavior. | `700df97`, PR #77 |
| Turning analysis markers on or off moved the article away from the paragraph being read. | Preserved Reader position while applying the saved analysis preference. | `1a7b93f`, PR #78 |
| The Android keyboard remained open after tapping ordinary content outside an input. | Added one non-consuming focus-dismissal behavior across authentication, search, and URL entry surfaces. | `NewsScreensInstrumentedTest.authFormDismissesKeyboardWhenTheUserTapsOrdinaryContent` |
| A zero-result category message did not help the reader recover even when the same query had results elsewhere. | Added a restrained action that returns to the existing All results without adding result-count badges. | `NewsScreensInstrumentedTest.searchUsesAllPoliticsAndSocietyFiltersWithoutCountBadges` |

These changes are implementation follow-up. The submitted HE spreadsheet remains
the source of participant findings and severity ratings; this document does not
replace or reconstruct that sheet.

## 6. Iteration 3 release-candidate smoke

The frozen six-story procedure and success criteria are in
`specs/testing/iteration-3-release-smoke.spec.md`. It samples the reviewed data
shapes needed by the Reader: one-to-one, one-to-many, multiple findings, and a
mix of single- and multi-source comparisons. Results are appended after running
the installed APK against the production API.

| Case | Expected findings / passages | Observed | API detail + analysis | Installed Reader | Result / deviation |
| --- | ---: | ---: | ---: | --- | --- |
| `b08` HD Hyundai Electric | 2 / 3 | 2 / 3 | 281 ms | Single- and multi-source previews opened | Pass |
| `b12` Shubao | 1 / 3 | 1 / 3 | 260 ms | Three distinct publishers paged | Pass |
| `b16` Hong Joon-pyo | 3 / 8 | 3 / 8 | 279 ms | Three findings; three distinct publishers paged for the sampled finding | Pass |
| `b33` Hongdae and Itaewon | 3 / 3 | 3 / 3 | 241 ms | Findings switched without stale explanation | Pass |
| `b34` Hanwha U.S. Navy order | 3 / 5 | 3 / 5 | 264 ms | Multi-source preview and grounded passage opened | Pass |
| `b11` Goheung space complex | 1 / 1 | 1 / 1 | 256 ms | Single-source detail showed no pager | Pass |

The API timing is the sum of the authenticated detail and analysis requests,
measured by `scripts/smoke_iteration3_release.py`; it is not presented as
tap-to-render UI latency. Per-case table values sum the two request times after
each was rounded to a whole millisecond. The reported median of 262 ms and
maximum of 280 ms were computed from the unrounded combined timings.
The installed Reader was also checked manually on the API 36 emulator with a
fixed two-second observation window per story; every story was interactive by
the observation point, with no crash, ANR, blank article, or infinite loading.

Stateful checks also passed: toggling analysis did not change the visible body
bounds, saving and reopening `b16` from Saved worked, and removing it made it
disappear from Saved. The six frozen cases intentionally cover one-to-one,
one-to-many, multiple-finding, and mixed single/multi-source structures.

## 7. Review cadence

- **Every feature PR:** specification link, focused tests, regression test for a
  bug, and green affected suites.
- **Every merge candidate:** all required CI jobs, coverage gates, and unresolved
  `Tip` gaps reviewed for release risk.
- **Every iteration:** rerun on the exact submission commit; append counts and
  evidence; review acceptance and AI-evaluation freezes without rewriting history.
- **Before a live demo or release:** Web smoke, installed Android smoke, API and
  database revision recorded together.

## 8. Iteration result log

| Date | Commit/build | Environment | Commands or procedure | Result | Evidence / open failure |
| --- | --- | --- | --- | --- | --- |
| 2026-11-05 | `ff16cd1`; APK SHA-256 `901c4a8bdc9ced21e32d0fe0129952979c43a4ddfcbc4ac0ca830ab4422f90e4` | GitHub Actions; production API; Android 16 / API 36 emulator | Verify workflow; `scripts/smoke_iteration3_release.py`; frozen six-story installed-Reader procedure | Pass: Django 381, Web 25, Android JVM 84; API 6/6; Reader 6/6 | [Verify run 42027115443](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/42027115443); Android lint: 0 errors, 28 warnings. No blocking deviation. |
| 2026-11-06 | `b9d03ef`; APK SHA-256 `04bd3bcbf2dad3281b399f8d7d98f469ce786b98682232efc354afe95d5ab640` | Local macOS; SQLite; Node 24; Android Studio JBR 21 | Canonical backend, Web, and Android commands from `AGENTS.md`; backend and Android coverage gates | Pass: Django 381, Web 27, Android JVM 84; Web lint/build; Android lint/build; all enforced coverage gates | A clean Python environment exposed that the OpenRouter client used `httpx` directly without declaring it; `requirements.txt` now declares it explicitly. Compose views remain a tracked device-test gap. The live API and six-story installed-Reader smoke were not rerun, so the 11/05 integration evidence remains the applicable result. |

The 11/05 APK was downloaded from the named CI run rather than rebuilt locally.
The 11/06 APK digest belongs only to the local regression build recorded in its
row. Iteration 2 evidence and known gaps remain preserved in the historical
baseline linked at the top of this document.
