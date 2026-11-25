# Reader paired expression panel test specification

| Field | Value |
| --- | --- |
| Status | Implemented with documented gaps |
| Owner | Android UI owner |
| Source requirements | [`../features/reader-expression-pair.md`](../features/reader-expression-pair.md) |
| Last reviewed | 2026-11-25 |

## Scope

The paired expression panel on comparison sheet pages and the sheet frame
restyle.

### Non-goals

- Existing sheet behaviour already covered by
  `NewsScreensInstrumentedTest.readerAnnotationOpensFullComparisonDetail`
  (heading, close, overview once, pager, article action).
- Pixel-level appearance; inspect on a physical 360dp phone.

## Preconditions

- A finding with a stored marker and sentence, a source publisher name, and one
  or more comparison pages whose evidence carries stored markers and sentences.

## Postconditions

### Success

- [Unit] The pair uses the finding's marker and sentence and the first BODY
  evidence of the page, with stored publisher names, verbatim.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairTest.pairsTheFindingPhraseWithTheFirstBodyEvidenceVerbatim`
- [Unit] Without BODY evidence, the first evidence of any scope is used.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairTest.fallsBackToTitleEvidenceWhenThereIsNoBodyEvidence`
- [Acceptance] On opening, both phrases are shown together, each with its
  `현재 기사` / `다른 보도` pill and publisher; paging changes only the compared
  side; evidence summaries are not shown; `이 기사 읽기` still opens the page's
  article after closing the sheet.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairInstrumentedTest.bothPhrasesAppearTogetherWithOutletsAndThePagerSwapsOnlyTheComparedSide`
- [Acceptance] Each side is one accessibility unit; the `표현 비교` heading and
  the connector are shown with no extra content description.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairInstrumentedTest.eachSideReadsAsOneUnitAndTheConnectorAddsNoExtraSpokenText`
- [Acceptance] Existing sheet behaviour (heading, close, overview once, pager,
  article action) is unchanged.\
  Tested by: `com.swpp.team5.frameless.ui.screens.NewsScreensInstrumentedTest.readerAnnotationOpensFullComparisonDetail`
- [Acceptance] No full compared body or replacement excerpt area is rendered
  below the article controls. Paired expressions and their context remain, the
  original link opens the same URL, and `이 기사 읽기` closes the sheet and
  passes the unchanged article, including its full body and evidence.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairInstrumentedTest.comparisonSheetOmitsFullBodyAndKeepsOriginalArticleActions`
- [Acceptance] The sheet uses its content height rather than reserving an empty
  full-screen article-reading region; overflowing comparison content remains
  scrollable and navigation does not cover its final controls.\
  Tip: inspect the single- and multiple-publisher cases on the physical phone,
  including enlarged text.

### Errors and boundaries

- [Unit] A missing phrase on either side yields no panel rather than invented
  text.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairTest.returnsNullInsteadOfInventingAMissingSide`
- [Unit] Context identical to the phrase is dropped.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairTest.contextThatOnlyRepeatsThePhraseIsDropped`
- [Unit] The context window keeps the phrase visible, prefixes `…` only when it
  cuts, and never changes characters.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairTest.contextWindowKeepsThePhraseVisibleWithoutAlteringText`
- [Acceptance] Long quoted phrases stop at two lines with ellipsis on both sides.\
  Tested by: `com.swpp.team5.frameless.ui.screens.ExpressionPairInstrumentedTest.longPhrasesStopAtTwoLines`
- [Acceptance] A page whose evidence has no marker shows no panel and keeps the
  rest of the page.\
  Tip: render the sheet with one page whose evidence marker is blank and assert
  `표현 비교` is absent while `이 기사 읽기` is present.
- [Manual] At 360dp width and 1.3× font scale, both sides stay readable with
  equal weight, enough of the compared article remains visible below the band, and dragging the panel down dismisses the sheet like the header.\
  Tip: add a physical-device pass for enlarged text and long publisher names.

- [Acceptance] Each context preview is a single line with end ellipsis.\
  Tip: render a long sentence and assert the context Text's layout has
  `lineCount == 1` and visual overflow.

### State transitions

- [Acceptance] Paging back restores the first compared phrase.\
  Tip: page next then previous and assert the first page's phrase is shown again.

## Undefined behavior

- Pairing when a page has several BODY evidence rows: only the first is shown in
  the panel; `이 기사 읽기` remains available for full-text reading.
