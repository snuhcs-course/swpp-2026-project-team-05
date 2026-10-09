# **AI Collaboration Report — Iteration 1 (Junseo Heo)**

|  |  |
| ----- | ----- |
| **Team** | Team 5 — *FrameLESS* |
| **Iteration** | Iteration 1 — Planning and Prototyping |
| **Period** | 2026.09.29 – 2026.10.09 |
| **Iteration goals** | 1. Build a working MVP: article URL → same-event coverage → claim comparison<br>2. Start the Requirements and Design documents<br>3. Set up the project schedule |
| **Authors** | Junseo Heo |

---

## **1\. Summary of AI Usage**

I used Codex as my main development assistant to build the article analysis pipeline, the server-side part of the MVP. I set the analysis rules: claims must come from the article text, quoted speech must not be treated as fact, NAVER is used only to find related articles, and the model must not judge which outlet is right. Codex implemented the code under these rules. The [first version](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/a9b97c0) (2026-10-03) added five files and about 1,300 lines: `article_fetch.py`, `article_search.py`, `llm_analysis.py`, `model_client.py`, and `related_example.py`. After the shared backend was deployed, I used Claude Code to check the Design Documentation against the code and to draft this report.

### **AI tools used**

| Tool | Model / version | Used by | Main purpose |
| ----- | ----- | ----- | ----- |
| Codex | GPT-based Codex model | Junseo Heo | Implementing the article analysis pipeline (5 Python files) and the README section |

---

## **2\. Major Tasks Where AI Contributed**

| \# | Task | Related deliverable | AI role | Contribution level | Human owner |
| ----- | ----- | ----- | ----- | ----- | ----- |
| T1 | Download an article page and extract title and body (`article_fetch.py`) | Prototype | Generated code | High | Junseo Heo |
| T2 | Gemini client with JSON-schema output (`model_client.py`) | Prototype | Generated code | High | Junseo Heo |
| T3 | Single-article analysis: core event, sentence roles, grounded claims, wording markers (`llm_analysis.py`) | Prototype | Generated code | High | Junseo Heo |
| T4 | Related-article search, same-event screening, and claim comparison (`article_search.py`, `llm_analysis.py`) | Prototype | Generated code | High | Junseo Heo |
| T5 | Command-line demo and README description of the pipeline (`related_example.py`, [5a978da](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/5a978da), [5052a7e](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/5052a7e)) | Prototype, README | Generated code, Drafted | Medium | Junseo Heo |

---

## **3\. Representative Prompts and Outputs**

The prompts below summarize the instructions I gave Codex. They are reconstructed from the code and commits, not copied from the chat.

### **Example 1 — Grounded claim extraction (T3)**

**Define** — Extract the claims of an article so that every claim can be traced to an exact sentence. Acceptance criteria: each claim has a quote that exists in the original sentence; a reporter's description, a quoted speaker, and an editorial opinion are labelled differently; a reason is attached only when the text states it.

**Context provided** — The project goal (compare how outlets describe the same event, without judging them), the label sets I defined (sentence roles, claim kinds, argument schemes), and the Gemini model to use.

**Prompt**

```
Split the article into numbered sentences and use Gemini to label each sentence's role and to
extract the claims related to the core event. Every claim must include a quote copied exactly
from one sentence. Keep a reporter's description, a quoted speaker's statement, and an
editorial opinion separate. Fill in a reason only when the text states it. Check the model
output in code before using it.
```

**Output (excerpt)**

```
analyze_article(): clean_article_body → _numbered_sentences → extract_core_event (1 call)
  → analyze_sentences (1 call per 12 sentences, JSON schema with fixed enums)
Server-side checks after each call:
  - keep a claim only if evidence_quote is a substring of the referenced sentence
  - reported_fact inside quotation marks → relabelled attributed_statement
  - drop a reason that only repeats the evidence quote
```

Full code: [llm_analysis.py at a9b97c0](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/a9b97c0/llm_analysis.py)

**Verify** — Ran `related_example.py` on real NAVER News articles and read the printed claims and quotes against the original articles.

**Integrate**

* Kept: Sentence numbering, JSON schemas, and all quote and label checks. They are still on the request path of the shared backend.
* Modified: Model calls now go through a client with retries for Gemini 5xx errors ([fc0de10](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10)).
* Discarded: An earlier approach that grouped claims across all articles before comparing them.
* Result: [Design Documentation §3.1–3.2](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation#31-analysis-pipeline)

### **Example 2 — Related coverage search and comparison (T4)**

**Define** — Find up to three articles about the same event as the input article, and show how each one describes the input article's claims. Acceptance criteria: an article about the same person but a different event is excluded; every match points to a sentence the server can check.

**Context provided** — The NAVER API HUB News Search documentation, the output of T3, and the three relation types I defined (`same`, `opposes`, `different_interpretation`).

**Prompt**

```
Use NAVER News Search only to find candidate URLs, then download each article page itself.
Keep only candidates that report the same specific event: exclude articles where the action,
target, or time is different, and exclude uncertain cases. For each source claim, send the
model only the related sentences that the code selected, and accept a match only when it
quotes one of those sentences.
```

**Output (excerpt)**

```
analyze_related_articles():
  NAVER search (≤ 4 keywords, 50 results) → deduplicate URLs (≤ 20 candidates)
  → fetch candidates in batches of 6 → Gemini same_news_event → stop at 3 matches
compare_issue_passages():
  rank sentences locally (keywords + character bigrams, top 4 + neighbours)
  → 1 Gemini call per related article → keep matches whose quote is in an allowed sentence
```

Full code: [article_search.py at a9b97c0](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/a9b97c0/article_search.py)

**Verify** — Checked that the selected related articles described the same event as the input article, and read each matched quote in the related article.

**Integrate**

* Kept: Search → same-event screening → local retrieval → matching. This is the pipeline the shared backend runs.
* Modified: Stage-level logs and request IDs were added so slow requests can be traced ([fc0de10](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10)).
* Discarded: An extra Gemini call that retried unmatched claims, because it added latency to every request ([fc0de10](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10)).
* Result: [Design Documentation §2.3 Request Data Flow](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation#23-request-data-flow)

### **Example 3 — Article download that needed correction (T1, T2)**

**Define** — Download one news URL and return its title and main text. Acceptance criteria: works on NAVER News pages; fails with a clear error when no article text is found.

**Context provided** — An example NAVER News URL and the choice of `trafilatura` for main-text extraction.

**Prompt**

```
Given a news article URL, download the page and return its title and main body as plain text.
Prefer og:title for the title. Raise a clear error if the page cannot be downloaded or has no
article text.
```

**Output (excerpt)**

```python
html = fetch_url(url)            # trafilatura download: any http(s) URL, no timeout or size limit
body = extract(html, url=url, include_comments=False)
```

The same first version also left empty variables in `model_client.py` and `article_search.py` (`GEMINI_API_KEY = ""`, `NAVER_CLIENT_ID = ""`) for pasting API keys into the source.

**Verify** — The code worked for local runs on my machine, but review during backend integration found that it was unsafe for a public server. Users would supply the URL, so the server could be made to request internal addresses, and a hard-coded key could be committed by mistake.

**Integrate**

* Kept: `trafilatura` main-text extraction and the `og:title` → `<title>` parser.
* Modified: Download replaced with a checked request: no localhost, `.local`/`.internal` hosts, IP addresses, custom ports, or credentials; every redirect is re-checked; HTML only; 15 s timeout; 4 MB limit ([44320eb](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/44320eb)). API keys are read only from environment variables ([f62b037](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/f62b037)).
* Discarded: The in-source key variables.
* Result: [article_fetch.py in the shared backend](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/main/server/analysis/article_fetch.py)

---

## **4\. What Worked Well and What Required Human Verification**

### **What worked well**

* Codex quickly produced well-defined parts: HTML title parsing, JSON schemas, and quote-verification logic needed few changes.
* Writing exact rules for the model ("exclude uncertain cases", "copy the quote exactly") gave code that was easy to check, because each rule became a schema field or a server-side check.
* Splitting the pipeline into small model calls made each intermediate result visible in `related_example.py`, which made errors easier to locate.

### **What required human verification or correction**

| Task | Issue in AI output | How it was detected | Correction made |
| ----- | ----- | ----- | ----- |
| T1 | `fetch_article()` downloaded any http(s) URL with no timeout or size limit | Review before deployment | URL validation, redirect checks, 15 s timeout, 4 MB limit ([44320eb](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/44320eb)) |
| T2, T4 | Empty variables for pasting API keys into the source code | Review during backend integration | Keys read only from environment variables ([f62b037](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/f62b037)) |
| T4 | An extra Gemini retry call for unmatched claims slowed down every request | Stage timing logs on the shared backend | Retry call removed ([fc0de10](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10)) |

### **Verification practices we used**

* [x] Code review by another team member before merge ([PR #2](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/2))
* [x] Ran / built the code and tested on real articles (`related_example.py`)
* [ ] Wrote or ran tests for AI-generated code (no unit tests for these five files in Iteration 1)
* [x] Checked output against the requirements / design spec (wiki Design Documentation compared with the code)
* [ ] Cross-checked facts with official documentation
* [ ] Other:

---

## **5\. Reflection on How AI Affected Our Development Process**

**Productivity** — Codex let me write a multi-stage LLM pipeline (about 1,300 lines) in a much shorter time than I could have alone. The cost appeared later: security and latency problems had to be fixed during integration.

**Quality** — Quality was good where I gave precise rules, because the rules turned into checks in code. It was weaker where I gave no requirements: the first version had no timeouts, no URL restrictions, and no limit on model calls.

**Team workflow** — Because the pipeline is one function (`analyze_related_articles`) with a documented JSON result, a teammate could connect it to the Django API and the Android app without changing the analysis code.

**Learning** — Reading and checking the generated code taught me how schema-constrained LLM output, local retrieval, and quote verification fit together. I still need to understand the deployment side better, since those issues were found by others.

**Lessons and plan for next iteration**

| What we learned | What we will change next iteration |
| ----- | ----- |
| Code written for a local run was not safe or fast enough for a shared server | Give deployment requirements (key handling, timeouts, number of model calls) in the first prompt |
| AI-generated code had no tests, so changes were checked only by running examples | Add unit tests with mocked NAVER and Gemini calls for the five files |
| Running a few articles does not measure comparison quality | Evaluate same-event matching and claim matching on the five-event benchmark set with human labels |
