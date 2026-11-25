# First-run tutorial test specification

| Field | Value |
| --- | --- |
| Status | Implemented with documented gaps |
| Owner | Reader / onboarding feature owner |
| Source requirements | [`../features/first-run-tutorial.md`](../features/first-run-tutorial.md) |
| Last reviewed | 2026-11-21 |

## Scope

Android first-launch onboarding, the first-analyzed-article coach marks, and the
label guide dialog.

### Non-goals

- Visual pixel placement of the spotlight; Web.

## Preconditions

- A fresh installation has no `frameless_tutorial` preferences.
- Coach-mark tests need an article with at least one finding with comparisons,
  one with a finding without comparisons, and one with no findings.

## Postconditions

### Success

- [Unit] A fresh install shows onboarding; finishing it persists and a new
  instance does not show it.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.freshInstallShowsIntroAndFinishingItPersists`
- [Unit] With comparisons, the coach advances TAP_FINDING → ANNOTATION_DOCK →
  COMPARISON and persists only when finished.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.fullReaderCoachWithComparisonsAdvancesThroughThreeStepsAndPersists`
- [Unit] Replaying onboarding re-arms the reader coach for the next article
  with findings.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.replayShowsIntroAgainAndRearmsTheReaderCoach`
- [Component] The replay/help action appears in the all-topics `전체 뉴스` section
  and MY, but not in `언론사`, `정치`, `사회`, or `경제`.\
  Tip: add semantics-driven app-shell coverage for the implemented section and
  MY entries; retained legacy feed-menu tests do not establish this routing.
- [Acceptance] Onboarding page 3 lists A–G with the chip label and a one-line
  summary; a signed-in replay hides `로그인하러 가기`.\
  Tip: add a Compose UI test that pages to the third page.
- [Unit] The label guide lists A–G once, each with a real chip label.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionLabelGuideTest.guideListsEveryRubricCategoryOnceWithTheChipLabel`
- [Acceptance] Tapping the spotlighted phrase selects it in the annotation dock; `다음`
  on step 2 opens the comparison sheet with the 3 / 3 callout.\
  Tip: add a Compose instrumented test in `NewsScreensInstrumentedTest` that passes a `ReaderCoachUi` with a fake step holder.
- [Acceptance] `로그인하러 가기` on onboarding page 2 opens the login screen and
  onboarding does not reappear after closing it.\
  Tip: add an instrumented test around `FrameLESSApp` with fake view models.

### Errors and boundaries

- [Unit] A finding without comparisons ends the coach at step 2.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.findingWithoutComparisonsEndsCoachAtAnnotationDock`
- [Unit] The coach does not start before onboarding is finished.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.readerCoachWaitsUntilIntroIsFinished`
- [Unit] Out-of-order events do not skip steps.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.outOfOrderEventsDoNotSkipSteps`
- [Unit] Closing a comparison sheet when no coach is active does not consume
  the coach.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.dismissingWithoutAnActiveCoachDoesNotMarkItSeen`
- [Unit] The tooltip is placed below a spotlight in the upper half and above
  it otherwise.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.tooltipSitsBelowSpotlightInUpperHalfAndAboveInLowerHalf`
- [Unit] A temporarily stale spotlight coordinate is clamped before it becomes
  Compose padding, so first Reader entry cannot crash during relayout.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.tooltipPaddingNeverPassesNegativeCoordinatesToCompose`
- [Acceptance] An article with no findings, or opened from a comparison sheet,
  does not start the coach.\
  Tip: instrumented test asserting `onEligible` is never called for these readers.
- [Integration] No scroll-synced automatic dock replacement occurs while a coach
  step is active, and tutorial movement emits no annotation haptic; the
  spotlighted marker remains the coached selection route.\
  Tip: cover the coach-active suppression state in the scroll-synced dock
  Reader integration test.

### State transitions

- [Acceptance] System Back on an intro page goes to its previous page; Back on
  the first page completes/dismisses the intro and returns to its origin,
  including MY for a settings replay.\
  Tip: add an `OnboardingScreen`/`FrameLESSApp` navigation test; this behavior
  is not established by the Reader coach's separate Back cases.

- [Unit] Skipping at step 1 is persisted and the coach never restarts.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.skippingAtFirstStepIsRememberedAndCoachDoesNotRestart`
- [Acceptance] System Back during steps 1–2 ends the coach and leaves the
  reader open.\
  Tip: instrumented `Espresso.pressBack()` test.

## Undefined behavior

- Rotation mid-coach may restart the current article's coach from the step
  held in the view model; spotlight position is recomputed on layout.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Happy path | onboarding finish; full 3-step coach | feature §2–3 |
| Boundary | finding without comparisons; no findings | feature §3 |
| Error | events out of order; dismiss without coach | feature §3 |
| State interaction | skip persisted; onboarding before coach | feature §3, §5 |

## Human review checkpoint

- [x] Product intent and non-goals were reviewed (see feature §0).
- [x] Assertions come from the specification, not the implementation output.
- [x] Missing coverage is recorded as `Tip`, not hidden.
- [x] The implemented annotation-dock transition is reflected in the contract.
