# Reviewed feed opening order test specification

| Field | Value |
| --- | --- |
| Status | Implemented; intent reviewed for the evaluation feed |
| Owner | Feed feature owner |
| Source requirements | `docs/news-app-v1-spec.md` §4.1 and §6.3; `docs/news-app-v1-ui-spec.md` §4.1 |
| Last reviewed | 2026-11-03 |

## Scope

The ordinary feed quietly opens with six reviewed analysis examples and then
continues in its existing reverse-chronological order. Search relevance remains
independent, and the client does not label the rows as recommendations.

## Preconditions

- Feed-visible articles may have a non-negative stored `feed_priority`.
- All articles without an explicit opening position have priority zero.

## Postconditions

- [Integration] Ordinary-feed rows sort by priority, then publication time and
  stable article ID; cursor pagination crosses from prioritized to ordinary rows
  without duplicates or gaps.\
  Tested by: `tests.test_news_api.NewsApiTests.test_feed_priority_precedes_date_and_paginates_stably`
- [Integration] Search keeps title/publisher/body relevance order and does not
  elevate a result because it has feed priority.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_ignores_feed_priority`
- [Unit] Gold-set projection assigns priority only to the six reviewed source
  articles in the approved order.\
  Tested by: `tests.test_gold_dataset.GoldDatasetConversionTests.test_reviewed_feed_opening_order`
- [Component] The Web feed exposes Politics and Society in one global location
  and does not duplicate a Section sidebar.\
  Tested by: `web/src/pages/FeedPage.test.tsx > FeedPage search > keeps section navigation in the global header only`

## Undefined behavior

- This ordering is editorial presentation, not a relevance score or user
  preference.
- Search, saved articles, and comparison-only articles do not use feed priority.

## Human review checkpoint

- [x] The six opening stories and their order were explicitly selected.
- [x] No recommendation label or separate featured surface is introduced.
- [x] Search and cursor behavior are covered by regression tests.
