from datetime import timedelta
from io import StringIO
import json
from unittest.mock import patch
from uuid import uuid4

from django.contrib.auth.hashers import is_password_usable
from django.core.management import call_command
from django.core.management.base import CommandError
from django.db import IntegrityError
from django.test import TestCase
from django.utils import timezone

from server.demo_activity import demo_accounts, demo_viewer_key, seed_demo_activity
from server.models import (Article, ArticleReaction, ArticleView, AuthSession, Publisher,
                           ReactionKind, SavedArticle, StoryCluster, TrendState, UserAccount)
from server.reactions import highlights_payload
from server.trends import latest_keys, trend_payload


class DemoActivityTests(TestCase):
    def setUp(self):
        self.now = timezone.now()
        self.publisher = Publisher.objects.create(code="news", name_ko="뉴스", domain="example.com")
        inactive = Publisher.objects.create(code="inactive", name_ko="중지", domain="stopped.example.com", is_active=False)
        self.ineligible = []
        self.feed = []
        for index in range(12):
            cluster = StoryCluster.objects.create(internal_label=f"주제 {index}", category="SOCIETY")
            for kind in ["feed", "support", "hidden", "inactive"]:
                article = Article.objects.create(publisher=inactive if kind == "inactive" else self.publisher,
                    canonical_url=f"https://example.com/{index}/{kind}", title=f"기사 {index} {kind}", body_text="본문",
                    content_hash="0" * 64, category="SOCIETY", story_cluster=cluster,
                    published_at=self.now - timedelta(minutes=index + 1),
                    is_feed_visible=kind == "feed", visibility="HIDDEN" if kind == "hidden" else "VISIBLE")
                if kind == "feed": self.feed.append(article)
                elif kind != "support": self.ineligible.append(article.pk)
        self.regular = UserAccount.objects.create(username="ordinary", password_hash="untouched")
        self.regular_reaction = ArticleReaction.objects.create(user=self.regular, article=self.feed[0], kind="LIKE", reacted_at=self.now)
        self.regular_view = ArticleView.objects.create(article=self.feed[0], viewer_key="ordinary-key", last_viewed_at=self.now)
        SavedArticle.objects.create(user=self.regular, article=self.feed[0])
        self.ids = [row.pk for row in demo_accounts()]

    def seed(self, **kwargs):
        with patch("server.demo_activity.timezone.now", return_value=self.now):
            return seed_demo_activity(**kwargs)

    def activity(self):
        return (
            list(ArticleView.objects.filter(viewer_key__startswith="demo:").order_by("viewer_key", "article_id")
                 .values_list("viewer_key", "article_id", "last_viewed_at")),
            list(ArticleReaction.objects.filter(user_id__in=self.ids).order_by("user_id", "article_id")
                 .values_list("user_id", "article_id", "kind", "reacted_at")),
        )

    def test_preview_is_read_only_and_apply_preserves_ordinary_data(self):
        before = list(Article.objects.order_by("id").values_list("id", "title", "published_at", "content_hash"))
        output = StringIO()
        call_command("seed_demo_activity", stdout=output)
        self.assertFalse(json.loads(output.getvalue())["applied"])
        self.assertEqual(UserAccount.objects.count(), 1)
        self.assertEqual(ArticleReaction.objects.count(), 1)
        self.assertEqual(ArticleView.objects.count(), 1)
        report = self.seed(apply=True)
        self.assertEqual(report["accounts"], 20)
        self.assertGreater(report["views"], 0)
        self.assertEqual(UserAccount.objects.count(), 21)
        self.assertFalse(any(is_password_usable(a.password_hash) or a.is_developer for a in UserAccount.objects.filter(pk__in=self.ids)))
        self.assertEqual(AuthSession.objects.count(), 0)
        self.assertTrue(ArticleReaction.objects.filter(pk=self.regular_reaction.pk, user=self.regular, kind="LIKE").exists())
        self.assertTrue(ArticleView.objects.filter(pk=self.regular_view.pk, viewer_key="ordinary-key").exists())
        self.assertEqual(SavedArticle.objects.count(), 1)
        self.assertEqual(before, list(Article.objects.order_by("id").values_list("id", "title", "published_at", "content_hash")))

    def test_distribution_uses_existing_topic_oracles_and_invalidation(self):
        original = {"calculated_at": self.now.isoformat(), "items": []}
        TrendState.objects.filter(pk=1).update(current_snapshot=original, next_refresh_at=self.now + timedelta(minutes=5))
        keys = latest_keys(60, set())
        self.seed(apply=True)
        state = TrendState.objects.get(pk=1)
        self.assertEqual(state.current_snapshot, original)
        self.assertEqual(state.next_refresh_at, self.now)
        self.assertEqual(set(ArticleReaction.objects.filter(user_id__in=self.ids).values_list("kind", flat=True)), set(ReactionKind.values))
        self.assertFalse(ArticleReaction.objects.filter(article_id__in=self.ineligible).exists())
        self.assertFalse(ArticleView.objects.filter(article_id__in=self.ineligible).exists())
        first = ArticleView.objects.filter(article__story_cluster=self.feed[0].story_cluster, viewer_key__startswith="demo:")
        self.assertEqual(first.values("viewer_key").distinct().count(), 20)
        self.assertGreater(first.count(), 20)
        self.assertFalse(ArticleReaction.objects.filter(article__story_cluster=self.feed[6].story_cluster).exists())
        self.assertTrue(ArticleView.objects.filter(last_viewed_at__lt=self.now - timedelta(hours=3)).exists())
        self.assertTrue(ArticleReaction.objects.filter(reacted_at__lt=self.now - timedelta(hours=24)).exists())
        with patch("server.trends.timezone.now", return_value=self.now):
            ranks = trend_payload(user=None)
        with patch("server.reactions.timezone.now", return_value=self.now):
            issue = highlights_payload(user=None)["issue"]
        self.assertEqual(ranks["items"][0]["story"]["story_key"], keys[0])
        self.assertEqual(issue["story"]["story_key"], keys[3])
        self.assertEqual(issue["reaction_user_count"], 18)
        self.assertEqual(TrendState.objects.get(pk=1).previous_snapshot, original)

    def test_repeat_and_clear_touch_only_exact_owned_accounts_and_viewers(self):
        self.seed(apply=True)
        first = self.activity()
        self.seed(apply=True)
        self.assertEqual(first, self.activity())
        self.assertEqual(UserAccount.objects.count(), 21)
        self.seed(apply=True, clear=True)
        self.seed(apply=True, clear=True)
        self.assertEqual(list(UserAccount.objects.values_list("id", flat=True)), [self.regular.pk])
        self.assertEqual(list(ArticleReaction.objects.values_list("pk", flat=True)), [self.regular_reaction.pk])
        self.assertEqual(list(ArticleView.objects.values_list("pk", flat=True)), [self.regular_view.pk])
        self.assertEqual(len(demo_viewer_key(self.ids[0])), 64)
        self.assertTrue(demo_viewer_key(self.ids[0]).startswith("demo:"))

    def test_reserved_identity_collisions_abort_without_modifying_data(self):
        wanted = demo_accounts()[0]
        variants = [
            dict(id=uuid4(), username=wanted.username.upper(), email="other@example.com", password_hash="other"),
            dict(id=wanted.pk, username="someone-else", email=wanted.email, password_hash=wanted.password_hash),
            dict(id=wanted.pk, username=wanted.username, email=wanted.email, password_hash="changed"),
            dict(id=wanted.pk, username=wanted.username, email=wanted.email, password_hash=wanted.password_hash, is_developer=True),
        ]
        for values in variants:
            with self.subTest(values={k: v for k, v in values.items() if k != "password_hash"}):
                collision = UserAccount.objects.create(**values)
                for clear in [False, True]:
                    with self.assertRaises(CommandError): self.seed(apply=True, clear=clear)
                self.assertEqual(UserAccount.objects.count(), 2)
                self.assertEqual(ArticleReaction.objects.count(), 1)
                self.assertEqual(ArticleView.objects.count(), 1)
                collision.delete()

    def test_empty_apply_fails_but_clear_remains_available(self):
        self.seed(apply=True)
        Article.objects.all().delete()
        with self.assertRaises(CommandError): self.seed(apply=True)
        self.assertEqual(UserAccount.objects.count(), 21)
        self.seed(apply=True, clear=True)
        self.assertEqual(UserAccount.objects.count(), 1)

    def test_failed_write_rolls_back_accounts_and_activity(self):
        with patch("server.demo_activity.ArticleReaction.objects.bulk_create", side_effect=IntegrityError("fixture")):
            with self.assertRaises(CommandError): self.seed(apply=True)
        self.assertEqual(UserAccount.objects.count(), 1)
        self.assertEqual(ArticleReaction.objects.count(), 1)
        self.assertEqual(ArticleView.objects.count(), 1)
