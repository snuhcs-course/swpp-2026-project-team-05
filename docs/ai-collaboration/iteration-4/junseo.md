# **AI Collaboration Report — Iteration 4 (Junseo Heo)**

|  |  |
| ----- | ----- |
| **Team** | Team 5 — *FrameLESS* |
| **Iteration** | Iteration 4 — Staged A–G Analysis, Economic Lens, and Event Issue View |
| **Period** | 2026.11.07 – 2026.11.20 |
| **Iteration goals** | 1. Make the A–G analysis fast and cheap enough to run on a pasted link<br>2. Add an economy section with an economic lens<br>3. After the TA feedback, compare outlets by issue, with articles chosen for different stances, and put it on the web |
| **Authors** | Junseo Heo |

---

## **1\. Summary of AI Usage**

I used Claude Code (Claude Opus 5.5) as my main assistant for design, code, experiments, data and pull requests. Models also ran inside the analysis:
* Claude Opus 5.5 built reference data and the event analyses.
* Gemini 3.8 Flash is the runtime model of the pasted-link analysis.
* Claude Haiku was the cheap model in the speed experiments.

I decided what the analysis should show, rejected designs that did not serve that goal, and checked results by reading them.

The main lesson of this iteration was about where the model should decide. Each time the model judged freely, results changed between runs and models. Each time I asked for its rules, some of them were rules it had made up. I moved the work toward code: the model answers only closed questions, and the methods come from published tasks (K-News-Stance, Media Frames Corpus, Key Point Analysis).

No human evaluation was run this iteration. Every figure below is a count of what the pipeline produced, a code check, or my own reading. None of them is an accuracy measured against people.

### **AI tools used**

| Tool | Model / version | Used by | Main purpose |
| ----- | ----- | ----- | ----- |
| Claude Code | Claude Opus 5.5 | Junseo Heo | Design, code, experiments, reference data, event analyses, PRs |
| Gemini via OpenRouter | Gemini 3.8 Flash | The link-analysis server | Closed questions of the staged A–G pipeline at run time |
| Claude Code | Claude Haiku | Junseo Heo | Cheap-model baseline in the speed experiments |

---

## **2\. Major Tasks Where AI Contributed**

| \# | Task | Related deliverable | AI role | Contribution level | Human owner |
| ----- | ----- | ----- | ----- | ----- | ----- |
| T1 | Staged A–G pipeline (v6): code builds pairs, candidates and explanations; the model answers closed questions | Staged code used by the link analysis ([#93](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/93), integrated by Seungmin Kim); lexicon [#100](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/100) | Drafted, Generated code, Experiments | High | Junseo Heo |
| T2 | 45 new events (Nov 1–15) analysed with v6; 35 imported into the shared DB | Shared DB (clusters 90 → 125) | Collected, Generated annotations | High | Junseo Heo |
| T3 | Link analysis for every reader, tutorial replay | [#94](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/94), [#95](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/95) | Generated code | High | Junseo Heo |
| T4 | Economy section and economic lens (code-first; a model only for closed questions the rules leave open) | [#96](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/96), [#97](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/97), [#98](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/98) (Android), [#99](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/99) | Researched, Drafted, Generated code | High | Junseo Heo |
| T5 | Choosing comparison articles with different stances | Evaluation tools (outside the repository) | Searched literature, Generated code | Medium | Junseo Heo |
| T6 | Event issue view: about ten outlets per event, issues and positions, reporter-wording A–G, what each article puts in front | [#105](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/105), [#106](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/106), [#109](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/109); 8 events in the shared DB | Drafted, Generated code, Generated annotations | High | Junseo Heo |

---

## **3\. Representative Prompts and Outputs**

The prompts below are verbatim. Article text stays out of this report, except for short phrases that the product itself displays.

### **Example 1 — The model answers only closed questions (T1, T2)**

**Define** — Run the A–G analysis on a cheap model without losing what Opus finds. Acceptance criteria:
* the cheap model's results stay close to Opus's;
* one event takes no more than about a minute;
* the output is the team's import format.

**Context provided** — Rubric v5, the Opus reference sets, and the stage-by-stage misses of Haiku and Gemini against those sets.

**Prompt**

```
지금 단계별로 어디서 문제인지 파악했잖아. 여기서 구현이 전부 llm 에게 맡기는게 아니라 명확하게 딱 목적만 줘서 각 단계들을 판단해야할 것 같아. 그렇지 않으면 이렇게 성능이 자꾸 바뀌고 오류가 나는 것 같아. 혹시 자연어 처리나 국어국문학 연구들을 다시 읽어보면서 각 단계별 구현이 지금 제대로 이루어지고 있는지 gemini같은 성능이 낮은 부분에서는 어떻게 지침이나 구조를 짜야 각 단계가 제대로 구현될지 정리해줘. 설계부터해보자
```

```
LLM은 후보마다 보기 중에서 고르는 좁은 질문에만 답하게를 우리의 goal로 잡자. 그리고 시간이 너무 오래걸리면 안돼
```

**Output (excerpt)** — The staged design v6:
* Code pairs sentences, cuts candidates (quote content, stop words, naming-only paths) and writes the explanation from templates.
* The model only chooses among given options at each step.
* Only the step that asks how a reader would feel the difference (S4b) gets more reasoning effort.

```
Gemini 3.8 Flash, analysis stage: 37–52 s per event (5 comparison articles)
Opus, same structure:              about 71 s per event
```

**Verify**
* I read the tables for new events myself.
* One article came out with 23 highlights and another with 51. I asked whether this was a bug ("어떻게 기사하나에서 하이라이트가 51개나 나오는거야? 구현상 오류가 있는 거 아니야?"). Both were rule problems:
  * number-only counterparts;
  * one sentence pair yielding several fragments;
  * common verbs of rise and fall treated as differences.
* I wrote the fixes, then reran only S4b on the earlier cases to check that the fixes did not remove correct answers.
* The team importer validated every quote before the 45-event data went into the shared DB.

**Integrate**
* Kept: v6 as the pipeline. Seungmin Kim connected it to the pasted-link flow (#93), and I opened it to readers without sign-in, within a daily limit (#94).
* Modified: The lexicon, counted over 1,484 articles (27,348 sentences), added missing A, G and D words (#100).
* Discarded: Free-form model judgments and the long Haiku path.
* Result: 35 of the 45 events (those with 1–9 highlights) went into the shared DB. I did not confirm a paid production run of the link flow myself; the team tested it through the app.

### **Example 2 — Results before product, and rules from the literature (T4)**

**Define** — Economy articles often agree on the facts and differ in what they put in front. Show the event and each outlet's view of it.

**Context provided** — Economy events with 4–10 articles each, and a literature review on economic news framing.

**Prompts**

```
아니 관련 논문이나 지식들을 활용해서 우리가 코드로 어느정도 구현을 하고 llm은 판단이나 최소한으로 진행하는게 나는 맞다고 생각하는데? 경제 부분도 이게 가능한가
```

I stopped Claude Code when it went ahead to the server, web and Android code before I had seen the output:

```
아니 opus 로 돌려서 결과 퀄리티를 먼저볼게. A~G와 economic lens를 더한 분석 결과를 한번 보고 싶어
```

**Output (excerpt)** — The code-only lens of one event (Q3 household debt), no model call:

```
lens event «증시 빚투 가계빚», articles with a view 10 of 10
  E1 머니투데이: «집·주식 '빚투' 열풍 꺾였다» 하락
  E3 JIBS: «'빚투' 꺾였는데 주택대출은 9조 6,000억 늘었다» 상승
  E4 TV조선: «가계빚 2030조원 또 역대 최대» 상승
```

**Verify**
* I read the lens of each event against its headlines.
* The first version showed channels, tones, sources, numbers and contrasts. I found it cluttered, so it was cut to the event and each outlet's view (#97, #99).
* Code errors remain. In a later event, «수출 둔화에도 내년 성장률 더 높게 본 이유» was labelled "fall" because the code read 둔화.

**Integrate**
* Kept: An economy tab and the lens on the web and Android.
* Modified: Two channels (loan soundness, policy stance), and a direction (rise/fall) instead of a tone for change words.
* Discarded: The contrast block.
* Result: #96–#99.

### **Example 3 — Choosing articles that disagree (T5)**

**Define** — The TA feedback said that we compared articles with the same stance sentence by sentence. For one event, find articles that support, criticise, or report it neutrally.

**Context provided** — The app's search and source analysis, and the four test events.

**Prompts** — Claude Code first proposed that the model label every candidate's stance from its title, and then wrote its own stance rules (for example, "an article that quotes the protagonist is positive"). I asked:

```
아니 llm만으로 모든 것을 판정하려고 하지말라고. 언론사별로 categorization해서 가져온다던가 아니면 다른방법없나? 유사한 task에서 다른사람들은 어떻게 해?
```

```
아니 그냥 만들지말고 논문이나 이와 관련된 task에 참고자료들을 보라고. 너 임의대로 구성하지말고 외부자원을 활용하라고
```

```
그리고 모델 설치를 굳이 너것으로 해야해? 여기서 핵심적인것만 가져오고 필요한건 opus로 하면 되잖아. 그대로 하라는게 아니고 여기서 우리 것에 적용할만한것만 가져와서 반영하라고
```

**Output (excerpt)** — The method follows K-News-Stance / JoA-ICL (EMNLP 2025):
* the annotation guideline's label definitions;
* stances of the headline, lead, conclusion and quotes judged first;
* a protagonist's statement reported as fact counted as neutral;
* an unclear article judged relative to the others.

Retrieval searches the topic and both sides, and classifies stance afterwards (ArgumenText). Over four events:

```
lead stance = article stance      33 of 39
headline stance = article stance  29 of 39
title pre-screen = article stance 28 of 39
```

**Verify**
* The protagonist rule was wrong in principle: quoting someone does not mean agreeing.
* These agreement figures compare the model with itself. Checking against K-News-Stance's labelled data needs a data request that I have not made.

**Integrate**
* Kept: Opus with the JoA ideas (two calls per event), then a greedy choice that covers stances and frames.
* Discarded: The hand-made rules and installing the authors' model.
* Result: The selection used for the eight events of Example 4.

### **Example 4 — The issue view, and whose words a position is (T6)**

**Define** — For one event, list the issues, show where each outlet stands, and show wording differences between outlets. Show it from whichever article the reader opens.

**Context provided** — About ten articles per event (Example 3), Key Point Analysis for the issues (Bar-Haim et al., 2020), and the web reader's code.

**Prompts** — The staged A–G gave 0 comparisons for one event and 32 for another. Then:

```
아그냥 판정기준다 무시하고 그냥 너가 해봐. 우리의 최종goal을 생각하면서 해봐
```

```
정치인 발언끼리의 비교는 제외하고 기자의 단어 선택 비교만 진행하면 좀 줄어들려나? 같은기사에서한번 해줘
```

After the first deployment I asked about the positions in the frame box:

```
즉 이 event에 대해서 특정세력을 위한 특혜법이라고 한거고 부산일보는 중앙일보는 결론을 정해둔 무리한 정치적 기소라고 한거지? 좋은 것 같은데? 여기서 입장을 그대로 한다는거지? 근데 다만 입장오분류문제가 남아있다는거고? 이런것은 언론사의 입장이 없나? 이럴때는 따로 정치인 노조의 말을 띄우는 것은 별로인가?
```

**Output (excerpt)**

```
Reporter-only A–G (one Opus call reads all ten articles):
  추경호 acquittal 7 pairs (5 outlets with none), 민주유공자법 13, 새만금 17

Positions in the issue table, reporter's own words / quoted speakers:
  민주유공자법 1 / 24   조작기소 특검법 4 / 37   추경호 5 / 22   대우조선 0 / 8
  가계빚 12 / 5        KDI 12 / 19
```

**Verify**
* Claude Code had shown me 부산일보 "특정 세력을 위한 특혜법" and 중앙일보 "결론을 정해둔 무리한 정치적 기소" as the outlets' positions.
* When I asked, it checked the evidence: both were 국민의힘's words that the articles quoted.
* The counts above show this was the normal case for politics.

**Integrate**
* Kept: The issue table per event, computed per article on request (two JSON columns, migration 0013).
* Modified: The frame box now shows what the article puts in front in its headline and lead (Pan & Kosicki, 1993):
  * its own position (editorials);
  * the speaker and their words;
  * or the fact its headline adds, cut by code.

  Each outlet in the issue view names the speaker when its position is quoted, e.g. "MBC (더불어민주당)" (#106, #109).
* Discarded: The wording-difference lists and the shared-facts list in the issue view, which I found hard to read.
* Result: Eight events (politics 3, society 3, economy 2) are in the shared DB. At my request, only these eight are in the feed. The 73 other feed articles were hidden, not deleted, and the change can be reverted from a saved list.

---

## **4\. What Worked Well and What Required Human Verification**

### **What worked well**

* **Asking for the source of a rule.** It replaced made-up rules with published task definitions, and each step can now name its source.
* **Closed questions for the model, the rest in code.** Runs became repeatable, and a cheap model became usable.
* **Seeing results before product changes.** I stopped the economic lens implementation midway to look at its output first (Example 2), and the issue view changed three times after I read its output (Example 4).
* **Validated import with a backup.** Every DB change this iteration (45 events, 8 events, the feed change) can be reverted. The 8-event import and its restore were rehearsed on a local SQLite copy first.

### **What required human verification or correction**

| Task | Issue in AI output | How it was detected | Correction made |
| ----- | ----- | ----- | ----- |
| T5 | Made-up stance rules ("quoting the protagonist = positive") | I asked whether the rules were right | K-News-Stance / JoA-ICL definitions and procedure |
| T5 | Stance labels from titles alone were unreliable: most sources came out neutral or mixed, and an off-event article took a slot | Reading the 4-event results | Body-level judgment with part stances; code signals for the pool |
| T1, T2 | 23 and 51 highlights on one article | I read the statistics and questioned them | Rule and lexicon fixes; reran only the affected step |
| T6 | Staged A–G gave 0 and 32 comparisons for two events | I questioned the counts | Reporter-only comparison read over all ten articles |
| T6 | Quoted speakers' claims were shown as outlets' positions, including in the examples shown to me | I asked whether straight news has a position at all | Speaker per position; the front of each article |
| T6 | The frame sentence was sometimes off the event (a 민주유공자법 article quoting a sentence on another bill) | Reading the deployed page | Replaced by the article's front |
| T6 | Fact phrases cut too short («판결»), with broken quotes, or taken from someone's quote | Reading the per-article table | Fixed in code |
| T6 | The first web mock-up ignored the repository's UI | I compared it with the deployed reader | Rebuilt inside the real `ArticlePage` |
| T6 | Remaining misses: «판단에 반발» attributed to the union (it is the reporter's wording); «즉각 처리» as a quote | Reading the table | Not fixed. Listed |

### **Verification practices we used**

* [ ] Code review by another team member before merge (#105, #106 and #109 were merged without a teammate's review)
* [x] Ran / built the code and tested on real articles (local API on SQLite with the eight events; production API checked for the eight feed articles)
* [x] Wrote or ran tests for AI-generated code (import validation and per-article projection tests, web component tests; CI on every PR)
* [x] Checked output against the requirements / design spec (`specs/features/event-issue-view.md`, `specs/features/economic-lens.md`)
* [x] Cross-checked facts with official documentation (the K-News-Stance guideline and repository; papers for each method)
* [ ] Other: no human evaluation this iteration

---

## **5\. Reflection on How AI Affected Our Development Process**

**Productivity** — I could try a design on several events in an afternoon. For example, five new events were analysed with 45 Opus calls in about four minutes, run in parallel. Model usage was the limit. The five-hour limit was hit twice on one day, and by the end of the iteration my weekly quota was nearly used up. Claude Code also spent time on things I had not asked for: rehearsals, waiting for every check, documents. I asked it to keep verification to what changed.

**Quality** — Grounding is strong: every quote, marker and phrase is checked against the stored article. Judgment quality is not measured. The stance agreement figures compare the model with itself. The position labels were wrong for most straight political news until I asked. The next step is the labelled K-News-Stance data and a human check.

**Team workflow**
* Seungmin Kim connected the staged pipeline to the pasted-link flow, so my code ran in the product without my writing the endpoint.
* The DB owner asked for analysis results to be handed over as JSON before import. I imported through the validated importer with backups instead, and I limited the feed to the eight events. These changes are reversible, but they change what the whole team sees. They should have been agreed first.

**Learning** — The hard part was not asking the model for an answer. It was deciding what the model should not decide. When I asked where a rule came from, or whose words a position was, the answer often changed the design.

**Lessons and plan for next iteration**

| What we learned | What we will change next iteration |
| ----- | ----- |
| The model invents rules when asked to classify | Start from a published task definition, and name its source in the code |
| Straight news rarely has the outlet's own position | Show whose words a position is; use an outlet position only for editorials and the reporter's evaluation |
| Self-agreement figures look like accuracy | Request the K-News-Stance labelled data and run a human check of positions and fronts |
| Long verification cycles cost time and quota | Test only what changed; batch display changes into one deployment |
| Shared DB changes affect the whole team | Hand the JSON to the DB owner and agree on feed changes before applying them |
| The eval tools are outside the repository | Move `event_matrix`, `issue_positions` and the dataset converter into the repository with tests |
