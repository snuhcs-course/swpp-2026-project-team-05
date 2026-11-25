from datetime import timedelta
import json
from unittest.mock import patch
from uuid import UUID, uuid4

from django.db import DatabaseError, IntegrityError, transaction
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.utils import timezone

from server.authentication import create_session
from server.models import Article, ArticleReaction, ArticleView, Publisher, ReactionKind, StoryCluster, UserAccount, UserPublisherFollow
from server.reactions import highlights_payload, is_breaking_title, set_reaction


class ReactionTests(TestCase):
    def setUp(self):
        self.now = timezone.now()
        self.publisher = Publisher.objects.create(code="news", name_ko="뉴스", domain="example.com")
        self.user = UserAccount.objects.create(username="first", password_hash="unused")
        self.other = UserAccount.objects.create(username="second", password_hash="unused")
        self.auth = {"HTTP_AUTHORIZATION": "Bearer " + create_session(self.user)[0]}
        self.other_auth = {"HTTP_AUTHORIZATION": "Bearer " + create_session(self.other)[0]}
        self.article = self.article_for()
        self.url = f"/api/v1/articles/{self.article.pk}/reactions"

    def article_for(self, **kwargs):
        defaults = dict(publisher=self.publisher, canonical_url=f"https://example.com/{uuid4()}",
                        title="기사", body_text="본문", content_hash="0" * 64,
                        category="SOCIETY", published_at=self.now - timedelta(days=10))
        return Article.objects.create(**(defaults | kwargs))

    def react(self, article=None, user=None, kind="LIKE", at=None):
        return ArticleReaction.objects.create(article=article or self.article, user=user or self.user,
                                              kind=kind, reacted_at=at or self.now)

    def put(self, kind, **auth):
        return self.client.put(self.url, json.dumps({"reaction": kind}), content_type="application/json", **(auth or self.auth))

    def test_current_counts_selection_and_idempotent_transitions(self):
        empty = self.client.get(self.url)
        self.assertEqual(empty.status_code, 200)
        self.assertEqual(empty.json()["counts"], dict.fromkeys(ReactionKind.values, 0))
        self.assertIsNone(empty.json()["my_reaction"])
        for kind in ReactionKind.values:
            with self.subTest(kind=kind), patch("server.reactions.timezone.now", return_value=self.now):
                response = self.put(kind)
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json()["my_reaction"], kind)
                self.assertEqual(sum(response.json()["counts"].values()), 1)
                row = ArticleReaction.objects.get()
                with patch("server.reactions.timezone.now", return_value=self.now + timedelta(hours=1)):
                    self.assertEqual(self.put(kind).json()["counts"][kind], 1)
                row.refresh_from_db()
                self.assertEqual(row.reacted_at, self.now)
        self.assertEqual(self.client.delete(self.url, **self.auth).json()["counts"]["SAD"], 0)
        self.assertEqual(self.client.delete(self.url, **self.auth).status_code, 200)
        self.assertFalse(ArticleReaction.objects.exists())

    def test_type_change_refreshes_time_but_get_does_not(self):
        self.react(at=self.now - timedelta(days=2))
        with patch("server.reactions.timezone.now", return_value=self.now):
            self.put("ANGRY")
        self.assertEqual(ArticleReaction.objects.get().reacted_at, self.now)
        self.client.get(self.url)
        self.assertEqual(ArticleReaction.objects.get().reacted_at, self.now)
        self.assertEqual(ArticleView.objects.count(), 0)

    def test_auth_ownership_headers_and_invalid_inputs(self):
        self.assertEqual(self.client.put(self.url, '{"reaction":"LIKE"}', content_type="application/json").status_code, 401)
        self.assertEqual(self.client.delete(self.url).status_code, 401)
        self.assertEqual(self.client.get(self.url, HTTP_AUTHORIZATION="Bearer invalid").status_code, 401)
        for value in [None, "like", "OTHER", 1, [], {}]:
            self.assertEqual(self.put(value).status_code, 400)
        for body in ["[]", "{}", '{"reaction":"LIKE","user_id":"other"}', "bad"]:
            self.assertEqual(self.client.put(self.url, body, content_type="application/json", **self.auth).status_code, 400)
        self.assertEqual(self.client.put(self.url, "reaction=LIKE", content_type="text/plain", **self.auth).status_code, 415)
        self.put("LIKE")
        self.put("SAD", **self.other_auth)
        result = self.client.get(self.url, **self.auth)
        self.assertEqual(result.json()["my_reaction"], "LIKE")
        self.assertEqual(result.json()["counts"]["SAD"], 1)
        self.assertNotIn(str(self.other.pk), result.content.decode())
        self.assertIn("Authorization", result["Vary"])
        self.assertEqual(result["Cache-Control"], "private, no-store")
        self.client.delete(self.url, **self.auth)
        self.assertEqual(self.client.get(self.url, **self.other_auth).json()["my_reaction"], "SAD")

    def test_support_hidden_inactive_missing_and_cascades(self):
        self.article.is_feed_visible = False
        self.article.save()
        self.assertEqual(self.put("MOVED").status_code, 200)
        self.article.visibility = "HIDDEN"
        self.article.save()
        for method in [lambda: self.client.get(self.url), lambda: self.put("LIKE"),
                       lambda: self.client.delete(self.url, **self.auth)]:
            self.assertEqual(method().status_code, 404)
        self.article.visibility = "VISIBLE"
        self.article.save()
        self.publisher.is_active = False
        self.publisher.save()
        self.assertEqual(self.put("LIKE").status_code, 404)
        self.publisher.is_active = True
        self.publisher.save()
        self.user.delete()
        self.assertFalse(ArticleReaction.objects.exists())
        self.react(user=self.other)
        self.article.delete()
        self.assertFalse(ArticleReaction.objects.exists())
        self.assertEqual(self.client.get(self.url).status_code, 404)

    def test_database_constraints_and_safe_failures(self):
        self.react()
        with self.assertRaises(IntegrityError), transaction.atomic():
            self.react()
        with self.assertRaises(IntegrityError), transaction.atomic():
            self.react(kind="INVALID", user=self.other)
        with patch("server.api_v1.set_reaction", side_effect=DatabaseError("private detail")):
            response = self.put("LIKE")
        self.assertEqual(response.status_code, 503)
        self.assertNotIn("private detail", response.content.decode())

    def test_issue_distinct_users_support_and_equal_types(self):
        cluster = StoryCluster.objects.create(internal_label="주제", category="SOCIETY")
        self.article.story_cluster = cluster
        self.article.save()
        support = self.article_for(story_cluster=cluster, is_feed_visible=False)
        self.react()
        self.react(article=support, kind="ANGRY")
        self.react(article=support, user=self.other, kind="SAD")
        with patch("server.reactions.timezone.now", return_value=self.now):
            payload = highlights_payload(user=None)
        self.assertIsNone(payload["breaking"])
        self.assertEqual(payload["issue"]["reaction_user_count"], 2)
        self.assertEqual(payload["issue"]["story"]["story_key"], f"cluster:{cluster.pk}")
        self.assertEqual(payload["issue"]["story"]["representative_article"]["id"], str(self.article.pk))

    def test_issue_window_eligibility_ties_and_zero(self):
        self.react(at=self.now - timedelta(hours=24))
        recent = self.article_for(published_at=self.now - timedelta(days=1))
        self.react(article=recent)
        for kwargs in [{"visibility": "HIDDEN"}, {"is_feed_visible": False}]:
            invalid = self.article_for(**kwargs)
            self.react(article=invalid)
            self.react(article=invalid, user=self.other)
        with patch("server.reactions.timezone.now", return_value=self.now):
            issue = highlights_payload(user=None)["issue"]
        self.assertEqual(issue["story"]["story_key"], f"article:{recent.pk}")
        self.assertEqual(issue["reaction_user_count"], 1)
        ArticleReaction.objects.filter(article=recent).delete()
        with patch("server.reactions.timezone.now", return_value=self.now):
            self.assertEqual(highlights_payload(user=None)["issue"]["story"]["story_key"], f"article:{self.article.pk}")
        with patch("server.reactions.timezone.now", return_value=self.now + timedelta(microseconds=1)):
            self.assertIsNone(highlights_payload(user=None)["issue"])
        ArticleReaction.objects.update(reacted_at=self.now + timedelta(hours=1))
        with patch("server.reactions.timezone.now", return_value=self.now):
            self.assertIsNone(highlights_payload(user=None)["issue"])

    def test_issue_time_changes_and_deletion_follow_current_state(self):
        old = self.now - timedelta(days=2)
        self.react(at=old)
        with patch("server.reactions.timezone.now", return_value=self.now):
            self.put("LIKE")
            self.assertIsNone(highlights_payload(user=None)["issue"])
            self.put("ANGRY")
            self.assertEqual(highlights_payload(user=None)["issue"]["reaction_user_count"], 1)
            self.client.delete(self.url, **self.auth)
            self.assertIsNone(highlights_payload(user=None)["issue"])
            self.put("SAD")
            self.assertEqual(ArticleReaction.objects.get().reacted_at, self.now)
            self.assertEqual(highlights_payload(user=None)["issue"]["reaction_user_count"], 1)

    def test_issue_stable_ties_and_personalization_do_not_change_the_topic_score(self):
        self.article.delete()
        cluster = StoryCluster.objects.create(id=UUID(int=2), internal_label="주제", category="SOCIETY")
        first = self.article_for(story_cluster=cluster)
        publisher = Publisher.objects.create(code="other", name_ko="다른 뉴스", domain="other.example.com")
        second = self.article_for(story_cluster=cluster, publisher=publisher)
        singleton = self.article_for(id=UUID(int=1))
        self.react(article=first)
        self.react(article=singleton)
        UserPublisherFollow.objects.create(user=self.user, publisher=self.publisher)
        UserPublisherFollow.objects.create(user=self.other, publisher=publisher)
        with patch("server.reactions.timezone.now", return_value=self.now):
            # Equal participants and publication times use the stable article:/cluster: key.
            self.assertEqual(highlights_payload(user=None)["issue"]["story"]["story_key"], f"article:{singleton.pk}")
            singleton.delete()
            for user, expected in [(self.user, first), (self.other, second)]:
                issue = highlights_payload(user=user)["issue"]
                self.assertEqual(issue["story"]["story_key"], f"cluster:{cluster.pk}")
                self.assertEqual(issue["reaction_user_count"], 1)
                self.assertEqual(issue["story"]["representative_article"]["id"], str(expected.pk))

    def test_support_only_and_inactive_topics_cannot_displace_a_discoverable_topic(self):
        self.react()
        cluster = StoryCluster.objects.create(internal_label="비공개 주제", category="SOCIETY")
        inactive = Publisher.objects.create(code="inactive", name_ko="중지", domain="inactive.example.com", is_active=False)
        for article in [self.article_for(story_cluster=cluster, is_feed_visible=False),
                        self.article_for(publisher=inactive), self.article_for(visibility="HIDDEN")]:
            self.react(article=article)
            self.react(article=article, user=self.other)
        with patch("server.reactions.timezone.now", return_value=self.now):
            self.assertEqual(highlights_payload(user=None)["issue"]["story"]["story_key"], f"article:{self.article.pk}")
            self.article.delete()
            self.assertIsNone(highlights_payload(user=None)["issue"])

    def test_breaking_keyword_boundaries_overlap_and_exact_article(self):
        for title in ["[속보] 제목", " ( 속보 ) 제목", "【속보】제목", "[정치] [속보] 제목",
                      "속보 - 제목", "오늘 속보 전해드립니다", "속보성 기사", "[속보) 제목"]:
            self.assertTrue(is_breaking_title(title), title)
        for title in ["일반 기사", "본문에만 있음", "속 보", ""]:
            self.assertFalse(is_breaking_title(title), title)
        cluster = StoryCluster.objects.create(internal_label="속보 주제", category="SOCIETY")
        breaking = self.article_for(title="[정치][속보] 새 소식", published_at=self.now, story_cluster=cluster)
        self.react(article=breaking)
        self.react(article=breaking, user=self.other)
        self.react()
        self.article_for(title="[속보] 만료", published_at=self.now - timedelta(hours=2))
        future = self.article_for(title="[속보] 미래", published_at=self.now + timedelta(microseconds=1))
        self.article_for(title="[속보] 비공개", published_at=self.now, is_feed_visible=False)
        with patch("server.reactions.timezone.now", return_value=self.now):
            payload = highlights_payload(user=self.user)
        self.assertEqual(payload["breaking"]["article"]["id"], str(breaking.pk))
        self.assertEqual(payload["breaking"]["expires_at"], self.now + timedelta(hours=2))
        self.assertEqual(payload["issue"]["story"]["story_key"], f"article:{self.article.pk}")
        future.delete()
        with patch("server.reactions.timezone.now", return_value=self.now + timedelta(hours=2)):
            self.assertIsNone(highlights_payload(user=None)["breaking"])

    def test_breaking_just_inside_window_sql_keywords_and_stable_tied_ids(self):
        inside = self.article_for(id=UUID(int=1), title="[속보] 경계", published_at=self.now - timedelta(hours=2) + timedelta(microseconds=1))
        self.article_for(title="일반 기사", body_text="본문에만 속보가 있습니다", published_at=self.now)
        self.article_for(title="[속보] 만료", published_at=self.now - timedelta(hours=2))
        self.article_for(title="[속보] 숨김", published_at=self.now, visibility="HIDDEN")
        inactive = Publisher.objects.create(code="stopped", name_ko="중지", domain="stopped.example.com", is_active=False)
        self.article_for(title="[속보] 중지", published_at=self.now, publisher=inactive)
        with patch("server.reactions.timezone.now", return_value=self.now):
            self.assertEqual(highlights_payload(user=None)["breaking"]["article"]["id"], str(inside.pk))
            inside.published_at = self.now
            for title in ["[속보] 제목", " ( 속보 ) 제목", "【속보】제목", "[정치] [속보] 제목",
                          "속보 - 제목", "오늘 속보 전해드립니다", "속보성 기사", "[속보) 제목"]:
                inside.title = title
                inside.save()
                with self.subTest(title=title):
                    self.assertEqual(highlights_payload(user=None)["breaking"]["article"]["id"], str(inside.pk))
            tied = self.article_for(id=UUID(int=2), title="[속보] 같은 시각", published_at=self.now)
            self.assertEqual(highlights_payload(user=None)["breaking"]["article"]["id"], str(inside.pk))
            tied.delete()
            for title in ["일반 기사", "속 보", ""]:
                inside.title = title
                inside.save()
                with self.subTest(title=title):
                    self.assertIsNone(highlights_payload(user=None)["breaking"])

    def test_highlights_empty_errors_and_bounded_queries(self):
        response = self.client.get("/api/v1/home-highlights")
        self.assertEqual(response.status_code, 200)
        self.assertIsNone(response.json()["issue"])
        self.assertIsNone(response.json()["breaking"])
        self.assertEqual(self.client.get("/api/v1/home-highlights?category=SOCIETY").status_code, 400)
        with patch("server.api_v1.highlights_payload", side_effect=DatabaseError("secret")):
            self.assertEqual(self.client.get("/api/v1/home-highlights").status_code, 503)
        self.react()
        with CaptureQueriesContext(connection) as small:
            highlights_payload(user=None)
        for _ in range(10):
            self.react(article=self.article_for())
        # Corpus growth must not introduce per-topic or per-publisher queries.
        with self.assertNumQueries(len(small)):
            highlights_payload(user=None)
