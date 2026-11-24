from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from threading import Barrier, Event
from unittest import skipUnless
from unittest.mock import patch

from django.db import close_old_connections, connection, transaction
from django.test import TransactionTestCase
from django.utils import timezone

from server.models import Article, ArticleView, Publisher, TrendState
from server.trends import TrendsUnavailable, calculate_snapshot, cleanup_views, record_view, trend_payload


@skipUnless(connection.vendor == "postgresql", "PostgreSQL locking requires the dedicated PostgreSQL test run")
class TrendConcurrencyTests(TransactionTestCase):
    def setUp(self):
        self.now = timezone.now()
        publisher = Publisher.objects.create(code="news", name_ko="뉴스", domain="example.com")
        self.article = Article.objects.create(
            publisher=publisher, canonical_url="https://example.com/a", title="제목", body_text="본문",
            content_hash="0" * 64, category="SOCIETY", published_at=self.now,
        )
        TrendState.objects.update_or_create(pk=1, defaults={
            "current_snapshot": calculate_snapshot(self.now - timedelta(minutes=5), None),
            "next_refresh_at": self.now - timedelta(seconds=1),
        })

    def threaded(self, action):
        close_old_connections()
        try:
            return action()
        finally:
            close_old_connections()

    def assert_one_refresh(self, *, initial):
        if initial:
            TrendState.objects.update(current_snapshot=None)
        entered, release = Event(), Event()
        real_calculate = calculate_snapshot

        def held_calculation(now, previous):
            entered.set()
            if not release.wait(5):
                raise AssertionError("test did not release refresh")
            return real_calculate(now, previous)

        with ThreadPoolExecutor(max_workers=2) as pool, \
                patch("server.trends.calculate_snapshot", side_effect=held_calculation) as calculate:
            owner = pool.submit(self.threaded, lambda: trend_payload(user=None))
            self.assertTrue(entered.wait(3))
            try:
                loser = pool.submit(self.threaded, lambda: trend_payload(user=None))
                if initial:
                    with self.assertRaises(TrendsUnavailable):
                        loser.result(timeout=1)
                else:
                    result = loser.result(timeout=1)
                    self.assertTrue(result["is_stale"])
                self.assertEqual(calculate.call_count, 1)
            finally:
                release.set()
            self.assertEqual(len(owner.result(timeout=3)["items"]), 1)

    def test_concurrent_refresh_returns_old_snapshot_without_waiting(self):
        self.assert_one_refresh(initial=False)

    def test_initial_concurrent_loser_is_unavailable_not_empty(self):
        self.assert_one_refresh(initial=True)

    def test_concurrent_view_inserts_are_one_row(self):
        barrier = Barrier(6)

        def write():
            barrier.wait(timeout=3)
            return record_view(article_id=self.article.id, key="same-reader")

        with ThreadPoolExecutor(max_workers=6) as pool:
            results = [pool.submit(self.threaded, write) for _ in range(6)]
            self.assertTrue(all(future.result(timeout=5) for future in results))
        self.assertEqual(ArticleView.objects.count(), 1)

    def test_cleanup_skips_concurrently_renewed_view(self):
        old = self.now - timedelta(hours=4)
        row = ArticleView.objects.create(article=self.article, viewer_key="renewing", last_viewed_at=old)
        ArticleView.objects.create(article=self.article, viewer_key="expired", last_viewed_at=old)
        entered, release = Event(), Event()

        def renew():
            with transaction.atomic():
                ArticleView.objects.filter(pk=row.pk).update(last_viewed_at=self.now)
                entered.set()
                if not release.wait(5):
                    raise AssertionError("test did not release writer")

        with ThreadPoolExecutor(max_workers=1) as pool:
            writer = pool.submit(self.threaded, renew)
            self.assertTrue(entered.wait(3))
            try:
                result = cleanup_views()
                self.assertEqual(result["deleted"], 1)
                self.assertTrue(result["remaining"])
            finally:
                release.set()
            writer.result(timeout=3)
        self.assertTrue(ArticleView.objects.filter(pk=row.pk).exists())
        self.assertFalse(cleanup_views()["remaining"])

    def test_real_statement_timeout_rolls_back_savepoint_and_preserves_cache(self):
        before = TrendState.objects.get().current_snapshot

        def slow_calculation(*_):
            with connection.cursor() as cursor:
                cursor.execute("SELECT pg_sleep(3)")

        with patch("server.trends.calculate_snapshot", side_effect=slow_calculation):
            result = trend_payload(user=None)
        self.assertEqual(result["calculated_at"], before["calculated_at"])
        self.assertGreater(TrendState.objects.get().next_refresh_at, self.now)
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            self.assertEqual(cursor.fetchone(), (1,))
