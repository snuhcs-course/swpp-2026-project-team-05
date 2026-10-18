"""Offline tests for the bounded OpenRouter Exa search fallback."""

from io import BytesIO
import json
from time import monotonic
from unittest import TestCase
from unittest.mock import patch
from urllib.error import HTTPError

from server.analysis.v2.article_search import search_candidate_metadata, search_openrouter_exa_news
from server.analysis.v2.model_client import OPENROUTER_COMPLETIONS_URL
from server.analysis.v2.pipeline import _log_stage_result
from server.analysis.v2.result_output import OperationError


SOURCE = {"url": "https://news.example.com/source", "title": "청계천 수질 조사"}
ANALYSIS = {"event_query": "청계천 수질 조사", "core_event": "청계천 수질 조사 발표", "claims": []}
EXA_ROW = {"title": "다른 언론사 기사", "description": "청계천 수질 조사 발표",
           "link": "https://other.example.com/report", "originallink": "",
           "search_provided_at": "", "search_provider": "openrouter_exa"}


def _citation(url: str, title: str = "다른 언론사 기사", content: str = "같은 사건") -> dict:
    return {"type": "url_citation", "url_citation": {"url": url, "title": title, "content": content}}


def _response(annotations: list[dict], *, searches: int | None = 1,
              finish_reason: str | None = None) -> bytes:
    response = {"choices": [{"finish_reason": finish_reason, "message": {
        "content": "허구 링크 https://invented.example.com/report 는 신뢰하지 않습니다.",
        "annotations": annotations}}]}
    if searches is not None:
        response["usage"] = {"server_tool_use": {"web_search_requests": searches}}
    return json.dumps(response).encode("utf-8")


class OpenRouterExaSearchTests(TestCase):
    def _search(self, response: bytes) -> tuple[list[dict], dict]:
        with (patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.return_value = BytesIO(response)
            rows = search_openrouter_exa_news(SOURCE, ANALYSIS)
            request = build.return_value.open.call_args.args[0]
            body = json.loads(request.data)
        return rows, body

    def test_one_pinned_exa_plugin_search_uses_only_structured_citations(self):
        rows, body = self._search(_response([
            _citation(SOURCE["url"], "원문"),
            _citation("https://other.example.com/report?utm_source=search", content="수질 결과"),
            _citation("https://other.example.com/report", content="중복 결과"),
            _citation("http://localhost/private", "로컬 주소"),
            _citation("https://google.com/search?q=cheonggyecheon", "검색 페이지"),
            {"type": "url_citation", "url": "https://flat.example.com/fake", "title": "다른 구조"},
        ]))
        self.assertEqual(body["model"], "google/gemini-3.8-flash")
        self.assertEqual(body["plugins"], [{"id": "web", "engine": "exa",
                                            "mode": "fast", "max_results": 10}])
        self.assertNotIn("tools", body)
        self.assertNotIn("tool_choice", body)
        self.assertNotIn("max_tool_calls", body)
        self.assertEqual(body["max_tokens"], 2048)
        self.assertEqual([message["role"] for message in body["messages"]], ["system", "user"])
        self.assertIn("웹 검색 도구를 정확히 한 번", body["messages"][0]["content"])
        search_data = json.loads(body["messages"][1]["content"].split("\n", 1)[1])
        self.assertEqual(search_data["title"], SOURCE["title"])
        self.assertEqual(search_data["event"], ANALYSIS["core_event"])
        self.assertEqual(search_data["queries"], [ANALYSIS["event_query"]])
        self.assertNotIn(SOURCE["url"], body["messages"][1]["content"])
        self.assertEqual([row["link"] for row in rows],
                         ["https://other.example.com/report?utm_source=search"])
        self.assertEqual(rows[0]["description"], "수질 결과")
        self.assertEqual(rows[0]["search_provider"], "openrouter_exa")

    def test_request_uses_chat_completions_and_bounded_timeout(self):
        with (patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.return_value = BytesIO(_response([_citation(EXA_ROW["link"])]))
            self.assertEqual(search_openrouter_exa_news(SOURCE, ANALYSIS, timeout_seconds=7)[0]["link"],
                             EXA_ROW["link"])
            request = build.return_value.open.call_args.args[0]
            self.assertEqual(request.full_url, OPENROUTER_COMPLETIONS_URL)
            self.assertEqual(request.get_method(), "POST")
            self.assertEqual(build.return_value.open.call_args.kwargs["timeout"], 7)

    def test_missing_or_zero_server_tool_usage_accepts_provider_citation(self):
        for search_count in (None, 0):
            with self.subTest(search_count=search_count):
                with (patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
                      patch("server.analysis.v2.article_search.build_opener") as build):
                    build.return_value.open.return_value = BytesIO(
                        _response([_citation(EXA_ROW["link"])], searches=search_count,
                                  finish_reason="stop"))
                    with self.assertLogs("server.analysis.v2.article_search", level="INFO") as logs:
                        rows = search_openrouter_exa_news(SOURCE, ANALYSIS)
                self.assertEqual(rows[0]["link"], EXA_ROW["link"])
                self.assertIn(f"web_search_requests={search_count if search_count is not None else 'missing'}",
                              logs.output[0])
                self.assertIn("annotation_count=1 structured_citation_count=1 valid_citation_count=1",
                              logs.output[0])
                self.assertIn("finish_reason=stop", logs.output[0])
                self.assertNotIn(EXA_ROW["link"], logs.output[0])
                self.assertNotIn("synthetic-key", logs.output[0])

    def test_missing_citations_rejects_model_prose_and_flat_urls(self):
        for annotations in ([], [{"type": "url_citation", "url": "https://flat.example.com/fake"}]):
            with self.subTest(annotations=annotations):
                with (patch("server.analysis.v2.article_search.get_openrouter_api_key",
                            return_value="synthetic-key"),
                      patch("server.analysis.v2.article_search.build_opener") as build):
                    build.return_value.open.return_value = BytesIO(_response(
                        annotations, searches=None, finish_reason="arbitrary provider text"))
                    with self.assertLogs("server.analysis.v2.article_search", level="INFO") as logs:
                        with self.assertRaises(OperationError) as raised:
                            search_openrouter_exa_news(SOURCE, ANALYSIS)
                self.assertEqual(raised.exception.code, "OPENROUTER_SEARCH_CITATIONS_MISSING")
                self.assertIn(f"annotation_count={len(annotations)} structured_citation_count=0 "
                              "valid_citation_count=0", logs.output[0])
                self.assertIn("finish_reason=other", logs.output[0])
                self.assertNotIn("flat.example.com", logs.output[0])

    def test_http_failure_records_only_status(self):
        with (patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.side_effect = HTTPError(OPENROUTER_COMPLETIONS_URL, 429,
                                                            "sensitive upstream text", None, None)
            with self.assertLogs("server.analysis.v2.article_search", level="WARNING") as logs:
                with self.assertRaises(OperationError) as raised:
                    search_openrouter_exa_news(SOURCE, ANALYSIS)
        self.assertEqual(raised.exception.code, "OPENROUTER_SEARCH_HTTP_ERROR")
        self.assertEqual(raised.exception.http_status, 429)
        self.assertTrue(raised.exception.retryable)
        self.assertEqual(logs.output, ["WARNING:server.analysis.v2.article_search:"
                                       "openrouter_search_http_error upstream_status=429"])

    def test_all_naver_and_google_failures_use_exa_and_preserve_errors(self):
        naver_error = OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
        google_error = OperationError("Google quota", code="GOOGLE_SEARCH_HTTP_ERROR", http_status=429)
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", side_effect=google_error),
              patch("server.analysis.v2.article_search.search_openrouter_exa_news", return_value=[EXA_ROW]) as exa):
            result = search_candidate_metadata(SOURCE, ANALYSIS)
        exa.assert_called_once()
        self.assertEqual(result["search_status"], "partial")
        self.assertEqual([(row["provider"], row["status"]) for row in result["queries"]],
                         [("naver", "search_failed"), ("google_grounding", "search_failed"),
                          ("openrouter_exa", "searched")])
        self.assertEqual([(row["provider"], row["http_status"]) for row in result["errors"]],
                         [("naver", 429), ("google_grounding", 429)])
        self.assertEqual(result["metadata_candidates"][0]["urls"], [EXA_ROW["link"]])
        self.assertEqual(result["metadata_candidates"][0]["search_matches"][0]["provider"],
                         "openrouter_exa")

    def test_google_empty_uses_exa_but_google_success_does_not(self):
        naver_error = OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", return_value=[]),
              patch("server.analysis.v2.article_search.search_openrouter_exa_news", return_value=[EXA_ROW]) as exa):
            result = search_candidate_metadata(SOURCE, ANALYSIS)
        self.assertEqual(result["metadata_candidates"][0]["search_matches"][0]["provider"],
                         "openrouter_exa")
        exa.assert_called_once()

        google_row = {**EXA_ROW, "search_provider": "google_grounding"}
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", return_value=[google_row]),
              patch("server.analysis.v2.article_search.search_openrouter_exa_news") as exa):
            result = search_candidate_metadata(SOURCE, ANALYSIS)
        self.assertEqual(result["metadata_candidates"][0]["search_matches"][0]["provider"],
                         "google_grounding")
        exa.assert_not_called()

    def test_missing_gemini_key_uses_exa_after_naver_outage(self):
        naver_error = OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value=""),
              patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news") as google,
              patch("server.analysis.v2.article_search.search_openrouter_exa_news", return_value=[EXA_ROW]) as exa):
            result = search_candidate_metadata(SOURCE, ANALYSIS)
        google.assert_not_called()
        exa.assert_called_once()
        self.assertEqual([(row["provider"], row["status"]) for row in result["queries"]],
                         [("naver", "search_failed"), ("openrouter_exa", "searched")])
        self.assertEqual([row["provider"] for row in result["errors"]], ["naver"])

    def test_exa_error_codes_survive_discovery_stage_logging(self):
        codes = ["OPENROUTER_SEARCH_CONFIGURATION_MISSING", "OPENROUTER_SEARCH_CONFIGURATION_INVALID",
                 "OPENROUTER_SEARCH_RESPONSE_INVALID", "OPENROUTER_SEARCH_CITATIONS_MISSING",
                 "OPENROUTER_SEARCH_TIMEOUT", "OPENROUTER_SEARCH_CONNECTION_FAILED",
                 "OPENROUTER_SEARCH_REDIRECT_BLOCKED", "OPENROUTER_SEARCH_HTTP_ERROR"]
        with patch("server.analysis.v2.pipeline.log_event") as log_event:
            _log_stage_result("discovery", {"execution_status": "failed",
                                            "errors": [{"stage": "search", "code": code} for code in codes]})
        self.assertEqual(log_event.call_args.kwargs["error_codes"], sorted(codes))
        self.assertNotIn("PROCESSING_FAILED", log_event.call_args.kwargs["error_codes"])

    def test_naver_success_does_not_call_exa_and_exa_failure_preserves_google_error(self):
        naver_row = {**EXA_ROW, "search_provider": "naver", "search_sort": "sim"}
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", return_value=[naver_row]),
              patch("server.analysis.v2.article_search.search_openrouter_exa_news") as exa):
            result = search_candidate_metadata(SOURCE, ANALYSIS)
        self.assertEqual(result["search_status"], "success")
        exa.assert_not_called()

        naver_error = OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
        google_error = OperationError("Google quota", code="GOOGLE_SEARCH_HTTP_ERROR", http_status=429)
        exa_error = OperationError("Exa quota", code="OPENROUTER_SEARCH_HTTP_ERROR", http_status=503)
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", side_effect=google_error),
              patch("server.analysis.v2.article_search.search_openrouter_exa_news", side_effect=exa_error)):
            failed = search_candidate_metadata(SOURCE, ANALYSIS)
        self.assertEqual(failed["search_status"], "failed")
        self.assertEqual([error["provider"] for error in failed["errors"]],
                         ["naver", "google_grounding", "openrouter_exa"])

    def test_google_timeout_reserves_time_for_exa_and_expired_deadline_skips_both(self):
        naver_error = OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
        google_error = OperationError("Google quota", code="GOOGLE_SEARCH_HTTP_ERROR", http_status=429)
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.get_openrouter_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", side_effect=google_error) as google,
              patch("server.analysis.v2.article_search.search_openrouter_exa_news", return_value=[]) as exa):
            search_candidate_metadata(SOURCE, ANALYSIS, google_fallback_deadline_at=monotonic() + 50)
            google_timeout = google.call_args.kwargs["timeout_seconds"]
            exa_timeout = exa.call_args.kwargs["timeout_seconds"]
        self.assertGreaterEqual(google_timeout, 5)
        self.assertLess(google_timeout, 20)
        self.assertGreaterEqual(exa_timeout, 5)
        self.assertLessEqual(exa_timeout, 35)

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key") as gemini_key,
              patch("server.analysis.v2.article_search.get_openrouter_api_key") as router_key,
              patch("server.analysis.v2.article_search.search_google_grounded_news") as google,
              patch("server.analysis.v2.article_search.search_openrouter_exa_news") as exa):
            result = search_candidate_metadata(SOURCE, ANALYSIS,
                                               google_fallback_deadline_at=monotonic() + 4)
        self.assertEqual(result["search_status"], "failed")
        gemini_key.assert_not_called()
        google.assert_not_called()
        exa.assert_not_called()
        self.assertGreaterEqual(router_key.call_count, 1)
