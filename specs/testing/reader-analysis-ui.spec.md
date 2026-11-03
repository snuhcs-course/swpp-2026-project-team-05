# Reader analysis hierarchy test specification

| Field | Value |
| --- | --- |
| Status | Implemented; intent reviewed from HE feedback |
| Owner | Reader feature owner |
| Source requirements | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §§6.1–6.5 |
| Last reviewed | 2026-11-03 |

## Scope

The Reader presents analysis in ordinary reader language rather than the
internal vocabulary of the reviewed dataset. Android progressively discloses a
finding without interrupting reading: a compact preview precedes an expanded
comparison sheet.

### Non-goals

- Changing finding selection, comparison grounding, article grouping, or the
  stored A–G rubric.
- Changing stored comparison grounding or finding semantics.

## Preconditions

- The signed-in user opens an article with stored, displayable findings.
- At least one fixture contains multiple findings and multiple comparison
  publishers.

## Postconditions

### Reader hierarchy

- [Component] The active finding is identified as `Finding n of m`; previous and
  next actions change findings without a row of numeric finding buttons.\
  Tested by: `web/src/pages/ArticlePage.test.tsx > ArticlePage analysis rail > separates finding navigation from named comparison sources`

### Stored analysis contract

- [Contract] The article-analysis response exposes one proposition-level
  `comparison_overview` per finding independently of its publisher pages.\
  Tested by: `tests.test_news_api.NewsApiTests.test_analysis_projects_source_and_grouped_comparison`
- [Data projection] Each reviewed gold finding owns one proposition overview;
  the multi-publisher gold note is not copied into every pair summary.\
  Tested by: `tests.test_gold_dataset.GoldDatasetConversionTests.test_complete_reviewed_set_is_projected`,
  `tests.test_gold_dataset.GoldDatasetConversionTests.test_product_projection_keeps_only_displayable_gold_differences`

### Android progressive disclosure

- [Component] Selecting an in-article marker first shows the compact finding
  preview while the expanded comparison sheet remains closed.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Component] The preview exposes an explicit natural-language comparison action,
  and activating it opens the comparison sheet directly at maximum height.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Component] The comparison sheet exposes a fixed-heading close action and does
  not depend on instructional gesture copy; closing it clears both the detail
  and preview while preserving the Reader.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Component] The shared comparison overview appears once above the publisher
  pager and remains unchanged while pages move. Publisher pages do not repeat
  that prose, and generic reporter attribution is omitted.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Component] A source-only finding shows its complete explanation and labels in
  the preview without exposing a detail action or opening another surface.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > sourceOnlyFindingEndsInPreviewWithoutDetailAction`
- [Component] Every preview exposes all feature labels, caps collapsed analysis
  copy at four lines, and offers inline expand/collapse only when it overflows.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > sourceOnlyFindingEndsInPreviewWithoutDetailAction`
- [State transition] Closing Android detail clears both detail and its preview,
  while closing a preview alone keeps the Reader open. Tapping ordinary article
  text also clears the preview; scrolling the article does not.\
  Tip: add a device-level back-navigation assertion when the instrumentation
  harness owns the activity navigation stack.
- [Regression] The expanded sheet keeps Material whole-sheet dragging disabled.
  Downward drag on fixed header regions dismisses it; article content dismisses
  only when its own scroll is already at the top, and the close animation starts
  after the gesture is released. Dismissal returns to a clean Reader without
  restoring the preview.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`,
  `android/app/src/test/java/com/swpp/team5/frameless/ui/screens/NewsReaderInteractionTest.kt > detailSheetDismissesOnlyAfterDeliberateDownwardDrag`
- [Component] Multiple Android comparison pages expose synchronized horizontal
  swipe and a compact floating previous/page/next navigator with disabled
  boundary actions. The navigator does not reserve article height.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Component] Comparison navigation uses publisher names and exposes the active
  publisher as a tab; it does not add a second `n / m` numeric pager.\
  Tested by: `web/src/pages/ArticlePage.test.tsx > ArticlePage analysis rail > separates finding navigation from named comparison sources`
- [Component] Desktop shows the proposition overview once above publisher tabs
  and does not repeat pair prose after paging.\
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

- Web publisher tabs may scroll horizontally when their combined labels exceed
  the rail width; v1 does not abbreviate publisher names.
- Android publisher pages follow stored order. Swipe and button navigation share
  one pager state; the detail sheet has no middle anchor or height transition.
- Finding order and comparison-source order continue to follow the stored
  display order.

## Human review checkpoint

- [x] The hierarchy and wording were reviewed in response to HE feedback.
- [x] The change does not alter analysis semantics or database relationships.
- [x] Regression assertions are derived from the reviewed UI behavior.
