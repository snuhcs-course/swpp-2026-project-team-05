## AI Collaboration Contribution — Lee Jinsol

### 1. Summary of AI Usage

I used ChatGPT/Codex to refine FrameLESS’s article-first user flow, draft the Android comparison UI, and prepare the Functional Requirements, Non-Functional Requirements, and UI Requirements sections. AI outputs were used as drafts and were checked against the running emulator, TA feedback, and the final Wiki preview before integration.

| Tool | Model / version | Used by | Main purpose |
|---|---|---|---|
| ChatGPT / Codex | Not recorded | Lee Jinsol | UI and requirements drafting, prototype troubleshooting |

### 2. Major Tasks Where AI Contributed

| # | Task | Related deliverable | AI role | Contribution level | Human owner |
|---|---|---|---|---|---|
| T1 | Refine the article-first flow and draft user stories, acceptance criteria, NFRs, and UI documentation | Requirements & Specifications Wiki | Drafted and reviewed | Medium | Lee Jinsol |
| T2 | Build and refine the Android source-linked comparison UI | UI prototype; commit `77a769e` | Assisted UI/code drafting | Medium | Lee Jinsol |

### 3. Representative Prompt and Output

#### Example — Requirements and UI specification (T1)

**Define** — We needed requirements that matched the Iteration 1 MVP: article URL input → same-event coverage retrieval → source-linked comparison.

**Context provided** — TA feedback, the revised proposal, required user-story formats, and screenshots of the running prototype.

**Prompt**

```text
내가 해야할건 다음 이부분만:
- Functional Requirements
- Non-functional Requirements
- UI Requirements
현재 코드랑 ui 등 같이 봐가면서 해
```

**Output (excerpt)** — AI drafted six user stories, Given-When-Then acceptance criteria, measurable NFRs, a UI flow diagram, and screen captions.

**Verify and integrate** — I compared the draft with the emulator screens and GitHub Wiki Preview. I kept the article-first flow and source-linked comparison, but changed wording that could imply the system decides factual truth or political neutrality. The final result was integrated into the Requirements & Specifications Wiki page.

### 4. What Worked Well and What Required Human Verification

AI quickly converted broad TA feedback into concrete UI states and testable requirements. However, human review was necessary because terms such as “consensus facts” could overstate what the system can determine. I revised these into evidence-oriented phrases such as “reported by multiple sources” and checked that the final documentation matched the actual input, loading, comparison, no-related-coverage, and sentence-comparison screens.

- [x] Ran and manually tested the Android prototype on the emulator
- [x] Checked the output against TA feedback and project scope
- [x] Verified the Wiki table, Mermaid flow, and screenshots in Preview

### 5. Reflection

AI sped up the first drafts of the UI and documentation, but it did not replace product judgment or testing. The most important lesson was that plausible UI and requirement text must be verified with real article inputs and the running prototype. In the next iteration, I will provide the current API contract and concrete test cases as prompt context before integrating AI-assisted changes.
