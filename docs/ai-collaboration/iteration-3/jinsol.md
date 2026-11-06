# Lee Jinsol — AI Collaboration Contribution, Iteration 3

## Role

I served as the Iteration 3 PM. My responsibilities were coordinating the HE release scope and team tasks, maintaining the schedule and submission checklist, documenting HE/testing evidence, recording the physical-device walkthrough, and preparing the final eTL package.

## AI Tools Used

- ChatGPT/Codex
- Claude

I used AI for drafting, structuring, clarification, and document preparation. I did not treat AI output as final evidence or as an implementation decision without checking it against the application, team discussion, course requirements, or source materials.

## Representative AI-Assisted Work

| Task | Prompt / request summary | AI-assisted output | Human review and final action |
| --- | --- | --- | --- |
| Iteration 3 scope and task planning | Clarified the difference between the fixed HE dataset and the post-HE issue-map pipeline; broke the Iteration 3 schedule into concrete tasks. | Draft schedule rows, HE release checklist, task allocation, and explanation of the scope decision. | I reviewed the schedule against actual meetings, deadlines, and completed work. The team decided to use the fixed 30-article dataset for HE and defer issue-map pipeline expansion until after validation. |
| HE preparation and findings | Organized the HE workflow, evaluator handoff, release readiness checklist, and usability findings. | Drafted a structured summary of HE methods, severity levels, findings, and possible follow-up actions. | I checked the actual evaluator sheets and preserved the class HE results as the source. The final summary distinguishes usability findings from AI-comparison correctness. |
| Physical-device regression record | Converted the recorded Galaxy walkthrough into a reproducible testing record. | Drafted the regression-test structure and tested-flow table. | I installed and used the HE-release APK on a physical Galaxy device, recorded the demo flow, and confirmed Feed → Reader → highlight → comparison sheet → original source → saved articles. |
| Demo and release documentation | Prepared the demo README and clarified the required contents of the `iteration-3-demo` branch and eTL ZIP. | Draft README structure, run instructions, demonstrated-feature list, and submission-file checklist. | I checked the actual demo video and app flow. Seungmin pushed the finalized demo branch; I used the required eTL filenames when assembling the final submission. |
| Testing documentation and final packaging | Organized the four Iteration 3 testing areas and the eTL deliverables. | Drafted section structure for automated/integration testing, human evaluation of AI results, HE, physical-device testing, and the final ZIP checklist. | I kept automated-test results, human evaluation, HE, and device regression as separate evidence types. I will export the final Wiki documents and verify filenames before eTL submission. |

## Key Decisions and Human Verification

### HE dataset versus post-HE pipeline

The HE release used a fixed, curated 30-article dataset with known article, evidence, and comparison payloads. This was necessary so evaluators could assess usability under stable conditions.

The new issue-map pipeline was not used to generate the HE dataset. It remains a post-HE direction that requires small-sample validation before any broader dataset generation or replacement decision.

### Evidence boundaries

- Automated tests verify implementation behavior, build health, and failure handling.
- Human evaluation assesses whether AI-generated comparison results are appropriate.
- Heuristic Evaluation assesses usability of the current application.
- Physical-device regression verifies that the core user flow works on the installed Android APK.

These are complementary forms of evidence. None alone establishes that every AI-generated comparison is semantically correct.

## Limitations and Corrections

- AI drafts occasionally used overly broad or unclear wording. I revised or removed claims that were not supported by the evaluator sheets, application behavior, or team decisions.
- The original prompt-log and documentation structure required clarification during Iteration 3. I confirmed that the public Wiki contains representative summaries, while private raw materials and evaluation artifacts are not uploaded publicly.
- Final claims in the submission are based on the team’s actual test results, evaluator sheets, device walkthrough, and reviewed documents rather than AI-generated text alone.
