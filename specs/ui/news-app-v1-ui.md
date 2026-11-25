# FrameLESS News App v1 UI and Wireframe Specification

Status: Android portal shell and Reader baseline implemented

The current Home weather and market cards use fixed demo data. Their vector
artwork and market-direction styling are implemented; external data providers
are not connected in this slice.

Companion document: [`../product/news-app-v1.md`](../product/news-app-v1.md)

## 0. Implementation references

The UI must remain straightforward to implement and maintain in native Android. Product screenshots are inspiration, not the engineering contract. Use these source-available references:

- [Now in Android](https://github.com/android/nowinandroid) for Compose, Material 3, unidirectional state flow, loading/error states, and adaptive structure.
- [Jetnews](https://github.com/android/compose-samples/tree/main/JetNews) for the news feed, article reader, typography hierarchy, navigation, and window-size behavior.
- [FeedFlow](https://github.com/prof18/feed-flow) for a maintained, Apache-2.0 reader implementation and practical article-list density.
- [Material 3 adaptive list-detail](https://developer.android.com/develop/adaptive-apps/guides/list-detail) for future phone/tablet layout adaptation.

FrameLESS-specific behavior stays deliberately small: exact in-article markers, a scroll-synced Android annotation dock, and an on-demand expanded sheet for publisher comparisons. Particle and Ground News may inform product behavior, but their private implementations are not development dependencies. GPL reader projects may be observed visually but their code must not be copied into this repository.

## 1. Scope and priorities

This document defines screen structure, user actions, UI states, navigation, and
client boundaries. The v1 visual direction is a calm, text-first editorial
reader: compact controls, serif headlines and article text, restrained neutral
chrome, and accent color used only for verified expression analysis or state.

Priorities:

1. Users can browse and read articles like a normal news app.
2. Analysis never blocks article reading.
3. Users can inspect exact title or body markers; Android additionally lets
   users turn marker styling on or off.
4. A finding explains the selected expression and shows other publishers only when valid comparison evidence exists.
5. Home, News, Saved, MY, authentication, Reader, comparison, and settings have complete loading, empty, error, and retry behavior.

Visual rules:

- Android UI icons use the shared Lucide SVG/vector family: one 24-unit grid,
  rounded strokes, theme-aware tint, and consistent optical sizes. Search,
  save, back/close, expansion, source links and comparison navigation use
  drawable assets instead of text arrows or individually drawn Canvas glyphs.
  Keep operable controls accessible and preserve their existing destinations.
- The Home trend heading has no decorative `i` badge without an information
  action. Trend movement arrows require actual movement data; the preview feed
  does not invent them. Expand/collapse remains an explicit labeled action.
- Android ordinary UI actions use a neutral palette throughout: charcoal/white in light
  mode and off-white/charcoal in dark mode. Authentication, navigation, settings,
  dialogs, fields, and loading states use the same semantic theme colors. Color
  remains available for actual analysis meaning, publisher artwork, market data,
  and the shared FrameLESS brand mark.
- Home, authentication, search, and onboarding reuse one FrameLESS wordmark and
  mark. Comparison cards have neutral theme surfaces, never fixed cream fills.
- App-selected appearance controls analysis colors as well as chrome; fields,
  switch thumbs, badges and marked text retain readable contrast under either
  explicit appearance mode, regardless of the device's current mode.
- Keep the feed compact enough to scan several headlines without turning articles into oversized cards.
- Use dividers and typography for grouping; reserve pills for real actions or compact analysis labels.
- Do not add generic thumbnails, generated summaries, bias meters, truth scores, or decorative gradients.
- Keep comparison as a contextual layer inside the Reader rather than turning the feed into a cluster dashboard.
- Default to the device light/dark setting. MY → 화면 설정 also offers explicit
  `기기 설정 사용 | 라이트 모드 | 다크 모드` choices, stored locally and
  available to guests. Dark mode uses a charcoal page
  (`#202124`) with slightly lighter inset surfaces rather than pure black; text,
  dividers, markers, fields, sheets, and loading states must all remain legible.
- Home shows the FrameLESS mark once in its header. The search control contains
  search copy and a search affordance, not a second copy of the product mark.

I-series analysis, including I1, is deferred and does not appear in these screens. All analysis shown here is already stored; opening the feed, Reader, annotation dock, or comparison sheet never starts collection or an LLM call.

## 2. Primary flow

```text
Launch → Home
  ├─ Home: portal utilities → 주요 뉴스 → 구독한 언론사 (로고 선택 → 기사 목록)
  ├─ News: 전체 뉴스 | 언론사 | 정치 | 사회 | 경제
  ├─ Saved: personal article list or an in-place sign-in state
  └─ MY: account, subscriptions, analysis preference, appearance, tutorial replay

Topic row or article row → Article Reader
  → tap an analyzed expression
  → annotation dock → full comparison detail
```

Android uses one permanent four-item root navigation bar: `홈 | 뉴스 | 저장 |
MY`. Search is a mode entered from Home or News, not a fifth root destination.
The bottom bar stays hidden in Reader, authentication, tutorial, and nested
settings screens.

The Android bottom navigation bar is 60dp tall, excluding the OS navigation inset, with 22dp
icons, a 3dp icon-to-label gap, and 11sp labels. Each tab's whole cell remains
selectable, at least 48dp high. Curated publisher logos use sharp original brand
assets sized for their largest 44dp display at high-density phone resolution;
do not enlarge 16/32px favicons to fill those slots. Keep source URLs and native
asset dimensions in `android/app/src/main/assets/publishers/sources.json`.
Publisher artwork uses 44dp inside Home's 54dp slot, 40dp in the directory,
38dp in publisher headers, and 36dp in subscription management. Home reuses the
same rounded-square white logo tile as subscription management, without a second
outer border. Selected Home publishers use the existing bold name and selected
semantics rather than an extra frame around the logo.
Keep publisher actions at least 48dp high as artwork becomes more compact.

Clusters, internal analysis state, A-G codes, URL input, and diagnostics are not ordinary-user navigation.

## 3. Guest and authentication wireframes

### 3.0 Guest entry and protected actions

A clean client opens Home without creating a server identity. Home, News,
publisher browsing, search, Reader, and stored analysis remain available.
The trends collector's local anonymous analytics key is scoped in
[Recent-read trends](../features/realtime-trends.md); it is not a guest account
or a prerequisite for these public reads.
Subscription and save mutations require authentication, but entering the Saved
root tab itself never opens authentication.

The guest Saved tab renders its own calm sign-in-required state with both
`Login` and `Create account` actions. It remains selected until the user chooses
one. A guest who taps `구독` or a Reader bookmark stays on the current surface
and sees the same contextual authentication sheet above it. The sheet explains
the capability and offers `로그인`, `회원가입`, and dismissal;
authentication begins only after an explicit choice.

Successful authentication returns to Saved or to the public surface that
requested it. Android may complete the single publisher subscription that
opened authentication after the publisher list has reloaded; dismissal or
authentication cancellation clears that pending intent. A pending bookmark is
not replayed automatically. Android and Web use the same capability boundary
even when the physical dialog or sheet differs.

An Android Reader reaction tap uses this same contextual authentication flow
and is not replayed after login. The Reader module and Home selection rules are in
[Article reactions and Home highlights](../features/article-reactions-and-home-highlights.md).

### 3.1 Login

```text
┌──────────────────────────────────────┐
│              FrameLESS               │
│     다른 보도를 함께 읽는 뉴스 앱     │
│                                      │
│  이메일 또는 사용자 이름              │
│  [_______________________________]   │
│                                      │
│  비밀번호                       [보기] │
│  [_______________________________]   │
│                                      │
│  [              로그인             ] │
│                                      │
│       계정이 없나요? 회원가입         │
└──────────────────────────────────────┘
```

Functional requirements:

- Authentication uses the shared brand header, one clearly dominant neutral
  submit button, restrained secondary links, consistent field/button corners,
  and touch targets of at least 48 dp. Submission shows progress and disables
  duplicate form edits/submits without clearing values or server errors.
- Password IME action submits login.
- Validation appears beside the relevant field.
- Server errors do not clear entered values.
- Guest entry keeps an explicit `로그인 없이 둘러보기`/`Continue without
  signing in` action in addition to platform Back navigation.
- Successful login returns to the public route that requested authentication,
  or to Home when Login was opened directly.

### 3.2 Registration

```text
┌──────────────────────────────────────┐
│  [뒤로]            회원가입           │
│                                      │
│  이메일       [___________________]   │
│  사용자 이름  [___________________]   │
│  비밀번호     [___________________]   │
│  비밀번호 확인[___________________]   │
│  비밀번호 요구사항                    │
│                                      │
│  [            계정 만들기           ] │
└──────────────────────────────────────┘
```

- Registration creates an authenticated session.
- Duplicate email/username and password validation are field-specific.
- Success returns to the route that requested authentication, or Home when
  registration was opened directly.

## 4. Home and News discovery

### 4.1 Android root shell

```text
┌──────────────────────────────────────┐
│  FrameLESS                       [검색] │
│──────────────────────────────────────│
│  selected root content               │
│                                      │
│──────────────────────────────────────│
│   홈        뉴스        저장       MY   │
└──────────────────────────────────────┘
```

Root-shell and discovery behavior:

- Root navigation is `홈 | 뉴스 | 저장 | MY`. Each destination owns its
  scroll state. The bottom bar is hidden on Reader, authentication, tutorial,
  and nested settings surfaces.
- Home uses a compact portal rhythm: one search entry, restrained weather and
  market utility blocks, `주요 뉴스` (ALL), then `구독한 언론사`.
  A horizontal row of rounded-square publisher logos selects the articles below it;
  names and an outline identify the selected outlet. The first subscription
  is the default, and switching outlets never opens another page or moves the
  reader's scroll position. Fetch only the selected publisher's article page,
  caching visited first pages for this viewer. The section owns loading,
  failure, retry and empty states;
  its header links to MY subscription management. Guests and readers with no
  subscriptions see an in-place invitation below the public headlines.
  Weather/market values remain presentation fixtures until a provider is
  connected. Weather condition selects the matching clear/cloudy/rain/snow
  SVG and label. Every market/currency card derives direction from its numeric
  change: red plus an upward SVG triangle for positive, blue plus a downward
  triangle for negative, and neutral with no triangle for zero. Do not attach
  arbitrary colors to individual symbols or show provider/as-of boilerplate.
- Live `실시간 트렌드` behavior is defined in
  [Recent-read trends](../features/realtime-trends.md). It is independent of
  `주요 뉴스`: recent-three-hour readership, at most five distinct topics,
  first two collapsed, and an expand control only when more than two entries
  exist. Keep stable tie ordering, real rank movement,
  and distinct loading/empty/failure states. Sparse popularity is supplemented
  with distinct latest topics labeled `최신`, without ranks. Any latest fill,
  incomplete previous popularity list, first calculation, or old baseline hides
  all movement indicators. Fewer than five eligible topics show only the actual
  number; an empty corpus shows the empty state. Home pull-to-refresh updates
  the feed and requests trends independently, respecting server refresh/backoff.
  The three-hour window and calculation timestamps are internal cache/ranking
  metadata, not visible Home captions.
- `속보` and `이슈` rows follow
  [Article reactions and Home highlights](../features/article-reactions-and-home-highlights.md):
  one unexpired publisher-tagged Breaking article and one recent-reaction topic,
  omitting rows with no qualifying item. These replace temporary labels on
  ordinary Home articles; they do not change trends ranks.
- News uses one text-tab row in this fixed order: `전체 뉴스 | 언론사 | 정치 |
  사회 | 경제`. There is no second category row and no `최신/팔로잉`
  selector.
- `전체 뉴스`, `정치`, `사회`, and `경제` use topic rows; `언론사`, search,
  and Saved use article rows.
- The tutorial entry appears only in the `전체 뉴스` section header. It is not
  repeated in category or publisher sections. MY may retain `튜토리얼 다시
  보기`.

- Sort by publication time descending with stable cursor pagination.
- The earlier article-feed evaluation rule moves six reviewed examples already
  present in a loaded Latest page to the front. It remains in Web's article feed
  and Android's legacy feed helper; current Android Home/News story lists use
  the server's topic ordering. No extra page is fetched to find an example.
- Group repeated coverage before pagination according to
  [`../features/story-feed.md`](../features/story-feed.md).
- On Web, search is part of the feed content, Latest/Following is an inline
  segmented control, Saved is global navigation, and source management opens a
  dialog. This existing Web navigation may remain while Android uses the portal
  shell; both clients share account subscription data and article contracts.
- Tapping a topic row opens its representative article in the Reader. Tapping
  an article row opens that exact article.
- On Android, pulling down from the top of the normal feed refreshes the first
  page without losing root or section semantics. The feed header has
  no separate refresh action. Search keeps its own submit/retry flow and does
  not inherit the feed pull gesture.
- Android Reader, Saved, login, and registration support a rightward swipe that
  starts at the left screen edge as an additional Back affordance. The gesture
  is disabled while an overlay or comparison sheet owns the interaction. The
  current surface follows the finger, returns smoothly after a cancelled drag,
  and completes its exit before Back runs. Ordinary horizontal or vertical
  drags must remain available.
- Pagination failure leaves existing articles visible and shows inline retry.
- Returning from Reader restores root tab, News section, loaded pages, and item
  position.

### 4.2 Topic and article row content

Topic-row required content:

- representative publisher;
- representative headline, normally capped at two lines for scanability;
- quiet distinct-source metadata such as `외 3개 언론사` when applicable.

Publisher pages, search, and Saved reuse the compact article row without source
count metadata. The selected section is not repeated on every row.
Publication/relative timestamps are omitted from Android Home, News, publisher,
search and Saved list rows. Preserve them in the API and for chronological
ordering, and retain original publication details inside Reader/comparisons.
Duplicate captions such as `최신 기사` beside `주요 뉴스` and `최근 저장순` on
Saved are omitted; actual offline/loading/error states remain visible.
Analysis status belongs to the Reader where its consequence can be explained;
discovery does not expose `READY`, `PARTIAL`, processing, or failure badges.

Do not show:

- thumbnails or generated summaries;
- raw A-G categories;
- finding or comparison counts;
- internal cluster identifiers;
- publisher ranking, bias, or truth labels.
- duplicate open/view actions when the headline or entire row already opens the
  article.

### 4.3 Publisher directory and subscriptions

```text
┌──────────────────────────────────────┐
│  언론사                        [검색] │
│  [전체] [종합·시사] [전문]               │
│                                      │
│  연합뉴스                  [ + 구독 ] │
│  최신 기사 제목 1                     │
│  최신 기사 제목 2                     │
│  최신 기사 제목 3                     │
└──────────────────────────────────────┘
```

- User-facing copy is `구독`, `구독 중`, and `구독 관리`. Existing
  internal API/model names containing `follow` remain implementation details.
- The directory is publisher-first, not a flat checkbox settings form. Each
  publisher block shows an actual publisher logo, name,
  one subscription action, and up to three unnumbered
  recent article headlines separated by quiet dividers. A stored logo URL
  takes precedence; bundled genuine publisher assets and a neutral identity
  placeholder cover unavailable images. No article image is fabricated.
  Selecting its identity opens the
  publisher's ordinary article page.
- MY → `구독한 언론사 관리` is a separate `구독 관리` settings screen, never
  a redirect to this directory, including for guests. It shows only publisher
  identities and subscription switches. Changes save individually in place;
  failures keep confirmed state and show an inline error. Guests get the
  contextual authentication sheet before any mutation. Back returns to MY.
- Root navigation icons are vector drawable assets with a consistent 24-unit
  grid and rounded strokes. Selected state changes tint and label emphasis,
  not icon geometry; every root tab retains an accessible 48 dp touch target.
- Category chips are optional presentation filters over publisher metadata. If
  the metadata does not exist, omit the chips rather than guessing.
- A signed-in subscription action updates the server-owned account set and
  shows pending state only on that publisher row. Its button or switch alone
  is disabled; other publishers keep their appearance and remain actionable.
  Progress replaces the existing button icon without changing button width.
  A failed update shows its error under the affected publisher, including in
  MY management, and never under another publisher's detail header.
- A guest subscription action opens the contextual authentication sheet above
  the directory. Login or registration preserves this origin; dismissal
  changes nothing.
- Subscriptions may be empty and never change global search, article text, or
  Reader comparison evidence.

### 4.4 Discovery states

| State | Behavior |
| --- | --- |
| First loading | Article-row skeletons |
| Refreshing | Keep current rows and show refresh progress |
| No subscriptions on Home | Show public topics and one quiet subscription path |
| Empty publisher directory | Explain that no publishers are currently available |
| Next-page failure | Inline retry after the last loaded row |

Rows do not expose analysis lifecycle badges. A readable article remains a
normal news item regardless of analysis availability; the Reader owns loading,
partial, empty, and failure explanations.

### 4.5 Global search results

Search is global across all supported categories regardless of the section that
opened it. The pre-search root tab and News section remain in the background and
are restored when search closes.

```text
┌──────────────────────────────────────┐
│  [뒤로] [ 검색어________________ × ] │
│──────────────────────────────────────│
│  [전체]   [정치]   [사회]   [경제]      │
│──────────────────────────────────────│
│  연합뉴스                             │
│  정치 분야 검색 결과                  │
│  …검색어가 있는 본문 일부…             │
│  [기사 더 보기]                       │
└──────────────────────────────────────┘
```

- With an empty field the screen shows only `검색어를 입력하세요`: no article list
  and no filter row. The filter row appears from the first non-blank character.
- Ordinary discovery navigation remains unchanged. Search adds a separate
  compact `All / Politics / Society / Economy` filter row so section navigation and result
  filtering are not conflated.
- Render filters as restrained text tabs with one selected underline. Do not use
  filled pills, outlined buttons, or result-count badges for this navigation.
- `All` is selected for every new normalized query and issues one corpus-wide
  request. A category request begins only after the user selects that filter;
  completed filter pages may be reused when switching back.
- The active filter owns its cursor, load-more, loading, and retry state. Filters
  do not show result-count badges, relevance scores, or layout-changing metadata.
- An empty category filter shows a restrained category-specific message. An
  inline `전체 검색 결과 보기` action returns to `All` without changing the
  query. An empty `All` result shows a concise no-results state, suggests
  another term or checking the spelling, and never exposes URL analysis or a
  redundant re-enter button. Keep the query editable in the existing search field.
- On Android, tapping ordinary content outside a focused search, authentication,
  or URL field dismisses the keyboard without consuming the tapped control's
  action.
- The existing compact article row and optional plain-text excerpt are reused.
  Do not add result cards, relevance scores, analysis badges, or thumbnails.
- A new normalized query resets the active filter to `All`. Search never rewrites the Web URL
  and never applies a subscription filter.

## 5. Article Reader

### 5.1 Android analysis off

```text
┌──────────────────────────────────────┐
│  [뒤로]  기사 읽기       표현 분석 ○  │
│──────────────────────────────────────│
│  언론사 · 발행 시각 · 정치            │
│                                      │
│  기사 제목                            │
│  원문 보기                            │
│──────────────────────────────────────│
│                                      │
│  기사 전문이 일반 뉴스 기사처럼        │
│  그대로 표시된다.                     │
│                                      │
│  분석 표시를 위한 빈 공간이나 카드가   │
│  본문 사이에 들어가지 않는다.          │
│                                      │
└──────────────────────────────────────┘
```

### 5.2 Android analysis on

```text
┌──────────────────────────────────────┐
│  [뒤로]  기사 읽기       표현 분석 ●  │
│──────────────────────────────────────│
│  언론사 · 발행 시각 · 정치            │
│                                      │
│  기사 [제목 표현]                     │
│  원문 보기                            │
│──────────────────────────────────────│
│  일반 본문 내용...                    │
│                                      │
│  기자가 사용한 [분석 표현]이          │
│  원래 문장 위치에서 표시된다.          │
│                                      │
│  계속되는 기사 전문...                │
└──────────────────────────────────────┘
```

Reader behavior:

- Article detail loads independently from analysis.
- Android analysis off removes all marker styling without changing text or
  scroll position; analysis on marks exact title and body phrases.
- Web has no analysis toggle in the implemented baseline. It marks available
  title/body findings and keeps the analysis rail in section 6.5 visible.
- A marker with multiple categories is one tap target.
- Overlapping markers render as one target and open one finding.
- On Android, tapping a marker selects it in the dock in section 6 immediately.
  During a user scroll, a different BODY marker entering the lower-middle focus
  zone replaces the dock annotation immediately. Web marker taps select the
  same finding in the persistent analysis rail.
- Updating the dock or opening and closing the comparison sheet preserves source Reader position.
- `원문 보기` opens the canonical source externally.

### 5.3 Reader analysis states

| State | Reader behavior |
| --- | --- |
| `NOT_STARTED` | Article readable; Android toggle disabled; Web rail shows an analysis-unavailable state |
| `UPDATING` | Article readable; compact updating notice |
| `READY` with findings | Android toggle enabled; Web rail shows the findings |
| `READY` with zero findings | Android toggle enabled; both clients show completed-with-no-visible-finding message |
| `PARTIAL` | Verified findings available with an incompleteness notice |
| `FAILED` | Article readable; Android toggle disabled; Web rail shows an analysis-unavailable state |

READY with zero findings:

> 분석은 완료되었지만 현재 표시할 표현 특징을 확인하지 못했어요.

Do not describe the article as neutral.

## 6. Finding annotation and comparison detail

### 6.1 Android scroll-synced annotation dock

```text
┌──────────────────────────────────────┐
│ 평가·감정이 담긴 표현  다른 보도 2곳 › │
│ “온도차가 있었다”                     │
│ 여러 지역의 반응을 ‘온도차’로 묶어요. │
└──────────────────────────────────────┘
```

- A 118 dp dock is reserved below the article viewport whenever expression
  analysis is enabled and at least one finding exists. It never overlays the
  article or changes height as findings change.
- The dock always contains one finding. It initially uses the first title
  finding, otherwise the first body finding, and retains the current annotation
  through gaps between analyzed sentences.
- During a real user scroll, a 30 dp low-contrast accent tick at the right edge
  marks the 55% anchor. Do not draw a full-width reading line.
- A direct marker tap updates the dock immediately. Automatic selection is
  Android-only and follows the eligibility and user-scroll rules
  in [`../features/scroll-synced-analysis-peek.md`](../features/scroll-synced-analysis-peek.md).
- During user scrolling, the nearest eligible BODY finding replaces the dock
  content immediately. Opening/restoring a Reader, coach movement, and comparison
  focus do not trigger automatic replacement.
- When a direct finger drag changes the active finding, Android may emit one
  low-intensity system haptic. Fling inertia, programmatic movement, tutorial
  movement, and direct marker taps do not emit this feedback.
- A comparison-backed dock shows `다른 보도 N곳 ›`; tapping it opens the
  existing comparison sheet directly. A source-only dock has no comparison
  action and never opens an empty sheet.
- With analysis off or zero displayable findings, do not reserve the dock.
- The article-level `비교 포인트` entry remains available and unchanged.
- Show natural-language feature labels rather than raw A–G codes.

### 6.2 Source-only finding

A source-only finding remains in the fixed dock and never opens a second
surface. The Reader already supplies its source context, so repeating the
expression and sentence in a full-height sheet would add navigation without
adding information.

### 6.3 Comparison publisher pages

```text
┌──────────────────────────────────────┐
│  같은 내용을 다른 기사에서는      [×] │
│  ┌────────────────────────────────┐  │
│  │ 언론사별 표현 차이를 한 번에 설명 │  │
│  └────────────────────────────────┘  │
│──────────────────────────────────────│
│  연합뉴스 · 발행 시각                 │
│  비교 기사 제목                       │
│  [이 기사 읽기]             원문 보기 │
│  “피의자”가 들어간 대응 문장           │
│                                      │
│  비교 기사 전문...                    │
│  대응 표현은 본문에서 강조된다.      │
│                  ┌───────────┐ │
│                  │  ‹  1 / 2  ›  │ │
│                  └───────────┘ │
└──────────────────────────────────────┘
```

Comparison behavior:

- When comparisons exist, the expanded sheet begins directly with `같은 내용을
  다른 기사에서는`. It does not repeat the dock's categories or explanation.
  Each publisher page includes the paired expression card defined in
  [`reader-expression-pair.md`](../features/reader-expression-pair.md): the
  current and compared markers appear together for direct comparison. Paging
  changes the compared side while retaining the current article's side.
- Keep an explicit `×` close action in the fixed heading. Do not render gesture
  instructions such as swipe or pull-to-close copy; the pager, handle, and
  controls communicate those interactions directly.
- Show the proposition-level comparison overview once between the fixed heading
  and pageable evidence. It remains visible while publisher pages change and
  must not be duplicated inside those pages.
- Return at most three publisher-diverse pages.
- One publisher occupies one horizontal page.
- Multiple supporting sentences from one publisher remain in that page.
- Show publisher, publication time, exact target marker, a filled primary
  `이 기사 읽기`/`Read full article` action, and a visually quieter original
  link. The article title comes before these actions so the destination is
  clear before navigation.
- The primary action opens the existing article Reader for that comparison
  target. It does not create a second comparison-only article view.
- Show a speaker label such as `교육부 표현` only when a specific attributed
  speaker is stored. Omit the generic `기자 표현` label.
- The sheet opens directly at its maximum height. The target article scrolls
  vertically, and horizontal swiping changes publisher pages.
- With multiple pages, a compact floating `‹  n / N  ›` pill overlays the
  bottom edge and mirrors horizontal swiping without reserving article height.
  Its surface is lightly translucent but keeps accessible contrast. Boundary
  actions are disabled, and bottom article padding keeps the last line reachable.
- Each page keeps its own vertical position while paging.
- If a target article becomes unavailable, remove its page rather than showing broken content.

### 6.4 Navigation and gesture ownership

- Tapping a comparison-backed dock opens the expanded comparison sheet without
  moving the source Reader. There is no additional intermediate surface.
- Tapping a comparison page's article action closes the sheet and opens that
  target in the ordinary Reader. Back returns to the source Reader rather than
  the feed or app exit and restores the source Reader's scroll position.
- The stored target marker is shown with a restrained comparison-context style
  and scrolled into view. It is not presented as an A–G finding and does not
  alter the article string.
- The target Reader shows one compact comparison-context notice inside the
  article scroll area, so it leaves the screen naturally while reading. It
  must not occupy persistent top chrome. When that target has no standalone
  findings, say so explicitly without implying that the comparison marker is
  missing or invalid.
- Saving from the target Reader uses the ordinary saved-article control. A
  comparison-support article stays absent from feed and search unless
  `is_feed_visible` is separately enabled.
- There is no middle sheet anchor and no expand/collapse transition.
- Back, outside tap, or downward dismissal returns to the same Reader position
  and the same persistent annotation. Automatic replacement resumes only when
  another user scroll brings a different marker into the eligible band.
- The fixed-heading close action follows the same dismissal path.
- Back with only the dock visible leaves Reader normally.
- The dock itself has no swipe or drag gesture. Inside the expanded sheet,
  vertical gestures scroll the selected article and horizontal gestures page publishers.
- Downward drag dismisses from the top handle, the fixed comparison heading, or
  the fixed article metadata/evidence area.
- Article body gestures scroll normally; once that body is already at its top,
  a deliberate downward pull dismisses the sheet after release. Dismissal uses
  the Material hide animation; Material whole-sheet dragging stays disabled so
  these regions never fight the article scroll and wobble.

### 6.5 Desktop web analysis rail

The desktop Reader keeps the same finding and comparison content in a persistent
right rail. Its two navigation levels must use different controls:

- Move between findings with `Finding n of m` and previous/next actions. Do not
  render a row of anonymous numbered boxes; selecting a marker in the article
  remains the direct way to open its finding.
- Move between comparison articles with publisher-named tabs such as
  `연합뉴스`, `MBC`, and `뉴스1`. Do not use a second numeric pager.
- Show the number of comparison sources as supporting metadata, not as a control.
- Product copy shows natural-language finding labels and publisher names. Raw
  A–G codes and evaluation-only article IDs such as `S`, `A1`, or `A2` never
  appear in the Reader.
- Each active publisher page presents the destination title before its actions,
  exposes `Read full article` as a comfortably sized primary destination, and
  keeps `Original` as an external secondary destination. Browser back returns
  to the source Reader. Refreshing the target URL keeps the article available
  but may clear the transient comparison marker.

## 7. MY and account settings

### 7.1 MY overview

```text
┌──────────────────────────────────────┐
│  MY                                  │
│                                      │
│  사용자 이름 / 로그인                        │
│──────────────────────────────────────│
│  구독한 언론사 관리                  │
│  기본 분석 표시                       │
│  화면 설정 · 기기/라이트/다크          │
│  튜토리얼 다시 보기                    │
│  로그아웃                              │
└──────────────────────────────────────┘
```

- Logout clears the local token and personalized state, then continues in the
  public Home or current public Reader. Saved becomes its guest sign-in state;
  subscription state is removed from the active UI.
- Android remembers the last analysis-toggle value locally; Web has no separate
  toggle or default setting in the implemented baseline.
- Appearance is device-local and account-independent. The initial value is
  `기기 설정 사용`; changing it updates the whole app immediately and survives
  process restart without a backend request.
- MY lists implemented settings only. Do not show the former `오프라인 저장 공간`
  / `상세 관리 준비 중` placeholder; Saved's existing offline cache remains automatic.

The existing legacy URL-analysis capability is not linked from this v1 UI. A developer screen may be specified later without changing the feed or Reader routes.

## 8. Navigation and state restoration

- Clean launch without a valid session → guest Home.
- Login and registration success → prior route, or Home when authentication
  was opened directly.
- Home/News/Publisher/Search/Saved → Reader → back restores the exact
  origin, section, pages, and scroll position.
- Reader with open detail → back closes detail and returns to the same persistent
  annotation, preserving article position. Back with only the dock visible
  leaves the Reader normally.
- Contextual authentication sheet dismiss → unchanged underlying surface.
- Confirmed expired session clears authenticated state, keeps public content
  readable as a guest, and explains that personalized features require sign-in.
- Process recreation restores authentication and root/News-section state.
  Subscriptions and saved articles reload from the account; restoring an open Reader
  detail is deferred and the dock selects its normal initial finding.

### 8.1 Android offline restoration

- Online feeds remain server-backed. After a network failure, the feed switches
  to the active user's locally complete Saved articles for that category.
- Cached Reader content remains readable with a restrained offline notice.
- A locally verified Saved snapshot is visible only for the active unexpired
  account session; guest and other-account states cannot read it.
- Summary-only Saved rows are omitted offline so every visible row opens.
- Offline search, subscription changes, and save/remove mutations require a
  connection and never create optimistic queued state.

## 9. Accessibility and content edge cases

- Minimum touch target 48 × 48 dp.
- Support system font scaling without clipped controls.
- Marker meaning cannot rely only on color; selected state also changes weight, underline, outline, or semantics.
- Screen reader marker label: `{marker}, 표현 분석 보기`.
- Comparison-page announcement includes its publisher name and page position.
- Icons need Korean descriptions unless decorative.
- Test long publisher names, three-line headlines, long paragraphs, long explanations, and three comparison publishers.
- No automatic scrolling while the user is reading. Updating the dock never moves the article.
  A comparison page, or a Reader opened directly from that page, may move once
  to its first body evidence when first shown; after that, it preserves the
  reader's own position.

## 10. Compose boundaries

Screen routes:

```text
LoginRoute
RegistrationRoute
HomeRoute
NewsFeedRoute
PublisherDirectoryRoute
PublisherArticlesRoute
SavedArticlesRoute
MyRoute
ArticleReaderRoute
```

Reusable UI:

```text
ArticleFeedRow
StoryFeedRow
RootBottomBar
NewsSectionTabs
PublisherDirectory
SubscriptionButton
ContextualAuthSheet
SavedArticlesList
GuestSavedState
ArticleHeader
AnalysisToggle
ExpressionAnnotatedText
AnalysisAnnotationDock
FindingDetailSheet
ComparisonArticlePage
ReaderStatusNotice
```

- Routes own navigation and collect state.
- Components receive immutable UI models and callbacks.
- UI models follow versioned API DTOs and are not database-shaped.
- Authentication, remote news, and Android local-cache data sources remain
  injectable behind repositories.
- Preserve the existing FrameLESS visual language; improve hierarchy and scanability rather than redesigning it.
- Reuse the existing Reader's full-text annotation, tap targeting, exact evidence highlighting, and Reader-position preservation after extracting them from the legacy `ReaderResult` contract.
- Do not pass legacy `AnalysisResult`/`ReaderResult` or database models into the news routes. Convert versioned API DTOs to immutable UI models at the repository boundary.
- Keep URL import, live-analysis loading, analysis history, and the old comparison screen outside ordinary navigation. They may remain developer-only and isolated.
- Home, News, registration, subscription management, saved articles, account
  controls, and news navigation are product components; do not distort legacy
  screens to imitate them.
- Convert API code-point offsets to Compose UTF-16 indices once in the mapper and verify the expected quote. Invalid markers are omitted safely while the article remains readable.
- Re-test focus-zone selection, fixed dock height, clean expanded-sheet return,
  long comparison articles, font scaling, and physical-device back behavior.

Suggested extraction boundary:

```text
legacy ReaderResult ─┐
                    ├─ adapter ─> ReaderUiModel ─> shared Reader components
news v1 API DTOs ───┘

legacy AnalysisViewModel ─> legacy/developer route only
NewsFeedViewModel + ArticleReaderViewModel ─> normal product routes
```

## 11. Required UI tests

### Authentication and navigation

- Guest launch opens Home without redirecting to Login
- Guest feed, search, Reader, and stored analysis remain available
- Guest Saved opens an in-place login-required state; it does not redirect
- Guest subscription and bookmark controls open the contextual auth sheet and
  return to the prior public surface after an explicit auth choice
- Login and registration validation
- Login error preserves entered values
- Confirmed expired session becomes Guest without discarding public Reader content
- Direct login opens Home; contextual login returns to its origin
- Discovery → Reader → back restores root, section, and scroll position
- Saved → Reader → back restores the saved list rather than exiting the app
- Back from detail returns to the persistent dock before leaving Reader; with
  only the dock visible, Back leaves Reader normally
- Ordinary users never receive URL-analysis or diagnostic navigation

### Discovery

- Home/News/Saved/MY root switching and restoration
- News/언론사/정치/사회/경제 section switching
- Topic grouping and stable representative behavior
- Publisher directory, subscription success, failure, guest auth sheet, and
  publisher-article navigation
- Tutorial entry appears only in the News all-topics section and MY replay
- Search and saved-article entry actions
- Global search with All/Politics/Society/Economy filters and active-filter pagination
- Initial loading, refresh, pagination, empty, and retry states
- Cached, refreshing, offline-with-cache, and offline-without-cache states on Android
- Article row with a long headline and no redundant category, analysis, or open
  labels

### Reader and analysis

- Article loads independently from analysis
- Android analysis toggle preserves article text and scroll position
- Web analysis rail selects findings without changing article text or position
- Title and body markers open the correct finding
- One marker with multiple labels remains one target
- READY with zero findings
- Source-only finding has no empty comparison section
- At most three publisher pages
- Same-publisher evidence stays on one page
- Attribution and publication time appear
- Manual marker selection and eligible BODY-marker changes update the dock immediately
- Initial/restored/programmatic positions never auto-replace the dock
- Scroll restart keeps the current dock content without moving the article
- Detail sheet opens directly at maximum height
- Horizontal publisher paging and vertical article scrolling remain stable

## 12. UI definition of done

The UI is complete when:

- guests can browse, search, read, and inspect stored analysis on Android and
  Web without registration;
- users can register or log in for Saved and subscriptions and return to their prior
  public route;
- users can switch root destinations and News sections without losing state;
- topic discovery shows repeated coverage once while publisher, search, and
  Saved surfaces keep exact articles;
- users can search all feed-visible articles across Politics, Society, and Economy, narrow
  results with category filters, paginate the active result set, and return to
  the prior feed;
- users can save an article and return from its Reader to the saved list;
- tapping Saved as a guest never causes an immediate redirect, and subscription
  or bookmark authentication begins only from an explicit contextual action;
- authenticated Android users can inspect locally complete Saved articles and
  reopen their verified analyses offline without queuing writes;
- every visible article is readable regardless of analysis status;
- Android analysis can be toggled without changing article content or position;
- Web analysis remains visible in its rail without changing article content or position;
- exact title/body markers open the correct article-specific finding;
- source-only findings end without an empty comparison section;
- valid compared publishers page horizontally while their articles scroll vertically;
- the fixed annotation dock can follow the reading position without
  covering the article, resizing, interrupting fast scrolling, or requiring dragging;
- original links, publisher, publication time, and attribution remain visible;
- ordinary users never encounter URL input or internal diagnostics;
- required Compose, accessibility, and end-to-end UI tests pass.
