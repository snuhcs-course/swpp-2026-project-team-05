# **AI Collaboration Report — Iteration 2 (Junseo Heo)**

|  |  |
| ----- | ----- |
| **Team** | Team 5 — *FrameLESS* |
| **Iteration** | Iteration 2 — Rubric-based Analysis and Integration |
| **Period** | 2026.10.10 – 2026.10.23 |
| **Iteration goals** | 1. Extend the analysis pipeline from claim matching to the team rubric (I1–I5 information, A–G wording)<br>2. Connect the analysis to the Django API and the Android Reader<br>3. Update the Requirements, Design, and Testing documents |
| **Authors** | Junseo Heo |

---

## **1\. Summary of AI Usage**

I used Claude Code as my main development assistant to build version 2 of the article analysis pipeline. I set the analysis rules:

* Every claim, relation, and wording marker must be an exact quote from the article.
* A quoted speaker's words are not the reporter's wording.
* A claim that was not found is not treated as an omission or an intent.
* No article gets an overall score.

### **AI tools used**

| Tool | Model / version | Used by | Main purpose |
| ----- | ----- | ----- | ----- |
| Claude Code | Claude (opus 5.5) | Junseo Heo | Implementing the v2 analysis pipeline (9 Python files), offline check scripts, live test runs, and the README function table |

---

## **2\. Major Tasks Where AI Contributed**

| \# | Task | Related deliverable | AI role | Contribution level | Human owner |
| ----- | ----- | ----- | ----- | ----- | ----- |
| T1 | Number the sentences of the input article and extract atomic claims about its core event (`article_input.py`, `llm_analysis.analyze_source_article`) | Analysis server (v2) | Generated code | High | Junseo Heo |
| T2 | Rank search candidates by metadata, favor different emphasis, verify bodies with speaker attribution, and refill rejected slots (`article_search.py`, `llm_analysis.py`, `comparison_selection.py`) | Analysis server (v2) | Generated code | High | Junseo Heo |
| T3 | Rubric analysis: I1–I5 for each article and A–G for each claim, with actual baselines and withheld scores (`rubric_analysis.py`) | Analysis server (v2) | Generated code | High | Junseo Heo |
| T4 | Structured errors and partial results: `execution_status`, `comparison_status`, error records, and safe file and cache writes (`result_output.py`, `pipeline.py`, `article_fetch.py`) | Analysis server (v2) | Generated code | High | Junseo Heo |
| T5 | Reproducible runs: temperature and seed settings, a snapshot client, a result cache, and snapshot re-evaluation (`model_client.py`, `pipeline.py`) | Analysis server (v2) | Generated code | High | Junseo Heo |
| T6 | Offline checks (28 error-handling checks, 17 rubric regression checks, a URL/offset audit) and the README function table | Local verification, README | Generated code, Drafted | Medium | Junseo Heo |

---

## **3\. Representative Prompts and Outputs**

### **Example 1 — A–G wording comparison with a real baseline (T3)**

**Define** — For each claim, compare how the input article and the articles selected for that claim word the same sub-claim, using the team rubric A–G. Acceptance criteria:

* The marker and its baseline are both exact quotes from the cited passages.
* Words inside a direct quote go to I5, not to A–G.
* Official names are excluded from F.
* The 0–2 markedness is relative to an actual baseline in another article. It is `null`, with a reason, when no comparable baseline exists.

**Context provided** — The rubric specification v0.2.1 (the A–G questions with their 0/1/2 examples, the direct-quote rule, and the proposals P1–P6, which the team had not yet adopted), the verified matches from T2, and the README pipeline, number of model calls, and output fields, which had to stay unchanged.

**Prompt**

```
Using the adopted A–G rubric, compare each claim's wording across the input article and the
articles selected for that claim. Python first builds sentence pairs whose wording differs and
finds reporting-verb candidates outside quotes; these are hints, not scores. The model links
pairs that state the same sub-claim, quotes the exact marker and baseline, and gives a 0–2
markedness relative to that baseline with a reason. Words inside direct quotes go to I5.
Withhold the score when there is no baseline, the propositions differ, or the reason is empty.
Do not apply P1–P6. Keep the README pipeline, call counts, and output fields.
```

**Output (excerpt)**

```
compare_claim_expressions():
  build_expression_pairs (≤ 6 pairs per article; text similarity is not a score)
  + find_reporting_verb_candidates (fixed verb pattern, outside direct quotes)
  → 1 model call per claim (schema: category A–G, marker, baseline_marker, voice, quoted_use,
    name_kind, semantic_relation, markedness 0–2 | null, markedness_reason)
Server-side checks:
  - marker and baseline must be exact substrings of the cited passages
  - direct quote            → excluded (direct_quote_recorded_in_I5)
  - official or proper name → excluded from F
  - forecast vs observed fact, no baseline, empty reason → markedness = null (needs_review / needs_comparison)
```

Full code: [rubric_analysis.py at ef73309](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/ef73309/server/analysis/v2/rubric_analysis.py)

**Verify**

* Ran 17 offline regression checks. All passed. Examples:
  * a reporting verb after a quote stays in A;
  * a word inside a direct quote is excluded;
  * a score without a reason is withheld;
  * no difference is created for identical text.
* Ran the pipeline on a real box-office article (2026-10-17). I read the findings against the articles. One wording (`저력을 발휘하고 있다` against `기록했다`) was kept as E with markedness 1. A forecast compared with an observed fact was withheld.

**Integrate**

* Kept: The whole module moved unchanged into Django. It still serves the older claim-card path in `main`.
* Modified: Teammates limited rubric calls to the three displayed articles ([5eb473e](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/5eb473e)). They also kept verified results when one article failed ([b1b179f](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/b1b179f), [7ccf32c](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/7ccf32c)).
* Not carried into the Reader: The Reader path ([b3d8b5c](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/b3d8b5c)) compares full texts sentence by sentence. Its A–G output keeps the category and the two markers, but not the markedness, baseline, or target fields of this module.
* Result: [rubric_analysis.py in main](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/main/server/analysis/v2/rubric_analysis.py)

### **Example 2 — Candidate selection with verified refill (T2)**

**Define** — For each claim, find up to three articles that report the same event. Acceptance criteria:

* Every selected article is verified on its full body, not on its search snippet.
* Candidates with a different emphasis are preferred over near-duplicate titles.
* A candidate that was never read is not treated as a different event or as missing the claim.

**Context provided** — NAVER metadata (title, description, link), the Iteration 1 verification code, and the relation set I defined (`same`, `opposes`, `different_interpretation`, `same_issue`, `not_found`, `uncertain`). Each relation must also record whether the reporter or a quoted speaker made the statement.

**Prompt**

```
Rank the NAVER candidates for each claim using only their title and description, then prefer
candidates that add a different emphasis and suppress near-duplicate titles. Fetch only the
chosen bodies, verify three bodies per model call, and record for each claim the relation and
whether the evidence is the reporter's text or a quoted speaker. If a candidate fails, refill
its slot first from bodies that were already verified, then from the next reserve candidate.
Stop at three articles or when the reserve list is empty. Never mark an unread candidate as
missing the claim.
```

**Output (excerpt)**

```
rank_comparison_candidates()   1 call, metadata only → initial picks + reserve order
prioritize_diverse_candidates()  character 3-gram similarity suppresses near-duplicate titles
verify_candidate_articles()    3 bodies per call → event relation, claim relation,
                               relation evidence with owner (reporter / speaker / uncertain)
select_verified_comparisons()  reuse verified bodies → read the next reserve → stop at 3 or exhausted
```

Full code: [comparison_selection.py at ef73309](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/ef73309/server/analysis/v2/comparison_selection.py)

**Verify** — Ran a title-diversity check on the same box-office article (2026-10-17). The refill step needed two verification calls: it reused seven earlier body checks and verified three new articles. Each claim ended with three same-event articles, and I read their titles and matched sentences.

**Integrate**

* Kept: Search on metadata → choose → fetch → verify → refill. This is still stages 1–4 of the production path.
* Modified: For the 300-second production limit, teammates changed the Reader settings ([views.py](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/main/server/views.py), [fb4c8c6](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fb4c8c6), [475f8ed](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/475f8ed)):
  * three claims instead of five;
  * four metadata candidates;
  * one body per verification call;
  * an early stop once two independent same-event articles are verified.

  They also added web search as a fallback when NAVER fails ([e2d6276](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/e2d6276)).
* Discarded: None.
* Result: [comparison_selection.py in main](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/main/server/analysis/v2/comparison_selection.py)

### **Example 3 — Run settings and failure handling that needed correction (T4, T5)**

**Define** — Make each run reproducible and keep usable results when a later stage fails. Acceptance criteria:

* Model, temperature, and seed are recorded with every result.
* A saved snapshot can be re-evaluated without new model calls.
* A failure in one stage does not erase the results of earlier stages.

**Context provided** — The Gemini JSON client from Iteration 1 and the stage list in the README.

**Prompt**

```
Add generation settings (model, temperature, seed) with range checks and record them in every
result. Add a snapshot client that stores each task's exact input and response, and a cache so
a repeated URL reuses a complete result. If a later stage fails, return the earlier results
with a structured error instead of raising.
```

**Output (excerpt)**

```python
# model_client.py (local version)
GEMINI_API_KEY = '<redacted>'        # local API settings; environment variables take precedence
NAVER_CLIENT_ID = '<redacted>'
# pipeline.py (local version)
DEFAULT_CACHE_DIR = Path(__file__).resolve().parent / ".analysis_cache"
def _record_failure(result, exc, stage):
    ...
    result["comparison_status"] = "unavailable"   # set for every failed stage
```

**Verify**

* The settings, snapshots, and cache worked in local runs. A real run on 2026-10-17 recorded `gemini-3.1-flash-lite`, temperature 1.0, seed 42, and 17 model calls.
* An earlier attempt on the same article stopped in the rubric stage. One article's I1–I5 evidence still failed validation after a repair call, and the error ended the whole stage.
* Review during the Django port found more problems for a shared server:
  * API keys were filled into the source file as a fallback;
  * the cache folder was inside the code directory, which is read-only on the server;
  * one f-string needed Python 3.12, but the runtime was Python 3.11.

**Integrate**

* Kept: Generation settings, the snapshot client, snapshot re-evaluation, and the error-record format.
* Modified ([ef73309](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/ef73309), [b1b179f](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/b1b179f)):
  * keys are read only from server environment variables;
  * the cache moved to a configurable temporary directory;
  * a rubric failure no longer marks verified comparisons as unavailable;
  * a failed article keeps the other articles' results.
* Discarded: The in-source key fallback. The filled-in keys do not appear in the history of `main` or of any remote branch.
* Result: [model_client.py in main](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/main/server/analysis/v2/model_client.py), [pipeline.py in main](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/main/server/analysis/v2/pipeline.py)

---

## **4\. What Worked Well and What Required Human Verification**

### **What worked well**

* Exact acceptance rules again turned into schema fields and server-side checks. Because of this, the code could move into Django with only import changes and four fixes.
* Asking Claude Code to write check scripts with mocked network calls tested failure handling cheaply. The 28 error-handling checks made zero external API calls.
* Snapshots and the cache let repeated runs reuse recorded model responses, so prompt changes could be compared on the same inputs.
* The URL audit confirmed the extracted text for 16 real articles: all 16 fetched, and the stored offsets matched all 260 cached sentences.

### **What required human verification or correction**

| Task | Issue in AI output | How it was detected | Correction made |
| ----- | ----- | ----- | ----- |
| T1 | One claim joined two facts that are compared separately: total audience, and consecutive days at first place | Reading the claims of a real run (2026-10-17) | The prompt now requires one predicate per claim; the facts became separate claims C1 and C2 |
| T1 | The claim list of a box-office article still included a side topic (police protection for an actor) | Reading the quality-audit output | A separate scope check against the title and lead was added in the Reader rebuild ([b3d8b5c](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/b3d8b5c)) |
| T3, T4 | One article's failed I1–I5 validation ended the whole rubric stage | Live run error after a repair call (2026-10-17) | Verified results kept per article ([b1b179f](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/b1b179f), [7ccf32c](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/7ccf32c)) |
| T4 | A rubric failure marked already verified comparisons as unavailable | Review during the Django port | Status kept as available after stage 4 ([ef73309](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/ef73309)) |

### **Verification practices we used**

* [x] Code review by another team member before merge (Seungmin Kim ported and reviewed the code in [PR #10](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/10))
* [x] Ran / built the code and tested on real articles (local runs on 2026-10-17: 20 metadata candidates, 9 fetched articles)
* [x] Wrote or ran tests for AI-generated code (28 offline error-handling checks and 17 rubric regression checks; `tests/test_v2_pipeline.py` was added during integration)
* [x] Checked output against the requirements / design spec (rubric specification v0.2.1)
* [x] Cross-checked facts with official documentation

---

## **5\. Reflection on How AI Affected Our Development Process**

**Productivity** — Claude Code let me extend a small claim-matching pipeline into a five-stage rubric pipeline of about 3,300 lines before the 10-18 integration. Writing check scripts was cheap with the AI, so I could test failure cases that are hard to reproduce on real sites.

**Quality** — Quality was good where I gave exact rules: grounding, withheld scores, and the direct-quote rule each became a check in code. It was weaker where I gave no requirements. The server's read-only file system, its Python version, its 300-second limit, and secret handling were all fixed by teammates. The number of model calls also grew with the number of articles: the real run used 17 calls. Teammates later had to limit the calls for production.

**Team workflow** — The pipeline has one entry function with a documented result, and the README lists every function. Because of this, a teammate could port it into Django in one commit without changing the analysis logic. The next day, however, the Reader path replaced the per-claim output. The markedness, baseline, and target fields of A–G were not carried into it, because we had not agreed on the output contract before the rebuild.

**Learning** — I learned how to keep LLM output checkable: fixed schemas, exact quotes, and explicit withheld states. I also learned that the same kind of problem can come back. Secrets in source code were fixed in Iteration 1 and appeared again in my local code, so a rule I only remember is not enough.

**Lessons and plan for next iteration**

| What we learned | What we will change next iteration |
| ----- | ----- |
| The Iteration 1 secret-handling problem came back in local code | Keep keys only in a git-ignored `.env` from the first prompt, and scan for secrets before sharing code |
| Local runs did not show server limits (read-only paths, Python 3.11, 300 s) | Put the runtime limits and the call budget in the first prompt, and test in the Django environment before handing off |
| The check scripts stayed in a local folder | Commit them as pytest tests next to the code |
| Rubric fields were lost when another path replaced the stage | Agree on the output contract with the team before rebuilding a stage, and add a contract test |
| Running real articles shows that the pipeline works, not that its judgments are correct | Evaluate A–G and I1 on a human-labelled article set |
