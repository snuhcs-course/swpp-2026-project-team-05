# Reader comparison points (Android)

| Field | Value |
| --- | --- |
| Status | Implemented; formal intent review not recorded |
| Owner | Reader feature owner |
| Clients | Android only |
| Iteration | 4 |
| Supplements | [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §6 (annotation dock and comparison sheet) |
| Test specification | [`../testing/reader-comparison-summary.spec.md`](../testing/reader-comparison-summary.spec.md) |

## Summary

A **reader-only, evidence-first UI addition** that uses **existing reviewed
comparison data** already delivered to the Android Reader. It adds a compact
`비교 포인트` entry section above the article so readers can reach a comparison
before finding a highlighted sentence.

The section **supplements** the existing comparison sheet. It does not amend or
replace any reviewed requirement: the sheet keeps its heading
`같은 내용을 다른 기사에서는` and its proposition-level comparison overview shown
once above the publisher pager, exactly as `news-app-v1.md` §4 and
`news-app-v1-ui.md` §6.3 require.

No backend API, database model, stored analysis, economic pipeline, navigation
architecture, or comparison sheet change. Nothing is generated on the device.

## Data used

From `ArticleDetail` and `ArticleAnalysisUi.findings` only:

- the current article's `publisher.name`;
- each finding's `markerText`, `markerRange`, `scope`, and `comparisons`;
- each compared article's `publisher` and `evidence[].markerText`.

The section does not render `comparisonOverview`, `explanation`, `categories`
(A–G codes or their labels), or evidence `summary`.

## Section `비교 포인트`

Position: in `NewsReaderScreen`, after the publisher/time/category metadata line
and before the article title and body.

```text
비교 포인트
연합뉴스  “징계”
KBS  “중징계”
                         표현 비교 보기 ›
```

### Eligibility

A finding produces a point only when:

- its own marker is non-blank; and
- at least one compared article has a non-blank evidence marker.

The second line uses the first compared article (stored page order) with a
non-blank evidence marker, and that article's first such marker. Markers are
shown exactly as stored; the section does not filter or rewrite reviewed
evidence.

### Selection

- At most two points, in reading order (title before body, then marker start).
- Each finding at most once (by id).
- Points that would render the same lines — same source marker, same compared
  publisher, same compared marker — appear once.

### Presentation

- Heading `비교 포인트`; per point, two lines `publisher  “exact marker”` (current
  article first), each rendered as one text, and the explicit action
  `표현 비교 보기`.
- Uses `Fl` colors, `Fl.Serif` for quoted markers, and Reader type sizes.
- No comparison counts, overview prose, explanation, A–G codes, category labels,
  or judgment wording.
- When no point qualifies, the section is not composed (no empty state).
- It is article-level, like the economic lens card, so the `표현 분석` marker
  toggle does not insert or remove it.

### Interaction

Tapping a point (the whole row) opens the **existing** comparison sheet for that
exact finding, with the same Reader state the annotation-dock action produces
(finding selected, sheet open). The highlight → dock → sheet flow and the sheet
itself are unchanged.

## Known limitations

- The section does not address stored overview text that contains
  internal-looking identifiers such as `A5`. That overview is still shown by the
  existing sheet as the reviewed specifications require; correcting it is a
  data/review issue outside this change.
- The section sits above the title. If stored analysis arrives after the article
  is first laid out, content below it shifts by the section's height; no scroll
  compensation is added by this section.

## Non-goals

- Any change to the comparison sheet, its heading, or its overview.
- Changing comparison grounding, page selection, or the three-page cap.
- Web client, feed, thumbnails, issue dashboards, real-time analysis.
