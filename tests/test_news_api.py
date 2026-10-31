import json
from datetime import timedelta

from django.contrib.auth.hashers import make_password
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

    def _article(self, *, publisher, title, body, published_at, url, visible=True):
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
        for path in ("/api/v1/publishers", "/api/v1/articles",
                     f"/api/v1/articles/{self.source.id}",
                     f"/api/v1/articles/{self.source.id}/analysis"):
            with self.subTest(path=path):
                response = self.client.get(path)
                self.assertEqual(response.status_code, 401)
                self.assertEqual(response.json()["error"]["code"], "AUTH_REQUIRED")

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

    def test_ready_article_can_have_zero_findings(self):
        response = self.client.get(f"/api/v1/articles/{self.target.id}/analysis", **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {
            "state": "READY", "version": "reviewed-v1", "findings": [],
        })
