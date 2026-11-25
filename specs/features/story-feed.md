# Topic-grouped story feed

| Field | Value |
| --- | --- |
| Status | Implemented |
| Version | 1.0 |
| Date | 2026-11-24 |
| API | Django JSON API |
| Initial client | Android |

## 1. Purpose

Home and the general/category news feeds show one entry per stored news topic so
that coverage of the same event does not repeat as adjacent article rows. The
Reader remains article-centered: each topic entry opens one real publisher
article, and the existing analysis flow exposes other coverage when reviewed
comparison evidence exists.

This is a discovery projection over the existing `StoryCluster` and `Article`
tables. It does not merge article records, invent a canonical article, alter
analysis, or add a topic-detail screen.

## 2. Surface boundaries

| Surface | Unit | Reason |
| --- | --- | --- |
| Home subscribed-publisher section | Article | Read the chosen subscribed outlet's latest articles |
| News `뉴스` tab | Topic | Scan mixed Politics, Society, and Economy coverage |
| News `정치`, `사회`, `경제` tabs | Topic | Apply the same deduplication inside a category |
| News `언론사` tab | Publisher, then article | Users deliberately browse one outlet |
| Search results | Article | A query may target a particular headline, outlet, or passage |
| Saved | Article | A bookmark points to the exact article the user saved |
| Reader and comparison navigation | Article | Text, offsets, links, and saved state are article-specific |

The News tab order is `전체 뉴스 | 언론사 | 정치 | 사회 | 경제`. `전체 뉴스` is an
uncategorized topic feed; it is not another name for Politics. `언론사` lists
active publishers with feed-visible articles, exposes subscription state, and opens a
publisher-specific article list.

Following is not a separate News tab on Android. The Android UI calls the
relationship `구독`; existing backend names containing `follow` remain internal
contract names. Signed-in users see subscribed-publisher coverage in Home and
manage the set from the publisher directory or MY. Web may keep its implemented
Following control until its navigation is redesigned, but it uses the same
account set.

The FrameLESS onboarding/tutorial entry appears only in the `전체 뉴스` all-topics
section, plus the explicit replay action in MY. It is not repeated in category
or publisher sections.

## 3. Database and grouping

No schema migration is required. `Article.story_cluster` is the grouping source
of truth. A visible, feed-visible article without a cluster is returned as a
singleton topic so that ingestion gaps never make readable news disappear.

Grouping is performed on the server before limiting or cursor pagination.
Grouping only a client page is invalid because the same topic could reappear on
the next page.

For each topic the API counts distinct active publishers, not article rows.
When one publisher has multiple eligible updates in a topic, only its newest
article participates in representative selection.

## 4. Representative article

Selection is deterministic and contains no request-time randomness:

1. keep visible, feed-visible articles from active publishers;
2. reduce candidates to the newest article per publisher;
3. if the signed-in viewer follows any candidate publishers, restrict the pool
   to those publishers;
4. otherwise retain all candidates;
5. choose the lowest SHA-256 score of `story_key`, viewer key (`user.id` or
   `anonymous`), and publisher code.

The same viewer therefore sees a stable representative across refresh and
pagination, while different topics distribute representation across eligible
outlets. Changing the followed set may legitimately change the representative.
Selection is a display rule, not an editorial quality or political-balance
claim.

For `scope=FOLLOWING`, a topic is eligible only when at least one followed
publisher covers it, and its representative is selected only from followed
publishers. Coverage metadata may still name every visible publisher in the
topic so the Reader's wider comparison context is not hidden.

## 5. API

```http
GET /api/v1/stories?category=POLITICS&scope=ALL&limit=20&cursor=...
Authorization: Bearer <optional for ALL; required for FOLLOWING>
```

Parameters:

| Name | Contract |
| --- | --- |
| `category` | Optional `POLITICS`, `SOCIETY`, or `ECONOMY`; absent means all |
| `scope` | `ALL` by default, or authenticated `FOLLOWING` |
| `limit` | 1–50, default 20 |
| `cursor` | Opaque cursor issued for the same viewer context |

Response:

```json
{
  "items": [
    {
      "story_key": "cluster:uuid-or-singleton-key",
      "representative_article": {
        "id": "article-uuid",
        "publisher": {"code": "yna", "name": "연합뉴스", "logo_url": null},
        "title": "기사 제목",
        "category": "POLITICS",
        "published_at": "2026-11-24T09:00:00+09:00",
        "analysis_state": "READY",
        "is_saved": false
      },
      "publisher_count": 4,
      "publishers": [
        {"code": "yna", "name": "연합뉴스", "logo_url": null}
      ],
      "latest_published_at": "2026-11-24T09:05:00+09:00"
    }
  ],
  "next_cursor": "opaque-or-null"
}
```

Topics order by `(latest_published_at DESC, internal_story_key ASC)`. The cursor
binds category, scope, viewer identity, and the sorted followed-publisher set.
Changing any bound value invalidates the cursor with `INVALID_CURSOR` rather
than returning a page with gaps or duplicates.

The existing `/api/v1/articles` contract remains article-based and gains no
grouping behavior. Its repeatable `publisher` filter is used for publisher
pages.

### 5.1 Android deployment compatibility

The Android client may be installed before the grouped-story route reaches the
production server. When `/api/v1/stories` is absent and returns a non-JSON
400/404/405 response, Android retries the equivalent `/api/v1/articles`
request and projects each article as a one-publisher story. This keeps Home and
News readable without inventing client-side grouping. A structured API error,
authentication failure, timeout, or other network error is never hidden by the
fallback. Once `/stories` is deployed, the grouped response is used without an
app update.

## 6. Android presentation and navigation

A topic row shows the representative headline, representative publisher,
and restrained coverage metadata such as `외 3개 언론사`. Android list rows omit
publication/relative time; the API still carries it for ordering and Reader.
It never shows an internal cluster ID, raw analysis state, A–G code, generated
summary, ranking, or bias/truth label.

Tapping the row opens `representative_article.id` in the existing Reader.
Reader-to-comparison-to-Reader navigation and save state remain unchanged.
Back restores the originating Home or News tab, selected section, loaded topic
pages, and scroll position.

Home keeps its portal utilities above two independent news sections:

- `주요 뉴스` always requests `scope=ALL`;
- `구독한 언론사` follows directly below it, with a horizontal row of rounded-square
  publisher logos and short names. Reuse subscription management's logo tile
  without a second outer frame. Select the first subscribed publisher by
  default; choosing another changes only the articles directly below the row;
- query the ordinary article endpoint with the chosen publisher filter. Never
  filter only the already-loaded public page or fetch every outlet eagerly;
- guest or empty follow set: retain the second section with a compact invitation
  to manage subscriptions; do not issue an authenticated request;
- the two sections have independent loading, error, and retry states. A
  subscription change invalidates and reloads the subscribed section;
- offline: retain the existing locally complete Saved fallback; do not pretend
  it is a live topic feed.

The current selection survives Reader navigation and Home/News tab switches.
The chosen publisher's first page is cached in memory for this viewer so returning
to a recently selected outlet is immediate. Refresh or subscription changes clear
that cache. Cancelling or finishing an older request must not replace the latest
selection. Removing the selected subscription selects the first remaining one.
Viewer changes cancel personalized requests and clear selection and cache.

The publisher directory is publisher-first. Each block shows publisher identity,
one `구독`/`구독 중` action, and at most three recent article headlines.
Selecting the identity or `더보기` opens the ordinary publisher article list;
it never filters only the articles already loaded on the device. The directory
supports loading, failure, empty, subscription-save, and subscription-save-error
states.

A guest who selects `구독` remains on the directory and receives the shared
contextual authentication sheet. Login or registration starts only after an
explicit choice. Dismissing the sheet does not mutate state, and successful
authentication does not automatically replay the subscription.

## 7. Non-goals

- automatic clustering or analysis generation;
- a topic landing page, topic follow, or topic notification;
- popularity ordering inside ordinary story feeds (the separate Home
  section is specified in [Recent-read trends](realtime-trends.md));
- engagement recommendation or political-balance ranking;
- client-side title similarity or page-local deduplication;
- live weather or market-data integration;
- changing Web navigation in the initial Android slice.

