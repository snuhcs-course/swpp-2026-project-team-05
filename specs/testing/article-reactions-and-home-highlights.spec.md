# Article reactions and Home highlights test specification

| Field | Value |
| --- | --- |
| Status | Implemented; automated checks and remaining integration gaps recorded below |
| Owner | News interaction feature owner |
| Source requirements | [Article reactions and Home highlights](../features/article-reactions-and-home-highlights.md) |
| Date | 2026-11-25 |

## Scope and preconditions

Freeze server time T in isolated fixtures. Provide two accounts, three reaction
types within the five-type enum, feed-visible articles, readable support
articles, hidden/inactive articles, at least two clusters, and an unclustered
article. Test every reaction type as an equivalent valid partition; use real
five-type response keys. No production reactions, publisher fetch, live model
call, or altered live article publication time is needed.

The source feature defines current article counts, trailing-24-hour distinct
topic participants, a separate two-hour Breaking cutoff, and per-article UI
state. Current readership-trends tests remain authoritative for their unchanged
three-hour ranking.

## Postconditions

### Storage, ownership, and API

1. One account has at most one row per article. All five types are accepted;
   unknown types and malformed bodies are rejected without modifying state.
   Type change updates reaction time; same-type PUT preserves it and repeated DELETE is
   idempotent. Counts reflect only current rows and include all zero keys.
   Tested by: `tests.test_reactions.ReactionTests.test_current_counts_selection_and_idempotent_transitions`,
   `tests.test_reactions.ReactionTests.test_type_change_refreshes_time_but_get_does_not`,
   `tests.test_reactions.ReactionTests.test_auth_ownership_headers_and_invalid_inputs`,
   `tests.test_reactions.ReactionTests.test_database_constraints_and_safe_failures`.
2. Guests can read counts with null selection. Protected writes require a valid
   session; invalid bearer on a public read is not silently treated as guest.
   Two accounts see their own selection and shared counts without user IDs.
   Tested by: `tests.test_reactions.ReactionTests.test_auth_ownership_headers_and_invalid_inputs`.
3. Readable comparison-support articles accept reactions. Hidden/deleted
   articles and inactive publishers reject reads/writes; article/account hard
   deletion cascades the rows. GET never writes reactions or view events.
   Tested by: `tests.test_reactions.ReactionTests.test_support_hidden_inactive_missing_and_cascades`,
   `tests.test_reactions.ReactionTests.test_type_change_refreshes_time_but_get_does_not`.
4. Concurrent create/change/delete never produce duplicate rows, negative
   counts, mixed selection, or an unhandled integrity failure. Different
   accounts do not overwrite each other.
   Tested by: `tests.test_reactions_postgres.ReactionConcurrencyTests.test_same_account_simultaneous_creates_and_put_delete_are_serialized`,
   `tests.test_reactions_postgres.ReactionConcurrencyTests.test_two_accounts_keep_two_independent_choices`
   on isolated PostgreSQL, not SQLite.

### Issue selection

5. At T, reactions exactly at T−24h count; earlier and future-dated rows do not.
   Article publication age is irrelevant. Actually changing reaction type
   renews eligibility; same-type retries do not. Deleting/re-adding creates a new timestamp.
   Tested by: `tests.test_reactions.ReactionTests.test_issue_window_eligibility_ties_and_zero`,
   `tests.test_reactions.ReactionTests.test_issue_time_changes_and_deletion_follow_current_state`.
6. One account reacting on multiple articles of one cluster contributes one
   participant. Another account contributes a second. All five types have equal
   weight. Article counts remain separate from topic participant count.
   Tested by: `tests.test_reactions.ReactionTests.test_issue_distinct_users_support_and_equal_types`.
7. A discoverable topic can count reactions on readable support articles;
   hidden/inactive reactions and support-only topics cannot contribute.
   Ungrouped feed articles retain independent identity.
   Tested by: `tests.test_reactions.ReactionTests.test_issue_distinct_users_support_and_equal_types`,
   `tests.test_reactions.ReactionTests.test_support_only_and_inactive_topics_cannot_displace_a_discoverable_topic`.
8. One topic is selected by participant count, newest eligible publication,
   then stable key. The existing topic representative policy and Reader route
   remain in force; subscriptions do not alter the shared topic score.
   Tested by: `tests.test_reactions.ReactionTests.test_issue_stable_ties_and_personalization_do_not_change_the_topic_score`,
   `tests.test_reactions.ReactionTests.test_issue_window_eligibility_ties_and_zero`.
9. Breaking's topic is not repeated in the adjacent Issue row; the next
   qualifying topic is used or Issue is null. No reactions means null, without
   latest-news fillers or fabricated scores. Existing trends ranks are unchanged.
   Tested by: `tests.test_reactions.ReactionTests.test_breaking_keyword_boundaries_overlap_and_exact_article`,
   `tests.test_reactions.ReactionTests.test_highlights_empty_errors_and_bounded_queries`;
   the unchanged readership suite remains `tests.test_trends`.

### Breaking selection

10. Any title containing the literal substring `속보` qualifies, including
    unbracketed `속보 - 제목`, mid-title mentions, tagged forms, and `속보성`.
    Titles without that substring, empty titles, and body-only matches do not.
    Tested by: `tests.test_reactions.ReactionTests.test_breaking_keyword_boundaries_overlap_and_exact_article`,
    `tests.test_reactions.ReactionTests.test_breaking_just_inside_window_sql_keywords_and_stable_tied_ids`.
11. Exactly T is eligible, T−2h is expired, and the cutoff +1 microsecond is
    eligible. Future, hidden, inactive, and non-feed articles are excluded.
    Collection/modification time cannot revive a stale matching article. Newest
    publication wins; equal-time IDs break ties deterministically.
    Tested by: `tests.test_reactions.ReactionTests.test_breaking_keyword_boundaries_overlap_and_exact_article`,
    `tests.test_reactions.ReactionTests.test_breaking_just_inside_window_sql_keywords_and_stable_tied_ids`.

### Android and request lifecycle

12. The article body is followed by five original vectors, the five labels and
    article counts before related articles. Every type remains reachable with
    minimum 48dp targets, light/dark appearance, and enlarged system text.
    Selection/progress does not resize cells or affect stored finding offsets.
    Component geometry, both appearances, 1.6× text, and exact accessibility
    counts are covered by `com.swpp.team5.frameless.ui.screens.ArticleReactionsInstrumentedTest`;
    formatting boundaries by `com.swpp.team5.frameless.ui.viewmodel.ArticleInteractionTest.countLabelsUseFloorAndKeepIntegerPrecision`.
    Tip: add a full Reader integration assertion for placement between body
    and related articles; component captures do not prove that placement.
13. Tapping the selected type removes it; a different type replaces it. Pending
    input is blocked only for the same article. Confirmed response replaces the
    entire count/selection state; failed or lost responses do not add phantom
    counts and retries remain idempotent.
    Tested by: `com.swpp.team5.frameless.ui.viewmodel.ArticleInteractionTest.selectionCancellationRetryAndFreshCacheUseConfirmedState`,
    `com.swpp.team5.frameless.data.NewsApiClientTest.reactionsAndHighlightsUseTheirOwnResourcesAndValidateResponses`.
14. A saving/failing does not change B's buttons or error. A → B → Back restores
    A's confirmed state without extra fresh-cache requests. Account change
    clears personal state and prevents late old-viewer responses from leaking.
    Tested by: `com.swpp.team5.frameless.ui.viewmodel.ArticleInteractionTest.otherArticleAndViewerAreIsolatedFromLateWrites`,
    `com.swpp.team5.frameless.ui.viewmodel.ArticleInteractionTest.selectionCancellationRetryAndFreshCacheUseConfirmedState`.
15. Guest interaction opens contextual authentication and preserves the Reader;
    dismissal and successful authentication never post the attempted reaction.
    Offline reading omits online reactions and creates no pending write queue.
    Tip: add guest/login/registration/cancellation and offline Reader cases,
    retaining existing Saved and analysis acceptance checks.
16. Reaction loading/failure does not block article/analysis or resemble zero
    counts. Home highlights load independently; successful empty, failure and
    expired cached data do not become fake labels or a full-screen Home failure.
    Component failure/unknown counts and nullable Home rows are covered by
    `com.swpp.team5.frameless.ui.screens.ArticleReactionsInstrumentedTest.unknownCountsAreNotZeroAndFailuresHaveAnIndependentRetry`
    and `com.swpp.team5.frameless.ui.screens.PortalNavigationInstrumentedTest.homeHighlightsUseOnlyServerCandidatesAndKeepExactReaderTargets`.
    Tip: add a full Reader test with a failed reaction request while article
    text and analysis succeed independently.
17. Home entry/resume/pull-to-refresh obey local freshness and no-polling rules.
    A successful reaction invalidates only the next Home highlights read.
    Breaking expires on an active screen and on resume, including phone-clock
    changes, while ordinary scrolling and navigation remain usable.
    Fake elapsed time, no polling, cache expiry, and explicit invalidation are
    covered by `com.swpp.team5.frameless.ui.viewmodel.ArticleInteractionTest.highlightsExpireWithElapsedTimeAndDoNotPollOrUseExpiredFailureCache`
    and `com.swpp.team5.frameless.ui.viewmodel.ArticleInteractionTest.resumingAfterSleepExpiresBreakingEvenWhenTheCacheIsStillFresh`.
    Tip: add an activity pause/resume regression with an intervening phone-clock
    change; the controller test does not drive Android lifecycle events.

### Performance and deployment

18. Counts use grouped aggregation; recent Issue reads filter by indexed time.
    Database query count does not grow with the number of candidate topics,
    and no client or server loads full reaction/body lists to rank them.
    PostgreSQL plan inspection is recorded as `MANUAL-REACTIONS-PLAN-20261125` below.
    Tested by: `tests.test_reactions.ReactionTests.test_highlights_empty_errors_and_bounded_queries`.
19. The forward migration adds only the reaction table/constraints/indexes and
    preserves articles, analyses, Saved, follows, trends, and existing data.
    API deployment precedes an APK that depends on these endpoints.
    Tested by: `MANUAL-REACTIONS-ROLLOUT-20261125` below.

## Undefined behavior and non-goals

- Topic groups are the stored groups; this feature does not judge or repair
  semantic cluster quality or infer unrelated events from reaction text.
- Counts are server-confirmed snapshots, not a realtime websocket stream.
  Several clients may observe different counts until their next refresh.
- Different clients changing the same account/article use the last serialized
  server operation; no client revision-history or conflict-resolution UI exists.
- No semantic quality, editorial endorsement, manipulation resistance beyond
  account uniqueness, or future-scale latency claim is established by these tests.

## Human review checkpoint

- [x] The user requested five reactions with original vector artwork, real Issue
  selection, and the previously discussed Breaking rules.
- [x] The user selected topic-level, recent-24-hour Issue aggregation.
- [x] The user authorized implementation after the specification draft.
- [x] Map executable cases to the reviewed rules; keep integration gaps as `Tip`.
- [x] Record measured outcomes separately from intended behavior.

## Verification record — 2026-11-25

Checks at reaction-feature rollout, before the later main telemetry integration:

- Django full suite: 468 discovered, 461 passed, seven PostgreSQL-only skips.
  The separate isolated PostgreSQL reaction run passed all 15 cases, including
  concurrent same-account and different-account writes. Existing PostgreSQL
  readership tests also passed with the initial reaction regression run.
- Android: 179 JVM tests passed; debug APK, AndroidTest APK, lint, and existing
  coverage gates passed. The connected physical phone passed 29 instrumented
  cases across reactions, Home/navigation, auth appearance, comparisons, and
  the reading guide. No emulator or browser test was used.
- Reaction captures were inspected in light/dark and at 360dp with 1.6× text.
  All five options remain present; wrapping labels keep counts aligned and
  selection/progress retains cell width. These are component and regression
  checks, not a claim that the integration gaps above are covered.

`MANUAL-REACTIONS-PLAN-20261125`: isolated PostgreSQL 16.2 with 8,000 current
reaction rows, of which 100 were within the window. Home returned 100 distinct
participants using four guest-read SQL queries. `EXPLAIN (ANALYZE, BUFFERS)`
selected `frameless_reaction_recent` for the recent-row filter. Candidate
aggregate statements took 0.280 ms and 0.045 ms on this small local fixture;
these are query-plan checks, not a production latency claim. The separate
corpus-expansion regression checks that query count stays constant.

`MANUAL-REACTIONS-ROLLOUT-20261125`: applied migration `0015_article_reactions`
to the shared database. Article, analysis-record, and Saved counts stayed at
1,277 / 4 / 12; the new reaction table started empty. The production API reached
Ready before APK installation. Public reaction reads returned all five numeric
keys, null personal selection, and `private, no-store`; Home returned null
Breaking and Issue because neither had a qualifying candidate. No reaction or
fake publication date was inserted into shared data for this check.
