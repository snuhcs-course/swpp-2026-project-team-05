# Evaluation feed opening order test specification

| Field | Value |
| --- | --- |
| Status | Implemented; intent reviewed for the evaluation build |
| Owner | Feed feature owner |
| Source requirements | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §4.1 |
| Last reviewed | 2026-11-03 |

## Scope

During evaluation, Web and Android quietly move six reviewed examples already
present in the loaded Latest page before the other rows. The backend, database,
search, Following feed, and cursor order remain unchanged.

### Non-goals

- Recommendation, personalization, a featured section, or fetching extra pages
  to find an example that is not already loaded.

## Preconditions

- The server response remains in its ordinary order.
- The active client feed is Latest with no search query.

## Postconditions

- [Component] Web moves approved titles first in the approved order, preserves
  server order for every other row, and adds no featured label. Search and
  Following retain server order.\
  Tested by: `web/src/pages/FeedPage.test.tsx > FeedPage search > quietly moves reviewed examples first only in Latest`
- [Unit] Android applies the same stable client-only ordering only to Latest and
  leaves search and Following unchanged.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.reviewedExamplesLeadOnlyTheLatestFeed`
- [Component] Web exposes Politics and Society in one global location and does
  not duplicate a Section sidebar.\
  Tested by: `web/src/pages/FeedPage.test.tsx > FeedPage search > keeps section navigation in the global header only`

## Undefined behavior

- A reviewed example not present in the loaded pages is not moved or fetched.
- This temporary order is not an editorial ranking exposed to users.

## Human review checkpoint

- [x] The six titles and their order were explicitly selected.
- [x] No recommendation label or separate surface is introduced.
- [x] The temporary behavior is isolated to clients and regression-tested.
