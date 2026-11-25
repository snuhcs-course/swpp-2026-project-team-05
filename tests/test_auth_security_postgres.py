import json
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from threading import Barrier
from unittest import skipUnless
from unittest.mock import patch

from django.contrib.auth.hashers import make_password
from django.db import close_old_connections, connection
from django.test import Client, TransactionTestCase, override_settings
from django.utils import timezone

from server.auth_throttle import RateLimited, cleanup_expired, key_hash, reserve
from server.models import AuthSession, AuthThrottleBucket, UserAccount


@skipUnless(connection.vendor == "postgresql", "Requires the isolated PostgreSQL run")
class AuthConcurrencyTests(TransactionTestCase):
    def concurrent(self, actions):
        barrier = Barrier(len(actions))
        def run(action):
            close_old_connections()
            try:
                barrier.wait(timeout=10)
                return action()
            finally:
                close_old_connections()
        with ThreadPoolExecutor(max_workers=len(actions)) as pool:
            return list(pool.map(run, actions))

    @override_settings(AUTH_LOGIN_IP_LIMIT=100, AUTH_LOGIN_ACCOUNT_LIMIT=3, AUTH_TRUST_VERCEL_PROXY=False)
    def test_workers_share_exact_admission_and_rejected_requests_never_hash(self):
        UserAccount.objects.create(username="reader", email="reader@example.com", password_hash=make_password("fixture-456-password"))
        def login():
            return Client().post("/api/v1/auth/login", json.dumps({"identifier": "reader", "password": "wrong"}),
                                 content_type="application/json").status_code
        with patch("server.authentication.check_password", return_value=False) as hasher:
            statuses = self.concurrent([login] * 8)
        self.assertEqual(statuses.count(401), 3)
        self.assertEqual(statuses.count(429), 5)
        self.assertEqual(hasher.call_count, 3)
        self.assertEqual(AuthThrottleBucket.objects.get(scope="login_account").count, 3)
        self.assertEqual(AuthSession.objects.count(), 0)

    def test_simultaneous_expiry_reset_reuses_one_counter(self):
        now = timezone.now()
        reserve("test", "one", limit=3, window=60, now=now - timedelta(seconds=60))
        original = AuthThrottleBucket.objects.get().pk
        def admit():
            try:
                reserve("test", "one", limit=3, window=60, now=now)
                return True
            except RateLimited:
                return False
        self.assertEqual(sum(self.concurrent([admit] * 8)), 3)
        row = AuthThrottleBucket.objects.get()
        self.assertEqual(row.pk, original)
        self.assertEqual(row.count, 3)

    def test_cleanup_racing_with_reuse_never_deletes_a_refreshed_counter(self):
        now = timezone.now()
        for i in range(8):
            identity = str(i)
            reserve("test", identity, limit=2, window=60, now=now - timedelta(seconds=60))
            self.concurrent([
                lambda: reserve("test", identity, limit=2, window=60, now=now),
                lambda: cleanup_expired(now),
            ])
            self.assertEqual(AuthThrottleBucket.objects.get(scope="test", expires_at__gt=now,
                key_hash=key_hash("test", identity)).count, 1)
