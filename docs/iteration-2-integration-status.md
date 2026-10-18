# FrameLESS Iteration 2 integration status

**Baseline:** `main` at [`2495c3a`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/2495c3a), 2026-10-19 KST. This is a status snapshot, not a claim that the eventual demo branch or Wiki has been submitted. See the [submission checklist](iteration-2-submission-checklist.md) for handoff work.

## Product and implemented path

FrameLESS starts from a Korean news article chosen by the reader. The server retrieves reports about the same event, compares passages from extracted article text, and returns the original article links. The app lets the reader inspect those passages. It does **not** decide which source is true, neutral, or politically biased.

The current Reader path examines the source article's sentences and aligns source content units against up to three displayed related articles. A–G highlights mark verified wording differences between corresponding passages. A source-only I1 star is shown only after at least two independently verified comparison articles were scanned, no article was found to cover that content (including rebuttal), and a separate absence review passed. Missing analysis remains unverified instead of becoming an `absent` result. I2–I5 belong to the older rubric path and are not Reader annotations. See [quality design](analysis-v3-quality-plan.md) and [pipeline architecture](analysis-pipeline-architecture.md).

## Verified integration, with limits

| Area | Evidence at this baseline | Limit |
| --- | --- | --- |
| Code | Android Reader UI and Django Reader response are integrated on `main`; the recent search, latency, and extraction fixes were merged in [PRs #27–#33](https://github.com/snuhcs-course/swpp-2026-project-team-05/pulls?q=is%3Apr+is%3Amerged). | A merged path does not prove semantic correctness on arbitrary news. |
| Automated checks | At `2495c3a`, 271 Django tests and the Django/Android CI jobs passed, according to the integration run recorded with [PR #33](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/33). | Synthetic contracts do not measure whether two passages truly address the same content. The final submission revision needs its own recorded run in [Testing Documentation](testing-documentation-iter2.md). |
| Production API | The existing [Vercel API](https://frameless-team05-api.vercel.app/api/health) was manually deployed after PR #33. One 2026-10-19 public-article request returned a Reader result in about 154.9 seconds with three related articles, two comparison issues, and twelve sentence pairs. | One run does not establish the NFR-02 ten-attempt latency target or I1/A–G accuracy. Search results and model time vary. The release log should retain the exact `X-Analysis-ID` for reproducibility. |
| Android demo | The Reader is connected to the response contract; a real emulator recording is being prepared separately. | Until that recording is checked against the same build and production response, do not label an older synthetic screenshot as the final live demo. |

The initial 2026-10-18 `e6de3f5` runs in this document's earlier revision had a partial rubric result and an emulator certificate-trust failure. They describe that **historical** build, not the current `2495c3a` Reader API. Their logs remain relevant to the change history, but should not be pasted into the Iteration 2 submission as current status.

## Before the Iteration 2 handoff

1. Freeze the Android APK and backend revisions used in the recording. Record the article URL, device/emulator, date, elapsed time, response status, `X-Analysis-ID`, related-article count, `reader.coverage`, `reader.i1_status`, and any partial failures. Confirm the video shows the same build.
2. Have two people inspect selected linked original passages, relation labels, A–G differences, and any I1 star. A literal quote proves provenance, not the model's interpretation. Keep uncertain and absent cases separate.
3. Rerun tests and coverage on the frozen revision; update [Testing Documentation](testing-documentation-iter2.md) with that revision, actual failures, and evidence. The current Wiki [Requirements](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications) still specifies the Iteration 1 flow and a 120-second NFR-02 target. The observed API run exceeds 120 seconds; leave that target and result visible until the team explicitly revises it.
4. Update the separate Wiki pages, demo branch README/video, and eTL package using the [checklist](iteration-2-submission-checklist.md). The [Wiki update draft](iteration-2-wiki-update-draft.md) identifies concrete changes without changing the live Wiki yet.
