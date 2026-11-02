# Reader analysis hierarchy test specification

| Field | Value |
| --- | --- |
| Status | Implemented; intent reviewed from HE feedback |
| Owner | Reader feature owner |
| Source requirements | `docs/news-app-v1-ui-spec.md` §§6.1–6.5 |
| Last reviewed | 2026-11-03 |

## Scope

The desktop Reader distinguishes finding navigation from comparison-source
navigation and presents analysis in ordinary reader language rather than the
internal vocabulary of the reviewed dataset.

### Non-goals

- Changing finding selection, comparison grounding, article grouping, or the
  stored A–G rubric.
- Redesigning the Android bottom-sheet gesture model.

## Preconditions

- The signed-in user opens an article with stored, displayable findings.
- At least one fixture contains multiple findings and multiple comparison
  publishers.

## Postconditions

### Reader hierarchy

- [Component] The active finding is identified as `Finding n of m`; previous and
  next actions change findings without a row of numeric finding buttons.\
  Tested by: `web/src/pages/ArticlePage.test.tsx > ArticlePage analysis rail > separates finding navigation from named comparison sources`
- [Component] Comparison navigation uses publisher names and exposes the active
  publisher as a tab; it does not add a second `n / m` numeric pager.\
  Tested by: `web/src/pages/ArticlePage.test.tsx > ArticlePage analysis rail > separates finding navigation from named comparison sources`

### Reader language

- [Unit] Known A–G categories become reviewed natural-language labels, and an
  unknown internal code is not echoed to the user.\
  Tested by: `web/src/lib/utils.test.ts > findingCategoryLabel > turns internal rubric codes into reader-facing labels`,
  `web/src/lib/utils.test.ts > findingCategoryLabel > does not leak an unknown internal code`
- [Unit] Product dataset explanations and comparison summaries replace local
  source/candidate IDs with publisher names while preserving names such as
  `S&P`.\
  Tested by: `tests.test_gold_dataset.GoldDatasetConversionTests.test_product_copy_does_not_expose_evaluation_article_ids`

## Undefined behavior

- Publisher tabs may scroll horizontally when their combined labels exceed the
  rail width; v1 does not abbreviate publisher names.
- Finding order and comparison-source order continue to follow the stored
  display order.

## Human review checkpoint

- [x] The hierarchy and wording were reviewed in response to HE feedback.
- [x] The change does not alter analysis semantics or database relationships.
- [x] Regression assertions are derived from the reviewed UI behavior.
