# Scroll-synced analysis peek test specification

| Field | Value |
| --- | --- |
| Status | Implemented with documented gaps |
| Owner | Android Reader owner |
| Source requirements | [`../features/scroll-synced-analysis-peek.md`](../features/scroll-synced-analysis-peek.md) |
| Last reviewed | 2026-11-21 |

## Scope

Automatic selection, timing, presentation lifecycle, and navigation behavior of
the Android Reader's scroll-synced analysis peek.

### Non-goals

- Pixel-perfect animation, Web behavior, analysis quality, API/database
  behavior, or comparison-sheet internals already covered by other specs.

## Preconditions

- Test data includes BODY findings above, inside, and below the focus zone; a
  TITLE finding; two equidistant BODY findings; a source-only finding; and a
  finding with at least three publisher-diverse comparisons.
- Expression analysis starts enabled unless a case states otherwise.
- Timing tests use a controllable coroutine clock rather than wall time.

## Postconditions

### Candidate policy

- [Unit] Only BODY markers whose centres are within 45–65% of the content
  viewport are automatic candidates; title findings remain manual. The closest
  marker to 55% wins, with reading order breaking equal-distance ties.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.selectsNearestEligibleBodyFindingIncludingBandBoundaries`,
  `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.readingOrderBreaksAnEqualDistanceTie`
- [Unit] Initial layout, restored position, comparison-focus movement, and coach
  movement do not arm automatic selection; a user vertical drag does.\
  Tip: add `ScrollSyncedPeekPolicyTest.requiresUserScrollBeforeAutomaticSelection`.

### Timing and lifecycle

- [Unit] A stable candidate is absent at 499 ms and active at 500 ms after
  scrolling stops. A candidate change or renewed scroll cancels the pending
  activation.\
  Tip: add coroutine-clock tests in `ScrollSyncedPeekPolicyTest`.
- [Component] Initial layout does not show a peek; an eligible finding appears
  only after a real user scroll settles.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerShowsAnalysisPeekOnlyAfterUserScrollSettles`
- [Unit] Scrolling, ordinary-text tap, close, Back, or disabling analysis clears
  the active peek. A dismissed finding cannot reactivate until it exits the
  focus zone or another finding becomes active.\
  Tip: add `ScrollSyncedPeekPolicyTest.dismissalRequiresFocusExitBeforeReentry`.
- [Unit] Direct marker tap activates title or body findings immediately and does
  not depend on dwell state.\
  Tip: add `ScrollSyncedPeekPolicyTest.manualSelectionBypassesDwell`.
- [Integration] Coach marks, label guide, and comparison sheet suppress automatic
  activation; after returning from a target article, the restored source Reader
  requires a new user scroll.\
  Tip: add focused Reader state tests in `NewsReaderInteractionTest`.

### Presentation and navigation

- [Unit] A comparison-backed compact peek renders exact stored markers for at
  most two publisher-diverse comparisons and reports remaining publishers as
  `외 N곳`; a source-only peek renders no comparison count or empty row.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.peekContentKeepsTwoPublisherDiverseExactMarkersAndCountsTheRest`,
  `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.sourceOnlyPeekHasNoComparisonRows`
- [Unit] The peek prefers the space above its marker, falls below near the top,
  and remains inside the Reader viewport.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ScrollSyncedAnalysisPeekTest.cardStaysInsideViewportAndUsesBelowFallbackNearTheTop`
- [Component] Showing or dismissing a peek leaves the Reader scroll value and
  article layout position unchanged, and no more than one peek exists.\
  Tip: add a Compose instrumented assertion in `NewsScreensInstrumentedTest`.
- [Component] Tapping a comparison-backed peek opens the existing comparison
  sheet for that finding; tapping a source-only peek expands its explanation in
  place and never opens a sheet.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`,
  `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > sourceOnlyFindingEndsInPreviewWithoutDetailAction`
- [Regression] Direct marker taps and the article-level `비교 포인트` section
  continue to open the intended finding.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`,
  `com.swpp.team5.frameless.ui.screens.ReaderComparisonSummaryTest.tappingAPointOpensTheSameDetailStateAsThePreviewAction`
- [Accessibility] Peek semantics expose the marker, `표현 분석`, the visible
  summary, comparison availability, and labelled close/expand actions.\
  Tip: assert the merged semantics tree in `NewsScreensInstrumentedTest`.

## Undefined behavior

- Rotation clears an active automatic peek and requires another user scroll.
- When a marker cannot be measured because layout is incomplete, it is not a
  candidate for that frame; the article remains readable.
- The exact animation curve is visual polish and not a behavioral oracle.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Eligibility | inside/outside/boundary band; title; tie | feature §3.1 |
| Timing | 499/500 ms; candidate changes; scrolling resumes | feature §3.2 |
| State interaction | analysis off; coach; sheet; restored Reader | feature §§3.2, 5 |
| Data shape | source-only; one/two/three comparison publishers | feature §4 |
| Navigation | direct tap; comparison point; target Reader return | feature §5 |
| Accessibility | large text; semantics; touch targets | feature §6 |

## Human review checkpoint

- [x] Product intent, Android-only scope, and preservation of `비교 포인트` were
  confirmed before drafting.
- [x] Assertions come from the reviewed interaction contract, not current output.
- [x] Missing executable coverage is recorded as `Tip`.
- [x] Implemented assertions name exact executable tests; remaining gaps stay
  explicit as `Tip` rather than being presented as measured coverage.

