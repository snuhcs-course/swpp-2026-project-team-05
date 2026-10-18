# FrameLESS midterm presentation outline

**Draft date:** 18 October 2026 (KST). **Format:** 11 slides, English, 8 minutes of presentation plus 3 minutes of Q&A. The slide file is due 19 October at 23:59 KST according to the course notice shared in the team Slack [S0]. This is a content outline, not a completed deck. Refresh all status statements and capture the final demo evidence before export.

**One-sentence thesis:** FrameLESS starts from a Korean news article selected by the reader and puts source-linked passages from same-event coverage beside its claims, so the reader can inspect reporting differences without an article-level truth or political-bias verdict.

**Evidence rule for the deck:** Describe a feature as *implemented locally* only when it is in the integration commit; describe it as *verified end to end* only after an Android run against a configured v2 backend. As of this draft, tested candidate `91d7c29` passed 58 Python tests and 25 Android JVM tests and built an APK. The synthetic-response emulator walkthrough used the same production code from integration commit `ef73309`; the later commits add CI and tests. One real article's **input fetch/extraction stage** also passed. A real NAVER/Gemini v2 request and deployed v2 demo have **not** been signed off because local service keys are unavailable [S3]. Never show the synthetic screenshot or clip as a live LLM result.

## Slide 1 — FrameLESS: Compare the Reporting, Inspect the Sources (0:15)

- Android app for readers of Korean online news.
- Start with one article URL; compare it with reports of the same event.
- Team 05: Seoyoung Do, Junseo Heo, Jinsol Lee, Seungmin Kim.

**Visual / evidence:** Product name, one clean device frame. Use a final app screenshot only after the demo build is frozen; otherwise a clearly marked design mockup. Project framing: [S1].

## Slide 2 — Why One Article Is Not Enough (0:35)

- A reader can find more coverage, but comparing corresponding claims and original wording takes work.
- Our Iteration 1 competitive review considered NAVER News for discovery and Ground News for cross-source coverage and outlet-level ratings.
- FrameLESS focuses on the reader's chosen article and the passages that address its particular claims.

**Visual / evidence:** Three-column workflow comparison: “discover articles” / “compare coverage” / “inspect claim-linked original passages.” Phrase this as our product positioning, not a claim that no competitor can ever provide a similar feature. Ground News's own documentation describes story coverage and bias/factuality filters [S2]; FrameLESS's intended distinction is specified in [S1]. Do not add unverified market size or usage statistics.

## Slide 3 — The Reader Journey: URL to Evidence (0:40)

- Paste a public Korean news URL; the app validates it and starts analysis.
- Find and screen same-event articles; group responses around claims in the selected article.
- Open the exact cited passage and its source-page link; show an honest empty or error state when evidence is insufficient.

**Visual / evidence:** Four-step flow drawn from [S1]: URL → event and claim extraction → same-event search and screening → comparison cards with source links. If showing the UI, label the build/revision. Avoid wording that implies the app fact-checks claims.

## Slide 4 — What Iteration 1 Delivered—and Revealed (0:35)

- Delivered: URL input, same-event retrieval, source-claim comparison, linked evidence, and a deployed Django API.
- Known gaps: related-only claims were outside the comparison; per-source absence and the insufficient-coverage state needed clearer handling.
- The first pipeline was a baseline for studying a more explicit comparison method, not a validated neutrality classifier.

**Visual / evidence:** A two-column “working baseline / observed gap” card. Use the historical [Iteration 1 demo video](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/iteration-1-demo/docs/demo/iteration-1-demo.mp4) only with an “Iteration 1” label. The status and limitations are documented in [S4].

## Slide 5 — Iteration 2: An Explicit Evidence Rubric (0:45)

- **I1–I5, information:** whether a claim appears, its placement, background, forecasts, and whose voice is quoted.
- **A–G, expression:** compare reporter wording such as reporting verbs, event labels, modifiers, and certainty against observed wording in other articles.
- Keep “not found,” uncertain attribution, and unscored expression distinct; no article-wide score.

**Visual / evidence:** Two-column I1–I5 / A–G diagram, with one short *synthetic, visibly labeled* passage-pair example. The currently adopted code lists I1–I5/A–G and treats P3 question grouping as a proposal [S5]. The slide must say “claim-level rubric”; do not present question-level clustering as shipped unless it is implemented and verified before deck lock.

## Slide 6 — Architecture: Thin Android Client, Server-Side Analysis (0:40)

- Kotlin/Jetpack Compose sends an article URL to the Django `POST /api/analyze` endpoint.
- The Python server fetches article text, searches via NAVER API HUB, and uses Gemini for structured event, claim, relation, and rubric analysis.
- The server returns source-linked JSON; API keys remain server-side.

**Visual / evidence:** Android → Django/Vercel → article sites + NAVER API HUB + Gemini → JSON → Android. Distinguish the Iteration 1 deployed backend from the local v2 integration; the production deployment is not proof that v2 has been deployed. Architecture in [S4], current integration path in [S6].

## Slide 7 — Technical Depth: Preserving Evidence Across the v2 Contract (1:05)

- **Problem:** the new Python pipeline and Android UI used different result structures; naïve mapping could attach a quote to the wrong article or turn an unselected article into “no match.”
- **Choice:** map stable claim and article IDs, verified original quotes, relation and speaker attribution, and explicit `available` / `partial` / `unavailable` states into the Android response.
- **Observed result:** tested candidate `91d7c29` passes the full offline suites (58 Python and 25 Android JVM tests) and builds an APK. A synthetic v2 response was rendered on an emulator with the same production code from `ef73309`. A real article input fetch passed; NAVER/Gemini v2 search, model output quality, and complete live integration remain unverified.

**Visual / evidence:** One claim node branching to two article cards with highlighted quote, article URL, and status; beside it, a small “unverified ≠ absent; unscored ≠ zero” callout. Cite implementation [S6] and preliminary test report [S3]. Do not state that exact quote presence proves the claim's truth or that model relation judgments are always correct.

## Slide 8 — Demo: Follow One Claim to Its Original Passage (1:30)

- Start from a real public article URL, then show the result, one comparison card, a rubric item, and the linked source page.
- Show the selected article, quote, and relation together; pause on any uncertain or partial status if it appears.
- End at the linked source page rather than an unsupported “correct/bias” verdict.

**Visual / evidence:** Replace this slot with a **recorded live v2 walkthrough only after the live-demo checklist below passes**. If that cannot be completed by slide lock, show the historical Iteration 1 end-to-end clip as such and, if useful, a separate short **“Synthetic v2 UI preview — mocked response, no live NAVER/Gemini analysis”** clip or screenshot [S3]. A synthetic preview alone must not be called a working end-to-end v2 demo.

## Slide 9 — Team Execution and Current Integration Status (0:50)

- Seoyoung: comparison rubric; Junseo: v2 analysis pipeline; Jinsol: v2 Android UI; Seungmin: backend/client integration, verification, and demo coordination.
- October 18 local status: Jinsol's UI branch and Junseo's v2 package are connected through the Django API in integration commit `ef73309`; offline tests and synthetic emulator navigation pass.
- Open issues: live v2 credentials/deployment and validation, model-quality review, source/article attribution, and measured UI/pipeline test gaps.

**Visual / evidence:** Four-lane responsibility strip and a small “local / offline verified / live pending” progress board. The current work is in [draft integration PR #10](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/10); Junseo's [PR #9](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/9) remains open as the source-work reference. Neither PR is proof that the integrated branch is merged. Refresh PR status and commit IDs at deck lock. Assignment context in team Slack [S7], code branches in [S8], test gaps in [S3]. Do not invent completed milestones or percent complete.

## Slide 10 — Next Steps: Turn an Integrated Build into a Reliable Demo (0:35)

- Verify a configured v2 request and Android walkthrough; capture build, backend revision, URL, outcome, time, and `X-Analysis-ID`.
- Review same-event selection and claim/rubric outputs on representative Korean news cases; fix wrong or missing evidence before tuning wording.
- Add deterministic pipeline and device UI tests, then update the Iteration 2 Wiki/testing package and freeze a reviewable demo branch.

**Visual / evidence:** Three gates: live run → human evidence review → reproducible release. Quality targets and known test gaps are in [S1], [S3], and [S4]. Iteration 2 delivery requirements are in the course Slack notice [S9]. Do not show a completion checkmark before the gate is actually passed.

## Slide 11 — AI-Assisted Development: Useful, but Audited (0:30)

- In the product, Gemini proposes structured analyses; Python checks references and literal evidence, while readers inspect the linked sources.
- In development, AI assistance can accelerate integration and test drafting; a human must still review contracts, failure semantics, and the final demo.
- The lesson from this integration: passing mocked tests supports interface confidence, not correctness of model judgments or real service availability.

**Visual / evidence:** A short “AI proposes → code validates → people inspect” loop, with one concrete example from the v2 contract [S6]. Before presenting this as a **team-wide historical reflection**, each member should confirm the actual tools/prompts they used and one accepted or rejected suggestion. The current AI Collaboration Wiki has no substantive Iteration 1 account yet [S10]; do not invent one. The Iteration 2 prompt log/report should document the real process [S9].

## Live-demo sign-off before replacing Slide 8's placeholder

1. Use the exact candidate backend revision and Android APK intended for the video; confirm `GET /api/health` reports configured credentials. Health alone does not establish service connectivity.
2. Submit at least one prepared, publicly accessible Korean article URL to `POST /api/analyze` with v2 enabled. Record the date, safe URL, backend and APK revisions, response status, `X-Analysis-ID`, elapsed time, and whether a same-event article was actually returned.
3. Inspect the returned source title, `article_index`/claim ID, literal quote, relation, speaker attribution, linked source URL, I1–I5/A–G item, and partial/unknown states against the fetched articles. A quoted span proves provenance, not truth.
4. Repeat the same response in the Android app on an emulator or test device. Check input → loading → result → claim detail → source link, plus no-results and recoverable-error behavior. Capture a screen recording and one still from that exact run.
5. If search/model/article fetch fails, keep the failure as a recorded test outcome, fix or choose another prepared article, and rerun. Do not substitute a fixture while calling the clip “live.”

## Source notes for the slide author

- **[S0] Course midterm notice:** [SWPP announcements Slack post](https://swpp2026fall.slack.com/archives/C0BUL8N1864/p1791813453833289). Course requirements were relayed in the task context: 9–11 English slides, 8-minute talk + 3-minute Q&A, title/concept, motivation/competition, features, demo video, architecture, a technical-depth problem/choice/result, project management roles/issues/PR/progress, next steps, and AI-development reflection. Verify the final deck against the notice before submitting.
- **[S1] Product and requirements:** [FrameLESS Requirements and Specifications Wiki](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications), including FR-01–FR-06, NFR-04–NFR-07, audience, and Iteration 1 competitive review.
- **[S2] Competitor's own description:** [Ground News: How Does Ground News Help Readers See News Differently?](https://help.ground.news/en/articles/485057), which describes story-level coverage, source comparison, and bias/factuality context. This supports only the high-level comparison above.
- **[S3] Preliminary integration testing:** [Testing Documentation draft](testing-documentation-iter2.md) and its [synthetic Android screenshot](testing-assets/android-synthetic-rubric.png). The draft records tested candidate `91d7c29`, 58 passing Python tests, 25 passing Android JVM tests, an APK build, one synthetic emulator walkthrough on the same production code, and one real article input-fetch check. It explicitly does not claim a live v2 search/Gemini result. Rerun and revise these figures if the submission code changes.
- **[S4] Iteration 1 architecture, status, and limitations:** [FrameLESS Design Documentation Wiki](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation), especially §§2 and 4.
- **[S5] Adopted rubric implementation and proposal boundary:** [v2 rubric package](../server/analysis/v2/rubric_analysis.py) (`EXPRESSION_CATEGORIES`, `PROPOSALS`, and I1–I5 result assembly); Seoyoung's [team Slack rubric discussion](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1792113199423159).
- **[S6] Local integration code:** [v2 adapter](../server/analysis/v2_adapter.py), [Django route](../server/views.py), [Android response model](../android/app/src/main/java/com/swpp/team5/frameless/data/AnalysisModels.kt), and [Android comparison state](../android/app/src/main/java/com/swpp/team5/frameless/ui/state/AnalysisUiState.kt). These local links reflect the worktree, not a deployed release.
- **[S7] Team assignment and schedule:** [team Slack post](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1791896799209629).
- **[S8] Teammate branches:** [`feature/ui-v02-jinsol`](https://github.com/snuhcs-course/swpp-2026-project-team-05/tree/feature/ui-v02-jinsol), [`feature/llm-article-comparison-v2`](https://github.com/snuhcs-course/swpp-2026-project-team-05/tree/feature/llm-article-comparison-v2).
- **[S9] Iteration 2 deliverables:** [SWPP announcements Slack post](https://swpp2026fall.slack.com/archives/C0BUL8N1864/p1791763218720289). The team still has to update Wiki requirements/design/standup, publish separate testing documentation, prepare the `iteration-2-demo` branch/video/README, and submit the specified PDF/XLSX ZIP by the course deadline.
- **[S10] Current AI report:** [AI Collaboration Report Wiki](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/AI-Collaboration-Report), which currently contains only its Iteration 1 heading; it is not evidence of specific past tool use.

**Inputs needed before slide lock:** a verified live-v2 screen recording or an explicit plan to label the Iteration 1/synthetic fallback; final submission commit and PR status; confirmed team roles and each member's actual AI-use example; a checked course notice and any slide-template rule.
