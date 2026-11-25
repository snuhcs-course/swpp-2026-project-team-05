# FrameLESS News App Feature Expansion Specification

| Field | Value |
| --- | --- |
| Status | Implemented baseline |
| Version | 0.7 |
| Date | 2026-11-25 |
| Clients | Android and Web |
| API | Django JSON API |
| Database | Supabase PostgreSQL |
| Implemented slices | Search, saved articles, and following sources |

This document defines incremental, ordinary news-app features that build on the
existing FrameLESS product. The current product and data contracts remain in
[`news-app-v1.md`](../product/news-app-v1.md), and the existing Reader and feed
interactions remain in [`news-app-v1-ui.md`](../ui/news-app-v1-ui.md).

The goal is to add one complete vertical slice at a time without turning the
baseline specifications into a feature backlog.

## 1. Feature roadmap

| Feature | Status | Initial outcome |
| --- | --- | --- |
| Article search | Implemented | Search every feed-visible article from Android and Web |
| Saved articles | Implemented | Save or remove an article and browse a personal saved list |
| Following sources | Implemented | Personalize Android Home; retain Web's existing Following scope |
| Reading history | Deferred | Saved articles already cover the primary return-to-reading need |
| Sharing | Deferred | Native and browser link sharing already cover the immediate need |
| Article reactions | Implemented in Android and the shared API | Five article reactions and reaction-based Home Issue selection; see [dedicated specification](article-reactions-and-home-highlights.md) |
| Comments | Deferred | Requires moderation, reporting, blocking, and deletion policies |

Implemented rows are the current product baseline. Deferred rows are
directional notes, not authorization to build their schemas or UI in advance.
The reaction row has its own feature file for data, UI, and Home selection rules.

## 2. Article search v1

### 2.1 Product goal

A guest or signed-in user can find any article that FrameLESS currently exposes
in the ordinary news feed, including articles the client has not downloaded.
Search is server-side. Android and Web send the query and filters to Django, and
Django searches PostgreSQL before returning one bounded page.

Client-side filtering of the currently loaded 20 stories is not a valid search
implementation.

### 2.2 Searchable corpus

An article is searchable only when both conditions hold:

- `Article.visibility == VISIBLE`
- `Article.is_feed_visible == true`

The search corpus is not restricted to a recent number of days. Date range may
become an optional user filter later, but it must not be used as a substitute for
an indexed search implementation.

The searchable corpus grows with eligible imported articles; it is not capped
at the original evaluation fixture count. Comparison-support articles remain readable inside reviewed comparison
pages but do not appear in feed search. If an article later becomes an ordinary
feed story, setting `is_feed_visible=true` makes it searchable without a client
release.

### 2.3 Searchable fields

The backend matches the normalized query against:

1. article title;
2. publisher display name;
3. stored article body.

Internal cluster labels, A-G category codes, analysis explanations, user data,
and comparison-only article text are not searchable in v1.

Queries are trimmed and Unicode-normalized. Latin matching is case-insensitive.
The accepted query length after normalization is 1 to 100 characters. An absent
or empty `q` keeps the existing feed behavior; a non-empty query outside the
accepted range returns a field-specific validation error.

### 2.4 Result ordering

Search results use deterministic tiers:

1. title contains the query;
2. publisher name contains the query;
3. body contains the query.

Within one tier, newer `published_at` values appear first, followed by article ID
as the stable tie-breaker. A result that matches multiple fields uses its highest
tier.

Search results do not claim semantic relevance, factual quality, popularity, or
political balance.

### 2.5 Search excerpt

While `q` is active, every result includes a nullable plain-text
`search_excerpt`. A body match returns an excerpt centered on its first match;
a title- or publisher-only match returns `null`. The excerpt:

- is at most 180 characters;
- preserves stored article text;
- uses an ellipsis when text is omitted;
- contains no HTML;
- is `null` when no useful body excerpt exists.

Clients render the excerpt as secondary text. They must not inject markup from
the API. Query highlighting, if added later, is performed safely from plain text.

## 3. API contract

Search extends the article collection endpoint. A bearer token is optional for
`scope=ALL` and required for `scope=FOLLOWING`:

```http
GET /api/v1/articles?q=검색어&category=POLITICS&publisher=yna&limit=20&cursor=...
Authorization: Bearer <session token>  # optional for scope=ALL
```

Parameters:

| Parameter | Required | Contract |
| --- | --- | --- |
| `q` | No | Normalized search query; empty means ordinary feed |
| `category` | No | Existing `POLITICS` or `SOCIETY` filter |
| `publisher` | No | Existing repeatable publisher-code filter |
| `limit` | No | Existing range of 1 to 50; clients use 20 |
| `cursor` | No | Opaque continuation cursor issued for the same search context |

The response keeps the existing article-page shape. While `q` is active, the
page also carries the exact `total_count` for the same filters before cursor
application, and article summaries include the nullable field below:

```json
{
  "items": [
    {
      "id": "article-uuid",
      "publisher": { "code": "yna", "name": "연합뉴스", "logo_url": null },
      "title": "기사 제목",
      "category": "POLITICS",
      "published_at": "2026-11-02T10:00:00+09:00",
      "analysis_state": "READY",
      "search_excerpt": "…본문에서 검색어가 포함된 주변 문장…"
    }
  ],
  "next_cursor": "opaque-or-null",
  "total_count": 27
}
```

`total_count` is present only while `q` is active. It counts the exact searchable
corpus after visibility, category, publisher, and scope filters and before the
cursor, so it remains stable across pages of one search context. Ordinary feed
responses do not add this search-only field.

### 3.1 Cursor integrity

A search cursor is bound to a fingerprint of the normalized query, category, and
sorted publisher codes. Reusing it with a different search context returns
`INVALID_CURSOR`. The cursor also carries the match tier, publication timestamp,
and article ID needed to continue the deterministic ordering.

Changing the query or any filter always resets pagination on the client.

### 3.2 Errors

| Condition | Response |
| --- | --- |
| Missing session with `scope=ALL` | Public guest search with `is_saved=false` |
| Missing session with `scope=FOLLOWING` | Existing `401 AUTH_REQUIRED` |
| Supplied invalid or expired session | Existing `401 AUTH_REQUIRED` |
| Unsupported category or publisher | Existing `400 VALIDATION_ERROR` |
| Query longer than 100 characters | `400 VALIDATION_ERROR` with `q` field detail |
| Cursor from another query or filter set | `400 INVALID_CURSOR` |
| Temporary server failure | Existing safe server error; client offers retry |

## 4. Backend implementation boundary

Search-specific normalization, ranking, excerpt generation, and query
construction live behind a dedicated article-search function or module. API
views validate transport parameters and do not embed search rules.

The first implementation may use parameterized Django ORM substring matching
because the reviewed corpus is small. It must still:

- filter visibility before pagination;
- request only one bounded page;
- avoid loading the full corpus into application memory;
- avoid raw string-built SQL;
- keep query construction compatible with PostgreSQL and testable with SQLite;
- expose the same API contract that an indexed implementation will use later.

### 4.1 Scaling path

Search covers the full feed-visible corpus at every stage.

1. **Current corpus to early growth:** bounded Django ORM matching.
2. **Tens or hundreds of thousands of articles:** PostgreSQL `pg_trgm` GIN
   indexes over the normalized search fields, introduced after production query
   plans and latency justify the storage cost.
3. **Millions of articles or sustained high search traffic:** asynchronous
   indexing into a dedicated search service. Django remains the authorization and
   response boundary, and the client contract does not change.

Recent-day truncation is not part of this scaling strategy. Recency is a ranking
signal and may also become an explicit user-selected filter.

## 5. Android interaction

### 5.1 Entry and exit

- Home's search field and News's search action open the same search flow.
- Search has a focused field, clear action, and back action inside the existing
  root navigation shell.
- Back exits search and restores the previous News section, loaded items, and
  scroll context. Web also restores its existing feed scope while that control remains.
- An empty field shows no article list and no filter row, only `검색어를 입력하세요`.
  This applies when search opens and when a non-empty field is cleared; it makes no
  request. The ordinary feed returns when search closes.

### 5.2 Request behavior

- Start automatic search after the first normalized character.
- Debounce typing by approximately 300 ms.
- Cancel or ignore an obsolete request when the query changes.
- Search the complete Politics, Society, and Economy corpus with one uncategorized request,
  regardless of the News section selected before search. A category
  request is made only after that search filter is selected. All search requests
  use the complete `ALL` scope rather than followed publishers.
- Render a dedicated `전체 / 정치 / 사회 / 경제` filter without count badges. Each
  loaded filter owns its items, continuation cursor, error, retry, and load-more state.
- Reset the active filter to `전체` when the query changes. Closing search restores
  the originating News section, items, and scroll context.

### 5.3 States

The screen must distinguish:

- focused with an empty field;
- loading with no existing results;
- results;
- loading another page;
- no matches;
- initial-search failure with retry;
- next-page failure without discarding loaded results.

Results reuse the existing compact article row. A returned search excerpt
appears below the headline in restrained secondary text. Opening a
result uses the existing Reader and analysis flow.

A no-results state keeps the query and search field available for editing.
Do not add a separate clear/re-enter button below the message; the field's
existing clear action handles that operation.

The search field has an accessibility label, the clear and back controls have
distinct button descriptions, and IME search submits immediately.

## 6. Web interaction

- Add a compact search control above the headline list without changing the
  editorial black-and-white visual system.
- Keep the draft and active search query in local UI state. Typing must not
  rewrite the browser URL; reload starts from the ordinary feed.
- Preserve the category and existing Web feed scope in the URL, but do not use either to
  restrict an active search. Search-result links still open the stable
  article route, but v1 does not provide a shareable search-results URL.
- Debounce typing by approximately 300 ms while allowing Enter to submit
  immediately.
- Respect IME composition: do not debounce or submit an unfinished Korean input,
  and start the delay only after composition ends.
- A new query resets to the All filter. Each loaded filter paginates and retries
  independently and can be reused when the user switches back.
- Reuse existing article layouts and show the optional excerpt as secondary text;
  do not show result-count badges.
- Provide explicit loading, no-results, error, retry, and clear-search states.
- Keep the layout usable without horizontal overflow at phone and desktop widths.

The Web query cache key includes the viewer identity (`guest` or user ID),
normalized query, and
category. Cached results from one search context must never appear under another.

## 7. Verification

### 7.1 Backend tests

- guest `scope=ALL` search succeeds while Following remains authenticated;
- invalid supplied credentials are rejected rather than silently ignored;
- title, publisher, and body matches are returned;
- title matches rank above publisher and body matches;
- same-tier results are stable and newest first;
- Korean and case-insensitive Latin queries work;
- whitespace and length validation work;
- category and one or more publisher filters compose with search;
- hidden and comparison-only articles never appear;
- excerpts are bounded, plain text, and grounded in stored body text;
- pagination neither duplicates nor skips results;
- a cursor is rejected after query or filter changes;
- an absent query preserves existing feed behavior and query bounds.

### 7.2 Web tests

- the API client encodes the query safely;
- debounce sends only the current query;
- typing and Korean IME composition do not mutate the browser URL;
- reload starts from the ordinary feed rather than restoring search;
- clear returns to the feed;
- category changes reset pagination;
- loading, empty, and error states render correctly;
- a result opens the existing article route.

### 7.3 Android tests

- API query encoding includes query and category;
- ViewModel discards obsolete responses;
- clear and back restore the feed state;
- category changes reset search pagination;
- empty, initial error, and next-page error states are distinct;
- a result opens the existing Reader.

### 7.4 End-to-end acceptance

After automated tests pass:

1. deploy the Django API;
2. deploy Web;
3. search a title-only, publisher, and body-only term on Web;
4. confirm a comparison-only article cannot be found;
5. install the updated Android app on the emulator and connected phone;
6. repeat the same searches and open a result through the Reader;
7. verify comparison navigation still works from the opened article;
8. confirm no failed API requests, console errors, or Android crashes.

## 8. Search v1 non-goals

- autocomplete or query suggestions;
- recent or popular searches;
- typo correction;
- semantic or embedding-based search;
- searching analysis explanations or A-G codes;
- searching comparison-only articles;
- query highlighting with server-provided HTML;
- user-visible total-result counts or count badges;
- user-selectable relevance or date sorting;
- date-range controls;
- saving or sharing a search query.

These capabilities require a separate decision and must not be partially added
while implementing article search v1.

## 9. Saved articles v1

### 9.1 Product goal

A signed-in user can save or remove any visible article opened in the Reader
from Web or Android, then reopen it from a personal list ordered by most
recently saved.
Both clients use the same server-owned state. Saving an article never copies its
headline, body, analysis, or publisher metadata into a user record.

Saved articles v1 is a complete vertical slice, not the foundation for folders,
tags, notes, reactions, or offline write synchronization. The bounded Android
read cache is defined separately in
[`guest-offline-reading.md`](guest-offline-reading.md).

### 9.2 Eligible articles and lifecycle

An article can be newly saved only when all of the following are true:

- `Article.visibility == VISIBLE`;
- its publisher is active.

`Article.is_feed_visible` is deliberately not a saveability condition. A
comparison-support article opened from reviewed evidence is the same first-class
`Article` as a feed article and can be saved from its Reader without becoming a
feed or search result. An ineligible or unknown article uses the same not-found
response so the endpoint does not reveal unpublished data.

Removing a save is idempotent: the final state is not saved whether or not the
relationship existed. Operational article removal uses existing soft-visibility
fields. A soft-hidden article disappears from the saved list while its save row
is retained, allowing the save to return if the article is restored. Hard
deletion of an article or account cascades to its save rows in PostgreSQL.

### 9.3 Data model

The database adds one relationship model:

```text
SavedArticle
- id: UUID primary key
- user: UserAccount foreign key, CASCADE
- article: Article foreign key, CASCADE
- saved_at: creation timestamp
```

The database enforces one row per `(user, article)` and indexes
`(user, saved_at DESC, id DESC)` for the personal list. Repeating a save keeps
the existing row and timestamp. Removing and later saving again creates a new
timestamp and returns the article to the top of the list.

### 9.4 API contract

All endpoints require the existing bearer session and infer the user from it:

```http
GET    /api/v1/saved-articles?limit=20&cursor=...
PUT    /api/v1/saved-articles/{article_id}
DELETE /api/v1/saved-articles/{article_id}
Authorization: Bearer <session token>
```

`PUT` is idempotent and returns the current relationship:

```json
{
  "article_id": "article-uuid",
  "is_saved": true,
  "saved_at": "2026-11-02T17:00:00+09:00"
}
```

`DELETE` is idempotent and returns `204 No Content`. It may remove an existing
relationship even after the article becomes ineligible. A request for an
already absent relationship also returns `204`.

`GET` returns the existing bounded article-page shape with one additional
timestamp:

```json
{
  "items": [
    {
      "id": "article-uuid",
      "publisher": { "code": "yna", "name": "연합뉴스", "logo_url": null },
      "title": "기사 제목",
      "category": "POLITICS",
      "published_at": "2026-11-02T10:00:00+09:00",
      "analysis_state": "READY",
      "is_saved": true,
      "saved_at": "2026-11-02T17:00:00+09:00"
    }
  ],
  "next_cursor": "opaque-or-null"
}
```

The ordinary feed, search results, and article detail also include
`is_saved: boolean`; it is false for a guest and account-specific for an
authenticated user. Analysis comparison pages do not duplicate save state; the
ordinary article-detail request supplies it after internal navigation.

The saved list accepts only `limit` in the existing range of 1 to 50 and an
opaque cursor. It does not add category, publisher, search, or sort controls.
The signed cursor is bound to the saved-list contract and carries `saved_at` and
the relationship ID so equal timestamps paginate deterministically.

### 9.5 Consistency and query boundary

PostgreSQL uniqueness and foreign keys are the source of truth. Save uses an
atomic `get_or_create`; remove deletes by the authenticated user and article ID.
The API does not implement a second cleanup job for hard-deleted articles.

Feed, search, and detail queries compute `is_saved` with a correlated `EXISTS`
annotation or one equivalent bounded query. They must not issue one save lookup
per returned article. The saved list joins its article, publisher, and analysis
metadata in a bounded query and filters out currently ineligible articles before
pagination.

Save state is user-specific. Web query keys include the current user ID, and
personalized caches are cleared when the account changes. Android clears its
news state on logout. Neither client may briefly present one user's saved state
to another user who signs in on the same installation.

Android may retain a user-keyed, locally verified Saved read snapshot as defined
in `guest-offline-reading.md`. It is not a pending-write queue and is never
visible to a guest or another account.

If a save races with a hard article deletion, the final database state contains
no orphan relationship. The API returns either the completed save before the
cascade or a safe not-found response; an integrity failure is not exposed as an
internal stack trace.

### 9.6 Web interaction

- Add `Saved` to desktop and mobile navigation and route it at `/saved`.
- Add an accessible save/remove control only to the article Reader. Feed and
  saved-list rows remain focused on opening the article.
- The same control applies after internal navigation to a comparison-support
  article; saving it does not add it to feed or search.
- Reuse the existing article-row layout for the saved list.
- Opening a saved row records `/saved` as the Reader origin. Reader back returns
  to that saved list instead of redirecting to the category feed.
- Keep feed, Reader, and saved-list state consistent through one shared mutation
  boundary rather than separate request logic in each component.
- Disable duplicate input while the request is pending and apply the intended
  state after server confirmation; a failure leaves the visible state unchanged.
- Removing an article in the Reader updates the saved page without resetting
  its remaining scroll and pagination context.

### 9.7 Android interaction

- Saved is a permanent root destination. A guest may enter it without an
  authentication redirect; the selected tab shows a dedicated sign-in state
  with explicit `로그인` and `회원가입` actions.
- Add an accessible save/remove action only to the Reader header.
- A guest who taps that Reader action remains in the Reader and receives the
  shared contextual authentication sheet. Authentication begins only after an
  explicit action, and success does not replay the save automatically.
- Internal comparison navigation opens the same Reader and therefore reuses the
  same save/remove action and mutation path.
- Open saved articles in a separate route and ViewModel state so the originating
  discovery surface retains its section, search, rows, and scroll context.
- Reader back returns to its recorded origin from loaded, loading, and error
  states; it must not fall through to Android's app-exit behavior.
- Reuse the compact feed row and current Reader navigation.
- Apply confirmed save state consistently across discovery, Reader, and Saved. Failed
  requests are not shown optimistically or queued for later synchronization.
- Save progress and errors belong to an article ID. A pending save blocks only
  repeat taps for that article; navigating to another article keeps its action
  independent. Account changes cancel pending personal mutations and discard
  late UI updates from the previous viewer.
- When offline, show only locally complete Saved rows whose body and validated
  analysis are available; save/remove stays online-only.

### 9.8 User-visible states

Both clients distinguish:

- a guest Saved sign-in state;
- loading the initial saved list;
- a populated list;
- an empty list with a link back to the feed;
- initial failure with retry;
- loading another page;
- next-page failure without discarding loaded rows;
- a save or remove mutation in progress;
- a failed mutation whose previous state has been restored.

Save controls use distinct accessible labels such as `Save article` and
`Remove from saved articles`; the icon alone is not the accessible name.

### 9.9 Verification

Automated verification covers authentication, per-user isolation, database
uniqueness and cascades, idempotent save and removal, eligibility, deterministic
pagination, query count, client parsing, cache isolation, state synchronization,
empty and error states, and mutation rollback.

Acceptance verifies one account saving on Android and observing the same article
on Web, removing it from Web, and observing the removal on Android after refresh.
It also verifies that signing into a second account on the same client does not
show the first account's saved state.

## 10. Saved articles v1 non-goals

- folders, labels, tags, or free-form notes;
- offline copies of article text or analysis;
- automatic saving from reading history;
- saved-list search, category filters, publisher filters, or custom sorting;
- bulk save, bulk removal, import, or export;
- notifications when a saved article or analysis changes;
- likes, reactions, comments, or public save counts.

These require separate product decisions and do not add nullable columns or
placeholder endpoints to saved articles v1.

## 11. Publisher subscriptions

### 11.1 Product role

A signed-in user explicitly subscribes to publishers. User-facing Android copy
uses `구독`, `구독 중`, and `구독 관리`; existing database, API, and
code identifiers containing `follow` remain unchanged. On Android, follow state powers
the Home news section and representative selection in topic feeds; it is not a
separate `최신/팔로잉` News tab. The News tabs remain
`뉴스 | 언론사 | 정치 | 사회 | 경제`.

Following never changes stored analysis, shared propositions, comparison
selection, or the set of publishers that may appear as reviewed comparison
evidence. Search remains global and article-based.

### 11.2 Persistence and API

`UserPublisherFollow` remains unique per `(user, publisher)` and has no weight,
rank, category, notification, or recommendation columns. The existing endpoints
remain the source of truth:

```http
GET /api/v1/publishers
PUT /api/v1/me/followed-publishers
```

The replacement body is `{"publisher_codes": [...]}`. It may be empty, is
idempotent, rejects unknown or unselectable publishers, and commits atomically.

`scope=FOLLOWING` remains a server query capability. Android Home now selects
one subscribed publisher through rounded-square logos and uses its exact article
filter, rather than exposing a feed-scope selector. Web may retain its
implemented Following control until its own navigation is redesigned.

### 11.3 Android interaction

- The `언론사` News tab is the primary publisher directory and subscription
  surface. Each publisher block exposes identity, one subscription action, and
  at most three recent headlines.
- MY exposes a second `구독한 언론사 관리` entry to the same account data.
- Selecting publisher identity or `더보기` opens that publisher's ordinary
  article list.
- A signed-in row action replaces the server set atomically and shows a pending
  state only on that row. A failure restores the confirmed state and leaves an
  inline retry message.
- Other publishers remain actionable and visually unchanged. Serialize the
  existing full-set API writes, applying each publisher change to the latest
  confirmed set; repeated taps on a pending publisher do not enqueue duplicates.
  Failure affects only that publisher and does not discard queued changes to
  others. Changing accounts cancels this transient queue; it is not an offline
  synchronization mechanism.
- A guest row action opens the shared contextual authentication sheet above the
  directory. Choosing Login or Create account preserves the publisher-directory
  origin; dismissal changes nothing and success does not replay the mutation.
- Home always shows public `주요 뉴스`, followed by `구독한 언론사`.
  The latter requests articles for the selected subscribed publisher and otherwise
  shows a compact invitation to subscription management. The two requests and
  their loading/failure states are independent.
- There is no Android `최신/팔로잉` tab, dropdown, radio sheet, or empty state.

### 11.4 Consistency and states

Follow state is account-specific. Android clears personalized rows on logout or
account switch and reloads publishers after authentication. Publisher-list
loading/failure, save progress/failure, and an empty selectable list are
distinct states. Checkboxes or switches expose publisher names and checked
state to accessibility services.

Changing follows invalidates any old followed-story cursor. The client reloads
Home from the first page and never splices results from two follow sets.

### 11.5 Non-goals

- inferred subscriptions, publisher ranking, weights, or favorite tiers;
- reporter, keyword, topic, or series follows;
- publisher blocks, notifications, or public follower counts;
- using follows as an analysis-quality or political-balance signal;
- offline follow mutations or a queued synchronization system.
