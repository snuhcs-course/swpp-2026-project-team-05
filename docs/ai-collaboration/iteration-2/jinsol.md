
# AI Collaboration Contribution — Iteration 2

|                     |                                                                    |
| ------------------- | ------------------------------------------------------------------ |
| **Team**            | Team 5 — FrameLESS                                                |
| **Iteration**       | Iteration 2 — Evidence-Oriented Reader Integration                |
| **Period**          | October 10, 2026 – October 23, 2026                               |
| **Iteration goals** | Integrate the Reader flow, validate evidence-oriented comparison, and update the requirements/UI documentation. |
| **Authors**         | Lee Jinsol — Individual contribution                              |

---

## 1. Summary of AI Usage

I used ChatGPT/Codex and Claude during Iteration 2 to refine the Android Reader experience, review UI states for source-linked comparison, and update the Requirements & Specifications documentation. AI was used to generate drafts of UI structures, wording, requirement tables, and troubleshooting hypotheses. I treated those outputs as implementation candidates rather than verified answers, and checked them against the running Android app, live analysis results, TA feedback, and the GitHub Wiki preview.

AI was not used to determine whether a news claim was true, politically neutral, or sufficiently supported. Decisions about evidence sufficiency, withheld marks, and final wording were reviewed manually by the team.

### AI tools used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| ChatGPT / Codex | Not recorded | Lee Jinsol | UI review, requirements drafting, troubleshooting |
| Claude | Not recorded | Lee Jinsol | Android UI iteration and component-level implementation assistance |

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **Contribution level** | **Human owner** |
| --- | --- | --- | --- | --- | --- |
| T1 | Refine the mobile Reader flow for A–G expression marks, I1 evidence states, and source-linked comparison | Android UI prototype | Drafted and reviewed UI/code ideas | Medium | Lee Jinsol |
| T2 | Update user stories, acceptance criteria, NFRs, UI flow, and screen descriptions to reflect the integrated application | Requirements & Specifications Wiki | Drafted and reviewed documentation | Medium | Lee Jinsol |
| T3 | Review loading, partial-result, withheld-evidence, and retryable-error states | Android UI prototype and UI requirements | Brainstormed and troubleshot | Medium | Lee Jinsol |

---

## 3. Representative Prompts and Outputs

### Example 1 — Reader comparison UI and evidence states (T1)

**Define** — We needed a mobile Reader that lets users inspect expression differences with source-linked passages without implying that FrameLESS decides factual truth, political bias, or neutrality.

**Context provided** — Current Android UI screenshots, the article-first user flow, TA feedback about clarity and neutral comparison, and the expected backend result structure.

**Prompt summary**

```text
Review the current FrameLESS Android Reader UI and propose a mobile-friendly
way to present A–G expression differences and I1 evidence states.

Requirements:
- A user must be able to open source-linked comparison evidence.
- When I1 evidence is insufficient, the mark must be withheld rather than
  presented as “no difference.”
- Do not describe a claim as true, false, politically biased, or neutral.
- Keep the design practical for a small Android screen.
```

**Output (excerpt)**

```text
Use an in-context Reader highlight and a source-linked comparison sheet
instead of a desktop-style split view. Distinguish loading, partial,
withheld-evidence, and retryable-error states. Keep the original source
link available from every displayed comparison item.
```

**Verify** — I ran the Android application on the emulator, checked the Reader against live analysis results, and compared the proposed wording with the evidence limits defined by the team.

**Integrate**

- **Kept:** Reader highlights, source-linked comparison sheet, clear loading and error states.
- **Modified:** Replaced wording that could imply a conclusion with “withheld” and evidence-oriented explanations.
- **Discarded:** Desktop-style split-view ideas that did not fit the mobile Reader flow.
- **Result:** Integrated into the Android UI and the Iteration 2 UI requirements.

### Example 2 — Requirements and UI documentation update (T2)

**Define** — We needed to revise the requirements document so that it accurately described the Iteration 2 implementation: sign-in, recent analyses, Reader marks, source-linked evidence, and incomplete-result handling.

**Context provided** — The Iteration 1 requirements, updated Android screens, TA feedback, and the current application flow.

**Prompt summary**

```text
Based on the current FrameLESS application and TA feedback, revise the
Functional Requirements, Non-Functional Requirements, and UI Requirements.

The document must:
- use testable Given–When–Then acceptance criteria;
- distinguish A–G expression marks from I1 evidence states;
- explain that a withheld mark is not proof that no difference exists;
- match the actual Android screens and current project scope.
```

**Output (excerpt)**

```text
The draft included revised user stories, measurable traceability targets,
a Reader-centered UI flow, and descriptions for loading, partial, withheld,
and retryable-error states.
```

**Verify** — I checked the draft against the running app, screenshots, GitHub Wiki Preview, and TA feedback.

**Integrate**

- **Kept:** Testable acceptance criteria, measurable UI and traceability requirements, and the Reader-centered flow.
- **Modified:** Replaced “consensus facts” and similar wording with source-linked, evidence-oriented language.
- **Discarded:** Suggestions that treated missing evidence as proof of no reporting difference.
- **Result:** Reflected in the revised Requirements & Specifications Wiki page.

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- AI quickly transformed broad feedback into concrete UI states, documentation structure, and candidate acceptance criteria.
- AI was useful for comparing alternative mobile layouts and identifying missing states such as loading, partial results, and retryable errors.
- AI accelerated first drafts, allowing more time for manual review of wording and evidence boundaries.

### What required human verification or correction

| **Task** | **Issue in AI output** | **How it was detected** | **Correction made** |
| --- | --- | --- | --- |
| T1 | Some drafts could make an absent I1 mark appear equivalent to “no difference.” | Team review against the I1 evidence rule and live Reader behavior. | Used a withheld-evidence state and explicit limitation message. |
| T1 | Some layout suggestions assumed a desktop-style split view. | Review on the Android emulator and mobile-screen constraints. | Used an in-context Reader highlight and comparison sheet instead. |
| T2 | Terms such as “consensus facts” could overstate what multi-source agreement proves. | TA feedback and requirements review. | Replaced them with “reported by multiple sources” and source-linked evidence language. |

### Verification practices we used

- [x] Ran and manually tested the Android application on an emulator
- [x] Checked UI and documentation against the project requirements and TA feedback
- [x] Reviewed live analysis results, including partial and withheld-evidence states
- [x] Verified Markdown tables, Mermaid flow, and screenshots in GitHub Wiki Preview
- [x] Discussed implementation and wording decisions with team members before integration

---

## 5. Reflection on How AI Affected Our Development Process

**Productivity** — AI shortened the time needed to turn broad feedback into candidate UI states and documentation drafts. It was most useful for iteration and structure, not for final product decisions.

**Quality** — AI improved the completeness of early drafts, but only after manual revision. In particular, evidence-related wording required careful human review to avoid implying truth, neutrality, or intentional omission.

**Team workflow** — AI outputs served as discussion material for implementation and documentation. The team still used the running app, shared feedback, and source-linked evidence rules to decide what to integrate.

**Learning** — AI helped identify possible Compose UI structures and state-handling patterns, but it did not replace understanding the Android code, API behavior, or real article-analysis failures.

### Lessons and plan for next iteration

| **What we learned** | **What we will change next iteration** |
| --- | --- |
| Broad UI prompts can produce layouts that do not fit the mobile product. | Include current screenshots, mobile constraints, and explicit acceptance criteria in prompts. |
| AI can use wording that overstates what evidence supports. | State evidence limits explicitly and review every user-facing claim before integration. |
| A plausible draft is not equivalent to a verified implementation. | Test AI-assisted changes with real articles, error cases, and the running Android app before merge. |

