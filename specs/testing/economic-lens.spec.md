# Economic lens test specification

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Analysis |
| Source requirements | `specs/features/economic-lens.md`; `GET /api/v1/articles`; `GET /api/v1/articles/{id}/analysis`; Web feed and reader |
| Last reviewed | 2026-11-17 |

## Scope

The `ECONOMY` category in the feed, the import of a reviewed economic lens, its projection in the reader analysis,
and its display on the Web reader.

### Non-goals

- The quality of the economic pipeline's judgments (measured offline against human labels, not in CI).
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

- [Integration] An economy cluster keeps each article's lens and its comparison, with comparison sides naming the
  stored article ids.\
  Tested by: `tests.test_news_import.NewsDatasetImportTests.test_economy_cluster_keeps_its_economic_lens_with_stored_article_ids`
- [Integration] A lens with sentence text that does not match its offsets, an unknown tone or channel, a comparison
  side outside the cluster, or a lens on a non-economy cluster is rejected and nothing is stored.\
  Tested by: `tests.test_news_import.NewsDatasetImportTests.test_economic_lens_that_does_not_match_its_article_is_rejected`

### Reader API

- [Integration] The analysis of an economy article carries focus, tone, evidence (without the pipeline's internal
  `decided_by`) and the comparison sides with publisher and `is_current`.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_analysis_carries_the_economic_lens_with_compared_coverage`
- [Integration] A hidden compared article and evidence that no longer matches the article are left out.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_hidden_compared_article_and_stale_evidence_are_left_out`
- [Integration] An article without a lens answers `economic: null`.\
  Tested by: `tests.test_news_api.EconomicLensApiTests.test_article_without_economic_lens_answers_null`,
  `tests.test_news_api.NewsApiTests.test_ready_article_can_have_zero_findings`

### Web reader

- [Component] The reader shows the lens summary, underlines evidence sentences, shows the other outlet's sentence on
  the same channel, and switches the panel when another underlined sentence is selected.\
  Tested by: `web/src/pages/ArticlePage.test.tsx › summarises the lens, marks its sentences and shows the other outlet for the selected one`
- [Component] An article without a lens shows no economic panel.\
  Tested by: `web/src/pages/ArticlePage.test.tsx › shows no economic lens for an article without one`
