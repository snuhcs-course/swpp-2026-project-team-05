# AI Collaboration Contribution — Iteration 3

|                     |                                                                    |
| ------------------- | ------------------------------------------------------------------ |
| **Team**            | Team 5 — FrameLESS                                                 |
| **Iteration**       | Iteration 3 — Advanced Testing & Evaluation                        |
| **Period**          | October 24, 2026 – November 6, 2026                                |
| **Iteration goals** | Deliver the midterm presentation and run the human evaluation of AI comparison results (Testing Documentation §5.2). |
| **Authors**         | Doh Seoyoung — Individual contribution                              |

---

## 1. Summary of AI Usage

My main AI-assisted task in Iteration 3 was the midterm presentation. I presented on 10-26, and I used Claude (claude.ai) to fit my script to the 8-minute limit and to polish the wording.

I also used Claude to study two topics that were **not my assigned deliverables**:

- how to score framing marks against human labels;
- what the Iteration 3 testing guideline requires.

These outputs were for my own understanding. They are not presented here as team decisions or as part of the submitted Testing Documentation.

**Deliberately not used: human evaluation (§5.2, my responsibility).** I did the human evaluation of AI comparison results without AI. That includes the labels, the evaluator sheets, and the results. AI-produced judgments must not become the ground truth for judging AI output. Claude had no write access to the repository or Wiki.

### AI tools used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| Claude (claude.ai) | Claude Opus 5.5 | Doh Seoyoung | Presentation script timing and wording; background study on evaluation metrics and the testing guideline |

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **Contribution level** | **Human owner** |
| --- | --- | --- | --- | --- | --- |
| T1 | Estimate the script length, cut it to 8 minutes, and polish the wording of key slides | Midterm presentation (10-26) | Counted syllables, suggested cuts and wording | Medium | Doh Seoyoung |
| T2 | Study precision, recall, F1, and labeler agreement for sentence-level framing marks (exploratory, not my deliverable) | None (background study) | Explained concepts | Low | — |
| T3 | Compare the Iteration 2 testing page with the Iteration 3 guideline (exploratory, not my deliverable) | None; the Testing Documentation was written by other members | Gap analysis, sample draft | Low | — |

The opposing article pair used on slides 8–10 (Fox News vs. ABC News on the French protests) was found on 10-22–23. It is reported in my Iteration 2 note.

---

## 3. Representative Prompts and Outputs

Prompts are quoted verbatim.

### Example 1 — Fitting the script to 8 minutes (T1)

**Define** — I took over the presentation and needed my script to fit an 8-minute slot, including a demo video.

**Context provided** — The slide deck with my speaker notes. In the notes, the script for each page ends at `---`.

**Prompt** (10-24 14:26, excerpt)

```text
1-12쪽까지 작성한 슬라이드 노트의 스크립트를 읽어보고, 여기까지 했을 때 대략 몇 분 정도 나올지 예측해줘 (다만 노트 전체 텍스트가 다 스크립트가 아니고,, 내가 '---'라고 한 부분까지가 해당 페이지의 대본이야.
```

**Output** — A syllable count per slide at about 320 syllables per minute. Pages 1–12 came to about 3 minutes.

**Verify** — Claude compared that figure with a 10-minute talk, which was wrong. I corrected it:

```text
전체 발표 시간은 8분이야 ㅋㅋ 12쪽까지가 '문제 상황 설명 및 우리 서비스 간략 소개' 부분인데 너무 긴가
```

Claude then proposed cutting the introduction to about 1.5 minutes. I re-checked twice with revised scripts on 10-26. The estimates were about 8 minutes 20 seconds, then about 8 minutes.

**Integrate**

- **Kept:**
  - merging the glass-of-water slides;
  - explaining one press example in detail;
  - finishing note-style lines as full sentences.
- **Modified:** I rewrote the script in my own words and used the suggested wording for slides 13 and 15 only as a reference.
- **Result:** I presented on 10-26.

### Example 2 — Slide messages in English (T1)

**Prompt** (10-23 17:31)

```text
단순한 팩트 너머 '특정 관점 내지는 프레이밍의 존재'를 인지하지 않고 
무비판적으로 기사를 읽게 될 경우, 
자신도 모르는 사이 기사에 내재된 관점을 내면화(흡수,수용)하게 됨

--- 영어로 작성해줘
```

**Output** — A full sentence ("…they may unknowingly internalize the viewpoint embedded in the article") and two short slide versions. For "물이 절반이나 안 남았네", Claude pointed out that the Korean wording seemed to be a typo for "절반이나 남았네" and translated the intended meaning.

**Integrate** — I chose the wording for each slide myself. 

### Example 3 — Background study: framing marks vs. human labels (T2, not a deliverable)

**Prompt, before** (11-04 20:21)

```text
팀프로젝트 앱의 성능을 평가하는 데에 있어, 기사 분석을 잘하는 가를 'false positive' ' false negative' 등으로 평가하고자 해. 이 때의 ground truth는 인간이 레이블링한 기사 분석 값으로 하고자 해.
```

**Output** — A textbook confusion-matrix explanation. Its example treated a whole article as the unit and "negative tone" as the positive class.

**Prompt, after** (20:24)

```text
아 내가 말하는 'false positive / false negative'는 '기사 단위'가 아닌 '기사 내의 한 문장 / 표현 단위'야. 그리고 positive가 정말 긍정적이라는 의미가 아니라, 여기에서는 'framing을 framing으로 잘 판별했는가(true positive)' 'fact를 framing으로 판별했는가 (false positive)'인거야
```

**Why the revision worked** — The first prompt left the unit and the positive class open, so the model filled them with a textbook default. Once I stated both, every metric mapped directly onto what the Reader shows: framing as the positive class, precision and recall, a span-matching rule, and Cohen's kappa.

**Integrate** — I used this only to understand the concepts. The human evaluation itself (§5.2) was designed and carried out without AI.

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- **Quick, repeatable estimates.** Each revised script could be re-timed in minutes, which made cutting to 8 minutes practical.
- **Concrete cuts.** Claude pointed to repeated phrases ("현재 읽고 있는 기사" twice, "의의가 있다" twice). These cuts saved time without losing content.
- **Catching an input error.** In the translation request, Claude noticed the likely typo instead of translating it literally.

### What required human verification or correction

| **Task** | **Issue in AI output** | **How it was detected** | **Correction made** |
| --- | --- | --- | --- |
| T1 | The time estimate assumed a 10-minute talk. | My correction (10-24 14:28) | Re-planned for 8 minutes |
| T1 | Times were syllable-count estimates, not measurements. | My own review | I timed the script myself before presenting |
| T2 | The first answer assumed article-level classification and "negative tone" as the positive class. | My correction (11-04 20:24) | Unit = sentence/expression; positive = framing |
| T3 | The sample draft contained Claude-proposed threshold values. | Claude's own `[PROPOSED]` labels | Not used; thresholds belong to the team |

### Verification practices I used

- [x] Rehearsed and timed the final script myself
- [x] Chose final slide wording myself
- [x] Ran the human evaluation (§5.2) without AI: labels, evaluator sheets, and results
- [x] Did not submit exploratory outputs as team deliverables

**AI markers.** I did not commit code in Iteration 3, so there are no in-code markers.

---

## 5. Reflection on How AI Affected Our Development Process

**Productivity** — Re-timing the script after each edit took minutes instead of a full read-aloud. 

**Quality** — The answers were only as good as the constraints I gave. With a missing time limit, the estimate was compared against the wrong target. With a vague evaluation question, I got a textbook example.

**Learning** — Stating constraints early (talk length, unit of judgment, which label counts as positive) matters more than any follow-up polishing.

### Lessons and plan for next iteration

| **What I learned** | **What I will change next iteration** |
| --- | --- |
| A missing constraint leads to a wrong baseline. | State limits such as talk length and the judgment unit in the first prompt. |
| Estimates are not measurements. | Time rehearsals myself before relying on a number. |
| Exploratory outputs can be mistaken for decisions. | Label background study clearly and keep it out of team documents. |
| Prompts were recovered from chat history afterwards. | Log prompts in the Prompt Log as I send them. |
