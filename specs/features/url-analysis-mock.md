# Retired URL analysis mock

| Field | Value |
| --- | --- |
| Status | Retired; no ordinary-user entry point |
| Owner | Article discovery feature owner |
| Related behavior | [`news-app-features.md`](news-app-features.md) §§2–8 |
| Last reviewed | 2026-11-24 |

## 1. Decision

The client-only URL-analysis preview is removed from Web and Android. A search
with no matches is an ordinary search empty state: it explains that nothing was
found and asks the reader to try another term or check the spelling. It exposes
no URL field, loading preview, modal sheet, or article-analysis action.

## 2. Search empty state

- It appears only after a valid completed search returns zero articles.
- It remains distinct from loading, request failure, a blank query, an empty
  feed, and an empty Following feed.
- Both clients keep the query editable in the search field above the message.
  Android has no separate `검색어 다시 입력` button; the field's clear control
  remains available.

## 3. Compatibility boundary

Removing the UI does not delete the legacy server endpoint, stored analysis
rows, or internal client data code. They may remain for compatibility and
deterministic tests, but ordinary navigation must not expose them. Reintroducing
URL ingestion requires a new reviewed product decision rather than reviving the
retired mock implicitly.

## 4. Acceptance

- Web and Android show a concise no-results message after a zero-result search.
- Neither client exposes URL entry or an analysis CTA from search or the home
  menu.
- No URL-analysis request or persistent mutation can be initiated from the
  ordinary UI.

