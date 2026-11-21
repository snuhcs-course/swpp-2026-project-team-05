# Scroll-synced analysis annotation test specification

| Field | Value |
| --- | --- |
| Status | Implemented with documented gaps |
| Owner | Android Reader owner |
| Source requirements | [`../features/scroll-synced-analysis-peek.md`](../features/scroll-synced-analysis-peek.md) |
| Last reviewed | 2026-11-21 |

## Scope

Candidate selection, fixed annotation-dock layout, immediate scroll updates, direct
selection, and routing into the existing Android comparison flow.

### Non-goals

- Web behavior, analysis quality, API/database behavior, and comparison-sheet
  internals covered by their own specifications.

## Postconditions

### Candidate policy

- [Unit] Only BODY marker centres inside the inclusive 45–65% band are automatic
  candidates. The marker nearest 55% wins and reading order breaks ties.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.selectsNearestEligibleBodyFindingIncludingBandBoundaries`,
  `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.readingOrderBreaksAnEqualDistanceTie`
- [Unit] Initial/restored layout and programmatic movement do not arm automatic
  selection; a real user drag does.\
  Tip: add a pure Reader-state test if scroll arming moves out of Compose.
- [Timing] An eligible candidate replaces the dock finding in the same settled
  Compose cycle; no dwell timer or delayed replacement remains.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerKeepsFixedAnnotationDockAndUpdatesItDuringUserScroll`

### Fixed presentation

- [Component] An analyzed article starts with one populated dock, keeps its
  height after deliberate scrolling, and eventually changes to the new nearest
  finding without creating a second dock.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerKeepsFixedAnnotationDockAndUpdatesItDuringUserScroll`
- [Unit] Comparison count is publisher-diverse; source-only data produces no
  comparison row or count.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.peekContentKeepsTwoPublisherDiverseExactMarkersAndCountsTheRest`,
  `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.sourceOnlyPeekHasNoComparisonRows`
- [Component] A source-only finding remains in the dock without exposing or
  opening an empty comparison surface.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > sourceOnlyFindingEndsInPreviewWithoutDetailAction`

### Navigation and regression

- [Component] The dock opens the existing full comparison sheet; closing it
  returns to the same populated dock, and `이 기사 읽기` still routes to the
  grounded target article.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Regression] Direct marker taps and the article-level `비교 포인트` route to
  the same finding/detail state. A manual marker remains selected until a later
  user scroll makes another BODY marker eligible.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`,
  `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.tappingAPointOpensTheSameDetailStateAsThePreviewAction`
- [Accessibility] The dock comparison action, label guide, active marker, and
  fixed-height behavior remain usable with system font scaling.\
  Tip: add large-font and merged-semantics device assertions.

## Test design

| Risk | Representative cases | Oracle |
| --- | --- | --- |
| Eligibility | inside/outside/boundary band; tie | feature §4 |
| Stability | immediate change during scroll; long gap | feature §4 |
| Data shape | source-only; one/two/three publishers | feature §3 |
| Layout | initial/updated dock has identical height | feature §3 |
| Navigation | dock; comparison point; target Reader | feature §5 |

## Human review checkpoint

- [x] Persistent content, fixed dock size, Android-only scope, and preservation
  of the existing comparison flow were confirmed before implementation.
- [x] Implemented assertions name executable tests; gaps remain explicit.
