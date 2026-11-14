# Guest and offline reading test specification

| Field | Value |
| --- | --- |
| Status | Draft |
| Owner | Guest/offline feature owner |
| Source requirements | [`../features/guest-offline-reading.md`](../features/guest-offline-reading.md) |
| Last reviewed | 2026-11-14 |

## Scope

This specification defines the observable contract for public news reads on
Android and Web and the bounded Android Room cache for Latest, Reader,
analysis, and authenticated Saved-list reads.

### Non-goals

- Offline search, Following, Saved writes, queued mutations, conflict
  resolution, WorkManager synchronization, and Web offline storage.
- Testing editorial quality or the correctness of A-G labels.
- Replacing the existing authenticated Saved and Following test specifications.

## Preconditions

- The server contains at least one visible feed article with READY analysis and
  one article with no displayable findings.
- Two ordinary accounts exist with different Saved and followed-source state.
- Android starts with an empty Room database unless a case explicitly seeds a
  cache row.
- Server article content is immutable for one article ID and exposes
  `content_hash`; analysis exposes the matching `source_content_hash`.

## Postconditions

### Backend public-read boundary

- [Integration] A request without `Authorization` can list publishers, list or
  search `scope=ALL` articles, read article detail, and read stored analysis. It
  receives stable response shapes with `is_saved=false` and
  `is_followed=false`.\
  Tip: add Django API tests covering all five public-read endpoints.
- [Integration] `scope=FOLLOWING`, followed-source replacement, Saved list, and
  Saved mutations return `401 AUTH_REQUIRED` without a valid session.\
  Tip: extend the news API authentication matrix with every protected route.
- [Security] A malformed or expired bearer token on a public endpoint returns
  `401` instead of silently becoming a guest request.\
  Tip: add missing-header, valid-header, malformed-header, unknown-token, and
  expired-token partitions.
- [Integration] Valid authenticated public reads retain correct `is_saved` and
  `is_followed` personalization without changing feed, article, or analysis
  content.\
  Tip: compare guest and two-user projections of the same records.
- [Security] Credential-varying responses carry safe cache headers including
  `Vary: Authorization`.\
  Tip: assert response headers for guest and authenticated reads.
- [Contract] Article detail and analysis return matching content hashes from the
  stored Article row.\
  Tip: assert exact hashes and a mismatched-fixture rejection path.

### Android authentication transitions

- [Unit] A clean install without a session resolves to Guest and initializes
  the public Latest feed rather than Login.\
  Tip: add AuthViewModel and FrameLESSApp state tests.
- [Unit] A temporary network failure during session restoration retains an
  unexpired stored session; locally expired or server-rejected sessions become
  Guest.\
  Tip: add network, local-expiry, and confirmed-401 restore cases.
- [Unit] A public GET that receives `401` clears the session and retries exactly
  once without a token, while a protected operation is not retried.\
  Tip: test repository request counts and final viewer state.
- [State transition] Explicit logout clears personalized ViewModel state and
  the active user's cached Saved relationships, falls Following back to Latest,
  and keeps public cache rows.\
  Tip: add a two-account logout regression test.

### Android Room and repository

- [Database] Room schema version 1 contains only `cached_article`,
  `cached_analysis`, and `cached_saved_article`, exports its schema, and enforces
  the documented keys and references.\
  Tip: add an instrumented Room schema and DAO test.
- [Unit] Feed summary upsert does not erase previously cached detail, and detail
  upsert does not persist personalized save/follow flags.\
  Tip: test both write orders against an in-memory database.
- [Unit] A validated analysis is reused only when its source hash matches the
  cached article hash. Malformed or mismatched analysis leaves a prior valid row
  untouched.\
  Tip: add exact-match, mismatch, invalid-offset, and invalid-quote cases.
- [Unit] Cached Latest rows are emitted before a remote refresh; remote success
  replaces visible rows and updates Room, while remote failure retains the cache
  and reports offline provenance.\
  Tip: test cache-empty/cache-present against network success and failure.
- [Unit] Cached Reader detail and matching analysis remain readable after a
  network failure. Detail-only cache shows the article without inventing an
  analysis result.\
  Tip: add full-cache, detail-only, and no-cache Reader cases.
- [Unit] Retention preserves the latest configured summaries, the 20 most recent
  details/analyses, and summaries referenced by retained Saved relationships.\
  Tip: seed values just below, at, and above each limit.

### Android Saved snapshot

- [Unit] Saved cache queries require the active user ID, and two accounts on one
  installation never see each other's relationships. Guest receives none.\
  Tip: seed opposing two-user rows and query each viewer state.
- [Unit] A successful Saved first-page refresh replaces that user's cached
  verified prefix atomically; loaded continuation pages append, and a failed
  refresh changes nothing.\
  Tip: cover replacement, append, and rollback behavior.
- [Unit] Successful online save/remove updates the local relationship only after
  the server succeeds. Offline or failed writes do not change UI or Room and are
  never queued.\
  Tip: assert call order and database state for success and failure.
- [Component] An offline Saved row with local detail opens Reader; summary-only
  rows remain visible and show the connect-once explanation.\
  Tip: add Compose semantics tests for both availability states.

### Web guest boundary

- [Component] Guest users can render the feed and article route, including
  analysis, without redirecting to Login.\
  Tip: add guest FeedPage, ArticlePage, and routing tests.
- [Component] Saved, Following, source management, and save controls open the
  shared sign-in path, then return to the prior public route after successful
  login without replaying the mutation.\
  Tip: test route state and mutation call counts.
- [Security regression] React Query keys distinguish guest and user identities,
  and account changes cannot display stale saved/followed flags.\
  Tip: render guest, user A, guest, and user B against one QueryClient.

### User-visible offline states

- [Component] Cached content uses a restrained offline notice rather than a
  full-screen error, while a cache miss uses a retryable connection error.\
  Tip: test feed and Reader state matrices.
- [Component] Starting search, Following, or a Saved mutation offline explains
  that a connection is required and preserves existing visible content.\
  Tip: add targeted Android UI state tests.

## Undefined behavior

- The app does not guarantee that the locally verified Saved prefix contains
  every server bookmark unless all pages were loaded successfully.
- Offline content may be stale until the next successful navigation-triggered
  refresh.
- Publisher original links remain external and may not open offline.
- The product does not guarantee offline availability for an article that has
  never been opened on that Android installation.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Viewer identity | guest / valid user / invalid token / expired token | Feature §§2–4 |
| Public API | publisher / feed / search / detail / analysis | Feature §4.1 |
| Protected API | Following / follow mutation / Saved read / Saved mutation | Feature §4.2 |
| Cache availability | none / summary / detail / detail+analysis | Feature §6 |
| Analysis validity | matching hash / mismatched hash / malformed offsets | Feature §§4.4, 5.2 |
| Saved snapshot | guest / user A / user B / first page / continuation | Feature §§5.3, 6.4 |
| Network outcome | success / timeout / 401 / 404 / malformed response | Feature §§3, 4, 6 |
| Retention boundary | below / at / above configured limits | Feature §7 |

## Human review checkpoint

- [ ] Guest capabilities and protected actions were reviewed by the team.
- [ ] Android-only offline scope and Saved read-only behavior were approved.
- [ ] Public full-article access was acknowledged as an internal-prototype
  policy pending external-release review.
- [ ] Assertions come from the specification rather than implementation output.
- [ ] Each implemented postcondition replaces its `Tip` with an exact test.
