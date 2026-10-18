# FrameLESS Testing Documentation — Iteration 2 draft

**Status:** Working draft, measured locally on 18 October 2026 (Asia/Seoul) against integration code commit `5eb473e`. The results below are 64 Django tests, 3 coverage-gate tests, and 25 Android host-JVM tests. Rerun the commands if the candidate changes before Wiki/PDF submission.

This document follows the course *Testing Documentation Guidelines*. It is intended for developers and course staff. The plan and the measured results are separate so that future iterations can retain the original goals and explain any revisions.

## 1. Test responsibility and schedule (required from Iteration 2)

### Plan

| Component | Proposed test owner | Scope of ownership |
| --- | --- | --- |
| Android import, response parsing, and Compose UI | Jinsol Lee | Android data/UI tests and device-level user flows for UI changes. |
| Article retrieval, comparison selection, and LLM pipeline | Junseo | Deterministic pipeline tests with mocked NAVER/Gemini boundaries; AI evaluation preparation. |
| Information/expression rubric (I1–I5, A–G) | Seoyoung | Rubric examples, expected interpretations, evidence rules, and later human review of AI cases. |
| Django API, Android/backend contract, deployment integration | Seungmin Kim | API/error tests, cross-language fixtures, coverage reports, integration smoke tests, and release gate. |

These owners are proposed from current feature responsibilities and need team confirmation before Wiki publication; test ownership has not yet been formally assigned. The proposed policy is that the author of a feature or fix adds or updates its regression tests in the same PR. A reviewer checks that the test exercises the changed behavior and that any model-dependent assertion is grounded in a visible source passage. Seungmin coordinates the final cross-component run; ownership of a layer does not remove the author’s obligation to test their own changes.

`.github/workflows/verify.yml` runs on pull requests targeting `main` and pushes to `main`. Its Python 3.12 job checks Django configuration, runs three tests of the coverage gate, executes the Django suite with branch coverage, and **fails** if the endpoint/adapter or v2 package falls below the named line goals. The JDK 21 Android job runs JVM tests, JaCoCo, `lintDebug`, and a debug APK build; it **fails** if data, presentation state, or ViewModel coverage falls below their named line goals. Both jobs save test/coverage evidence, and the APK artifact is explicitly labeled as using the default backend without a live service check. The Compose UI goal remains an **open exception**: host-JVM coverage is reported as `OPEN GAP`, not treated as a passing 30% gate, until device tests can measure it. Run the same checks on each PR, the exact merge/demo candidate, and at iteration end. A release or demo that claims end-to-end analysis additionally needs a real configured backend request and an Android device/emulator walkthrough. That live gate has not yet been satisfied.

### Results (18 October 2026)

The local offline gates passed on `5eb473e` (details in §2), and the [PR CI run for this revision](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/39119062346) independently passed both jobs with the new coverage and lint gates. An earlier [CI run for `91d7c29`](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/39116785595) passed before those gates were added. No nightly job is configured. A manual `Medium_Phone` emulator walkthrough using a synthetic v2 HTTP 200 response passed after a parser display fix: input → claim list → detail → I1/I2/I3/I5/A–G rubric cards, with no `대상: null` artifact. This checks the contract and UI path, not a live model request. No live v2 analysis sign-off has been recorded. Before submission, confirm owners and reviewer and rerun the checks on the named final commit.

## 2. Unit and integration testing (required from Iteration 2)

### Plan

#### Architecture, frameworks, and isolation

FrameLESS has an Android client (Kotlin/Compose), a Django JSON API, and a Python retrieval/comparison/rubric pipeline. The Android tests use JUnit 4, host-JVM `org.json`, Robolectric 4.17 for the ViewModel/Android looper (test SDK 35), and JaCoCo. Python tests use Django `SimpleTestCase`/test client, `unittest.mock`, and coverage.py. The current tests avoid paid and nondeterministic external calls by mocking Gemini and NAVER at the network/model boundary. Synthetic article text is used for contract cases. This validates mapping and error handling; it does not validate the factual quality of a model answer.

| Layer | Unit/integration cases to maintain | Line-coverage goal |
| --- | --- | ---: |
| Django HTTP endpoint and v2-to-Android adapter (`server/views.py`, `server/analysis/v2_adapter.py`) | JSON and URL validation, busy/retryable errors, v2 routing, stable article IDs, source URLs/quotes, claim IDs, conservative partial/unavailable states, rubric I1–I5/A–G mapping. | ≥85% |
| v2 pipeline package (`server/analysis/v2/`) | Fetch/search failure boundaries, same-event selection, per-claim comparison, schema and evidence validation, rubric exclusions, model error/timeout handling; mock external services. | ≥65% |
| Android data (`android/.../data/`) | Parse required/optional fields, reject unsafe links or conflicting IDs, preserve relations and quote provenance, retain nullable rubric markedness. | ≥80% |
| Android presentation state (`android/.../ui/state/`) | Claim-to-card grouping, article counts, quoted speech handling, empty/error states, optional question grouping. | ≥85% |
| Android ViewModel (`android/.../ui/viewmodel/`) | Loading, success, retryable error, cancellation, and stale-response handling with a fake API. | ≥60% |
| Compose views and theme (`android/.../ui/`, excluding `state` and `viewmodel`) | Import, loading, comparison, source-link and status flows in device/emulator tests. | ≥30% |

These are **line** coverage goals for production source, not test count or model accuracy goals. They are retained as the plan for the next measurement; failing a goal is reported below. JaCoCo excludes generated `BuildConfig`/`R` classes. Both tools exclude third-party libraries and test source. Python's `--source=server` includes the legacy v1 modules and Django plumbing in the overall total; the v2 package is reported separately because it is the Iteration 2 implementation. Theme code remains in the Compose denominator. No production code is silently omitted because it needs external services: those branches require fakes/mocks or a separate live integration run.

The contract integration path is covered in two ways. Django's test client exercises `POST /api/analyze` with a mocked pipeline and checks HTTP status and response structure. A checked-in synthetic JSON fixture is emitted from the Python v2 adapter test and parsed in the Android JVM test (`tests/test_v2_adapter.py` ↔ `android/app/src/test/resources/v2_android_response.json` ↔ `V2AdapterGoldenTest.kt`). This catches cross-language schema drift but does not execute the HTTP transport, Android Compose UI, Gemini, or NAVER together.

The planned live integration scenarios are: (1) `GET /api/health` confirms configured keys; (2) a public Korean-news URL gives verified same-event articles and clickable original sources; (3) claims remain attached to the correct source evidence and article identity, including `same_issue`; (4) absent or unverified evidence is not presented as an omission or score of zero; (5) model/search failures appear as errors or partial states rather than “no matches”; (6) the same response is inspected on an emulator/device for navigation and readable UI. Record the URL, backend deployment commit, Android APK commit, response ID, date, and observed result. Do not store credentials or complete article bodies in test output.

#### Reproducible offline commands

Use Python 3.12, JDK 21, Android SDK 37, and the repository root as the working directory. Before the Gradle command, point `ANDROID_HOME` to the installed Android SDK, **or** create `android/local.properties` with `sdk.dir` set to its absolute path. Without one of these, Gradle fails before testing because it cannot locate the SDK. Robolectric downloads its Android 35 test runtime during dependency setup. The first dependency install needs network access; the test runs themselves do not need Gemini/NAVER keys. `coverage.py` is a measurement tool installed separately for this draft, so its pinned version is included explicitly.

```sh
python3.12 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m pip install 'coverage==7.16.2'
.venv/bin/python -m unittest discover -s ci -p 'test_*.py'

mkdir -p /tmp/frameless-iter2-coverage
COVERAGE_FILE=/tmp/frameless-iter2-coverage/data \
  .venv/bin/python -m coverage run --branch --source=server manage.py test --verbosity 0
COVERAGE_FILE=/tmp/frameless-iter2-coverage/data \
  .venv/bin/python -m coverage report -m
COVERAGE_FILE=/tmp/frameless-iter2-coverage/data \
  .venv/bin/python -m coverage json -o /tmp/frameless-iter2-coverage/coverage.json
COVERAGE_FILE=/tmp/frameless-iter2-coverage/data \
  .venv/bin/python -m coverage html -d /tmp/frameless-iter2-coverage/html
.venv/bin/python ci/check_coverage.py python /tmp/frameless-iter2-coverage/coverage.json

cd android
./gradlew :app:jacocoDebugUnitTestReport :app:lintDebug :app:assembleDebug
cd ..
.venv/bin/python ci/check_coverage.py android android/app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml
```

Python test output is the test-count evidence. Python per-file executed/total **statement** counts come from `coverage.json` (`summary.covered_lines` / `summary.num_statements`); add the numerators and denominators of the named files for each Python layer in the table below. The coverage.py console/HTML `Cover` column also includes branch opportunities because `--branch` is enabled, so its total percentage differs from the line-only figures. Android counts come from each package's `LINE` counter in `android/app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml`; JUnit results are in `android/app/build/test-results/testDebugUnitTest/`. Keep a screenshot of each coverage report with the exported Wiki/PDF. Local HTML reports and `/tmp` data are generated evidence, not checked-in artifacts.

### Results (updated Iteration 2 measurement, 18 October 2026)

**Tested code revision:** `5eb473e` on 18 October 2026. The Django command ran **64 tests: 64 passed, 0 failed, 0 skipped**; the coverage-gate command ran **3 tests: 3 passed**. The Android command ran **25 host-JVM tests: 25 passed, 0 failed, 0 skipped**; `:app:lintDebug` and `:app:assembleDebug` succeeded. Lint reported 13 warnings and no errors; these include unused starter colors and dependency/catalog notices, with no blocking finding. No automated instrumented Android test was run. The earlier manual emulator smoke used synthetic HTTP data and passed after reinstalling the fixed APK.

![Synthetic v2 response displayed on the Android emulator after the parser fix; this is UI contract evidence, not a live LLM result.](testing-assets/android-synthetic-rubric.png)

A separate live **input-stage-only** smoke on 18 October fetched and extracted one public news URL, `https://n.news.naver.com/article/057/0001971678`, using `server.analysis.v2.article_input.prepare_article`. It returned a title, 10 segmented sentences, and a 1,055-character body in 2.4 seconds. This verifies that one article could be fetched and structured at that time. The page or network may change, and this check did not call NAVER search, Gemini, comparison, or the rubric. It is not an end-to-end result. To repeat the attempt without logging article text:

```sh
.venv/bin/python - <<'PY'
from time import perf_counter
from server.analysis.v2.article_input import prepare_article
started = perf_counter()
article = prepare_article('https://n.news.naver.com/article/057/0001971678')
print({'has_title': bool(article.get('title')),
       'sentences': len(article['sentences']),
       'body_chars': len(article['body']),
       'seconds': round(perf_counter() - started, 1)})
PY
```

| Production layer (line coverage) | Goal | Actual | Result | Report source |
| --- | ---: | ---: | --- | --- |
| Django HTTP endpoint + adapter | ≥85% | 320/365 = **87.7%** | Met, CI gate | Python JSON: `server/views.py` + `server/analysis/v2_adapter.py` |
| v2 analysis package | ≥65% | 1363/2050 = **66.5%** | Met, CI gate | Python JSON: `server/analysis/v2/*.py` |
| Android data | ≥80% | 319/378 = **84.4%** | Met, CI gate | JaCoCo XML: `.../data` |
| Android presentation state | ≥85% | 192/197 = **97.5%** | Met, CI gate | JaCoCo XML: `.../ui/state` |
| Android ViewModel | ≥60% | 65/66 = **98.5%** | Met, CI gate | JaCoCo XML: `.../ui/viewmodel` |
| Compose views and theme | ≥30% | 14/1058 = **1.3%** | **Below goal; open exception** | JaCoCo XML: `.../ui`, `.../ui/screens`, `.../ui/components`, `.../ui/theme` |

![coverage.py HTML report captured after the 64 passing Django tests on commit 5eb473e.](testing-assets/python-coverage.png)

The Python coverage screenshot rounds whole-server statement coverage to 62% and shows **57% total** when branch opportunities are included. The table uses statement/line coverage; `coverage.json` gives the exact whole-server value of 1,940/3,134 = 61.9%.

![JaCoCo package report for the Android host-JVM tests. The Lines columns support the line-coverage calculations above.](testing-assets/android-jacoco-coverage.png)

The JaCoCo HTML screenshot's `Cov.` column reports **instruction coverage** (26% overall), while this document's goals and actual percentages use the XML `LINE` counters. The screenshot reports 1,114 missed lines out of 1,704 total, giving 590/1,704 = 34.6% line coverage.

Context for the denominators: all `server/` production files together are **1940/3134 = 61.9% line coverage**; the combined line-and-branch coverage shown by coverage.py is **57.3%** (rounded to 57% in HTML). All Android app production packages together are **590/1704 = 34.6% line coverage**. Android data/state alone is **511/575 = 88.9%**; it must not be described as whole-app coverage.

The currently passing regression cases include URL/redirect rejection, HTTP body/configuration/concurrency errors, transient Gemini error handling without article text in logs, v2 evidence and article-ID mapping, partial rubric status, rubric evidence links and nullable markedness, direct-quote and speaker attribution, conservative “no matches” behavior, display selection that covers distinct claims, a cap on expensive rubric calls, and a withheld I1 judgment when analysis stages disagree. Android tests cover grouping and the shared Python-to-Android fixture. `AnalysisViewModelTest.kt` exercises loading, successful analysis, and error/retry state with a fake API. The 29 tests in `tests/test_v2_pipeline.py` exercise deterministic retrieval, selection, comparison, rubric, and pipeline branches with external services mocked. The v2 package meets its line-coverage goal, but `result_output.py`, `model_client.py`, and some LLM branches remain comparatively weak. The v2 model prompts, search ranking, and output quality have not been validated by these green tests. The host-JVM tests exercise 14 Compose-component lines, while Compose screens/theme remain at 0%; this is an open test gap, not an exclusion from the denominator.

**Follow-up before treating the demo as verified:** expand deterministic v2 pipeline coverage for selection edge cases, retrieval failures, evidence/schema rejection, and model timeout/fallback paths; add automated Compose UI tests on an emulator; run the live integration scenarios above with authorized server-side credentials. Rerun both suites and regenerate report screenshots on the named final candidate commit. The current work has no local API keys or live v2 Gemini/NAVER result, so it cannot yet substantiate a claim that the complete demo works.

## 3. AI module testing (first required in Iteration 3)

Plan and first results are due in Iteration 3. Before running an evaluation, commit a frozen set of at least 30 article cases with expected behavior or reference labels, the label/review procedure, model and prompt versions, temperature/seed, assertion method, and numerical thresholds. Do not edit or remove submitted cases; record later additions. Exact string equality alone is insufficient as an assertion method. Synthetic contract tests in §2 are **not** an AI-quality evaluation. Discuss the ground-truth method with the TA in advance because FrameLESS compares framing and evidence rather than producing a simple objective label.

## 4. Acceptance testing (plan first required in Iteration 3)

At least five stable user stories, observable pass criteria, and tester procedures will be specified in Iteration 3. Per-story results are first due in Iteration 4.

## 5. Alpha and beta testing (first required in Iteration 5)

The participant plan, distribution/version, procedures, feedback handling, and execution results will be completed in Iteration 5. Alpha participants must be outside this team but within the course; beta users test independently on their own devices.

## Submission checklist for this draft

- Keep the tested code revision identified as `5eb473e` in the Wiki/PDF, or rerun and update all results if later code is submitted.
- Confirm proposed test owners and the PR/merge test policy with the team.
- Confirm the included Python and JaCoCo report screenshots still correspond to the code revision submitted in the Wiki/PDF.
- When copying this draft to the Wiki, replace local image paths with repository image URLs pinned to the submission commit so they appear in the Wiki/PDF.
- Publish this in English as a **separate GitHub Wiki page** from Design Documentation and export that page as a PDF for the Iteration 2 eTL submission.
- Preserve the original plan and record any later target changes with a reason in that iteration's Results section.
