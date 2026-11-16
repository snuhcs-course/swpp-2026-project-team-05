# Link analysis

| Field | Value |
| --- | --- |
| Status | Draft (feature branch `feat/link-analysis-v6`) |
| Owner | Analysis |
| Related behavior | [`url-analysis-mock.md`](url-analysis-mock.md) §5; test specification [`../testing/link-analysis.spec.md`](../testing/link-analysis.spec.md) |
| Last reviewed | 2026-11-16 |

## 1. Purpose

A reader who has an article link can see how other outlets worded the same event without waiting for that event
to appear in the feed. The feed header's `+` button opens the flow; the result opens in the existing reader.

The search empty state keeps the URL analysis mock unchanged; this feature is a separate entry point.

## 2. Flow

1. The reader taps `+` (left of search). A guest is sent to sign in first.
2. A bottom sheet asks for the article link. An empty field or a value that is not an absolute `http(s)` URL with a
   host stays in the sheet with an inline error and sends no request.
3. `분석하기` sends `POST /api/v1/link-analyses {"url"}` and the sheet shows a progress state: the server is finding
   other outlets' articles and comparing their wording, about one to two minutes.
4. On success the sheet closes and the reader opens the returned article id.
5. On failure the sheet stays with the server's message and the entered link, so the reader can retry.

## 3. Server

1. Fetch the source article. A link already stored answers at once (`status: existing`) with no search or model call.
2. Find same-event articles with the existing discovery pipeline (claims, Naver search, verification).
3. Keep up to three comparison articles that are not stored yet. A stored article is never reused here, because an
   import would move it out of its current cluster.
4. Run the staged A-G analysis (`server/analysis/staged`, model `FRAMELESS_STAGED_MODEL`, default
   `google/gemini-3.8-flash` through OpenRouter) and import the result with `news_import.import_dataset`.
5. Answer `{"article_id", "status": "created", "highlight_count"}`.

## 4. Decisions the mock left open

- **Authentication**: a signed-in user only (`401` for guests). The analysis calls paid models.
- **Ingestion safety**: the URL is validated before any fetch (scheme, host, no local or IP hosts, no ports or user
  info), and the existing fetcher pins public DNS results.
- **Deduplication**: one cluster per source URL; a repeated link opens the stored article.
- **Copyright handling**: articles are stored like the reviewed events, but every article of an analysed link is
  `is_feed_visible = false`. It opens only from the link flow and is excluded from the feed and feed search.
- **Lifecycle**: one synchronous request, one analysis at a time per server (`429` with `Retry-After: 30` while
  busy). There is no queue, polling or notification.
- **Cancellation**: closing the sheet stops waiting on the device. The request already sent still finishes on the
  server, and the next attempt for the same link opens the stored result.
- **Failure recovery**:
  - `422` when no same-event article was found or the page could not be read.
  - `503` with `Retry-After` when the model or search is unavailable.
  - Nothing is stored unless the import succeeds; the import runs in one transaction.

## 5. Open question for the team

The team's data rule is that analysis results reach the shared database as reviewed JSON, imported after a check,
never inserted ad hoc. This feature imports each link's result at once through the same validated import
(`news_import.import_dataset`, no `--replace-news`, a new cluster key per source URL), hidden from the feed. Before
this branch is deployed against the shared database, the team decides one of these:

- accept reader-only clusters from link analysis;
- point link analysis at a separate database or table;
- keep the result only in the response, which would need a separate result screen.

## 6. Acceptance

- `+` appears left of search on the feed header and opens the link sheet for a signed-in reader.
- A valid news link ends in the reader with the comparison highlights, and the article does not appear in the feed.
- Invalid input, errors and cancellation behave as in §2 and §4.
