# Following sources v1 test specification

| Field | Value |
| --- | --- |
| Status | Intent-reviewed |
| Owner | Following sources feature owner |
| Source requirements | `docs/news-app-features-spec.md` §§11–12 |
| Last reviewed | 2026-11-02 |

## Scope

Signed-in users explicitly follow publishers, keep the complete non-personalized
Latest feed as the default, and optionally browse a separate Following feed on
Web and Android. Follow state changes article discovery only. Reader analysis
and cross-outlet comparison remain independent of follows.

### Non-goals

- Recommendation, ranking, inferred preferences, notifications, blocks,
  publisher editions, source ordering, or category-specific follows.
- Changing article analysis, shared propositions, findings, or comparison
  evidence based on user follows.
- Testing editorial correctness of stored analysis.

## Preconditions

- A valid bearer session identifies exactly one `UserAccount`.
- A selectable publisher is active and has at least one visible, feed-visible
  article.
- Ordinary article visibility, category, search, pagination, Reader, and saved
  article contracts remain in force.
- Latest and Following use the same article row and Reader contracts.

## Postconditions

### Data model and API success

- [Migration] PostgreSQL permits at most one follow relationship for one user
  and publisher and cascades relationships after hard deletion of either parent.\
  Tip: add model and migration tests for uniqueness and both cascade directions.
- [Integration] `GET /api/v1/publishers` returns active, selectable publishers
  in stable editorial order with the authenticated user's `is_followed` value.\
  Tip: extend the publisher API tests with followed, unfollowed, inactive, and
  no-feed-article partitions.
- [Integration] `PUT /api/v1/me/followed-publishers` atomically replaces the
  user's selectable set, accepts an empty array, normalizes duplicates, and is
  idempotent for an unchanged set.\
  Tip: add transaction-backed replacement, empty-set, duplicate, and repeated
  request tests.
- [Integration] `scope=ALL` or an absent scope returns the complete eligible
  feed without considering follows; `scope=FOLLOWING` returns only eligible
  articles whose publishers the user follows.\
  Tip: add feed tests with followed and unfollowed articles in both scopes.
- [Integration] Category and legacy publisher filters compose by intersection
  with Following, while a zero-follow account receives an empty page and null
  cursor.\
  Tip: add pairwise category, explicit publisher, and follow-set cases.
- [Performance] Publisher and feed responses use a bounded number of database
  queries independent of returned publisher or article count.\
  Tip: extend query-count tests for publisher follow annotations and Following.

### FrameLESS analysis independence

- [Integration] Follow state does not change article detail, analysis findings,
  shared propositions, comparison pages, or comparison target selection.\
  Tip: fetch one analyzed article before and after replacing follows and compare
  all non-user-specific Reader and analysis fields.
- [Acceptance] A followed article may show grounded comparison evidence from an
  unfollowed publisher when both belong to the stored analysis.\
  Tip: add this assertion to the cross-client acceptance run using a reviewed
  fixture with at least two publishers.

### Errors and boundaries

- [Integration] Missing or expired authentication is rejected by publisher
  follow mutation and personalized Following reads.\
  Tip: extend authentication coverage to the new mutation and scope.
- [Integration] Missing `publisher_codes`, a non-array value, an unknown code,
  or a currently unselectable publisher returns a field-specific
  `400 VALIDATION_ERROR` without partially changing the stored set.\
  Tip: add malformed-body and atomic rollback tests for each partition.
- [Integration] Unsupported scope returns `400 VALIDATION_ERROR`. A cursor from
  another scope, user-follow fingerprint, category, query, or publisher filter
  returns `400 INVALID_CURSOR`.\
  Tip: add signed-cursor context and follow-change invalidation tests.
- [Integration] Concurrent full-set replacements expose one complete committed
  set using last committed request wins semantics, never a partial union.\
  Tip: add a transaction-level concurrency test where the database supports it.

### Web client

- [Component] A fresh authenticated entry starts in Latest even when follows
  exist; Following is entered only through an explicit scope control.\
  Tip: render the feed with followed publishers and assert initial query scope.
- [Component] The source editor separates draft from persisted follows; Cancel
  discards the draft, Save applies one full-set request, and a failed Save keeps
  the editor open with the previous server set.\
  Tip: add dialog component tests for cancel, success, duplicate submission,
  and rollback.
- [Component] Zero follows and no matching article in the active category render
  different empty states and actions.\
  Tip: add both empty-state fixtures and assert their destinations.
- [Component] Global search uses the complete corpus and closing it restores the
  previous scope, loaded rows, and scroll context. Reader back does the same.\
  Tip: extend feed navigation tests for Following → search → back and Following
  → Reader → back.
- [Security regression] Query caches include user identity and are cleared on
  account change so another user never observes stale follows or rows.\
  Tip: render two sessions sequentially against one QueryClient.

### Android client

- [Unit] API and repository layers parse `is_followed`, replace the full set,
  encode scope, and keep the existing authenticated request boundary.\
  Tip: extend API-client and repository tests with exact request/response cases.
- [Unit] ViewModel starts in Latest, loads Following on explicit selection,
  resets pagination after a follow change, and preserves each feed state across
  Reader and global search navigation.\
  Tip: add deterministic coroutine tests for the complete state transitions.
- [Unit] Server follow state replaces device-local publisher selection without
  uploading legacy local codes; category and analysis visibility remain local.\
  Tip: add a repository migration test with legacy preferences populated.
- [Component] The scope bottom sheet and follow editor expose selected state,
  named checkboxes, Save, Cancel, retry, and both empty states.\
  Tip: add semantics-driven Compose tests when implementing the UI.
- [Security regression] Logout clears follow and Following-feed state before a
  second account initializes the app.\
  Tip: extend reset tests with two accounts and opposing follow sets.

### Cross-client acceptance

- [Acceptance] Follows saved on Web appear on Android for the same account;
  replacing the set on Android appears on Web after refresh without affecting a
  second account.\
  Tip: add a two-client, two-account procedure to the release acceptance run.
- [Acceptance] Latest remains identical for accounts with different follows,
  while Following differs according to each explicit set.\
  Tip: compare article IDs from both scopes for two prepared accounts.

## Undefined behavior

- V1 does not guarantee order among followed publishers; feed rows remain
  ordered by the ordinary article ordering contract.
- If a publisher becomes unselectable during editing, the next Save may reject
  the stale draft and require a publisher-list refresh.
- Simultaneous updates from several clients use last committed request wins and
  do not expose revisions or merge preference sets.
- The product makes no promise that every followed publisher has an article in
  every category or time window.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Relationship set | empty / one / many / duplicate / replacement | Product spec §§11.3–11.5 |
| Account boundary | same user / different user / sequential login | Product spec §11.6 |
| Publisher lifecycle | selectable / inactive / no eligible article / hard-deleted | Product spec §§11.3–11.5 |
| Feed scope | absent / ALL / FOLLOWING / invalid | Product spec §§11.2, 11.5 |
| Cursor context | same set / changed follows / changed category / changed scope | Product spec §11.6 |
| Analysis independence | followed source / unfollowed comparison source | Product spec §11.1 |
| Client editor | draft / cancel / saving / success / failure | Product spec §§11.7–11.9 |
| Navigation | Following / global search / Reader / back | Product spec §§11.7–11.8 |

## Human review checkpoint

- [x] Product intent and non-goals were reviewed.
- [x] Assertions come from the specification, not implementation output.
- [x] Missing coverage is recorded as `Tip`, not hidden.
- [x] The feed and public-contract change received explicit team confirmation.
