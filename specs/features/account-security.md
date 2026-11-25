# Account security hardening

| Field | Value |
| --- | --- |
| Status | Draft — specification only; implementation has not started |
| Date | 2026-11-25 |
| Clients | Android and Web |
| Baseline | Existing Django accounts, opaque sessions and PostgreSQL |

## 1. Intent and scope

Make ordinary registration and sign-in safer without adding routine challenges
for readers or replacing the account system. Extend the account/session rules in
[`../product/news-app-v1.md`](../product/news-app-v1.md), navigation in
[`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md), and the public/private boundary
in [`guest-offline-reading.md`](guest-offline-reading.md).

This is a proposed extension, not a description of deployed protection. Existing
bearer contracts remain authoritative until the corresponding stage is reviewed,
implemented and verified. Keep the stages independently reviewable:

| Stage | Deliverable | Persistence/API impact |
| --- | --- | --- |
| 1 | Login/registration throttling, bounded validation, authentication response protection, Android backup exclusions | One short-lived throttle table; existing account/session and article schemas stay unchanged |
| 2 | Browser HttpOnly session, CSRF checks, Web security headers and storage cutover | Reuse AuthSession; add browser auth routes; Android bearer routes keep their contract |
| Follow-up | Verified email, password change/recovery and account-wide session revocation | Separate spec once the mail sender and ownership policy are decided |

Complete Stage 1 before starting Stage 2. Neither stage silently implements or
claims email verification/recovery. Those remain prerequisites for accepting
external real-user accounts under the product baseline.

JWT/refresh-token infrastructure, a second identity provider, Redis, routine
CAPTCHA, mandatory MFA, a session-management dashboard and background client
refresh jobs are outside this change. Keep guest reading and existing
contextual authentication/return navigation.

## 2. Stage 1: protect the existing authentication boundary

### 2.1 Shared attempt limits

Use the same limiter for `/api/auth/login`, `/api/v1/auth/login`, and the future
browser login route. Changing route or using username instead of that same
username's casing must not provide a second budget. Registration has its own
IP budget. These are initial project defaults, configurable in deployment:

| Budget | Maximum admitted requests | Window |
| --- | ---: | --- |
| Login, per trusted client IP | 100 | 10 minutes |
| Login, per account / unknown identifier | 10 | 10 minutes |
| Registration, per trusted client IP | 30 | 1 hour |

Reject invalid/nonpositive limit configuration rather than silently disabling
protection. Deploy the throttle-table migration before enabling the code; a
missing table is an unavailable authentication dependency, not a bypass mode.

- Normalize an identifier exactly as authentication does: trim and lowercase.
  After IP admission, resolve the existing account before hashing. Its username
  and email share an account-UUID-based budget; unknown accounts use the normalized
  identifier. Do not reveal which key form was selected. The coarse IP budget
  allows for people sharing a university/home network and is additional.
- Reserve admission atomically before password hashing or account/session
  creation. A successful login clears only that account's login budget;
  it never clears the IP budget. Rejected attempts do not extend the window.
- At the limit, return `429 RATE_LIMITED` and an integer `Retry-After` in seconds.
  Apply the same limit/error to existing and nonexistent identifiers. Never
  permanently lock an account or affect public news reads.
- The database is authoritative across Vercel workers. An in-process dictionary
  or Django's default local-memory cache is not an enforcement mechanism.
- Use one table with unique `(scope, key_hash)`, `count`, and indexed `expires_at`.
  Store a namespaced HMAC of the IP/identifier using the server secret, not the
  raw values, passwords or tokens. Reuse/reset an expired row rather than adding
  a new row for every request. Counts saturate at the configured limit.
- Remove expired rows in small indexed batches during authentication traffic.
  Housekeeping must not turn an otherwise valid login into a failure. No
  full request-history table or extra scheduling service is needed.
- Counter failures return retryable `503 AUTH_UNAVAILABLE`; never disable the
  limit silently. Ordinary news reads remain independent.
- Trust forwarded client IPs only through the documented deployment proxy
  boundary. Before release, verify the platform-supplied header cannot be
  replaced by arbitrary client `X-Forwarded-For` input. Unknown/untrusted input
  uses one conservative fallback bucket, not a caller-selected value. Limiter
  correctness and this deployment trust check are separate evidence.

### 2.2 Validation and credential checks

- Keep the existing new-password policy: minimum 10 characters, common-password
  rejection and numeric-only rejection. Add a registration maximum of 128 Unicode
  code points; do not add composition rules or silently truncate a password.
  The server is authoritative; client limits must not reject valid supplementary
  Unicode characters by treating UTF-16 code units as code points.
- Keep username length 3–30 and email length at most 254 characters. Login
  identifiers are strings of at most 254 characters. Reject arrays/objects and
  other coerced credential types as validation errors.
- Login still accepts previously issued credentials without applying the new
  registration minimum/complexity policy. Its existing 8 KiB JSON body limit
  remains an upper bound; never reject an existing short password merely because
  new accounts require longer passwords.
- Use Django password hashers. An unknown account performs equivalent current
  password-hasher work before the same generic invalid-credentials response.
  Do not add sleeps or claim identical wall-clock response times.
- Registration always creates an ordinary user. Client-supplied role/developer
  flags cannot grant privileges. Existing database uniqueness constraints remain
  the final authority for concurrent registration. Account creation and its
  initial session are one transaction: a server-side session-creation failure
  rolls both back. A client timeout after commit has an unknown outcome; do not
  automatically replay registration or claim the server rolled it back.
- An operator-initiated password rotation revokes that user's existing sessions
  in the same transaction. Reuse AuthSession; do not build a new reset UI here
  or silently change existing passwords during deployment.

### 2.3 Authentication responses and client behavior

- Authentication success, failure, `me`, logout and future browser bootstrap
  responses use `Cache-Control: no-store`. Do not cache credentials or account
  identity at a CDN. Preserve existing safe machine-readable errors/request IDs.
- Logs never include passwords, Authorization/Cookie headers, raw tokens or full
  authentication bodies. Request outcome/status may be counted without storing
  an identifier or IP in application logs.
- Android/Web display a compact inline retry message on 429, preserve the form
  and its return destination, and disable repeat submission until Retry-After
  elapses. Clamp malformed/extreme delay values to a safe client bound. No
  background retry, automatic password resubmission or countdown overlay.
- 429 and 503 do not clear a valid existing session or sign a person out. Buttons
  recover after a failure; guest browsing remains available.
- Exclude `frameless_auth_v1.xml` from Android legacy backup and Android 12+
  cloud backup/device transfer. Preserve the current Keystore-backed encryption.
  An unreadable restored auth store recovers to Guest safely without logging its
  contents; it must not crash the app or erase public article/analysis rows.
  Never fall back to persisting tokens in unencrypted preferences.

## 3. Stage 2: browser session transport

### 3.1 Reuse the existing session model

Android and legacy clients retain bearer authentication and current response
shapes. Web uses explicit browser endpoints, keeping cookie behavior out of a
client-selected registration field:

| Route | Contract |
| --- | --- |
| `GET /api/v1/web/auth/session` | Bootstrap CSRF; return `{user, expires_at, csrf_token}`; no cookie means `user=null` |
| `POST /api/v1/web/auth/register` | Existing registration validation; set session cookie; return metadata and fresh CSRF token, never the raw session token |
| `POST /api/v1/web/auth/login` | Shared authentication and throttle budgets; set cookie; return the same metadata shape |
| `POST /api/v1/web/auth/logout` | Revoke the current session and expire its cookie; successful repeat logout remains 204 |

The raw cookie value has the same random-token/hash-at-rest design and 30-day
absolute server expiry as AuthSession. Do not add another session table or a
refresh token. Successful login/registration replaces a prior browser session; it must not
leave the overwritten browser session usable indefinitely.

Production session cookie: `__Host-frameless_session`, `Secure`, `HttpOnly`,
`SameSite=Lax`, `Path=/`, no Domain, and expiry no later than the server session.
Local HTTP development uses a clearly separate development cookie name/config;
that exception must never weaken production flags.

### 3.2 Request security

- Web calls relative `/api/...` URLs through its existing same-origin Vercel
  rewrite. No cross-site cookie/CORS setup or new BFF service is needed.
- Server reads accept the browser cookie or the existing bearer header. A supplied
  Authorization header is authoritative: malformed/expired bearer credentials
  return 401 and cannot fall back to a valid cookie or Guest. Invalid cookies
  likewise do not silently authenticate as Guest on protected/public auth-aware
  reads. Browser bootstrap returns 401 for an invalid cookie and still supplies
  CSRF material for an explicit sign-in. A read response must not delete/set the
  authentication cookie: a late old 401 could otherwise erase a newer login.
  Cookie replacement/deletion belongs to explicit successful auth writes.
- Enforce Django CSRF protection on browser login, registration and logout,
  including login when no session cookie exists, and on every cookie-authenticated
  unsafe API operation. Cover Saved, follows, reactions, view recording and any
  retained legacy/link-analysis POST that consumes a cookie identity. Body
  parsing, CSRF and origin rejection precede protected side effects.
- Obtain CSRF through bootstrap; send `X-CSRFToken` on browser writes. Rotate
  CSRF on login/registration and return the fresh masked value. A stale-token
  403 may bootstrap and retry once only when the server confirms CSRF rejected
  the request before business execution. Never retry a generic failure as a write.
- Use exact trusted Web origins configured for production/local development.
  Missing production origin configuration fails closed for browser writes.
  Do not trust every `*.vercel.app` project or enable wildcard credentialed CORS.
- Bearer-only Android/legacy requests keep their existing CSRF exemption;
  the presence of a cookie must not become a way to skip cookie-mode checks.
- Auth-aware news responses vary by both `Cookie` and `Authorization` and use
  private cache rules. Pure Guest reads stay public where otherwise permitted.
  Keep browser/in-memory query caches scoped to the confirmed viewer.

### 3.3 Web state and rollout

- Never persist browser bearer tokens in localStorage, sessionStorage or IndexedDB.
  Keep session/user metadata in memory and determine identity through bootstrap.
- A temporary transport failure leaves the HttpOnly cookie intact and keeps an
  already confirmed identity for that page. A fresh page whose identity cannot
  be checked permits public browsing with an account-status retry state; it does
  not claim that locally remembered metadata proves authentication. Protected
  actions wait for confirmation rather than running under an uncertain account.
- Confirmed expiry/logout becomes Guest, clears personalized query state and
  preserves the public reading/navigation origin. Revalidate on tab re-entry
  after another tab has logged in/out; never reuse the previous viewer's Saved
  flags or invalidate a newer session from a late bootstrap response.
- Browser sign-out completes only when the server revokes the session and
  expires its HttpOnly cookie. A network failure shows an inline retry error
  rather than claiming sign-out succeeded; do not silently queue logout or
  pretend JavaScript can erase an HttpOnly cookie. Android retains its existing
  ability to clear local credentials when server logout cannot be reached.
- Proposed minimal cutover: one-time browser sign-in. Delete the old
  `frameless.web.session.v1` entry and, if present, attempt to revoke that exact
  bearer through the old logout route before discarding it. Do not transfer the
  old bearer to another persistent store. Android remains signed in.
- That best-effort logout cannot revoke old tokens on devices that never revisit
  the Web app or when revocation fails. Those expire under their existing 30-day
  policy; immediate global revocation would also sign Android out and requires
  a separate explicit rollout decision. Do not claim the cutover revokes all
  historical browser credentials instantly.
- Add a production CSP using the actual scripts, fonts, API and image origins,
  plus `frame-ancestors 'none'`, `object-src 'none'`, a restricted `base-uri`,
  `nosniff` and a referrer policy. No `unsafe-eval` or wildcard script origins.
  Do not break existing rendering with a policy copied from another product.
- Verify Cookie/Set-Cookie/CSRF behavior through the deployed Web rewrite before
  switching the Web client. Deploy backward-compatible backend support first;
  rollback cannot restore a localStorage token fallback or bypass CSRF.

## 4. Follow-up boundary

Email verification, verified-address changes, password change/reset and all-device
logout require their own reviewed ownership/recovery flow. Choose the mail sender
before promising delivery or adding user-facing controls. Recovery must use
expiring single-use tokens, safe account-existence responses and session revocation
on password reset; never turn an unverified address into an account-ownership claim.
Retain the product baseline's other public-release requirements, including account
deletion. No mail-provider SDK, mail-token table or recovery UI is created here.

## 5. Implementation and review order

1. Review this draft and its behavioral oracles before generating test/code.
2. Implement Stage 1 and run relevant Django/Android/Web checks. Concurrent limiter
   enforcement needs isolated PostgreSQL evidence in addition to SQLite tests;
   include those tests in the existing PostgreSQL CI job before merge.
3. Implement Stage 2 only after its one-time sign-in and fresh-page network-failure
   behavior are accepted. Update the canonical product/guest/UI contracts in the
   same change; do not silently weaken their existing bearer/offline guarantees.
4. Verify the deployment proxy/origin/cookie assumptions separately. No production
   login brute-force tests, credentials in artifacts, or automatic browser/emulator
   runs. Live/browser checks require the user's requested test scope.

Behavioral criteria: [`../testing/account-security.spec.md`](../testing/account-security.spec.md).

References: [OWASP authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html),
[OWASP browser storage](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html#local-storage),
[Django CSRF](https://docs.djangoproject.com/en/5.2/ref/csrf/),
[Vercel proxy request headers](https://vercel.com/docs/headers/request-headers),
[Android encrypted-preference backup warning](https://developer.android.com/reference/kotlin/androidx/security/crypto/EncryptedSharedPreferences).
