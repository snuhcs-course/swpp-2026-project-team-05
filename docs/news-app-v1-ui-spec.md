# FrameLESS News App v1 UI and Wireframe Specification

Status: v1.6 implementation and visual baseline

Companion document: `docs/news-app-v1-spec.md`

## 0. Implementation references

The UI must remain straightforward to implement and maintain in native Android. Product screenshots are inspiration, not the engineering contract. Use these source-available references:

- [Now in Android](https://github.com/android/nowinandroid) for Compose, Material 3, unidirectional state flow, loading/error states, and adaptive structure.
- [Jetnews](https://github.com/android/compose-samples/tree/main/JetNews) for the news feed, article reader, typography hierarchy, navigation, and window-size behavior.
- [FeedFlow](https://github.com/prof18/feed-flow) for a maintained, Apache-2.0 reader implementation and practical article-list density.
- [Material 3 adaptive list-detail](https://developer.android.com/develop/adaptive-apps/guides/list-detail) for future phone/tablet layout adaptation.

FrameLESS-specific behavior stays deliberately small: exact in-article markers, a Material 3 bottom sheet, and a Compose horizontal pager for publisher comparisons. Particle and Ground News may inform product behavior, but their private implementations are not development dependencies. GPL reader projects may be observed visually but their code must not be copied into this repository.

## 1. Scope and priorities

This document defines screen structure, user actions, UI states, navigation, and Compose boundaries. The v1 visual direction is a calm, text-first editorial reader: compact controls, serif headlines and article text, restrained navy/gray chrome, and lilac used only for verified expression analysis.

Priorities:

1. Users can browse and read articles like a normal news app.
2. Analysis never blocks article reading.
3. Users can turn analysis on and inspect exact title or body markers.
4. A finding explains the selected expression and shows other publishers only when valid comparison evidence exists.
5. Login, feed, Reader, comparison, and settings have complete loading, empty, error, and retry behavior.

Visual rules:

- Keep the feed compact enough to scan several headlines without turning articles into oversized cards.
- Use dividers and typography for grouping; reserve pills for real actions or compact analysis labels.
- Do not add generic thumbnails, generated summaries, bias meters, truth scores, or decorative gradients.
- Keep comparison as a contextual layer inside the Reader rather than turning the feed into a cluster dashboard.

I-series analysis, including I1, is deferred and does not appear in these screens. All analysis shown here is already stored; opening the feed, Reader, or analysis sheet never starts collection or an LLM call.

## 2. Primary flow

```text
Launch
  ├─ no valid session → Login → Register if needed
  └─ valid session → News feed
                                                  ↓
                                            Article Reader
                                                  ↓
                                   Tap an analyzed expression
                                                  ↓
                              Finding detail / comparison bottom sheet
```

The feed is the only top-level destination. A small account action shows account identity and logout without a permanent bottom navigation bar.

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

### 4.1 Main wireframe

```text
┌──────────────────────────────────────┐
│  FrameLESS                    [계정]  │
│  정치   사회          언론사 · 3곳 ⌄ │
│  연합뉴스 · MBC · SBS                 │
│──────────────────────────────────────│
│  연합뉴스 · 정치 · 12분 전    분석 완료 │
│  기사 제목은 최대 두 줄로 표시        │
│──────────────────────────────────────│
│  MBC · 정치 · 35분 전         분석 중 │
│  다음 기사 제목                       │
│──────────────────────────────────────│
│  ...                                 │
│                                      │
└──────────────────────────────────────┘
```

Feed behavior:

- Sort by publication time descending with stable cursor pagination.
- Category control switches between politics and society.
- Publisher filter opens the sheet in section 4.3.
- Tapping anywhere on an article row opens the Reader.
- The compact refresh action updates the first page without losing filters; a pull gesture may be added later without changing feed state semantics.
- Pagination failure leaves existing articles visible and shows inline retry.
- Returning from Reader restores item position, category, and publisher filters.

### 4.2 Article row content

Required:

- publisher;
- publication time;
- headline, normally capped at two lines for scanability;
- politics/society category;
- analysis state when useful.

Do not show:

- thumbnails or generated summaries;
- raw A-G categories;
- finding or comparison counts;
- cluster information;
- publisher ranking, bias, or truth labels.

### 4.3 Publisher filter sheet

```text
┌──────────────────────────────────────┐
│  ─────                               │
│  언론사 선택                  [완료]  │
│                                      │
│  [✓] 전체 언론사                     │
│  ──────────────────────────────────  │
│  선택한 언론사                        │
│  [ ] 연합뉴스  [✓] MBC  [✓] SBS       │
│                                      │
│  모든 언론사                          │
│  [ ] 뉴스1     [ ] KBS  [ ] YTN       │
│  ...                                 │
└──────────────────────────────────────┘
```

- `전체 언론사` is mutually exclusive with individual selection.
- Apply changes on `완료`; dismiss without completion restores the previous filter.
- Changing the filter scrolls the refreshed feed to the top.
- Remember the last publisher selection locally on the device; do not create server-side preference UI in v1.

### 4.4 Feed states

| State | Behavior |
| --- | --- |
| First loading | Article-row skeletons |
| Refreshing | Keep current rows and show refresh progress |
| Empty filter | Explain the active filter; offer `전체 언론사 보기` |
| Next-page failure | Inline retry after the last loaded row |

Analysis badges:

| State | Feed treatment |
| --- | --- |
| `NOT_STARTED` | no badge |
| `UPDATING` | `분석 중` |
| `READY` | `분석 완료`; this does not promise that a visible finding exists |
| `PARTIAL` | `일부 분석` |
| `FAILED` | no failure badge; article remains ordinary readable content |

## 5. Article Reader

### 5.1 Analysis off

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

### 5.2 Analysis on

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
- Analysis off removes all marker styling without changing text or scroll position.
- Analysis on marks exact title and body phrases.
- A marker with multiple categories is one tap target.
- Overlapping markers render as one target and open one finding.
- Tapping a marker opens the sheet in section 6.
- Opening, paging, expanding, collapsing, and closing the sheet preserves source Reader position.
- `원문 보기` opens the canonical source externally.

### 5.3 Reader analysis states

| State | Reader behavior |
| --- | --- |
| `NOT_STARTED` | Article readable; toggle disabled; analysis unavailable message |
| `UPDATING` | Article readable; compact updating notice |
| `READY` with findings | Toggle enabled |
| `READY` with zero findings | Toggle enabled; show completed-with-no-visible-finding message |
| `PARTIAL` | Verified findings available with an incompleteness notice |
| `FAILED` | Article readable; toggle disabled; analysis unavailable message |

READY with zero findings:

> 분석은 완료되었지만 현재 표시할 표현 특징을 확인하지 못했어요.

Do not describe the article as neutral.

## 6. Finding detail and comparison sheet

### 6.1 Source-only finding

```text
┌──────────────────────────────────────┐
│                 ─────                │
│  이 표현에서 드러난 특징             │
│                                      │
│  선택한 표현: “온도차가 있었다”       │
│  [평가·범주화 표현]                   │
│                                      │
│  여러 지역의 서로 다른 반응을         │
│  ‘온도차’라는 말로 묶어 설명해요.      │
│                                      │
│  기사 문맥                            │
│  ... “온도차가 있었다” ...            │
│                                      │
└──────────────────────────────────────┘
```

- End after the explanation and source context.
- Do not render an empty pager.
- Do not claim that other articles omitted the content.
- Show natural-language feature labels rather than raw A-G codes.

### 6.2 Finding with comparisons — middle state

```text
┌──────────────────────────────────────┐
│                 ─────                │
│  이 표현에서 드러난 특징             │
│  “보복살해범”                         │
│  확정적 명명과 수사 단계 명칭의 차이  │
│──────────────────────────────────────│
│  같은 내용을 다른 기사에서는    1 / 2 │
│                                      │
│  연합뉴스 · 발행 시각                 │
│  ... “피의자” 신상 공개 ...           │
│  원문 보기                            │
│                                      │
│  ← 좌우로 다른 기사 보기 →            │
└──────────────────────────────────────┘
```

### 6.3 Finding with comparisons — expanded state

```text
┌──────────────────────────────────────┐
│                 ─────                │
│  같은 내용을 다른 기사에서는    1 / 2 │
│  연합뉴스 · 발행 시각 · 원문 보기     │
│──────────────────────────────────────│
│                                      │
│  비교 기사 제목                       │
│                                      │
│  비교 기사 전문...                    │
│                                      │
│  대응되는 [피의자] 표현이 화면의      │
│  읽기 좋은 위치에 오도록 이동한다.     │
│                                      │
│  나머지 비교 기사 전문...             │
│                                      │
└──────────────────────────────────────┘
```

Comparison behavior:

- Return at most three publisher-diverse pages.
- One publisher occupies one page.
- Multiple supporting sentences from one publisher remain in that page.
- Show publisher, publication time, exact target marker, comparison explanation, and original link.
- When target wording is attributed, show a label such as `교육부 표현` or `익명 관계자 발언`.
- Middle state shows the selected finding and comparison preview.
- Expanded state allows vertical scrolling through the target article.
- Horizontal swiping changes publisher; each page keeps its own vertical position.
- The selected publisher page remains stable while expanding or collapsing.
- If a target article becomes unavailable, remove its page rather than showing broken content.

### 6.4 Sheet gesture ownership

- Sheet initially opens at the middle anchor.
- Upward drag on the handle expands it.
- Downward drag on the handle collapses expanded → middle.
- Back closes the sheet before leaving Reader.
- In expanded state, vertical gestures on article content scroll the article, not the sheet.
- Horizontal gestures in comparison content page publishers.
- Vertical and horizontal gestures must not make the sheet oscillate or change anchors unexpectedly.

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
- The Reader remembers the last analysis-toggle value locally; there is no separate default setting.

The existing legacy URL-analysis capability is not linked from this v1 UI. A developer screen may be specified later without changing the feed or Reader routes.

## 8. Navigation and state restoration

- Login and registration success → all-publisher feed.
- Feed → Reader → back restores feed position and filters.
- Reader with open sheet → back closes sheet first.
- Account menu dismiss → same feed state.
- Expired session clears authenticated state and returns to Login with an explanation.
- Process recreation restores authentication and the last feed category/publisher filter. Restoring an open Reader or sheet is deferred.

## 9. Accessibility and content edge cases

- Minimum touch target 48 × 48 dp.
- Support system font scaling without clipped controls.
- Marker meaning cannot rely only on color; selected state also changes weight, underline, outline, or semantics.
- Screen reader marker label: `{marker}, 표현 분석 보기`.
- Pager announcement: `비교 기사 2개 중 1번째, 연합뉴스`.
- Icons need Korean descriptions unless decorative.
- Test long publisher names, three-line headlines, long paragraphs, long explanations, and three comparison publishers.
- No automatic scrolling while the user is reading, except deliberate centering of a selected target sentence when a comparison page opens.

## 10. Compose boundaries

Screen routes:

```text
LoginRoute
RegistrationRoute
NewsFeedRoute
ArticleReaderRoute
```

Reusable UI:

```text
ArticleFeedRow
CategorySelector
PublisherFilterSheet
AccountMenuSheet
AnalysisStateBadge
ArticleHeader
AnalysisToggle
ExpressionAnnotatedText
FindingDetailSheet
ComparisonPublisherPager
ComparisonArticlePage
ReaderStatusNotice
```

- Routes own navigation and collect state.
- Components receive immutable UI models and callbacks.
- UI models follow versioned API DTOs and are not database-shaped.
- Authentication, feed, article, and analysis repositories remain injectable.
- Preserve the existing FrameLESS visual language; improve hierarchy and scanability rather than redesigning it.
- Reuse the existing Reader's full-text annotation, tap targeting, related-article pager, focused scrolling, and bottom-sheet gesture helpers after extracting them from the legacy `ReaderResult` contract.
- Do not pass legacy `AnalysisResult`/`ReaderResult` or database models into the news routes. Convert versioned API DTOs to immutable UI models at the repository boundary.
- Keep URL import, live-analysis loading, analysis history, and the old comparison screen outside ordinary navigation. They may remain developer-only and isolated.
- Feed, registration, publisher filtering, account controls, and news navigation are new components; do not distort legacy screens to imitate them.
- Convert API code-point offsets to Compose UTF-16 indices once in the mapper and verify the expected quote. Invalid markers are omitted safely while the article remains readable.
- Treat the current non-oscillating sheet drag implementation as the baseline and re-test middle ↔ expanded movement with long articles, horizontal paging, font scaling, and a physical device.

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
- Feed → Reader → back restores scroll position and filters
- Back closes sheet before Reader
- Ordinary users never receive URL-analysis or diagnostic navigation

### Feed

- Politics/society switching
- Publisher filter apply and cancel
- Publisher filter remembered locally
- Initial loading, refresh, pagination, empty, and retry states
- Article row with a long headline
- Simplified available/preparing/absent analysis badges

### Reader and analysis

- Article loads independently from analysis
- Analysis toggle preserves article text and scroll position
- Title and body markers open the correct finding
- One marker with multiple labels remains one target
- READY with zero findings
- Source-only finding has no empty comparison pager
- At most three publisher pages
- Same-publisher evidence stays on one page
- Attribution and publication time appear
- Middle → expanded → middle sheet movement
- Expanded article scroll does not oscillate the sheet
- Horizontal publisher paging preserves each page's vertical position

## 12. UI definition of done

The UI is complete when:

- users can register or log in and reach the all-publisher feed directly;
- users can filter politics/society and publishers without losing state;
- every visible article is readable regardless of analysis status;
- analysis can be toggled without changing article content or position;
- exact title/body markers open the correct article-specific finding;
- source-only findings end without a comparison carousel;
- valid compared publishers page horizontally and their articles scroll vertically;
- the bottom sheet moves predictably in both directions;
- original links, publisher, publication time, and attribution remain visible;
- ordinary users never encounter URL input or internal diagnostics;
- required Compose, accessibility, and end-to-end UI tests pass.
