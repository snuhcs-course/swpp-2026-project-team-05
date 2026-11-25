# FrameLESS testing documentation

| Field | Value |
| --- | --- |
| Status | Living team document |
| Current revision | 2026-11-25 |
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
| Django | Django TestCase/SimpleTestCase/TransactionTestCase, unittest.mock, coverage.py | API, persistence, validation, PostgreSQL concurrency, analysis adapters, failures |
| Web | Vitest, Testing Library/jsdom, Oxlint, TypeScript/Vite build | request contracts, client state/navigation, type/build regressions |
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
| Retired URL-analysis preview | [`specs/testing/url-analysis-mock.spec.md`](../specs/testing/url-analysis-mock.spec.md) | Removed from ordinary Web/Android navigation; regression oracles prevent its return |
| Evaluation feed opening order | [`specs/testing/feed-opening-order.spec.md`](../specs/testing/feed-opening-order.spec.md) | Retained on Web and in the legacy Android helper; current Android discovery uses topic ordering |
| News app acceptance | [`specs/testing/acceptance-v1.spec.md`](../specs/testing/acceptance-v1.spec.md) | Six stories frozen for Iteration 3 against `ab8d9bf` |
| Guest and offline reading | [`specs/testing/guest-offline-reading.spec.md`](../specs/testing/guest-offline-reading.spec.md) | Implemented public reads and bounded Android Saved cache; app-shell/Web return gaps remain explicit |
| Staged link analysis | [`specs/testing/link-analysis.spec.md`](../specs/testing/link-analysis.spec.md) | Deterministic endpoint boundaries implemented; live model quality remains separate evaluation evidence |
| Iteration 4 release verification | [`specs/testing/iteration-4-release.spec.md`](../specs/testing/iteration-4-release.spec.md) | Release CI, dataset-aware API smoke, UAT cycle 1, and device evidence are recorded below |
| Iteration 3 Android release smoke | [`specs/testing/iteration-3-release-smoke.spec.md`](../specs/testing/iteration-3-release-smoke.spec.md) | Passed 6/6 on `ff16cd1`; exact API structure matched 6/6 |
| Current Android portal and publishers | [`story-feed.spec.md`](../specs/testing/story-feed.spec.md), [`publisher-navigation.spec.md`](../specs/testing/publisher-navigation.spec.md), [`appearance-consistency.spec.md`](../specs/testing/appearance-consistency.spec.md) | Topic discovery, subscriptions, root navigation and appearance; component coverage is distinct from an installed-device pass |
| Reader annotation and tutorial | [`scroll-synced-analysis-peek.spec.md`](../specs/testing/scroll-synced-analysis-peek.spec.md), [`first-run-tutorial.spec.md`](../specs/testing/first-run-tutorial.spec.md) | Fixed scroll-synced dock, comparison navigation and first-run guidance; app-shell/device gaps are recorded |
| Economic/event context | [`economic-lens.spec.md`](../specs/testing/economic-lens.spec.md), [`event-issue-view.spec.md`](../specs/testing/event-issue-view.spec.md) | Stored data, offsets, API projection and client presentation; no semantic-accuracy claim |
| Trends, reactions and Home highlights | [`realtime-trends.spec.md`](../specs/testing/realtime-trends.spec.md), [`article-reactions-and-home-highlights.spec.md`](../specs/testing/article-reactions-and-home-highlights.spec.md) | Three-hour readership, per-article reactions, 24-hour topic participation and two-hour Breaking eligibility; PostgreSQL races run separately |
| Demo activity | [`demo-activity.spec.md`](../specs/testing/demo-activity.spec.md) | Explicit repeatable seed/cleanup, ordinary-account isolation and rollback; not organic engagement evidence |
| News loading/cache reuse | [`news-loading-cache.spec.md`](../specs/testing/news-loading-cache.spec.md) | Viewer-scoped reuse, invalidation, independent loading and complete-Saved-only offline fallback |
| Account security Stage 1 | [`account-security.spec.md`](../specs/testing/account-security.spec.md) | Shared admission limits, DB-role isolation, no-store and Android credential recovery; browser-cookie Stage 2 deferred |

### Result recording template

Append a dated row after running a release candidate; never replace an older
failure or change its revision.

| Date | Commit/build | Environment | Commands or procedure | Result | Evidence / open failure |
| --- | --- | --- | --- | --- | --- |
| YYYY-MM-DD | commit / APK / deployment | local / CI / device | exact command or UAT ID | pass/fail | run, report, screenshot, or issue |

## 3. Acceptance testing plan

`acceptance-v1.spec.md` freezes six Iteration 3 stories against `ab8d9bf`.
`acceptance-v2.spec.md` preserves a separate, unfrozen guest/offline proposal;
it is not the five-story Iteration 4 UAT sheet recorded in §8.3. Neither is a
claim that its procedures have been rerun on the current portal.

For the next formal cycle, reconcile the selected procedures with current
feature/UI contracts and freeze their revision before execution. Record each
story's outcome, tester/date, client/build, backend, dataset, deviations and
evidence. Keep old plans and results attached to their own revisions. The
Iteration 3 release smoke below is integration evidence, not completed formal UAT.

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
| 2026-11-20 | `ffc093c`; APK SHA-256 `23ce07d220c0c37ca4ff44283b6af85f2e3fe8daa4ba5b4f656f92ecf18694f7` | GitHub Actions; production API/Web; Samsung Galaxy S22 demo device | Final Verify workflow; dataset-aware six-article API smoke; Iteration 4 UAT cycle 1; recorded device walkthrough | Pass: Django 421, Web 37, Android JVM 122; API smoke 6/6. UAT accepted 109/128 executed steps; manual negative-state gaps remain explicit. | [Verify run 44697780855](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/44697780855); [smoke evidence](evidence/iteration-4-release-api-smoke.json); [demo branch](https://github.com/snuhcs-course/swpp-2026-project-team-05/tree/iteration-4-demo). |
| 2026-11-25 | Tested code `2157916`, merged as `faba4aa` in PR #116 | GitHub Actions SQLite/PostgreSQL; local JVM/Web checks; deployed API/Web rewrite | Verify; news-loading/cache and security Stage 1 regression suites; additive security migrations; focused HTTP checks | Pass: SQLite 496 discovered, 11 skipped; separate CI PostgreSQL 51; Web 44; Android JVM 205; coverage gates, lint and builds passed | [Verify 45561135353](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/45561135353); [PR #116 rollout evidence](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/116). Local PostgreSQL additionally ran the 16 ordinary auth cases (67 total). No device/browser walkthrough or new UAT/AI-quality result is claimed. |

The 11/05 APK was downloaded from the named CI run rather than rebuilt locally.
The 11/06 APK digest belongs only to the local regression build recorded in its
row. Iteration 2 evidence and known gaps remain preserved in the historical
baseline linked at the top of this document.

## 8. Iteration 4 release verification

This section preserves the recorded release-candidate evidence for `ffc093c`.
Later merges and counts in §7 do not replace its APK, dataset, UAT results or
known limitations.

The execution contract for the final Iteration 4 candidate is versioned in
[`specs/testing/iteration-4-release.spec.md`](../specs/testing/iteration-4-release.spec.md).
Results are attached to an explicit release identity. Successful counts from a
preview branch are not copied into the final record, and an APK, deployment, or
database observation from different revisions is identified rather than
silently combined.

### 8.1 Release identity

| Field | Final value |
| --- | --- |
| Tested release commit | `ffc093c1301bf1ac3070e5925e74d8852fcdef9b` |
| GitHub Actions run | [Verify 44697780855](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/44697780855) |
| Web deployment | [`https://frameless-team05-web.vercel.app`](https://frameless-team05-web.vercel.app), Vercel status passed for `ffc093c` |
| API deployment | [`https://frameless-team05-api.vercel.app`](https://frameless-team05-api.vercel.app), Vercel status passed for `ffc093c` |
| APK SHA-256 | `23ce07d220c0c37ca4ff44283b6af85f2e3fe8daa4ba5b4f656f92ecf18694f7` (13,941,605 bytes) |
| Android device/version | Samsung Galaxy S22 (`SM-S901N`), Android 15 physical walkthrough; final debug APK built in CI |
| Database dataset/seed | Mutable production data; the public feed projection exposed 8 entries at 2026-11-20 16:51 KST. Exact sample IDs, hashes, versions, and fingerprint are frozen in the smoke JSON |
| Test date and tester | 2026-11-20, Kim Seungmin; UAT cycle 1 used three testers including the course evaluator |
| Deliberate boundary | No new claim of model editorial accuracy; deterministic tests and production smoke cover structure, grounding, provenance, and failure handling only |

### 8.2 Automated and integration results

| Area | Command or CI job | Tests/result | Coverage versus target | Evidence |
| --- | --- | --- | --- | --- |
| Django | `FRAMELESS_USE_SQLITE=1 python -m coverage run --branch --source=server manage.py test` | 421 passed; configuration and coverage checks passed | endpoint + v2 adapter 88.7% (goal 85%); v2 analysis 82.5% (goal 65%); Reader contract 91.5% (goal 65%); total branch-inclusive report 75% | [Verify run 44697780855](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/44697780855), `backend-coverage` artifact |
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
and Economy (two per category). The public feed projection exposed eight
entries (3/3/2 by category). Median detail-plus-analysis latency was 282 ms,
the maximum was 309 ms, and no scan request failed. Sample fingerprint:
`9a9c82d539749a4ede237b554b908fced77ab143e32ccda57e00c731c9050831`.

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

Wiki updates preserve earlier iteration evidence. Submission packaging and
uploading belong to the PM; any exported copy must preserve the release identity
and limitations of its actual source revision.

## 9. Current boundaries after PR #116

The 2026-11-25 evidence covers news-cache correctness and Stage 1 account
security. Production migrations through `0017_backend_only_tables` were applied
before the API rollout. RLS and the absence of PUBLIC/Supabase client-role grants
were verified on all 17 managed/metadata tables; Guest article reads remained
HTTP 200. Auth errors used no-store. Four malformed registration requests with
no credentials confirmed that forged forwarding headers could not choose a
different limiter key through either the direct API or Web rewrite, and both
paths shared the same client bucket. These are focused deployment checks,
not a penetration-test result.

Web still uses its existing bearer/localStorage transport. Browser-cookie/CSRF
Stage 2 was deferred; no email verification, recovery flow or new browser CSP
was implemented. Android instrumentation was compiled but not run for this
change. Physical backup restoration, app-shell authentication transitions and
the direct guest Web `/saved` return path remain explicit gaps in their test
specifications. The older HE/UAT and editorial-quality results remain historical.
