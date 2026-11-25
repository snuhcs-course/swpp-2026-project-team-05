# Guest and Offline Reading Specification

| Field | Value |
| --- | --- |
| Status | Implemented |
| Version | 1.2 |
| Date | 2026-11-24 |
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
retains a bounded read cache for explicitly Saved content.

The server remains the source of truth. Room is a client read cache, not a
second news database or a general synchronization engine.

## 1. Goals and boundaries

### 1.1 Goals

- Let a person browse, search, read, and inspect stored analysis on Android and
  Web without first registering or signing in.
- Keep Following, source management, Saved mutations, and the Saved route
  account-specific.
- Let an authenticated Android user reopen Saved articles whose body and
  verified analysis are complete on that installation during a network failure.
- Keep the offline surface honest: never show a headline that cannot open into
  the same Reader experience.
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
| Home and topic/category navigation | Yes | Yes |
| Global article search | Yes | Yes |
| Article detail and stored analysis | Yes | Yes |
| Comparison evidence and original links | Yes | Yes |
| Subscribed Home feed | Public fallback | Yes |
| Publisher subscription mutation | Contextual auth sheet | Yes |
| Saved route | In-place sign-in state | Yes |
| Save/remove mutation | Contextual auth sheet | Yes |
| Registration, login, and logout | Register or sign in | Logout |

`Guest` is a client state with no bearer token. The backend does not create or
persist a guest identity. Public data returned to a guest always uses
`is_saved=false` and `is_followed=false` so the existing response shapes remain
stable.

Separate extension: [Recent-read trends](realtime-trends.md) adds a local
random guest UUID and short-lived derived read-count keys solely for analytics.
This is an explicit extension of the no-installation-ID boundary above,
not a guest account or an identifier required by public read APIs.
It does not change guest Saved/Following permissions or add identity merging.

Implemented separate extension:
[Article reactions and Home highlights](article-reactions-and-home-highlights.md)
allows public reaction-count reads and authenticated-only reaction writes.
Its new server table belongs to that feature; it does not add guest accounts,
Room reaction storage, or offline synchronization to this contract.

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
Home falls back to public topics and Saved becomes its guest sign-in state.

When a guest selects the Saved root destination, the client enters Saved and
renders an in-place sign-in state; tab selection alone never opens Login. When a
guest selects a subscription or save mutation, the current public route remains
visible beneath one shared contextual authentication sheet. Login or
registration begins only from an explicit sheet action. Success returns to the
origin and does not automatically replay the protected mutation.

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

Saved-list responses upsert summary columns. Detail responses update the same
row without creating a duplicate article. Ordinary feed pages are not copied
into Room because summary-only rows have no useful offline interaction.
Personalized fields such as `is_saved` and `is_followed` are not stored here.

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
valid cache row. Only `READY` or `PARTIAL` analysis is retained as an offline
copy. Before reading, `source_content_hash` must equal the associated article's
`content_hash`; otherwise the article remains readable but the cached analysis
is ignored.

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

### 6.1 Home and discovery feeds

1. Request the selected server feed normally; online behavior is unchanged.
2. On success, display the server page without persisting ordinary feed
   summaries in Room.
3. On a network failure, query only the active user's Saved relationships whose
   article detail and matching validated analysis are both cached.
4. Apply the selected category to that offline query and disable cursor
   pagination.
5. If no such rows exist, show the explicit empty offline state rather than
   stale headlines or a false server-empty state. A guest has no offline rows.

### 6.2 Search and subscribed Home

Search remains server-wide and subscribed Home remains account-specific, so neither
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

Reader-to-Reader navigation also keeps an in-memory session snapshot containing
the already loaded article, validated analysis, scroll offset, selected finding,
and transient comparison focus. Back, or a comparison link to the immediately
previous article, restores that snapshot synchronously instead of creating a
duplicate `A → B → A` history entry. Room remains the fallback after process
death or eviction; the session snapshot is not a second persistent store.

Displaying a valid session or Room snapshot while an online refresh is pending
does not claim that the device is offline and does not replace readable analysis
with a loading surface. Refresh failure marks the Reader offline only after the
network request fails. A changed article `content_hash` invalidates the visible
cached analysis before the replacement analysis is shown.

Original publisher links may still fail in the external browser while offline;
FrameLESS does not mirror publisher sites.

### 6.4 Saved list and mutations

For an authenticated user, the Saved route first reads that user's locally
complete Saved rows, then refreshes the server list.

Showing this local snapshot while the network refresh is pending does not mark
the UI offline. The offline notice appears only after an actual network failure.

- A successful first-page refresh transaction replaces that user's cached
  Saved relationships with the returned first page. Successfully loaded later
  pages append to this verified prefix.
- A failed refresh preserves the previous local snapshot.
- Before a save request, Android ensures that article detail and a hash-matched,
  validated analysis are present in Room. If this preparation fails, the
  server mutation is not sent and Saved state does not change.
- A successful save inserts or updates the local relationship only after the
  server confirms the mutation.
- A successful removal deletes the local relationship.
- An offline or failed save/remove leaves both UI and Room unchanged and shows
  a connection error. The operation is not queued.
- Online Saved may include a server bookmark created on another device whose
  body is not local yet. Such a row is omitted from offline lists until it is
  opened or saved successfully on this installation.

The cached list is the locally verified prefix, not a claim that every server
bookmark has been downloaded. A successful Android save leaves that article
offline-readable because detail and validated analysis preparation is part of
the mutation contract.

## 7. Retention and deletion

- Keep full detail and analysis for the 20 most recently opened articles.
- Keep up to 200 locally verified Saved relationships per user.
- Keep a summary while a retained Saved relationship refers to it; remove
  unused summary-only rows.
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

- Launch directly into Home rather than Login.
- Show `로그인` in the account action.
- Keep Saved, subscription management, and save controls discoverable. Saved
  shows its own guest state; mutation actions open the shared contextual sheet.
- Login and registration return to the prior public route.

### 8.2 Offline Android

- Feed and Saved show only locally complete Saved articles for the active user;
  a small notice identifies the offline Saved view.
- Do not replace readable cached content with a full-screen network error.
- Distinguish `no local copy` from `server returned no results`.
- Never show a summary-only row that cannot be opened offline.
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
- Saved renders its guest state without redirecting, while mutation controls
  request sign-in contextually and return to the same public route;
- authenticated Saved and subscription behavior remains account-isolated;
- Android opens locally complete Saved articles and their verified Reader
  analysis during a network failure without showing non-openable feed rows;
- offline Saved writes are rejected without optimistic or queued state;
- content-hash mismatch and malformed analysis never produce a wrong highlight;
- Room schema export, retention, logout, and account-switch behavior are tested;
- no guest server model, backend migration, write queue, or background sync
  subsystem is introduced.
