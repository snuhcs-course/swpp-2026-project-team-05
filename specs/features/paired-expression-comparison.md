# Paired expression comparison card (Android comparison sheet)

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Reader feature owner |
| Clients | Android only |
| Iteration | 4 |
| Supplements | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6.3; [`../product/news-app-v1.md`](../product/news-app-v1.md) §4 comparison section |
| Test specification | [`../testing/paired-expression-comparison.spec.md`](../testing/paired-expression-comparison.spec.md) |

## Summary

In the existing Android comparison sheet, a `표현 비교` card puts the source
article's exact stored expression and the currently paged comparison article's
exact stored expression together, before the reader scrolls into the compared
article.

The card **supplements** the existing sheet. It does not amend or replace any
reviewed requirement:

- the sheet heading stays `같은 내용을 다른 기사에서는`;
- the proposition-level `comparisonOverview` stays visible once above the pager,
  unchanged;
- the pager, page order, compared article title/body/highlights,
  `이 기사 읽기`, `원문 보기`, close action, and gestures are unchanged.

It is a presentation of existing reviewed data only. It does not decide which
outlet is correct, biased, stronger, or better. No backend API, database model,
stored analysis, Reader history, scrolling, onboarding, or analysis-toggle
behavior changes.

## Placement

Directly below the overview paragraph and above the comparison pager, inside the
sheet's fixed header area.

## Content

```text
표현 비교
┌──────────────────────────────────────┐
│ [현재 기사] 프레시안                  │
│ “중심에 섰던”                         │
│ …파업 유도 사건’의 중심에 섰던 진형구… │
│ ───── 같은 내용, 다른 표현 ─────      │
│ (다른 보도) 뉴스1        현재 비교 기사 │
│ “연루되기도 했다”                     │
│ …파업 유도 사건’에 연루되기도 했다.    │
└──────────────────────────────────────┘
```

- **Source block** (fixed while paging): filled label `현재 기사`, the current
  article's publisher name, the exact `finding.markerText` in quotation marks on
  a light lilac highlight, and the exact `finding.sentenceText` as context, at
  most two lines.
- **Connector**: horizontal dividers with the centered text
  `같은 내용, 다른 표현`.
- **Target block** (follows the pager): outlined label `다른 보도`, the current
  page's publisher name, the note `현재 비교 기사`, the page's first evidence
  marker that is non-blank in quotation marks with the same treatment, and that
  evidence's exact `sentenceText` as context, at most two lines.
- Vertical layout only; no two-column comparison.
- Quoted markers are visually limited to two lines with an ellipsis, so a very
  long stored expression cannot take over the sheet's fixed header.
- Where a marker occurs in its sentence, it is underlined in the context line.
  The stored text itself is never filtered, rewritten, or shortened (beyond the
  two-line visual limits with an ellipsis).
- No A–G codes, category labels, scores, judgments, or rankings.

## Visibility

The card is not rendered for the current page when:

- the current article's publisher name is blank;
- `finding.markerText` is blank; or
- the current page has no evidence with a non-blank marker.

It may therefore appear on one page and not on another. No text is invented.

## Data

`FindingDetailSheet` receives the current article's publisher name from the
existing Reader call site. A pure helper,
`expressionComparisonFor(sourcePublisher, finding, pageIndex)`, selects the data
from the current pager page.

## Non-goals

- Changing or replacing the overview, heading, pager, or pages.
- Filtering identifier-like text in stored evidence (`G7 정상회의`, `A-3 구역`
  are shown as stored). Quality issues in stored overview text are a data
  matter outside this change.
- Web client parity.
