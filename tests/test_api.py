"""API contract tests that do not call Gemini or Naver."""

import json
import os
from unittest.mock import patch

from django.test import Client, SimpleTestCase

from article_fetch import _AllowedRedirectHandler, validate_article_url


ARTICLE_URL = "https://n.news.naver.com/article/057/0001971678"


class AnalyzeApiTests(SimpleTestCase):
    def setUp(self):
        self.client = Client()

    def post(self, payload, *, token="test-token", content_type="application/json"):
        return self.client.post(
            "/api/analyze",
            data=json.dumps(payload),
            content_type=content_type,
            HTTP_AUTHORIZATION=f"Bearer {token}",
        )

    def test_health_does_not_need_credentials(self):
        response = self.client.get("/api/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok"})

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": "test-token"})
    @patch("server.views.analyze_related_articles")
    def test_analyze_returns_existing_pipeline_result(self, analyze):
        analyze.return_value = {
            "source_analysis": {"title": "기사 제목"},
            "related_articles": [],
            "candidates": [],
            "screened": [],
            "comparison": {"sentence_comparisons": []},
        }
        response = self.post({"url": ARTICLE_URL, "max_related": 2})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), analyze.return_value)
        analyze.assert_called_once_with(ARTICLE_URL, max_related=2)

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": "test-token"})
    @patch("server.views.analyze_related_articles")
    def test_wrong_token_cannot_call_pipeline(self, analyze):
        response = self.post({"url": ARTICLE_URL}, token="wrong")
        self.assertEqual(response.status_code, 401)
        analyze.assert_not_called()

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": "test-token"})
    @patch("server.views._analysis_slot.acquire", return_value=False)
    @patch("server.views.analyze_related_articles")
    def test_busy_server_returns_retryable_error(self, analyze, acquire):
        response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 429)
        self.assertEqual(response.headers["Retry-After"], "30")
        analyze.assert_not_called()

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": "test-token"})
    @patch("server.views.analyze_related_articles")
    def test_invalid_url_cannot_call_pipeline(self, analyze):
        response = self.post({"url": "http://127.0.0.1/admin"})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "invalid_url")
        analyze.assert_not_called()

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": "test-token"})
    def test_max_related_is_bounded(self):
        response = self.post({"url": ARTICLE_URL, "max_related": 10})
        self.assertEqual(response.status_code, 400)
        response = self.post({"url": ARTICLE_URL, "max_related": True})
        self.assertEqual(response.status_code, 400)

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": "test-token"})
    def test_large_request_is_rejected(self):
        response = self.post({"url": ARTICLE_URL, "extra": "x" * 9000})
        self.assertEqual(response.status_code, 413)

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": "test-token"})
    @patch("server.views.analyze_related_articles", side_effect=RuntimeError("private upstream detail"))
    def test_upstream_error_does_not_leak_details(self, analyze):
        with self.assertLogs("server.views", level="ERROR"):
            response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 502)
        self.assertNotIn("private upstream detail", response.content.decode())

    @patch.dict(os.environ, {"API_ACCESS_TOKEN": ""})
    def test_unconfigured_server_returns_503(self):
        with self.assertLogs("server.views", level="ERROR"):
            response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 503)


class ArticleUrlTests(SimpleTestCase):
    def test_known_news_host_is_allowed(self):
        self.assertEqual(validate_article_url(ARTICLE_URL), ARTICLE_URL)

    def test_deceptive_or_local_hosts_are_rejected(self):
        for url in (
            "http://localhost/",
            "http://127.0.0.1/",
            "http://n.news.naver.com.evil.test/article/1",
            "http://n.news.naver.com:8080/article/1",
            "http://user@n.news.naver.com/article/1",
        ):
            with self.subTest(url=url), self.assertRaises(ValueError):
                validate_article_url(url)

    def test_redirect_to_unlisted_host_is_rejected_before_request(self):
        with self.assertRaises(ValueError):
            _AllowedRedirectHandler().redirect_request(
                None, None, 302, "Found", {}, "http://127.0.0.1/private"
            )
