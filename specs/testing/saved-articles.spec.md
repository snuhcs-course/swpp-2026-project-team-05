# Saved articles v1 test specification

| Field | Value |
| --- | --- |
| Status | Implemented |
| Owner | Saved articles feature owner |
| Source requirements | `docs/news-app-features-spec.md` §§9–10 |
| Last reviewed | 2026-11-02 |

## Scope

Signed-in users save and remove feed-visible articles and browse one personal,
recently-saved list from Web and Android. PostgreSQL owns uniqueness and cascade
integrity; Django owns authorization, eligibility, pagination, and user-specific
save state; clients own interaction and cache synchronization.

### Non-goals

- Folders, tags, notes, offline storage, comparison-support saves, saved-list
  filtering, reactions, comments, or public save counts.
- Revalidating article or analysis editorial quality.

## Preconditions

- A valid session identifies exactly one `UserAccount`.
- A newly saveable article is visible, feed-visible, and belongs to an active
  publisher.
- Saved-list pagination uses the endpoint's own signed cursor and a limit from 1
  to 50.

## Postconditions

### Data model and backend success

- [Migration] The database enforces one relationship per user and article and
  cascades rows after hard deletion of either parent.\
  Tested by: `tests.test_news_api.NewsApiTests.test_saved_article_unique_constraint`,
  `tests.test_news_api.NewsApiTests.test_saved_article_relationships_cascade_with_parent`
- [Integration] Saving an eligible article creates one relationship and returns
  its server timestamp; repeating the request returns the same relationship and
  timestamp.\
  Tested by: `tests.test_news_api.NewsApiTests.test_save_article_is_idempotent_and_updates_article_payloads`
- [Integration] Removing a relationship returns `204`; repeating removal also
  returns `204`.\
  Tested by: `tests.test_news_api.NewsApiTests.test_remove_saved_article_is_idempotent`
- [Integration] Each user sees only their own saved relationships and ordinary
  feed/detail `is_saved` values.\
  Tested by: `tests.test_news_api.NewsApiTests.test_saved_articles_are_isolated_per_user`
- [Integration] The saved list is ordered by `saved_at DESC, relationship id
  DESC`, paginates without duplicates or gaps, and rejects a tampered or foreign
  cursor.\
  Tested by: `tests.test_news_api.NewsApiTests.test_saved_articles_cursor_is_stable_and_bound`
- [Integration] Feed, search, article detail, and saved-list payloads expose the
  specified user-specific `is_saved` state without changing article content.\
  Tested by: `tests.test_news_api.NewsApiTests.test_save_article_is_idempotent_and_updates_article_payloads`,
  `tests.test_news_api.NewsApiTests.test_saved_articles_are_isolated_per_user`
- [Performance] Listing a bounded article page or saved page does not add one
  database query per article.\
  Tested by: `tests.test_news_api.NewsApiTests.test_feed_and_analysis_queries_are_bounded`

### Backend errors, lifecycle, and races

- [Integration] Missing or expired authentication is rejected on every saved
  endpoint.\
  Tested by: `tests.test_news_api.NewsApiTests.test_news_endpoints_require_authentication`
- [Integration] A hidden, comparison-only, inactive-publisher, or unknown
  article cannot be newly saved and receives the same not-found response.\
  Tested by: `tests.test_news_api.NewsApiTests.test_save_eligibility_and_soft_hidden_lifecycle`
- [Integration] A soft-hidden saved article is omitted without deleting its
  relationship and reappears after restoration.\
  Tested by: `tests.test_news_api.NewsApiTests.test_save_eligibility_and_soft_hidden_lifecycle`
- [Integration] Hard article deletion removes its relationships; deleting an
  account removes that account's relationships.\
  Tested by: `tests.test_news_api.NewsApiTests.test_saved_article_relationships_cascade_with_parent`
- [Integration] Removing a relationship remains safe after the article becomes
  ineligible, and a save racing with hard deletion never leaves an orphan.\
  Tip: removal after soft hide is covered by
  `NewsApiTests.test_save_eligibility_and_soft_hidden_lifecycle`; add a
  transaction-level save/delete race test.
- [Integration] Limits outside 1–50 and invalid cursors return the documented
  validation errors without leaking unpublished article data.\
  Tested by: `tests.test_news_api.NewsApiTests.test_saved_articles_validate_limit_and_cursor`

### Web client

- [Unit] The API client parses `is_saved` and `saved_at` and sends authenticated
  list, PUT, and DELETE requests to the exact contract paths.\
  Tested by: `web/src/lib/api.test.ts > saved articles API > lists, saves, and removes through the authenticated contract`
- [Component] Reader uses the shared mutation boundary; success updates
  all relevant caches and failure restores the previous state with an error.\
  Tested by: `web/src/hooks/useSavedArticle.test.tsx > useSavedArticle > updates personalized article caches before the save request completes`,
  `web/src/hooks/useSavedArticle.test.tsx > useSavedArticle > restores cached state when the save request fails`
- [Component] `/saved` renders loading, populated, empty, initial-error, and
  next-page-error states without discarding existing rows.\
  Tip: populated and empty are covered by `SavedArticlesPage.test.tsx`;
  add loading, initial-error, and next-page-error cases.
- [Security regression] Query caches are partitioned by user or cleared on
  account change so another login cannot observe the previous user's save state.\
  Tip: render two sequential sessions against one QueryClient and assert no stale
  saved flag or saved list is shown.
- [Accessibility] Reader save and remove controls have distinct accessible
  names and expose a non-duplicating pending state.

### Android client

- [Unit] The API client parses save fields and sends list, PUT, and DELETE
  requests with the current bearer token.\
  Tested by: `com.swpp.team5.frameless.data.NewsApiClientTest.newsEndpointsParseFeedDetailAndGroundedComparison`
- [Unit] Repository and ViewModel keep feed, Reader, and saved-list state in sync
  after success and roll all affected state back after failure.\
  Tested by: `com.swpp.team5.frameless.data.NewsRepositoryTest.preferencesRoundTripAndNetworkCallsUseAuthenticatedService`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.saveMutationUpdatesAllStateAndRollsBackAfterFailure`
- [Unit] Opening and closing the saved route preserves category, publisher,
  search, feed rows, and scroll-state inputs.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.savedRouteLoadsPagesAndReturnsToPreservedFeed`
- [Component] The saved screen distinguishes loading, populated, empty,
  initial-error, and next-page-error states and opens the selected Reader.\
  Tip: add semantics-driven Compose tests when implementing the screen.
- [Security regression] Logout clears personalized save state before a second
  account initializes news data.\
  Tip: extend logout/reset tests with two users and opposing save values.

### Cross-client acceptance

- [Acceptance] Saving an article on Android makes it visible for the same account
  on Web after refresh; removing it on Web removes it from Android after refresh.\
  Tip: add this procedure to the Iteration 3 acceptance run after both clients
  are deployed.
- [Acceptance] A second account on the same browser and phone does not see the
  first account's saved articles or saved indicators.\
  Tip: execute a two-account handoff during the acceptance run.

## Undefined behavior

- A soft-hidden article may return to the saved list if editorial staff restore
  it; v1 does not notify the user about the interval.
- Simultaneous save/remove requests from several clients use last completed
  server operation as the observable state; v1 does not expose a revision log.
- The saved list reflects current article metadata rather than a snapshot from
  the time it was saved.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Relationship state | absent / saved / repeated save / repeated remove | Product spec §§9.3–9.4 |
| Account boundary | same user / different user / sequential login | Product spec §9.5 |
| Article lifecycle | eligible / soft-hidden / restored / hard-deleted | Product spec §§9.2, 9.5 |
| Ordering | distinct timestamps / equal timestamps / next page | Product spec §§9.3–9.4 |
| Client mutation | pending / success / failure rollback | Product spec §§9.6–9.8 |
| Screen state | loading / rows / empty / initial error / next-page error | Product spec §9.8 |

## Human review checkpoint

- [ ] Product, API, soft-delete, cache, and UI behavior reviewed by the team.
- [ ] Test oracles approved before implementation begins.
- [x] Implementation maps each completed postcondition to an exact test and
  replaces its `Tip` without weakening the criterion.
