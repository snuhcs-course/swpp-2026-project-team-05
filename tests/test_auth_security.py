import json
from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth.hashers import check_password, make_password
from django.core.exceptions import ImproperlyConfigured
from django.db import DatabaseError
from django.test import Client, RequestFactory, TestCase, override_settings
from django.utils import timezone

from server import auth_throttle
from server.authentication import authenticate, create_session, provision_developer
from server.models import AuthSession, AuthThrottleBucket, UserAccount
from server.settings import positive_env


class AuthSecurityTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.user = UserAccount.objects.create(username="reader", email="reader@example.com",
            password_hash=make_password("private-test-password-456"))
        cls.other = UserAccount.objects.create(username="other", password_hash=make_password("other-password-456"))

    def login(self, identifier="reader", password="wrong", path="/api/v1/auth/login", **headers):
        key = "username" if path == "/api/auth/login" else "identifier"
        return self.client.post(path, json.dumps({key: identifier, "password": password}),
            content_type="application/json", **headers)

    def register(self, **changes):
        body = dict(email="new@example.com", username="new_reader", password="private-test-password-456",
                    password_confirmation="private-test-password-456")
        body.update(changes)
        return self.client.post("/api/v1/auth/register", json.dumps(body), content_type="application/json")

    @override_settings(AUTH_LOGIN_ACCOUNT_LIMIT=2, AUTH_TRUST_VERCEL_PROXY=False)
    def test_aliases_and_legacy_route_share_budget_before_hashing(self):
        self.assertEqual(self.login(" READER ").status_code, 401)
        self.assertEqual(self.login("READER@EXAMPLE.COM", path="/api/auth/login").status_code, 401)
        with patch("server.authentication.check_password") as hasher:
            denied = self.login(password="private-test-password-456")
        self.assertEqual(denied.status_code, 429)
        self.assertEqual(denied.json()["error"]["code"], "RATE_LIMITED")
        self.assertGreater(int(denied["Retry-After"]), 0)
        hasher.assert_not_called()
        self.assertEqual(AuthSession.objects.count(), 0)
        self.assertEqual(AuthThrottleBucket.objects.get(scope="login_account").count, 2)

    @override_settings(AUTH_LOGIN_ACCOUNT_LIMIT=2)
    def test_unknown_and_known_accounts_share_public_failure_semantics(self):
        with patch("server.authentication.make_password", wraps=make_password) as dummy:
            unknown = self.login("missing")
        self.assertEqual(dummy.call_count, 1)
        known = self.login()
        self.assertEqual(unknown.status_code, known.status_code)
        self.assertEqual(unknown.json()["error"], known.json()["error"])
        self.login("missing")
        self.assertEqual(self.login(" MISSING ").status_code, 429)

    @override_settings(AUTH_LOGIN_IP_LIMIT=3, AUTH_LOGIN_ACCOUNT_LIMIT=10, AUTH_TRUST_VERCEL_PROXY=False)
    def test_success_resets_account_but_not_ip_and_does_not_remove_other_sessions(self):
        existing, _ = create_session(self.user)
        self.login()
        good = self.login(password="private-test-password-456")
        self.assertEqual(good.status_code, 200)
        self.assertFalse(AuthThrottleBucket.objects.filter(scope="login_account").exists())
        self.login("other")
        with patch("server.authentication.make_password") as hasher:
            blocked = self.login("missing")
        self.assertEqual(blocked.status_code, 429)
        hasher.assert_not_called()
        self.assertEqual(self.client.get("/api/v1/auth/me", HTTP_AUTHORIZATION=f"Bearer {existing}").status_code, 200)

    @override_settings(AUTH_REGISTER_IP_LIMIT=2)
    def test_registration_limit_counts_validation_failures_without_creating_accounts(self):
        self.assertEqual(self.register(password="short").status_code, 400)
        self.assertEqual(self.register(password="short").status_code, 400)
        with patch("server.authentication.make_password") as hasher:
            blocked = self.register()
        self.assertEqual(blocked.status_code, 429)
        hasher.assert_not_called()
        self.assertFalse(UserAccount.objects.filter(username="new_reader").exists())

    def test_counter_expiry_is_fixed_and_rows_are_reused(self):
        now = timezone.now()
        reserve = lambda at: auth_throttle.reserve("test", "secret-identity", limit=2, window=60, now=at)
        reserve(now)
        row = AuthThrottleBucket.objects.get()
        reserve(now + timedelta(seconds=59))
        with self.assertRaises(auth_throttle.RateLimited) as denied:
            reserve(now + timedelta(seconds=59, microseconds=500000))
        self.assertEqual(denied.exception.retry_after, 1)
        self.assertEqual(AuthThrottleBucket.objects.get().expires_at, row.expires_at)
        reserve(now + timedelta(seconds=60))
        refreshed = AuthThrottleBucket.objects.get()
        self.assertEqual(refreshed.pk, row.pk)
        self.assertEqual(refreshed.count, 1)
        self.assertNotIn("secret-identity", refreshed.key_hash)
        self.assertEqual(len(refreshed.key_hash), 64)
        self.assertNotEqual(auth_throttle.key_hash("a", "x"), auth_throttle.key_hash("b", "x"))

    def test_cleanup_is_bounded_and_preserves_active_rows(self):
        now = timezone.now()
        AuthThrottleBucket.objects.bulk_create([AuthThrottleBucket(scope="test", key_hash=f"{i:064x}",
            count=1, expires_at=now - timedelta(seconds=1)) for i in range(105)])
        auth_throttle.reserve("active", "reader", limit=2, window=60, now=now)
        auth_throttle.cleanup_expired(now)
        self.assertEqual(AuthThrottleBucket.objects.count(), 6)
        self.assertEqual(AuthThrottleBucket.objects.get(scope="active").count, 1)
        with patch.object(AuthThrottleBucket.objects, "filter", side_effect=DatabaseError("private diagnostic")):
            auth_throttle.cleanup_expired(now)

    def test_counter_failure_is_safe_and_housekeeping_failure_does_not_bypass_admission(self):
        with patch("server.auth_throttle.reserve", side_effect=DatabaseError("private diagnostic")):
            response = self.login(password="private-test-password-456")
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["error"]["code"], "AUTH_UNAVAILABLE")
        self.assertNotContains(response, "private diagnostic", status_code=503)
        self.assertEqual(AuthSession.objects.count(), 0)
        self.assertEqual(self.client.get("/api/v1/articles").status_code, 200)
        with patch.object(AuthThrottleBucket.objects, "filter", side_effect=DatabaseError("cleanup")):
            auth_throttle.cleanup_expired(timezone.now())
        with patch("server.auth_throttle.cleanup_expired") as cleanup:
            good = self.login(password="private-test-password-456")
        self.assertEqual(good.status_code, 200)
        cleanup.assert_called_once()
        self.assertEqual(AuthThrottleBucket.objects.get(scope="login_ip").count, 1)

    @override_settings(AUTH_REGISTER_IP_LIMIT=1000)
    def test_typed_credentials_unicode_bounds_and_legacy_passwords(self):
        for field in ("email", "username", "password", "password_confirmation"):
            for bad in (None, 123, [], {}, True, ""):
                with self.subTest(field=field, bad=bad):
                    self.assertEqual(self.register(**{field: bad}).status_code, 400)
        for length, expected in ((9, 400), (10, 201), (128, 201), (129, 400)):
            password = "A!" + "🙂" * (length - 2)
            response = self.register(email=f"n{length}@example.com", username=f"new_{length}",
                password=password, password_confirmation=password, is_developer=True)
            self.assertEqual(response.status_code, expected)
            if expected == 201:
                user = UserAccount.objects.get(username=f"new_{length}")
                self.assertTrue(check_password(password, user.password_hash))
                self.assertFalse(user.is_developer)
        for invalid in (None, 123, [], {}, "x" * 255):
            self.assertEqual(self.login(identifier=invalid).status_code, 400)
        self.assertEqual(self.register(email="x" * 255).status_code, 400)
        for password in ("password123", "123456789012345"):
            response = self.register(password=password, password_confirmation=password)
            self.assertEqual(response.status_code, 400)
            self.assertIn("password", response.json()["error"]["fields"])
            self.assertNotContains(response, password, status_code=400)
        for length, expected in ((2, 400), (3, 201), (30, 201), (31, 400)):
            self.assertEqual(self.register(username="n" * length,
                email=f"username{length}@example.com").status_code, expected)
        email = "x" * 64 + "@" + "a" * 63 + "." + "b" * 63 + "." + "c" * 61
        self.assertEqual(len(email), 254)
        self.assertEqual(self.register(username="email_edge", email=email).status_code, 201)
        self.assertEqual(self.login(email, "private-test-password-456").status_code, 200)
        legacy = UserAccount.objects.create(username="legacy", password_hash=make_password("1234"))
        self.assertEqual(self.login("legacy", "1234").status_code, 200)
        self.assertTrue(UserAccount.objects.filter(pk=legacy.pk).exists())

    def test_oversized_body_never_hashes_or_creates_session(self):
        with patch("server.authentication.make_password") as hasher:
            for path in ("/api/auth/login", "/api/v1/auth/login", "/api/v1/auth/register"):
                response = self.client.post(path, json.dumps({"password": "a" * 8192}), content_type="application/json")
                self.assertEqual(response.status_code, 413)
        hasher.assert_not_called()
        self.assertEqual(AuthSession.objects.count(), 0)

    def test_initial_session_failure_rolls_back_registration(self):
        with patch("server.api_v1.create_session", side_effect=DatabaseError("do not expose")):
            response = self.register()
        self.assertEqual(response.status_code, 503)
        self.assertFalse(UserAccount.objects.filter(username="new_reader").exists())
        self.assertEqual(AuthSession.objects.count(), 0)

    def test_auth_responses_are_no_store_even_for_errors_and_wrong_methods(self):
        responses = [self.login(), self.login(password="private-test-password-456"), self.register(), self.register(password="short"),
            self.client.get("/api/v1/auth/me"), self.client.get("/api/auth/me"),
            self.client.get("/api/v1/auth/login"), self.client.post("/api/v1/auth/logout")]
        with override_settings(AUTH_LOGIN_ACCOUNT_LIMIT=1):
            self.login()
            responses.append(self.login())
        with patch("server.auth_throttle.reserve", side_effect=DatabaseError("private diagnostic")):
            responses.append(self.login())
        for response in responses:
            self.assertEqual(response["Cache-Control"], "no-store")

    def test_auth_application_logs_do_not_contain_submitted_credentials(self):
        with self.assertLogs("server.views", level="WARNING") as logs:
            self.login("do-not-log-reader", "do-not-log-password", path="/api/auth/login")
        recorded = "\n".join(logs.output)
        self.assertNotIn("do-not-log-reader", recorded)
        self.assertNotIn("do-not-log-password", recorded)

    def test_exact_body_byte_limit_is_enforced_before_hashing(self):
        base = json.dumps({"identifier": "missing", "password": "wrong"})
        with patch("server.authentication.make_password", wraps=make_password) as hasher:
            for size, expected in ((8191, 401), (8192, 401), (8193, 413)):
                response = self.client.post("/api/v1/auth/login", base + " " * (size - len(base)), content_type="application/json")
                self.assertEqual(response.status_code, expected)
        self.assertEqual(hasher.call_count, 2)

    def test_operator_rotation_revokes_only_its_sessions_and_old_verified_credentials(self):
        _, old = create_session(self.user)
        _, other = create_session(self.other)
        rotated = provision_developer("reader", "rotated-private-password-456")
        self.assertFalse(AuthSession.objects.filter(pk=old.pk).exists())
        self.assertTrue(AuthSession.objects.filter(pk=other.pk).exists())
        self.assertIsNone(authenticate("reader", "private-test-password-456"))
        self.assertIsNotNone(authenticate("reader", "rotated-private-password-456"))
        with self.assertRaises(auth_throttle.CredentialsChanged):
            create_session(self.user)  # A login which verified the old password cannot race the reset.
        with patch("server.authentication.AuthSession.objects.filter", side_effect=DatabaseError("rotate")):
            with self.assertRaises(DatabaseError):
                provision_developer("reader", "another-private-password-456")
        self.assertIsNotNone(authenticate("reader", "rotated-private-password-456"))
        self.assertIsNotNone(rotated)


class AuthConfigurationTests(TestCase):
    def test_ip_trust_canonicalization_and_spoofed_headers(self):
        request = RequestFactory().get("/", REMOTE_ADDR="::ffff:192.0.2.1",
            HTTP_X_FORWARDED_FOR="198.51.100.2", HTTP_X_VERCEL_FORWARDED_FOR="2001:db8::1")
        with override_settings(AUTH_TRUST_VERCEL_PROXY=False):
            self.assertEqual(auth_throttle.client_ip(request), "192.0.2.1")
        with override_settings(AUTH_TRUST_VERCEL_PROXY=True):
            self.assertEqual(auth_throttle.client_ip(request), "2001:db8::1")
            request.META["HTTP_X_VERCEL_FORWARDED_FOR"] = "192.0.2.1, 198.51.100.2"
            self.assertEqual(auth_throttle.client_ip(request), "unknown")
            del request.META["HTTP_X_VERCEL_FORWARDED_FOR"]
            self.assertEqual(auth_throttle.client_ip(request), "unknown")
            request.META["HTTP_X_VERCEL_FORWARDED_FOR"] = None
            self.assertEqual(auth_throttle.client_ip(request), "unknown")

    def test_nonpositive_and_invalid_configuration_is_rejected(self):
        for value in ("0", "-1", "", "no", "1.5", "1000001"):
            with patch.dict("os.environ", {"AUTH_TEST_LIMIT": value}):
                with self.assertRaises(ImproperlyConfigured):
                    positive_env("AUTH_TEST_LIMIT", 10)
        with patch.dict("os.environ", {"AUTH_TEST_LIMIT": "12"}):
            self.assertEqual(positive_env("AUTH_TEST_LIMIT", 10), 12)
