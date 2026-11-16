# Link analysis

| Field | Value |
| --- | --- |
| Status | Reviewed for merge (developer-only capability) |
| Owner | Analysis |
| Related behavior | [`url-analysis-mock.md`](url-analysis-mock.md) §5; test specification [`../testing/link-analysis.spec.md`](../testing/link-analysis.spec.md) |
| Last reviewed | 2026-11-16 |

## 1. Purpose

A developer who has an article link can see how other outlets worded the same event without waiting for that event
to appear in the feed. The developer feed header's `+` button opens the flow; the result opens in the existing
reader. Ordinary and guest accounts keep the v1 read-only product contract and do not see this entry point.

The search empty state keeps the URL analysis mock unchanged; this feature is a separate entry point.

## 2. Flow

1. A developer account taps `+` (left of search). Guests and ordinary accounts do not see the control; the API
   independently returns `401` or `403` if called without developer access.
2. A bottom sheet asks for the article link. An empty field or a value that is not an absolute `http(s)` URL with a
   host stays in the sheet with an inline error and sends no request.
3. `분석하기` sends `POST /api/v1/link-analyses {"url"}` and the sheet shows a progress state: the server is finding
   other outlets' articles and comparing their wording, about one to two minutes.
4. On success the sheet closes and the reader opens the returned article id.
5. On failure the sheet stays with the server's message and the entered link, so the reader can retry.
6. A server without this API yet (it answers 404, 405 or a page that is not the API's JSON) shows
   `링크 분석은 서버 업데이트 후 사용할 수 있어요` instead of a raw HTTP error. The same app works unchanged once
   the server is deployed.

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

- **Authentication**: developer accounts only (`401` for guests, `403` for ordinary accounts). This bounds access to
  the paid, not-yet-human-reviewed pipeline without changing registration or the ordinary news experience.
- **Ingestion safety**: the URL is validated before any fetch (scheme, host, no local or IP hosts, no ports or user
  info), and the existing fetcher pins public DNS results.
- **Deduplication**: one cluster per source URL; a repeated link opens the stored article.
- **Copyright handling**: articles are stored like the reviewed events, but every article of an analysed link is
  `is_feed_visible = false`. It opens only from the link flow and is excluded from the feed and feed search.
- **Lifecycle**: one synchronous request and one analysis at a time per application process (`429` with
  `Retry-After: 30` while busy). There is no queue, polling or notification. Cross-instance serialization is not
  promised; developer-only access keeps this acceptable for the prototype.
- **Cancellation**: closing the sheet stops waiting on the device. The request already sent still finishes on the
  server, and the next attempt for the same link opens the stored result.
- **Failure recovery**:
  - `422` when no same-event article was found or the page could not be read.
  - `503` with `Retry-After` when the model or search is unavailable.
  - Nothing is stored unless the import succeeds; the import runs in one transaction.

## 5. Data boundary

Developer-triggered results may be stored as reader-only clusters through the same validated, transactional import
used by reviewed data (`news_import.import_dataset`, no `--replace-news`, a stable cluster key per source URL). They
remain excluded from feed and search, and their `staged-v6` analysis version distinguishes them from reviewed
fixtures. Incremental imports may create a missing publisher but must not replace existing publisher metadata.

This is an internal prototype capability, not a decision to publish model output in the ordinary feed. Promotion to
feed-visible reviewed data still requires the normal dataset review and import path.

## 6. Acceptance

- `+` appears left of search only for a developer account and opens the link sheet.
- A valid news link ends in the reader with the comparison highlights, and the article does not appear in the feed.
- Invalid input, errors and cancellation behave as in §2 and §4.
