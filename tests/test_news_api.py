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
)


class NewsApiTests(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = UserAccount.objects.create(
            email="reader@example.com",
            username="reader",
            password_hash=make_password("safe-password-123"),
        )
        self.token, _ = create_session(self.user)
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
                 feed_visible=True):
        return Article.objects.create(
            publisher=publisher,
            story_cluster=self.cluster,
            canonical_url=url,
            title=title,
            body_text=body,
            content_hash="0" * 64,
            category=NewsCategory.POLITICS,
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

    def test_news_endpoints_require_authentication(self):
        for path in ("/api/v1/publishers", "/api/v1/articles", "/api/v1/saved-articles",
                     f"/api/v1/articles/{self.source.id}",
                     f"/api/v1/articles/{self.source.id}/analysis"):
            with self.subTest(path=path):
                response = self.client.get(path)
                self.assertEqual(response.status_code, 401)
                self.assertEqual(response.json()["error"]["code"], "AUTH_REQUIRED")

        save = self.client.put(f"/api/v1/saved-articles/{self.source.id}")
        self.assertEqual(save.status_code, 401)
        remove = self.client.delete(f"/api/v1/saved-articles/{self.source.id}")
        self.assertEqual(remove.status_code, 401)

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

    def test_publishers_are_ordered_and_active_only(self):
        Publisher.objects.create(
            code="hidden", name_ko="숨김", domain="hidden.example", is_active=False,
            display_order=0,
        )
        response = self.client.get("/api/v1/publishers", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual([row["code"] for row in response.json()["items"]], ["yna", "mbc"])

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
        rejected = self.client.put(
            f"/api/v1/saved-articles/{comparison_only.id}", **self.auth
        )
        self.assertEqual(rejected.status_code, 404)
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
        self.assertEqual(hidden.json()["items"], [])
        self.assertTrue(SavedArticle.objects.filter(id=row.id).exists())

        self.source.visibility = ArticleVisibility.VISIBLE
        self.source.save(update_fields=["visibility"])
        restored = self.client.get("/api/v1/saved-articles", **self.auth)
        self.assertEqual(restored.json()["items"][0]["id"], str(self.source.id))

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
        for query in ("한", "가" * 101):
            with self.subTest(length=len(query)):
                response = self.client.get("/api/v1/articles", {"q": query}, **self.auth)
                self.assertEqual(response.status_code, 400)
                self.assertIn("q", response.json()["error"]["fields"])

        feed = self.client.get("/api/v1/articles", **self.auth)
        self.assertNotIn("search_excerpt", feed.json()["items"][0])

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
        self.assertEqual(len(payload["findings"]), 1)
        result = payload["findings"][0]
        self.assertEqual(result["source"]["marker"]["start_in_sentence"],
                         finding.marker_start - finding.source_sentence_start)
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
        with self.assertNumQueries(4):
            self.client.get(f"/api/v1/articles/{self.source.id}/analysis", **self.auth)
        SavedArticle.objects.create(user=self.user, article=self.source)
        with self.assertNumQueries(2):
            self.client.get("/api/v1/saved-articles", **self.auth)

    def test_ready_article_can_have_zero_findings(self):
        response = self.client.get(f"/api/v1/articles/{self.target.id}/analysis", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {
            "state": "READY", "version": "reviewed-v1", "findings": [],
        })
