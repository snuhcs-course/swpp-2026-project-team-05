# FrameLESS News App v1 UI and Wireframe Specification

Status: v1.9 implemented Android and Web baseline

Companion document: [`../product/news-app-v1.md`](../product/news-app-v1.md)

## 0. Implementation references

The UI must remain straightforward to implement and maintain in native Android. Product screenshots are inspiration, not the engineering contract. Use these source-available references:

- [Now in Android](https://github.com/android/nowinandroid) for Compose, Material 3, unidirectional state flow, loading/error states, and adaptive structure.
- [Jetnews](https://github.com/android/compose-samples/tree/main/JetNews) for the news feed, article reader, typography hierarchy, navigation, and window-size behavior.
- [FeedFlow](https://github.com/prof18/feed-flow) for a maintained, Apache-2.0 reader implementation and practical article-list density.
- [Material 3 adaptive list-detail](https://developer.android.com/develop/adaptive-apps/guides/list-detail) for future phone/tablet layout adaptation.

FrameLESS-specific behavior stays deliberately small: exact in-article markers, a compact Android finding preview, and an on-demand expanded sheet for publisher comparisons. Particle and Ground News may inform product behavior, but their private implementations are not development dependencies. GPL reader projects may be observed visually but their code must not be copied into this repository.

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
5. Login, feed, Reader, comparison, and settings have complete loading, empty, error, and retry behavior.

Visual rules:

- Keep the feed compact enough to scan several headlines without turning articles into oversized cards.
- Use dividers and typography for grouping; reserve pills for real actions or compact analysis labels.
- Do not add generic thumbnails, generated summaries, bias meters, truth scores, or decorative gradients.
- Keep comparison as a contextual layer inside the Reader rather than turning the feed into a cluster dashboard.

I-series analysis, including I1, is deferred and does not appear in these screens. All analysis shown here is already stored; opening the feed, Reader, preview, or comparison sheet never starts collection or an LLM call.

## 2. Primary flow

```text
Launch
  ├─ no valid session → Login → Register if needed
  └─ valid session → News feed ↔ Saved articles
                                                  ↓
                                            Article Reader
                                                  ↓
                                   Tap an analyzed expression
                                                  ↓
                         Finding preview → full comparison detail
```

The feed is the primary discovery destination and Saved is a secondary route.
Search is a feed mode rather than a separate destination. A small account action
shows account identity and logout without a permanent bottom navigation bar.

Clusters, internal analysis state, A-G codes, URL input, and diagnostics are not ordinary-user navigation.

## 3. Authentication wireframes

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

- Password IME action submits login.
- Validation appears beside the relevant field.
- Server errors do not clear entered values.
- Successful login routes directly to the all-publisher feed.
- There is no guest path.

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
- Success routes directly to the all-publisher feed.

## 4. News feed

### 4.1 Android main wireframe

```text
┌──────────────────────────────────────┐
│  FrameLESS          [검색][저장][계정] │
│  정치   사회              최신 기사 ⌄ │
│──────────────────────────────────────│
│  연합뉴스 · 12분 전                    │
│  기사 제목은 최대 두 줄로 표시        │
│──────────────────────────────────────│
│  MBC · 35분 전                       │
│  다음 기사 제목                       │
│──────────────────────────────────────│
│  ...                                 │
│                                      │
└──────────────────────────────────────┘
```

Feed behavior:

- Sort by publication time descending with stable cursor pagination.
- During the evaluation build, Web and Android may quietly move the six reviewed
  example stories already present in a loaded Latest page to the front. This is
  client-only, adds no label or separate section, and does not change API or
  cursor ordering.
- Category control switches between politics and society.
- On Android, Latest/Following opens the scope sheet and source management opens
  the bottom sheet in section 4.3.
- The Android scope sheet presents Latest and Following as one mutually
  exclusive radio group. Checkboxes are reserved for selecting multiple
  publishers in source management.
- On Web, search is part of the feed content, Latest/Following is an inline
  segmented control, Saved is global navigation, and source management opens a
  dialog. These controls preserve the same feed state and API contracts as
  Android; they are not required to share the same physical placement.
- Politics and Society live in the global header only; do not duplicate the
  same section control in a feed sidebar.
- Tapping anywhere on an article row opens the Reader.
- The compact refresh action updates the first page without losing filters; a pull gesture may be added later without changing feed state semantics.
- Pagination failure leaves existing articles visible and shows inline retry.
- Returning from Reader restores item position, category, and feed scope.

### 4.2 Article row content

Required:

- publisher;
- publication time;
- headline, normally capped at two lines for scanability.

The selected politics/society category is already visible in the feed
navigation and is not repeated on every row. Analysis status belongs to the
Reader where its consequence can be explained; the feed does not expose
`READY`, `PARTIAL`, processing, or failure badges.

Do not show:

- thumbnails or generated summaries;
- raw A-G categories;
- finding or comparison counts;
- cluster information;
- publisher ranking, bias, or truth labels.
- duplicate open/view actions when the headline or entire row already opens the
  article.

### 4.3 Followed-source management

```text
┌──────────────────────────────────────┐
│  ─────                               │
│  팔로우할 언론사              [저장]  │
│                                      │
│  [ ] 연합뉴스  [✓] MBC  [✓] SBS       │
│  [ ] 뉴스1     [ ] KBS  [ ] YTN       │
│  ...                                 │
└──────────────────────────────────────┘
```

- Changes remain a local draft until `저장`; dismissing restores the server set.
- Saving atomically replaces the authenticated account's followed publishers.
- Following may be empty and never changes Latest, search, or Reader comparisons.
- Android uses a bottom sheet and Web uses a dialog while preserving the same contract.

### 4.4 Feed states

| State | Behavior |
| --- | --- |
| First loading | Article-row skeletons |
| Refreshing | Keep current rows and show refresh progress |
| Empty Following | Distinguish no followed sources from no matching article in the category |
| Next-page failure | Inline retry after the last loaded row |

Feed rows do not expose analysis lifecycle badges. A readable article remains a
normal news item regardless of analysis availability; the Reader owns loading,
partial, empty, and failure explanations.

### 4.5 Global search results

Search is global across the two supported categories even when the user entered
it from one category or from Following. The pre-search category and scope remain
selected in the background and are restored when search closes.

```text
┌──────────────────────────────────────┐
│  [뒤로] [ 검색어________________ × ] │
│──────────────────────────────────────│
│  정치  27                            │
│──────────────────────────────────────│
│  연합뉴스 · 12분 전                    │
│  정치 분야 검색 결과                  │
│  …검색어가 있는 본문 일부…             │
│  [정치 기사 더 보기]                  │
│                                      │
│  사회  8                             │
│──────────────────────────────────────│
│  MBC · 20분 전                       │
│  사회 분야 검색 결과                  │
└──────────────────────────────────────┘
```

- Politics and Society always have separate headers. The small neutral count
  pill is the exact server count, not the number currently loaded.
- Each section owns its page cursor and load-more/error state. Loading or
  retrying one section must not clear or move the other section.
- A section with zero matches keeps its header and shows a restrained empty row.
  If both sections are empty, show the existing no-results state and URL-analysis
  preview entry below the section search outcome.
- The existing compact article row and optional plain-text excerpt are reused.
  Do not add result cards, relevance scores, analysis badges, or thumbnails.
- A new normalized query resets both sections. Search never rewrites the Web URL
  and never applies the pre-search Following filter.

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
- Tapping a marker opens the preview in section 6 on Android or selects the same
  finding in the Web rail.
- Opening and closing the preview or comparison sheet preserves source Reader position.
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

## 6. Finding preview and comparison detail

### 6.1 Android finding preview

```text
┌──────────────────────────────────────┐
│  표현 분석                         [×] │
│  “온도차가 있었다”                    │
│  여러 지역의 반응을 ‘온도차’라는    │
│  말로 묶어 설명해요.                  │
│  [평가·감정이 담긴 표현]              │
└──────────────────────────────────────┘
```

- The preview floats over the bottom of the Reader without a scrim and does not
  change article scroll position.
- It keeps one anchored compact form and is never dragged or internally
  scrolled. Only the inline explanation disclosure may increase its height.
- The article remains scrollable; tapping a different marker replaces the
  preview content in place.
- Close, Android back, disabling analysis, or tapping ordinary article text
  dismisses the preview. Article scrolling alone does not dismiss it.
- Every preview shows all natural-language feature labels and up to four lines
  of explanation. When text overflows, `설명 더보기` expands it in place and
  `설명 접기` restores the compact card; no analysis text becomes inaccessible.
- A source-only finding ends in the preview with no chevron or detail action.
- A comparison-backed finding additionally shows `다른 기사 N곳과 비교`.
- Show natural-language feature labels rather than raw A–G codes.

### 6.2 Source-only finding

A source-only finding never opens a second surface. The Reader already supplies
its source context, so repeating the expression and sentence in a full-height
sheet would add navigation without adding information.

### 6.3 Comparison publisher pages

```text
┌──────────────────────────────────────┐
│  같은 내용을 다른 기사에서는      [×] │
│  ┌────────────────────────────────┐  │
│  │ 언론사별 표현 차이를 한 번에 설명 │  │
│  └────────────────────────────────┘  │
│──────────────────────────────────────│
│  연합뉴스 · 발행 시각                 │
│  비교 기사 제목                 원문 보기 │
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
  다른 기사에서는`; it does not repeat the marker, categories, or explanation
  already shown in the preview.
- Keep an explicit `×` close action in the fixed heading. Do not render gesture
  instructions such as swipe or pull-to-close copy; the pager, handle, and
  controls communicate those interactions directly.
- Show the proposition-level comparison overview once between the fixed heading
  and pageable evidence. It remains visible while publisher pages change and
  must not be duplicated inside those pages.
- Return at most three publisher-diverse pages.
- One publisher occupies one horizontal page.
- Multiple supporting sentences from one publisher remain in that page.
- Show publisher, publication time, exact target marker, and original link.
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

- Tapping the preview action opens the expanded comparison sheet without moving the source Reader.
- There is no middle sheet anchor and no expand/collapse transition.
- Back, outside tap, or downward dismissal returns to the same Reader position
  and clears the preview instead of reopening a surface the reader has already completed.
- The fixed-heading close action follows the same dismissal path.
- Back from the preview closes it before leaving Reader.
- The preview itself has no swipe or drag gesture. Inside the expanded sheet,
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

## 7. Account menu

### 7.1 Account menu

```text
┌──────────────────────────────────────┐
│  계정                                │
│                                      │
│  사용자 이름                         │
│  이메일                              │
│──────────────────────────────────────│
│  로그아웃                            │
└──────────────────────────────────────┘
```

- Logout clears the local token and returns to Login.
- Android remembers the last analysis-toggle value locally; Web has no separate
  toggle or default setting in the implemented baseline.

The existing legacy URL-analysis capability is not linked from this v1 UI. A developer screen may be specified later without changing the feed or Reader routes.

## 8. Navigation and state restoration

- Login and registration success → all-publisher feed.
- Feed → Reader → back restores feed position and filters.
- Reader with open detail → back closes both detail and its preview, preserving
  the article position. Back from a preview alone closes the preview first.
- Account menu dismiss → same feed state.
- Expired session clears authenticated state and returns to Login with an explanation.
- Process recreation restores authentication and the last feed category. Followed
  sources and saved articles reload from the account; restoring an open Reader
  preview or detail is deferred.

## 9. Accessibility and content edge cases

- Minimum touch target 48 × 48 dp.
- Support system font scaling without clipped controls.
- Marker meaning cannot rely only on color; selected state also changes weight, underline, outline, or semantics.
- Screen reader marker label: `{marker}, 표현 분석 보기`.
- Comparison-page announcement includes its publisher name and page position.
- Icons need Korean descriptions unless decorative.
- Test long publisher names, three-line headlines, long paragraphs, long explanations, and three comparison publishers.
- No automatic scrolling while the user is reading. Opening a preview never moves the article.
  A comparison page may move once to its first body evidence when first shown;
  after that, it preserves the reader's own position.

## 10. Compose boundaries

Screen routes:

```text
LoginRoute
RegistrationRoute
NewsFeedRoute
SavedArticlesRoute
ArticleReaderRoute
```

Reusable UI:

```text
ArticleFeedRow
CategorySelector
FeedScopeSheet
ManageFollowedSourcesSheet
SavedArticlesList
AccountMenuSheet
AnalysisStateBadge
ArticleHeader
AnalysisToggle
ExpressionAnnotatedText
FindingPreviewCard
FindingDetailSheet
ComparisonArticlePage
ReaderStatusNotice
```

- Routes own navigation and collect state.
- Components receive immutable UI models and callbacks.
- UI models follow versioned API DTOs and are not database-shaped.
- Authentication, feed, article, and analysis repositories remain injectable.
- Preserve the existing FrameLESS visual language; improve hierarchy and scanability rather than redesigning it.
- Reuse the existing Reader's full-text annotation, tap targeting, exact evidence highlighting, and Reader-position preservation after extracting them from the legacy `ReaderResult` contract.
- Do not pass legacy `AnalysisResult`/`ReaderResult` or database models into the news routes. Convert versioned API DTOs to immutable UI models at the repository boundary.
- Keep URL import, live-analysis loading, analysis history, and the old comparison screen outside ordinary navigation. They may remain developer-only and isolated.
- Feed, registration, followed-source management, saved articles, account
  controls, and news navigation are product components; do not distort legacy
  screens to imitate them.
- Convert API code-point offsets to Compose UTF-16 indices once in the mapper and verify the expected quote. Invalid markers are omitted safely while the article remains readable.
- Re-test preview dismissal, clean expanded-sheet return, long comparison articles, font scaling, and physical-device back behavior.

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

- Login and registration validation
- Login error preserves entered values
- Expired session returns to Login
- Login and registration open the all-publisher feed
- Feed → Reader → back restores scroll position, category, and scope
- Saved → Reader → back restores the saved list rather than exiting the app
- Back from detail clears detail and preview before leaving Reader; back from a
  preview alone clears it before leaving Reader
- Ordinary users never receive URL-analysis or diagnostic navigation

### Feed

- Politics/society switching
- Latest/Following switching and distinct empty states
- Followed-source draft, save, cancel, and failure behavior
- Search and saved-article entry actions
- Global search category headers, exact count pills, and independent pagination
- Initial loading, refresh, pagination, empty, and retry states
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
- Marker opens preview before detail
- Detail sheet opens directly at maximum height
- Horizontal publisher paging and vertical article scrolling remain stable

## 12. UI definition of done

The UI is complete when:

- users can register or log in and reach the all-publisher feed directly;
- users can switch politics/society and Latest/Following without losing state;
- users can search all feed-visible articles across Politics and Society, see
  exact category counts, paginate either section independently, and return to
  the prior feed;
- users can save an article and return from its Reader to the saved list;
- every visible article is readable regardless of analysis status;
- Android analysis can be toggled without changing article content or position;
- Web analysis remains visible in its rail without changing article content or position;
- exact title/body markers open the correct article-specific finding;
- source-only findings end without an empty comparison section;
- valid compared publishers page horizontally while their articles scroll vertically;
- the compact preview does not interrupt article reading or require dragging;
- original links, publisher, publication time, and attribution remain visible;
- ordinary users never encounter URL input or internal diagnostics;
- required Compose, accessibility, and end-to-end UI tests pass.
