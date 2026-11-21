# Reader analysis hierarchy test specification

| Field | Value |
| --- | --- |
| Status | Implemented; intent reviewed from HE feedback |
| Owner | Reader feature owner |
| Source requirements | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §§6.1–6.5 |
| Last reviewed | 2026-11-16 |

## Scope

The Reader presents analysis in ordinary reader language rather than the
internal vocabulary of the reviewed dataset. Android progressively discloses a
finding without interrupting reading: a fixed scroll-synced annotation dock
precedes an expanded comparison sheet, while direct marker taps remain available.

### Non-goals

- Changing finding selection, comparison grounding, article grouping, or the
  stored A–G rubric.
- Changing stored comparison grounding or finding semantics.

## Preconditions

- A guest or signed-in user opens an article with stored, displayable findings.
- At least one fixture contains multiple findings and multiple comparison
  publishers.

## Postconditions

### Reader hierarchy

- [Component] The active finding is identified as `Finding n of m`; previous and
  next actions change findings without a row of numeric finding buttons.\
  Tested by: `web/src/pages/ArticlePage.test.tsx > ArticlePage analysis rail > separates finding navigation from named comparison sources`
- [Component] A comparison publisher page opens its first-class target article
  in the ordinary Reader with the exact evidence marker as transient context;
  back returns to the source Reader at its preserved scroll position and the
  original external link remains available.\
  Tested by: `web/src/pages/ArticlePage.test.tsx > ArticlePage comparison navigation > opens a comparison target in Reader context and returns to its source`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.comparisonReaderNavigationPreservesSourceBackStackAndFocus`
- [Component] The internal full-article action is visually primary and named as
  a Reader destination, while the publisher original remains secondary. The
  target Reader keeps one transient context notice inside article scrolling;
  an article with no standalone findings describes that state without treating
  the comparison marker as a finding or an error.\
  Tested by: `web/src/pages/ArticlePage.test.tsx > ArticlePage comparison navigation > opens a comparison target in Reader context and returns to its source`,
  `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > comparisonTargetExplainsTransientContextWithoutClaimingAStandaloneFinding`,
  `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > comparisonTargetWaitsForLayoutBeforeScrollingToFocusedPassage`,
  `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`

### Stored analysis contract

- [Contract] The article-analysis response exposes one proposition-level
  `comparison_overview` per finding independently of its publisher pages.\
  Tested by: `tests.test_news_api.NewsApiTests.test_analysis_projects_source_and_grouped_comparison`
- [Data projection] Each reviewed gold finding owns one proposition overview;
  the multi-publisher gold note is not copied into every pair summary.\
  Tested by: `tests.test_gold_dataset.GoldDatasetConversionTests.test_complete_reviewed_set_is_projected`,
  `tests.test_gold_dataset.GoldDatasetConversionTests.test_product_projection_keeps_only_displayable_gold_differences`

### Android progressive disclosure

- [Component] Turning verified marker styling on or off does not insert ordinary
  instructional content or move the article text; status notices remain reserved
  for loading, partial, unavailable, failed, and empty-result states.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > analysisToggleAddsMarkersWithoutMovingArticleContent`
- [Component] An analyzed article keeps one fixed-height annotation dock;
  selecting an in-article marker updates it while the comparison sheet remains
  closed.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`
- [Component] The dock exposes an explicit natural-language comparison action,
  and activating it opens the comparison sheet directly at maximum height.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`
- [Component] The comparison sheet exposes a fixed-heading close action and does
  not depend on instructional gesture copy; closing it returns to the same
  persistent dock while preserving the Reader.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`
- [Component] The shared comparison overview appears once above the publisher
  pager and remains unchanged while pages move. Publisher pages do not repeat
  that prose, and generic reporter attribution is omitted.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`
- [Component] A source-only finding shows its explanation and labels in the dock
  and exposes no comparison-detail action or empty surface.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > sourceOnlyFindingStaysInDockWithoutDetailAction`
- [Component] Every dock exposes natural-language feature labels. Its explanation
  is capped at two lines and the dock remains 118 dp tall for every finding.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > sourceOnlyFindingStaysInDockWithoutDetailAction`
- [State transition] A direct marker tap immediately selects that finding and
  keeps it through recomposition and detail close. A later user scroll may
  replace it immediately when a different finding enters the eligible band.
  Back with only the dock visible leaves the Reader.\
  Tip: add a device-level back-navigation assertion when the instrumentation
  harness owns the activity navigation stack.
- [Regression] The expanded sheet keeps Material whole-sheet dragging disabled.
  Downward drag on fixed header regions dismisses it; article content dismisses
  only when its own scroll is already at the top, and the close animation starts
  after the gesture is released. Dismissal returns to a clean Reader without
  restoring a transient overlay; the persistent dock remains selected.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`,
  `android/app/src/test/java/com/swpp/team5/frameless/ui/screens/NewsReaderInteractionTest.kt > detailSheetDismissesOnlyAfterDeliberateDownwardDrag`
- [Component] Multiple Android comparison pages expose synchronized horizontal
  swipe and a compact floating previous/page/next navigator with disabled
  boundary actions. The navigator does not reserve article height.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`
- [Component] Android comparison detail exposes an internal article action;
  selecting it sends the target article and its first exact evidence range to
  Reader navigation rather than opening the publisher URL.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerAnnotationOpensFullComparisonDetail`
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
- Navigation focus lasts only for the in-app transition. Reloading or reopening
  the same article normally does not create a comparison-context marker.
- Finding order and comparison-source order continue to follow the stored
  display order.

## Human review checkpoint

- [x] The hierarchy and wording were reviewed in response to HE feedback.
- [x] The change does not alter analysis semantics or database relationships.
- [x] Regression assertions are derived from the reviewed UI behavior.
