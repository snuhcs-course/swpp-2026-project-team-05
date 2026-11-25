# Reader paired expression panel (Android)

| Field | Value |
| --- | --- |
| Status | Implemented; remaining verification gaps are recorded in the test specification |
| Owner | Android UI owner |
| Related behavior | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6.3–6.4, [`home-feed-refresh.md`](home-feed-refresh.md) |
| Last reviewed | 2026-11-25 |

## 1. Purpose

Opening a comparison from a highlighted expression is FrameLESS's signature
moment. Each publisher page of the comparison sheet now opens with the two
expressions side by side, so the reader sees at once that the same content was
written in different words, before reading the compared article itself.

This is a presentation change on Android only. It uses stored finding and
comparison evidence already delivered to the Reader; no API, database,
pipeline, or mapper change.

## 2. Sheet frame

- Surface: theme-aware `Fl.Card`, matching the Reader in light and dark mode.
- The fixed heading keeps the exact text `같은 내용을 다른 기사에서는` (theme ink,
  serif) and the `비교 상세 닫기` close action.
- The stored comparison overview is still shown once, unchanged, between the
  heading and the pager, as plain text beside a thin theme-colored rule instead of a
  grey box. It is not repeated inside pages.
- The floating pager pill keeps `‹  n / N  ›`, its `이전 비교 기사` /
  `다음 비교 기사` descriptions and disabled boundaries; arrows use theme ink.

## 3. Paired expression panel (top of every publisher page)

A compact comparison card (revised in phase 4c; the phase 4 two-column layout
was too cramped at 360dp and the phase 4b lavender band read as a dense text
block):

- Small theme-ink heading `표현 비교` above the card.
- A `Fl.Inset` card with a thin `Fl.Line` border and rounded corners.
- Top side: a filled theme-ink pill `현재 기사`, the current publisher name beside
  it, the finding's stored marker in quotes (serif 22sp, at most two lines with
  ellipsis, theme analysis tint), then one muted context line.
- Connector: the centred text `같은 내용을 이렇게 다르게` between two hairlines.
- Bottom side: an outlined theme-ink pill `다른 보도`, the page publisher name beside
  it, the marker of the page's first BODY evidence (falling back to the first
  evidence of any scope) in quotes, then one muted context line.
- Both sides share one layout: identical pill size, publisher style, phrase type,
  highlight, spacing and context style. The filled versus outlined pill only
  marks which article is open; it is not a judgment, and neither side is
  coloured or sized as correct, preferred, or dominant.
- Context: one line with end ellipsis; when the phrase starts deep in the
  sentence, the context starts shortly before it with a leading `…`. The phrase
  is underlined inside the context. Context that would only repeat the phrase is
  omitted. A missing publisher name leaves only the pill.
- Each side is read by screen readers as one unit (pill, publisher, phrase,
  context). The heading and connector are read as their visible text; the
  hairlines are silent.
- If either phrase is missing, the panel is omitted for that page; nothing is
  generated, paraphrased or summarised. Evidence summaries are not shown.
- Paging swaps only the bottom side; the top side stays the same.
- No score, ranking, bias judgment, red/blue coding, outlet logo, or "VS" label.

## 4. Unchanged

Publisher and time caption, speaker label rules (`기자 표현` omitted), article
title, `이 기사 읽기` (closes the sheet, then opens the Reader), `원문 보기`, the
full compared article with highlighted sentence and marker, automatic scroll to
that marker, per-page scroll positions, drag-to-dismiss regions (the panel is part
of the page's header region), the tutorial 3 / 3 callout, and all callbacks.

## 5. Not shown

No score, bias verdict, political label, ranking, red/blue colouring, invented
explanation, fake comparison count, or fabricated text.

## 6. Relationship to the annotation dock

The paired markers are the explicit comparison-card exception in
[`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6.3. They let both expressions
be read in one place; the dock's categories and explanation remain outside the
expanded sheet. The stored proposition overview is still shown once above pages.

## 7. Verification status

The three `ExpressionPairInstrumentedTest` cases passed on the connected 360dp
physical phone, including publisher paging, accessibility grouping, and
two-line phrase truncation. Enlarged-font and long-publisher combinations remain
explicit gaps in the linked behavioral test specification.
