# Link analysis

| Field | Value |
| --- | --- |
| Status | Compatibility-only; hidden from ordinary navigation |
| Owner | Analysis |
| Related behavior | Retired UI decision in [`url-analysis-mock.md`](url-analysis-mock.md); test specification [`../testing/link-analysis.spec.md`](../testing/link-analysis.spec.md) |
| Last reviewed | 2026-11-24 |

## 1. Purpose

The legacy endpoint and stored reader-only clusters remain for compatibility and
deterministic server tests. Web and Android do not expose URL submission in the
feed, home menu, search empty state, or ordinary Reader navigation.

## 2. Flow

There is no ordinary-client flow. Direct internal callers may still send
`POST /api/v1/link-analyses {"url"}` and receive the compatibility behavior in
§3–5. Reintroducing a client flow requires a new reviewed UI and lifecycle
contract.

## 3. Server

1. Check the exact stored URL first. A stored link answers at once (`status: existing`) without fetching the page or
   requiring a model key. Otherwise fetch and canonicalize the source, then check the canonical URL again.
2. Find same-event articles with the existing discovery pipeline (claims, Naver search, verification).
3. Keep up to three comparison articles that are not stored yet. A stored article is never reused here, because an
   import would move it out of its current cluster.
4. Run the staged A-G analysis (`server/analysis/staged`, model `FRAMELESS_STAGED_MODEL`, default
   `google/gemini-3.8-flash` through OpenRouter) and import the result with `news_import.import_dataset`. Runtime
   imports preserve existing publisher names, domains, logos and display order.
5. Answer `{"article_id", "status": "created", "highlight_count"}`.

## 4. Decisions the mock left open

- **Access and limits**: anyone may call the API, signed in or not. Each new analysis is a paid model run, so a caller
  that is not a developer account has a daily limit per client address
  (`FRAMELESS_LINK_ANALYSIS_PER_CLIENT_DAILY`, default 3) and all such callers share a daily total
  (`FRAMELESS_LINK_ANALYSIS_DAILY`, default 30). Over either limit the API answers `429 DAILY_LIMIT`. Developer
  accounts have no limit. A stored link opens at once and never counts; a link whose page could not be read, or a
  server without the model key, does not count either. Usage rows store a hash of the address, never the address,
  and are not tied to an account, so they stay out of every reader's history.
- **Ingestion safety**: the URL is validated before any fetch (scheme, host, no local or IP hosts, no ports or user
  info), and the existing fetcher pins public DNS results.
- **Deduplication**: one cluster per source URL; a repeated link opens the stored article.
- **Copyright handling**: articles are stored like the reviewed events, but every article of an analysed link is
  `is_feed_visible = false`. It opens only from the link flow and is excluded from the feed and feed search.
- **Lifecycle**: one synchronous request and one analysis at a time per application process (`429` with
  `Retry-After: 30` while busy). There is no queue, polling or notification. Cross-instance serialization is not
  promised; the daily limits keep this acceptable for the prototype.
- **Cancellation**: closing the sheet stops waiting on the device. The request already sent still finishes on the
  server, and the next attempt for the same link opens the stored result.
- **Failure recovery**:
  - `422` when no same-event article was found or the page could not be read.
  - `503` with `Retry-After` when the model or search is unavailable.
  - Nothing is stored unless the import succeeds; the import runs in one transaction.

## 5. Data boundary

Link-analysis results may be stored as reader-only clusters through the same validated, transactional import
used by reviewed data (`news_import.import_dataset`, no `--replace-news`, a stable cluster key per source URL). They
remain excluded from feed and search, and their `staged-v6` analysis version distinguishes them from reviewed
fixtures. Incremental imports may create a missing publisher but must not replace existing publisher metadata.

This is an internal prototype capability, not a decision to publish model output in the ordinary feed. Promotion to
feed-visible reviewed data still requires the normal dataset review and import path.

## 6. Acceptance

- No ordinary Web or Android surface exposes URL submission.
- A reader who is not on a developer account gets `429 DAILY_LIMIT` after the daily limit; a developer account never
  does.
- A valid direct API request stores a reader-only result that does not appear in the feed.
- Invalid input and server failures behave as in §3–4.
