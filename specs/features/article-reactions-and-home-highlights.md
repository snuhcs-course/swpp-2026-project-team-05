# Article reactions and Home highlights

| Field | Value |
| --- | --- |
| Status | Draft for intent review; not implemented |
| Date | 2026-11-25 |
| Clients | Android Reader and Home; shared Django API |
| Database | Existing PostgreSQL, one new reaction table |
| Related contracts | [Product](../product/news-app-v1.md), [UI](../ui/news-app-v1-ui.md), [trends](realtime-trends.md), [guest/offline](guest-offline-reading.md) |

## 1. Product decision and scope

Add five article reactions to the Android Reader. Home's `이슈` becomes a
reaction-based topic selection; `속보` becomes a recent, explicitly tagged
article selection. The existing readership-based `실시간 트렌드` remains
independent.

The user selected **topic-level, trailing 24-hour** Issue aggregation on
2026-11-25. Article reactions remain article-specific. The rest of this draft
provides concrete rules for review before implementation.

This slice does not add a Web reaction interface, notifications, comments,
public user activity, custom reactions, a recommendation model, or offline
writes. It does not change findings, A–G classifications, comparison evidence,
article feed membership, or ordinary feed ordering. UI/API behavior described
here is planned, not evidence of a completed migration or deployment.

## 2. Reactions on one article

### 2.1 Types and selection

Keep this fixed order in both the API contract and the Reader:

| API value | Android label | Original vector direction |
| --- | --- | --- |
| `RECOMMEND` | 추천해요 | Spark with a small upward gesture |
| `LIKE` | 좋아요 | Soft, open heart |
| `MOVED` | 감동이에요 | Warm expression with a small sparkle |
| `ANGRY` | 화나요 | Restrained angular expression |
| `SAD` | 슬퍼요 | Downturned expression with a single tear |

A signed-in account has **zero or one reaction per article**. Selecting an
unselected type creates the reaction; selecting another type replaces it;
selecting the current type removes it. A reaction describes the reader's
response to the article and does not endorse a FrameLESS analysis conclusion.

All five reactions count equally toward Issue selection. Negative reactions
are not subtracted, and Recommend/Like do not receive extra weight. Saving an
article or subscribing to its publisher does not add a reaction.

### 2.2 Eligibility and counts

- Every readable article can receive reactions, including comparison-support
  articles opened through the existing Reader. Feed membership is not required.
- Hidden/deleted articles and articles from inactive publishers return not
  found for reaction reads and mutations.
- Displayed counts describe **current active reaction rows on that article**,
  across all time, split into the five types. They are not cumulative click
  counters and are not the 24-hour Issue score.
- A change moves one count from the old type to the new type. Removal subtracts
  one from its type. Counts never become negative; repeated requests do not
  create extra votes.
- Guests can read aggregate counts but have no selected reaction. Only an
  authenticated account may create, change, or remove a reaction. Do not expose
  user identities or a list of people behind a count.

### 2.3 Time semantics

Record `reacted_at` when an active reaction is first created or its type actually
changes. Repeating the same-type PUT preserves that time: a network retry is
not fresh participation. Deleting and later adding a reaction creates a new
active row with a new time. In every case, one account contributes at most one
participant to a topic's current Issue score.

This is current reaction state, not a historical event log. No separate
click-history table, hourly buckets, lifetime counters, or periodic reaction
deletion job is required.

## 3. Reader UI and lifecycle

Place one reaction module **immediately after the full article body and before
any related-outlet article list**, inside the existing reading scroll. It also
appears in internally opened comparison articles. Do not put reaction controls
in article lists, the annotation dock, the comparison sheet, or a sticky bar.
The module is outside stored article text and finding-offset calculations.

Use the short heading `이 기사를 어떻게 보셨나요?`, a quiet divider, and five
equal-width cells. Each cell has an original icon, its label, and a count;
confirmed zero counts are visible. Selection uses a restrained outline or inset
surface without changing cell size. All cells retain at least 48dp touch targets.
Labels must stay legible on a 360dp phone and with enlarged system text; cells
may grow vertically rather than clipping labels or hiding a reaction type.
Counts below 10,000 use their exact decimal value. Larger visible counts use
`만`/`억` with at most one fractional digit, rounded down and with a trailing
`.0` omitted; for example, 12,345 becomes `1.2만`. Accessibility announces the
full count, reaction label, and selection state. Public counts stay numeric in
the API; abbreviation is presentation only.

Create original SVG sources in the repository and matching native Android vector
drawables. Keep consistent proportions and optical weight, readable light/dark
colors, and clear selected/pressed states. The reference supplies the five-cell
interaction, not reusable artwork. Do not copy its emoji images or use platform
emoji glyphs in place of the requested assets. No image-generation model or
downloaded icon font is needed for these code-native vectors.

State rules:

- Load reaction state independently of article text and analysis. A reaction
  request never blocks reading, markers, comparisons, Saved, or Back.
- On the first read, use a quiet loading state with stable module geometry;
  do not present unknown counts as zero.
- While a mutation is pending, disable the five reaction cells **for that
  article only** and show progress in the selected action's icon slot. Keep
  confirmed counts and selection until the server responds.
- Replace counts and selection with the confirmed response. Do not increment
  displayed counts locally and then add the server count again.
- Failure keeps the last confirmed state and gives that article a compact
  retry action. Retrying sends the same idempotent desired PUT or DELETE;
  a lost response must not cause a second vote.
- Opening B while A is saving keeps B independent. A's late result/error must
  not overwrite B, and returning to A must preserve its confirmed reaction.
- Cache loaded state in memory by article ID for this viewer. Reuse a successful
  read for 60 seconds; revalidate older state on article entry/resume, without
  blanking existing confirmed values. No background polling or per-recomposition
  GET. Explicit retry bypasses the local freshness check.
- A successful mutation makes Home highlights locally stale. Refresh the
  highlights on the next Home entry; do not pull Home ranking into the Reader
  mutation's critical path.
- A guest tap opens the existing contextual Login/Create-account sheet above
  the Reader. Dismissal changes nothing; authentication returns to the Reader
  without automatically posting the attempted reaction.
- Account change/logout cancels pending requests and clears selected reactions,
  errors, and the viewer's reaction cache. Discard late UI updates from the old
  viewer even when the transport cannot cancel an already committed write.
- Offline Reader keeps its existing body/analysis behavior and omits the online
  reaction module. Do not add reaction rows to Room, show guessed counts, or
  queue writes to send later.

## 4. Home `이슈`

### 4.1 Score and eligible topics

At server time T, count current reactions with
`T - 24 hours <= reacted_at <= T`. Old articles can become Issues through new
reader participation; publication age is not an Issue cutoff. This is a rolling
window, not a midnight reset. Participation measures reader response volume,
not positive endorsement, editorial importance, or analysis quality.

Group by the existing StoryCluster. For an unclustered article, use the existing
`article:{id}` story identity. Within one topic, **distinct authenticated user
IDs**, not reaction rows or summed button counts, determine the score. One
account reacting to three articles in one topic contributes one participant.

A topic is discoverable only if it has at least one active-publisher article
that is both `visibility=VISIBLE` and `is_feed_visible=true`. Reactions on
readable support articles may contribute to that discoverable topic. Reactions
on hidden/inactive articles, or on a support-only topic with no eligible feed
article, do not contribute. An unclustered support-only article is excluded.

### 4.2 Selection and display

1. Consider topics with at least one qualifying participant.
2. Order by distinct participant count descending, newest eligible publication
   within the topic descending, then stable story key ascending.
3. Show **one** topic. Reuse the existing topic-feed representative policy and
   Reader destination; do not invent a new publisher-preference algorithm.
4. Article button counts and topic participant count are different measures.
   Home shows the representative headline under `이슈`, without exposing an
   internal score, rank movement, time-window caption, or five reaction counters.
5. If the selected Breaking article belongs to the same topic, Breaking takes
   that adjacent headline slot and Issue considers the next qualifying topic.
   A topic may still appear in the independent readership trends list; do not
   change its genuine rank to force different content across every section.
6. With no qualifying topic, omit the Issue row. Do not fill it with ordinary
   latest articles or fabricate reaction counts.

The 24-hour window is independent of the existing three-hour readership window.
Changing/removing reactions affects the next highlights calculation; no nightly
batch or administrator approval is required.

## 5. Home `속보`

A candidate must satisfy all these rules:

- Publisher is active; article is `VISIBLE` and feed-visible.
- A leading title tag explicitly says `속보`: `[속보]`, `(속보)`, or `【속보】`.
  Leading whitespace and whitespace inside the tag are allowed. A sequence of
  leading tags such as `[정치][속보] 제목` is accepted. A body-only mention,
  untagged prose about breaking news, or a tag appearing mid-sentence is not.
- The original stored publication time satisfies
  `T - 2 hours < published_at <= T`. Exactly two hours old is expired.
  Ingestion time and later title/body updates never extend eligibility. Missing
  or invalid publication times cannot be replaced with the collection time.

Select the newest eligible article, with article ID ascending as a stable
tie-breaker. Show one `속보` headline and open that exact article in Reader.
Return `expires_at = published_at + 2 hours`. The active Home screen removes an
expired Breaking row without waiting for another pull-to-refresh. Future-dated
articles are excluded.

If there is no candidate, omit the row. Do not relabel a latest article or alter
stored dates/visibility to manufacture a Breaking example. The inspected live
dataset currently has no eligible Breaking candidate; that is a valid empty
state, not a reason to backfill old tagged stories.

Two hours is this product's initial setting, not a claim about a universal news
industry standard. This feature uses publisher labeling; it does not claim an
independent assessment of urgency or truth.

## 6. Data model and APIs

### 6.1 One new domain table

`ArticleReaction` has an internal primary key plus:

| Field | Constraint |
| --- | --- |
| `user_id` | Existing UserAccount FK; cascade on hard deletion |
| `article_id` | Existing Article FK; cascade on hard deletion |
| `kind` | One of the five fixed values in §2.1; database check constraint |
| `reacted_at` | Server timestamp for creation/actual type change; same-type PUT preserves it |

Add uniqueness on `(user_id, article_id)`, an article lookup index for counts,
and one recent-time index for the 24-hour selection. Reuse indexes supplied by
FK/unique constraints where possible. No reaction-type lookup table, stored
article/cluster counters, new user model, new cluster mapping, or ranking-cache
table is part of this slice. Keep model changes in a forward migration; do not
rewrite earlier migrations or replace the existing dataset.

### 6.2 Article reaction resource

```http
GET    /api/v1/articles/{article_id}/reactions
PUT    /api/v1/articles/{article_id}/reactions
DELETE /api/v1/articles/{article_id}/reactions
```

GET uses the existing optional-auth rule. PUT/DELETE require a valid account;
PUT accepts exactly `{"reaction": "LIKE"}` or another allowed value. A missing,
null, wrong-case, or unsupported value and unknown request fields are invalid.
Use DELETE
for no selection rather than inventing another enum value.

All successful operations return the same shape with HTTP 200:

```json
{
  "article_id": "article-uuid",
  "counts": {"RECOMMEND": 0, "LIKE": 3, "MOVED": 0, "ANGRY": 1, "SAD": 0},
  "my_reaction": "LIKE"
}
```

All five keys are always present. Guests receive `my_reaction=null`. Counts and
selection come from committed server state. Serialize conflicting operations
for one account/article, enforce uniqueness in PostgreSQL, and make repeated
PUT/DELETE idempotent. Do not modify another account's selection.

Use existing structured errors: 400 for an invalid reaction/body, 401 for missing
or invalid mutation credentials, 404 for an unreadable article, and a safe 503
for transient persistence failure. Personalized reads use `Vary: Authorization`
and `Cache-Control: private, no-store`; no shared HTTP cache stores a user's
selection. A public GET does not record a view or a reaction.

### 6.3 Home highlights resource

```http
GET /api/v1/home-highlights
```

The optional-auth endpoint has no category, publisher, or subscription filter:
both highlights are public discovery. Unsupported query parameters return 400.
Every successful response has this shape; timestamps are UTC ISO 8601 values:

```text
HomeHighlights {
  generated_at: timestamp,
  breaking: { article: ArticleSummary, expires_at: timestamp } | null,
  issue: { story: StorySummary, reaction_user_count: positive integer } | null
}
```

- `null` means a successful calculation found no qualifying item; it does not
  mean a request failed. Database failure returns a structured 503 instead.

The article and story payloads retain their existing field shapes and
personalization rules. The three-hour trends resource keeps its existing
contract and snapshot policy; no reaction ranking is inserted into its ranks.
Use `Vary: Authorization` and `Cache-Control: private, no-store` for this resource
as well; a public score does not make its personalized representative safe for
shared HTTP caching.

## 7. Requests, performance, and expiry

- Compute article counts with a grouped query, not five separate count queries.
  Home selection filters the indexed 24-hour reaction window before grouping.
  Hydrate only the selected article/topic; never load all article bodies or
  reaction rows into Python/client memory to count them.
- Query count is bounded independently of publisher/topic count. Windowed
  aggregation still scales with recent activity; a bounded response is not a
  promise of constant query cost. Measure the query plan and latency before
  introducing a separate ranking cache or materialized counters.
- Android loads Home highlights independently of trends and ordinary Home
  stories, on Home entry/resume and pull-to-refresh. Reuse a successful result
  for 60 seconds unless a local reaction mutation marked it stale. No background
  polling, per-composition requests, or timer that repeatedly hits the server.
  Key this local state by viewer and clear it on account change, because the
  representative payload may contain personalized Saved/follow flags even
  though the topic score itself is shared.
- The response's server time and Breaking expiry determine the remaining
  display duration. Use elapsed time while the screen is active so a phone
  clock adjustment cannot prolong the Breaking label. Recheck on resume.
- A failed refresh may retain only a still-fresh successful local result;
  Breaking expiry always wins. Otherwise omit the optional highlights and keep
  ordinary news/trends usable. Retry through normal Home refresh; do not add a
  full-screen error or a permanent empty placeholder for these two rows.
- No migration of article bodies, analysis offsets, Room schema, or trends
  retention job is required. Reaction rows persist as current choices until
  cancellation or cascading account/article deletion.

## 8. Implementation sequence and review

1. Review this feature and its [behavioral test specification](../testing/article-reactions-and-home-highlights.spec.md).
2. Add the reaction model/migration, transactional mutations, aggregate reads,
   and highlights selection with isolated SQLite and PostgreSQL verification.
3. Add API parsing, per-article client state, original vector assets, Reader
   module, contextual auth entry, and real Home highlights.
4. Verify dark/light, text scaling, Reader/analysis geometry, cross-article and
   account transitions, and Home expiry on the connected phone. Browser tests
   remain opt-in; this slice does not redesign Web.
5. Apply the forward migration to the shared DB, deploy the API before installing
   the dependent APK, and verify real reads without planting fake reactions or
   fake recent publication dates into the shared dataset.

Review must confirm the concrete selection/cancellation behavior, article versus
topic counts, empty states, and failure boundaries before executable tests are
generated. The user has confirmed the Issue aggregation unit/window; the whole
draft is not marked implemented or fully intent-reviewed by that decision alone.
