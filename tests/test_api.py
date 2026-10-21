"""API contract tests that do not call Gemini or Naver."""

import json
import logging
import os
from unittest.mock import patch

from django.test import Client, SimpleTestCase, override_settings

from server.analysis.article_fetch import _PublicRedirectHandler, validate_article_url
from server.analysis.model_client import ModelUnavailableError
from server.analysis.diagnostics import analysis_stage
from server.analysis.v2_adapter import PipelineContractError
from server.views import PipelineResultError, analyze_related_articles_v2


ARTICLE_URL = "https://n.news.naver.com/article/057/0001971678"


class AnalyzeApiTests(SimpleTestCase):
    def setUp(self):
        self.client = Client()
        credentials = patch.dict(os.environ, {
            "GEMINI_API_KEY": "test-key",
            "NAVER_CLIENT_ID": "test-id",
            "NAVER_CLIENT_SECRET": "test-secret",
            "FRAMELESS_PIPELINE": "v1",
            "FRAMELESS_ALLOW_LEGACY_V1": "1",
            "OPENROUTER_API_KEY": "",
        })
        credentials.start()
        self.addCleanup(credentials.stop)

    def post(self, payload, *, content_type="application/json", **headers):
        return self.client.post(
            "/api/analyze",
            data=json.dumps(payload),
            content_type=content_type,
            **headers,
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

    @patch.dict(os.environ, {"FRAMELESS_PIPELINE": "v2", "GEMINI_API_KEY": "",
                                 "GOOGLE_API_KEY": "", "OPENROUTER_API_KEY": "test-router-key"})
    def test_openrouter_key_alone_is_ready_for_v2(self):
        response = self.client.get("/api/health")
        self.assertTrue(response.json()["analysis_ready"])
        self.assertEqual(response.json()["missing_configuration"], [])

    @patch.dict(os.environ, {"FRAMELESS_PIPELINE": "v1", "GEMINI_API_KEY": "",
                                 "GOOGLE_API_KEY": "", "OPENROUTER_API_KEY": "test-router-key"})
    def test_legacy_v1_still_requires_direct_gemini_key(self):
        response = self.client.get("/api/health")
        self.assertFalse(response.json()["analysis_ready"])

    @override_settings(IS_PRODUCTION=True)
    @patch.dict(os.environ, {"FRAMELESS_PIPELINE": "v2", "OPENROUTER_API_KEY": "test-router-key"})
    @patch("server.views.analyze_related_articles_v2", return_value={"ok": True})
    def test_production_openrouter_uses_existing_analysis_request(self, analyze):
        self.assertTrue(self.client.get("/api/health").json()["analysis_ready"])
        response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"ok": True})
        analyze.assert_called_once_with(ARTICLE_URL, max_related=3)

    @override_settings(IS_PRODUCTION=True)
    @patch.dict(os.environ, {"FRAMELESS_PIPELINE": "v2", "OPENROUTER_API_KEY": ""})
    @patch("server.views.analyze_related_articles_v2", return_value={"ok": True})
    def test_production_direct_gemini_path_does_not_require_demo_token(self, analyze):
        response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 200)
        analyze.assert_called_once_with(ARTICLE_URL, max_related=3)

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
        with self.assertLogs("server.views", level="ERROR") as captured:
            response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 502)
        self.assertNotIn("private upstream detail", response.content.decode())
        self.assertTrue(any("pipeline_unhandled" in row for row in captured.output))
        self.assertTrue(all("private upstream detail" not in row for row in captured.output))
        self.assertTrue(all(record.exc_info is None for record in captured.records))

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

    def test_json_api_rejects_wrong_content_type_and_non_object_body(self):
        wrong_type = self.client.post("/api/analyze", data="{}", content_type="text/plain")
        non_object = self.client.post("/api/analyze", data="[]", content_type="application/json")

        self.assertEqual(wrong_type.status_code, 415)
        self.assertEqual(wrong_type.json()["error"]["code"], "invalid_content_type")
        self.assertEqual(non_object.status_code, 400)
        self.assertEqual(non_object.json()["error"]["code"], "invalid_request")

    @patch("server.views.analyze_related_articles")
    def test_invalid_url_shape_and_oversized_url_do_not_call_pipeline(self, analyze):
        for url in (None, 42, "https://example.com/" + "x" * 2049):
            oversized = isinstance(url, str) and len(url) > 2048
            with self.subTest(url_type=type(url).__name__, oversized=oversized):
                response = self.post({"url": url})
                self.assertEqual(response.status_code, 400)
                self.assertEqual(response.json()["error"]["code"], "invalid_url")
        analyze.assert_not_called()

    @patch("server.views.analyze_related_articles")
    def test_pipeline_error_retryability_maps_to_http_status(self, analyze):
        for retryable, expected_status in ((False, 502), (True, 503)):
            with self.subTest(retryable=retryable):
                analyze.side_effect = PipelineResultError(
                    "source_timeout", "기사 본문 확인 실패", retryable=retryable
                )
                response = self.post({"url": ARTICLE_URL})
                self.assertEqual(response.status_code, expected_status)
                self.assertEqual(response.json()["error"]["code"], "source_timeout")
                if retryable:
                    self.assertEqual(response["Retry-After"], "30")
                else:
                    self.assertNotIn("Retry-After", response)

    @patch.dict(os.environ, {"FRAMELESS_PIPELINE": "unknown"})
    @patch("server.views.analyze_related_articles")
    def test_unknown_pipeline_configuration_fails_closed(self, analyze):
        response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["error"]["code"], "invalid_pipeline_config")
        analyze.assert_not_called()

    def test_wrong_method_is_rejected(self):
        self.assertEqual(self.client.get("/api/analyze").status_code, 405)

    @override_settings(IS_PRODUCTION=True)
    @patch("server.views.analyze_related_articles")
    def test_legacy_pipeline_cannot_run_on_public_server(self, analyze):
        response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["error"]["code"], "invalid_pipeline_config")
        analyze.assert_not_called()

    @patch.dict(os.environ, {"FRAMELESS_ALLOW_LEGACY_V1": ""})
    @patch("server.views.analyze_related_articles")
    def test_legacy_pipeline_requires_explicit_local_opt_in(self, analyze):
        response = self.post({"url": ARTICLE_URL})
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["error"]["code"], "invalid_pipeline_config")
        analyze.assert_not_called()

    @patch.dict(os.environ, {"GEMINI_API_KEY": "", "GOOGLE_API_KEY": "", "OPENROUTER_API_KEY": "",
                                 "NAVER_CLIENT_ID": ""})
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


class DiagnosticsTests(SimpleTestCase):
    def test_stage_failure_log_never_records_exception_text_or_traceback(self):
        with self.assertLogs("tests.safe_diagnostics", level="ERROR") as captured:
            with self.assertRaisesRegex(RuntimeError, "private article body"):
                with analysis_stage(logging.getLogger("tests.safe_diagnostics"), "synthetic_stage"):
                    raise RuntimeError("private article body")
        self.assertIn('"stage":"synthetic_stage"', captured.output[0])
        self.assertNotIn("private article body", captured.output[0])
        self.assertIsNone(captured.records[0].exc_info)


class V2EndpointAdapterTests(SimpleTestCase):
    @patch("server.analysis.v2.pipeline.run_analysis_pipeline")
    @patch("server.views.adapt_v2_result", return_value={"reader": "ok"})
    def test_successful_pipeline_is_adapted_with_reader_limits(self, adapt, run_pipeline):
        run_pipeline.return_value = {
            "execution_status": "success",
            "source_analysis": {"title": "source"},
            "comparison_status": "available",
        }

        result = analyze_related_articles_v2(ARTICLE_URL, max_related=2)

        self.assertEqual(result, {"reader": "ok"})
        run_pipeline.assert_called_once_with(
            ARTICLE_URL,
            max_claims=3,
            max_candidates=4,
            max_per_claim=2,
            event_baseline_limit=2,
            verification_batch_size=1,
            max_rubric_articles=2,
            analysis_profile="reader",
            use_cache=False,
        )
        adapt.assert_called_once_with(
            run_pipeline.return_value, max_related=2, requested_url=ARTICLE_URL
        )

    @patch("server.analysis.v2.pipeline.run_analysis_pipeline")
    def test_terminal_pipeline_failure_ignores_earlier_recoverable_search_error(self, run_pipeline):
        run_pipeline.return_value = {
            "execution_status": "failed",
            "source_analysis": None,
            "errors": [
                {"stage": "search", "code": "SEARCH_HTTP_ERROR", "retryable": True},
                {"stage": "source_analysis", "code": "SOURCE_REJECTED", "retryable": False},
            ],
        }

        with self.assertRaises(PipelineResultError) as raised:
            analyze_related_articles_v2(ARTICLE_URL)

        self.assertEqual(raised.exception.code, "source_rejected")
        self.assertFalse(raised.exception.retryable)

    @patch("server.analysis.v2.pipeline.run_analysis_pipeline")
    def test_unavailable_comparison_is_retryable_when_search_can_recover(self, run_pipeline):
        run_pipeline.return_value = {
            "execution_status": "success",
            "source_analysis": {"title": "source"},
            "comparison_status": "unavailable",
            "errors": [{"stage": "search", "code": "SEARCH_HTTP_ERROR", "retryable": True}],
        }

        with self.assertRaises(PipelineResultError) as raised:
            analyze_related_articles_v2(ARTICLE_URL)

        self.assertEqual(raised.exception.code, "comparison_unavailable")
        self.assertTrue(raised.exception.retryable)

    @patch("server.analysis.v2.pipeline.run_analysis_pipeline")
    @patch("server.views.adapt_v2_result")
    def test_invalid_adapter_contract_is_reported_without_leaking_details(self, adapt, run_pipeline):
        adapt.side_effect = PipelineContractError("unsafe contract detail")
        run_pipeline.return_value = {
            "execution_status": "success",
            "source_analysis": {"title": "source"},
        }

        with self.assertRaises(PipelineResultError) as raised:
            analyze_related_articles_v2(ARTICLE_URL)

        self.assertEqual(raised.exception.code, "invalid_analysis_result")
        self.assertNotIn("unsafe contract detail", str(raised.exception))
