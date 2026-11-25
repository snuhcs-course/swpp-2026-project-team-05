# Article reactions and Home highlights test specification

| Field | Value |
| --- | --- |
| Status | Draft; planned oracles, no implementation or measurement claim |
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
   Tip: add API/model tests for none → type → same type → other type → none,
   invalid input, meaningful-change time, and database enum/uniqueness constraints.
2. Guests can read counts with null selection. Protected writes require a valid
   session; invalid bearer on a public read is not silently treated as guest.
   Two accounts see their own selection and shared counts without user IDs.
   Tip: add API ownership, optional-auth, response-header, and no-identity-leak tests.
3. Readable comparison-support articles accept reactions. Hidden/deleted
   articles and inactive publishers reject reads/writes; article/account hard
   deletion cascades the rows. GET never writes reactions or view events.
   Tip: add lifecycle, eligibility, cascade, and GET-purity API tests.
4. Concurrent create/change/delete never produce duplicate rows, negative
   counts, mixed selection, or an unhandled integrity failure. Different
   accounts do not overwrite each other.
   Tip: add real PostgreSQL transaction tests for same-account simultaneous
   PUTs, PUT/DELETE, and different-account updates; do not claim SQLite proves locking.

### Issue selection

5. At T, reactions exactly at T−24h count; earlier and future-dated rows do not.
   Article publication age is irrelevant. Actually changing reaction type
   renews eligibility; same-type retries do not. Deleting/re-adding creates a new timestamp.
   Tip: add frozen-clock tests at the cutoff ±1 microsecond and for old
   articles, repeated PUT, replacement, deletion, and re-addition.
6. One account reacting on multiple articles of one cluster contributes one
   participant. Another account contributes a second. All five types have equal
   weight. Article counts remain separate from topic participant count.
   Tip: add fixtures where summing article counts or counting only positive
   reactions would choose the wrong winning topic.
7. A discoverable topic can count reactions on readable support articles;
   hidden/inactive reactions and support-only topics cannot contribute.
   Ungrouped feed articles retain independent identity.
   Tip: add clustered/ungrouped, feed/support, hidden, inactive, and deletion cases.
8. One topic is selected by participant count, newest eligible publication,
   then stable key. The existing topic representative policy and Reader route
   remain in force; subscriptions do not alter the shared topic score.
   Tip: test tie ordering, repeated equivalent reads, representative eligibility,
   two users with different subscriptions, and the one-row output limit.
9. Breaking's topic is not repeated in the adjacent Issue row; the next
   qualifying topic is used or Issue is null. No reactions means null, without
   latest-news fillers or fabricated scores. Existing trends ranks are unchanged.
   Tip: test overlap with one/two candidates, no activity, and independent
   readership ranking against the existing trend oracle.

### Breaking selection

10. Leading `[속보]`, `(속보)`, and `【속보】` tags, whitespace variants, and
    a sequence such as `[정치][속보]` qualify. Untagged prose, body-only matches,
    mid-sentence tags, and other words such as `속보성` do not.
    Tip: add parameterized title classification cases, including mixed/mismatched
    bracket pairs and blank titles; expected matches come from feature §5.
11. Exactly T is eligible, T−2h is expired, and the cutoff +1 microsecond is
    eligible. Future, hidden, inactive, and non-feed articles are excluded.
    Collection/modification time cannot revive a stale tagged article. Newest
    publication wins; equal-time IDs break ties deterministically.
    Tip: add frozen-clock API/service cases and assert the exact expires_at.

### Android and request lifecycle

12. The article body is followed by five original vectors, the five labels and
    article counts before related articles. Every type remains reachable with
    minimum 48dp targets, light/dark appearance, and enlarged system text.
    Selection/progress does not resize cells or affect stored finding offsets.
    Tip: add controlled Compose previews and assertions for five zero counts,
    9,999/10,000/12,345/100,000,000 counts with full accessibility values,
    selected/pending state, font scaling, and actual device captures.
13. Tapping the selected type removes it; a different type replaces it. Pending
    input is blocked only for the same article. Confirmed response replaces the
    entire count/selection state; failed or lost responses do not add phantom
    counts and retries remain idempotent.
    Tip: add gated ViewModel/API-client tests for duplicate taps, response loss,
    success, mutation failure, and explicit retry.
14. A saving/failing does not change B's buttons or error. A → B → Back restores
    A's confirmed state without extra fresh-cache requests. Account change
    clears personal state and prevents late old-viewer responses from leaking.
    Tip: add out-of-order response, cache-age 60s boundary, back-stack, and
    account-switch tests using deferred responses rather than sleeps.
15. Guest interaction opens contextual authentication and preserves the Reader;
    dismissal and successful authentication never post the attempted reaction.
    Offline reading omits online reactions and creates no pending write queue.
    Tip: add guest/login/registration/cancellation and offline Reader cases,
    retaining existing Saved and analysis acceptance checks.
16. Reaction loading/failure does not block article/analysis or resemble zero
    counts. Home highlights load independently; successful empty, failure and
    expired cached data do not become fake labels or a full-screen Home failure.
    Tip: add controlled loading, error, partial-feature availability, and
    no-results fixtures for Reader and Home.
17. Home entry/resume/pull-to-refresh obey local freshness and no-polling rules.
    A successful reaction invalidates only the next Home highlights read.
    Breaking expires on an active screen and on resume, including phone-clock
    changes, while ordinary scrolling and navigation remain usable.
    Tip: add fake-clock lifecycle and API request-count tests; include a Home
    capture where Breaking disappears at expiry and Issue is absent/present.

### Performance and deployment

18. Counts use grouped aggregation; recent Issue reads filter by indexed time.
    Database query count does not grow with the number of candidate topics,
    and no client or server loads full reaction/body lists to rank them.
    Tip: add query-count assertions for small/expanded fixtures and inspect a
    representative PostgreSQL query plan before considering a separate cache.
19. The forward migration adds only the reaction table/constraints/indexes and
    preserves articles, analyses, Saved, follows, trends, and existing data.
    API deployment precedes an APK that depends on these endpoints.
    Tip: test migration from the current schema, run affected full checks, then
    verify live read responses without seeding fake production activity.

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
- [ ] Review the full draft's one-reaction rule, Reader UI, time semantics,
  representative policy, overlap behavior, and failure boundaries.
- [ ] Implement executable cases before replacing any `Tip` with `Tested by`.
- [ ] Record measured outcomes separately; this draft reports no passing tests.
