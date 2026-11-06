# AI Collaboration Contribution — Iteration 3

|                     |                                                                 |
| ------------------- | --------------------------------------------------------------- |
| **Team**            | Team 5 — FrameLESS                                             |
| **Iteration**       | Iteration 3 — Advanced Testing & Evaluation                    |
| **Period**          | October 24, 2026 – November 6, 2026                            |
| **Iteration goals** | Prepare a stable HE release, collect systematic testing evidence, and document post-HE decisions and follow-up work. |
| **Authors**         | Lee Jinsol — Individual contribution                           |

---

## 1. Summary of AI Usage

I used ChatGPT/Codex during Iteration 3 for project-management and documentation work: clarifying the HE release scope, expanding the schedule, structuring testing evidence, preparing the demo README, and checking final submission requirements.

AI outputs were used as drafts, not as evidence or final decisions. I checked the final scope through team discussion and the TA meeting, checked HE findings against completed evaluator sheets, and checked the device-regression record against the actual Galaxy walkthrough and installed APK.

AI was not used to decide whether a news comparison was correct. The fixed 30-article HE dataset, human evaluation of comparison results, and HE findings were kept separate from AI-generated documentation drafts.

### AI tools used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| ChatGPT / Codex | Not recorded | Lee Jinsol | PM planning, documentation drafting, HE/test scope clarification, demo and submission preparation |

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **Contribution level** | **Human owner** |
| --- | --- | --- | --- | --- | --- |
| T1 | Clarify HE release scope and distinguish the fixed HE dataset from the post-HE issue-map pipeline | Schedule, Requirements/Design updates, TA meeting materials | Drafted explanations and planning structure | Medium | Lee Jinsol |
| T2 | Expand the Iteration 3 schedule with HE preparation, internal meeting, TA meeting, release, and documentation tasks | Project Schedule | Drafted task breakdown and status structure | Medium | Lee Jinsol |
| T3 | Structure testing evidence across automated testing, human evaluation, HE, and physical-device regression | Testing Documentation Wiki | Drafted sections and evidence tables | Medium | Lee Jinsol |
| T4 | Prepare demo/release documentation and final eTL submission checklist | `iteration-3-demo` README, eTL ZIP preparation | Drafted README and packaging checklist | Medium | Lee Jinsol |

---

## 3. Representative Prompts and Outputs

### Example 1 — HE dataset scope and post-HE pipeline decision (T1)

**Define** — We needed to explain why the HE release used a fixed dataset rather than the newly proposed issue-map pipeline. The explanation had to distinguish usability evaluation from later data-generation and AI-quality work.

**Context provided** — Team meeting notes, the 30-article curated HE dataset, the new issue-map pipeline proposal, HE submission requirements, and TA feedback.

**Prompt summary**

```text
Clarify the difference between the fixed 30-article dataset used for
Heuristic Evaluation and the new issue-map pipeline proposed for later
analysis. Explain what should be included in the HE release, what should
be deferred, and how to document this decision without implying that the
new pipeline already generated the HE content.
```

**Output (excerpt)**

```text
Use the fixed, verified 30-article dataset for HE so every evaluator
encounters stable articles, evidence, and comparison payloads. Treat the
issue-map pipeline as a post-HE direction: validate it on a small sample
before considering dataset expansion or regeneration.
```

**Verify** — I checked the output against the team’s October 31 discussion, the actual HE release preparation, and the November 4 TA meeting. The team confirmed that the HE app used the fixed curated dataset rather than the new pipeline.

**Integrate**

- **Kept:** The distinction between stable HE content and later pipeline validation.
- **Modified:** Replaced broad “DB upgrade” wording with the concrete fixed-30-article scope.
- **Discarded:** Any implication that the new pipeline had already generated the HE release.
- **Result:** Reflected in the project schedule, TA-meeting slides, Testing Documentation, and Iteration 3 scope notes.

### Example 2 — Testing evidence and physical-device regression record (T3)

**Define** — We needed a testing-documentation structure that separated implementation checks from AI-result evaluation and usability evaluation. The physical-device test also needed to reflect the actual demo walkthrough rather than a hypothetical test plan.

**Context provided** — The Iteration 3 announcement, completed HE evaluator sheets, Junseo’s five-event human-evaluation set, build/APK results, and the Galaxy demo video.

**Prompt summary**

```text
Organize Iteration 3 testing evidence into separate sections for:
(1) automated and integration testing,
(2) human evaluation of AI comparison results,
(3) in-class Heuristic Evaluation, and
(4) physical-device regression testing.

Do not treat HE as proof of semantic AI correctness. Use the actual
Galaxy walkthrough as evidence for the device regression flow.
```

**Output (excerpt)**

```text
Document the four forms of evidence separately. For physical-device
regression, record the installed APK, network/server condition, and the
end-to-end flow: Feed → Reader → expression highlight → comparison
sheet → original source → saved articles.
```

**Verify** — I checked the HE summary against the completed evaluator sheets and checked the physical-device flow against the recorded Galaxy walkthrough. I also confirmed that the human evaluation of AI comparison outputs uses a separate five-event evaluation set.

**Integrate**

- **Kept:** The four-part testing structure and the end-to-end device-flow table.
- **Modified:** Replaced generic or planned test claims with the actual completed HE sheets and demo evidence.
- **Discarded:** Wording that treated usability findings as evidence that all AI comparison results were semantically correct.
- **Result:** Added to the Iteration 3 Testing Documentation update.

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- AI quickly converted course announcements and team discussions into structured schedules, checklists, and documentation drafts.
- AI was useful for separating automated testing, human evaluation of AI outputs, HE, and physical-device regression.
- AI helped produce a first draft of the demo README and eTL submission checklist.

### What required human verification or correction

| **Task** | **Issue in AI output** | **How it was detected** | **Correction made** |
| --- | --- | --- | --- |
| T1 | Early wording could blur the fixed HE dataset and the new issue-map pipeline. | Team discussion and TA-meeting review. | Documented that HE used the fixed 30-article dataset and deferred issue-map validation to post-HE work. |
| T3 | A draft could make HE appear to evaluate AI semantic correctness. | Checked the course HE sheet and testing requirements. | Separated HE usability findings from human evaluation of AI comparison results. |
| T3 | Generic metric suggestions did not exactly match Junseo’s established five-event evaluation spreadsheet. | Reviewed the actual evaluation set and its labeling instructions. | Used the designated human-review labels and aggregate metrics from the evaluation set. |
| T4 | Documentation drafts can look correct while formatting incorrectly in GitHub Wiki or failing filename conventions. | GitHub Wiki preview and final submission checklist review. | Checked rendered Markdown and used the required eTL filenames exactly. |

### Verification practices we used

- [x] Checked HE claims against completed evaluator sheets
- [x] Checked the physical-device regression record against the recorded Galaxy walkthrough
- [x] Checked documentation against the Iteration 3 announcement and TA feedback
- [x] Reviewed Wiki formatting in GitHub Preview
- [x] Discussed scope, testing, and release decisions with team members
- [x] Kept private raw evaluation materials separate from the public Wiki

---

## 5. Reflection on How AI Affected Our Development Process

**Productivity** — AI reduced the time required to turn meeting notes, course announcements, and screenshots into structured schedule rows, checklists, and documentation drafts. This was especially useful during the short HE-to-submission period.

**Quality** — AI improved the completeness and organization of early drafts, but its output needed careful human review. The most important corrections involved evidence boundaries: usability, implementation health, and AI-comparison correctness are different evaluation targets.

**Team workflow** — AI drafts gave the team a starting point for discussing scope and documentation. Final decisions came from team meetings, completed HE sheets, actual app behavior, and the assigned owners’ testing results.

**Learning** — AI helped me distinguish a fixed evaluation dataset from a future data-generation pipeline, and document different types of testing without overstating their conclusions.

### Lessons and plan for next iteration

| **What we learned** | **What we will change next iteration** |
| --- | --- |
| A stable HE dataset and an experimental generation pipeline should not be described as the same system state. | State the evaluation dataset, release scope, and experimental pipeline separately in every plan and document. |
| AI-generated documentation can overstate what a test actually proves. | Tie every claim to a specific evaluator sheet, test output, device walkthrough, or reviewed source artifact. |
| Formatting and filenames can create submission risk even when content is correct. | Perform a final rendered-Wiki review and exact filename/ZIP checklist before submission. |
