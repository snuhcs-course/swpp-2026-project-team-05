## AI Collaboration Contribution — Junseo Heo

### 1. Summary of AI Usage

I used Codex as my main development assistant to build the article analysis pipeline. I set the analysis rules: claims must come from the article text, quoted speech must not be treated as fact, NAVER is used only to find related articles, and the model must not judge which outlet is right. Codex implemented the code under these rules. The [first version](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/a9b97c0) (2026-10-03) added five files and about 1,300 lines: `article_fetch.py`, `article_search.py`, `llm_analysis.py`, `model_client.py`, and `related_example.py`. I also documented the pipeline in the README ([5a978da](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/5a978da), [5052a7e](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/5052a7e)).

### 2. Major Tasks Where AI Contributed

| Task | AI contribution | My role |
| --- | --- | --- |
| Article download and text extraction (`article_fetch.py`) | Implemented the download, main-text extraction with `trafilatura`, and title parsing (`og:title`, then `<title>`) | Chose to read the publisher page directly instead of using search snippets, and checked the output on real articles |
| Gemini client (`model_client.py`) | Implemented one adapter for all model calls: JSON schemas, response checks, and automatic function calling turned off | Required JSON-only output and that article text is passed as data, not as instructions |
| Single-article analysis (`llm_analysis.py`) | Implemented text cleaning, sentence numbering, core-event extraction, claim extraction in batches of 12 sentences, and regex rules for wording markers | Defined the labels (sentence roles, claim kinds, argument schemes) and the rule that every claim needs a verbatim quote |
| Related-article search and comparison (`article_search.py`, `llm_analysis.py`) | Implemented the NAVER search query, URL deduplication, same-event screening, local sentence ranking, and claim matching | Defined what "same event" means and the three relation types (`same`, `opposes`, `different_interpretation`) |
| Command-line demo (`related_example.py`) | Wrote a script that prints, for each source sentence, how related articles phrase it | Used it to check results by reading them against the original articles |

### 3. Prompts and Results

The prompts below summarize the instructions I gave Codex. They are reconstructed from the code and commits, not copied from the chat.

**Grounded claim extraction.**

> Split the article into numbered sentences and use Gemini to label each sentence's role and to extract the claims related to the core event. Every claim must include a quote copied exactly from one sentence. Keep a reporter's description, a quoted speaker's statement, and an editorial opinion separate. Fill in a reason only when the text states it. Check the model output in code before using it.

Codex used JSON schemas with fixed label values and added server-side checks. A claim is kept only if its quote appears in the referenced sentence. A `reported_fact` inside quotation marks is relabelled `attributed_statement`. A reason that only repeats the evidence is dropped ([llm_analysis.py](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/a9b97c0/llm_analysis.py)). I reviewed the claims printed by `related_example.py` against the original articles.

**Finding related coverage and comparing it.**

> Use NAVER News Search only to find candidate URLs, then download each article page itself. Keep only candidates that report the same specific event: exclude articles where the action, target, or time is different, and exclude uncertain cases. For each source claim, send the model only the related sentences that the code selected, and accept a match only when it quotes one of those sentences.

Codex implemented `analyze_related_articles()`, which screens candidates in batches with the `same_news_event` call and stops after three matches. It also implemented `compare_issue_passages()`, which ranks sentences locally by keyword and character-bigram overlap before one matching call per related article ([article_search.py](https://github.com/snuhcs-course/swpp-2026-project-team-05/blob/a9b97c0/article_search.py)). The same pipeline still runs in the shared backend. The [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation#31-analysis-pipeline) describes it as stages 1–10.

### 4. What Worked Well and What Required Human Verification

Codex was most useful for well-defined parts: HTML title parsing, JSON schemas, and quote-verification logic. Model output was not trustworthy without checks, so the main design choice was to verify every model answer in code. Integration and later review found several problems in the first version:

| Issue in the first version | How it was found | Correction |
| --- | --- | --- |
| `model_client.py` and `article_search.py` had empty variables meant for pasting API keys into the source, which risked committing keys | Review during backend integration | Keys are read only from environment variables ([f62b037](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/f62b037)) |
| `fetch_article()` downloaded any http(s) URL with no timeout or size limit, which is unsafe once the URL comes from users | Review before deployment | URL validation (no localhost, IP addresses, or custom ports), redirect checks, 15 s timeout, 4 MB limit ([44320eb](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/44320eb)) |
| An extra Gemini retry call for unmatched claims slowed down every request | Stage timing logs on the shared backend | Removed the retry call ([fc0de10](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10)) |


### 5. Reflection on the Development Process

Codex let me write a multi-stage LLM pipeline much faster than I could have alone. It worked best when I gave it precise rules for what the model may and may not decide. Its first version was written for running on my own machine, so security and latency issues appeared only during integration with the shared backend. In the next iteration, I will set deployment requirements (key handling, timeouts, number of model calls) from the start, remove unused code, and evaluate same-event matching and claim matching on the five-event benchmark set.
