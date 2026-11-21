# Scroll-synced analysis peek (Android)

| Field | Value |
| --- | --- |
| Status | Implemented |
| Owner | Android Reader owner |
| Clients | Android only |
| Last reviewed | 2026-11-21 |
| Supplements | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §§5–6 |
| Test specification | [`../testing/scroll-synced-analysis-peek.spec.md`](../testing/scroll-synced-analysis-peek.spec.md) |

## 1. Purpose

Readers should discover stored expression analysis while reading instead of
repeatedly tapping highlighted phrases to learn whether they contain useful
comparisons. Android therefore promotes the body finding nearest a stable
reading position into a small, non-blocking peek after the reader pauses.

The flow is deliberately short:

```text
read and pause → compact peek → existing comparison sheet → target Reader
```

The peek improves discovery; it does not replace direct marker taps, the
article-level `비교 포인트` section, or the existing comparison sheet.

## 2. Scope and non-goals

This is an Android Reader presentation and interaction change. It uses the
existing `FindingUi`, comparison evidence, and Reader navigation state.

It does not:

- change the API, database, analysis pipeline, finding order, or grounding;
- generate or paraphrase text on the device;
- add automatic peeks to Web, whose persistent analysis rail already exposes
  the selected finding;
- automatically open title findings;
- remove or alter the article-level `비교 포인트` section;
- move the article to make a finding eligible.

## 3. Automatic selection

### 3.1 Focus zone

- The eligible vertical band is 45–65% of the Reader content viewport, measured
  below fixed app chrome. Its anchor is 55%.
- A BODY finding is eligible when the vertical centre of its rendered marker
  lies inside that band.
- If several markers are eligible, select the marker centre nearest the anchor.
  A distance tie resolves to the earlier finding in reading order.
- At most one finding is active.

### 3.2 Trigger

An automatic peek appears only when all of the following are true:

- expression analysis is enabled and the finding is displayable;
- the reader has performed a vertical drag in this article since it opened or
  regained focus;
- scrolling has stopped and the same candidate remains eligible for 500 ms;
- no coach mark, label guide, comparison sheet, or other modal surface is open.

Initial layout, restored scroll position, coach-controlled movement, and the
one-time jump to comparison evidence never trigger a peek. A title finding and
an off-screen finding remain available through direct taps.

## 4. Compact peek

- The peek is a screen-fixed overlay immediately above the focus zone. It does
  not participate in article layout, reserve bottom padding, or move the
  article when it appears or changes.
- After the reader first scrolls, a short low-contrast accent tick at the right
  edge marks the 55% anchor while no peek or modal is open. It is decorative,
  has no label, and never draws a line across article text.
- The active marker receives the existing selected styling so the relationship
  between sentence and peek remains visible without relying on colour alone.
- It shows ordinary Reader language, never raw A–G codes or evaluation IDs.
- For a comparison-backed finding, it shows the current marker and up to two
  publisher-labelled comparison markers exactly as stored. Additional distinct
  publishers are represented as `외 N곳`.
- For a source-only finding, it shows the current marker and a compact portion
  of the stored explanation instead of an empty comparison row.
- The current marker and each comparison expression stay on one line. A
  source-only explanation uses at most two lines until expanded; stored analysis
  is not silently discarded.
- Directly tapping any title or body marker shows its peek immediately, without
  waiting for the scroll trigger.

## 5. Interaction and lifecycle

- Starting another deliberate article scroll hides the automatic peek before
  selecting a new candidate.
- Tapping ordinary article text, the close action, Android Back, or disabling
  expression analysis clears the peek.
- A manually dismissed finding is suppressed until its marker leaves the focus
  zone or a different finding becomes active. Minor scroll settling must not
  reopen it in place.
- Tapping a comparison-backed peek opens the existing full comparison sheet for
  that finding directly. No new intermediate comparison screen is added.
- Tapping a source-only peek expands its stored explanation in the same overlay;
  it never opens an empty comparison sheet.
- Dismissing the comparison sheet clears the peek. Returning from a comparison
  target restores the source article's scroll position but requires a new user
  scroll before another automatic peek may appear.
- Existing comparison paging, target-article navigation, saving, original links,
  and source Reader back-stack behavior remain unchanged.

## 6. Accessibility and motion

- The peek is announced as `{marker}, 표현 분석` followed by its visible
  summary and whether a comparison is available.
- Its close and expand actions meet the 48 × 48 dp touch-target rule.
- Large text may increase the card height but must not cover the active marker;
  the compact card remains bounded and the expanded source-only explanation is
  scrollable only when required by system font scaling.
- Appearance and dismissal use a short fade/vertical transition. The card does
  not chase a marker while the article is moving.

## 7. Implementation boundary

The Reader owns a small presentation state: candidate finding, dwell state,
active peek, dismissal suppression, and whether a source-only explanation is
expanded. Candidate selection should be a pure policy function over measured
marker centres and viewport bounds so timing and boundary behavior can be unit
tested independently of Compose layout.

The existing `FindingDetailSheet` remains the only comparison-detail surface.
The existing direct marker annotation, comparison-point entry, and navigation
callbacks are reused.

## 8. Acceptance criteria

- A normal reading pause can reveal a useful comparison without a marker tap.
- Opening an article, restoring its position, or returning from another article
  never produces a surprise peek before the reader scrolls.
- Fast scrolling does not produce a trail of cards, and only one finding is
  presented at a time.
- The focus position is discoverable without adding persistent instructional
  copy or covering article text.
- Showing, replacing, expanding, or dismissing the peek never changes article
  text, layout, or scroll position.
- A comparison-backed finding reaches the existing detail sheet with one tap
  from the peek; a source-only finding exposes its complete explanation without
  an empty detail surface.
- Direct marker taps and the article-level `비교 포인트` entry continue to work.

