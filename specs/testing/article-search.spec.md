# Article search v1 test specification

| Field | Value |
| --- | --- |
| Status | Implemented; intent review inherited from the product specification |
| Owner | Article search feature owner |
| Source requirements | [`../features/news-app-features.md`](../features/news-app-features.md) §§2–8 |
| Last reviewed | 2026-11-05 |

## Scope

Signed-in users search the complete feed-visible article corpus from Web and
Android. Django owns normalization, filtering, deterministic ranking, excerpts,
and cursor integrity. Clients own query state, debounce, and restoration of the
ordinary feed.

### Non-goals

- Semantic search, suggestions, typo correction, and searching analysis text or
  comparison-only articles.
- Proving the editorial quality of an article or its analysis.

## Preconditions

- The request has a valid session.
- Searchable rows have `visibility=VISIBLE` and `is_feed_visible=true`.
- `q`, when present, normalizes to 2–100 characters.
- Category, publisher, limit, and cursor use the existing article-feed contract.

## Postconditions

### Backend success

- [Unit] A query is Unicode-normalized, trimmed, and has internal whitespace
  collapsed.\
  Tested by: `tests.test_article_search.ArticleSearchTextTests.test_query_is_trimmed_whitespace_collapsed_and_unicode_normalized`
- [Integration] Title, body, and case-insensitive publisher matches are returned;
  a body match has a grounded excerpt and a publisher-only match does not.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_matches_title_body_and_publisher`
- [Integration] Match tiers are ordered title, publisher, then body regardless of
  publication recency across tiers.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_ranks_title_then_publisher_then_body`
- [Integration] Category and publisher filters compose with search, and hidden or
  comparison-only articles never appear.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_combines_filters_and_excludes_non_feed_articles`
- [Integration] `total_count` is exact for each category after feed-visibility
  filters and remains the pre-cursor total on later pages; ordinary feed pages
  omit the field.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_total_count_matches_each_visible_category_before_pagination`, `tests.test_news_api.NewsApiTests.test_search_validates_query_length_and_feed_shape_is_unchanged`
- [Unit] A body excerpt is grounded, bounded to the configured maximum, and uses
  ellipses when surrounding text is omitted.\
  Tested by: `tests.test_article_search.ArticleSearchTextTests.test_excerpt_is_bounded_and_contains_the_grounded_match`
- [Unit] A query absent from the body produces no excerpt.\
  Tested by: `tests.test_article_search.ArticleSearchTextTests.test_excerpt_is_none_without_a_body_match`
- [Integration] Search pagination continues without duplicating the first row and
  a cursor is rejected under a different query.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_cursor_is_bound_to_query_and_paginates_ranked_results`
- [Integration] A missing query preserves the ordinary feed response shape.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_validates_query_length_and_feed_shape_is_unchanged`
- [Integration] All article endpoints, including search, require authentication.\
  Tested by: `tests.test_news_api.NewsApiTests.test_news_endpoints_require_authentication`

### Backend errors and boundaries

- [Integration] Normalized queries shorter than 2 or longer than 100 characters
  return a field-specific validation error.\
  Tested by: `tests.test_news_api.NewsApiTests.test_search_validates_query_length_and_feed_shape_is_unchanged`
- [Integration] Invalid or tampered cursors use the existing `INVALID_CURSOR`
  response.\
  Tested by: `tests.test_news_api.NewsApiTests.test_feed_rejects_tampered_cursor`
- [Integration] Results within the same match tier are newest-first with article
  ID as the stable tie-breaker.\
  Tip: add a same-tier fixture with equal and unequal publication timestamps.
- [Unit] An excerpt containing stored angle brackets remains plain data and never
  becomes server-provided HTML.\
  Tip: add an excerpt case containing HTML-like source text.

### Web client

- [Unit] Search query, category, repeated publisher filters, and cursor are URL
  encoded without losing values.\
  Tested by: `web/src/lib/api.test.ts > articleQueryString > encodes search, filters, and cursor without losing repeated publishers`
- [Unit] Ordinary feed requests omit search-only parameters.\
  Tested by: `web/src/lib/api.test.ts > articleQueryString > keeps the ordinary feed request free of search-only parameters`
- [Component] Search input remains local and does not mutate the URL. Korean IME
  composition does not submit an unfinished query; after composition ends,
  debounce sends only the final normalized query.\
  Tested by: `web/src/pages/FeedPage.test.tsx > keeps IME input local and searches only after composition ends`
- [Component] One query searches Politics and Society with `ALL` scope, keeps the
  pre-search URL state, and renders exact count pills for both sections.\
  Tested by: `web/src/pages/FeedPage.test.tsx > searches both categories and shows exact section counts`
- [Component] Failure in one Web category does not discard the successful
  category, and retry refreshes only the failed section.\
  Tested by: `web/src/pages/FeedPage.test.tsx > keeps a successful category visible while retrying the failed category`
- [Component] Clear returns to feed; filter changes reset pagination; loading,
  empty, and error states remain distinct.\
  Tip: extend `FeedPage.test.tsx` with filter and failure-state cases.
- [Acceptance] Selecting a result opens the existing article route.\
  Tip: verify through the Iteration 3 acceptance procedure after deployment.

### Android client

- [Unit] The API client encodes the query with repeated publishers and parses the
  optional excerpt.\
  Tested by: `com.swpp.team5.frameless.data.NewsApiClientTest.newsEndpointsParseFeedDetailAndGroundedComparison`
- [Unit] The repository forwards the active query through the authenticated news
  service.\
  Tested by: `com.swpp.team5.frameless.data.NewsRepositoryTest.preferencesRoundTripAndNetworkCallsUseAuthenticatedService`
- [Unit] Input shorter than two characters does not search; rapid input is
  debounced to the latest query; both categories search independently; closing
  search restores the existing feed.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.searchDebouncesServerQueryAndClosingRestoresFeed`
- [Component] Politics and Society headers expose their exact result counts.\
  Tested by: `com.swpp.team5.frameless.ui.screens.NewsScreensInstrumentedTest.searchGroupsBothCategoriesWithExactCounts`
- [Unit] Failure and retry are isolated to one Android category section.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.searchRetriesOneCategoryWithoutDiscardingTheOther`
- [Unit] A response from a request that became obsolete after a newer request
  started cannot replace the newer result.\
  Tip: add a controllable delayed data source and complete requests out of order.
- [Component] Empty, initial-error, and next-page-error search states render
  distinctly and a result opens Reader.\
  Tip: add focused Compose tests when the device test harness covers search.

## Undefined behavior

- Ordering is not a semantic-relevance, popularity, truthfulness, or balance
  judgment.
- Results may change when article visibility, feed membership, stored text, or
  publication metadata changes.
- v1 makes no latency guarantee for a future corpus size beyond using bounded
  server-side pagination.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Search field | title / publisher / body / no match | Product spec §2.3 |
| Query boundary | empty / 1 / 2 / 100 / 101 characters | Product spec §§2.3, 3.2 |
| Corpus membership | visible feed / comparison-only / hidden | Product spec §2.2 |
| Ordering | cross-tier / same-tier timestamp / ID tie | Product spec §2.4 |
| Cursor state | same context / changed query / changed filter / tampered | Product spec §3.1 |
| Client state | pre-search / pending / two category sections / partial failure / empty / restored feed | Product spec §§5–6 |

## Human review checkpoint

- [x] Behavior is traceable to the reviewed article-search product specification.
- [x] Current executable tests are mapped without claiming uncovered UI behavior.
- [ ] Add the explicit same-tier, HTML-like excerpt, stale-response, and component
  tests recorded above.
