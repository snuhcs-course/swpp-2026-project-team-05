# Account security test specification

| Field | Value |
| --- | --- |
| Status | Stage 1 verified and deployed; Stage 2 deferred, not implemented |
| Owner | Account/API and client maintainers |
| Source requirements | [Account security hardening](../features/account-security.md) |
| Date | 2026-11-25 |

## Scope and preconditions

Stage 1 protects the existing credential APIs. The deferred Stage 2 proposal
would change browser transport while preserving Android/legacy bearer clients;
none of its postconditions is a current passing claim. Tests use isolated SQLite and
PostgreSQL databases, controllable UTC time and fake credentials. No real account,
production database, mail provider, live password guessing or live AI call.

Use two ordinary accounts, one developer, one nonexistent identifier, two trusted
client IPs, untrusted forwarded headers, expired/valid sessions and separate
browser cookie jars for Stage 2. Only synthetic credentials appear in test
source; reports/logs must not contain submitted secrets.

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
   Tested by: `tests.test_auth_security.AuthSecurityTests.test_success_resets_account_but_not_ip_and_does_not_remove_other_sessions`,
   `tests.test_auth_security.AuthSecurityTests.test_registration_limit_counts_validation_failures_without_creating_accounts`,
   `tests.test_auth_security.AuthSecurityTests.test_counter_expiry_is_fixed_and_rows_are_reused`,
   `tests.test_auth_security.AuthConfigurationTests.test_nonpositive_and_invalid_configuration_is_rejected`.
4. Legacy, v1 and future browser login share a budget. Username/email aliases of
   one account share its budget; casing/whitespace does not create another.
   Unknown identifiers receive the same limit and public error semantics.
   Tested by: `tests.test_auth_security.AuthSecurityTests.test_aliases_and_legacy_route_share_budget_before_hashing`,
   `tests.test_auth_security.AuthSecurityTests.test_unknown_and_known_accounts_share_public_failure_semantics`.
   Tip: the future browser route joins this oracle when Stage 2 is implemented.
5. Concurrent workers cannot exceed admission limits. Correct credentials do not
   reset the IP budget; successful login resets the admitted account's budget.
   Tested by: `tests.test_auth_security_postgres.AuthConcurrencyTests.test_workers_share_exact_admission_and_rejected_requests_never_hash`,
   `tests.test_auth_security_postgres.AuthConcurrencyTests.test_simultaneous_expiry_reset_reuses_one_counter`,
   `tests.test_auth_security.AuthSecurityTests.test_success_resets_account_but_not_ip_and_does_not_remove_other_sessions`.
6. IP spoofing cannot select an arbitrary limiter bucket. Missing/untrusted
   forwarding information has a conservative stable fallback. Stored keys are
   namespaced hashes, not raw identifiers/IPs.
   Django-managed tables and migration metadata are backend-only: RLS is enabled
   and PUBLIC/Supabase client roles have no direct table grants. A direct database
   client cannot forge sessions or alter counters to bypass API protection;
   ordinary Guest reads through Django still work.
   Tested by: `tests.test_auth_security.AuthConfigurationTests.test_ip_trust_canonicalization_and_spoofed_headers`,
   `tests.test_auth_security.AuthSecurityTests.test_counter_expiry_is_fixed_and_rows_are_reused`.
   Tested by: `tests.test_auth_security_postgres.AuthConcurrencyTests.test_managed_tables_are_backend_only_without_breaking_guest_api`.
   Tip: verify the canonical header through the deployed proxy path separately;
   parser tests and provider documentation alone are not that evidence.
7. Expired counters are reused/pruned in bounded batches. Counter-store failure
   returns 503 without an account/session mutation; failed housekeeping does not
   disable admission or break an otherwise valid login. Public reads still work.
   Tested by: `tests.test_auth_security.AuthSecurityTests.test_cleanup_is_bounded_and_preserves_active_rows`,
   `tests.test_auth_security.AuthSecurityTests.test_counter_failure_is_safe_and_housekeeping_failure_does_not_bypass_admission`,
   `tests.test_auth_security_postgres.AuthConcurrencyTests.test_cleanup_racing_with_reuse_never_deletes_a_refreshed_counter`.
   Tip: the migration declares the expiry index; no production query-plan or
   large-table performance claim is made by these small isolated fixtures.
8. Credentials are typed strings. New passwords honor 10/128-code-point limits,
   the existing common/numeric checks and exact non-truncated content. Existing
   short login passwords remain valid; oversized bodies fail before hashing.
   Tested by: `tests.test_auth_security.AuthSecurityTests.test_typed_credentials_unicode_bounds_and_legacy_passwords`,
   `tests.test_auth_security.AuthSecurityTests.test_oversized_body_never_hashes_or_creates_session`,
   `tests.test_auth_security.AuthSecurityTests.test_exact_body_byte_limit_is_enforced_before_hashing`,
   `tests.test_news_api.NewsApiTests.test_invalid_password_is_rejected_and_expired_session_cannot_read`.
9. Wrong-password and unknown-account responses have the same status/message;
   unknown accounts perform current password-hasher work. Submitted privilege
   fields cannot grant developer access. Registration failure rolls back both
   the new account and initial session.
   Tested by: `tests.test_auth_security.AuthSecurityTests.test_unknown_and_known_accounts_share_public_failure_semantics`,
   `tests.test_auth_security.AuthSecurityTests.test_typed_credentials_unicode_bounds_and_legacy_passwords`,
   `tests.test_auth_security.AuthSecurityTests.test_initial_session_failure_rolls_back_registration`,
   `tests.test_news_api.NewsApiTests.test_register_rejects_case_insensitive_duplicate`,
   `web/src/pages/LoginPage.test.tsx > account form retry handling > does not replay a registration whose response was lost`.
   Tip: duplicate registration has uniqueness/rollback coverage but no dedicated
   concurrent-registration race test yet. Hash-call assertions do not establish
   equal wall-clock latency.
10. Every auth response, including 4xx/5xx/429 and legacy responses, is no-store.
    Logs contain safe outcomes/request IDs but no auth bodies, credentials or
    raw limiter identifiers.
    Tested by: `tests.test_auth_security.AuthSecurityTests.test_auth_responses_are_no_store_even_for_errors_and_wrong_methods`,
    `tests.test_auth_security.AuthSecurityTests.test_auth_application_logs_do_not_contain_submitted_credentials`,
    `tests.test_auth_security.AuthSecurityTests.test_counter_failure_is_safe_and_housekeeping_failure_does_not_bypass_admission`.
    Tip: local application-log assertions do not audit the hosting provider's
    access logs, tracing or reverse-proxy logging configuration.
11. Both clients preserve form/navigation state through 429/503, avoid automatic
    credential replay, disable submit for a bounded Retry-After and recover when
    the wait/request ends. Existing sessions remain valid.
    Tested by: `com.swpp.team5.frameless.ui.viewmodel.AuthViewModelTest.rateLimitWaitIsBoundedSeparatePerFormAndNeverReplaysCredentials`,
    `com.swpp.team5.frameless.ui.viewmodel.AuthViewModelTest.cooldownRefreshHandlesSleepAndErrorsNeverClearAnExistingSession`,
    `com.swpp.team5.frameless.ui.viewmodel.AuthViewModelTest.registrationCooldownPreservesTheDraftAndDoesNotBlockGuestOrLoginNavigation`,
    `com.swpp.team5.frameless.data.NewsApiClientTest.authenticationRateLimitsCarryBoundedRetryMetadataWithoutReplaying`,
    `com.swpp.team5.frameless.data.AuthRepositoryTest.retryAfterBoundsAndPackagedBackupRulesProtectTheAuthStore`,
    `web/src/lib/api.test.ts > authentication retry metadata > bounds missing, malformed, negative and extreme delays`,
    `web/src/pages/LoginPage.test.tsx > account form retry handling > preserves fields and origin, separates forms, and unlocks without replaying credentials`,
    `web/src/pages/LoginPage.test.tsx > account form retry handling > recovers from unavailable services without clearing fields or retrying`.
    Tip: Android evidence is ViewModel/HTTP-client JVM coverage, not a new Compose
    device test. Pending-request cancellation/navigation remains a separate UI
    race test gap; no browser/emulator run is claimed.
12. Android excludes auth preferences from pre-31 backup and 31+ cloud/device
    transfer. Corrupt/unreadable restored credentials recover to Guest without
    deleting public Room rows or crashing.
    Tested by: `com.swpp.team5.frameless.data.AuthRepositoryTest.retryAfterBoundsAndPackagedBackupRulesProtectTheAuthStore`,
    `com.swpp.team5.frameless.data.AuthRepositoryTest.unavailableEncryptedStoreStartsAsGuestAndNeverSendsCredentials`,
    `com.swpp.team5.frameless.data.AuthRepositoryTest.unreadableCredentialsRecoverToACleanEncryptedStore`,
    `com.swpp.team5.frameless.data.AuthRepositoryTest.failedSecureWriteRevokesTheNewSessionAndNeverKeepsAPartialLogin`,
    `com.swpp.team5.frameless.data.AuthRepositoryTest.encryptedStoreRecoveryPreservesThePublicRoomCache`.
    Tip: packaged XML and simulated key failures do not prove a real cloud/device
    restore on physical hardware. Failed revocation over an unavailable network
    cannot be reported as successful server-side logout.

13. Operator password rotation invalidates that user's existing sessions
    atomically without revoking another account or changing credentials as a
    deployment side effect.
    Tested by: `tests.test_auth_security.AuthSecurityTests.test_operator_rotation_revokes_only_its_sessions_and_old_verified_credentials`.
    Tip: the password-revision race is covered by replaying a previously verified
    account object after rotation; no concurrent operator/login stress test is
    claimed.

## Deferred oracles: Stage 2 (not implemented)

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
  is claimed by these local checks. Stage 2 tests remain proposed.

## Review checkpoint

- [x] Stage 1 defaults and shared-network behavior accepted before implementation.
- [ ] Stage 2 implementation approved again after its 2026-11-25 deferral.
- [x] Canonical product/UI/guest contracts updated for Stage 1; Stage 2 remains deferred.
- [x] Stage 1 executable tests derived from these oracles, with remaining gaps explicit.

## Local verification — 2026-11-25

The full SQLite suite passed 496 tests (11 environment-specific skips); the
isolated PostgreSQL run passed 67 tests, including three admission/cleanup races
and the direct-database-role boundary. Android passed 205 JVM tests, lint, debug APK and device-test APK compilation.
Web passed 44 tests, lint and production build. Existing backend/Android coverage
gates passed without threshold changes. PostgreSQL used a temporary local
cluster; no production credential guessing or browser/emulator/device run was
part of this verification. Compose device coverage and the deployment checks
listed above remain separate evidence.

Deployment evidence is attached to [PR #116](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/116)
and the dated entry in [`docs/testing-documentation.md`](../../docs/testing-documentation.md).
The deployed checks cover auth no-store responses, direct database-role
restrictions, and forged forwarding headers through the API/Web rewrite. They
do not establish browser-cookie behavior or physical Android backup restoration.
