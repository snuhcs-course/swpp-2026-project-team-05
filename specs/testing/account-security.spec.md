# Account security test specification

| Field | Value |
| --- | --- |
| Status | Draft — intent review pending; no new executable coverage claimed |
| Owner | Account/API and client maintainers |
| Source requirements | [Account security hardening](../features/account-security.md) |
| Date | 2026-11-25 |

## Scope and preconditions

Stage 1 protects the existing credential APIs. Stage 2 changes browser transport
while preserving Android/legacy bearer clients. Tests use isolated SQLite and
PostgreSQL databases, controllable UTC time and fake credentials. No real account,
production database, mail provider, live password guessing or live AI call.

Use two ordinary accounts, one developer, one nonexistent identifier, two trusted
client IPs, untrusted forwarded headers, expired/valid sessions and separate
browser cookie jars. No credential appears in saved test artifacts.

## Postconditions: retained baseline

1. Valid registration normalizes account fields and issues a non-developer
   account/session; case-insensitive uniqueness is enforced.
   Tested by: `tests.test_news_api.NewsApiTests.test_register_normalizes_fields_and_returns_session`,
   `tests.test_news_api.NewsApiTests.test_register_rejects_case_insensitive_duplicate`.
2. The existing bearer login accepts email, expired sessions cannot authenticate,
   and logout invalidates its session and remains idempotent.
   Tested by: `tests.test_news_api.NewsApiTests.test_login_accepts_email_and_logout_is_idempotent`,
   `tests.test_news_api.NewsApiTests.test_invalid_password_is_rejected_and_expired_session_cannot_read`.

## Postconditions: Stage 1

3. Admit exactly the configured login/IP, login/account and registration/IP
   budgets. Reject the next request with 429 and Retry-After; at expiry, admit
   a fresh budget without manual unlocking. Rejections do not extend expiry.
   Tip: test limit−1, limit, limit+1 and expiry−1 second/exact expiry for each
   scope, with small injected limits; keep deployment defaults covered separately.
4. Legacy, v1 and future browser login share a budget. Username/email aliases of
   one account share its budget; casing/whitespace does not create another.
   Unknown identifiers receive the same limit and public error semantics.
   Tip: add route/alias/case metamorphic tests and existing/nonexistent partitions.
5. Concurrent workers cannot exceed admission limits. Correct credentials do not
   reset the IP budget; successful login resets the admitted account's budget.
   Tip: run all-failed admission races through independent PostgreSQL connections;
   assert admitted count and that rejected attempts never hash passwords or create
   sessions. Add separate successful-login reset cases. SQLite is not evidence
   of PostgreSQL row-lock behavior.
6. IP spoofing cannot select an arbitrary limiter bucket. Missing/untrusted
   forwarding information has a conservative stable fallback. Stored keys are
   namespaced hashes, not raw identifiers/IPs.
   Tip: test parser/proxy-trust cases, IPv4/IPv6 canonicalization, header chains
   and stored rows. Separately verify the platform overwrites the selected header
   in the deployment path; do not label a mocked parser test as that evidence.
7. Expired counters are reused/pruned in bounded batches. Counter-store failure
   returns 503 without an account/session mutation; failed housekeeping does not
   disable admission or break an otherwise valid login. Public reads still work.
   Tip: inject store/cleanup failures and verify transaction outcomes, cleanup
   batch bounds, expiry-index use and unaffected unauthenticated article reads.
8. Credentials are typed strings. New passwords honor 10/128-code-point limits,
   the existing common/numeric checks and exact non-truncated content. Existing
   short login passwords remain valid; oversized bodies fail before hashing.
   Tip: partition null/number/array/object, empty/whitespace, 9/10/128/129-length
   Unicode passwords, email/identifier limits, existing short credentials and
   the 8 KiB body boundary. Never echo a password in validation messages.
9. Wrong-password and unknown-account responses have the same status/message;
   unknown accounts perform current password-hasher work. Submitted privilege
   fields cannot grant developer access. Registration failure rolls back both
   the new account and initial session.
   Tip: assert hash-work calls and generic responses rather than flaky wall-clock
   equality; inject initial-session failure and race duplicate registrations.
   Simulate a committed-but-lost response separately: the client must not infer
   rollback or automatically replay registration.
10. Every auth response, including 4xx/5xx/429 and legacy responses, is no-store.
    Logs contain safe outcomes/request IDs but no auth bodies, credentials or
    raw limiter identifiers.
    Tip: exercise success and each failure path, inspect headers and captured
    application logs, and verify no extra credentials are emitted by error paths.
11. Both clients preserve form/navigation state through 429/503, avoid automatic
    credential replay, disable submit for a bounded Retry-After and recover when
    the wait/request ends. Existing sessions remain valid.
    Tip: use fake clocks for absent/invalid/negative/extreme Retry-After values,
    repeated taps, cancellation and expiry. Check inline text and button state
    with Web/Compose component tests; no unrequested browser/emulator run.
12. Android excludes auth preferences from pre-31 backup and 31+ cloud/device
    transfer. Corrupt/unreadable restored credentials recover to Guest without
    deleting public Room rows or crashing.
    Tip: inspect both packaged rule formats and inject encrypted-store creation/
    read failures into AuthRepository; verify safe Guest recovery and unchanged
    public cache. Do not claim that an XML assertion proves a real cloud restore.

13. Operator password rotation invalidates that user's existing sessions
    atomically without revoking another account or changing credentials as a
    deployment side effect.
    Tip: cover existing/new developer provisioning, transaction rollback and
    old-token rejection; preserve the command's existing password requirements.

## Postconditions: Stage 2

14. Browser login/registration sets the production host-only Secure/HttpOnly/Lax
    session cookie, returns metadata without a raw bearer, and reuses hash-only
    AuthSession persistence. Expiry is absolute and is enforced server-side.
    Android/legacy response shapes remain compatible.
    Tip: assert response JSON, cookie attributes/path/expiry, stored hashes,
    replacement-session invalidation and unchanged bearer contract cases.
15. Cookie-authenticated writes require valid Django CSRF and trusted origin.
    Browser login/registration require CSRF even with no prior session. Failed
    checks do not mutate accounts, Saved, follows, reactions or analysis records.
    Tip: use `Client(enforce_csrf_checks=True)` across bootstrap/auth and every
    auth-aware unsafe route, including retained legacy/link/view POSTs; test
    missing/wrong/stale token, hostile Origin and valid rotated-token success.
16. Missing credentials allow public Guest reads. Invalid supplied bearer cannot
    fall back to a valid cookie; invalid cookies do not silently become Guest.
    Auth-aware responses vary by Cookie and Authorization and cannot be shared
    as personalized CDN responses.
    Tip: test missing/valid/expired/malformed cookie and bearer combinations,
    conflicting identities and two-user cache-header/projection isolation.
17. Startup/reload/cross-tab account changes cannot mix viewer metadata, Saved
    flags or late bootstrap results. Temporary network failure leaves cookies
    intact; a fresh unconfirmed page keeps public browsing and an account-status
    retry state without sending protected writes under an inferred identity.
    An invalid-cookie read never clears a newer authentication cookie through
    a delayed Set-Cookie expiry response.
    Tip: test delayed A/B bootstrap, logout/login races, tab re-entry, 401 versus
    transport error, and preservation of contextual Reader/MY return navigation.
18. Web neither persists nor receives raw browser tokens. Legacy localStorage is
    removed; only its exact old bearer is targeted for best-effort revocation.
    Migration does not invalidate unrelated Android sessions or falsely report
    that offline/unvisited browsers' old tokens were revoked.
    Tip: test missing/corrupt/valid/expired old entries, revocation failure and
    multiple tabs; inspect storage writes and server session IDs/hashes.
19. Failed browser logout is visibly retryable and does not claim cookie removal
    or server revocation. Successful logout/repeated logout becomes Guest;
    Android retains local logout on a transport failure.
    Tip: cover 204/401/CSRF failure/network failure, cookie deletion attributes,
    session-row state, protected query cleanup and retained auth-return routes.
20. Cookie/Set-Cookie, origin and CSRF behavior work through the configured Web
    rewrite; the deployed CSP permits the actual app while restricting scripts
    and framing. Rollback cannot restore insecure browser-token persistence.
    Tip: collect preview HTTP/proxy evidence after deployment and, only when
    requested, browser evidence of cookie persistence/logout and CSP violations.
    Local TestClient/header assertions alone do not establish browser behavior.

## Undefined behavior and deferred work

- Rate limits reduce guessing/creation abuse; they do not establish resistance
  to a distributed attack below the configured budgets or claim zero timing leaks.
- Email identity/recovery/deletion remain the existing release gaps. No tests
  here establish email delivery, verified ownership, single-use recovery tokens
  or password-reset revocation; those belong to the later feature's oracle.
- No latency/security-audit score, penetration-test pass or device/browser pass
  is claimed by writing this draft.

## Review checkpoint

- [ ] Stage 1 defaults and shared-network behavior accepted.
- [ ] Stage 2 browser-only one-time sign-in and fresh-page restore behavior accepted.
- [ ] Canonical product/UI/guest contracts updated with the implementing stage.
- [ ] New executable tests derived from these oracles, with remaining gaps explicit.
