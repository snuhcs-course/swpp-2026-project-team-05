# FrameLESS testing documentation

| Field | Value |
| --- | --- |
| Status | Living team document |
| Current revision | 2026-11-20 |
| Historical baseline | [`archive/iteration-2/testing-documentation-iter2.md`](archive/iteration-2/testing-documentation-iter2.md) |

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
must pass before merge. A live smoke or human editorial review is never
represented as a deterministic unit-test result.

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
| Guest and offline reading | [`specs/testing/acceptance-v2.spec.md`](../specs/testing/acceptance-v2.spec.md) | Implemented behavior with five supplemental cases still awaiting team freeze and execution |
| Staged link analysis | [`specs/testing/link-analysis.spec.md`](../specs/testing/link-analysis.spec.md) | Deterministic endpoint boundaries implemented; live model quality remains separate evaluation evidence |
| Iteration 4 release verification | [`specs/testing/iteration-4-release.spec.md`](../specs/testing/iteration-4-release.spec.md) | Drafted before the final release run; no result is claimed yet |
| Iteration 3 Android release smoke | [`specs/testing/iteration-3-release-smoke.spec.md`](../specs/testing/iteration-3-release-smoke.spec.md) | Passed 6/6 on `ff16cd1`; exact API structure matched 6/6 |

### Result recording template

Append a dated row after running a release candidate; never replace an older
failure or change its revision.

| Date | Commit/build | Environment | Commands or procedure | Result | Evidence / open failure |
| --- | --- | --- | --- | --- | --- |
| YYYY-MM-DD | commit / APK / deployment | local / CI / device | exact command or UAT ID | pass/fail | run, report, screenshot, or issue |

## 3. Acceptance testing plan

The six stable user stories and concrete procedures are frozen in
`specs/testing/acceptance-v1.spec.md` against `main` commit `ab8d9bf`. From
Iteration 4 onward, record per-story pass/fail, tester, date, client/build,
backend revision, database seed, deviations, and evidence here. The smaller
Iteration 3 release smoke below is integration evidence, not a claim that formal
UAT results were completed early.

## 4. Heuristic evaluation follow-up

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

## 5. Iteration 3 release-candidate smoke

The frozen six-story procedure and success criteria are in
`specs/testing/iteration-3-release-smoke.spec.md`. It samples the reviewed data
shapes needed by the Reader: one-to-one, one-to-many, multiple findings, and a
mix of single- and multi-source comparisons. Results are appended after running
the installed APK against the production API.

| Case | Expected findings / comparison pages | Observed | API detail + analysis | Installed Reader | Result / deviation |
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

## 6. Review cadence

- **Every feature PR:** specification link, focused tests, regression test for a
  bug, and green affected suites.
- **Every merge candidate:** all required CI jobs, coverage gates, and unresolved
  `Tip` gaps reviewed for release risk.
- **Every iteration:** rerun on the exact submission commit; append counts and
  evidence; review frozen acceptance criteria without rewriting history.
- **Before a live demo or release:** Web smoke, installed Android smoke, API and
  database revision recorded together.

## 7. Iteration result log

| Date | Commit/build | Environment | Commands or procedure | Result | Evidence / open failure |
| --- | --- | --- | --- | --- | --- |
| 2026-11-05 | `ff16cd1`; APK SHA-256 `901c4a8bdc9ced21e32d0fe0129952979c43a4ddfcbc4ac0ca830ab4422f90e4` | GitHub Actions; production API; Android 16 / API 36 emulator | Verify workflow; `scripts/smoke_iteration3_release.py`; frozen six-story installed-Reader procedure | Pass: Django 381, Web 25, Android JVM 84; API 6/6; Reader 6/6 | [Verify run 42027115443](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/42027115443); Android lint: 0 errors, 28 warnings. No blocking deviation. |
| 2026-11-06 | `b9d03ef`; APK SHA-256 `04bd3bcbf2dad3281b399f8d7d98f469ce786b98682232efc354afe95d5ab640` | Local macOS; SQLite; Node 24; Android Studio JBR 21 | Canonical backend, Web, and Android commands from `AGENTS.md`; backend and Android coverage gates | Pass: Django 381, Web 27, Android JVM 84; Web lint/build; Android lint/build; all enforced coverage gates | A clean Python environment exposed that the OpenRouter client used `httpx` directly without declaring it; `requirements.txt` now declares it explicitly. Compose views remain a tracked device-test gap. The live API and six-story installed-Reader smoke were not rerun, so the 11/05 integration evidence remains the applicable result. |

The 11/05 APK was downloaded from the named CI run rather than rebuilt locally.
The 11/06 APK digest belongs only to the local regression build recorded in its
row. Iteration 2 evidence and known gaps remain preserved in the historical
baseline linked at the top of this document.

## 8. Iteration 4 release verification

The execution contract for the final Iteration 4 candidate is versioned in
[`specs/testing/iteration-4-release.spec.md`](../specs/testing/iteration-4-release.spec.md).
This section is intentionally a result shell until the final feature branches
are merged and one release identity is available. Do not copy successful counts
from a preview branch or combine an APK, deployment, and database seed from
different revisions.

### 8.1 Release identity

| Field | Final value |
| --- | --- |
| `main` commit | Pending final merge |
| GitHub Actions run | Pending |
| Web deployment | Pending |
| API deployment | Pending |
| APK SHA-256 | Pending |
| Android device/version | Pending |
| Database dataset/seed | Pending |
| Test date and tester | Pending |

### 8.2 Automated and integration results

| Area | Command or CI job | Tests/result | Coverage versus target | Evidence |
| --- | --- | --- | --- | --- |
| Django | `FRAMELESS_USE_SQLITE=1 python3.12 manage.py test` | Pending | Pending | Pending final CI run |
| Web | `npm test`, `npm run lint`, `npm run build` | Pending | Not used as a coverage gate | Pending final CI run |
| Android | `:app:jacocoDebugUnitTestReport`, `:app:lintDebug`, `:app:assembleDebug` | Pending | Pending | Pending final CI run |

### 8.3 Frozen acceptance results

Run the six unchanged stories in
[`acceptance-v1.spec.md`](../specs/testing/acceptance-v1.spec.md). The signed-out
expectation in UAT-01 predates public guest reading; its observed deviation must
be recorded rather than edited away.

| Story | Client/environment | Tester/date | Expected and observed behavior | Result | Deviation/evidence |
| --- | --- | --- | --- | --- | --- |
| UAT-01 Account session | Pending | Pending | Pending | Pending | Guest-contract change must be reported explicitly |
| UAT-02 Feed browsing | Pending | Pending | Pending | Pending | Pending |
| UAT-03 Complete-corpus search | Pending | Pending | Pending | Pending | Pending |
| UAT-04 Article and optional analysis | Pending | Pending | Pending | Pending | Pending |
| UAT-05 Grounded comparison | Pending | Pending | Pending | Pending | Pending |
| UAT-06 Per-account Saved synchronization | Pending | Pending | Pending | Pending | Pending |

### 8.4 Iteration 4 focused regression

These checks cover current behavior added after the frozen v1 plan. They are
supplementary until the team reviews and freezes acceptance v2.

| Case | Scope | Result | Evidence or deviation |
| --- | --- | --- | --- |
| R4-01 | Guest feed, search, Reader, and comparison | Pending | Pending |
| R4-02 | Account isolation and Android offline Saved | Pending | Pending |
| R4-03 | Compared article opens in Reader and can be saved | Pending | Pending |
| R4-04 | Existing/new/invalid/failed staged link analysis | Pending | Pending |
| R4-05 | First-run guidance and understandable analysis vocabulary | Pending | Pending |
| R4-06 | Feed categories and economic-lens projection | Pending | Pending |

### 8.5 AI-module evaluation update

The Iteration 3 human-review result remains historical evidence. Iteration 4
must add the exact case-set revision, pipeline/model settings, reviewer method,
per-metric denominators, and failure analysis for any new semantic result. Until
that run is completed, deterministic fixture and grounding checks must not be
described as editorial accuracy. If no new semantic run is performed, the final
record will say so explicitly and report only the structural results actually
measured.

### 8.6 Final limitations and publication

After the run, record every unresolved failure or intentional deviation, update
the separate English Wiki page without removing earlier iteration evidence, add
the final revision-history row, and export that exact Wiki revision to PDF for
eTL submission.
