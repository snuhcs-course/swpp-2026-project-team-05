# First-run tutorial test specification

| Field | Value |
| --- | --- |
| Status | Draft |
| Owner | Reader / onboarding feature owner |
| Source requirements | [`../features/first-run-tutorial.md`](../features/first-run-tutorial.md) |
| Last reviewed | 2026-11-16 |

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
- [Unit] With comparisons, the coach advances TAP_FINDING → PREVIEW_CARD →
  COMPARISON and persists only when finished.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.fullReaderCoachWithComparisonsAdvancesThroughThreeStepsAndPersists`
- [Unit] The label guide lists A–G once, each with a real chip label.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionLabelGuideTest.guideListsEveryRubricCategoryOnceWithTheChipLabel`
- [Acceptance] Tapping the spotlighted phrase opens its preview card; `다음`
  on step 2 opens the comparison sheet with the 3 / 3 callout.\
  Tip: add a Compose instrumented test in `NewsScreensInstrumentedTest` that passes a `ReaderCoachUi` with a fake step holder.
- [Acceptance] `로그인하러 가기` on onboarding page 2 opens the login screen and
  onboarding does not reappear after closing it.\
  Tip: add an instrumented test around `FrameLESSApp` with fake view models.

### Errors and boundaries

- [Unit] A finding without comparisons ends the coach at step 2.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.TutorialViewModelTest.findingWithoutComparisonsEndsCoachAtPreviewCard`
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
- [Acceptance] An article with no findings, or opened from a comparison sheet,
  does not start the coach.\
  Tip: instrumented test asserting `onEligible` is never called for these readers.

### State transitions

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

- [ ] Product intent and non-goals were reviewed (see feature §0).
- [ ] Assertions come from the specification, not the implementation output.
- [ ] Missing coverage is recorded as `Tip`, not hidden.
- [ ] A contract or acceptance change received explicit team confirmation.
