# Event issue view test specification

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Analysis |
| Source requirements | `specs/features/event-issue-view.md`; `GET /api/v1/articles/{id}/analysis`; Web reader |
| Last reviewed | 2026-11-20 |

## Scope

The import of a reviewed issue table and article frames, their projection onto each article of the event, and the
Web reader's frame box and issue view.

### Non-goals

- The quality of the offline analysis (issues, positions, frames, wording differences), reviewed before import.

## Preconditions

- A cluster imported through `news_import.import_dataset`, or created directly in tests.

## Postconditions

### Import

- [Integration] The issue table is stored with article ids in place of dataset keys, and the frame with its sentence.\
  Tested by: `tests.test_news_import.NewsDatasetImportTests.test_issue_table_is_stored_with_article_ids`
- [Integration] A cluster revision without an issue table clears the old one.\
  Tested by: `tests.test_news_import.NewsDatasetImportTests.test_revision_without_issue_table_clears_it`
- [Integration] A frame sentence that does not match its offsets, an empty frame or event, an unknown article,
  position, kind or code, an article twice in an issue, a wording phrase that is not words of its article, a wording
  within one article, or a duplicate key is rejected and nothing is stored.\
  Tested by: `tests.test_news_import.NewsDatasetImportTests.test_issue_table_or_frame_that_does_not_match_its_articles_is_rejected`

### Reader API

- [Integration] Each article reads the same table from its own side: its position, the other outlets per position,
  the outlets reporting facts only, the wordings, issues in reading order, the facts with their outlet counts, and
  the other outlets' frames with their positions.\
  Tested by: `tests.test_news_api.EventIssueViewApiTests.test_each_article_sees_the_same_table_from_its_own_side`
- [Integration] A hidden outlet disappears from positions, wordings, fact counts and frames; an article whose frame
  sentence no longer matches answers `frame: null` and is left out of the others' frames.\
  Tested by: `tests.test_news_api.EventIssueViewApiTests.test_hidden_outlet_and_stale_frame_are_left_out`
- [Integration] An article without a frame, or in a cluster without a table, answers `frame: null`.\
  Tested by: `tests.test_news_api.EventIssueViewApiTests.test_article_without_frame_or_cluster_table_answers_null`,
  `tests.test_news_api.NewsApiTests.test_ready_article_can_have_zero_findings`

### Web reader

- [Component] The reader shows the event and this article's frame on top and, below the article, the issues with
  this article's position, the other outlets linked to their articles, the wording differences and the shared facts.\
  Tested by: `web/src/pages/ArticlePage.test.tsx › shows the event and this article's frame on top and the issues across outlets at the bottom`
- [Component] An economy article keeps the economic lens on top and lists the issues under "Issue by issue across
  outlets".\
  Tested by: `web/src/pages/ArticlePage.test.tsx › keeps the economic lens on top for an economy article and lists the issues under another title`
