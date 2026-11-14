# Guest and Offline Reading Specification

| Field | Value |
| --- | --- |
| Status | Draft for team review |
| Version | 0.1 |
| Date | 2026-11-14 |
| Clients | Android and Web guest access; Android offline cache |
| API | Existing Django JSON API with optional authentication on public reads |
| Server database | Existing Supabase PostgreSQL schema; no new server tables |
| Local database | Android Room |

This specification extends the implemented news-app baseline in
[`../product/news-app-v1.md`](../product/news-app-v1.md), the UI contract in
[`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md), and the account-backed
Saved and Following features in
[`news-app-features.md`](news-app-features.md). It deliberately keeps the
extension small: public reading no longer requires an account, and Android
retains a bounded read cache for previously fetched content.

The server remains the source of truth. Room is a client read cache, not a
second news database or a general synchronization engine.

## 1. Goals and boundaries

### 1.1 Goals

- Let a person browse, search, read, and inspect stored analysis on Android and
  Web without first registering or signing in.
- Keep Following, source management, Saved mutations, and the Saved route
  account-specific.
- Let Android immediately show previously fetched Latest rows and reopen
  locally available articles and analysis during a network failure.
- Let an authenticated Android user inspect the last locally verified Saved
  list while offline.
- Preserve strict account isolation and the existing server-owned Saved and
  Following contracts.

### 1.2 Non-goals

- Server-side guest accounts, anonymous bearer tokens, installation IDs, or a
  guest-to-account merge API.
- Guest bookmarks or local bookmarks that later merge into an account.
- Offline Saved or Following writes, a pending-operation queue, conflict
  resolution, or WorkManager-based background synchronization.
- Offline search, full offline Following, automatic bulk article download, or a
  user-facing download manager.
- Web IndexedDB persistence or service-worker offline support.
- A second backend cache, new PostgreSQL tables, or changes to article-analysis
  domain relationships.

## 2. Viewer capabilities

| Capability | Guest | Authenticated |
| --- | ---: | ---: |
| Latest feed and category navigation | Yes | Yes |
| Global article search | Yes | Yes |
| Article detail and stored analysis | Yes | Yes |
| Comparison evidence and original links | Yes | Yes |
| Following feed | Sign-in prompt | Yes |
| Publisher follow management | Sign-in prompt | Yes |
| Saved route and save/remove mutations | Sign-in prompt | Yes |
| Registration, login, and logout | Register or sign in | Logout |

`Guest` is a client state with no bearer token. The backend does not create or
persist a guest identity. Public data returned to a guest always uses
`is_saved=false` and `is_followed=false` so the existing response shapes remain
stable.

## 3. Authentication state and transitions

Clients expose three top-level authentication states:

```text
Restoring
├─ valid stored session       → Authenticated(session)
├─ no stored session          → Guest
├─ confirmed expired session  → Guest + sign-in notice
└─ temporary network failure  → Authenticated(stored session, unverified)
```

An offline startup must not erase an otherwise unexpired local session merely
because `/auth/me` cannot be reached. The client checks the stored `expires_at`
locally. A session past that time is not used. A server `401 AUTH_REQUIRED`
confirms that an apparently unexpired token is invalid and clears it.

Logging out clears the bearer token, in-memory personalized state, and that
user's local Saved relationships. Public article and analysis cache rows remain
available. If the current route is public, logout keeps the user in that route;
Following falls back to Latest and Saved returns to the feed.

When a guest selects Saved, Following, source management, or a save control, the
client presents one shared sign-in explanation. Successful login returns to the
same public route. It does not automatically replay the protected mutation; the
user confirms it by selecting the action again.

## 4. Public and authenticated API contract

No server model or migration is required for guest access.

### 4.1 Public reads

The following endpoints accept either no `Authorization` header or a valid
bearer session:

```http
GET /api/v1/publishers
GET /api/v1/articles?scope=ALL&...
GET /api/v1/articles?q=...&scope=ALL&...
GET /api/v1/articles/{article_id}
GET /api/v1/articles/{article_id}/analysis
```

With a valid bearer session, responses include that user's `is_saved` and
`is_followed` values. Without a bearer session, these values are `false`.

### 4.2 Authenticated operations

The following remain authentication-required:

```http
GET /api/v1/auth/me
GET /api/v1/articles?scope=FOLLOWING&...
PUT /api/v1/me/followed-publishers
GET /api/v1/saved-articles
PUT /api/v1/saved-articles/{article_id}
DELETE /api/v1/saved-articles/{article_id}
```

Registration, login, and idempotent logout keep their existing contracts.

### 4.3 Missing and invalid credentials

- No bearer header on a public read means guest access.
- A valid bearer header enables personalization.
- A malformed, expired, or unknown bearer token returns `401 AUTH_REQUIRED`;
  it is never silently downgraded to a guest request.
- After a public read receives `401`, a client may clear the invalid local
  session and retry that same idempotent GET once without credentials.
- A protected read or mutation is never retried as a guest operation.

Responses whose personalized flags vary by credentials include
`Vary: Authorization` and are not stored in a shared cache that could leak one
user's state to another.

### 4.4 Article and analysis identity

Article detail adds the immutable article `content_hash`. Article analysis adds
the same value as `source_content_hash`:

```json
{
  "id": "article-uuid",
  "content_hash": "sha256-hex",
  "body": "..."
}
```

```json
{
  "state": "READY",
  "version": "reviewed-version",
  "source_content_hash": "sha256-hex",
  "findings": []
}
```

These fields let Android reject a cached analysis whose offsets target a
different article body. They expose no new secret and require no database
change because `Article.content_hash` already exists.

## 5. Android local data model

Android adds one Room database with three tables. Room entities remain local
storage models and do not leak into API DTOs or Compose UI models.

### 5.1 `cached_article`

| Column | Contract |
| --- | --- |
| `article_id` | Server article UUID; primary key |
| `publisher_code`, `publisher_name`, `publisher_logo_url` | Offline row metadata |
| `category`, `title`, `published_at`, `analysis_state` | Feed metadata |
| `body_text`, `original_url`, `content_hash` | Nullable until detail is fetched |
| `summary_cached_at`, `detail_cached_at`, `last_opened_at` | Cache provenance and retention |

Feed responses upsert summary columns. Detail responses update the same row
without creating a duplicate article. Personalized fields such as `is_saved`
and `is_followed` are not stored in this table.

### 5.2 `cached_analysis`

| Column | Contract |
| --- | --- |
| `article_id` | Primary key and reference to `cached_article` |
| `payload_json` | Validated article-analysis response |
| `analysis_version` | Server analysis version |
| `source_content_hash` | Body identity required for reuse |
| `cached_at` | Last successful write time |

The payload remains article-shaped JSON. The local client has no requirement to
query individual findings or comparisons through SQL, so duplicating the server
analysis schema in Room would add complexity without product value.

Before writing, Android parses the response and applies the existing exact-text
and offset validation. A malformed response does not overwrite a previously
valid cache row. Before reading, `source_content_hash` must equal the associated
article's `content_hash`; otherwise the article remains readable but the cached
analysis is ignored.

### 5.3 `cached_saved_article`

| Column | Contract |
| --- | --- |
| `user_id` | Authenticated account UUID |
| `article_id` | Reference to `cached_article` |
| `saved_at` | Server relationship timestamp |
| `last_verified_at` | Last successful Saved-list or mutation confirmation |

The composite primary key is `(user_id, article_id)`. Queries always require the
active session's user ID. Guest UI and another account cannot read these rows.

This table is a cached server relationship, not a pending-write log. It never
contains an unconfirmed local save or removal.

### 5.4 Room lifecycle

- Start at database version 1 with exported schemas committed to the repository.
- Do not use destructive migration fallback in production.
- Add explicit migrations for later versions.
- Do not encrypt this database: it stores public news content and opaque account
  UUID relationships, while the bearer token remains in encrypted preferences.
- Do not add WorkManager or a connectivity observer for this slice. Repository
  requests fail normally and fall back to Room.

## 6. Repository read and refresh policy

The repository is the only layer that coordinates network and Room. UI and
ViewModels do not call a DAO or `NewsService` directly.

### 6.1 Latest feed

1. Read cached summaries for the selected category and show them immediately
   when available.
2. Request the server first page.
3. On success, replace the visible in-memory page and upsert its summaries.
4. On failure, keep cached rows and expose a non-blocking offline state.
5. When neither source has rows, expose the ordinary retryable error state.

Loaded online continuation pages are also upserted. Offline mode does not
simulate server cursor pagination; it shows the bounded locally known set.

### 6.2 Search and Following

Search remains server-wide and Following remains account-specific, so neither
falls back to a partial local query. When offline, the client keeps any already
visible in-memory rows but explains that refreshing or starting a new request
requires a connection.

### 6.3 Reader and analysis

1. Read cached article detail and matching cached analysis.
2. Show valid cached content immediately.
3. Request article detail and analysis from the server when possible.
4. Validate and persist each successful response; do not replace valid cached
   data with malformed or failed responses.
5. If only the article is cached, keep it readable and show analysis as
   unavailable offline.
6. If no detail is cached and the network fails, show a retryable Reader error.

Original publisher links may still fail in the external browser while offline;
FrameLESS does not mirror publisher sites.

### 6.4 Saved list and mutations

For an authenticated user, the Saved route first reads that user's locally
verified relationships joined with cached article summaries, then refreshes the
server list.

- A successful first-page refresh transaction replaces that user's cached
  Saved relationships with the returned first page. Successfully loaded later
  pages append to this verified prefix.
- A failed refresh preserves the previous local snapshot.
- A successful save inserts or updates the local relationship.
- A successful removal deletes the local relationship.
- An offline or failed save/remove leaves both UI and Room unchanged and shows
  a connection error. The operation is not queued.
- A Saved row with cached detail opens offline. A row with summary only remains
  visible but explains that it must be opened once online before offline use.

The cached list is the locally verified prefix, not a claim that every server
bookmark has been downloaded. Saving from the current Reader normally leaves
that article available because its detail and analysis have already been
fetched.

## 7. Retention and deletion

- Keep up to 100 non-Saved article summaries ordered by recent cache use.
- Keep full detail and analysis for the 20 most recently opened articles.
- Keep up to 200 locally verified Saved relationships per user.
- Do not delete an article summary while a retained Saved relationship refers
  to it.
- Saved status does not exempt large body and analysis payloads from the
  20-article detail limit.
- Run bounded cleanup after successful cache writes; do not schedule periodic
  background work.
- Explicit logout deletes the current user's cached Saved relationships but
  retains public article and analysis rows.
- A confirmed `NOT_FOUND` for an article removes its local detail, analysis, and
  Saved relationship for the active user.

## 8. User-visible states

### 8.1 Guest

- Launch directly into Latest rather than Login.
- Show `로그인` in the account action.
- Keep Saved, Following, source management, and save controls discoverable; a
  selection opens the shared sign-in explanation rather than failing silently.
- Login and registration return to the prior public route.

### 8.2 Offline Android

- Cached content remains primary; a small notice states that a saved copy is
  being shown.
- Do not replace readable cached content with a full-screen network error.
- Distinguish `no local copy` from `server returned no results`.
- A Saved summary without local detail explains that the article must be opened
  online once; it does not disappear from the list.
- Disabled online-only mutations explain why they cannot complete.

Web has the same guest and protected-action behavior but no persistent offline
contract in this version.

## 9. Security and data isolation

- Optional authentication is decided on the server, not only by hiding client
  controls.
- An invalid supplied token never becomes anonymous access silently.
- Room Saved queries are always keyed by the active `user_id`.
- Account change clears personalized in-memory state before new queries run.
- Explicit logout removes local Saved relationships for that account.
- Raw bearer tokens, passwords, database URLs, full API logs, and credentials
  never enter Room or application logs.
- Full stored article display remains an internal-prototype policy and still
  requires publisher/legal review before external release.

## 10. Definition of done

This slice is complete when:

- Android and Web can reach feed, search, Reader, and stored analysis without an
  account;
- protected controls consistently request sign-in and return to the same public
  route;
- authenticated Saved and Following behavior remains account-isolated;
- Android opens cached Latest, Reader, analysis, and a locally verified Saved
  snapshot during a network failure;
- offline Saved writes are rejected without optimistic or queued state;
- content-hash mismatch and malformed analysis never produce a wrong highlight;
- Room schema export, retention, logout, and account-switch behavior are tested;
- no guest server model, backend migration, write queue, or background sync
  subsystem is introduced.
