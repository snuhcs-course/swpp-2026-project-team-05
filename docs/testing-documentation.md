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
| Iteration 4 release verification | [`specs/testing/iteration-4-release.spec.md`](../specs/testing/iteration-4-release.spec.md) | Release CI, dataset-aware API smoke, UAT cycle 1, and device evidence are recorded below |
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
| 2026-11-20 | `806283a`; APK SHA-256 `1b5dc105666789434b73cf5de2a19e4b27948c117a0678bb50ebc58c88d4c251` | GitHub Actions; production API/Web; Samsung Galaxy S22 demo device | Final Verify workflow; dataset-aware six-article API smoke; Iteration 4 UAT cycle 1; recorded device walkthrough | Pass: Django 421, Web 37, Android JVM 122; API smoke 6/6. UAT accepted 109/128 executed steps; manual negative-state gaps remain explicit. | [Verify run 44695281510](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/44695281510); [smoke evidence](evidence/iteration-4-release-api-smoke.json); [demo branch](https://github.com/snuhcs-course/swpp-2026-project-team-05/tree/iteration-4-demo). |

The 11/05 APK was downloaded from the named CI run rather than rebuilt locally.
The 11/06 APK digest belongs only to the local regression build recorded in its
row. Iteration 2 evidence and known gaps remain preserved in the historical
baseline linked at the top of this document.

## 8. Iteration 4 release verification

The execution contract for the final Iteration 4 candidate is versioned in
[`specs/testing/iteration-4-release.spec.md`](../specs/testing/iteration-4-release.spec.md).
Results are attached to an explicit release identity. Successful counts from a
preview branch are not copied into the final record, and an APK, deployment, or
database observation from different revisions is identified rather than
silently combined.

### 8.1 Release identity

| Field | Final value |
| --- | --- |
| Tested release commit | `806283a100ba9dc018f2ee1e339b364e84ca8ecb` |
| GitHub Actions run | [Verify 44695281510](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/44695281510) |
| Web deployment | [`https://frameless-team05-web.vercel.app`](https://frameless-team05-web.vercel.app), Vercel status passed for `806283a` |
| API deployment | [`https://frameless-team05-api.vercel.app`](https://frameless-team05-api.vercel.app), Vercel status passed for `806283a` |
| APK SHA-256 | `1b5dc105666789434b73cf5de2a19e4b27948c117a0678bb50ebc58c88d4c251` (13,941,605 bytes) |
| Android device/version | Samsung Galaxy S22 (`SM-S901N`), Android 15 physical walkthrough; final debug APK built in CI |
| Database dataset/seed | Mutable production corpus: 81 visible articles at 2026-11-20 16:33 KST; exact sample IDs, hashes, versions, and fingerprint are frozen in the smoke JSON |
| Test date and tester | 2026-11-20, Kim Seungmin; UAT cycle 1 used three testers including the course evaluator |
| Deliberate boundary | No new claim of model editorial accuracy; deterministic tests and production smoke cover structure, grounding, provenance, and failure handling only |

### 8.2 Automated and integration results

| Area | Command or CI job | Tests/result | Coverage versus target | Evidence |
| --- | --- | --- | --- | --- |
| Django | `FRAMELESS_USE_SQLITE=1 python -m coverage run --branch --source=server manage.py test` | 421 passed; configuration and coverage checks passed | endpoint + v2 adapter 88.7% (goal 85%); v2 analysis 82.5% (goal 65%); Reader contract 91.5% (goal 65%); total branch-inclusive report 75% | [Verify run 44695281510](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/44695281510), `backend-coverage` artifact |
| Web | `npm test`, `npm run lint`, `npm run build` | 37 passed in 9 files; lint 0 warnings/errors; production build passed | Not used as a coverage gate | Same Verify run |
| Android | `:app:jacocoDebugUnitTestReport`, `:app:lintDebug`, `:app:assembleDebug` | 122 passed, 0 skipped/failed; lint 0 errors and 36 warnings; APK built | data 83.8% (goal 80%); presentation state 97.0% (goal 85%); ViewModel 85.0% (goal 60%); Compose 4.9% tracked as an open device-test gap | Same Verify run, `android-test-evidence` and APK artifacts |

### 8.3 UAT cycle 1

The Iteration 4 UAT sheet used five current user stories rather than claiming
that the six historical `acceptance-v1` stories were rerun unchanged. Three
testers executed 128 of 144 planned step executions on a Galaxy S22. The cycle
accepted 109/128 executions (85.2%). All 92 positive-path executions passed;
negative-path executions accepted 17/36 (47.2%), with 16 executions not tested.

| User story | Accepted / executed | Acceptance rate |
| --- | ---: | ---: |
| Expression analysis and cross-publisher comparison | 28 / 28 | 100% |
| Source traceability | 20 / 26 | 76.9% |
| Home-feed browsing | 18 / 25 | 72.0% |
| Article search and category filtering | 25 / 27 | 92.6% |
| Save and revisit articles | 18 / 22 | 81.8% |

Of the 19 rejected steps, most reflected unavailable preconditions rather than
an observed product defect: the session did not provide controlled fixtures for
dead publisher links, empty categories, one-request failures, or a second test
account. These cases should be prepared before the next cycle and recorded as
`Not Tested` when a prerequisite is unavailable. One reproducible defect was
found outside the scripted cases: with an empty search query, switching the
category did not filter the displayed list. The later search-state redesign
removed that inconsistent mode; current Web regression tests cover normal feed
category navigation, corpus-wide search, category search filters, retries, and
query resets.

Evidence: [team UAT thread and analysis report](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1795150370788409?thread_ts=1794979945.300139&cid=C0C56TCMKU6).

### 8.4 Dataset-aware production API smoke

The historical six-story script first failed against the final API because all
six Iteration 3 titles had been replaced in the mutable production dataset.
That failure was retained as a test-design finding rather than interpreted as
six application regressions. The current release smoke discovers the public
corpus, validates evidence offsets and content hashes, opens a compared article
as a first-class Reader article, and records the exact sampled IDs.

The final 2026-11-20 run passed 6/6 sampled articles across Politics, Society,
and Economy (two per category). The feed exposed 81 articles (39/29/13 by
category),
median detail-plus-analysis latency was 278 ms, maximum was 292 ms, and no scan
request failed. Sample fingerprint:
`2f75ede15e3bf7a45abf241ccc05bd7a357f173356b17c410a174031fde8ce7a`.

Evidence: [`docs/evidence/iteration-4-release-api-smoke.json`](evidence/iteration-4-release-api-smoke.json).

### 8.5 Focused device regression

The recorded physical-device walkthrough covers global search, article reading,
analysis markers, expression selection, compared coverage, opening a compared
article as a normal Reader destination, saving it, and reopening the complete
Saved article offline. The demo branch records the environment and exact flow,
and the final CI artifact is identified separately by its SHA-256 above. The
walkthrough and release candidate use the same Android production source.

Two-account isolation, forced one-request failures, and every staged
link-analysis error partition were not repeated manually on the physical device.
Their deterministic checks remain green, but these manual cases stay open rather
than being inferred from the demo recording.

### 8.6 AI-module evaluation update

No new frozen semantic-accuracy run was completed for Iteration 4. The Iteration
3 human-review result remains historical evidence. The final automated and
production checks establish schema validity, stored-text offsets, content-hash
freshness, comparison provenance, and recoverable failure behavior; they do not
establish whether every editorial label or explanation is correct. A future
semantic result must freeze the case-set revision, model and prompt settings,
reviewer method, denominators, and failure analysis before the first run.

### 8.7 Final limitations and publication

The separate English Wiki page is updated without removing earlier iteration
evidence. Its final revision is exported to PDF for eTL submission; the Wiki and
PDF must preserve the same release identity and limitations recorded here.
