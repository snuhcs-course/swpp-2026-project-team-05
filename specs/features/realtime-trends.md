# Recent-read trends

| Field | Value |
| --- | --- |
| Status | Implemented in the Django API and Android; verification gaps are tracked in the test specification |
| Date | 2026-11-25 |
| Scope | Shared Django/PostgreSQL API; Android Home first |
| Related contracts | [Story feed](story-feed.md), [Guest reading](guest-offline-reading.md), [UI](../ui/news-app-v1-ui.md), [Tests](../testing/realtime-trends.spec.md) |

## 1. Product rule

Home's `실시간 트렌드` shows five distinct topics when the eligible corpus has
at least five. Topics with qualifying readership come first, ordered by distinct
viewers over the preceding three hours; latest topics fill the remaining slots
with a `최신` label and no rank. Reads are recorded against individual articles;
`Article.story_cluster` provides the existing topic boundary. A viewer reading
three comparison articles about one topic contributes one viewer to that topic.
An unclustered article is its own topic, as in the story feed.

This is an approximate readership signal inside FrameLESS. A viewer identifier
is not proof of a unique human or of editorial quality. The window concerns
**when the article was read**, not when it was published. An older article can
therefore trend if it is being read now. Public/category feeds retain their
publication ordering; only this Home section uses popularity.

Initial policy constants:

| Setting | Value |
| --- | --- |
| Qualifying read | Article body rendered while Reader is foreground for 3 uninterrupted seconds |
| Rolling window | 3 hours, based on server-controlled UTC time |
| Ranked topics | At most 5, each with at least 1 qualifying distinct viewer |
| Latest-news fill | Fill to 5 distinct eligible topics; no rank or movement on fillers |
| Refresh eligibility | 5 minutes after the last successful calculation |
| Retry after a handled refresh failure | 1 minute |
| Maximum display age | 15 minutes from successful calculation |
| View retention cleanup | Once daily, independent of Home traffic |

These are operating rules, not performance measurements. No
hourly counters, cumulative article counter, personalization score, or ranking
of publisher quality is introduced.

## 2. What counts as a read

- Record once per Reader visit after the three-second condition. Time before
  body rendering, an error screen, prefetching, and background time do not count.
  Leaving the foreground before qualification resets the timer. Rotation or
  recomposition does not create a new visit or a second event.
- Opening an article through search, Saved, a publisher, a trend, or comparison
  navigation uses the same rule. Opening comparison evidence alone is not a
  visit to that article's full Reader.
- Online cached article bodies may qualify. Offline reads do not queue or
  replay analytics when connectivity returns. Public reading never depends on
  recording succeeding, and analytics failure causes no toast or login prompt.
- The client sends the event separately from article/analysis loading. It does
  not await it to render or navigate and does not run an automatic retry queue.
- Each valid event sets the last-read time for that viewer/article. Reopening
  can extend presence in the rolling window, but never adds another viewer.
  Concurrent or delayed writes cannot move the stored timestamp backwards.
- An identity change before qualification restarts the timer with the active
  identity. After a visit has sent its one event, login/logout does not send a
  second event for that same visit. Never send a timer's captured expired token.
- The server accepts no client-provided account ID, count, or read timestamp.
  Article-detail GETs remain read-only and never create a view.

## 3. Viewer identity

For a valid bearer session, derive `viewer_key` from the authenticated account.
All devices for that account share the key. With no bearer header, accept a
client-generated random UUID in `X-Guest-Viewer`; derive a separately namespaced
guest key. Malformed UUIDs are rejected. A supplied invalid bearer token follows
the existing 401 contract and never silently becomes a guest event.

Android persists its guest UUID locally, outside Room article content. A
future Web collector can use a same-origin persisted random UUID. Neither
creates a `UserAccount`, an anonymous-session table, or a new login capability.
Only opaque derived viewer keys are stored with views; do not log raw guest
identifiers, tokens, account names, email, IP addresses, or article text.

Guest and account events are not retrospectively merged on login. Clearing
storage, changing browser/device while a guest, or switching identity can count
as another viewer. This limitation is preferable to fingerprinting or an
identity-merging subsystem. The API cannot prove that an untrusted client really
read the article; strong anti-fraud analytics is outside this first version.

## 4. Database: two small tables

### ArticleView

| Field | Purpose |
| --- | --- |
| `id` | Ordinary primary key |
| `article_id` | FK to Article, cascade on article deletion |
| `viewer_key` | Opaque, server-derived account/guest key |
| `last_viewed_at` | Last qualifying server timestamp |

Constraints/indexes: unique `(viewer_key, article_id)` and B-tree
`(last_viewed_at, article_id)`, plus the normal FK index. Use an atomic upsert,
not a read-then-insert sequence. There is no row for each repeat visit and no
serialized article body, title, or analysis in this table.

Article audience is the number of its rows inside the rolling window. Topic
audience is `COUNT(DISTINCT viewer_key)` over those rows joined to Article and
grouped by cluster ID, with article ID as the singleton fallback. Do not sum
article counts to obtain the topic count. Perform the aggregation in SQL, not
by fetching viewer rows into Python.

Views may be recorded for any ordinarily readable article from an active
publisher, including comparison-support articles. A topic can rank only if it
currently has at least one visible, feed-visible article from an active
publisher. Hidden/deleted articles and inactive publishers contribute no views.
Support-only singleton articles have no eligible feed representative and do
not rank.

### TrendState

One fixed row, seeded by migration; this is the shared database cache and refresh
coordination point. It contains:

- `current_snapshot`: nullable JSON containing calculation time and up to five
  `{story_key, kind, rank, viewer_count}` entries; `kind` is `POPULAR` or
  `LATEST`, and rank/count are null for latest-news fillers;
- `previous_snapshot`: the preceding successful snapshot, with the same shape;
- `next_refresh_at`: earliest time another request may attempt refresh.

Null means never calculated; a successful snapshot with `items: []` means no
eligible news topics, even for latest-news fill. Zero qualifying reads normally
produces latest-news entries. Keep only these two slots. Viewer keys and personalized
article summaries never appear in a snapshot or public response.

## 5. Shared refresh, bounded request cost

`GET /api/v1/trends` uses the same global ranks for every viewer:

1. Read the single state row. When refresh is not due, serve its usable snapshot
   without aggregating or deleting views.
2. When due, try to lock **only the state row** inside a transaction using
   `select_for_update(skip_locked=True)`. Recheck the deadline after locking.
3. Exactly one request calculates audience and top-five order in SQL, fills
   remaining slots with a bounded latest-topic query, rotates current into
   previous, and writes the new current snapshot atomically. Do
   not lock all articles or view rows during the aggregate.
4. Requests that cannot acquire the row immediately serve the last committed
   snapshot. They never wait for the ranking transaction or start another
   aggregate. With no usable snapshot, return the temporary-unavailable state.
5. A handled calculation failure preserves both snapshots and sets a one-minute
   retry deadline. A rolled-back calculation never becomes an empty successful
   result and never advances the public calculation timestamp.

Use a transaction-local PostgreSQL statement timeout for the aggregate
(initial cap: 2 seconds); recover from an expected timeout through a savepoint
before persisting the retry deadline. Do not keep a failed transaction alive,
retry the aggregate in a loop, use process-local locks as global coordination,
or rely on a background thread surviving the HTTP response. The triggering
request can incur calculation latency; fetch trends independently so it never
holds up the ordinary Home feed. Database outages use the ordinary failure
path rather than pretending this design eliminates infrastructure failures.

The five-minute rule is request-driven, not a promise of wall-clock cron
execution: with no requests, no new ranking is computed. Responses carry the
actual calculation time. A snapshot older than 15 minutes is not displayed.
After a gap longer than 15 minutes, the first successful result has no movement
indicators because there is no recent comparison baseline.

## 6. Retention without deleting on the read path

An authenticated internal maintenance route is invoked by one daily Vercel cron
job. It removes `ArticleView` rows older than three hours at cleanup time. This
is deliberately separate from ranking refresh: a Home request never pays for
an unbounded deletion backlog.

- Use the time index and bounded batches of 1,000 oldest expired rows, skipping
  rows already being written. Recheck expiry when deleting so a renewed view
  survives. Commit between batches; do not lock the whole view table.
- Cap each invocation at 20 seconds, log deleted count, duration, and whether
  work remains. A cap/failure is observable and can be rerun with the same
  management command; it is not reported as complete. Never expose viewer IDs.
- The route accepts only the deployment's cron secret, fails closed if it is
  missing, and rejects ordinary account credentials. GET is permitted for this
  cron-only route because the scheduler invokes GET; it is not a public cleanup
  action. Keep rank recalculation off this route.
- Retention is **not a hard three-hour physical TTL**. Normal daily cleanup
  retains approximately a day plus the three-hour window, with scheduler delay;
  failed or incomplete cleanup can retain more and must be checked in logs.
  Expired rows never count even when they have not yet been deleted.

This replaces the earlier discussion's immediate three-hour deletion proposal.
There is no lifetime history product, but timely physical deletion is an
operational task, not something an index or upsert guarantees by itself.

## 7. Ordering, ties, and movement

Aggregate every eligible topic before limiting; never rank just the articles
already loaded by the current client. Order by:

1. distinct viewers descending;
2. previous popularity rank ascending when a recent baseline exists; topics not
   present there follow the previously ranked topics with the same count;
3. `story_key` ascending as the final deterministic tie-breaker.

Ranks are unique display positions 1 through N, not competition-style shared
positions (`1, 1, 3`). A tie at the fifth-place cutoff does not expand the list.
If tied topics have no previous rank, their stable keys decide the cutoff.
No request-time randomness, artificial recency bonus, or hidden minimum count
above one is applied.

Movement is enabled only when the current and recent previous result each have
five eligible `POPULAR` entries, with no latest-news fillers or removed entries.
Sparse popularity, latest-only results, the first snapshot, and a baseline older
than 15 minutes suppress **all** movement indicators, including NEW. Latest-news
fillers never serve as a previous popularity rank. When comparison is enabled:

- both present: `previous_rank - current_rank`; positive is `↑N`, negative is
  `↓N`, zero has no indicator;
- absent from previous but present now: `NEW` (new to the displayed list, not a
  claim that the article was just published);
- unchanged rank: no indicator.

| Distinct qualifying topics | Result |
| --- | --- |
| 0 | Up to five latest topics; no ranks or movement |
| 1–4 | Those ranked topics, then latest topics to fill five; no movement anywhere |
| 5 or more | Five popular topics; movement only with a complete, recent previous baseline |

Several read articles in one cluster can still produce only one ranked topic.
Choose latest fillers using ordinary story-feed publication ordering, excluding
already selected story keys. Cache these keys in the same snapshot; warm
requests do not repeat a latest-feed query. Fillers do not get an invented view
count, popularity rank, or NEW badge, and never become previous popularity ranks.
If the eligible corpus contains fewer than five topics, show only those topics.
Zero eligible topics is the successful empty state. Never duplicate a topic or
insert demo data to reach five. Collapse to two items; offer expansion only when
more than two actual entries exist.

## 8. API and presentation

### Record

`POST /api/v1/articles/{article_id}/view`, empty JSON body. Use optional bearer
auth or the guest header above. Success is 204, including a valid repeat view.
Unknown/unreadable articles return 404; malformed identity/body returns 400;
an invalid supplied bearer returns 401. Recording uses only the single-article
upsert and normal validation; it never calculates ranks or cleans history.

### Read

`GET /api/v1/trends`, optional bearer auth, no category/scope/cursor parameters.
The server fixes the three-hour window and maximum five topics. Response:

```json
{
  "window_hours": 3,
  "calculated_at": "2026-11-25T09:00:00Z",
  "is_stale": false,
  "items": [
    {
      "rank": 1,
      "kind": "POPULAR",
      "previous_rank": null,
      "movement": null,
      "story": {
        "story_key": "cluster:example-uuid",
        "representative_article": {
          "id": "example-article-uuid",
          "publisher": {"code": "yna", "name": "연합뉴스", "logo_url": null},
          "title": "기사 제목",
          "category": "SOCIETY",
          "published_at": "2026-11-25T08:00:00Z",
          "analysis_state": "READY",
          "is_saved": false
        },
        "publisher_count": 1,
        "publishers": [{"code": "yna", "name": "연합뉴스", "logo_url": null}],
        "latest_published_at": "2026-11-25T08:00:00Z"
      }
    }
  ]
}
```

Every item includes `kind`: `POPULAR` or `LATEST`; latest items have null rank,
previous_rank, and movement. `story` is the ordinary structured story-feed
object. `movement` is `UP`, `DOWN`,
`NEW`, or null; `previous_rank` is null when no comparable previous entry exists.
The API does not expose viewer keys or audience counts. An empty successful
response has a real `calculated_at` and `items: []`. With no usable snapshot,
return 503 `TRENDS_UNAVAILABLE`, optionally with `Retry-After: 60`.

Ranks/counts are shared. Hydrate only the five selected topics using the story feed's
existing representative selection, eligibility, publisher metadata, and saved
state rules. Viewer subscriptions can change the representative headline, not
the topic rank. Personalized responses must not enter a shared HTTP/CDN cache;
use private/no-store headers and the existing Authorization vary convention.
If an entry loses all eligible representatives after snapshot creation, omit it,
fill the gap with an eligible latest topic through one bounded query, and suppress
movement for the result. Keep remaining popularity ranks until recalculation.

Android preserves the existing compact Home layout and ordinary Reader/Back
flow. Do not render calculation times, `최근 3시간`, or `최근 집계` captions;
the payload retains those fields solely for cache age, expiry, and backoff.
Expand/collapse is offered only for more than two
returned entries and preserves current expansion state when data refreshes.
Use standard vector arrows only for real movement. Do not add an inert info
badge or expose SQL/internal IDs. Latest fillers show `최신` instead of a rank.

Load trends independently on Home entry/resume and pull-to-refresh. Reuse a
fresh local result for five minutes; do not poll in the background or on each
recomposition/Reader return. On refresh failure retain only a still-usable
cached result with its actual timestamp retained in the model. Initial load, successful empty, and
failure are separate states; an inline retry never bypasses server backoff.
Offline Home keeps its existing Saved fallback and does not show cached ranks
as live data.

## 9. Implementation boundary and checks

The implementation adds migration `0014_recent_read_trends`, an isolated
`server/trends.py` service, two public API routes, one cron-only maintenance
route/management command, and the Android repository, Reader collector, and
independent Home state. It reuses current auth, story
hydration, networking, and navigation; do not add a generic telemetry framework.
The API is client-neutral. Web collection/Home redesign is a later integration;
initial rankings reflect only clients that actually send qualifying events.

Release checks cover duplicate upserts, exact three-hour expiry, topic-level
distinct viewers, sparse/empty results, ties/cutoff, rank movement, failure and
concurrent refresh, cleanup versus a renewed view, and Android lifecycle.
Validate query shape with PostgreSQL EXPLAIN on synthetic recent/expired data;
measure warm/cold request latency and refresh/cleanup duration. Verify the time
index exists, warm requests never aggregate, only top-five topics are hydrated,
and ordinary article GETs have no new writes. Do not require an index scan for
tiny fixtures when the optimizer reasonably selects a sequential scan.

No Redis, Celery, hourly aggregation table, per-user rank cache, historical
dashboard, WebSocket, or perpetual polling is part of this version. Add a
different aggregation strategy only if measured recent-window volume cannot
meet the bounded refresh budget. Zero infrastructure cost/load is not claimed.

Implementation references: [Django row locks and transaction tests](https://docs.djangoproject.com/en/5.2/ref/models/querysets/#select-for-update),
[PostgreSQL nonblocking row locks](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE).
The daily-only maintenance schedule fits the documented [Vercel Hobby cron limits](https://vercel.com/docs/cron-jobs/usage-and-pricing);
it does not assume a paid five-minute scheduler.
