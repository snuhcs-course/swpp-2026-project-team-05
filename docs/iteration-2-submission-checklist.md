# Iteration 2 submission status

**Checked:** 23 October 2026 (KST)

**Assignment deadline:** Friday, 23 October, 23:59

## GitHub requirements

| Requirement | Status | Notes |
| --- | --- | --- |
| Requirements & Specifications Wiki | Updated | Iteration 2 scope, acceptance criteria, and I1 evidence threshold are documented. |
| Design Documentation Wiki | Updated | Rev.2.1 describes the account/history API, current Reader behavior, and open implementation limits. |
| Testing Documentation Wiki | Updated | Records the latest reviewed CI run and an older live walkthrough with the app/backend revisions and limitations. |
| Daily Standup Meeting Log Wiki | Updated through 20 October | Do not infer meeting minutes from commits. Add a later meeting only from an actual meeting record. |
| `iteration-2-demo` branch and root `README.md` | Published | README includes setup, environment, implemented features, iteration progress, and the linked demo video. |
| Short demo video | Included in branch | The 68.5-second clip is the recording linked from slide 4 of the midterm deck. It discloses that the loading interval was shortened and describes the demonstrated C and I1 marks and partial-search warning. It does not show other marks or article swiping. |

## eTL package

The local ZIP is `team5-iter2.zip` and contains the five requested files:

- `team5-iter2-reqspec.pdf`
- `team5-iter2-design.pdf`
- `team5-iter2-testing.pdf`
- `team5-iter2-schedule.xlsx`
- `team5-iter2-AI-collaboration-report.pdf`

The archive contains exactly these five files. The schedule export shows 11/11 planned Iteration 1 items and 15/15 planned Iteration 2 items complete. The AI report is one page and links to the prompt log; saved prompts, summaries, and the reconstructed Reader-spec prompt are distinguished there.

## Results to describe accurately

- The latest reviewed GitHub Actions run is [Verify #145](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/39573353749) on `07586b0`. Django configuration and all 314 Django tests passed. The backend coverage gate did not pass: the endpoint/adapter group measured 79.0% against an 85% target. Android compilation stopped in `AnalysisViewModelTest.kt`; Android tests, lint, and APK build did not complete.
- The manual Android walkthrough in the Testing Documentation used an earlier APK/backend pair. It showed the Reader sheet, swiping, and scrolling for one partial result, but did not show A–G or I1 marks or meet the 120-second target.
- The demo video is a separate earlier recording. It shows two C marks and three I1 stars in one article result; it is not evidence that all marker types or current backend behavior were tested.
- The latest standup entry is 20 October. The eTL ZIP is prepared locally; upload and receipt are not evidenced in this repository.

## Final checks before eTL upload

1. Upload the ZIP and verify the eTL receipt before the deadline.
2. If a standup meeting occurred after 20 October, add its log from the actual notes.
3. Keep the coverage failure, Android build failure, demo recording scope, and unverified upload status visible when describing completion.
