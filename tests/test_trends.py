import json
from io import StringIO
from datetime import datetime, timedelta, timezone as dt_timezone
from unittest.mock import patch
from uuid import UUID, uuid4

from django.db import DatabaseError
from django.core.management import call_command, CommandError
from django.test import TestCase

from server.authentication import create_session
from server.models import Article, ArticleView, Publisher, SavedArticle, StoryCluster, TrendState, UserAccount, UserPublisherFollow
from server.trends import calculate_snapshot, cleanup_views, record_view, trend_payload, viewer_key


NOW = datetime(2026, 11, 25, 12, tzinfo=dt_timezone.utc)


class TrendTests(TestCase):
    def setUp(self):
        self.publisher = Publisher.objects.create(code="news", name_ko="뉴스", domain="example.com")
        TrendState.objects.update_or_create(pk=1, defaults={
            "current_snapshot": None, "previous_snapshot": None, "next_refresh_at": NOW,
        })

    def article(self, number, *, cluster=None, feed=True, visible=True):
        return Article.objects.create(
            id=UUID(int=number), publisher=self.publisher, story_cluster=cluster,
            canonical_url=f"https://example.com/{number}", title=f"기사 {number}",
            body_text="본문", content_hash="0" * 64, category="SOCIETY",
            published_at=NOW - timedelta(minutes=number), is_feed_visible=feed,
            visibility="VISIBLE" if visible else "HIDDEN",
        )

    def view(self, article, who="a", at=NOW):
        return ArticleView.objects.create(article=article, viewer_key=who, last_viewed_at=at)

    def snapshot(self):
        with patch("server.trends.timezone.now", return_value=NOW):
            return trend_payload(user=None)

    def test_repeat_upsert_and_older_write_do_not_inflate_or_rewind(self):
        article = self.article(1)
        with patch("server.trends.timezone.now", return_value=NOW):
            for _ in range(3):
                self.assertTrue(record_view(article_id=article.id, key="reader"))
        with patch("server.trends.timezone.now", return_value=NOW - timedelta(minutes=1)):
            record_view(article_id=article.id, key="reader")
        row = ArticleView.objects.get()
        self.assertEqual(row.last_viewed_at, NOW)

    def test_identity_validation_and_account_precedence(self):
        article = self.article(1)
        path = f"/api/v1/articles/{article.id}/view"
        self.assertEqual(self.client.post(path, "{}", content_type="application/json").status_code, 400)
        guest = str(uuid4())
        self.assertEqual(self.client.post(path, "{}", content_type="application/json",
                                         HTTP_X_GUEST_VIEWER=guest).status_code, 204)
        user = UserAccount.objects.create(username="reader", password_hash="unused")
        token, _ = create_session(user)
        for _ in range(2):
            response = self.client.post(path, "{}", content_type="application/json",
                                        HTTP_AUTHORIZATION=f"Bearer {token}", HTTP_X_GUEST_VIEWER="invalid")
            self.assertEqual(response.status_code, 204)
        self.assertEqual(ArticleView.objects.count(), 2)
        self.assertNotEqual(viewer_key(user=user, guest_id=""), viewer_key(user=None, guest_id=str(user.id)))
        response = self.client.post(path, "{}", content_type="application/json",
                                    HTTP_AUTHORIZATION="Bearer invalid", HTTP_X_GUEST_VIEWER=guest)
        self.assertEqual(response.status_code, 401)

    def test_hidden_and_unknown_reads_rejected_and_get_never_records(self):
        hidden = self.article(1, visible=False)
        for article_id in [hidden.id, uuid4()]:
            response = self.client.post(f"/api/v1/articles/{article_id}/view", "{}",
                                        content_type="application/json", HTTP_X_GUEST_VIEWER=str(uuid4()))
            self.assertEqual(response.status_code, 404)
        visible = self.article(2)
        self.assertEqual(self.client.get(f"/api/v1/articles/{visible.id}").status_code, 200)
        self.assertEqual(ArticleView.objects.count(), 0)

    def test_body_cannot_forge_counts_or_timestamps(self):
        article = self.article(1)
        response = self.client.post(f"/api/v1/articles/{article.id}/view", json.dumps({"count": 99}),
                                    content_type="application/json", HTTP_X_GUEST_VIEWER=str(uuid4()))
        self.assertEqual(response.status_code, 400)
        self.assertFalse(ArticleView.objects.exists())

    def test_inactive_publisher_is_excluded_and_arbitrary_filters_rejected(self):
        article = self.article(1)
        self.view(article)
        self.publisher.is_active = False
        self.publisher.save(update_fields=["is_active"])
        self.assertFalse(record_view(article_id=article.id, key="new"))
        self.assertEqual(calculate_snapshot(NOW, None)["items"], [])
        self.assertEqual(self.client.get("/api/v1/trends?limit=999").status_code, 400)

    def test_global_rank_preserves_personalized_representative_and_saved_isolation(self):
        cluster = StoryCluster.objects.create(category="SOCIETY")
        self.article(1, cluster=cluster)
        other = Publisher.objects.create(code="other", name_ko="다른 언론사", domain="other.example.com")
        b = self.article(2, cluster=cluster)
        b.publisher = other
        b.save(update_fields=["publisher"])
        self.view(b)
        user = UserAccount.objects.create(username="reader", password_hash="unused")
        UserPublisherFollow.objects.create(user=user, publisher=other)
        SavedArticle.objects.create(user=user, article=b)
        with patch("server.trends.timezone.now", return_value=NOW):
            public = trend_payload(user=None)["items"][0]
            personal = trend_payload(user=user)["items"][0]
        self.assertEqual(public["rank"], personal["rank"])
        self.assertEqual(public["story"]["story_key"], personal["story"]["story_key"])
        self.assertEqual(personal["story"]["representative_article"]["id"], str(b.id))
        self.assertTrue(personal["story"]["representative_article"]["is_saved"])
        self.assertFalse(public["story"]["representative_article"]["is_saved"])

    def test_window_boundary_is_read_time_not_publication_time(self):
        article = self.article(1)
        Article.objects.filter(pk=article.pk).update(published_at=NOW - timedelta(days=500))
        self.view(article, "boundary", NOW - timedelta(hours=3))
        self.view(article, "expired", NOW - timedelta(hours=3, microseconds=1))
        snapshot = calculate_snapshot(NOW, None)
        self.assertEqual(snapshot["items"][0]["viewer_count"], 1)
        ArticleView.objects.filter(viewer_key="boundary").delete()
        self.assertEqual(calculate_snapshot(NOW, None)["items"][0]["kind"], "LATEST")

    def test_topic_distinct_viewers_include_readable_support_articles(self):
        cluster = StoryCluster.objects.create(category="SOCIETY")
        a = self.article(1, cluster=cluster)
        b = self.article(2, cluster=cluster, feed=False)
        isolated_support = self.article(3, feed=False)
        for article in [a, b, isolated_support]:
            self.view(article)
        self.view(b, "b")
        rows = calculate_snapshot(NOW, None)["items"]
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["viewer_count"], 2)
        self.assertEqual(rows[0]["story_key"], f"cluster:{cluster.id}")

    def test_sparse_and_zero_popularity_fill_to_five_with_distinct_latest(self):
        articles = [self.article(i) for i in range(1, 8)]
        for count in range(7):
            with self.subTest(popular=count):
                ArticleView.objects.all().delete()
                for article in articles[:count]:
                    self.view(article)
                rows = calculate_snapshot(NOW, None)["items"]
                self.assertEqual(len(rows), 5)
                self.assertEqual(len({row["story_key"] for row in rows}), 5)
                self.assertEqual(sum(row["kind"] == "POPULAR" for row in rows), min(count, 5))
                for row in rows[count:]:
                    self.assertIsNone(row["rank"])
                    self.assertIsNone(row["viewer_count"])

    def test_small_or_empty_corpus_has_only_real_topics(self):
        for count in range(5):
            with self.subTest(count=count):
                if count:
                    self.article(count)
                self.assertEqual(len(calculate_snapshot(NOW, None)["items"]), count)

    def test_ties_preserve_prior_rank_then_key_including_cutoff(self):
        for i in reversed(range(1, 7)):
            self.view(self.article(i))
        first = calculate_snapshot(NOW, None)
        keys = [f"article:{UUID(int=i)}" for i in range(1, 6)]
        self.assertEqual([row["story_key"] for row in first["items"]], keys)
        previous = {"calculated_at": NOW.isoformat(), "items": [
            {"story_key": key, "kind": "POPULAR", "rank": rank, "viewer_count": 1}
            for rank, key in enumerate(reversed(keys), 1)]}
        self.assertEqual([row["story_key"] for row in calculate_snapshot(NOW, previous)["items"]], list(reversed(keys)))

    def test_movement_only_with_two_complete_recent_popularity_lists(self):
        articles = [self.article(i) for i in range(1, 7)]
        for article in articles[:5]:
            self.view(article)
        self.snapshot()
        self.view(articles[5], "a")
        self.view(articles[5], "b")
        self.view(articles[2], "b")
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=5)):
            items = trend_payload(user=None)["items"]
        self.assertEqual(items[0]["movement"], "UP")
        self.assertEqual(items[1]["movement"], "NEW")
        self.assertTrue(any(item["movement"] == "DOWN" for item in items))
        ArticleView.objects.filter(article__in=articles[1:]).delete()
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=10)):
            sparse = trend_payload(user=None)["items"]
        self.assertTrue(all(item["movement"] is None and item["previous_rank"] is None for item in sparse))
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(hours=1)):
            self.assertTrue(all(item["movement"] is None for item in trend_payload(user=None)["items"]))

    def test_warm_cache_does_not_aggregate_or_delete(self):
        self.article(1)
        first = self.snapshot()
        with patch("server.trends.calculate_snapshot", side_effect=AssertionError("unnecessary aggregate")), \
                patch("server.trends.cleanup_views", side_effect=AssertionError("delete on read")):
            self.assertEqual(self.snapshot(), first)

    def test_latest_baseline_never_produces_new_badges_and_unchanged_full_list_has_no_movement(self):
        articles = [self.article(i) for i in range(1, 6)]
        self.view(articles[0])
        self.snapshot()
        for article in articles[1:]:
            self.view(article)
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=5)):
            first_full = trend_payload(user=None)["items"]
        self.assertTrue(all(row["movement"] is None and row["previous_rank"] is None for row in first_full))
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=10)):
            unchanged = trend_payload(user=None)["items"]
        self.assertTrue(all(row["movement"] is None and row["previous_rank"] == row["rank"] for row in unchanged))

    def test_cached_snapshot_expires_just_after_fifteen_minutes(self):
        self.article(1)
        self.snapshot()
        TrendState.objects.update(next_refresh_at=NOW + timedelta(hours=1))
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=15)):
            self.assertEqual(self.client.get("/api/v1/trends").status_code, 200)
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=15, microseconds=1)):
            self.assertEqual(self.client.get("/api/v1/trends").status_code, 503)

    def test_management_command_reports_incomplete_cleanup_as_failure(self):
        with patch("server.management.commands.cleanup_article_views.cleanup_views",
                   return_value={"deleted": 1000, "remaining": True}):
            with self.assertRaises(CommandError):
                call_command("cleanup_article_views", stdout=StringIO())

    def test_refresh_failure_preserves_snapshot_and_backs_off(self):
        self.article(1)
        first = self.snapshot()
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=5)), \
                patch("server.trends.calculate_snapshot", side_effect=DatabaseError("timeout")):
            cached = trend_payload(user=None)
        self.assertEqual(cached["calculated_at"], first["calculated_at"])
        self.assertTrue(cached["is_stale"])
        self.assertEqual(TrendState.objects.get().next_refresh_at, NOW + timedelta(minutes=6))
        with patch("server.trends.timezone.now", return_value=NOW + timedelta(minutes=5, seconds=1)), \
                patch("server.trends.calculate_snapshot", side_effect=AssertionError("backoff ignored")):
            self.assertEqual(self.client.get("/api/v1/trends").status_code, 200)

    def test_unavailable_and_empty_are_different(self):
        with patch("server.trends.timezone.now", return_value=NOW), \
                patch("server.trends.calculate_snapshot", side_effect=DatabaseError("timeout")):
            response = self.client.get("/api/v1/trends")
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response["Retry-After"], "60")
        TrendState.objects.update(next_refresh_at=NOW)
        with patch("server.trends.timezone.now", return_value=NOW):
            response = self.client.get("/api/v1/trends")
        self.assertEqual(response.json()["items"], [])
        self.assertEqual(response["Cache-Control"], "private, no-store")
        self.assertIn("Authorization", response["Vary"])

    def test_hidden_snapshot_entry_is_replaced_without_false_movement(self):
        articles = [self.article(i) for i in range(1, 7)]
        for article in articles[:5]:
            self.view(article)
        self.snapshot()
        Article.objects.filter(pk=articles[0].pk).update(visibility="HIDDEN")
        result = self.snapshot()["items"]
        self.assertEqual(len(result), 5)
        self.assertEqual(result[-1]["kind"], "LATEST")
        self.assertEqual(result[-1]["story"]["representative_article"]["id"], str(articles[5].pk))
        self.assertTrue(all(item["movement"] is None for item in result))

    def test_cleanup_is_batched_keeps_boundary_and_recent_and_requires_cron_secret(self):
        article = self.article(1)
        self.view(article, "expired", NOW - timedelta(hours=4))
        self.view(article, "boundary", NOW - timedelta(hours=3))
        self.view(article, "recent", NOW)
        with patch("server.trends.timezone.now", return_value=NOW):
            result = cleanup_views(batch_size=1)
        self.assertEqual(result["deleted"], 1)
        self.assertFalse(result["remaining"])
        self.assertEqual(ArticleView.objects.count(), 2)
        with patch.dict("os.environ", {"CRON_SECRET": "test-secret"}):
            self.assertEqual(self.client.get("/api/internal/trends/cleanup").status_code, 403)
            response = self.client.get("/api/internal/trends/cleanup", HTTP_AUTHORIZATION="Bearer test-secret")
            self.assertEqual(response.status_code, 200)

    def test_cleanup_cap_is_observable_and_repeatable(self):
        self.view(self.article(1), at=NOW - timedelta(hours=4))
        with patch("server.trends.timezone.now", return_value=NOW):
            self.assertTrue(cleanup_views(budget_seconds=0)["remaining"])
            self.assertFalse(cleanup_views()["remaining"])
            self.assertEqual(cleanup_views()["deleted"], 0)
