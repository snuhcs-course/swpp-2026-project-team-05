"""API contract tests that do not call Gemini or Naver."""

import json
import os
from unittest.mock import patch

from django.test import Client, SimpleTestCase

from server.analysis.article_fetch import _PublicRedirectHandler, validate_article_url
from server.analysis.model_client import ModelUnavailableError


ARTICLE_URL = "https://n.news.naver.com/article/057/0001971678"


class AnalyzeApiTests(SimpleTestCase):
    def setUp(self):
        self.client = Client()
        credentials = patch.dict(os.environ, {
            "GEMINI_API_KEY": "test-key",
            "NAVER_CLIENT_ID": "test-id",
            "NAVER_CLIENT_SECRET": "test-secret",
        })
        credentials.start()
        self.addCleanup(credentials.stop)

    def post(self, payload, *, content_type="application/json"):
        return self.client.post(
            "/api/analyze",
            data=json.dumps(payload),
            content_type=content_type,
        )

    def test_health(self):
        response = self.client.get("/api/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {
            "status": "ok",
            "analysis_ready": True,
            "missing_configuration": [],
        })

    @patch.dict(os.environ, {"GEMINI_API_KEY": "", "GOOGLE_API_KEY": "test-google-key"})
    def test_google_api_key_is_accepted_for_gemini(self):
        response = self.client.get("/api/health")
        self.assertTrue(response.json()["analysis_ready"])

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
        self.assertEqual(len(response.headers["X-Analysis-ID"]), 12)
        self.assertEqual(response.json(), analyze.return_value)
        analyze.assert_called_once_with(ARTICLE_URL, max_related=2)

    @patch("server.views._analysis_slot.acquire", return_value=False)
    @patch("server.views.analyze_related_articles")
    def test_busy_server_returns_retryable_error(self, analyze, acquire):
        response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 429)
        self.assertEqual(response.headers["Retry-After"], "30")
        analyze.assert_not_called()

    @patch("server.views.analyze_related_articles")
    def test_invalid_url_cannot_call_pipeline(self, analyze):
        response = self.post({"url": "http://127.0.0.1/admin"})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "invalid_url")
        analyze.assert_not_called()

    def test_max_related_is_bounded(self):
        response = self.post({"url": ARTICLE_URL, "max_related": 10})
        self.assertEqual(response.status_code, 400)
        response = self.post({"url": ARTICLE_URL, "max_related": True})
        self.assertEqual(response.status_code, 400)

    def test_large_request_is_rejected(self):
        response = self.post({"url": ARTICLE_URL, "extra": "x" * 9000})
        self.assertEqual(response.status_code, 413)

    @patch("server.views.analyze_related_articles", side_effect=RuntimeError("private upstream detail"))
    def test_upstream_error_does_not_leak_details(self, analyze):
        with self.assertLogs("server.views", level="ERROR"):
            response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 502)
        self.assertNotIn("private upstream detail", response.content.decode())

    @patch("server.views.analyze_related_articles", side_effect=ModelUnavailableError("unavailable"))
    def test_model_service_unavailable_is_retryable(self, analyze):
        with self.assertLogs("server.views", level="WARNING"):
            response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["error"]["code"], "analysis_unavailable")
        self.assertEqual(response.headers["Retry-After"], "30")

    def test_invalid_json_is_rejected(self):
        response = self.client.post(
            "/api/analyze", data="{not json", content_type="application/json"
        )
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "invalid_json")

    def test_wrong_method_is_rejected(self):
        self.assertEqual(self.client.get("/api/analyze").status_code, 405)

    @patch.dict(os.environ, {"GEMINI_API_KEY": "", "GOOGLE_API_KEY": "", "NAVER_CLIENT_ID": ""})
    def test_missing_credentials_are_reported_without_calling_pipeline(self):
        with patch("server.views.analyze_related_articles") as analyze:
            health = self.client.get("/api/health")
            response = self.post({"url": ARTICLE_URL})
        self.assertFalse(health.json()["analysis_ready"])
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["error"]["code"], "missing_configuration")
        analyze.assert_not_called()


class ArticleUrlTests(SimpleTestCase):
    def test_public_news_hosts_are_allowed(self):
        self.assertEqual(validate_article_url(ARTICLE_URL), ARTICLE_URL)
        self.assertEqual(validate_article_url("https://news.example.com/story"), "https://news.example.com/story")

    def test_local_hosts_and_special_urls_are_rejected(self):
        for url in (
            "http://localhost/",
            "http://127.0.0.1/",
            "http://[::1]/",
            "http://printer.local/article",
            "http://n.news.naver.com:8080/article/1",
            "http://user@n.news.naver.com/article/1",
        ):
            with self.subTest(url=url), self.assertRaises(ValueError):
                validate_article_url(url)

    def test_redirect_to_local_host_is_rejected_before_request(self):
        with self.assertRaises(ValueError):
            _PublicRedirectHandler().redirect_request(
                None, None, 302, "Found", {}, "http://127.0.0.1/private"
            )
