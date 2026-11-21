# Scroll-synced analysis annotation (Android)

| Field | Value |
| --- | --- |
| Status | Implemented |
| Owner | Android Reader owner |
| Clients | Android only |
| Last reviewed | 2026-11-21 |
| Supplements | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §§5–6 |
| Test specification | [`../testing/scroll-synced-analysis-peek.spec.md`](../testing/scroll-synced-analysis-peek.spec.md) |

## 1. Purpose

Expression analysis should read like a book annotation, not a card that chases
the sentence or repeatedly covers article text. When an analyzed article has at
least one displayable finding, Android reserves one fixed annotation dock below
the Reader and keeps one finding in it while the user reads.

```text
read article → annotation updates after a pause → existing comparison sheet → target Reader
```

Direct marker taps and the article-level `비교 포인트` section remain alternate
entries into the same finding and comparison flow.

## 2. Scope and non-goals

This is an Android presentation change over existing `FindingUi` data. It does
not change the API, database, analysis pipeline, grounding, finding order, Web,
comparison sheet, target-article navigation, or saving behavior.

## 3. Fixed annotation dock

- The dock is 118 dp high and participates in Reader layout below the article
  viewport. It never overlays article text and its height never changes when the
  active finding changes.
- When analysis is enabled and at least one finding is displayable, the dock is
  always populated. Initial selection is the first title finding, otherwise the
  first body finding.
- It shows natural-language labels, the exact source marker, up to two lines of
  stored explanation, and `다른 보도 N곳 ›` when grounded comparisons exist.
- Source-only findings show no comparison action and never open an empty sheet.
- When analysis is disabled or the completed result contains zero displayable
  findings, no dock is reserved. The existing analysis-state notice explains
  the zero-finding case.

## 4. Scroll-synced selection

- The eligible band is 45–65% of the article viewport and its anchor is 55%.
- Only BODY markers are selected automatically. The eligible marker nearest the
  anchor wins; reading order breaks an equal-distance tie.
- User scrolling keeps the current annotation stable. After scrolling stops and
  the same candidate remains eligible for 500 ms, only dock content changes.
- Long gaps retain the last active annotation rather than flashing an empty
  state. Initial layout, restored scroll, coach movement, and comparison-focus
  movement do not change the annotation automatically.
- While a real user scroll is in progress, a short low-contrast tick at the
  right edge marks the 55% anchor. It is decorative and never crosses the text.

## 5. Interaction and existing flow

- Tapping any analyzed title or body expression selects it immediately in the
  dock without moving the article.
- Tapping a comparison-backed dock opens the existing `FindingDetailSheet` for
  the active finding. Closing the sheet returns to the same Reader and leaves
  the annotation selected.
- Existing publisher paging, `이 기사 읽기`, original links, comparison Reader
  back-stack, article saving, and the top `비교 포인트` entry are unchanged.
- The dock label-guide button continues to open the existing A–G explanation.
- Android Back leaves the Reader when only the persistent dock is visible; the
  dock is not treated as a dismissible modal.

## 6. Accessibility and layout

- The dock's comparison action has a natural-language click label; raw A–G
  codes and evaluation IDs are never exposed.
- Marker meaning does not rely on color: the active source marker retains
  underline and selected styling.
- Large text truncates within the fixed dock instead of changing its height or
  covering the article. Complete comparison context remains available in the
  existing detail sheet.

## 7. Acceptance criteria

- An analyzed Reader with findings always has exactly one 118 dp annotation
  dock; scrolling and finding changes do not resize it.
- The annotation remains stable during scrolling and changes only after a
  qualifying 500 ms pause or a direct marker tap.
- No floating analysis card follows or obscures article text.
- One dock tap reaches the existing comparison detail and target-article flow.
- Zero-finding, analysis-off, direct marker, coach, comparison-point, and Back
  behavior remain explicit and regression-tested.
