# Guest and offline reading test specification

| Field | Value |
| --- | --- |
| Status | Implemented with documented coverage gaps |
| Owner | Guest/offline feature owner |
| Source requirements | [`../features/guest-offline-reading.md`](../features/guest-offline-reading.md) |
| Last reviewed | 2026-11-24 |

## Scope

This specification defines the observable contract for public news reads on
Android and Web and the bounded Android Room cache for Reader, analysis, and
authenticated offline Saved reads.

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
  Tested by: `tests.test_news_api.NewsApiTests.test_public_news_reads_allow_guests_and_disable_personalization`
- [Integration] `scope=FOLLOWING`, followed-source replacement, Saved list, and
  Saved mutations return `401 AUTH_REQUIRED` without a valid session.\
  Tested by: `tests.test_news_api.NewsApiTests.test_personalized_news_operations_still_require_authentication`
- [Security] A malformed or expired bearer token on a public endpoint returns
  `401` instead of silently becoming a guest request.\
  Tested by: `tests.test_news_api.NewsApiTests.test_invalid_supplied_token_is_not_downgraded_to_guest`
- [Integration] Valid authenticated public reads retain correct `is_saved` and
  `is_followed` personalization without changing feed, article, or analysis
  content.\
  Tip: compare guest and two-user projections of the same records.
- [Security] Credential-varying responses carry safe cache headers including
  `Vary: Authorization`.\
  Tested by: `tests.test_news_api.NewsApiTests.test_public_news_reads_allow_guests_and_disable_personalization`
- [Contract] Article detail and analysis return matching content hashes from the
  stored Article row.\
  Tested by: `tests.test_news_api.NewsApiTests.test_article_detail_is_exact_and_hidden_articles_are_not_found`,
  `tests.test_news_api.NewsApiTests.test_article_analysis_projects_findings_and_comparisons`

### Android authentication transitions

- [Unit] A clean install without a session resolves to Guest and initializes
  the public Latest feed rather than Login.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.AuthViewModelTest.cleanInstallStartsAsGuestWithoutOpeningLogin`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.initializeLoadsPublishersAndTheCompleteLatestFeed`
- [Unit] A temporary network failure during session restoration retains an
  unexpired stored session; locally expired or server-rejected sessions become
  Guest.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.AuthViewModelTest.temporaryRestoreFailureKeepsTheUnexpiredLocalSession`,
  `com.swpp.team5.frameless.ui.viewmodel.AuthViewModelTest.savedSessionRestoresAndExpiredRestoreReturnsToLogin`,
  `com.swpp.team5.frameless.data.AuthRepositoryTest.locallyExpiredSessionIsClearedBeforeAnyNetworkRequest`
- [Unit] A public GET that receives `401` clears the session and retries exactly
  once without a token, while a protected operation is not retried.\
  Tip: test repository request counts and final viewer state.
- [State transition] Signing in from a protected Reader action refreshes
  personalization without leaving the article or replaying the mutation.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.viewerChangeRefreshesPersonalizationWithoutLeavingReader`
- [State transition] Explicit logout clears personalized ViewModel state and
  the active user's cached Saved relationships, falls Following back to Latest,
  and keeps public cache rows.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.logoutClearsOnlyTheActiveSavedSnapshotAndAlwaysFinishes`,
  `com.swpp.team5.frameless.data.NewsCacheTest.savedSnapshotsAreIsolatedAndClearedPerUser`

### Android Room and repository

- [Database] Room schema version 1 contains only `cached_article`,
  `cached_analysis`, and `cached_saved_article`, exports its schema, and enforces
  the documented keys and references.\
  Tip: add an instrumented Room schema and DAO test.
- [Unit] A Saved-page summary refresh does not erase previously cached detail,
  and detail storage does not persist personalized save/follow flags.\
  Tested by: `com.swpp.team5.frameless.data.NewsCacheTest.savedSummaryRefreshPreservesPreviouslyCachedDetail`,
  `com.swpp.team5.frameless.data.NewsCacheTest.savedSummaryRefreshPreservesVerifiedAnalysisState`
- [Unit] A validated analysis is reused only when its source hash matches the
  cached article hash. Non-ready, malformed, or mismatched analysis cannot form
  an offline copy and leaves a prior valid row untouched.\
  Tested by: `com.swpp.team5.frameless.data.NewsCacheTest.analysisIsReusedOnlyForTheMatchingArticleHash`,
  `com.swpp.team5.frameless.data.NewsCacheTest.invalidAnalysisDoesNotOverwriteTheLastGroundedCopy`,
  `com.swpp.team5.frameless.data.NewsCacheTest.nonReadyAnalysisDoesNotBecomeAnOfflineCopy`
- [Unit] A feed network failure replaces ordinary rows with only locally
  complete Saved rows from the selected category and reports offline
  provenance. Summary-only, detail-only, unsaved, and other-category rows are
  excluded.\
  Tested by: `com.swpp.team5.frameless.data.NewsCacheTest.offlineSavedQueryRequiresRelationshipDetailAndMatchingAnalysis`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.networkFailureShowsOnlyOfflineSavedArticles`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.guestNetworkFailureShowsHonestOfflineEmptyState`
- [Unit] Cached Reader detail and matching analysis remain readable after a
  network failure.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.cachedReaderAndAnalysisRemainVisibleWhenRefreshIsOffline`
- [State transition] Reader Back restores the previous in-memory article,
  analysis, scroll offset, and selected finding synchronously; a comparison link
  to that same previous article pops the snapshot instead of adding a duplicate
  history entry.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.comparisonReaderNavigationPreservesSourceBackStackAndFocus`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.reverseComparisonLinkRestoresThePreviousSnapshotWithoutDuplicatingHistory`,
  `android/app/src/androidTest/java/com/swpp/team5/frameless/ui/screens/NewsScreensInstrumentedTest.kt > readerSnapshotRestoresThePreviouslySelectedFinding`
- [Unit] A valid cached Reader shown during a pending online refresh is not
  labeled offline; an actual refresh failure transitions it to the restrained
  offline state without removing readable content.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.cachedReaderDoesNotClaimOfflineWhileItsRefreshIsPending`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.cachedReaderAndAnalysisRemainVisibleWhenRefreshIsOffline`
- [Unit] Detail-only cache shows the article without inventing an analysis
  result, while a cache miss uses the retryable connection state.\
  Tip: add detail-only and no-cache Reader cases.
- [Unit] Retention keeps the 20 most recently opened details and their matching
  analyses.\
  Tip: add explicit detail/analysis boundary cases at 19, 20, and 21 rows.

### Android Saved snapshot

- [Unit] Saved cache queries require the active user ID, and two accounts on one
  installation never see each other's relationships. Guest receives none.\
  Tested by: `com.swpp.team5.frameless.data.NewsCacheTest.savedSnapshotsAreIsolatedAndClearedPerUser`,
  `com.swpp.team5.frameless.data.NewsRepositoryTest.cachedArticleRestoresTheActiveUsersSavedState`
- [Unit] A successful Saved first-page refresh replaces that user's cached
  verified prefix atomically; loaded continuation pages append, and a failed
  refresh changes nothing.\
  Tip: cover replacement, append, and rollback behavior.
- [Unit] Before an online save, Android prepares article detail and matching
  validated analysis. It sends the server mutation and writes the local
  relationship only after preparation succeeds.\
  Tested by: `com.swpp.team5.frameless.data.NewsRepositoryTest.savePreparesOfflineCopyBeforeServerMutation`,
  `com.swpp.team5.frameless.data.NewsRepositoryTest.failedOfflineCopyPreparationDoesNotSave`
- [Unit] Successful online save/remove updates visible and local state only
  after the server succeeds. Offline or failed writes leave state unchanged and
  are never queued.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.saveMutationChangesStateOnlyAfterSuccess`
- [Unit] An offline Saved snapshot contains only locally complete rows and can
  open Reader.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.offlineSavedRouteShowsOnlyCompleteLocalCopies`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.cachedReaderAndAnalysisRemainVisibleWhenRefreshIsOffline`
- [State transition] Showing a local Saved snapshot during an online refresh
  does not claim the device is offline; the offline state begins only after a
  network failure.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.localSavedSnapshotDoesNotClaimOfflineWhileRefreshIsPending`

### Web guest boundary

- [Component] Guest users can render the feed and article route, including
  analysis, without redirecting to Login.\
  Tested by: `web/src/pages/GuestFlow.test.tsx > guest news flow > loads Latest without a token and sends Following to contextual sign-in`,
  `web/src/pages/GuestFlow.test.tsx > guest news flow > reads an article and analysis as guest, then protects the save action`
- [Component] Following and save controls open contextual sign-in without
  issuing the protected request.\
  Tested by: `web/src/pages/GuestFlow.test.tsx > guest news flow > loads Latest without a token and sends Following to contextual sign-in`,
  `web/src/pages/GuestFlow.test.tsx > guest news flow > reads an article and analysis as guest, then protects the save action`
- [Component] Saved and source management use the same sign-in path, and a
  successful login returns to the prior route without replaying a mutation.\
  Tip: add the successful-login return assertion for each protected entry point.
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
- A server bookmark created on another device is omitted offline until this
  installation has cached its validated body and analysis.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Viewer identity | guest / valid user / invalid token / expired token | Feature §§2–4 |
| Public API | publisher / feed / search / detail / analysis | Feature §4.1 |
| Protected API | Following / follow mutation / Saved read / Saved mutation | Feature §4.2 |
| Cache availability | none / summary / detail / detail+matching analysis | Feature §6 |
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
