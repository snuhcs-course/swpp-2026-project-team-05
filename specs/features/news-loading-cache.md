# News loading and cache reuse

Status: Implemented
Date: 2026-11-25
Clients: Android; existing Web query-cache consistency

## Intent

Opening another section or returning from a comparison should reuse recent data
without losing the reading position. Refresh must still retrieve current server
data, and cached discovery rows must not become a misleading offline feed.
Extend the existing repository and Room cache; retain the current article,
finding, topic, cursor and authenticated mutation contracts.

## Loading rules

- Android starts the public topic feed independently of the publisher directory.
  Completing that directory must not restart the same public feed request. The
  portal does not preload the retired, invisible article feed.
- Home and News share identical topic queries. Home's selected publisher and a
  publisher's article page share identical first-page queries. Search and Saved
  remain article-level; they never reuse a topic page as article results.
- The repository owns bounded recent responses and concurrent-request sharing.
  Keys include viewer, resource, normalized filters, cursor and, for analysis,
  the source content hash. No bearer token is written to a cache key or disk.
- Successful list/search/Saved pages are fresh for 60 seconds; the publisher
  directory for five minutes; article detail and verified READY/PARTIAL analysis
  for five minutes. These are initial revalidation intervals, not deletion times
  for offline Saved copies. Pending/error analysis is not retained as a completed
  five-minute result. Failures are never successful cache entries.
- Fresh data needs no duplicate GET. Expired data already on screen stays visible
  while one request refreshes it. Concurrent consumers share one in-flight read;
  cancelling one consumer must not cancel another consumer's read.
- Pull-to-refresh bypasses freshness for its query and invalidates that query's
  continuation pages. Refresh replaces the loaded page chain rather than joining
  an updated first page to old continuation cursors.
- News remembers each section's loaded pages and scroll position while it remains
  in the current viewer's session. Switching sections first restores that state,
  then revalidates its first page. A still-fresh, unchanged first page preserves
  the loaded continuation pages.
- Reader keeps the existing immediate snapshot/Room presentation and Back stack.
  A fresh cached article/analysis pair avoids repeated reads on A → B → A. A body
  refresh must never apply analysis with another `source_content_hash`; an
  independently updated analysis is revalidated after its own freshness interval.
  A newly restored authenticated viewer first obtains authoritative server Saved
  flags; a public disk body alone does not skip that check. Public Room analysis
  remains reusable when its hash and freshness match.

## Correctness and invalidation

- Login, logout, account or credential changes discard personalized memory
  results. An old in-flight response cannot repopulate the new viewer's cache.
- Saved changes update the visible state immediately after server confirmation
  and invalidate affected cached article/list/Saved responses. Subscription
  changes invalidate publisher flags and personalized topic representatives.
  Article reactions, the one-minute Home highlights cache, and the five-minute
  trend snapshot retain their independent policies.
- A device known to have no usable network must not bypass the offline policy by
  treating an in-memory response as a live feed. Check connectivity on reads;
  an unvalidated active network tries the server instead of assuming it is
  offline or bypassing that check with cached data. Actual transport failure
  remains authoritative. No polling or background sync.
- On a discovery network failure, Home and general/category News replace online
  rows with complete local Saved copies only, filtered by category, with no next
  cursor. With no copies, show an offline-empty state. Cache read failure must not
  leave a spinner running. Server/validation errors remain distinct from offline.
- Home renders separate initial-loading, empty, error, refreshing and offline
  states. A failed refresh does not falsely claim that no articles exist.
  Offline discovery hides cached live-trend/Breaking/Issue links and labels the
  fallback section as Saved articles; those ranking caches are retained for the
  next online visit, not reset as if their data were malformed.
- Web retains TanStack Query as its cache owner, includes search in Saved-state
  invalidation, and only renders analysis whose source hash matches the article.

## Server and memory scope

- Select only summary columns for ordinary article lists and publisher previews.
  Search may load body text to build its existing excerpt. Keep topic grouping in
  SQL before cursor pagination and avoid deferred-field N+1 queries.
- Reuse the existing Room timestamps and limits. Do not persist normal feed pages
  or add Redis, server cache tables, a new synchronization layer, or eager bulk
  article prefetching.
- Decode publisher logos at a size suitable for their small UI tiles. Prefer the
  bundled reviewed logo; remote images are a fallback rather than an additional
  request for every already-bundled brand.
- Preserve the analysis response shape in this slice. A read-only sample of the
  three feed articles with the most findings returned about 60–83 KB of JSON,
  including 16–26 KB of repeated comparison bodies. Removing that duplication
  would change Android/Web/offline contracts and is a separate measured follow-up.

## Verification

Behavioral oracles and exact automated references belong in
[`../testing/news-loading-cache.spec.md`](../testing/news-loading-cache.spec.md).
Validate request counts, cache expiry, viewer transitions, invalidation, offline
fallback and cursor continuity, rather than claiming an unmeasured latency gain.
Use JVM, backend and Web component tests. Device/browser checks require a separate
request; this work does not start an emulator or browser.
