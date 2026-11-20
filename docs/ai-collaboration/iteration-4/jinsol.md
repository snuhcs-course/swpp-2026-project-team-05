# AI Collaboration Contribution — Iteration 4

|                     |                                                                 |
| ------------------- | --------------------------------------------------------------- |
| **Team**            | Team 5 — FrameLESS                                             |
| **Iteration**       | Iteration 4 — UI Refinement and Reader Comparison Presentation |
| **Period**          | November 7, 2026 – November 20, 2026                           |
| **Iteration goals** | Refine the Android Home visual hierarchy, make expression comparison easier to read, and verify the UI changes with focused automated and emulator checks. |
| **Authors**         | Lee Jinsol — Individual contribution                           |

---

## 1. Summary of AI Usage

I used ChatGPT/Codex as a drafting and implementation partner for my Iteration 4 Android UI work. It helped turn detailed visual requirements into Kotlin/Compose proposals, revise the Reader comparison layout through several iterations, draft focused tests and specifications, and prepare a PR summary and verification plan.

The collaboration was iterative rather than one-shot. I first defined the desired interaction and the invariants that could not change, reviewed an AI-assisted implementation proposal, inspected the rendered UI, then requested a narrower revision when the visual result did not meet the mobile-screen goal. This loop was used for both the Home feed refresh and the Reader comparison card.

The work was deliberately constrained to presentation and existing stored comparison data. The Home refresh changed the feed's visual hierarchy and tutorial entry point; the Reader card presents an already-stored current expression beside an already-stored comparison expression. It did not add a backend/API, create article matches, alter article text, or make any claim about news correctness or bias.

AI output was never treated as proof that the app built or tests passed. I reviewed the final diff, ran the Gradle commands on my local machine and Android emulator, manually inspected the rendered UI, and checked the resulting GitHub Actions run for [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103). The final result was therefore based on source code, terminal output, emulator behavior, and CI status—not on an AI assertion that a patch should work.

### Scope boundaries used in the collaboration

- **Allowed to change:** Home/Reader presentation, Compose semantics, artwork, focused tests, and UI specifications.
- **Required to preserve:** Stored comparison evidence, sentence-trimming behavior, existing callbacks, Reader actions, article highlighting, search, pagination, and navigation.
- **Explicitly out of scope:** New recommendation/ranking logic, new data sources, backend/API changes, database changes, or new claims about the quality of AI analysis.

### AI tools used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| ChatGPT / Codex | Not recorded | Lee Jinsol | Kotlin/Compose UI drafting, test and specification drafting, debugging discussion, and documentation/PR preparation |

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **Contribution level** | **Human owner** |
| --- | --- | --- | --- | --- | --- |
| T1 | Refresh the Android Home feed with category-specific lead artwork, an editorial featured-article layout, and a reading-guide rail | `NewsFeedScreen.kt`, `FeedArtwork.kt`, `FeedHomeChrome.kt`, [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103) | Drafted and refined Compose implementation and test ideas | Medium | Lee Jinsol |
| T2 | Replace the Reader comparison-sheet header with a compact paired-expression card | `ExpressionPair.kt`, `NewsReaderScreen.kt`, [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103) | Drafted implementation and iterated from feedback | Medium | Lee Jinsol |
| T3 | Add focused unit/instrumentation coverage and document the Home and Reader UI contracts | `ExpressionPairTest.kt`, `FeedHomeChromeTest.kt`, focused Android tests, and feature/test specifications | Drafted test cases, acceptance language, and command checklist | Medium | Lee Jinsol |
| T4 | Package the completed change as a reviewable branch/PR with actual local and CI verification evidence | Commit `eb6cdb9`, [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103), GitHub Actions run | Drafted PR summary, handoff wording, and evidence checklist | Low | Lee Jinsol |

---

## 3. Representative Prompts and Outputs

The prompts below are reformatted for readability, but preserve the actual implementation scope, constraints, and acceptance conditions I provided during the iteration. They are included as representative prompts rather than as a claim that the text was copied verbatim from one single message.

### Example 1 — Compact paired-expression comparison card (T2)

**Define** — The Reader comparison sheet needed to show the current article's stored expression and a compared article's stored expression together, but without changing the existing pairing, sentence-trimming, paging, Reader actions, or stored article text. The final panel had to fit comfortably in a mobile Reader sheet and keep both sides visually equal.

**Context provided** — The existing `ExpressionPair.kt` pairing/trimming functions, `NewsReaderScreen.kt`, screenshots of the previous comparison sheet, the existing Reader behavior, and concrete mobile layout constraints. The request explicitly stated that data/trimming functions were untouched.

**Prompt (reformatted for readability)**

```text
Task: Refine the Reader expression-comparison presentation.

Scope
- Modify only the UI rendered by ExpressionPairPanel in ExpressionPair.kt.
- Keep the existing pairing and sentence-trimming functions unchanged.

Required UI
1. Show a small navy “표현 비교” heading above an off-white card with a
   thin lavender/navy border.
2. Render the current side with a filled “현재 기사” pill, publisher name,
   quoted stored expression, and one context line.
3. Render the comparison side with an outlined “다른 보도” pill and the
   same visual hierarchy.
4. Place “같은 내용을 이렇게 다르게” between the two sides.

Invariants
- Reuse only stored phrase, sentence, and publisher values.
- Do not modify pager behavior, callbacks, navigation, Reader actions, or
  article highlighting.
- Omit the card rather than inventing a missing phrase.

Validation
- Preserve one accessibility unit per side and keep decorative lines silent.
- Update focused tests that depend on visible labels or semantics.
```

**Output (excerpt)**

```text
Refactor ExpressionPairPanel as a presentation-only composable. Build the
current and comparison sections from the existing ExpressionPair values;
do not alter pairing or sentence trimming. Use a filled current-article
pill and outlined comparison-article pill, with identical phrase/context
hierarchy. Keep the current side stable when the user pages between
comparison articles, and omit the entire card if either required phrase
is absent. Update focused instrumentation assertions for labels, paging,
and accessibility grouping.
```

**Verify** — I compared the implementation against the existing Reader behavior and inspected the card on the Android Studio Medium Phone emulator. I then ran `ExpressionPairTest`, compiled Android test Kotlin, and ran `ExpressionPairInstrumentedTest`. I also confirmed that the current expression stays fixed while paging changes only the compared side, and that “이 기사 읽기” still opens the correct article.

**Integrate**

- **Kept:** Reuse of stored expression/sentence/publisher data; missing-data omission; existing Reader callbacks and pager behavior.
- **Modified:** The earlier requested version used two side-by-side columns and a decorative connector. It was visually too dense for the sheet, so I revised the presentation requirement after visual inspection and iterated through a vertical layout to the final compact card.
- **Discarded:** The two-column/ring presentation and any UI that made the comparison band excessively tall.
- **Result:** Final compact comparison card in [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103).

### Example 2 — Home reading-guide rail and featured feed refresh (T1, T3)

**Define** — The normal Home feed needed a clearer entry point to the app's core comparison feature without changing search, pagination, navigation, article data, or the existing tutorial callback. The guide needed to sit under category tabs and above the featured article only when the normal online feed has content.

**Context provided** — The existing Home feed composables, category tabs, featured-article flow, tutorial callback, screenshots of the app, and constraints that the guide must not add a route, list item, recommendation, article score, or new data source.

**Prompt (reformatted for readability)**

```text
Task: Add a reading-guide rail to the normal Android Home feed.

Placement and copy
- Place the rail immediately below category tabs and above the featured
  article cover.
- Use the visible copy “FRAMELESS”, “같은 사건, 다른 표현”, and the short
  explanation that purple expressions can be compared across reports.

Behavioral contract
- Render only when the normal online feed has at least one article.
- Hide it when search is open, a query is active, the app is offline, or
  the feed is empty.
- Make the whole rail a single accessibility button labelled
  “표현 비교 읽는 법” and call the existing onTutorialOpen callback.

Invariants
- Do not add a route, callback, recommendation/ranking signal, article
  score, data source, list item, or change to load-more behavior.
- Keep the rail inside the featured-cover list item.

Validation
- Add focused instrumented tests for visibility conditions, position, and
  exactly-once callback invocation.
```

**Output (excerpt)**

```text
Add a Home-specific guide composable inside the featured-cover list item.
Gate it with the normal-feed condition so it is absent in search, active
query, offline, and empty-feed states. Merge the rail into one semantic
button labelled “표현 비교 읽는 법” and delegate tap handling to the
existing onTutorialOpen callback. Cover the visible state, hidden states,
relative order, and exactly-once callback behavior with instrumentation
tests.
```

**Verify** — I reviewed the diff for the requested boundaries, installed the debug APK, and inspected the Politics and Economy Home screens on the emulator. I ran `FeedReadingGuideInstrumentedTest`, `FeedFeaturedArticleInstrumentedTest`, and `FeedHomeRefreshInstrumentedTest`, followed by `:app:lintDebug` and `:app:assembleDebug`. The focused tests passed; the final PR workflow also passed after rerun.

**Integrate**

- **Kept:** Existing article content, tutorial callback, featured-article behavior, and pagination mechanics.
- **Modified:** The visual motif and copy were refined from the initial generated draft using the actual Home screenshots.
- **Discarded:** Any proposal that would create a new navigation destination, use a recommendation/ranking signal, or render the guide in search/offline/empty states.
- **Result:** Home feed refresh and reading-guide rail in [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103).

### Example 3 — Focused Android verification and evidence boundaries (T3, T4)

**Define** — The PR needed credible evidence that the UI work compiled and behaved as intended. The evidence had to distinguish change-specific tests from a broad pre-existing suite with unrelated failures, and it could not rely on a claim that AI had executed any command.

**Context provided** — The changed Kotlin files, the newly added JVM/instrumentation test classes, Android Studio emulator availability, local Gradle configuration, the existing `NewsScreensInstrumentedTest` baseline issue, and the expected PR/CI workflow.

**Prompt (reformatted for readability)**

```text
Prepare a verification plan for this UI-only change.

Required checks
1. Run the focused JVM tests for ExpressionPair and FeedHomeChrome.
2. Compile Android test Kotlin before device tests.
3. Run only the focused instrumentation classes for expression pairing,
   reading guide, featured article, and Home refresh.
4. Run lintDebug and assembleDebug.
5. Verify the final app visually on the emulator and confirm CI for the PR.

Evidence rules
- Do not use the full NewsScreensInstrumentedTest suite as pass/fail
  evidence because it has unrelated existing failures.
- Do not report a command as passed unless a human has supplied the actual
  local log or CI result.
- Report what was tested, what was intentionally excluded, and why.
```

**Output (excerpt)**

```text
Use targeted test classes as the primary regression oracle:
ExpressionPairTest, FeedHomeChromeTest, ExpressionPairInstrumentedTest,
FeedReadingGuideInstrumentedTest, FeedFeaturedArticleInstrumentedTest,
and FeedHomeRefreshInstrumentedTest. Compile Android test Kotlin first,
then run lintDebug and assembleDebug. Treat NewsScreensInstrumentedTest
as out of scope for this PR's evidence until its unrelated baseline
failures are resolved. Mark results only after receiving terminal or CI
output from the human operator.
```

**Verify** — I ran every selected command locally. The two focused JVM test classes, Android test compilation, all four focused instrumentation classes, lint, and debug assembly returned `BUILD SUCCESSFUL`. I then reran the failed GitHub Actions job; all PR checks became green. I did not describe the broad `NewsScreensInstrumentedTest` as passing or failing for this change.

**Integrate**

- **Kept:** Focused test classes that directly exercise the new presentation and state contracts.
- **Modified:** Replaced a broad, noisy test command with a smaller evidence set tied to the modified code.
- **Discarded:** Any assertion that an AI environment limitation or an unrun command demonstrated a project build problem.
- **Result:** Local verification record and green CI evidence for [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103).

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- AI translated precise visual constraints into focused Compose-level implementation proposals much faster than starting each layout from a blank file.
- It was useful for keeping UI-only changes separated from existing comparison data, Reader callbacks, navigation, and pagination.
- It drafted focused JVM/instrumentation test scenarios and specification wording that I could map to the final user-visible behavior.
- It made iterative redesign inexpensive: I could compare a proposed layout against screenshots and state constraints, then request a smaller or clearer variant.
- The explicit scope/invariant format made it easier to identify whether a proposal changed only presentation or accidentally expanded into a data or navigation change.

### What required human verification or correction

| **Task** | **Issue in AI output** | **How it was detected** | **Correction made** |
| --- | --- | --- | --- |
| T2 | The early side-by-side comparison layout was technically plausible but crowded on a phone-sized Reader sheet. | Manual inspection of the Android Studio emulator screenshots. | Rejected the two-column/ring design; iterated to the shorter, vertically ordered compact card. |
| T3 | An early command list included the broad `NewsScreensInstrumentedTest`, whose existing unrelated failures would obscure the change-specific result. | I recognized the suite was not a meaningful oracle for this PR and checked the actual focused test classes instead. | Excluded that broad suite from the evidence; retained the targeted JVM/instrumentation tests plus lint and assembly. |
| T3 | The AI's own execution environment could not run Gradle, which could be mistaken for a project build limitation. | I ran the selected Gradle commands locally; they returned `BUILD SUCCESSFUL`. | Replaced the AI-environment limitation with actual local terminal logs and CI evidence. |
| T1/T2 | A patch can appear correct in text while either already being applied or not appearing in the installed app. | `git apply --check`, Git status/diff checks, reinstall, and visual inspection. | Stopped reapplying an already-applied patch and verified the final installed build before accepting the UI. |
| T1/T2 | Generated test and documentation language can overstate behavioral coverage. | Final diff review against the requested data/UI boundaries. | Kept claims limited to existing stored data and the focused states/tests actually exercised. |

### Verification practices I used

- [x] Reviewed the final Git diff before committing
- [x] Ran focused JVM tests: `ExpressionPairTest` and `FeedHomeChromeTest`
- [x] Compiled Android test Kotlin
- [x] Ran focused Android instrumentation tests for expression pairing, reading guide, featured article, and Home refresh
- [x] Ran Android lint and assembled the debug APK
- [x] Installed and manually inspected the result on the Android Studio emulator
- [x] Checked [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103) and its successful GitHub Actions checks
- [x] Checked that the UI uses existing stored comparison data and existing callbacks rather than claiming new analysis/data behavior

---

## 5. Reflection on How AI Affected My Development Process

**Productivity** — AI substantially shortened the first-draft cycle for Compose layout, test scaffolding, and feature specifications. The biggest gain was converting detailed UI feedback into a concrete next revision without rebuilding the entire Home or Reader screen manually. It was especially helpful when I wanted several visual alternatives while preserving the same stored data and interaction contract.

**Quality** — AI improved structure and coverage, but it did not guarantee visual quality. The most important correction was rejecting a layout that looked reasonable in code yet occupied too much of the Reader sheet. Final quality came from human visual review, actual Gradle/emulator results, and keeping the change within the existing data/behavior boundaries. In particular, the compact card was accepted only after the card, highlighted expression, pager behavior, and article-opening action were observed in the installed app.

**Team workflow** — The resulting branch and [PR #103](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/103) gave the team one reviewable unit containing the Home refresh, Reader card, specs, and focused tests. AI helped me turn informal feedback into a traceable implementation/test checklist, while Git history, local logs, screenshots, and CI provided the reviewable evidence. I kept the report limited to my own contribution and separated it from other team members' implementation and documentation work.

**Learning** — This iteration reinforced that a prompt should specify both what may change and what must remain invariant. For UI work, “data/trimming unchanged,” existing callback names, state conditions, and screen-size constraints were more valuable than broad requests to “make it prettier.” It also made clear that an AI-generated command sequence is only a test plan; it becomes evidence only when I run it and retain the resulting local or CI output.

### Lessons and plan for next iteration

| **What we learned** | **What we will change next iteration** |
| --- | --- |
| A layout that compiles can still be too dense or unclear on a mobile screen. | Review an emulator screenshot early for every major layout change, including long text and small-screen constraints. |
| Explicit invariants prevent AI-assisted UI work from leaking into data, navigation, or callback behavior. | Put the untouched functions, state conditions, and callback contracts directly in every implementation prompt and review checklist. |
| Broad test suites can hide the signal of a focused UI change when they contain unrelated baseline failures. | Report focused test evidence clearly, run broader suites when their baseline is stable, and never present an excluded suite as passed. |
| AI-generated verification language is not execution evidence. | Treat local build logs, instrumentation results, manual device/emulator checks, and CI results as the only pass/fail evidence. |
| A clean PR narrative is easier to review when scope, implementation, and evidence are linked together. | Keep one concise handoff note that links each UI change to its relevant test class, manual check, and final PR/commit. |
