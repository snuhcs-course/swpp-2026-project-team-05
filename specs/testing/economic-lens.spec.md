# Economic lens test specification

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Analysis |
| Source requirements | `specs/features/economic-lens.md`; `GET /api/v1/articles`; `GET /api/v1/articles/{id}/analysis`; Web feed and reader |
| Last reviewed | 2026-11-18 |

## Scope

The `ECONOMY` category in the feed, the import of a reviewed economic lens (event and views), its projection in the
reader analysis with the other outlets' views and contrasts, and its display on the Web reader.

### Non-goals

- The quality of the economic pipeline's judgments and phrases (measured offline against human labels, not in CI).
- Android (keeps the politics and society sections in this change).

## Preconditions

- An economy cluster imported through `news_import.import_dataset`, or created directly in tests.

## Postconditions

### Feed

- [Integration] `category=ECONOMY` lists only economy articles; the politics feed does not list them.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_economy_feed_lists_only_economy_articles`
- [Integration] An unknown category is rejected and the field message names the three sections.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_unknown_category_names_all_three_fields`
- [Component] The Web header links to the economy section and the feed asks for `ECONOMY` articles.\
  Tested by: `web/src/pages/FeedPage.test.tsx › opens the economy section from the header and asks for economy articles`

### Import

- [Integration] An economy article keeps its event and its views as reviewed.\
  Tested by: `tests.test_news_import.NewsDatasetImportTests.test_economy_cluster_keeps_the_event_and_the_article_view`
- [Integration] A lens whose sentence does not match its offsets, whose phrase is not in its sentence, with an
  unknown stance or channel, with no event, with more than two views, or on a non-economy cluster is rejected and
  nothing is stored.\
  Tested by: `tests.test_news_import.NewsDatasetImportTests.test_economic_lens_that_does_not_match_its_article_is_rejected`

### Reader API

- [Integration] The analysis of an economy article carries the event, its views, the other outlets' views (articles
  without a lens left out) and the channel on which another outlet takes a different stance.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_analysis_carries_the_event_the_article_view_and_the_other_outlets_views`
- [Integration] A hidden outlet and a view whose sentence no longer matches the article are left out.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_hidden_outlet_and_stale_view_are_left_out`
- [Integration] An article without a lens answers `economic: null`.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_article_without_economic_lens_answers_null`,
  `tests.test_news_api.NewsApiTests.test_ready_article_can_have_zero_findings`

### Web reader

- [Component] The reader shows the event and this article's views, marks the view sentence and its phrase, lists the
  other outlets with their views, marks the one that differs, and names the contrast.\
  Tested by: `web/src/pages/ArticlePage.test.tsx › shows the event, this article's view in its own words, and how the other outlets see it`
- [Component] An article without a lens shows neither economic section.\
  Tested by: `web/src/pages/ArticlePage.test.tsx › shows no economic lens for an article without one`
