# News loading and cache reuse test specification

Status: Automated cache, repository, ViewModel, backend and Web regressions implemented

## Intent and preconditions

Verify the observable loading rules in
[`../features/news-loading-cache.md`](../features/news-loading-cache.md).
Use a controllable clock, delayed fake reads, independent viewers and explicit
transport failures. Never contact production or a publisher from CI.

## Postconditions

1. Fresh reads reuse one successful result; expiry, explicit refresh and failed
   attempts allow a new read. Concurrent callers share one read, and cancelling
   a caller does not cancel the shared request. Memory use has a bounded entry
   count.
   Tested by: `NewsReadCacheTest.freshnessExpiryFailureAndBoundedReuse`,
   `NewsReadCacheTest.concurrentReadsSurviveOneConsumersCancellation`,
   `NewsRepositoryTest.freshReaderAndListReadsAreReusedAndRefreshBypassesThem`.
2. Viewer changes and mutations cannot reuse another viewer's flags or restore
   obsolete responses after invalidation.
   Tested by: `NewsReadCacheTest.invalidatedLateCompletionCannotReplaceNewResult`,
   `NewsRepositoryTest.credentialsAndMutationsInvalidatePersonalizedResponses`,
   `NewsRepositoryTest.oldViewersDelayedResponseCannotReplaceTheNewViewersCache`,
   `NewsViewModelTest.viewerChangeReloadsVisitedPortalFeedsWithoutWaitingForAnotherTabSwitch`.
3. Home loading is independent of a delayed publisher directory and completing
   that directory does not restart the public feed.
   Tested by: `NewsViewModelTest.portalHomeDoesNotWaitForOrRestartAfterPublisherDirectory`.
4. Section round trips preserve loaded pages; refresh discards the old cursor
   chain, and returning to an unchanged fresh page does not duplicate rows.
   Tested by: `NewsViewModelTest.newsSectionRoundTripKeepsPagesUntilExplicitRefresh`.
5. Home and category News network failures show only complete local Saved copies
   with no cursor; empty or failed local reads give an explicit offline-empty
   state, never the old online-only rows.
   Tested by: `NewsViewModelTest.portalOfflineFallbackNeverRetainsUnsavedOnlineRows`,
   `NewsRepositoryTest.offlineReadsDoNotReuseOnlineHeadlinesAndPublishersShareTheirFirstPage`,
   `NewsRepositoryTest.anUnvalidatedNetworkAttemptsTheServerRatherThanClaimingOffline`.
6. A fresh Reader pair avoids repeat detail/analysis reads, while a changed body
   cannot render old analysis offsets.
   Tested by: `NewsRepositoryTest.freshReaderAndListReadsAreReusedAndRefreshBypassesThem`,
   `NewsRepositoryTest.mismatchedAnalysisAndConfirmedDeletionAreNotCachedAsSuccess`,
   `NewsCacheTest.analysisIsReusedOnlyForTheMatchingArticleHash`.
7. Article summaries and publisher previews avoid unused body/analysis columns
   without introducing per-row queries; search excerpts retain their content.
   Tested by: `tests.test_news_api.NewsApiTests.test_summary_projections_skip_large_fields_without_extra_queries`.
8. Web Saved changes cover search caches and mismatched article/analysis versions
   never render markers or explanations together.
   Tested by: `useSavedArticle > updates personalized article caches before the save request completes`,
   `ArticlePage analysis rail > does not show findings from another version of the article body`.
9. Confirmed deletion removes the cached detail, analysis and active viewer's
   Saved relation, without silently deleting another viewer's Saved relation.
   Tested by: `NewsCacheTest.confirmedDeletionRemovesDetailAnalysisAndTheActiveSavedRelation`.
10. Each News section has an independent scroll position across section changes.
    Tip: the shell now supplies one LazyListState per section; add a Compose
    navigation test before claiming device-verified scroll restoration.

11. Offline Home hides cached live ranking links and labels its fallback as Saved.
    Tip: add a Compose semantics check for offline Home; the loader's Saved-only
    data contract is covered above, but no device rendering pass is claimed.

## Boundaries

- Existing reaction, highlights and trend freshness tests remain authoritative.
- Cold-start latency and visual smoothness are not established by fake-clock
  tests. No latency percentile, browser/device pass or production improvement is
  claimed without a corresponding measurement.

## Verification record

2026-11-25, cache slice `c9305c8` (later integrated through PR #116):

- Android JVM suite: 196 tests passed; JaCoCo layer gates passed.
- Android lint, debug APK and instrumentation APK builds passed. Instrumentation
  was compiled only; no device, emulator or browser run is claimed.
- Django suite: 476 discovered, seven PostgreSQL-only skips, no failures. The
  summary/projection regression also passed separately after its Saved assertion
  was added. No schema or PostgreSQL-specific behavior changed in this slice.
- Web: 39 component tests, lint and production build passed.

These are implementation checks, not a production latency or editorial-quality
measurement. Historical iteration test reports are unchanged.
