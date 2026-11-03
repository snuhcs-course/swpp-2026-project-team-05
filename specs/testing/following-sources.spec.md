# Following sources v1 test specification

| Field | Value |
| --- | --- |
| Status | Implemented |
| Owner | Following sources feature owner |
| Source requirements | [`../features/news-app-features.md`](../features/news-app-features.md) §§11–12 |
| Last reviewed | 2026-11-03 |

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
  Tested by: `tests.test_news_api.NewsApiTests.test_follow_relationship_is_unique_and_cascades_with_either_parent`
- [Integration] `GET /api/v1/publishers` returns active, selectable publishers
  in stable editorial order with the authenticated user's `is_followed` value.\
  Tested by: `tests.test_news_api.NewsApiTests.test_publishers_are_ordered_active_and_follow_aware`
- [Integration] `PUT /api/v1/me/followed-publishers` atomically replaces the
  user's selectable set, accepts an empty array, normalizes duplicates, and is
  idempotent for an unchanged set.\
  Tested by: `tests.test_news_api.NewsApiTests.test_followed_publishers_replace_atomically_and_are_idempotent`
- [Integration] `scope=ALL` or an absent scope returns the complete eligible
  feed without considering follows; `scope=FOLLOWING` returns only eligible
  articles whose publishers the user follows.\
  Tested by: `tests.test_news_api.NewsApiTests.test_following_scope_is_user_specific_and_latest_stays_complete`
- [Integration] Category and legacy publisher filters compose by intersection
  with Following, while a zero-follow account receives an empty page and null
  cursor.\
  Tested by: `tests.test_news_api.NewsApiTests.test_following_composes_with_category_and_legacy_publisher_filter`,
  `tests.test_news_api.NewsApiTests.test_following_scope_is_user_specific_and_latest_stays_complete`
- [Performance] Publisher and feed responses use a bounded number of database
  queries independent of returned publisher or article count.\
  Tested by: `tests.test_news_api.NewsApiTests.test_feed_and_analysis_queries_are_bounded`

### FrameLESS analysis independence

- [Integration] Follow state does not change article detail, analysis findings,
  shared propositions, comparison pages, or comparison target selection.\
  Tested by: `tests.test_news_api.NewsApiTests.test_follow_changes_neither_reader_nor_stored_analysis`
- [Acceptance] A followed article may show grounded comparison evidence from an
  unfollowed publisher when both belong to the stored analysis.\
  Tip: add this assertion to the cross-client acceptance run using a reviewed
  fixture with at least two publishers.

### Errors and boundaries

- [Integration] Missing or expired authentication is rejected by publisher
  follow mutation and personalized Following reads.\
  Tested by: `tests.test_news_api.NewsApiTests.test_news_endpoints_require_authentication`,
  `tests.test_news_api.NewsApiTests.test_following_scope_is_user_specific_and_latest_stays_complete`
- [Integration] Missing `publisher_codes`, a non-array value, an unknown code,
  or a currently unselectable publisher returns a field-specific
  `400 VALIDATION_ERROR` without partially changing the stored set.\
  Tested by: `tests.test_news_api.NewsApiTests.test_followed_publishers_reject_invalid_payload_without_partial_update`
- [Integration] Unsupported scope returns `400 VALIDATION_ERROR`. A cursor from
  another scope, user-follow fingerprint, category, query, or publisher filter
  returns `400 INVALID_CURSOR`.\
  Tested by: `tests.test_news_api.NewsApiTests.test_following_rejects_invalid_scope`,
  `tests.test_news_api.NewsApiTests.test_following_cursor_is_invalid_after_follow_set_changes`,
  `tests.test_news_api.NewsApiTests.test_feed_rejects_tampered_cursor`
- [Integration] Concurrent full-set replacements expose one complete committed
  set using last committed request wins semantics, never a partial union.\
  Tip: add a transaction-level concurrency test where the database supports it.

### Web client

- [Component] A fresh authenticated entry starts in Latest even when follows
  exist; Following is entered only through an explicit scope control.\
  Tested by: `web/src/pages/FeedPage.test.tsx > keeps Latest complete and requests Following only when selected`
- [Component] The source editor separates draft from persisted follows; Cancel
  discards the draft, Save applies one full-set request, and a failed Save keeps
  the editor open with the previous server set.\
  Tested by: `web/src/pages/FeedPage.test.tsx > saves source preferences explicitly from the manager`,
  `web/src/pages/FeedPage.test.tsx > discards a cancelled draft and keeps the editor open after a failed save`
- [Component] Zero follows and no matching article in the active category render
  different empty states and actions.\
  Tested by: `web/src/pages/FeedPage.test.tsx > distinguishes an empty follow set from a followed category with no stories`
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
  Tested by: `com.swpp.team5.frameless.data.NewsApiClientTest.newsEndpointsParseFeedDetailAndGroundedComparison`,
  `com.swpp.team5.frameless.data.NewsRepositoryTest.preferencesRoundTripAndNetworkCallsUseAuthenticatedService`
- [Unit] ViewModel starts in Latest, loads Following on explicit selection,
  resets pagination after a follow change, and preserves each feed state across
  Reader and global search navigation.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.initializeLoadsPublishersAndTheCompleteLatestFeed`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.categoryScopeRefreshAndPaginationPreserveExpectedState`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.sourcePreferencesAreAccountBackedAndFollowingReloadsAfterSave`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.searchDebouncesServerQueryAndClosingRestoresFeed`
- [Unit] Server follow state replaces device-local publisher selection without
  uploading legacy local codes; category and analysis visibility remain local.\
  Tested by: `com.swpp.team5.frameless.data.NewsRepositoryTest.preferencesRoundTripAndNetworkCallsUseAuthenticatedService`
- [Component] The scope bottom sheet exposes Latest and Following as one
  mutually exclusive, named radio group. The follow editor alone uses named
  checkboxes for multi-publisher selection and preserves Save, Cancel, retry,
  and both empty states.\
  Tested by: `com.swpp.team5.frameless.ui.screens.NewsScreensInstrumentedTest.feedScopeUsesSingleSelectionRadioOptions`
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
