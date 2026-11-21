# Reader paired expression panel (Android)

| Field | Value |
| --- | --- |
| Status | Draft |
| Owner | Android UI owner |
| Related behavior | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6.3–6.4, [`home-feed-refresh.md`](home-feed-refresh.md) |
| Last reviewed | 2026-11-19 |

## 1. Purpose

Opening a comparison from a highlighted expression is FrameLESS's signature
moment. Each publisher page of the comparison sheet now opens with the two
expressions side by side, so the reader sees at once that the same content was
written in different words, before reading the compared article itself.

This is a presentation change on Android only. It uses stored finding and
comparison evidence already delivered to the Reader; no API, database,
pipeline, or mapper change.

## 2. Sheet frame

- Surface: off-white (`#F7F7F4`), matching Home.
- The fixed heading keeps the exact text `같은 내용을 다른 기사에서는` (navy
  serif) and the `비교 상세 닫기` close action.
- The stored comparison overview is still shown once, unchanged, between the
  heading and the pager, now as plain text beside a thin navy rule instead of a
  grey box. It is not repeated inside pages.
- The floating pager pill keeps `‹  n / N  ›`, its `이전 비교 기사` /
  `다음 비교 기사` descriptions and disabled boundaries; arrows are navy.

## 3. Paired expression panel (top of every publisher page)

A compact comparison card (revised in phase 4c; the phase 4 two-column layout
was too cramped at 360dp and the phase 4b lavender band read as a dense text
block):

- Small navy heading `표현 비교` above the card.
- An off-white card with a thin, subtle lavender-navy border and rounded
  corners. The card itself is not lavender.
- Top side: a filled navy pill `현재 기사`, the current publisher name beside
  it, the finding's stored marker in quotes (serif 22sp, at most two lines with
  ellipsis, light lavender highlight), then one muted context line.
- Connector: the centred text `같은 내용을 이렇게 다르게` between two hairlines.
- Bottom side: an outlined navy pill `다른 보도`, the page publisher name beside
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

## 6. Conflict with a reviewed spec (to resolve at review)

[`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6.3 says the expanded sheet
"does not repeat the marker, categories, or explanation already shown in the
annotation dock". This panel repeats the current article's marker, by explicit product
request, so that both expressions are compared in one place. Categories and the
explanation are still not repeated. This draft does not amend §6.3; a reviewer
should either accept the exception there or reject this panel.

## 7. Verification status

Not yet checked on a 360dp emulator. Long publisher names, very long markers,
large font scale, and dark-on-lavender contrast need a manual pass before this
is called finished.
