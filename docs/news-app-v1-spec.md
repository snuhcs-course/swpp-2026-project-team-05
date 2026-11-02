# FrameLESS News App v1 Specification

| Field | Value |
| --- | --- |
| Status | Implementation baseline |
| Version | 1.5 (reviewed-analysis vertical slice) |
| Date | 2026-10-31 |
| Client | Android |
| API | Django JSON API |
| Database | Supabase PostgreSQL |
| Analysis scope | Read and present already-reviewed results |

## 1. Product contract

FrameLESS v1 is an article-first news application. A signed-in user browses articles from supported publishers, reads one article in full, and optionally turns on FrameLESS annotations. The annotations are prepared before the read request and stored in PostgreSQL. Other publishers appear only when a reviewed comparison exists for the selected expression.

The ordinary user does not submit a URL and never waits for an LLM call. The main flow is:

```text
Register or log in
        ↓
Browse politics or society articles
        ↓
Filter by publisher
        ↓
Read one stored article in full
        ↓
Turn expression analysis on
        ↓
Tap a highlighted expression
        ↓
Read its explanation and optional cross-publisher evidence
```

Internal story clusters organize related articles, but v1 does not expose a topic dashboard, cluster cards, raw A–G labels, bias scores, or truth scores.

`ExpressionFinding` means one reviewed, displayable result: an exact source sentence, one exact marker, one or more A–G feature categories, a user-facing explanation, and optional comparisons with sentences from other articles.

Android layout, gestures, loading states, and accessibility are specified in [`news-app-v1-ui-spec.md`](news-app-v1-ui-spec.md).

### 1.1 Required v1 functionality

- Registration, login, persistent session, and logout
- No anonymous mode
- Ordinary user and developer-account distinction
- Politics and society feed
- Publisher and category filters
- Stable cursor pagination
- Full stored article title and body
- Original publisher, publication time, category, and source link
- Analysis on/off in the Reader
- Exact title/body highlights for stored findings
- Source-only finding explanations
- Optional cross-publisher comparison cards
- Multiple compared publishers for one finding
- Complete loading, empty, error, and retry states
- Reviewed demonstration data loaded through a repeatable importer

### 1.2 Explicitly outside this implementation

The following designs remain valid future work, but they are not required to ship this vertical slice:

- Scheduled article collection
- Automatic semantic clustering
- LLM-based finding generation or cross-article mapping
- Re-analysis orchestration and background workers
- Embeddings and pgvector
- Cluster merge/split tools
- An administrator CMS
- Analysis history and rollback
- Article edit/version history
- I-series analysis, including I1
- Personalized ranking, bookmarks, recommendations, and notifications
- Server-synchronized publisher preferences
- Email verification, password reset, account deletion, and session management UI
- Offline article storage
- Public web and iOS clients

The existing legacy URL-analysis endpoint may remain for compatibility, but it is not linked from the v1 ordinary-user UI and is not part of the new read-path implementation.

Future automatic analysis work is documented separately in [`cluster-analysis-pipeline-lessons.md`](cluster-analysis-pipeline-lessons.md). It must not complicate the v1 read APIs or Android UI.

## 2. Supported content

### 2.1 Publishers

Publisher rows come from the database; Android does not hard-code publisher branches.

The reviewed seed currently contains:

- Yonhap News Agency (`yna`)
- News1 (`news1`)
- Newsis (`newsis`)
- MBC (`mbc`)
- SBS (`sbs`)
- YTN (`ytn`)
- Hankyoreh (`hani`)
- Kyunghyang Shinmun (`khan`)

KBS, Chosun Ilbo, JoongAng Ilbo, and Dong-A Ilbo may be added as ordinary publisher rows when reviewed article data is available. Adding an outlet must not require an Android release.

`Publisher.code` is the only stable API identifier. Use `yna` consistently; do not introduce a second `yonhap` code.

### 2.2 Categories

v1 supports:

- `POLITICS`
- `SOCIETY`

An article belongs to one category and at most one story cluster in v1.

### 2.3 Stored article text

The Reader displays the exact title and extracted body stored in FrameLESS. Reporter-written decks and internal subheadings may remain in `body_text` in reading order. Images, comments, advertisements, related-link modules, and page chrome are excluded.

The analysis offsets are valid only for the stored title/body pair. `content_hash` is SHA-256 over the UTF-8 bytes of `title + "\0" + body_text`. Importing the same canonical URL and identical content is idempotent. A different title or body for an existing canonical URL is rejected as a content conflict in v1; silently overwriting text would invalidate existing offsets.

Full-text display is for the internal prototype. Publisher-policy and legal review are required before public release.

## 3. Accounts and sessions

### 3.1 Account shape

Keep the existing lightweight account implementation and evolve it with a forward migration rather than introducing a second authentication framework.

`UserAccount`:

- `id`: UUID primary key
- `email`: trimmed, case-insensitive unique
- `username`: trimmed, case-insensitive unique
- `password_hash`: Django password hasher output
- `is_developer`: boolean, false for ordinary registrations
- `created_at`, `updated_at`

The existing developer account remains `is_developer=true`. Self-registration always creates an ordinary account.

### 3.2 Registration and login rules

Registration accepts:

- email;
- username;
- password;
- password confirmation.

Rules:

- normalize email and username before uniqueness checks;
- username is 3–30 characters and may contain Korean/Latin letters, digits, and underscore;
- apply Django password validation with a minimum length of 10;
- return field-specific validation errors;
- successful registration returns an authenticated session;
- login accepts email or username plus password;
- invalid login does not reveal whether the account exists.

### 3.3 Session contract

`AuthSession` stores a random bearer token safely:

- generate at least 256 bits of randomness;
- return the raw token once;
- store only its SHA-256 hash;
- expire after a configurable period, initially 30 days;
- delete or invalidate it on logout;
- store the raw token in Android Keystore-backed encrypted storage.

Authenticated requests use:

```http
Authorization: Bearer <session-token>
```

All feed, article, and analysis endpoints require a valid session. Only registration and login are public.

Password change, reset, account deletion, and session listing remain outside this vertical slice. They must be added before an external public release.

## 4. User experience contract

### 4.1 Feed

The feed is sorted by `(published_at DESC, id DESC)` and supports:

- politics/society selection;
- zero or more publisher filters;
- pull-to-refresh;
- cursor pagination;
- restoration of scroll position and filters after returning from the Reader.

Each row shows:

- publisher;
- publication time;
- headline;
- category;
- a simple analysis badge when useful.

Do not show generated summaries, thumbnails, cluster information, A–G codes, finding counts, rankings, or bias labels.

The last category, publisher selection, and Reader analysis-toggle value are stored locally on the device. They are not account preferences.

### 4.2 Reader

The article must remain readable regardless of analysis availability.

With analysis off:

- render the exact stored title/body without highlights;
- preserve paragraph breaks;
- show publisher, publication time, category, and original link.

With analysis on:

- fetch the article-centered analysis endpoint;
- highlight exact markers in the title and body;
- merge overlapping markers into one tappable region with multiple labels;
- do not modify the underlying article string or scroll position;
- open the finding sheet when a marker is tapped.

### 4.3 Analysis display states

| State | Reader behavior |
| --- | --- |
| `READY` with findings | Show verified highlights. |
| `READY` with zero findings | State that analysis completed but no displayable expression feature was confirmed. Do not call the article neutral. |
| `PARTIAL` | Show the verified subset and a non-blocking partial-results notice. |
| `NOT_STARTED`, `FAILED` | Keep the article readable and explain that analysis is unavailable. |
| `UPDATING` | If reviewed findings exist, keep them readable; otherwise show analysis as unavailable. No read request starts work. |

### 4.4 Finding sheet

Every finding shows:

- natural-language feature labels instead of raw A–G codes;
- the selected marker;
- its explanation.

When comparisons exist, add `같은 내용을 다른 기사에서는`:

- one publisher per horizontal page;
- at most three publisher-diverse pages;
- publisher, target title, publication time, target sentence, highlighted target marker, explanation, and original link;
- multiple evidence sentences from one publisher remain on the same page.

When comparisons do not exist, omit the comparison section entirely. Do not render an empty pager or claim that other articles lack the content.

The bottom sheet has stable collapsed, middle, and expanded anchors. Vertical article scrolling, sheet dragging, and horizontal publisher paging must not fight each other.

## 5. Database contract

The news-analysis schema already exists through `server.0004_news_domain`. Do not add speculative job, audit, embedding, release-history, or candidate tables for this implementation.

### 5.1 Relationship overview

```text
UserAccount ── N AuthSession

Publisher ── N Article ── N ExpressionFinding
                    │              │
StoryCluster ───────┘              ├─ 0..1 SharedProposition
                                   └─ N FindingComparison ── target Article
```

### 5.2 Core tables

`Publisher`

- stable unique `code`;
- Korean display name and domain;
- optional logo;
- active flag and display order.

`StoryCluster`

- internal UUID and label;
- politics/society category;
- materialized analysis state, version, input hash, and analyzed time.

`Article`

- publisher and optional cluster;
- unique canonical URL;
- exact title/body and SHA-256 content hash;
- category, publication time, visibility, timestamps.

`ExpressionFinding`

- source article and `TITLE`/`BODY` scope;
- absolute source-sentence offsets and exact sentence text;
- absolute marker offsets and exact marker text;
- non-empty, distinct A–G category list;
- explanation and display order;
- optional shared proposition.

`SharedProposition`

- one cluster;
- one precise neutral proposition shared by the finding and all its comparisons.

`FindingComparison`

- source finding and different target article in the same cluster;
- target scope, absolute sentence/marker offsets, and exact text;
- target owner and optional speaker label;
- comparison explanation and display rank.

### 5.3 A–G feature semantics

The stored categories remain useful internal metadata and regression labels. Android translates them into plain Korean instead of displaying raw letters.

| Code | Stored feature | Suggested UI label |
| --- | --- | --- |
| A | Reporting or quotation verb that adds attitude toward the statement | 보도 태도가 드러나는 표현 |
| B | A name applied to a statement or remark | 발언을 규정하는 표현 |
| C | Action/event wording that adds agency, intent, force, responsibility, or legality | 행위를 규정하는 표현 |
| D | Meaningful degree or quantity modifier on the same dimension | 정도를 강조하는 표현 |
| E | Evaluative, emotional, or affective wording | 평가·감정이 담긴 표현 |
| F | One-sided or contested naming | 한쪽의 명명이 반영된 표현 |
| G | Modality, certainty, or categorization | 확신·범주화가 드러나는 표현 |

One marker may have multiple independently reviewed categories. This does not create multiple highlights or duplicate comparisons.

### 5.4 Persisted-data invariants

- Every finding is already reviewed and displayable.
- A finding may have zero comparisons.
- A finding with comparisons has exactly one shared proposition.
- Every shared proposition is used by at least one compared finding.
- All comparison targets belong to the source finding's cluster.
- Source and target marker text exactly match their stored offsets.
- A target sentence does not need its own finding.
- Multiple categories on one marker do not duplicate comparison rows.
- Equivalent wording or merely related sentences do not become comparison rows.

These invariants are enforced in the import/service layer and covered by tests. Do not add database triggers unless another trusted writer bypasses Django.

## 6. API contract

All new endpoints use `/api/v1`. Successful payloads are JSON. Errors use:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "사용자에게 표시할 안전한 메시지",
    "fields": {"email": "이미 사용 중인 이메일입니다."}
  },
  "request_id": "safe-request-id"
}
```

`fields` is optional. Expected error codes include `AUTH_REQUIRED`, `INVALID_CREDENTIALS`, `VALIDATION_ERROR`, `INVALID_CURSOR`, `NOT_FOUND`, `FORBIDDEN`, and `SERVER_ERROR`.

### 6.1 Authentication

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

Registration and login return:

```json
{
  "token": "returned-once",
  "expires_at": "2026-11-30T12:00:00+09:00",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "username": "username",
    "is_developer": false
  }
}
```

Logout is idempotent and returns HTTP 204. `me` returns only the `user` object.

### 6.2 Publishers

- `GET /api/v1/publishers`

Return active publishers in `display_order`, then name order:

```json
{"items": [{"code": "yna", "name": "연합뉴스", "logo_url": null}]}
```

### 6.3 Feed

- `GET /api/v1/articles`

Query parameters:

- `category=POLITICS|SOCIETY`;
- repeated `publisher=yna&publisher=mbc`;
- opaque signed `cursor`;
- `limit`, default 20 and maximum 50.

Response:

```json
{
  "items": [
    {
      "id": "uuid",
      "publisher": {"code": "yna", "name": "연합뉴스"},
      "title": "...",
      "category": "POLITICS",
      "published_at": "...",
      "analysis_state": "READY"
    }
  ],
  "next_cursor": "opaque-or-null"
}
```

The cursor represents the last `(published_at, id)` pair and is validated with Django signing. Feed payloads do not include body or findings.

### 6.4 Article detail

- `GET /api/v1/articles/{article_id}`

Return publisher, title, exact body, category, publication time, original URL, and analysis state. Hidden articles return `NOT_FOUND` to ordinary users.

### 6.5 Article analysis

- `GET /api/v1/articles/{article_id}/analysis`

The response is projected onto the selected article. It never triggers collection or model work.

```json
{
  "state": "READY",
  "version": "cluster-pilot-reviewed-v1",
  "findings": [
    {
      "id": "uuid",
      "categories": ["F", "G"],
      "source": {
        "scope": "TITLE",
        "sentence": {"start": 0, "end": 31, "text": "..."},
        "marker": {"start_in_sentence": 13, "end_in_sentence": 19, "text": "..."}
      },
      "explanation": "...",
      "comparison_articles": [
        {
          "article_id": "uuid",
          "publisher": {"code": "mbc", "name": "MBC"},
          "title": "...",
          "published_at": "...",
          "original_url": "https://...",
          "body": "비교 기사 전문...",
          "evidence": [
            {
              "scope": "TITLE",
              "sentence": {"start": 0, "end": 28, "text": "..."},
              "marker": {"start_in_sentence": 5, "end_in_sentence": 8, "text": "피의자"},
              "target_owner": "REPORTER",
              "target_speaker_label": null,
              "comparison_summary": "..."
            }
          ]
        }
      ]
    }
  ]
}
```

Offset rules:

- database offsets are Unicode code-point positions in the exact stored title/body;
- API source and target `sentence.start/end` remain absolute positions in the selected title or body;
- source and target `start_in_sentence/end_in_sentence` are relative to the returned sentence text;
- Android must not normalize whitespace before applying source offsets;
- the server converts database absolute marker offsets into sentence-relative DTO offsets.

The server groups comparisons by publisher and returns at most three pages using persisted display rank, publication time, and stable article ID.

## 7. Reviewed data import

The Git-tracked fixture contains URLs, expected publishers, clusters, reviewed findings, and reviewed comparisons, but not complete article bodies:

```text
server/fixtures/reviewed_news_pilot_v1.json
```

The importer:

- fetches article bodies before opening a database transaction;
- validates every exact sentence and marker;
- supports a rollback-only dry run;
- stores content hashes;
- imports one cluster atomically;
- is safe to rerun with identical input;
- rejects conflicting content rather than invalidating offsets;
- removes obsolete findings/propositions/comparisons from the reviewed cluster result.

Commands:

```bash
python manage.py import_news_dataset \
  server/fixtures/reviewed_news_pilot_v1.json \
  --fetch-articles \
  --dry-run

python manage.py import_news_dataset \
  server/fixtures/reviewed_news_pilot_v1.json \
  --fetch-articles
```

The current reviewed dataset contains five clusters and exercises source-only findings, one-to-many comparison evidence, title/body markers, multiple categories on one marker, and completed analyses with no forced comparisons.

## 8. Security and robustness

- Never store or log raw passwords or session tokens.
- Never log database URLs, API keys, full article bodies, or raw model output.
- Enforce authentication on the server, not only in Android navigation.
- Preserve SSRF protection when the management command fetches URLs.
- Limit request bodies and validate all enum, UUID, cursor, and filter inputs.
- Use constant-time password checks through Django hashers.
- Return safe request IDs and machine-readable error codes.
- Use `select_related`/`prefetch_related` for feed and analysis reads; avoid one query per finding/comparison.
- Keep the database password only in ignored local configuration and deployment secrets.
- Apply registration/login rate limiting before any external public release.

## 9. Implementation boundaries

Prefer clear modules over speculative architecture. The existing `server` Django app may contain small focused modules such as:

```text
server/
  authentication.py
  news_queries.py
  analysis_queries.py
  api_v1.py
  news_import.py
```

Do not create a generic repository framework, job framework, event bus, CMS, audit subsystem, or plugin abstraction for v1.

Android uses the existing Kotlin/Compose stack with injectable authentication, article, and analysis clients. DTOs are versioned API models rather than database-shaped objects.

### 9.1 Android reuse and separation

Reuse the existing product language and the parts of the Reader that already solve the new product's interaction problems:

- typography, colors, spacing primitives, pills, status notices, and source links;
- full-article text rendering and annotated-string highlight utilities;
- highlight hit testing and the finding-detail sheet entry behavior;
- stable middle/expanded sheet anchors, the dedicated drag handle, and the expanded-body scroll handoff;
- horizontally paged related-article pages and focused scrolling to a target sentence;
- shared loading, retry, empty, and error presentation where the semantics still match.

Reuse is component-level, not contract-level. The legacy `AnalysisResult`/`ReaderResult` contract assumes one user-supplied source article and must not become the news product's domain model. Map the versioned news API DTOs into immutable feed, Reader, finding, and comparison UI models, then feed those models to extracted rendering components. Database models must not leak into Compose.

Keep these flows separate:

- the normal product route is authentication → feed → Reader → finding sheet;
- the URL import, live-analysis loading, legacy history, and legacy comparison state machine are not reachable by ordinary users;
- legacy URL analysis may remain as an isolated developer-only capability, but it must not shape feed navigation or Reader state.

The feed, publisher filter, registration, account menu, and news navigation are new surfaces. The existing legacy `ComparisonScreen` is not reused wholesale; only focused cards, text annotation helpers, and provenance components that match the new contract may be extracted.

Stored offsets are Unicode code-point offsets, while Kotlin `String` and Compose text ranges use UTF-16 indices. Convert offsets once in the API-to-UI mapper and validate the quoted text before rendering. A malformed range disables only that marker and is reported safely; it must never highlight a different substring.

The existing bottom-sheet gesture implementation is the baseline because it already avoids the expanded-sheet/body-scroll oscillation. It still requires Compose and physical-device regression tests with long target articles and font scaling.

## 10. Implementation order

The news schema, migration, reviewed fixture, and importer baseline already exist. Continue in this order:

1. Extend the existing account model for email registration and preserve the developer account.
2. Implement registration, login, logout, `me`, and Android encrypted-session persistence.
3. Implement publisher, feed, article-detail, and article-analysis read APIs.
4. Add backend contract, permission, pagination, grouping, and query-count tests.
5. Introduce news routes and API-to-UI mappers without coupling them to the legacy analysis state machine.
6. Implement Android login/registration, encrypted session restoration, and feed filters.
7. Extract the reusable Reader text/highlight and sheet primitives, then implement the full Reader and analysis toggle.
8. Connect findings and the multi-publisher bottom sheet to the analysis DTO.
9. Run the required end-to-end flows against the reviewed Supabase data.
10. Deploy compatible API code before distributing the Android build.

Automatic collection and analysis are intentionally not in this order.

## 11. Required tests

### 11.1 Backend

- email/username normalization and duplicate handling;
- Django password hashing and invalid-login behavior;
- session creation, expiry, authenticated read, and idempotent logout;
- authentication required for every read endpoint;
- feed category/publisher filters and stable cursor pagination;
- hidden article behavior;
- article detail returns exact stored body;
- analysis source offsets map to the selected article;
- READY with zero findings;
- source-only finding with no comparison array items;
- one finding grouped into multiple publisher pages;
- multiple rows from one publisher grouped together;
- at most three publisher-diverse pages;
- target attribution and original URL preserved;
- malformed stored evidence fails safely instead of creating a wrong highlight;
- bounded query counts for feed and analysis reads;
- importer dry-run rollback and idempotent rerun;
- importer rejects changed body text for an existing canonical URL.

### 11.2 Android

- registration and login validation;
- session restoration and expired-session return to login;
- category and publisher filters;
- initial, refresh, empty, pagination, and retry feed states;
- Reader works independently of analysis;
- analysis toggle preserves text and scroll position;
- title and body offsets open the correct finding;
- multiple labels remain one tappable marker;
- READY with zero findings;
- source-only finding has no empty pager;
- publisher pages swipe horizontally;
- target article content scrolls vertically;
- expanded sheet returns naturally to middle state;
- long text, font scaling, and screen-reader semantics.

### 11.3 End to end

1. Register → feed → filter → open article → source-only finding.
2. Open the 제주 finding → compare `보복살해범` with `피의자` from two publishers.
3. Turn analysis off/on without moving the Reader position.
4. Restart the app and restore the authenticated feed and local filters.
5. Confirm an ordinary user cannot access hidden or developer-only behavior.

## 12. Definition of done

v1 is complete when:

- a new user can register, log in, remain signed in, and log out;
- configured politics and society articles appear in a chronological feed;
- publisher/category filters and pagination are stable;
- every visible article is readable regardless of analysis state;
- exact title/body findings render on the correct characters;
- source-only findings end without a comparison carousel;
- validated cross-publisher evidence is grouped and pageable;
- no read request starts article collection or an LLM call;
- the reviewed fixture can be loaded repeatedly and verified;
- backend, Android, and end-to-end tests pass on the supported environment.
