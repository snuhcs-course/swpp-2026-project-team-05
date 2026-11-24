# Recent-read trends test specification

| Field | Value |
| --- | --- |
| Status | Backend/PostgreSQL, Android JVM, and device coverage implemented; remaining gaps noted below |
| Source requirements | [Recent-read trends](../features/realtime-trends.md) |
| Last reviewed | 2026-11-25 |

## Intent and preconditions

Verify that the Home list reflects the last three hours of qualifying readership
without repeated-view inflation, duplicated topics, ranking churn, or expensive
work on every request. Use a frozen UTC clock, isolated database, and explicit
readers, articles, clusters, publishers, and current/previous snapshots.
Expected ranks come from the feature's sorting rules, not production output.
No live users, production identifiers, publisher pages, or AI calls are needed.

## Postconditions

1. A first qualifying event creates one viewer/article row. Repeats and
   simultaneous inserts leave one row and never move last-read time backwards.
   Two accounts count twice; the same authenticated account on two devices
   counts once. Guest and account namespaces cannot collide.  
   Tested by: `tests.test_trends.TrendTests.test_repeat_upsert_and_older_write_do_not_inflate_or_rewind`,
   `tests.test_trends_postgres.TrendConcurrencyTests.test_concurrent_view_inserts_are_one_row`.
2. Missing/malformed guest identity is rejected, valid bearer identity overrides
   a guest header, and an invalid bearer returns 401 without a guest write.
   Hidden/deleted articles and inactive publishers do not accept counted reads.
   Article GET and prefetch never write views.  
   Tested by: `tests.test_trends.TrendTests.test_identity_validation_and_account_precedence`,
   `tests.test_trends.TrendTests.test_hidden_and_unknown_reads_rejected_and_get_never_records`,
   `tests.test_trends.TrendTests.test_inactive_publisher_is_excluded_and_arbitrary_filters_rejected`.
3. With calculation time T, rows exactly at T−3h count; rows just before do not.
   Re-reading extends eligibility. Publication age is not a read-window filter.
   Expired-but-not-yet-deleted rows have no effect on ranks.  
   Tested by: `tests.test_trends.TrendTests.test_window_boundary_is_read_time_not_publication_time`,
   `tests.test_trends.TrendTests.test_repeat_upsert_and_older_write_do_not_inflate_or_rewind`.
4. One viewer reading A and B in the same cluster contributes one topic viewer;
   another viewer contributes a second. A qualifying support article can
   contribute to its discoverable topic; an undiscoverable singleton cannot.
   Unclustered feed articles stay independent.  
   Tested by: `tests.test_trends.TrendTests.test_topic_distinct_viewers_include_readable_support_articles`.
5. With at least five eligible topics, 0..4 qualifying topics are supplemented
   to five with distinct latest topics; five/six qualifying topics return five
   popular topics. Latest entries have null ranks and a LATEST kind. Six read
   articles inside one cluster produce one popular entry. A corpus of 0..4
   topics returns only that actual number.  
   Tested by: `tests.test_trends.TrendTests.test_sparse_and_zero_popularity_fill_to_five_with_distinct_latest`,
   `tests.test_trends.TrendTests.test_small_or_empty_corpus_has_only_real_topics`.
6. Count ties retain previous display order. Previously unranked tied topics
   follow incumbents, then stable story keys decide their order. The fifth-place
   tie never grows the list or changes on repeated equivalent input.  
   Tested by: `tests.test_trends.TrendTests.test_ties_preserve_prior_rank_then_key_including_cutoff`.
7. Movement uses previous minus current rank only when both snapshots have five
   popular topics and a recent baseline. Sparse popularity, any latest fill,
   first calculation, removed entries, and baselines older than 15 minutes have
   no movement or NEW badges. Previous LATEST entries never supply a rank.
   With a complete baseline, a new popular entry is NEW and unchanged rank has
   null movement.  
   Tested by: `tests.test_trends.TrendTests.test_movement_only_with_two_complete_recent_popularity_lists`.
   Tested by: `tests.test_trends.TrendTests.test_latest_baseline_never_produces_new_badges_and_unchanged_full_list_has_no_movement`.
8. Warm requests read the snapshot without aggregation/deletion. At the
   five-minute boundary, one refresh owner atomically replaces both snapshot
   slots; a concurrent loser promptly receives the committed old snapshot or
   503 if none is usable. Readers never see a half-rotated result.  
   Tested by: `tests.test_trends.TrendTests.test_warm_cache_does_not_aggregate_or_delete`,
   `tests.test_trends_postgres.TrendConcurrencyTests.test_concurrent_refresh_returns_old_snapshot_without_waiting`,
   `tests.test_trends_postgres.TrendConcurrencyTests.test_initial_concurrent_loser_is_unavailable_not_empty`.
9. Aggregate failure/timeout preserves the calculation timestamp and snapshots,
   sets the one-minute retry deadline, and does not produce an empty success.
   The database transaction remains usable after expected timeout recovery.
   Snapshots older than 15 minutes yield unavailable rather than live ranks.  
   Tested by: `tests.test_trends.TrendTests.test_refresh_failure_preserves_snapshot_and_backs_off`,
   `tests.test_trends.TrendTests.test_unavailable_and_empty_are_different`,
   `tests.test_trends_postgres.TrendConcurrencyTests.test_real_statement_timeout_rolls_back_savepoint_and_preserves_cache`.
   Tested by: `tests.test_trends.TrendTests.test_cached_snapshot_expires_just_after_fifteen_minutes`.
10. Cleanup deletes only expired rows in bounded batches. A concurrently renewed
    view survives; a capped run reports remaining work. Repeating the command is
    safe. Missing/wrong cron secrets and ordinary user credentials are rejected.
    Ranking refresh never performs backlog deletion.  
    Tested by: `tests.test_trends.TrendTests.test_cleanup_is_batched_keeps_boundary_and_recent_and_requires_cron_secret`,
    `tests.test_trends.TrendTests.test_cleanup_cap_is_observable_and_repeatable`,
    `tests.test_trends_postgres.TrendConcurrencyTests.test_cleanup_skips_concurrently_renewed_view`.
    Tested by: `tests.test_trends.TrendTests.test_management_command_reports_incomplete_cleanup_as_failure`.
11. A shared rank can hydrate different eligible representatives for different
    users without changing topic order. Saved state does not leak across users.
    A removed representative falls back within its topic; an undiscoverable
    topic is replaced with a distinct latest filler and all movement is hidden.  
    Tested by: `tests.test_trends.TrendTests.test_global_rank_preserves_personalized_representative_and_saved_isolation`,
    `tests.test_trends.TrendTests.test_hidden_snapshot_entry_is_replaced_without_false_movement`,
    `tests.test_trends.TrendTests.test_unavailable_and_empty_are_different`.
12. Android records only after three foreground seconds with the body rendered.
    Navigation away, backgrounding before qualification, recomposition, rotation,
    prefetch, cached online reading, offline reading, and identity changes have
    the exact outcomes in feature sections 2–3. Event failure never interrupts
    reading and is not queued for offline replay.  
    Tested by: `NewsViewModelTest.qualifyingReadNeedsThreeContinuousForegroundSecondsAndOnlySendsOnce`,
    `NewsViewModelTest.readIdentityChangesRestartPendingTimerAndAnalyticsFailureIsSilent`,
    `NewsApiClientTest.trendsDecodeNullableRanksAndViewRecordingCarriesOnlyIdentity`.
    Tip: verify Activity rotation and actual foreground/offline collection on a
    physical phone; the JVM tests establish the timer's state transitions.
13. Home loading, valid empty, cached stale, and failure remain distinct. Zero
    items have no rank controls; one/two have no expand control; three/four/five
    expand only to their actual length. Expansion survives refresh. Tapping an
    entry opens its real representative and Back restores Home.  
    Tested by: `PortalNavigationInstrumentedTest.homeTrendExpansionKeepsHeadlineTargets`,
    `PortalNavigationInstrumentedTest.trendsDistinguishEmptyFailureAndSparseCorpus`.
    Tip: automate full Reader-to-Home restoration in the app shell; these
    Compose cases verify section behavior and the exact article callback.
14. Returning to fresh Home reuses its local result; pull-to-refresh may request
    the endpoint but never forces a server calculation before its deadline.
    Ordinary Home news stays usable when trends fail.  
    Tested by: `NewsViewModelTest.trendsReuseFreshCacheBackOffFailuresAndDiscardExpiredResults`,
    `PortalNavigationInstrumentedTest.homePullRefreshCallsItsRefreshAction`.

## Cost and deployment evidence

Use synthetic data, not production traffic. Measure warm and refresh request
durations separately, record corpus/recent-window size, inspect PostgreSQL query
plans, and confirm only one aggregate occurs in a concurrent refresh burst.
Verify time-index migration, SQL-side distinct counting, bounded top-five
hydration, and maintenance caps. Do not confuse passing small SQLite fixtures
with a PostgreSQL concurrency or load result. Cron authorization and a successful
cleanup invocation must be checked in the deployed environment before claiming
retention is operational.

Local measurement (2026-11-25): PostgreSQL 17, synthetic 60,000 view rows
(10,000 in-window) across ten topics. Snapshot calculation took 23.06 ms;
30 warm guest payloads had a 1.04 ms median and 2.55 ms maximum, with two SQL
queries and zero aggregate queries per warm request. This measures local service
work, not HTTP/network latency or a production capacity guarantee. The planner
selected the article FK index for this corpus; the time index also exists.
Dedicated PostgreSQL tests cover real row locks and actual statement timeout;
the SQLite run deliberately skips those five PostgreSQL-only cases.

The final APK passed 18 physical-device UI cases, including sparse/empty/failure
trend states, expansion, article selection, and Home pull refresh. The shared
production API returned five unranked latest topics before activity; a real
Reader visit then created one ArticleView row. Migration 0014 and the cleanup
management command were applied to production. Daily Vercel cleanup and its
secret are configured; the scheduler's first automatic invocation remains an
operational check, distinct from the successful manual cleanup command.

## Undefined guarantees and review

This version does not establish bot resistance, exact cross-device anonymous
human identity, retrospective read history, or exact five-minute wall-clock
updates. A scheduler outage can delay physical deletion without changing the
three-hour ranking window.

- [x] Sparse fill and movement suppression follow the user's reviewed intent.
- [x] Implemented assertions name exact tests; remaining gaps stay explicit.
- [x] PostgreSQL, Android JVM/build/lint, and physical-device results recorded.
