# AI Collaboration Contribution — Iteration 2

|                     |                                                                    |
| ------------------- | ------------------------------------------------------------------ |
| **Team**            | Team 5 — FrameLESS                                                 |
| **Iteration**       | Iteration 2 — Comparison Rubric and Design Documentation           |
| **Period**          | October 10, 2026 – October 23, 2026                                |
| **Iteration goals** | Define the comparison rubric (A–G expression, I1–I5 information) with literature support, prototype the Reader around it, and update the Design Documentation to the integrated system. |
| **Authors**         | Do Seoyeong — Individual contribution                              |

---

## 1. Summary of AI Usage

I used Claude (claude.ai) for five things:

- a literature review and the team's criteria table;
- a rubric specification;
- Reader prototypes;
- the first draft of Design Documentation Rev.2.0;
- a wording review of the midterm slides.

I set the structure of the rubric myself:

- article-level vs. word-level differences;
- "difference" instead of "bias";
- a cited source for every decision.

I checked every output before using it:

- rubric judgments against the source articles;
- the design draft against the code at `92d9668` and against the Rev.1.2 page.

AI did **not** decide which categories the team adopted, whether an article is biased, or the final slide text. Claude had read-only access to the repository and Wiki, and I pasted every Wiki edit myself.

### AI tools used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| Claude (claude.ai, incl. Claude Docs) | Claude Opus 5.5 | Doh Seoyeong | Literature review, rubric, prototypes, design draft, slide review |

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **Contribution level** | **Human owner** |
| --- | --- | --- | --- | --- | --- |
| T1 | Literature review and criteria table: v0.1 had A–G only; v0.2 added I1–I5, three levels, and 40 endnotes | Team doc "판정 기준표 (초안 v0.2)", 10-14 | Searched and summarized papers, drafted the table | High (draft); I set the structure and terms | Do Seoyeong |
| T2 | Rubric spec v0.2.1: applies the table to a real article pair, adds proposals P1–P6 and endnotes [41]–[57] | Rubric spec (rubric version `0.2.1` in code) | Drafted the text and case judgments | High (draft); P1–P6 not adopted | Do Seoyeong |
| T3 | Web and Android Reader prototypes (v0.1 10-13, v0.2 10-15) | Prototypes (outside the repo) | Generated the code | High | Do Seoyeong |
| T4 | Design Documentation Rev.2.0 drafted from code `92d9668` | Wiki "Design Documentation", 10-21 | Drafted, checked against the code | High (draft), Medium after review | Do Seoyeong |
| T5 | Midterm slides checked against the Final Proposal; English wording on slides 1 and 3 | Midterm slides | Reviewed, suggested wording | Medium | Do Seoyeong |
| T6 | Background: CHI 2023 "Discord Questions" paper, mobile UI constraints, article pairs for the presentation | Input to T1, T3, T5 | Summarized, searched | Low | Do Seoyeong |

---

## 3. Representative Prompts and Outputs

Prompts are quoted verbatim.

### Example 1 — Literature review and criteria table (T1)

**Define** — We needed a cited way to say that two outlets wrote the same event differently. The project may become a paper.

**Context provided** — The CHI 2023 paper (attached), the proposal, and my examples "언급했다 vs 강조했다" and "많은 vs 도가 지나친".

**Prompt** (10-14 14:45)

```text
궁금한 점이, 혹시 그러면 '이러한 언어적 편향/표현의 강도'를 규정한 연구들이 있는지 알고 싶어. 가령 '언급했다'와 '강조했다'는 뉘앙스적으로 그 강도가 다르잖아. 혹은 '많은'과 '도가 지나친'은 뉘앙스적 차이(편향의 정도 차이)가 다르고.
```

**Output (excerpt)** — A summary of Recasens et al. 2013, BASIL (Fan et al. 2019), scalar-adjective work, Appraisal Theory, and Korean studies, each marked as "checked" or "from memory". Criteria table v0.1: seven expression categories A–G with markedness 0–2.

**Verify** — I found two gaps and set the hierarchy and the terms myself (15:07):

```text
개인적으로는 이 둘의 위계가 다른 거 같아:
* informational bias는 기사의 거시적 위계라면, Lexical bias는 그보다 미시적 레벨인 거 같아.
2. 지금 만들어준 '5. ... 기준표'는 lexical bias에 한정된건가 ? informational은 고려되지 않은 거 같아서.
 단 유의사항은, 우리 프로젝트에서는 가급적 'bias'라는 표현을 지양하고자 해.
```

I then asked for sources for the split (15:09) and endnotes on every claim (15:19).

**Integrate**

- **Kept:** A–G with markedness 0–2, and a verification label on every reference.
- **Modified:** Added I1–I5. Arranged the categories in three levels. Replaced "bias" with "차이 / 유표성".
- **Result:** Criteria table v0.2 became the team's working rubric. The spec, the v2 pipeline, and the Design Documentation use its A–G and I1 definitions.

### Example 2 — From a scoring prototype to a cited rubric (T2, T3)

**Prompt, before** (10-13 20:25)

```text
혹시 만일, 이 두 기사를 보여주는 앱이 있는데, 방금 내가 요청한 것처럼, 각 기사에서 문장별로 팩트와 관점을 판별해서 서로 다른 색으로 하이라이트하는 앱이 있다고 하면, 혹시 이를 프로토타이핑해줄 수 있어 ? 가령 팩트면 회색, 보수쪽 관점 문장이면 연한 붉은색, 진보쪽이면 연한 파란색으로
```

**Output** — The two articles (주간경향 10-10 and TV조선 10-12, on "암살자(들)" passing 3 million viewers). Each sentence was colored gray, red, or blue and given a 0–100 "perspective score".

**Verify** — I asked "혹시 이 점수 책정과 판정 근거는 어떻게 너가 분석한건지 알려줘" (20:40). The scores turned out to be Claude's unvalidated judgment.

**Prompt, after** (10-14) — I pasted criteria table v0.2 into the spec chat and asked:

```text
아 다만 이 루브릭에 논문 출처도 반드시 미주로 같이 넣어줘. 추후에 논문화를 하고자 해서, 이를 위한 justification이 탄탄해야 해
```

**Why the revision worked** — The first prompt asked the model to invent a judgment. The second gave it a fixed, cited rubric. Rules without a source were marked "(직접 근거 없음)".

**Integrate**

- **Discarded:** The scores and the political colors.
- **Added:** The article pair re-judged with the rubric (18 expression rows), proposals P1–P6, and endnotes [41]–[57]. Prototype v0.2 shows a question grid with omission reasons, colors by target, and markedness 0–2.
- **Result:** Junseo's report lists spec v0.2.1 as context for `rubric_analysis.py`. P1–P6 are still proposals.

### Example 3 — Design Documentation Rev.2.0 (T4)

**Context provided** — The Wiki page (Rev.1.2), the repository at `92d9668`, the team draft `docs/iteration-2-wiki-update-draft.md`, and the guideline.

**Prompt** (10-21 13:54): "일단은 design documentation 초안 작성해줘"

**Output** — About 3,500 words covering:

- architecture, request flow, and the comparison method (A–G, I1);
- the partial-result policy and the cache model;
- the API and the MVVM Android app;
- design decisions, security, and four Mermaid diagrams.

Testing was moved to the Testing Documentation.

**Verify** — Before delivery, Claude re-read `views.py`, `reader_contract.py`, and `ReaderScreen.kt` and fixed three of its own errors. I then asked: "근데 이 버전은 이전 버전에 갱신된 내용까지 다 포함되어있는 거야 ?" The answer showed that three still-valid Rev.1.2 notes were missing.

**Integrate**

- **Kept:** The structure. Open items stay marked "Open" (FR-02/FR-05, NFR-02).
- **Modified:** The three Rev.1.2 notes were added back.
- **Result:** I pasted the page into the Wiki on 10-21 (five edits, +257/−169 lines).

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- **Honest literature labels.** Claude found the core sources (BASIL, Recasens et al., Entman, Hamborg et al.) and labeled how each was checked. It never invented a source for an unsupported rule.
- **Fast prototypes.** Two prototypes in one evening showed early why political colors would not work.
- **Code-grounded documentation.** Re-reading the code caught three errors before I received the draft.

### What required human verification or correction

| **Task** | **Issue in AI output** | **How it was detected** | **Correction made** |
| --- | --- | --- | --- |
| T1 | Table v0.1 covered only word-level differences and used "bias". In BASIL, about 72% of differences are informational. | My question at 15:07 | Added I1–I5, three levels, and neutral terms |
| T1, T2 | The Korean examples were never checked against real articles. Some references were seen only through other papers (e.g. [11]: the year does not match). | Claude's own labels | Labels kept; key references must be checked in the original before writing a paper |
| T3 | The 0–100 scores looked precise but had no basis. | My question at 20:40 | Replaced by markedness 0–2 against a baseline expression |
| T2, T3 | Political colors contradicted the "no bias score" principle. Claude also played down what single-color highlighting loses. | Claude flagged the first issue. My pushback caught the second: "같은 색으로 칠하는 건 잘못된 거 아닌가 ?" | Each expression records its target and polarity |
| T4 | Three Reader/API details were wrong in the draft (tappable sentences, 502/503 mapping, `I1Star` fields). | Claude re-read the code | Fixed before delivery |
| T4 | Three still-valid Rev.1.2 notes were dropped (`missing_configuration`, NAVER links / FR-04, v1 refused in production). | My question at 14:18 | Added back |
| T4 | Claude ran `git push` to the Wiki without being asked. | Rejected by the proxy (403); Claude reported it | Nothing changed; I publish by hand |
| T5 | Claude read the slide 3 highlight as "difference from other articles". | My correction (10-19 21:05) | Legend rewritten; slide 7 flagged |

### Verification practices I used

- [x] Checked the rubric's case judgments against the two source articles
- [x] Required a source, or a "no direct evidence" note, for every decision
- [x] Checked the design draft against the code and against Rev.1.2 before pasting
- [x] Previewed the Wiki page after pasting 
- [ ] Checked every reference in the original paper (pending)

**AI markers.** I did not commit code to `main` in Iteration 2. Claude's local Wiki commits carried `Co-Authored-By` but were never pushed. The pasted Design Documentation shows up as my edits (`RaonDoh`, 10-21), so this note is the record that its first draft was AI-generated.

---

## 5. Reflection on How AI Affected Our Development Process

**Productivity** — Without AI, the literature review, rubric, two prototype versions, and a 3,500-word design document would not have fit into two weeks.

**Quality** — Quality depended on my questions. The scores, the expression-only table, and the missing Rev.1.2 notes all looked finished. Each problem surfaced only when I asked "how did you decide this?" or "is anything missing?".

**Team workflow** — The criteria table gave the team one definition of A–G and I1. Two gaps remain:

- The prototype handoff documents still describe v0.1.
- The Reader path dropped the markedness, baseline, and target fields from the spec. Junseo's report notes the same gap.

**Learning** — A fixed, cited rubric worked far better than asking the model to judge.

### Lessons and plan for next iteration

| **What I learned** | **What I will change next iteration** |
| --- | --- |
| Precise-looking numbers can have no basis. | Ask "how was this computed?" before using any score. |
| A cited rubric beats asking the model to judge. | Give the rubric and its sources in the first prompt. |
| Spec fields were lost in the Reader path. | Agree with the pipeline owner on the API fields and add a contract test. |
| A rewrite can silently drop valid content. | Ask for an old-vs-new diff before replacing a Wiki page. |
| Prompts were recovered from chat history afterwards. | Log prompts in the team Prompt Log as I send them. |
