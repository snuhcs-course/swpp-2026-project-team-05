# Reader comparison points test specification

| Field | Value |
| --- | --- |
| Status | Draft |
| Owner | Reader feature owner |
| Source requirements | [`../features/reader-comparison-summary.md`](../features/reader-comparison-summary.md); [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6 |
| Last reviewed | — (awaiting intent review) |

## Scope

The Android Reader's `비교 포인트` entry section: a reader-only, evidence-first
addition using existing reviewed comparison data. It supplements the existing
comparison sheet and does not change it.

### Non-goals

- The comparison sheet's heading, overview, pages, and gestures (covered by
  `reader-analysis-ui.spec.md`, unchanged).
- Backend, database, stored analysis, Web client.
- Stored overview text quality (for example identifiers such as `A5`).

## Preconditions

- An article is open in the Android Reader with `ArticleAnalysisUi` loaded.
- Fixtures vary: comparisons empty / present; blank target or source marker;
  duplicate source–target lines; scope; all A–G categories; identifier-like
  exact markers (`G7 정상회의`, `A-3 구역`).

## Postconditions

### Section content

- [Unit] No point exists when no finding has both a non-blank own marker and a
  compared article with a non-blank evidence marker; the section then renders no
  text.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.sectionIsHiddenWithoutAValidComparison`
- [Component] With no valid comparison the section is absent from the Reader.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > comparisonPointsSectionIsHiddenWithoutAValidComparison`
- [Unit] A point shows the current publisher with its exact marker and the first
  compared publisher with a non-blank marker, with that exact marker; stored
  markers are not filtered or rewritten.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.pointUsesCurrentPublisherMarkerAndOneRealComparedMarkerExactly`
- [Unit] At most two points, in reading order.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.atMostTwoPointsInReadingOrder`
- [Unit] A repeated finding id, or a point with the same source marker, compared
  publisher, and compared marker, is shown once.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.duplicateComparisonPointIsNotRepeated`
- [Component] On screen, duplicate lines appear once and two distinct points each
  carry `표현 비교 보기`.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > comparisonPointsSectionShowsPublisherMarkersOnceWithoutOverviewOrLabels`

### Not rendered by the section

- [Unit] The section's strings contain no overview, explanation, standalone A–G
  code, or category label.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.sectionTextsNeverContainOverviewExplanationOrCategoryLabels`
- [Component] With the sheet closed, the Reader shows neither the overview, the
  explanation, an A–G code, nor a category label.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > comparisonPointsSectionShowsPublisherMarkersOnceWithoutOverviewOrLabels`

### State transitions

- [Unit] Activating a point yields exactly the selection state the existing
  preview action yields for the same finding; a source-only preview stays a
  preview.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.tappingAPointOpensTheSameDetailStateAsThePreviewAction`
- [Component] Tapping a point opens the existing sheet for that finding with its
  unchanged heading and overview shown once; closing it returns to the Reader
  with the section intact; `이 기사 읽기` routes the compared article.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > comparisonPointOpensTheExistingComparisonDetail`
- [Component] The existing highlight → preview → sheet flow and its reviewed
  assertions are unchanged with the section present.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Unit] Opening a compared article and going Back restores the source article,
  focus, and scroll offset (Reader history unchanged).\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.comparisonReaderNavigationPreservesSourceBackStackAndFocus`
- [Component] Toggling `표현 분석` neither inserts nor removes the section.\
  Tip: add a component test that toggles the switch on an article with a valid
  point and asserts the section count and body position are unchanged.
- [Acceptance] When analysis arrives after the article, content below the
  section may shift by its height (feature §Known limitations).\
  Tip: record an emulator walkthrough of Back from a compared article and note
  whether the restored passage stays in view.

## Undefined behavior

- Which compared article is shown when several qualify beyond "first in stored
  page order".
- Layout of very long markers beyond two lines (truncated with an ellipsis).

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Happy path | one and two points | feature §Section |
| Boundary | no comparisons; blank target or source marker; three qualifying findings | feature §Eligibility, §Selection |
| Duplicates | same lines from two findings; same finding id | feature §Selection |
| Exactness | identifier-like real markers shown as stored | feature §Eligibility |
| Not rendered | overview, explanation, A–G codes and labels | feature §Presentation |
| State interaction | point vs. preview routing; close; open compared article | feature §Interaction |

## Human review checkpoint

- [ ] Product intent and non-goals were reviewed.
- [ ] Assertions come from the specification, not the implementation output.
- [ ] Missing coverage is recorded as `Tip`, not hidden.
