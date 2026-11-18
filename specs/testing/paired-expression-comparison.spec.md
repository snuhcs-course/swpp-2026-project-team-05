# Paired expression comparison card test specification

| Field | Value |
| --- | --- |
| Status | Draft |
| Owner | Reader feature owner |
| Source requirements | [`../features/paired-expression-comparison.md`](../features/paired-expression-comparison.md); [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6.3 |
| Last reviewed | — (awaiting intent review) |

## Scope

The `표현 비교` card inside the existing Android comparison sheet. It
supplements, rather than replaces, the existing heading, overview, and pager
requirements, which remain covered by `reader-analysis-ui.spec.md`.

### Non-goals

- Backend, database, stored analysis, Web client.
- Re-verifying existing sheet behavior beyond confirming it is unchanged.

## Preconditions

- A comparison-backed finding is open in the Reader's comparison sheet.
- Fixtures include two compared pages, a page whose evidence markers are blank,
  a blank source marker, and identifier-like real evidence.

## Postconditions

### Data selection

- [Unit] The source side uses the current publisher name and the exact stored
  marker and sentence; the target side uses the current page's publisher and its
  first evidence with a non-blank marker, exactly as stored.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionComparisonTest.sourceAndTargetUseExactStoredData`
- [Unit] Selecting a different page changes the target side and leaves the
  source side unchanged.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionComparisonTest.targetFollowsTheSelectedPageWhileSourceStaysFixed`
- [Unit] A blank source publisher, a blank source marker, a page without a
  non-blank evidence marker, a page without evidence, or a missing page yields
  no card.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionComparisonTest.blankSourceOrTargetHidesTheCard`
- [Unit] Identifier-like real evidence (`G7 정상회의`, `A-3 구역`) is kept
  exactly.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionComparisonTest.identifierLikeRealEvidenceStaysVisible`

### Sheet presentation

- [Component] With the card present, the sheet heading `같은 내용을 다른
  기사에서는` is unchanged and the overview appears exactly once, before and after
  paging.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/ExpressionComparisonInstrumentedTest.kt > cardShowsBothSidesBelowTheUnchangedHeadingAndOverviewAndFollowsThePager`
- [Component] The card shows `표현 비교`, `현재 기사` with the source publisher and
  quoted marker, the connector `같은 내용, 다른 표현`, and `다른 보도` with
  `현재 비교 기사`, the current page's publisher, and its quoted marker.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/ExpressionComparisonInstrumentedTest.kt > cardShowsBothSidesBelowTheUnchangedHeadingAndOverviewAndFollowsThePager`
- [Component] Moving the pager to the next page replaces the target side with
  that page's publisher and marker while the source side stays.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/ExpressionComparisonInstrumentedTest.kt > cardShowsBothSidesBelowTheUnchangedHeadingAndOverviewAndFollowsThePager`
- [Component] On a page without usable evidence the card is absent while the
  heading and overview remain; it appears on a following page that has usable
  evidence.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/ExpressionComparisonInstrumentedTest.kt > cardIsHiddenOnAPageWithoutUsableEvidence`
- [Component] Existing reviewed sheet behavior (preview → sheet, overview once,
  paging, close, `이 기사 읽기`) still holds with the card present.\
  Tested by: `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerPreviewsSelectedFindingBeforeOpeningFullDetail`
- [Component] The card shows no A–G code or category label.\
  Tip: add an assertion to a new component test with a finding carrying all
  categories that no `categoryLabel(code)` text or bare code appears in the sheet.
- [Component] A quoted marker longer than two lines is shown in at most two
  lines with an ellipsis.\
  Tip: add a component test with a very long marker asserting the quoted text's
  layout reports at most two lines and visual overflow.
- [Acceptance] On a 360×800 device the card, overview, and the start of the
  compared article are all readable.\
  Tip: record an emulator screenshot review with a long overview and long
  markers.

## Undefined behavior

- Quoted markers and context sentences longer than two lines are truncated with
  an ellipsis; in a context sentence the marker may fall outside the visible part.
- Which evidence row is used when a page has several non-blank markers beyond
  "first in stored order".

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Happy path | two pages with usable evidence | feature §Content |
| Boundary | blank source marker; blank target markers; no evidence; missing page | feature §Visibility |
| Exactness | identifier-like real evidence | feature §Content, §Non-goals |
| State interaction | paging forward changes target only | feature §Content |
| Regression | heading and overview unchanged | feature §Summary |

## Human review checkpoint

- [ ] Product intent and non-goals were reviewed.
- [ ] Assertions come from the specification, not the implementation output.
- [ ] Missing coverage is recorded as `Tip`, not hidden.
