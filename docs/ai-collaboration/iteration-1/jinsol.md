## AI Collaboration Contribution — Jinsol Lee

> Individual contribution note for the team-level **AI Collaboration Report – Iteration 1**.

### 1. Where AI Was Used

I used ChatGPT/Codex to refine FrameLESS’s article-first user flow, draft the Android source-linked comparison UI, and prepare the Functional Requirements, Non-Functional Requirements, and UI Requirements sections. AI outputs were treated as drafts and were checked against the running emulator, TA feedback, and the final Wiki preview before integration.

| Tool | Model / version | Used by | Main purpose |
|---|---|---|---|
| ChatGPT / Codex | Not recorded | Lee Jinsol | UI and requirements drafting; prototype troubleshooting |

**Deliberately not used.** I did not use AI to decide whether a claim was factually true, politically neutral, or sufficiently supported by evidence. I made those scope and wording decisions manually using TA feedback, the project scope, and the running prototype.

### 2. Prompt History

The following is the retained Iteration 1 prompt that I can verify. I did not reconstruct deleted or unrecorded prompts retrospectively.

```text
내가 해야할건 다음 이부분만:
- Functional Requirements
- Non-functional Requirements
- UI Requirements
현재 코드랑 ui 등 같이 봐가면서 해
```

The prompt provided TA feedback, the revised proposal, required user-story formats, and screenshots of the running prototype as context.

### 3. What AI Did Well

AI produced a useful first draft of six user stories, Given-When-Then acceptance criteria, measurable NFRs, a UI flow diagram, and screen captions. These drafts were used to prepare the Requirements & Specifications Wiki page. It also assisted the initial source-linked comparison UI draft in `android/app/src/main/java/com/swpp/team5/frameless/MainActivity.kt`, which I reviewed and revised before commit `77a769e`.

### 4. Overclaim / Correction

AI-generated drafts used terms such as “consensus facts” and “what actually happened.” This wording could incorrectly imply that the system can determine factual truth or political neutrality merely because multiple outlets report a similar claim. I identified this issue during TA-feedback and requirements review. I manually changed the terminology to evidence-oriented phrases such as “reported by multiple sources” and “outlet-specific claims/framing candidates.” The correction required revising the UI and documentation wording; no unsupported truth or neutrality judgment was integrated.

### 5. Prompt Revisions

I did not retain a reliable before-and-after prompt pair during Iteration 1, so I will not create one retrospectively. From Iteration 2, I will record the original prompt, revised prompt, and reason for revision when AI is used.

### 6. Manual Fixes and Verification

I manually revised the terminology and UI/documentation copy rather than asking AI to decide neutrality, because this required an evidence-based product decision rather than a wording-only change. I also manually tested the Android prototype on the emulator and verified that the final documentation matched the input, loading, comparison, no-related-coverage, and sentence-comparison screens.

- [x] Ran and manually tested the Android prototype on the emulator
- [x] Checked the output against TA feedback and project scope
- [x] Verified the Wiki table, Mermaid flow, and screenshots in Preview

### 7. Takeaway

AI accelerated first drafts, but it did not replace product judgment or testing. In the next iteration, I will record prompts at the time of use and provide the current API contract and concrete test cases as context before integrating AI-assisted changes.