# FrameLESS midterm presentation — speaker outline

**Draft date:** 18 October 2026 (KST). **Deck:** [11 editable slides](midterm-presentation-draft.pptx), [slide preview](midterm-presentation-preview.png). The course notice [S0] calls for an 8-minute English talk, 3-minute Q&A, and slide submission by 19 October 23:59 KST.

**Main point:** FrameLESS starts with one Korean news article and shows source-linked passages from reports of the same event. Readers inspect differences themselves; the app does not give an article-wide truth or bias score.

**Evidence boundary:** [PR #10](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/10) was merged into `main` at `6b75cb9`, then deployed to the existing Vercel production URL on 18 October. One real API request returned HTTP 200 in 46.3 seconds with three input claims, three related articles, and an available comparison. `rubric_analysis` failed validation, so `execution_status` was `partial`. An Android request is still blocked by an HTTPS certificate trust error on the current local emulator. Test Android on a suitable network or device. Slide 8 remains a **synthetic-response UI preview**, not a live Android or complete NAVER/Gemini demo. Confirm the final backend and APK revisions, source accuracy, and complete walkthrough before replacing it with a recorded v2 demo.

| Slide | Time | Spoken point |
| --- | ---: | --- |
| 1. FrameLESS | 0:15 | Start from a reader-selected article; compare passages and sources, not a verdict. |
| 2. The comparison gap | 0:35 | Existing discovery and coverage views help, but finding passages that address one claim still takes effort [S1, S2]. |
| 3. From URL to source | 0:40 | Paste URL → find same-event reports → compare a claim → open the original page [S1]. |
| 4. Iteration 1 baseline | 0:35 | The first app/API worked. Missing-versus-unavailable evidence motivated a clearer rubric [S4]. |
| 5. Two kinds of comparison | 0:45 | I1–I5 tracks information; A–G compares expression. Insufficient evidence stays unrated. P3 question grouping is still a proposal [S5]. |
| 6. System architecture | 0:40 | Android calls Django; server-side NAVER/Gemini analysis returns source-linked JSON. Keys stay on the server [S4, S6]. |
| 7. Keeping evidence attached | 1:05 | The v2/Android contract mismatch could misattribute a quote. Stable IDs, source URL, quote and status now travel together. Offline tests check this contract [S3, S6]. |
| 8. Current UI preview | 1:30 | Show the clearly labeled synthetic screen. The first real API run produced comparison data, but the rubric failed validation; source review and Android are pending [S3]. |
| 9. Team and integration status | 0:50 | Credit each person's work. PR #10 is merged and v2 is deployed. The first API run was partial; the Android request is blocked by local TLS trust [S3, S7, S8]. |
| 10. Before we call it a demo | 0:35 | Fix and rerun the rubric, inspect cited passages, then verify Android on an approved network or device and record the result [S3, S9]. |
| 11. AI in the workflow | 0:30 | AI helped draft this integration. Review found a relation mismatch between two cards, which we fixed and covered with a regression test. Human review must judge the comparisons. Confirm teammates’ own examples before speaking for them [S10]. |

The timings total **8:00**. Speaker notes in the deck contain source links and reminders; keep them out of the visible slides. If a verified live recording becomes available, shorten the narration on slide 8 and use the 1:30 slot for the clip.

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
- **[S3] Preliminary integration testing:** [Testing Documentation draft](testing-documentation-iter2.md) and its [synthetic Android screenshot](testing-assets/android-synthetic-rubric.png). Commit `5eb473e` passed 64 Django tests, 3 CI-gate tests, 25 Android JVM tests, lint, and an APK build locally. The first live production API run returned HTTP 200 in 46.3 seconds with an available comparison across three input claims and three related articles; `rubric_analysis` failed validation and the overall status was `partial`. Android remains unverified because of a local emulator HTTPS certificate trust error. The screenshot is still synthetic. Rerun and revise these facts after code or environment changes.
- **[S4] Iteration 1 architecture, status, and limitations:** [FrameLESS Design Documentation Wiki](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation), especially §§2 and 4.
- **[S5] Adopted rubric implementation and proposal boundary:** [v2 rubric package](../server/analysis/v2/rubric_analysis.py) (`EXPRESSION_CATEGORIES`, `PROPOSALS`, and I1–I5 result assembly); Seoyoung's [team Slack rubric discussion](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1792113199423159).
- **[S6] Integrated v2 code:** [v2 adapter](../server/analysis/v2_adapter.py), [Django route](../server/views.py), [Android response model](../android/app/src/main/java/com/swpp/team5/frameless/data/AnalysisModels.kt), and [Android comparison state](../android/app/src/main/java/com/swpp/team5/frameless/ui/state/AnalysisUiState.kt). This code is merged into `main`; the server was deployed to Vercel, while the Android and live analysis path still need verification.
- **[S7] Team assignment and schedule:** [team Slack post](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1791896799209629).
- **[S8] Teammate branches:** [`feature/ui-v02-jinsol`](https://github.com/snuhcs-course/swpp-2026-project-team-05/tree/feature/ui-v02-jinsol), [`feature/llm-article-comparison-v2`](https://github.com/snuhcs-course/swpp-2026-project-team-05/tree/feature/llm-article-comparison-v2).
- **[S9] Iteration 2 deliverables:** [SWPP announcements Slack post](https://swpp2026fall.slack.com/archives/C0BUL8N1864/p1791763218720289). The team still has to update Wiki requirements/design/standup, publish separate testing documentation, prepare the `iteration-2-demo` branch/video/README, and submit the specified PDF/XLSX ZIP by the course deadline.
- **[S10] Current AI report:** [AI Collaboration Report Wiki](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/AI-Collaboration-Report), which currently contains only its Iteration 1 heading; it is not evidence of specific past tool use.

**Inputs needed before slide lock:** a successful rerun of the rubric, human review of cited source passages, and a real Android walkthrough on an approved network or device, or an explicit plan to label the Iteration 1/synthetic fallback; deployed backend and final APK revisions; confirmed team roles and each member's actual AI-use example; a checked course notice and any slide-template rule.
