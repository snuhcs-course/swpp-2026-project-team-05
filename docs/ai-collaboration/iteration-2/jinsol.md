## AI Collaboration Contribution — Jinsol Lee

> Individual contribution record for the team-level **AI Collaboration Report – Iteration 2**.  
> This note covers my work on the Android Reader UI, UI specification, and requirements documentation. It does not claim ownership of the backend retrieval, claim-matching, or LLM-analysis pipeline.

### 1. Summary of AI Usage

During Iteration 2, I used ChatGPT/Codex and Claude as drafting and review assistants for the FrameLESS Android Reader experience and the updated Requirements & Specifications document. I provided UI specifications, screenshots from the running application, TA feedback, and relevant implementation context when needed.

AI was used to explore mobile-friendly interaction patterns, review whether the implemented Reader reflected the intended user flow, refine requirements wording, and troubleshoot local development workflow issues. All outputs were treated as provisional drafts. I verified final UI and documentation decisions against the integrated application, the project scope, and GitHub Wiki Preview before integration.

| Tool | Model / version | Used by | Main purpose |
|---|---|---|---|
| ChatGPT / Codex | Not recorded | Lee Jinsol | UI/specification review, requirements drafting, presentation wording, and workflow troubleshooting |
| Claude | Not recorded | Lee Jinsol | UI prototyping, layout exploration, and implementation-handoff preparation |

**Deliberate scope boundary.** I did not use AI to decide whether a claim was true, whether an outlet was politically biased, whether an omitted claim was intentional, or whether a comparison result was neutral. These are evidence and product-scope decisions that required team-defined rules and human review.

### 2. Major Tasks Where AI Contributed

| # | Task | Related deliverable | AI role | Contribution level | Human owner |
|---|---|---|---|---|---|
| T1 | Review and refine the mobile Reader flow, including source-linked comparison and uncertainty states | Android UI / Reader experience | Drafting and review assistance | Medium | Lee Jinsol |
| T2 | Update functional requirements, quality targets, UI flow, screen descriptions, and screenshots to match the integrated application | Requirements & Specifications Wiki | Drafting and review assistance | Medium | Lee Jinsol |
| T3 | Prepare concise documentation-update and presentation wording | TA meeting / midterm materials | Drafting assistance | Low–Medium | Lee Jinsol |

### 3. Representative Prompts and Outputs

The original Korean prompts and complete conversation records are retained in the team’s Iteration 2 Prompt Log: **[TEAM PROMPT LOG LINK]**. The descriptions below are faithful English summaries included for readability; they are not reconstructed as verbatim prompts.

#### Example 1 — Reader UI and specification alignment

**Goal** — Verify that the mobile Reader UI clearly represented the required article-first flow, source-linked comparison, and uncertainty states.

**Context provided** — Current Reader screenshots, the UI specification, TA feedback, and the distinction between A–G expression marks and I1 evidence conditions.

**Prompt summary**

```text
Review the attached Reader UI against the current product specification.
Identify whether the mobile flow clearly distinguishes source-linked comparison,
expression differences, withheld evidence, partial results, and retryable errors.
Recommend only changes that are compatible with the existing implementation.
```

**Output** — AI generated a structured UI review checklist and suggested ways to clarify visual hierarchy and state-specific messages.

**Verify and integrate** — I compared the suggestions with the running Android application and retained only changes consistent with the implemented Reader flow. Suggestions that would imply unsupported conclusions or require unverified backend behavior were not integrated.

#### Example 2 — Safe implementation handoff

**Goal** — Prepare sufficient context before requesting UI changes from another AI-assisted development environment.

**Context provided** — Existing Android project structure, `MainActivity.kt`, `AnalysisApi.kt`, Gradle configuration, UI screenshots, and the need to preserve the current backend contract.

**Prompt summary**

```text
Prepare a handoff prompt for an existing Android Compose project.
Specify which implementation files and screenshots must be provided before proposing
UI changes. Preserve the current API contract and avoid rewriting the application
from screenshots alone.
```

**Output** — AI identified the relevant implementation files and recommended giving the existing data/API structure as context before requesting UI modifications.

**Verify and integrate** — This guidance was used to avoid an unsupported visual-only rewrite and to keep UI work compatible with the existing application structure.

#### Example 3 — I1 withheld-state review

**Goal** — Confirm that an unavailable I1 star would not be presented as evidence that no reporting difference exists.

**Context provided** — Current application behavior, Reader screenshots, and the updated requirements for I1 evidence conditions.

**Prompt summary**

```text
Review whether the current UI and documentation distinguish an available I1 mark
from a withheld mark. A withheld mark must not be interpreted as proof of similarity,
neutrality, or the absence of a reporting difference.
```

**Output** — AI helped identify wording that could overstate the meaning of an absent comparison signal.

**Verify and integrate** — I reviewed the implemented behavior and revised the documentation to state that a withheld mark represents insufficient evidence for display, not a substantive conclusion.

### 4. What Worked Well and What Required Human Verification

AI was effective at turning broad feedback into a structured review checklist and at producing first drafts for UI/documentation language. However, human verification remained necessary because a visually plausible interface or polished wording could overstate what the system actually verifies.

| Area | Risk in AI-generated output | Human verification and correction |
|---|---|---|
| I1 mark | A missing or withheld mark could be interpreted as proof that no meaningful difference exists. | I described a withheld mark as **no conclusion**, not as evidence of similarity, neutrality, or absence. |
| Claim language | Terms such as “fact,” “truth,” or “bias” could overstate the result of multi-source comparison. | I retained evidence-oriented wording such as “source-linked comparison,” “reported by multiple sources,” and “withheld when evidence is insufficient.” |
| Mobile layout | A visually appealing split-view layout may be too dense for a phone-sized interface. | I evaluated proposed layouts against the actual Android Reader and prioritized a readable source-linked comparison flow. |
| Wiki formatting | Markdown that appears correct in a draft can render incorrectly with tables, Mermaid diagrams, or screenshots. | I checked GitHub Wiki Preview, corrected formatting, and re-uploaded screenshots before saving. |

### 5. Manual Verification

- [x] Ran the integrated Android application and inspected the Reader flow.
- [x] Compared the implemented UI with the updated Functional, Non-Functional, and UI Requirements.
- [x] Verified that the I1 withheld state was not documented as a political-bias, omission-intent, or no-difference conclusion.
- [x] Checked the GitHub Wiki page, tables, flow, and screenshots in Preview before saving.
- [x] Kept UI/documentation claims separate from backend and LLM-analysis claims owned by other team members.

### 6. Reflection

AI reduced the time required to produce first drafts and organize UI-review criteria, but it could not verify implementation status, source evidence, or the meaning of an absent comparison signal. The most useful prompts included concrete screenshots, the current specification, code/API constraints, and explicit non-overclaim rules.

For Iteration 3, I will retain raw prompts at the time of use, provide concrete test cases and current interface constraints as context, mark newly integrated AI-generated code according to the course policy, and require a build/UI check before treating an AI-assisted change as complete.
