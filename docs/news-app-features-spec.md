# FrameLESS News App Feature Expansion Specification

| Field | Value |
| --- | --- |
| Status | Ready for implementation |
| Version | 0.3 |
| Date | 2026-11-02 |
| Clients | Android and Web |
| API | Django JSON API |
| Database | Supabase PostgreSQL |
| Current feature | Following sources v1 |

This document defines incremental, ordinary news-app features that build on the
existing FrameLESS product. The current product and data contracts remain in
[`news-app-v1-spec.md`](news-app-v1-spec.md), and the existing Reader and feed
interactions remain in [`news-app-v1-ui-spec.md`](news-app-v1-ui-spec.md).

The goal is to add one complete vertical slice at a time without turning the
baseline specifications into a feature backlog.

## 1. Feature roadmap

| Feature | Status | Initial outcome |
| --- | --- | --- |
| Article search | Implemented | Search every feed-visible article from Android and Web |
| Saved articles | Implemented | Save or remove an article and browse a personal saved list |
| Following sources | Ready | Keep Latest diverse while offering a separate feed from explicitly followed publishers |
| Reading history | Deferred | Saved articles already cover the primary return-to-reading need |
| Sharing | Deferred | Native and browser link sharing already cover the immediate need |
| Likes or reactions | Deferred | Add only after defining what a reaction means for a news product |
| Comments | Deferred | Requires moderation, reporting, blocking, and deletion policies |

Only the feature marked `Ready` is part of the next implementation. Planned and
deferred rows are directional notes, not authorization to build their schemas or
UI in advance.

## 2. Article search v1

### 2.1 Product goal

A signed-in user can find any article that FrameLESS currently exposes in the
ordinary news feed, including articles the client has not downloaded. Search is
server-side. Android and Web send the query and filters to Django, and Django
searches PostgreSQL before returning one bounded page.

Client-side filtering of the currently loaded 20 stories is not a valid search
implementation.

### 2.2 Searchable corpus

An article is searchable only when both conditions hold:

- `Article.visibility == VISIBLE`
- `Article.is_feed_visible == true`

The search corpus is not restricted to a recent number of days. Date range may
become an optional user filter later, but it must not be used as a substitute for
an indexed search implementation.

The current reviewed dataset therefore exposes 30 searchable source stories.
The 148 comparison-support articles remain readable inside reviewed comparison
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
The accepted query length after normalization is 2 to 100 characters. An absent
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

Search extends the existing authenticated article collection endpoint:

```http
GET /api/v1/articles?q=검색어&category=POLITICS&publisher=yna&limit=20&cursor=...
Authorization: Bearer <session token>
```

Parameters:

| Parameter | Required | Contract |
| --- | --- | --- |
| `q` | No | Normalized search query; empty means ordinary feed |
| `category` | No | Existing `POLITICS` or `SOCIETY` filter |
| `publisher` | No | Existing repeatable publisher-code filter |
| `limit` | No | Existing range of 1 to 50; clients use 20 |
| `cursor` | No | Opaque continuation cursor issued for the same search context |

The response keeps the existing article-page shape. While `q` is active, article
summaries include the nullable field below:

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
  "next_cursor": "opaque-or-null"
}
```

The API does not return an exact total count in v1. Counting the full result set
on every keystroke adds cost without improving the main search flow. Clients show
the loaded results and whether another page is available.

### 3.1 Cursor integrity

A search cursor is bound to a fingerprint of the normalized query, category, and
sorted publisher codes. Reusing it with a different search context returns
`INVALID_CURSOR`. The cursor also carries the match tier, publication timestamp,
and article ID needed to continue the deterministic ordering.

Changing the query or any filter always resets pagination on the client.

### 3.2 Errors

| Condition | Response |
| --- | --- |
| Missing or expired session | Existing `401 AUTH_REQUIRED` |
| Unsupported category or publisher | Existing `400 VALIDATION_ERROR` |
| Query shorter than 2 or longer than 100 characters | `400 VALIDATION_ERROR` with `q` field detail |
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

- Add a search icon beside the account action in the feed header.
- Tapping it replaces the header controls with a focused search field, clear
  action, and back action.
- Back exits search and restores the previous category, feed scope, feed items,
  and scroll context.
- Clearing a non-empty field returns to the ordinary feed state.

### 5.2 Request behavior

- Start automatic search after at least two normalized characters.
- Debounce typing by approximately 300 ms.
- Cancel or ignore an obsolete request when the query changes.
- Include the active category. Search uses the complete `ALL` feed scope rather
  than silently restricting results to followed publishers.
- Reset the list position and cursor when query or category changes.
- Continue using server cursor pagination near the end of the list.

### 5.3 States

The screen must distinguish:

- focused but not yet searchable;
- loading with no existing results;
- results;
- loading another page;
- no matches;
- initial-search failure with retry;
- next-page failure without discarding loaded results.

Results reuse the existing compact article row. A returned search excerpt appears
below the headline in restrained secondary text. Opening a result uses the
existing Reader and analysis flow.

The search field has an accessibility label, the clear and back controls have
distinct button descriptions, and IME search submits immediately.

## 6. Web interaction

- Add a compact search control above the headline list without changing the
  editorial black-and-white visual system.
- Keep the draft and active search query in local UI state. Typing must not
  rewrite the browser URL; reload starts from the ordinary feed.
- Preserve the category in the URL. Search-result links still open the stable
  article route, but v1 does not provide a shareable search-results URL.
- Debounce typing by approximately 300 ms while allowing Enter to submit
  immediately.
- Respect IME composition: do not debounce or submit an unfinished Korean input,
  and start the delay only after composition ends.
- Changing query or category resets the result cursor.
- Reuse existing article layouts and show the optional excerpt as secondary text.
- Provide explicit loading, no-results, error, retry, and clear-search states.
- Keep the layout usable without horizontal overflow at phone and desktop widths.

The Web query cache key includes the current user, normalized query, and
category. Cached results from one search context must never appear under another.

## 7. Verification

### 7.1 Backend tests

- authentication remains mandatory;
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
- exact total-result counts;
- user-selectable relevance or date sorting;
- date-range controls;
- saving or sharing a search query.

These capabilities require a separate decision and must not be partially added
while implementing article search v1.

## 9. Saved articles v1

### 9.1 Product goal

A signed-in user can save or remove an ordinary feed article from Web or
Android, then reopen it from a personal list ordered by most recently saved.
Both clients use the same server-owned state. Saving an article never copies its
headline, body, analysis, or publisher metadata into a user record.

Saved articles v1 is a complete vertical slice, not the foundation for folders,
tags, notes, reactions, or offline article storage.

### 9.2 Eligible articles and lifecycle

An article can be newly saved only when all of the following are true:

- `Article.visibility == VISIBLE`;
- `Article.is_feed_visible == true`;
- its publisher is active.

Comparison-support articles are not independently saveable in v1. An
ineligible or unknown article uses the same not-found response so the endpoint
does not reveal unpublished data.

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
`is_saved: boolean` for the authenticated user. Analysis comparison pages do not
include save state because their supporting articles are outside the v1 saveable
corpus.

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

If a save races with a hard article deletion, the final database state contains
no orphan relationship. The API returns either the completed save before the
cascade or a safe not-found response; an integrity failure is not exposed as an
internal stack trace.

### 9.6 Web interaction

- Add `Saved` to desktop and mobile navigation and route it at `/saved`.
- Add an accessible save/remove control only to the article Reader. Feed and
  saved-list rows remain focused on opening the article.
- Reuse the existing article-row layout for the saved list.
- Opening a saved row records `/saved` as the Reader origin. Reader back returns
  to that saved list instead of redirecting to the category feed.
- Keep feed, Reader, and saved-list state consistent through one shared mutation
  boundary rather than separate request logic in each component.
- Apply the intended state immediately; disable duplicate input while the
  request is pending and roll back with an inline error if it fails.
- Removing an article in the Reader updates the saved page without resetting
  its remaining scroll and pagination context.

### 9.7 Android interaction

- Add a saved-articles action beside search in the feed header.
- Add an accessible save/remove action only to the Reader header.
- Open saved articles in a separate route and ViewModel state so the ordinary
  feed retains its category, feed scope, search, rows, and scroll context.
- Reader back returns to its recorded origin from loaded, loading, and error
  states; it must not fall through to Android's app-exit behavior.
- Reuse the compact feed row and current Reader navigation.
- Apply and roll back save state consistently across feed, Reader, and saved
  list. Failed requests are not queued for later offline synchronization.

### 9.8 User-visible states

Both clients distinguish:

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
- saving comparison-support articles;
- saved-list search, category filters, publisher filters, or custom sorting;
- bulk save, bulk removal, import, or export;
- notifications when a saved article or analysis changes;
- likes, reactions, comments, or public save counts.

These require separate product decisions and do not add nullable columns or
placeholder endpoints to saved articles v1.

## 11. Following sources v1

### 11.1 Product goal and FrameLESS invariants

A signed-in user can explicitly follow publishers and open a separate
`Following` feed containing their articles. The ordinary `Latest` feed remains
the default and continues to mix every eligible publisher by recency.

Following is a convenience for article discovery, not a recommendation signal
and not an analysis input. It must preserve these FrameLESS invariants:

- `Latest` is never narrowed, ranked, or personalized by follows;
- entering the app or a category starts in `Latest`, not `Following`;
- article rows remain article-first and identify their publisher without
  grouping the feed into publisher editions or story-cluster cards;
- opening any article uses the same Reader and expression-analysis flow;
- a Reader analysis may compare against every eligible article in its story
  cluster, including publishers the user does not follow;
- follow state never changes finding generation, shared propositions,
  comparison selection, analysis quality, or stored editorial data;
- search remains global across the complete feed-visible corpus and restores
  the prior feed scope when closed.

Following therefore gives the user control without making FrameLESS reinforce a
single-source information bubble or hide the cross-outlet evidence that explains
why a phrase was highlighted.

### 11.2 Feed information architecture

Both clients expose two explicit feed scopes:

| Scope | Meaning | Default |
| --- | --- | --- |
| `Latest` | Every eligible feed article, newest first | Yes |
| `Following` | Eligible articles from explicitly followed publishers, newest first | No |

Politics and Society remain the primary category navigation. Feed scope is
secondary and does not create a new route hierarchy. Switching category or
scope resets pagination and list position. Returning from Reader restores the
previous category, scope, loaded rows, and scroll context.

The existing transient multi-publisher filter is removed from the primary UI so
that users do not have to distinguish temporary source filters from durable
follows. The repeatable `publisher` API parameter remains supported for contract
compatibility and internal use.

One article remains one feed row even when several feed-visible articles share
a story cluster. V1 does not select a canonical article or render several
publisher headlines inside one feed card. Cross-outlet detail belongs in the
Reader analysis.

### 11.3 Followable publisher lifecycle

A publisher is selectable when it is active and has at least one visible,
feed-visible article. The publisher chooser uses the existing editorial
`display_order` and then its stable name/code ordering.

If a followed publisher becomes inactive or loses all eligible feed articles,
it does not appear in the Following feed or active chooser. Existing relationship
rows may remain until the user next replaces the set or the publisher is hard
deleted. Re-enabling an unchanged relationship makes it visible again. Hard
deletion of an account or publisher cascades its follow relationships.

Following zero publishers is valid. It produces a dedicated empty state rather
than falling back to Latest, because silently changing scope would misrepresent
the user's selection.

### 11.4 Data model

The database adds one relationship model:

```text
UserPublisherFollow
- id: UUID primary key
- user: UserAccount foreign key, CASCADE
- publisher: Publisher foreign key, CASCADE
- followed_at: creation timestamp
```

PostgreSQL enforces one row per `(user, publisher)`. No rank, weight, category,
notification flag, recommendation score, or ordering column is added. Publisher
display order remains editorial metadata on `Publisher`.

### 11.5 API contract

All operations require the existing bearer session and infer the user from it.

The authenticated publisher collection adds user-specific follow state:

```http
GET /api/v1/publishers
```

```json
{
  "items": [
    {
      "code": "yna",
      "name": "연합뉴스",
      "logo_url": null,
      "is_followed": true
    }
  ]
}
```

The user replaces the selectable follow set atomically:

```http
PUT /api/v1/me/followed-publishers
Content-Type: application/json

{"publisher_codes": ["yna", "hani"]}
```

The array is required, contains unique publisher codes, and may be empty. The
server normalizes duplicate inputs, rejects unknown or currently unselectable
codes with `400 VALIDATION_ERROR`, locks the user preference boundary, and
performs one transaction. Repeating the same set is idempotent. The response is:

```json
{"publisher_codes": ["yna", "hani"]}
```

The article collection adds one optional scope:

```http
GET /api/v1/articles?category=POLITICS&scope=FOLLOWING&limit=20&cursor=...
```

`scope` is `ALL` or `FOLLOWING` and defaults to `ALL`. `FOLLOWING` intersects
the ordinary visibility, active-publisher, category, search, and legacy
repeatable `publisher` filters. An empty follow set returns an empty page with a
null cursor, not an error.

### 11.6 Pagination, consistency, and query boundary

A feed cursor is bound to scope and, for `FOLLOWING`, a stable fingerprint of
the authenticated user's current followed publisher codes. Changing follows
invalidates an old Following cursor with `INVALID_CURSOR`; the client reloads
from the first page. This prevents duplicates or gaps when the filter changes
mid-pagination.

Publisher listing and Following pages use bounded queries and must not perform
one follow lookup per publisher or article. PostgreSQL uniqueness is the source
of truth. Concurrent full-set updates use last committed request wins semantics
without exposing partial sets.

Follow state is account-specific. Web query keys include user ID and Android
clears personalized state on logout. Neither client may render a previous
user's follows or Following rows after an account switch on the same device.

### 11.7 Web interaction

- Place compact `Latest` and `Following` controls above the existing article
  list; `Latest` remains visually and behaviorally primary.
- Keep Politics and Society navigation unchanged.
- Replace the persistent publisher-checkbox sidebar with a restrained
  `Manage sources` action that opens a dialog or drawer using the existing
  publisher list and checkbox vocabulary.
- Changes remain a local draft until `Save`; `Cancel` discards them.
- Disable duplicate submission while saving. On failure, retain the prior
  server set and show an inline retryable error without closing the editor.
- Article rows keep publisher, publication time, headline, and analysis state.
  They do not add follow, save, reaction, or comparison controls.
- Closing global search restores the previous Latest or Following feed and
  scroll context.

The layout preserves the existing editorial black-and-white design and remains
usable at phone and desktop widths without adding a separate settings page.

### 11.8 Android interaction

- Reuse the existing compact source control beside the Politics and Society
  tabs to expose `Latest` and `Following` through a bottom sheet.
- The bottom sheet also exposes `Manage followed sources`; editing reuses the
  current checkbox and explicit apply pattern.
- Do not add a new bottom navigation destination or onboarding flow.
- Returning from Reader restores the selected scope, list rows, pagination, and
  scroll position in loaded, loading, and error states.
- Server follow state replaces the current device-local publisher selection.
  Existing local publisher codes are not silently uploaded to an account.
- The category and analysis-visibility preferences may remain device-local.

### 11.9 User-visible states and accessibility

Both clients distinguish:

- publisher list loading and failure;
- follow-set save in progress and failure;
- Following with no followed publishers;
- Following with followed publishers but no article in the current category;
- Following initial loading, populated list, pagination, and retry;
- an old cursor invalidated after the follow set changes.

The two empty states have different actions: `Manage sources` for no follows,
and `View latest coverage` when the current category has no matching article.
Scope controls expose selected state to assistive technology; checkboxes use
publisher names; Save, Cancel, retry, and scope-change actions have distinct
accessible names.

### 11.10 Verification

Automated verification covers authentication, relationship uniqueness and
cascades, full-set replacement, validation, user isolation, source lifecycle,
scope filtering, cursor invalidation, bounded queries, client draft/apply/error
behavior, account cache isolation, and navigation-state restoration.

Cross-client acceptance sets follows on Web, observes the same Following feed on
Android, changes the set on Android, and observes the update on Web. It also
confirms that an unfollowed publisher still appears as comparison evidence in a
followed article's Reader when the stored analysis requires it.

## 12. Following sources v1 non-goals

- ranking, boosting, or recommending followed publishers inside Latest;
- using follows as an analysis, clustering, or comparison-selection signal;
- topic, reporter, series, keyword, or story-cluster follows;
- publisher-specific home pages or publisher-curated editions;
- per-category follow sets, custom source order, weights, or favorites tiers;
- publisher blocks, mutes, notifications, or public follower counts;
- automatic follows inferred from reading behavior;
- a canonical article or clustered multi-headline feed card;
- a separate account-settings page or forced onboarding;
- offline synchronization or queued preference changes.

These require separate product decisions. Following v1 adds only the explicit
user-publisher relationship and the alternate discovery scope described above.
