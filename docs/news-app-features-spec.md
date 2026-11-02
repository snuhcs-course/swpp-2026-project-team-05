# FrameLESS News App Feature Expansion Specification

| Field | Value |
| --- | --- |
| Status | Ready for implementation |
| Version | 0.1 |
| Date | 2026-11-02 |
| Clients | Android and Web |
| API | Django JSON API |
| Database | Supabase PostgreSQL |
| First feature | Article search v1 |

This document defines incremental, ordinary news-app features that build on the
existing FrameLESS product. The current product and data contracts remain in
[`news-app-v1-spec.md`](news-app-v1-spec.md), and the existing Reader and feed
interactions remain in [`news-app-v1-ui-spec.md`](news-app-v1-ui-spec.md).

The goal is to add one complete vertical slice at a time without turning the
baseline specifications into a feature backlog.

## 1. Feature roadmap

| Feature | Status | Initial outcome |
| --- | --- | --- |
| Article search | Ready | Search every feed-visible article from Android and Web |
| Saved articles | Planned | Save or remove an article and browse a personal saved list |
| Reading history | Planned | Reopen recently viewed articles in last-read order |
| Sharing | Planned | Share a stable link that opens the same FrameLESS article |
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
- Back exits search and restores the previous category, publishers, feed items,
  and scroll context.
- Clearing a non-empty field returns to the ordinary feed state.

### 5.2 Request behavior

- Start automatic search after at least two normalized characters.
- Debounce typing by approximately 300 ms.
- Cancel or ignore an obsolete request when the query changes.
- Include the active category and publisher filters.
- Reset the list position and cursor when query or filters change.
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
- Changing query, category, or publishers resets the result cursor.
- Reuse existing article layouts and show the optional excerpt as secondary text.
- Provide explicit loading, no-results, error, retry, and clear-search states.
- Keep the layout usable without horizontal overflow at phone and desktop widths.

The Web query cache key includes normalized query, category, and sorted publisher
codes. Cached results from one search context must never appear under another.

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
- filter changes reset pagination;
- loading, empty, and error states render correctly;
- a result opens the existing article route.

### 7.3 Android tests

- API query encoding includes query and filters;
- ViewModel discards obsolete responses;
- clear and back restore the feed state;
- filter changes reset search pagination;
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
