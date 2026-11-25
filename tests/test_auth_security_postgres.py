import json
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from threading import Barrier
from unittest import skipUnless
from unittest.mock import patch
from uuid import uuid4

from django.apps import apps
from django.contrib.auth.hashers import make_password
from django.db import DatabaseError, close_old_connections, connection, transaction
from django.test import Client, TransactionTestCase, override_settings
from django.utils import timezone

from server.auth_throttle import RateLimited, cleanup_expired, key_hash, reserve
from server.models import AuthSession, AuthThrottleBucket, UserAccount


@skipUnless(connection.vendor == "postgresql", "Requires the isolated PostgreSQL run")
class AuthConcurrencyTests(TransactionTestCase):
    def test_managed_tables_are_backend_only_without_breaking_guest_api(self):
        tables = sorted({model._meta.db_table for model in apps.get_app_config("server").get_models()
                         if model._meta.managed} | {"django_migrations"})
        with connection.cursor() as cursor:
            for table in tables:
                cursor.execute("SELECT relrowsecurity FROM pg_class WHERE oid = %s::regclass", [table])
                self.assertTrue(cursor.fetchone()[0], table)
                cursor.execute("SELECT COUNT(*) FROM pg_class c, LATERAL aclexplode(c.relacl) a "
                               "WHERE c.oid = %s::regclass AND a.grantee = 0", [table])
                self.assertEqual(cursor.fetchone()[0], 0, table)
            cursor.execute("SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated')")
            for (name,) in cursor.fetchall():
                for table in tables:
                    for privilege in ("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER"):
                        cursor.execute("SELECT has_table_privilege(%s, %s, %s)", [name, table, privilege])
                        self.assertFalse(cursor.fetchone()[0], (name, table, privilege))
        reserve("test", "owner", limit=2, window=60)
        role = connection.ops.quote_name("frameless_boundary_" + uuid4().hex)
        with connection.cursor() as cursor:
            cursor.execute(f"CREATE ROLE {role} NOLOGIN NOSUPERUSER NOBYPASSRLS")
        try:
            # Even an accidental future row-operation grant cannot bypass RLS.
            with connection.cursor() as cursor:
                cursor.execute(f"GRANT USAGE ON SCHEMA public TO {role}")
                cursor.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON frameless_auth_throttle TO {role}")
            with transaction.atomic(), connection.cursor() as cursor:
                cursor.execute(f"SET LOCAL ROLE {role}")
                cursor.execute("SELECT COUNT(*) FROM frameless_auth_throttle")
                self.assertEqual(cursor.fetchone()[0], 0)
                cursor.execute("UPDATE frameless_auth_throttle SET count = 0")
                self.assertEqual(cursor.rowcount, 0)
                cursor.execute("DELETE FROM frameless_auth_throttle")
                self.assertEqual(cursor.rowcount, 0)
            with self.assertRaises(DatabaseError) as denied:
                with transaction.atomic(), connection.cursor() as cursor:
                    cursor.execute(f"SET LOCAL ROLE {role}")
                    cursor.execute("INSERT INTO frameless_auth_throttle (id, scope, key_hash, count, expires_at) "
                                   "VALUES (999999, 'test', %s, 0, CURRENT_TIMESTAMP)", ["0" * 64])
            self.assertEqual(denied.exception.__cause__.sqlstate, "42501")
            self.assertIn("row-level security", str(denied.exception).lower())
        finally:
            with connection.cursor() as cursor:
                cursor.execute(f"DROP OWNED BY {role}")
                cursor.execute(f"DROP ROLE {role}")
        self.assertEqual(AuthThrottleBucket.objects.get().count, 1)
        self.assertEqual(Client().get("/api/v1/articles").status_code, 200)

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
