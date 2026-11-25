# Home feed visual refresh (Android, phases 1–3 and final polish)

| Field | Value |
| --- | --- |
| Status | Archived visual baseline; navigation superseded |
| Owner | Android UI owner |
| Related behavior | [`news-app-features.md`](news-app-features.md), [`link-analysis.md`](link-analysis.md), [`first-run-tutorial.md`](first-run-tutorial.md) |
| Last reviewed | 2026-11-19 |

## 1. Purpose

This document records the pre-portal Android feed styling and its regression
tests. The current Home/News information architecture, topic rows, subscription
copy, and tutorial placement are defined by
[`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) and
[`story-feed.md`](story-feed.md). Sections 2, 4, 5.0, and scope-specific copy
below are not current Android product requirements.

The shared list byline also changed on 2026-11-25: Android rows now retain only
publisher/category and omit dates. Historical `publisher · time` copy below is
superseded by the current UI specification; Reader publication details remain.

Give the Android home feed a clearer, calmer news-app hierarchy (navy, off-white
and soft lavender; serif headlines) without changing what the feed shows or
where any action leads. This is a presentation change only: no API, database,
pipeline or view-model change.

## 2. App bar

- Left: a menu button (content description `메뉴`), then the FrameLESS mark.
- The menu holds only `튜토리얼 다시 보기`, which replays the existing
  tutorial. Choosing it closes the menu before invoking the callback. URL
  analysis is not ordinary-user navigation.
- Right, still one tap away: `기사 검색`, `저장한 기사`, `계정 메뉴`.
- Search mode keeps its existing header unchanged.

## 3. Visual system (phase 3)

- One page surface: off-white (`#F7F7F4`) behind the app bar, tabs, section
  header and rows; navy (`#1E2638`) for the active tab, refresh link and
  section rule; hairline dividers (`#E4E2EA`) inset to the text column.
- Serif for headlines (cover title, row titles, `최신 기사`); sans for kickers,
  bylines and captions.
- Each category has its own art direction, used consistently:

| Category | Cover artwork | Kicker mark | Kicker ink on off-white |
| --- | --- | --- | --- |
| 정치 | "Chamber": dashed concentric seating rows behind a domed colonnade on a stepped plinth | arch on a baseline | lavender ink `#5B4A93` |
| 사회 | "Commons": layered skyline with lit windows over rows of abstract figures and a crosswalk | two figures | slate blue `#34548A` |
| 경제 | "Treasury": ledger grid, an even lattice of points, three equal overlapping square-holed coins on one level line, a level scalloped band | square-holed coin | plum `#7A4878` |

- All artwork is drawn in Compose at runtime. No photo, remote image, logo,
  bundled third-party asset or news image is used. The shapes are fixed and
  never encode data (no values, counts, trend direction or political side).
  The Economy artwork deliberately avoids chart forms (bars, candlesticks,
  rising or falling lines, plotted points) so it cannot be read as market data.
- Every drawn visual is removed from the accessibility tree.

## 4. Category tabs and scope

The `정치` / `사회` / `경제` tabs keep their equal width, `<label> 섹션`
descriptions and tab role; the selected tab is bold navy with a short rounded
navy indicator. The scope control (`최신  ⌄` / `팔로잉 · N곳  ⌄`) is unchanged.

## 5. Feed list

### 5.0 Reading guide rail (final polish)

- In the normal feed (the same condition as the lead cover: online, search
  closed, no active query, at least one article), a compact rail sits directly
  below the category tabs and above the lead cover. It is never shown while
  searching, offline, or in loading, error and empty states.
- Content is fixed product copy only: the small label `FRAMELESS`, the serif
  line `같은 사건, 다른 표현`, and `보라색 표현을 누르면 다른 보도의 표현을 함께
  볼 수 있어요.` (at most two lines). No issue, outlet, count, quote, topic,
  rating or score.
- Visual: very light lavender surface (`#F2EFF9`) on the off-white page, 13dp
  corners, no shadow, navy type. On the right, a decorative app-drawn motif of
  two short text-line marks whose underlines converge on one shared point, then
  a small chevron. Both are hidden from screen readers.
- The whole rail is one button with the accessibility label `표현 비교 읽는 법`.
  Tapping it calls the existing `onTutorialOpen` (the same replay as the menu's
  `튜토리얼 다시 보기`); no new route or callback.
- The rail shares the lead cover's list item, so the number of list positions
  and the load-more rule are unchanged.

### 5.1 Lead article cover

- When the normal feed is shown (online, search closed, no active query) and
  has at least one article, the first article in the feed's existing order is
  shown as an editorial cover directly below the category tabs and scope
  control.
- The cover is a rounded card filled by the category's artwork. A dark scrim
  rises from the bottom; on it sit the category kicker, the stored title
  (white serif, up to four lines, exactly as supplied), the excerpt when
  present (up to two lines), the byline `publisher · time`, and a small
  decorative arrow ring as the tap affordance.
- The whole cover is one tap target. It calls the existing `onArticle(id)` and
  announces the click label `기사 열기`.
- The cover has no heading or label of its own. It is never called a top,
  recommended or comparison story, and shows no counts or rankings. Being
  first only reflects the feed's existing ordering.
- The lead article is not repeated in the list below.
- Not shown while searching (search open or an active query), offline, or
  in loading, error and empty states (including following with no
  publishers), because those states do not render the feed list.

### 5.2 Latest list

- Below the cover, a section header: serif `최신 기사`, then a short navy rule and the existing
  caption (`전체 언론사 · 최신순`, `팔로잉 언론사 · 최신순`, refreshing caption).
- With exactly one article, the header keeps only its caption and no rows follow.
- Android refreshes the normal feed by pulling down while the list is already
  at its top. The Material pull indicator is the only refresh affordance; it
  invokes the existing first-page refresh and ignores further pulls while that
  refresh is running. Search results retain their own retry controls.
- Offline, the header reads `이 기기의 기사` with the offline caption, and every
  stored article is listed as a row (no cover).
- Each article row sits directly on the page surface with no image tile and no
  box: category kicker (mark plus label in the category ink), the stored title
  exactly as supplied (serif, up to three lines), the search excerpt when
  present, then the byline `publisher · time`. A blank publisher or time is
  omitted, never replaced; if both are blank, there is no byline line.
- Search results use the same row on the same surface.
- Load-more, pagination, error, empty, offline and following states keep their
  current text and actions. The number of list positions is unchanged for two
  or more articles, so the existing scroll-based load-more rule still applies.

## 6. Not shown

No "오늘의 비교 이슈" card, "top story" or "recommended" label, outlet counts, comparison counts, `비교 가능` badges,
bias scores, rankings, left/right colouring or rubric codes. These need real
backend data and are out of scope for this phase.

## 7. Related specification note

- This archived phase moved tutorial replay into the legacy feed menu. Current
  replay entries are the all-topics News section and MY, as defined in
  [`first-run-tutorial.md`](first-run-tutorial.md). The Reader label-guide `?`
  remains a separate control.
