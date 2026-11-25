import json
from datetime import timedelta
from uuid import uuid4

from django.contrib.auth.hashers import make_password
from django.db import IntegrityError, transaction
from django.test import Client, TestCase
from django.utils import timezone

from server.authentication import create_session
from server.models import (
    AnalysisStatus,
    Article,
    ArticleVisibility,
    ExpressionFinding,
    ExpressionOwner,
    FindingComparison,
    NewsCategory,
    Publisher,
    SavedArticle,
    SharedProposition,
    StoryCluster,
    TextScope,
    UserAccount,
    UserPublisherFollow,
)


class NewsApiTests(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = UserAccount.objects.create(
            email="reader@example.com",
            username="reader",
            password_hash=make_password("safe-password-123"),
        )
        self.token, self.session = create_session(self.user)
        self.auth = {"HTTP_AUTHORIZATION": f"Bearer {self.token}"}
        self.yna = Publisher.objects.create(
            code="yna", name_ko="연합뉴스", domain="yna.co.kr", display_order=1,
        )
        self.mbc = Publisher.objects.create(
            code="mbc", name_ko="MBC", domain="imnews.imbc.com", display_order=2,
        )
        self.cluster = StoryCluster.objects.create(
            internal_label="정책 추진",
            category=NewsCategory.POLITICS,
            analysis_status=AnalysisStatus.READY,
            analysis_version="reviewed-v1",
        )
        now = timezone.now()
        self.source = self._article(
            publisher=self.yna,
            title="정부, 정책 전격 추진",
            body="정부는 정책을 전격 추진한다고 밝혔다. 후속 협의가 이어진다.",
            published_at=now,
            url="https://example.com/source",
        )
        self.target = self._article(
            publisher=self.mbc,
            title="정부 정책 추진 계획",
            body="정부는 정책을 빠르게 추진할 계획이라고 설명했다.",
            published_at=now - timedelta(minutes=3),
            url="https://example.com/target",
        )

    def _article(self, *, publisher, title, body, published_at, url, visible=True,
                 feed_visible=True, category=NewsCategory.POLITICS):
        return Article.objects.create(
            publisher=publisher,
            story_cluster=self.cluster,
            canonical_url=url,
            title=title,
            body_text=body,
            content_hash="0" * 64,
            category=category,
            published_at=published_at,
            visibility=ArticleVisibility.VISIBLE if visible else ArticleVisibility.HIDDEN,
            is_feed_visible=feed_visible,
        )

    def _finding(self):
        sentence = "정부는 정책을 전격 추진한다고 밝혔다."
        marker = "전격"
        source_start = self.source.body_text.index(sentence)
        marker_start = self.source.body_text.index(marker)
        proposition = SharedProposition.objects.create(
            story_cluster=self.cluster,
            normalized_proposition="정부가 정책을 추진한다",
            comparison_overview="연합뉴스는 속도를 강조하고 MBC는 계획을 중립적으로 전한다.",
        )
        return ExpressionFinding.objects.create(
            article=self.source,
            source_scope=TextScope.BODY,
            source_sentence_start=source_start,
            source_sentence_end=source_start + len(sentence),
            source_sentence_text=sentence,
            marker_start=marker_start,
            marker_end=marker_start + len(marker),
            marker_text=marker,
            categories=["C", "D"],
            explanation="결정의 속도와 강도를 강조한다.",
            shared_proposition=proposition,
        )

    def _comparison(self, finding, target, *, sentence=None, marker="빠르게", rank=0):
        sentence = sentence or target.body_text
        sentence_start = target.body_text.index(sentence)
        marker_start = target.body_text.index(marker, sentence_start)
        return FindingComparison.objects.create(
            expression_finding=finding,
            target_article=target,
            target_scope=TextScope.BODY,
            target_sentence_start=sentence_start,
            target_sentence_end=sentence_start + len(sentence),
            target_sentence_text=sentence,
            target_marker_start=marker_start,
            target_marker_end=marker_start + len(marker),
            target_marker_text=marker,
            target_owner=ExpressionOwner.REPORTER,
            comparison_summary=f"비교 설명 {rank}",
            display_rank=rank,
        )

    def test_public_news_reads_allow_guests_and_disable_personalization(self):
        for path in (
            "/api/v1/publishers",
            "/api/v1/articles",
            f"/api/v1/articles/{self.source.id}",
            f"/api/v1/articles/{self.source.id}/analysis",
        ):
            with self.subTest(path=path):
                response = self.client.get(path)
                self.assertEqual(response.status_code, 200)
                self.assertIn("Authorization", response.headers["Vary"])

        self.assertFalse(self.client.get("/api/v1/publishers").json()["items"][0]["is_followed"])
        self.assertTrue(all(
            not item["is_saved"]
            for item in self.client.get("/api/v1/articles").json()["items"]
        ))
        authenticated = self.client.get("/api/v1/articles", **self.auth)
        self.assertEqual(authenticated.status_code, 200)
        self.assertIn("Authorization", authenticated.headers["Vary"])

    def test_personalized_news_operations_still_require_authentication(self):
        for path in (
            "/api/v1/saved-articles",
            "/api/v1/articles?scope=FOLLOWING",
        ):
            with self.subTest(path=path):
                response = self.client.get(path)
                self.assertEqual(response.status_code, 401)
                self.assertEqual(response.json()["error"]["code"], "AUTH_REQUIRED")

        save = self.client.put(f"/api/v1/saved-articles/{self.source.id}")
        self.assertEqual(save.status_code, 401)
        follow = self.client.put(
            "/api/v1/me/followed-publishers",
            data=json.dumps({"publisher_codes": ["yna"]}),
            content_type="application/json",
        )
        self.assertEqual(follow.status_code, 401)
        remove = self.client.delete(f"/api/v1/saved-articles/{self.source.id}")
        self.assertEqual(remove.status_code, 401)

    def test_story_feed_groups_coverage_and_is_stable(self):
        first = self.client.get("/api/v1/stories")
        second = self.client.get("/api/v1/stories")
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.json(), second.json())
        self.assertEqual(len(first.json()["items"]), 1)
        story = first.json()["items"][0]
        self.assertEqual(story["story_key"], f"cluster:{self.cluster.id}")
        self.assertEqual(story["publisher_count"], 2)
        self.assertEqual(
            {publisher["code"] for publisher in story["publishers"]},
            {"yna", "mbc"},
        )

    def test_story_feed_prefers_a_followed_publisher(self):
        UserPublisherFollow.objects.create(user=self.user, publisher=self.mbc)
        response = self.client.get("/api/v1/stories", **self.auth)
        self.assertEqual(response.status_code, 200)
        representative = response.json()["items"][0]["representative_article"]
        self.assertEqual(representative["publisher"]["code"], "mbc")

    def test_following_story_feed_requires_auth_and_filters_topics(self):
        self.assertEqual(
            self.client.get("/api/v1/stories?scope=FOLLOWING").status_code,
            401,
        )
        UserPublisherFollow.objects.create(user=self.user, publisher=self.mbc)
        response = self.client.get("/api/v1/stories?scope=FOLLOWING", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.json()["items"]), 1)
        self.assertEqual(
            response.json()["items"][0]["representative_article"]["publisher"]["code"],
            "mbc",
        )

    def test_story_feed_cursor_is_stable_and_context_bound(self):
        second_cluster = StoryCluster.objects.create(
            internal_label="후속 정책",
            category=NewsCategory.POLITICS,
            analysis_status=AnalysisStatus.READY,
        )
        Article.objects.create(
            publisher=self.yna,
            story_cluster=second_cluster,
            canonical_url="https://example.com/second-cluster",
            title="후속 정책 기사",
            body_text="후속 정책 본문",
            content_hash="1" * 64,
            category=NewsCategory.POLITICS,
            published_at=timezone.now() - timedelta(days=1),
        )
        first = self.client.get("/api/v1/stories?category=POLITICS&limit=1")
        self.assertEqual(first.status_code, 200)
        cursor = first.json()["next_cursor"]
        self.assertIsNotNone(cursor)
        second = self.client.get(
            "/api/v1/stories",
            {"category": "POLITICS", "limit": 1, "cursor": cursor},
        )
        self.assertEqual(second.status_code, 200)
        self.assertNotEqual(
            first.json()["items"][0]["story_key"],
            second.json()["items"][0]["story_key"],
        )
        invalid = self.client.get(
            "/api/v1/stories",
            {"category": "SOCIETY", "limit": 1, "cursor": cursor},
        )
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(invalid.json()["error"]["code"], "INVALID_CURSOR")

    def test_story_feed_keeps_unclustered_articles(self):
        article = Article.objects.create(
            publisher=self.yna,
            story_cluster=None,
            canonical_url="https://example.com/unclustered",
            title="아직 묶이지 않은 기사",
            body_text="기사 본문",
            content_hash="2" * 64,
            category=NewsCategory.SOCIETY,
            published_at=timezone.now() + timedelta(minutes=1),
        )
        response = self.client.get("/api/v1/stories?category=SOCIETY")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["items"][0]["story_key"], f"article:{article.id}")
        self.assertEqual(response.json()["items"][0]["publisher_count"], 1)

    def test_invalid_supplied_token_is_not_downgraded_to_guest(self):
        for authorization in ("Bearer invalid-token", "Basic malformed"):
            headers = {"HTTP_AUTHORIZATION": authorization}
            for path in (
                "/api/v1/publishers",
                "/api/v1/articles",
                f"/api/v1/articles/{self.source.id}",
                f"/api/v1/articles/{self.source.id}/analysis",
            ):
                with self.subTest(path=path, authorization=authorization):
                    response = self.client.get(path, **headers)
                    self.assertEqual(response.status_code, 401)
                    self.assertEqual(response.json()["error"]["code"], "AUTH_REQUIRED")

        self.session.expires_at = timezone.now() - timedelta(seconds=1)
        self.session.save(update_fields=["expires_at"])
        expired = self.client.get("/api/v1/articles", **self.auth)
        self.assertEqual(expired.status_code, 401)
        self.assertEqual(expired.json()["error"]["code"], "AUTH_REQUIRED")

    def test_register_normalizes_fields_and_returns_session(self):
        response = self.client.post(
            "/api/v1/auth/register",
            data=json.dumps({
                "email": "  New.User@Example.COM ",
                "username": "  새사용자_1 ",
                "password": "not-common-password-456",
                "password_confirmation": "not-common-password-456",
            }),
            content_type="application/json",
        )
        self.assertEqual(response.status_code, 201)
        payload = response.json()
        self.assertTrue(payload["token"])
        self.assertEqual(payload["user"]["email"], "new.user@example.com")
        self.assertEqual(payload["user"]["username"], "새사용자_1")
        self.assertFalse(payload["user"]["is_developer"])

    def test_register_rejects_case_insensitive_duplicate(self):
        response = self.client.post(
            "/api/v1/auth/register",
            data=json.dumps({
                "email": "READER@example.com",
                "username": "new_reader",
                "password": "not-common-password-456",
                "password_confirmation": "not-common-password-456",
            }),
            content_type="application/json",
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("email", response.json()["error"]["fields"])

    def test_login_accepts_email_and_logout_is_idempotent(self):
        response = self.client.post(
            "/api/v1/auth/login",
            data=json.dumps({"identifier": "READER@EXAMPLE.COM", "password": "safe-password-123"}),
            content_type="application/json",
        )
        self.assertEqual(response.status_code, 200)
        token = response.json()["token"]
        headers = {"HTTP_AUTHORIZATION": f"Bearer {token}"}
        self.assertEqual(self.client.post("/api/v1/auth/logout", **headers).status_code, 204)
        self.assertEqual(self.client.post("/api/v1/auth/logout", **headers).status_code, 204)
        self.assertEqual(self.client.get("/api/v1/auth/me", **headers).status_code, 401)

    def test_invalid_password_is_rejected_and_expired_session_cannot_read(self):
        self.assertNotEqual(self.user.password_hash, "safe-password-123")
        invalid = self.client.post(
            "/api/v1/auth/login",
            data=json.dumps({"identifier": "reader", "password": "wrong-password"}),
            content_type="application/json",
        )
        self.assertEqual(invalid.status_code, 401)
        self.assertEqual(invalid.json()["error"]["code"], "INVALID_CREDENTIALS")

        from server.models import AuthSession
        AuthSession.objects.filter(token_hash__isnull=False).update(
            expires_at=timezone.now() - timedelta(seconds=1)
        )
        self.assertEqual(self.client.get("/api/v1/auth/me", **self.auth).status_code, 401)

    def test_publishers_are_ordered_active_and_follow_aware(self):
        Publisher.objects.create(
            code="hidden", name_ko="숨김", domain="hidden.example", is_active=False,
            display_order=0,
        )
        Publisher.objects.create(
            code="no-feed", name_ko="기사 없음", domain="no-feed.example",
            display_order=0,
        )
        UserPublisherFollow.objects.create(user=self.user, publisher=self.mbc)
        response = self.client.get("/api/v1/publishers", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual([row["code"] for row in response.json()["items"]], ["yna", "mbc"])
        self.assertEqual(
            [row["is_followed"] for row in response.json()["items"]],
            [False, True],
        )

    def test_follow_relationship_is_unique_and_cascades_with_either_parent(self):
        follow = UserPublisherFollow.objects.create(user=self.user, publisher=self.yna)
        with self.assertRaises(IntegrityError), transaction.atomic():
            UserPublisherFollow.objects.create(user=self.user, publisher=self.yna)
        self.user.delete()
        self.assertFalse(UserPublisherFollow.objects.filter(pk=follow.pk).exists())

        other = UserAccount.objects.create(
            email="cascade@example.com", username="cascade_user", password_hash="unused",
        )
        publisher = Publisher.objects.create(
            code="cascade", name_ko="삭제 언론사", domain="cascade.example",
        )
        follow = UserPublisherFollow.objects.create(user=other, publisher=publisher)
        publisher.delete()
        self.assertFalse(UserPublisherFollow.objects.filter(pk=follow.pk).exists())

    def test_followed_publishers_replace_atomically_and_are_idempotent(self):
        path = "/api/v1/me/followed-publishers"
        first = self.client.put(
            path,
            data=json.dumps({"publisher_codes": ["mbc", "yna", "mbc"]}),
            content_type="application/json",
            **self.auth,
        )
        second = self.client.put(
            path,
            data=json.dumps({"publisher_codes": ["yna", "mbc"]}),
            content_type="application/json",
            **self.auth,
        )
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.json(), {"publisher_codes": ["yna", "mbc"]})
        self.assertEqual(second.json(), first.json())
        self.assertEqual(UserPublisherFollow.objects.filter(user=self.user).count(), 2)

        cleared = self.client.put(
            path,
            data=json.dumps({"publisher_codes": []}),
            content_type="application/json",
            **self.auth,
        )
        self.assertEqual(cleared.json(), {"publisher_codes": []})
        self.assertFalse(UserPublisherFollow.objects.filter(user=self.user).exists())

    def test_followed_publishers_reject_invalid_payload_without_partial_update(self):
        UserPublisherFollow.objects.create(user=self.user, publisher=self.yna)
        inactive = Publisher.objects.create(
            code="inactive-follow", name_ko="비활성", domain="inactive-follow.example",
            is_active=False,
        )
        self._article(
            publisher=inactive,
            title="비활성 기사",
            body="본문",
            published_at=timezone.now(),
            url="https://example.com/inactive-follow",
        )
        path = "/api/v1/me/followed-publishers"
        for payload in (
            {},
            {"publisher_codes": "yna"},
            {"publisher_codes": ["yna", "unknown"]},
            {"publisher_codes": ["inactive-follow"]},
        ):
            with self.subTest(payload=payload):
                response = self.client.put(
                    path,
                    data=json.dumps(payload),
                    content_type="application/json",
                    **self.auth,
                )
                self.assertEqual(response.status_code, 400)
                self.assertEqual(response.json()["error"]["code"], "VALIDATION_ERROR")
                self.assertEqual(
                    list(UserPublisherFollow.objects.filter(user=self.user)
                         .values_list("publisher__code", flat=True)),
                    ["yna"],
                )

    def test_following_scope_is_user_specific_and_latest_stays_complete(self):
        UserPublisherFollow.objects.create(user=self.user, publisher=self.yna)
        default_latest = self.client.get("/api/v1/articles", **self.auth)
        latest = self.client.get("/api/v1/articles", {"scope": "ALL"}, **self.auth)
        following = self.client.get(
            "/api/v1/articles", {"scope": "FOLLOWING"}, **self.auth
        )
        self.assertEqual(
            {row["id"] for row in latest.json()["items"]},
            {str(self.source.id), str(self.target.id)},
        )
        self.assertEqual(default_latest.json()["items"], latest.json()["items"])
        self.assertEqual(
            [row["id"] for row in following.json()["items"]],
            [str(self.source.id)],
        )

        other = UserAccount.objects.create(
            email="following-other@example.com",
            username="following_other",
            password_hash=make_password("safe-password-456"),
        )
        other_token, _ = create_session(other)
        other_following = self.client.get(
            "/api/v1/articles",
            {"scope": "FOLLOWING"},
            HTTP_AUTHORIZATION=f"Bearer {other_token}",
        )
        self.assertEqual(other_following.json()["items"], [])
        self.assertIsNone(other_following.json()["next_cursor"])

    def test_following_composes_with_category_and_legacy_publisher_filter(self):
        UserPublisherFollow.objects.bulk_create([
            UserPublisherFollow(user=self.user, publisher=self.yna),
            UserPublisherFollow(user=self.user, publisher=self.mbc),
        ])
        response = self.client.get(
            "/api/v1/articles",
            {"scope": "FOLLOWING", "category": "POLITICS", "publisher": "mbc"},
            **self.auth,
        )
        self.assertEqual(
            [row["id"] for row in response.json()["items"]],
            [str(self.target.id)],
        )

    def test_follow_changes_neither_reader_nor_stored_analysis(self):
        finding = self._finding()
        self._comparison(finding, self.target)
        before_detail = self.client.get(
            f"/api/v1/articles/{self.source.id}", **self.auth
        ).json()
        before_analysis = self.client.get(
            f"/api/v1/articles/{self.source.id}/analysis", **self.auth
        ).json()

        response = self.client.put(
            "/api/v1/me/followed-publishers",
            data=json.dumps({"publisher_codes": ["yna"]}),
            content_type="application/json",
            **self.auth,
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            self.client.get(f"/api/v1/articles/{self.source.id}", **self.auth).json(),
            before_detail,
        )
        self.assertEqual(
            self.client.get(
                f"/api/v1/articles/{self.source.id}/analysis", **self.auth
            ).json(),
            before_analysis,
        )
        self.assertEqual(
            before_analysis["findings"][0]["comparison_articles"][0]["publisher"]["code"],
            "mbc",
        )

    def test_following_cursor_is_invalid_after_follow_set_changes(self):
        older = self._article(
            publisher=self.yna,
            title="이전 연합뉴스 기사",
            body="이전 본문",
            published_at=timezone.now() - timedelta(hours=1),
            url="https://example.com/older-yna",
        )
        UserPublisherFollow.objects.create(user=self.user, publisher=self.yna)
        first = self.client.get(
            "/api/v1/articles",
            {"scope": "FOLLOWING", "limit": 1},
            **self.auth,
        )
        self.assertEqual(first.status_code, 200)
        self.assertTrue(first.json()["next_cursor"])
        self.assertIn(str(older.id), {
            row["id"] for row in self.client.get(
                "/api/v1/articles", {"scope": "FOLLOWING"}, **self.auth
            ).json()["items"]
        })
        self.client.put(
            "/api/v1/me/followed-publishers",
            data=json.dumps({"publisher_codes": ["mbc"]}),
            content_type="application/json",
            **self.auth,
        )
        stale = self.client.get(
            "/api/v1/articles",
            {
                "scope": "FOLLOWING",
                "limit": 1,
                "cursor": first.json()["next_cursor"],
            },
            **self.auth,
        )
        self.assertEqual(stale.status_code, 400)
        self.assertEqual(stale.json()["error"]["code"], "INVALID_CURSOR")

    def test_following_rejects_invalid_scope(self):
        response = self.client.get("/api/v1/articles", {"scope": "MINE"}, **self.auth)
        self.assertEqual(response.status_code, 400)
        self.assertIn("scope", response.json()["error"]["fields"])

    def test_feed_filters_and_uses_stable_cursor(self):
        first = self.client.get("/api/v1/articles?category=POLITICS&limit=1", **self.auth)
        self.assertEqual(first.status_code, 200)
        payload = first.json()
        self.assertEqual([row["id"] for row in payload["items"]], [str(self.source.id)])
        self.assertTrue(payload["next_cursor"])
        second = self.client.get(
            "/api/v1/articles",
            {"category": "POLITICS", "limit": 1, "cursor": payload["next_cursor"]},
            **self.auth,
        )
        self.assertEqual([row["id"] for row in second.json()["items"]], [str(self.target.id)])
        filtered = self.client.get("/api/v1/articles?publisher=mbc", **self.auth)
        self.assertEqual([row["id"] for row in filtered.json()["items"]], [str(self.target.id)])

    def test_save_article_is_idempotent_and_updates_article_payloads(self):
        path = f"/api/v1/saved-articles/{self.source.id}"
        first = self.client.put(path, **self.auth)
        second = self.client.put(path, **self.auth)

        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(first.json(), second.json())
        self.assertEqual(SavedArticle.objects.count(), 1)

        feed = self.client.get("/api/v1/articles", **self.auth).json()["items"]
        source = next(row for row in feed if row["id"] == str(self.source.id))
        self.assertTrue(source["is_saved"])
        detail = self.client.get(f"/api/v1/articles/{self.source.id}", **self.auth)
        self.assertTrue(detail.json()["is_saved"])

    def test_saved_article_unique_constraint(self):
        SavedArticle.objects.create(user=self.user, article=self.source)
        with self.assertRaises(IntegrityError):
            with transaction.atomic():
                SavedArticle.objects.create(user=self.user, article=self.source)

    def test_remove_saved_article_is_idempotent(self):
        SavedArticle.objects.create(user=self.user, article=self.source)
        path = f"/api/v1/saved-articles/{self.source.id}"

        self.assertEqual(self.client.delete(path, **self.auth).status_code, 204)
        self.assertEqual(self.client.delete(path, **self.auth).status_code, 204)
        self.assertFalse(SavedArticle.objects.exists())

    def test_saved_articles_are_isolated_per_user(self):
        SavedArticle.objects.create(user=self.user, article=self.source)
        other = UserAccount.objects.create(
            email="other@example.com",
            username="other",
            password_hash=make_password("safe-password-456"),
        )
        other_token, _ = create_session(other)
        other_auth = {"HTTP_AUTHORIZATION": f"Bearer {other_token}"}

        mine = self.client.get("/api/v1/saved-articles", **self.auth)
        theirs = self.client.get("/api/v1/saved-articles", **other_auth)
        self.assertEqual([row["id"] for row in mine.json()["items"]], [str(self.source.id)])
        self.assertEqual(theirs.json()["items"], [])
        other_feed = self.client.get("/api/v1/articles", **other_auth).json()["items"]
        self.assertTrue(all(not row["is_saved"] for row in other_feed))

    def test_saved_articles_cursor_is_stable_and_bound(self):
        older = SavedArticle.objects.create(user=self.user, article=self.source)
        newer = SavedArticle.objects.create(user=self.user, article=self.target)
        SavedArticle.objects.filter(id=older.id).update(
            saved_at=timezone.now() - timedelta(days=1)
        )

        first = self.client.get("/api/v1/saved-articles", {"limit": 1}, **self.auth)
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.json()["items"][0]["id"], str(newer.article_id))
        self.assertTrue(first.json()["items"][0]["is_saved"])
        self.assertTrue(first.json()["items"][0]["saved_at"])
        cursor = first.json()["next_cursor"]
        second = self.client.get(
            "/api/v1/saved-articles", {"limit": 1, "cursor": cursor}, **self.auth
        )
        self.assertEqual([row["id"] for row in second.json()["items"]], [str(older.article_id)])

        invalid = self.client.get(
            "/api/v1/saved-articles", {"cursor": "not-a-cursor"}, **self.auth
        )
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(invalid.json()["error"]["code"], "INVALID_CURSOR")

    def test_save_eligibility_and_soft_hidden_lifecycle(self):
        comparison_only = self.target
        comparison_only.is_feed_visible = False
        comparison_only.save(update_fields=["is_feed_visible"])
        saved_comparison = self.client.put(
            f"/api/v1/saved-articles/{comparison_only.id}", **self.auth
        )
        self.assertEqual(saved_comparison.status_code, 200)
        self.assertTrue(saved_comparison.json()["is_saved"])
        self.assertEqual(
            [row["id"] for row in self.client.get(
                "/api/v1/saved-articles", **self.auth
            ).json()["items"]],
            [str(comparison_only.id)],
        )
        self.assertNotIn(
            str(comparison_only.id),
            [row["id"] for row in self.client.get(
                "/api/v1/articles", **self.auth
            ).json()["items"]],
        )
        hidden_article = self._article(
            publisher=self.yna,
            title="숨긴 기사",
            body="숨긴 본문",
            published_at=timezone.now(),
            url="https://example.com/save-hidden",
            visible=False,
        )
        inactive_publisher = Publisher.objects.create(
            code="inactive", name_ko="비활성", domain="inactive.example", is_active=False,
        )
        inactive_article = self._article(
            publisher=inactive_publisher,
            title="비활성 언론사 기사",
            body="본문",
            published_at=timezone.now(),
            url="https://example.com/save-inactive",
        )
        for article_id in (hidden_article.id, inactive_article.id, uuid4()):
            with self.subTest(article_id=article_id):
                response = self.client.put(
                    f"/api/v1/saved-articles/{article_id}", **self.auth
                )
                self.assertEqual(response.status_code, 404)

        row = SavedArticle.objects.create(user=self.user, article=self.source)
        self.source.visibility = ArticleVisibility.HIDDEN
        self.source.save(update_fields=["visibility"])
        hidden = self.client.get("/api/v1/saved-articles", **self.auth)
        self.assertEqual(
            [item["id"] for item in hidden.json()["items"]],
            [str(comparison_only.id)],
        )
        self.assertTrue(SavedArticle.objects.filter(id=row.id).exists())

        self.source.visibility = ArticleVisibility.VISIBLE
        self.source.save(update_fields=["visibility"])
        restored = self.client.get("/api/v1/saved-articles", **self.auth)
        self.assertEqual(
            {item["id"] for item in restored.json()["items"]},
            {str(self.source.id), str(comparison_only.id)},
        )

        self.source.visibility = ArticleVisibility.HIDDEN
        self.source.save(update_fields=["visibility"])
        self.assertEqual(
            self.client.delete(
                f"/api/v1/saved-articles/{self.source.id}", **self.auth
            ).status_code,
            204,
        )
        self.assertFalse(SavedArticle.objects.filter(id=row.id).exists())

    def test_saved_articles_validate_limit_and_cursor(self):
        for limit in ("zero", "0", "51"):
            with self.subTest(limit=limit):
                response = self.client.get(
                    "/api/v1/saved-articles", {"limit": limit}, **self.auth
                )
                self.assertEqual(response.status_code, 400)
                self.assertEqual(response.json()["error"]["code"], "VALIDATION_ERROR")
        invalid = self.client.get(
            "/api/v1/saved-articles", {"cursor": "invalid"}, **self.auth
        )
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(invalid.json()["error"]["code"], "INVALID_CURSOR")

    def test_saved_article_relationships_cascade_with_parent(self):
        row = SavedArticle.objects.create(user=self.user, article=self.source)
        self.source.delete()
        self.assertFalse(SavedArticle.objects.filter(id=row.id).exists())

        row = SavedArticle.objects.create(user=self.user, article=self.target)
        self.user.delete()
        self.assertFalse(SavedArticle.objects.filter(id=row.id).exists())

    def test_search_matches_title_body_and_publisher(self):
        title_match = self.client.get("/api/v1/articles", {"q": "전격"}, **self.auth)
        self.assertEqual(
            [row["id"] for row in title_match.json()["items"]],
            [str(self.source.id)],
        )
        self.assertIn("전격", title_match.json()["items"][0]["search_excerpt"])

        body_match = self.client.get(
            "/api/v1/articles", {"q": "  후속   협의  "}, **self.auth
        )
        self.assertEqual(
            [row["id"] for row in body_match.json()["items"]],
            [str(self.source.id)],
        )
        self.assertIn("후속 협의", body_match.json()["items"][0]["search_excerpt"])

        publisher_match = self.client.get("/api/v1/articles", {"q": "mbc"}, **self.auth)
        self.assertEqual(
            [row["id"] for row in publisher_match.json()["items"]],
            [str(self.target.id)],
        )
        self.assertIsNone(publisher_match.json()["items"][0]["search_excerpt"])

    def test_search_ranks_title_then_publisher_then_body(self):
        now = timezone.now()
        ranked_publisher = Publisher.objects.create(
            code="ranked", name_ko="특별검색뉴스", domain="ranked.example.com",
            display_order=3,
        )
        title_match = self._article(
            publisher=self.yna,
            title="특별검색 제목",
            body="제목에서만 일치합니다.",
            published_at=now - timedelta(days=3),
            url="https://example.com/search-title",
        )
        publisher_match = self._article(
            publisher=ranked_publisher,
            title="언론사 일치 기사",
            body="언론사 이름으로 찾습니다.",
            published_at=now - timedelta(days=2),
            url="https://example.com/search-publisher",
        )
        body_match = self._article(
            publisher=self.mbc,
            title="본문 일치 기사",
            body="가장 최근이지만 특별검색 단어는 본문에만 있습니다.",
            published_at=now,
            url="https://example.com/search-body",
        )

        response = self.client.get("/api/v1/articles", {"q": "특별검색"}, **self.auth)
        self.assertEqual(
            [row["id"] for row in response.json()["items"]],
            [str(title_match.id), str(publisher_match.id), str(body_match.id)],
        )

    def test_search_combines_filters_and_excludes_non_feed_articles(self):
        self.target.is_feed_visible = False
        self.target.save(update_fields=["is_feed_visible"])

        hidden_from_search = self.client.get(
            "/api/v1/articles",
            {"q": "정부", "category": "POLITICS", "publisher": "mbc"},
            **self.auth,
        )
        self.assertEqual(hidden_from_search.json()["items"], [])

        visible = self.client.get(
            "/api/v1/articles",
            {"q": "정부", "category": "POLITICS", "publisher": "yna"},
            **self.auth,
        )
        self.assertEqual(
            [row["id"] for row in visible.json()["items"]],
            [str(self.source.id)],
        )

    def test_search_total_count_matches_each_visible_category_before_pagination(self):
        now = timezone.now()
        society = self._article(
            publisher=self.mbc,
            title="정부 사회 정책 점검",
            body="사회 분야 정부 정책을 점검한다.",
            published_at=now - timedelta(minutes=5),
            url="https://example.com/society-search",
            category=NewsCategory.SOCIETY,
        )
        self._article(
            publisher=self.yna,
            title="정부 숨김 기사",
            body="검색에서 제외되어야 한다.",
            published_at=now,
            url="https://example.com/hidden-search",
            visible=False,
        )
        self._article(
            publisher=self.yna,
            title="정부 비교 전용 기사",
            body="검색에서 제외되어야 한다.",
            published_at=now,
            url="https://example.com/comparison-search",
            feed_visible=False,
        )

        politics = self.client.get(
            "/api/v1/articles",
            {"q": "정부", "category": "POLITICS", "limit": 1},
            **self.auth,
        )
        society_response = self.client.get(
            "/api/v1/articles",
            {"q": "정부", "category": "SOCIETY", "limit": 1},
            **self.auth,
        )

        self.assertEqual(politics.status_code, 200)
        self.assertEqual(politics.json()["total_count"], 2)
        self.assertEqual(len(politics.json()["items"]), 1)
        self.assertTrue(politics.json()["next_cursor"])
        second_page = self.client.get(
            "/api/v1/articles",
            {
                "q": "정부",
                "category": "POLITICS",
                "limit": 1,
                "cursor": politics.json()["next_cursor"],
            },
            **self.auth,
        )
        self.assertEqual(second_page.json()["total_count"], 2)
        self.assertEqual(society_response.json()["total_count"], 1)
        self.assertEqual(
            [row["id"] for row in society_response.json()["items"]],
            [str(society.id)],
        )

    def test_search_cursor_is_bound_to_query_and_paginates_ranked_results(self):
        first = self.client.get(
            "/api/v1/articles", {"q": "정부", "limit": 1}, **self.auth
        )
        cursor = first.json()["next_cursor"]
        self.assertTrue(cursor)
        second = self.client.get(
            "/api/v1/articles", {"q": "정부", "limit": 1, "cursor": cursor}, **self.auth
        )
        self.assertEqual(second.status_code, 200)
        self.assertNotEqual(
            first.json()["items"][0]["id"], second.json()["items"][0]["id"]
        )

        wrong_query = self.client.get(
            "/api/v1/articles", {"q": "정책", "limit": 1, "cursor": cursor}, **self.auth
        )
        self.assertEqual(wrong_query.status_code, 400)
        self.assertEqual(wrong_query.json()["error"]["code"], "INVALID_CURSOR")

    def test_search_validates_query_length_and_feed_shape_is_unchanged(self):
        single = self.client.get("/api/v1/articles", {"q": " 정 "})
        self.assertEqual(single.status_code, 200)
        self.assertEqual(single.json()["total_count"], 2)
        self.assertEqual(
            {row["id"] for row in single.json()["items"]},
            {str(self.source.id), str(self.target.id)},
        )
        boundary = self.client.get("/api/v1/articles", {"q": "가" * 100}, **self.auth)
        self.assertEqual(boundary.status_code, 200)
        self.assertEqual(boundary.json()["total_count"], 0)
        response = self.client.get("/api/v1/articles", {"q": "가" * 101}, **self.auth)
        self.assertEqual(response.status_code, 400)
        self.assertIn("q", response.json()["error"]["fields"])

        blank = self.client.get("/api/v1/articles", {"q": "  "}, **self.auth)
        self.assertEqual(blank.status_code, 200)
        self.assertNotIn("total_count", blank.json())

        feed = self.client.get("/api/v1/articles", **self.auth)
        self.assertNotIn("search_excerpt", feed.json()["items"][0])
        self.assertNotIn("total_count", feed.json())

    def test_comparison_article_is_readable_but_excluded_from_feed(self):
        self.target.is_feed_visible = False
        self.target.save(update_fields=["is_feed_visible"])

        feed = self.client.get("/api/v1/articles?category=POLITICS", **self.auth)
        self.assertEqual(
            [row["id"] for row in feed.json()["items"]],
            [str(self.source.id)],
        )
        detail = self.client.get(f"/api/v1/articles/{self.target.id}", **self.auth)
        self.assertEqual(detail.status_code, 200)

        finding = self._finding()
        self._comparison(finding, self.target)
        analysis = self.client.get(
            f"/api/v1/articles/{self.source.id}/analysis", **self.auth
        )
        self.assertEqual(
            analysis.json()["findings"][0]["comparison_articles"][0]["article_id"],
            str(self.target.id),
        )

    def test_publishers_exclude_comparison_only_sources(self):
        self.target.is_feed_visible = False
        self.target.save(update_fields=["is_feed_visible"])

        response = self.client.get("/api/v1/publishers", **self.auth)
        self.assertEqual(
            [row["code"] for row in response.json()["items"]],
            ["yna"],
        )

    def test_feed_rejects_tampered_cursor(self):
        response = self.client.get("/api/v1/articles?cursor=not-a-cursor", **self.auth)
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "INVALID_CURSOR")

    def test_article_detail_returns_exact_body_and_hides_hidden_article(self):
        response = self.client.get(f"/api/v1/articles/{self.source.id}", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["body"], self.source.body_text)
        self.assertEqual(response.json()["content_hash"], self.source.content_hash)
        hidden = self._article(
            publisher=self.yna,
            title="숨긴 기사",
            body="외부에 보이지 않는다.",
            published_at=timezone.now(),
            url="https://example.com/hidden",
            visible=False,
        )
        self.assertEqual(self.client.get(f"/api/v1/articles/{hidden.id}", **self.auth).status_code, 404)

    def test_analysis_projects_source_and_grouped_comparison(self):
        finding = self._finding()
        self._comparison(finding, self.target)

        response = self.client.get(f"/api/v1/articles/{self.source.id}/analysis", **self.auth)
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["state"], "READY")
        self.assertEqual(payload["source_content_hash"], self.source.content_hash)
        self.assertEqual(len(payload["findings"]), 1)
        result = payload["findings"][0]
        self.assertEqual(result["source"]["marker"]["start_in_sentence"],
                         finding.marker_start - finding.source_sentence_start)
        self.assertEqual(
            result["comparison_overview"],
            "연합뉴스는 속도를 강조하고 MBC는 계획을 중립적으로 전한다.",
        )
        self.assertEqual(len(result["comparison_articles"]), 1)
        self.assertEqual(result["comparison_articles"][0]["body"], self.target.body_text)
        self.assertEqual(result["comparison_articles"][0]["evidence"][0]["marker"]["text"],
                         "빠르게")

    def test_source_only_finding_has_no_comparison_pages(self):
        self._finding()
        response = self.client.get(f"/api/v1/articles/{self.source.id}/analysis", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["findings"][0]["comparison_articles"], [])

    def test_comparison_pages_are_publisher_diverse_grouped_and_capped_at_three(self):
        finding = self._finding()
        first_sentence = "정부는 정책을 빠르게 추진할 계획이라고 설명했다."
        self.target.body_text = first_sentence + " 정책을 빠르게 집행한다."
        self.target.save(update_fields=["body_text"])
        self._comparison(finding, self.target, sentence=first_sentence, rank=0)
        self._comparison(finding, self.target, sentence="정책을 빠르게 집행한다.", rank=1)

        for index in range(3):
            publisher = Publisher.objects.create(
                code=f"p{index}", name_ko=f"언론사{index}",
                domain=f"p{index}.example.com", display_order=10 + index,
            )
            target = self._article(
                publisher=publisher,
                title=f"비교 기사 {index}",
                body="정부는 정책을 빠르게 추진한다.",
                published_at=timezone.now() - timedelta(minutes=10 + index),
                url=f"https://example.com/target-{index}",
            )
            self._comparison(finding, target, rank=2 + index)

        response = self.client.get(f"/api/v1/articles/{self.source.id}/analysis", **self.auth)
        pages = response.json()["findings"][0]["comparison_articles"]
        self.assertEqual(len(pages), 3)
        self.assertEqual(len({page["publisher"]["code"] for page in pages}), 3)
        self.assertEqual(len(pages[0]["evidence"]), 2)

    def test_hidden_or_inactive_comparison_target_is_not_exposed(self):
        finding = self._finding()
        self._comparison(finding, self.target)
        self.target.visibility = ArticleVisibility.HIDDEN
        self.target.save(update_fields=["visibility"])

        response = self.client.get(f"/api/v1/articles/{self.source.id}/analysis", **self.auth)
        self.assertEqual(response.json()["findings"][0]["comparison_articles"], [])

    def test_malformed_stored_offsets_fail_closed(self):
        finding = self._finding()
        finding.marker_text = "다른 문구"
        finding.save(update_fields=["marker_text"])
        response = self.client.get(f"/api/v1/articles/{self.source.id}/analysis", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["findings"], [])

    def test_feed_and_analysis_queries_are_bounded(self):
        finding = self._finding()
        self._comparison(finding, self.target)
        with self.assertNumQueries(2):
            self.client.get("/api/v1/articles?category=POLITICS", **self.auth)
        UserPublisherFollow.objects.create(user=self.user, publisher=self.yna)
        with self.assertNumQueries(3):
            self.client.get(
                "/api/v1/articles?category=POLITICS&scope=FOLLOWING", **self.auth
            )
        # Auth + publisher directory + one bounded window query for three previews per publisher.
        with self.assertNumQueries(3):
            self.client.get("/api/v1/publishers", **self.auth)
        with self.assertNumQueries(4):
            self.client.get(f"/api/v1/articles/{self.source.id}/analysis", **self.auth)
        SavedArticle.objects.create(user=self.user, article=self.source)
        with self.assertNumQueries(2):
            self.client.get("/api/v1/saved-articles", **self.auth)

    def test_ready_article_can_have_zero_findings(self):
        response = self.client.get(f"/api/v1/articles/{self.target.id}/analysis", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {
            "state": "READY",
            "version": "reviewed-v1",
            "source_content_hash": self.target.content_hash,
            "findings": [],
            "economic": None,
            "frame": None,
        })



class EconomicLensApiTests(TestCase):
    """specs/testing/economic-lens.spec.md: the economy feed and the economic lens of the reader analysis."""

    def setUp(self):
        self.client = Client()
        user = UserAccount.objects.create(username="econ-reader", password_hash=make_password("safe-password-123"))
        token, _ = create_session(user)
        self.auth = {"HTTP_AUTHORIZATION": f"Bearer {token}"}
        self.yna = Publisher.objects.create(code="yna", name_ko="연합뉴스", domain="yna.co.kr", display_order=1)
        self.hk = Publisher.objects.create(code="hankyung", name_ko="한국경제", domain="hankyung.com", display_order=2)
        self.newsis = Publisher.objects.create(code="newsis", name_ko="뉴시스", domain="newsis.com", display_order=3)
        self.cluster = StoryCluster.objects.create(
            internal_label="기준금리 인하", category=NewsCategory.ECONOMY,
            analysis_status=AnalysisStatus.READY, analysis_version="staged-v6",
        )
        now = timezone.now()
        self.worried = self._article(self.yna, "한은 기준금리 인하", "금리를 내렸다. 집값과 가계부채만 자극할 것이라는 우려도 있다.",
                                     now, "https://example.com/econ-yna")
        self.limited = self._article(self.hk, "집값 또 오르나", "이번 인하가 집값엔 큰 영향을 주지 않을 것이란 평가다.",
                                     now - timedelta(hours=1), "https://example.com/econ-hk")
        self.plain = self._article(self.newsis, "환율 마감", "환율이 1375.9원에 마감했다.",
                                   now - timedelta(hours=2), "https://example.com/econ-newsis")
        self.politics = Article.objects.create(
            publisher=self.yna, canonical_url="https://example.com/politics", title="정치 기사", body_text="정치 본문.",
            content_hash="0" * 64, category=NewsCategory.POLITICS, published_at=now,
        )
        self._lens(self.worried, "집값과 가계부채만 자극할 것이라는 우려도 있다.", ["housing", "household_credit"], "CONCERN",
                   "집값과 가계부채만 자극할 것이라는 우려")
        self._lens(self.limited, self.limited.body_text, ["housing"], "LIMITED", "집값엔 큰 영향을 주지 않을 것")

    def _article(self, publisher, title, body, published_at, url):
        return Article.objects.create(
            publisher=publisher, story_cluster=self.cluster, canonical_url=url, title=title, body_text=body,
            content_hash="0" * 64, category=NewsCategory.ECONOMY, published_at=published_at,
        )

    @staticmethod
    def _lens(article, sentence, channels, stance, phrase):
        start = article.body_text.index(sentence)
        article.economic_context = {"event": "한은, 기준금리 2.50%로 0.25%p 인하", "views": [{
            "channels": channels, "stance": stance, "direction": None, "phrase": phrase,
            "sentence": {"scope": "BODY", "start": start, "end": start + len(sentence), "text": sentence},
        }]}
        article.save()

    def _economic(self, article):
        return self.client.get(f"/api/v1/articles/{article.id}/analysis", **self.auth).json()["economic"]

    def test_economy_feed_lists_only_economy_articles(self):
        response = self.client.get("/api/v1/articles?category=ECONOMY", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual([item["id"] for item in response.json()["items"]],
                         [str(self.worried.id), str(self.limited.id), str(self.plain.id)])
        self.assertEqual({item["category"] for item in response.json()["items"]}, {"ECONOMY"})
        politics = self.client.get("/api/v1/articles?category=POLITICS", **self.auth).json()["items"]
        self.assertEqual([item["id"] for item in politics], [str(self.politics.id)])

    def test_unknown_category_names_all_three_fields(self):
        response = self.client.get("/api/v1/articles?category=CULTURE", **self.auth)
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["fields"]["category"], "정치, 사회 또는 경제를 선택해 주세요.")

    def test_analysis_carries_the_event_the_article_view_and_the_other_outlets_views(self):
        economic = self._economic(self.worried)
        self.assertEqual(economic["event"], "한은, 기준금리 2.50%로 0.25%p 인하")
        self.assertEqual([(v["channels"], v["stance"], v["phrase"]) for v in economic["views"]],
                         [(["housing", "household_credit"], "CONCERN", "집값과 가계부채만 자극할 것이라는 우려")])
        self.assertEqual([(o["article_id"], o["publisher"]["name"], [(v["stance"], v["phrase"]) for v in o["views"]])
                          for o in economic["others"]],
                         [(str(self.limited.id), "한국경제", [("LIMITED", "집값엔 큰 영향을 주지 않을 것")])])
        self.assertNotIn("contrasts", economic)

    def test_hidden_outlet_and_stale_view_are_left_out(self):
        self.limited.visibility = ArticleVisibility.HIDDEN
        self.limited.save()
        self.worried.economic_context["views"][0]["sentence"]["text"] = "본문과 다른 문장"
        self.worried.save()
        economic = self._economic(self.worried)
        self.assertEqual((economic["views"], economic["others"]), ([], []))

    def test_article_without_economic_lens_answers_null(self):
        self.assertIsNone(self._economic(self.plain))

    def test_position_is_served_for_the_article_and_an_outlet_with_no_views(self):
        self.worried.economic_context["position"] = {"stance": "CONCERN", "summary": "집값 자극을 우려함"}
        self.worried.save()
        self.plain.economic_context = {"event": "한은, 기준금리 2.50%로 0.25%p 인하", "views": [],
                                       "position": {"stance": "NEUTRAL", "summary": "환율 마감을 전함"}}
        self.plain.save()
        economic = self._economic(self.worried)
        self.assertEqual(economic["position"], {"stance": "CONCERN", "summary": "집값 자극을 우려함"})
        self.assertEqual([(o["publisher"]["name"], o["position"]) for o in economic["others"]],
                         [("한국경제", None), ("뉴시스", {"stance": "NEUTRAL", "summary": "환율 마감을 전함"})])


class EventIssueViewApiTests(TestCase):
    """specs/testing/event-issue-view.spec.md: the frame box and the issue view of the reader analysis."""

    def setUp(self):
        self.client = Client()
        user = UserAccount.objects.create(username="issue-reader", password_hash=make_password("safe-password-123"))
        token, _ = create_session(user)
        self.auth = {"HTTP_AUTHORIZATION": f"Bearer {token}"}
        self.cluster = StoryCluster.objects.create(
            internal_label="법안 통과", category=NewsCategory.POLITICS,
            analysis_status=AnalysisStatus.READY, analysis_version="event-issues-v1",
        )
        now = timezone.now()
        pubs = [Publisher.objects.create(code=c, name_ko=n, domain=f"{c}.example.com", display_order=k)
                for k, (c, n) in enumerate([("a", "가신문"), ("b", "나일보"), ("c", "다방송")], 1)]
        bodies = ["국회가 법안을 강행 처리했다. 야당은 반발했다.", "국회가 법안을 통과시켰다. 여당은 개혁이라고 밝혔다.",
                  "국회가 법안을 처리했다. 표결은 오후에 열렸다."]
        self.a, self.b, self.c = [
            Article.objects.create(publisher=p, story_cluster=self.cluster, canonical_url=f"https://{p.code}.example.com/1",
                                   title=f"{p.name_ko} 기사", body_text=body, content_hash="0" * 64,
                                   category=NewsCategory.POLITICS, published_at=now + timedelta(minutes=k))
            for k, (p, body) in enumerate(zip(pubs, bodies))]
        for article, frame in ((self.a, "정치"), (self.b, "정책 평가"), (self.c, "정치")):
            sentence = article.body_text.split(". ")[0] + "."  # the first sentence
            article.frame_context = {"frame": frame, "sentence": {"scope": "BODY", "start": 0, "end": len(sentence),
                                                                  "text": sentence}}
            article.save()
        self.a.frame_context["front"] = {"kind": "position", "label": "일방 처리"}
        self.b.frame_context["front"] = {"kind": "voices", "voices": [{"speaker": "여당", "quote": "개혁"}]}
        self.c.frame_context["front"] = {"kind": "fact", "phrase": "표결은 오후에 열렸다."}
        for article in (self.a, self.b, self.c):
            article.save()
        ids = {k: str(x.id) for k, x in (("a", self.a), ("b", self.b), ("c", self.c))}
        self.cluster.issue_table = {
            "event": "국회, 법안 통과",
            "issues": [
                {"key": "K2", "question": "표결 시점은 적절했나?", "kind": "common_fact", "positions": {},
                 "outlets": [{"article": ids["c"], "position": "F"}], "wordings": []},
                {"key": "K1", "question": "법안 처리 절차는 정당했나?", "kind": "contested",
                 "positions": {"P1": "일방 처리", "P2": "정상 처리"},
                 "outlets": [{"article": ids["a"], "position": "P1"},
                             {"article": ids["b"], "position": "P2", "voice": "여당"},
                             {"article": ids["c"], "position": "F"}],
                 "wordings": [{"left": {"article": ids["a"], "phrase": "강행 처리했다"},
                               "right": {"article": ids["b"], "phrase": "통과시켰다"}, "code": "C",
                               "explanation": "같은 행위를 서술하면서 가신문 쪽이 더 센 말을 골랐습니다."}]},
            ],
            "facts": [{"key": "F1", "label": "국회가 법안을 처리했다", "outlets": [ids["a"], ids["b"], ids["c"]],
                       "wordings": []}],
        }
        self.cluster.save()

    def _frame(self, article):
        return self.client.get(f"/api/v1/articles/{article.id}/analysis", **self.auth).json()["frame"]

    def test_each_article_sees_the_same_table_from_its_own_side(self):
        a = self._frame(self.a)
        self.assertEqual((a["event"], a["frame"], a["sentence"]["text"]), ("국회, 법안 통과", "정치", "국회가 법안을 강행 처리했다."))
        self.assertEqual(a["front"], {"kind": "position", "label": "일방 처리"})
        first = a["issues"][0]
        self.assertEqual(first["question"], "법안 처리 절차는 정당했나?")
        self.assertEqual(first["own"], {"covered": True, "fact_only": False, "position": "일방 처리"})
        self.assertEqual([(g["label"], g["is_own"], [(o["publisher"]["name"], o["voice"]) for o in g["outlets"]])
                          for g in first["positions"]], [("일방 처리", True, []), ("정상 처리", False, [("나일보", "여당")])])
        self.assertEqual([o["publisher"]["name"] for o in first["fact_only_outlets"]], ["다방송"])
        self.assertEqual([(w["left"]["phrase"], w["right"]["phrase"]) for w in first["wordings"]], [("강행 처리했다", "통과시켰다")])
        self.assertEqual(a["issues"][1]["own"]["covered"], False)  # an issue it does not cover comes last
        self.assertEqual(a["facts"], [{"label": "국회가 법안을 처리했다", "outlets": 3, "wordings": []}])
        self.assertEqual([(o["publisher"]["name"], o["frame"], o["positions"]) for o in a["others"]],
                         [("나일보", "정책 평가", [{"issue": "법안 처리 절차는 정당했나?", "position": "정상 처리"}]),
                          ("다방송", "정치", [])])

        c = self._frame(self.c)
        # both only as fact here: contested before common fact
        self.assertEqual([i["question"] for i in c["issues"]], ["법안 처리 절차는 정당했나?", "표결 시점은 적절했나?"])
        self.assertEqual(c["issues"][0]["own"], {"covered": True, "fact_only": True, "position": None})
        self.assertEqual([[o["publisher"]["name"] for o in g["outlets"]] for g in c["issues"][0]["positions"]],
                         [["가신문"], ["나일보"]])

    def test_hidden_outlet_and_stale_frame_are_left_out(self):
        self.b.visibility = ArticleVisibility.HIDDEN
        self.b.save()
        a = self._frame(self.a)
        contested = next(i for i in a["issues"] if i["kind"] == "contested")
        self.assertEqual([g["label"] for g in contested["positions"]], ["일방 처리"])
        self.assertEqual(contested["wordings"], [])
        self.assertEqual(a["facts"][0]["outlets"], 2)
        self.assertEqual([o["publisher"]["name"] for o in a["others"]], ["다방송"])
        self.c.frame_context["sentence"]["text"] = "본문과 다른 문장"
        self.c.save()
        stale = self._frame(self.c)
        self.assertIsNone(stale["sentence"])  # only the stale sentence is left out
        self.assertEqual(stale["front"], {"kind": "fact", "phrase": "표결은 오후에 열렸다."})
        self.assertEqual([o["sentence"] for o in self._frame(self.a)["others"]], [""])

    def test_article_without_frame_or_cluster_table_answers_null(self):
        self.a.frame_context = None
        self.a.save()
        self.assertIsNone(self._frame(self.a))
        self.cluster.issue_table = None
        self.cluster.save()
        self.assertIsNone(self._frame(self.b))
