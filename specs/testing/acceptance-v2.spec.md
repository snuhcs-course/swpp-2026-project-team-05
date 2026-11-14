# News app acceptance plan v2 — guest and offline reading

| Field | Value |
| --- | --- |
| Status | Draft for Iteration 4 review |
| Owner | Team |
| Source requirements | [`../features/guest-offline-reading.md`](../features/guest-offline-reading.md) |
| Last reviewed | 2026-11-14 |

This plan extends the product after the frozen Iteration 3 plan in
[`acceptance-v1.spec.md`](acceptance-v1.spec.md). The implementation is
available for review, but these acceptance cases are not considered frozen or
passed until the team runs them. It does not rewrite the v1 results. Once
reviewed and frozen, failures are fixed in the product rather than by weakening
a criterion.

## UAT-07 — Read FrameLESS without an account

**Given** a clean Android install and a clean Web browser with no session,
**when** the tester launches each client, searches, opens an article, and selects
an analyzed expression, **then** Latest, search, the complete stored article,
and its reviewed analysis work without registration or a guest account being
created.

Procedure: verify no bearer token or user row is created; switch Politics and
Society; search a known title; open one source-only finding and one
comparison-backed finding; follow one original link; confirm the Web and Android
public flows use the same stored article and analysis.

## UAT-08 — Enter and leave authenticated features safely

**Given** a guest viewing a public article, **when** the tester selects Saved,
Following, source management, or the Reader save control, **then** the client
explains that sign-in is required and preserves the current public route.

Procedure: trigger each protected entry on one client; dismiss once and verify
the article/feed is unchanged; sign in from the Reader prompt and verify the
same article returns without being saved automatically; save it explicitly;
log out and verify the article remains readable as a guest while personal flags
and Following state disappear.

## UAT-09 — Reopen cached reading content offline on Android

**Given** an online Android session that has loaded both categories and opened
one article with comparison-backed analysis, **when** the tester enables airplane
mode and restarts the app, **then** locally known Latest rows appear and the
opened article and matching analysis remain readable with a clear offline
notice.

Procedure: record the article ID and marker; enable airplane mode; restart;
open the cached article and the same marker; verify the exact cached comparison;
attempt a non-cached article or fresh search and verify a connection-specific
retry state rather than false empty results or infinite loading.

## UAT-10 — Read the locally verified Saved snapshot offline

**Given** an authenticated Android user with at least two server Saved articles,
one opened locally and one known only by its Saved summary, **when** the tester
goes offline and opens Saved, **then** both cached rows remain visible, the first
article opens, and the second explains that it must be opened online once.

Procedure: while still offline, attempt save and remove and verify neither the
visible state nor Room relationship changes; reconnect, refresh Saved, complete
one mutation, and verify the local snapshot changes only after server success.

## UAT-11 — Preserve account isolation on one installation

**Given** two accounts with different Saved and followed-source state, **when**
the tester switches accounts on one Android installation and one Web browser,
**then** neither client displays the other account's personalized state.

Procedure: populate account A, log out, verify guest reading still works, sign
in as account B, and inspect feed, Saved, Following, and Reader save controls.
On Android, go offline after account B's snapshot is loaded and verify only B's
locally verified Saved rows are available.

## Freeze and results policy

- Team approval changes the status to `Intent-reviewed (frozen)` and records the
  reviewed commit.
- Existing v1 cases remain the historical Iteration 3 plan; changed product
  behavior is represented by these new cases instead of rewriting history.
- Each run records client/build, API revision, local database version, tester,
  date, pass/fail, and evidence in the testing documentation.
