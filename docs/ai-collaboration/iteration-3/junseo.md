# **AI Collaboration Report — Iteration 3 (Junseo Heo)**

|  |  |
| ----- | ----- |
| **Team** | Team 5 — *FrameLESS* |
| **Iteration** | Iteration 3 — A–G Rubric and Prepared Analysis Dataset |
| **Period** | 2026.10.24 – 2026.11.06 |
| **Iteration goals** | 1. Refine the A–G rubric against real articles<br>2. Build the prepared analysis dataset that the news app reads from its database<br>3. Prepare a five-event set for human evaluation |
| **Authors** | Junseo Heo |

---

## **1\. Summary of AI Usage**

I used Claude Code as my main assistant for the A–G rubric and for the dataset behind the news app. AI had two roles here. It helped me write the rubric and scripts, and Claude Opus 5.5 also produced the data: it chose comparison articles, annotated A–G differences, and reviewed the results. I decided what to compare, what counted as a difference, and how far to check the results.

Human evaluation of the five-event set was done separately, without AI, and is reported in Testing Documentation §5.2. I also read eight model-marked differences myself and read samples of the output. Every accuracy figure in this report comes from LLM judges, not from that evaluation.

### **AI tools used**

| Tool | Model / version | Used by | Main purpose |
| ----- | ----- | ----- | ----- |
| Claude Code | Claude Opus 5.5 | Junseo Heo | Literature search, rubric drafts, experiments, analysis scripts, evaluation set |

---

## **2\. Major Tasks Where AI Contributed**

| \# | Task | Related deliverable | AI role | Contribution level | Human owner |
| ----- | ----- | ----- | ----- | ----- | ----- |
| T1 | Find a basis for the A–G categories in NLP, linguistics, and Korean-language studies | Rubric evidence table | Searched, summarized | Medium | Junseo Heo |
| T2 | Refine the rubric through v5: judgment order, categories set by slot, exclusions, three certainty stages, rule checks | [`feat/reader-ag-rubric-v5`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/efd31e1), `gold_brief.md` | Drafted, Generated code | High | Junseo Heo |
| T3 | Test whether rules can find or check A–G differences without a model | Rule experiment report | Generated code, Judged | Medium | Junseo Heo |
| T4 | Build the 30-case dataset and convert it to the app's JSON format | [`server/fixtures/gold_v5/`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/a53f441) | Generated code, Generated annotations | High | Junseo Heo |
| T5 | Build an issue map for each of the 30 cases | Issue maps (review artifact) | Generated, Verified | High | Junseo Heo |
| T6 | Write the A–G and withheld criteria, and package the five-event evaluation set | Criteria document, evaluation workbook | Drafted, Generated code | Medium | Junseo Heo |

---

## **3\. Representative Prompts and Outputs**

The prompts below are verbatim. Article text stays out of this report because the raw data holds full article bodies.

### **Example 1 — Building the dataset (T4)**

**Define** — Produce reference data for 30 real articles: which part of each article falls under A–G, and how other articles word the same content. Acceptance criteria:

* Every sentence and marker is an exact quote from the article.
* The output converts to the JSON that the team's importer accepts.

**Context provided** — The rubric instructions (`gold_brief.md`), and the numbered sentences of each source article and its comparison articles.

**Prompt**

```
최근 기사 기준으로 기사전문, 기사 전문에서 어떤 부분이 A~G에 들어가는지, 그 부분이 다른 기사에서는 어떻게 나오는지 순으로 30개 기사에 대해서 한번 진행해줘. 
```

**Output (excerpt)**

```
Set 1  two Opus annotators and one adjudicator per article, 1–3 comparison articles
       30 articles, 39 items, mean 1.3 per article, 8 articles with no item, 186 candidates dropped

Set 2  one Opus annotator and one quick Opus review per article, 4–5 comparison articles
       30 articles, 92 items (A 33, B 4, C 7, D 17, E 15, F 1, G 15), mean 3.1 per article,
       4 articles with no item, 13 candidates recorded as dropped
       Review step, in the 29 cases with a recorded result: 84 kept, 5 fixed, 5 removed, 0 added
```

**Verify**

* The team's importer validates that each sentence and marker occurs exactly once in the stored text. A dry run at `862b8b0` passed: 56 publishers, 30 clusters, 178 articles, 91 findings, 160 comparisons.
* This check proves that quotes exist. It does not prove that a difference is real.
* A rule pass over the 160 comparison rows later withheld 37 rows (23%). Some look like real problems and some look like false alarms of the rule. Human judgment has to settle which is which.

**Integrate**

* Kept: Set 2 became the app's data. Seungmin Kim imported it into the database ([a53f441](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/a53f441)).
* Modified: I changed the topics and the number of comparison articles after reading set 1. Set 1 and set 2 use different articles and checks, so the rise in items does not show better recall. My decision to skip adjudication for speed also made the checks weaker.
* Discarded: Set 1 as product data.
* Result: [`server/fixtures/gold_v5/`](https://github.com/snuhcs-course/swpp-2026-project-team-05/tree/iteration-3-demo/server/fixtures/gold_v5). The scripts that made it (`gold2_tools.py`, `gold_to_app_json.py`) are still outside the repository.

### **Example 2 — A wrong boundary and a wrong architecture (T2, T3)**

**Define** — Decide whether the model-marked differences are real, and whether A–G can depend less on the model.

**Context provided** — A list of model-marked differences with their categories, which I had asked Claude Code to prepare so that I could check them.

**Prompt**

```
b => 아이폰이 아쉽다는 / 후기가 잇달앗다, 제보가 잇달앗다
c => 독일체인에 진출햇다/입점했다
c => 일반 관람객을 만난다 / 일반에 공개한다
c => 기록을 보존 요청 / 자료를 보존할 것을 요구했다
d => 빠르게 확장했다 / 넓히고 있다
d => 기저효과가 영향을 미쳤다 / 기저효과가 컸다
e => 네발로 걷기 시작 / 네발로 아장아장 걷기 시작
f => 러시아 경유딜 / 러시아산 경유공급 합의

이게 실제 다른 부분들인가?  의미상으로 같은 부분인거 아닌가?
```

**Output (excerpt)** — Claude Code proposed that Python find the differences and the model only confirm them. I asked to test this first. No model was called for the rule steps; two LLM judges labelled the results.

```
Rules checking candidates the model chose:   20 of 28 real (71%), held-out data 12 of 16 (75%)
Rules finding new differences on their own:  11 of 120 real (9%)
  52 were certainty differences no reader would feel ('계획이다' / '연다')
  34 were words attached to different content
  11 were a speaker's words
```

**Verify**

* The proposal failed on its own test. Rules can tell that words differ, but not whether the words attach to the same content.
* In the first comparison of rubric versions, two LLM judges rated displayed differences at 44% for the old prompt and 80% for v4. They agreed on 112 of 126 items, and the sample was 18 articles.
* On v5, a review agent that I asked to read as a person counted 19 of 22 displayed differences as real (86%). That agent is also a model, not a person.
* The LLM judges disagreed with me on '빠르게 확장했다 / 넓히고 있다': they counted it as a difference and I did not. This is why I do not report these figures as accuracy.

**Integrate**

* Kept: The model finds differences; rules check them and classify closed lists.
* Modified: The rubric now excludes neutral synonyms from C and mimetic words from E, and it uses three certainty stages for G instead of five. The rules were also removing strong real differences ('하필', '패색이 짙은', '풀이된다'), which I corrected ([efd31e1](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/efd31e1)).
* Discarded: The architecture in which rules find differences first.
* Result: Rubric v5 is on [`feat/reader-ag-rubric-v5`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/efd31e1). That branch is not merged, and the news app does not run it. The product uses v5 only through the dataset, because `gold_brief.md` applies the same rubric.

### **Example 3 — Issue maps (T5)**

**Define** — Show how outlets treat each issue in one event, because A–G misses differences in what an article includes.

**Context provided** — Gold set 2 and its articles.

**Prompt**

```
이걸 지금 다른 형태로 볼 수 있나? 이거를 조금 다르게 생각하면 한 주제 안에 여러언론사들의 기사들을 일괄 찾고, 그 기사들에 대해서 '묶음'으로 분석을 하는 방식이 가능할 것 같은데 어떻게 생각해
```

```
명제는 조금 작은 단위인것 같고 조금 확장시켜서 한 내용을 잡고 같은 쟁점을 어떻게 말했는지로 정리하자?
```


**Output (excerpt)**

```
One Opus builder and one independent Opus verifier per case
30 cases, 234 issues, 367 findings
Fixes reported by the verifier: 319
```

**Verify** — A separate model call checked each map, and code checked that quotes occur in the articles. The 319 fixes show how much the first pass needed correction. Of the 367 findings, 131 (36%) were content differences that no A–G code can describe.

**Integrate**

* Kept: Issue maps as a review artifact.
* Modified: None.
* Discarded: Storing issue maps in the database. The app does not read them.
* Result: The 131 findings gave me a measure of what A–G does not cover, and I recorded it in the criteria document.

---

## **4\. What Worked Well and What Required Human Verification**

### **What worked well**

* Testing a proposal before adopting it. The rule experiment took no new model calls for the rule steps, and it overturned a design that looked reasonable.
* Exact quotes as a hard requirement. The importer could check every sentence and marker by code.
* Parallel subagents. Gold set 2 took about 40 minutes from request to last result.
* Fixed output files. Seungmin Kim could build the database and importer against the JSON while I kept working on the analysis.

### **What required human verification or correction**

| Task | Issue in AI output | How it was detected | Correction made |
| ----- | ----- | ----- | ----- |
| T2 | Neutral synonyms and mimetic words were marked as differences (진출/입점, 아장아장) | I read eight examples against the articles | Exclusions added to C and E |
| T3 | The proposed design, rules first and model second, found mostly wrong differences | Rule experiment: 9% of new differences were real, by LLM judges | Design reversed |
| T2 | Rule checks removed strong real differences ('하필', '패색이 짙은', '풀이된다') | Review of results on 10 articles | Rules corrected ([efd31e1](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/efd31e1)) |
| T4 | The first set was very sparse, with 8 of 30 articles having no item | I read the counts and questioned them | New topics and more comparison articles. Recall itself is not measured |
| T4 | About 37% of items (34 of 92, estimated from a word list) have the marked word only in the comparison article, while the database stores the source side. A neutral phrase can then appear as evaluative (b13, '입원한 곳이기도 하다') | Reading the stored data while writing the criteria | Not fixed. Listed for a team decision |
| T4 | One case (b36) has sentences from another article mixed into a comparison article's body | Checking cases for the evaluation set | Excluded from the evaluation set. It is still in the dataset |
| T1 | Part of the Korean sources had not been read further than the first pass, and some sources are known only through other papers (Martin & White, van Leeuwen) | Reading the literature report | I asked for the 14 Korean papers to be read further. The evidence table states how far each source was read |

### **Verification practices we used**

* [ ] Code review by another team member before merge (the v5 branch is not merged)
* [x] Ran / built the code and tested on real articles (importer dry run at `862b8b0`, v5 on real articles)
* [x] Wrote or ran tests for AI-generated code (the 314 tests in `main` still passed with the three v5 files swapped in; rule regression on saved answers)
* [x] Checked output against the requirements / design spec (`specs/product/news-app-v1.md`)
* [ ] Cross-checked facts with official documentation
* [x] Other: human evaluation of the five-event set, done without AI (Testing Documentation §5.2)

---

## **5\. Reflection on How AI Affected Our Development Process**

**Productivity** — AI made it possible to produce and revise 30 cases several times within days. It also cost a lot. Replaying the whole pipeline used so many tokens that I stopped it and ran only the A–G stage.

**Quality** — I can show that the data is grounded in the articles. Whether its judgments are correct is a question for the human evaluation (Testing Documentation §5.2), not for the LLM judges. Asking a model to review a model made the numbers look better than what I can defend, and my own reading disagreed with the judges on at least one case.

**Team workflow** — The fixed JSON format let the dataset and the database move in parallel. One gap remains. The team agreed on 10/31 to analyze each article alone and then map comparisons, but the current dataset still compares against one source article.

**Learning** — Most of the difficulty was in deciding whether two sentences say the same thing, not in assigning a code. A–G also cannot describe every difference. I learned to test an AI proposal before adopting it, and to ask who judged a number before I repeat it.

**Lessons and plan for next iteration**

| What we learned | What we will change next iteration |
| ----- | ----- |
| LLM judges agree with each other but can differ from a person | Report accuracy from the human evaluation, not from LLM judges |
| Skipping adjudication for speed weakened the dataset's checks | Record which checks each dataset passed, and say who reviewed it |
| A plausible design failed once it was measured | Test proposed designs on saved data before implementing them |
| The analysis scripts are outside the repository | Move them into the repository with tests |
| The stored marker can sit on the neutral side of a comparison | Agree on the data contract with teammates before exporting |
