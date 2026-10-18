"""Offline regression tests for the five-stage article comparison pipeline."""

# AI-assisted Iteration 2 tests use invented articles and never call external services.
from copy import deepcopy
from concurrent.futures import Future, ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from email.message import Message
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Event, Lock
from time import monotonic
from types import SimpleNamespace
from unittest.mock import patch

from django.test import SimpleTestCase

from server.analysis.diagnostics import analysis_id
from server.analysis.v2.article_fetch import ArticleFetchError, fetch_article, validate_article_url
from server.analysis.v2.article_input import prepare_article, structure_article_body
from server.analysis.v2.article_search import (
    fetch_candidate_articles, normalize_news_url, search_candidate_metadata,
)
from server.analysis.v2.comparison_selection import select_verified_comparisons
from server.analysis.v2.llm_analysis import (
    analyze_source_article, prioritize_diverse_candidates, verify_candidate_articles,
)
from server.analysis.v2.model_client import (
    MetricsJSONClient, ModelHTTPError, ModelResponseError, ModelResponseTruncatedError,
    ModelUnavailableError, OpenRouterJSONClient,
)
from server.analysis.v2.pipeline import (
    _load_cached_analysis, _scoped_model_deadline, complete_reader_analysis, complete_rubric_analysis,
    evaluate_analysis_snapshot, run_analysis_pipeline,
    run_discovery_pipeline,
)
from server.analysis.v2.result_output import OperationError, format_analysis_result, make_error_record
from server.analysis.v2.rubric_analysis import (
    _verified_comparison_context, analyze_selected_articles, compare_claim_expressions,
    retain_verified_comparison_context,
)


SOURCE_URL = "https://news.example.com/source"


def article(candidate_id=None, body="시청은 새 계획을 발표했다."):
    result = {
        "url": SOURCE_URL if candidate_id is None else f"https://news.example.com/{candidate_id}",
        "title": "새 계획 발표", "publisher": "가상일보", "published_at": "",
        "body": body,
        "sentences": [{"sentence_index": 0, "paragraph_index": 0, "start": 0,
                       "end": len(body), "text": body}],
    }
    if candidate_id is not None:
        result["candidate_id"] = candidate_id
    return result


def source_analysis():
    return {
        "core_event": "시청의 새 계획 발표", "event_query": "시청 새 계획 발표",
        "claims": [{"claim_id": "C1", "claim": "시청이 계획을 발표했다",
                    "claim_kind": "reported_fact", "speaker": "",
                    "evidence": {"sentence_index": 0, "quote": "시청은 새 계획을 발표했다"},
                    "search_query": "시청 새 계획"}],
    }


def empty_search(*, failure=False):
    query = "시청 새 계획 발표"
    error = make_error_record(OperationError("검색 실패", code="SEARCH_FAILED"),
                              stage="search", query=query)
    return {
        "queries": [{"query": query, "status": "search_failed" if failure else "searched"}],
        "metadata_candidates": [], "discovered_count": 0,
        "errors": [error] if failure else [],
        "search_status": "failed" if failure else "success",
    }


class StubClient:
    """Supply one schema-valid model response to the real grounding code."""

    def __init__(self, response):
        self.response = response
        self.calls = 0

    def generate_json(self, **_kwargs):
        self.calls += 1
        return deepcopy(self.response)


class SequenceClient:
    """Return fixed synthetic outputs while preserving requests for retry checks."""

    def __init__(self, responses):
        self.responses = list(responses)
        self.requests = []

    def generate_json(self, **request):
        self.requests.append(deepcopy(request))
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return deepcopy(response)


class RubricQueueClient:
    """Give each named full-rubric operation one reproducible model response."""

    def __init__(self, information, expression, repair_information=None):
        self.information = information
        self.expression = expression
        self.repair_information = repair_information or {}
        self.calls = []
        self.requests = []

    def generate_json(self, **request):
        self.calls.append((request["name"], request["input_data"].get("article_id")))
        self.requests.append(deepcopy(request))
        if request["name"] == "article_information_I1_I5":
            return deepcopy(self.information[request["input_data"]["article_id"]])
        if request["name"] == "repair_article_information_I1_I5":
            return deepcopy(self.repair_information[request["input_data"]["article_id"]])
        if request["name"] == "claim_expression_A_G":
            return deepcopy(self.expression)
        raise AssertionError(f"unexpected model task: {request['name']}")


class StubHTTPResponse:
    def __init__(self, payload, *, content_type="text/html; charset=utf-8", content_length=None):
        self.payload = payload
        self.headers = Message()
        self.headers["Content-Type"] = content_type
        if content_length is not None:
            self.headers["Content-Length"] = str(content_length)

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self, limit):
        return self.payload[:limit]


class ArticleInputTests(SimpleTestCase):
    def test_public_url_only_rejects_local_hosts_ip_and_custom_port(self):
        for unsafe in ("http://localhost/story", "http://127.0.0.1/story",
                       "https://169.254.169.254/latest/meta-data/",
                       "https://news.example.com:8443/story", "file:///etc/passwd"):
            with self.subTest(url=unsafe), self.assertRaises(ValueError):
                validate_article_url(unsafe)

    def test_sentence_positions_preserve_quotes_decimals_urls_and_paragraphs(self):
        body = ("시장은 “예산 1.5억 원이다. 다음 달 시행한다”고 말했다.\n\n"
                "Dr. Kim은 https://news.example.com/a.b 를 인용했다. 마지막 문장")
        structure = structure_article_body(body)
        self.assertEqual(len(structure["paragraphs"]), 2)
        self.assertEqual([row["paragraph_index"] for row in structure["sentences"]], [0, 1, 1])
        self.assertEqual([row["sentence_index"] for row in structure["sentences"]], [0, 1, 2])
        self.assertEqual([row["text"] for row in structure["sentences"]], [
            "시장은 “예산 1.5억 원이다. 다음 달 시행한다”고 말했다.",
            "Dr. Kim은 https://news.example.com/a.b 를 인용했다.", "마지막 문장"])
        for row in structure["sentences"] + structure["paragraphs"]:
            self.assertEqual(body[row["start"]:row["end"]], row["text"])

    def test_prepare_article_retains_extracted_body_and_stable_sentence_offsets(self):
        body = "첫 보도다.\n두 번째 보도다."
        with patch("server.analysis.v2.article_input.fetch_article", return_value={
            "url": SOURCE_URL, "title": "제목", "publisher": "가상일보", "published_at": "", "body": body}):
            result = prepare_article(SOURCE_URL)
        self.assertEqual(result["body"], body)
        self.assertEqual([s["text"] for s in result["sentences"]], ["첫 보도다.", "두 번째 보도다."])
        self.assertEqual([s["paragraph_index"] for s in result["sentences"]], [0, 1])

    def test_fetch_article_preserves_metadata_and_extracted_body(self):
        url = "https://n.news.naver.com/article/001/0000001"
        html = ("<html><head><meta property='og:title' content='기사 제목'>"
                "<meta property='og:site_name' content='네이버 뉴스'>"
                "<meta property='og:article:author' content='가상일보 | 네이버'>"
                "<meta property='article:published_time' content='2026-10-18T10:00:00+09:00'>"
                "</head><body><article>본문입니다.</article></body></html>")
        response = StubHTTPResponse(html.encode())
        with (patch("server.analysis.v2.article_fetch.build_opener",
                    return_value=SimpleNamespace(open=lambda _request, timeout: response)),
              patch("trafilatura.extract", return_value="본문입니다.") as extract):
            result = fetch_article(url)
        self.assertEqual(result["title"], "기사 제목")
        self.assertEqual(result["publisher"], "가상일보")
        self.assertEqual(result["published_at"], "2026-10-18T10:00:00+09:00")
        self.assertEqual(result["body"], "본문입니다.")
        extract.assert_called_once()

    def test_fetch_article_reads_json_ld_and_falls_back_to_page_title(self):
        metadata = {"@graph": [{"@type": "NewsArticle", "publisher": {"name": "가상일보"},
                                "datePublished": "2026-10-18T11:00:00+09:00"}]}
        html = ("<html><head><title>기본 제목</title><script type='application/ld+json'>"
                + json.dumps(metadata) + "</script></head><body>기사 본문</body></html>")
        response = StubHTTPResponse(html.encode())
        with (patch("server.analysis.v2.article_fetch.build_opener",
                    return_value=SimpleNamespace(open=lambda _request, timeout: response)),
              patch("trafilatura.extract", return_value="기사 본문")):
            result = fetch_article(SOURCE_URL)
        self.assertEqual((result["title"], result["publisher"], result["published_at"]),
                         ("기본 제목", "가상일보", "2026-10-18T11:00:00+09:00"))

    def test_fetch_article_rejects_non_html_and_truncated_responses(self):
        cases = [
            (StubHTTPResponse(b"%PDF", content_type="application/pdf"), "content_type"),
            (StubHTTPResponse(b"<html/>", content_length=100), "incomplete_response"),
        ]
        for response, stage in cases:
            with (self.subTest(stage=stage),
                  patch("server.analysis.v2.article_fetch.build_opener",
                        return_value=SimpleNamespace(open=lambda _request, timeout: response))):
                with self.assertRaises(ArticleFetchError) as raised:
                    fetch_article(SOURCE_URL)
                self.assertEqual(raised.exception.stage, stage)

    def test_fetch_article_rejects_missing_extracted_body(self):
        response = StubHTTPResponse(b"<html><title>Article</title></html>")
        with (patch("server.analysis.v2.article_fetch.build_opener",
                    return_value=SimpleNamespace(open=lambda _request, timeout: response)),
              patch("trafilatura.extract", return_value=None)):
            with self.assertRaises(ArticleFetchError) as raised:
                fetch_article(SOURCE_URL)
        self.assertEqual(raised.exception.stage, "extraction")


class StageBudgetTests(SimpleTestCase):
    def test_candidate_deadline_bounds_provider_call_and_restores_reader_budget(self):
        raw = object.__new__(OpenRouterJSONClient)
        raw.model, raw.task_models = "synthetic-model", {}
        requests = []
        raw.generate_json = lambda **request: requests.append(request) or {}
        original_deadline = monotonic() + 180
        recorder = MetricsJSONClient(raw, deadline_at=original_deadline)
        stage_deadline = monotonic() + 5

        with _scoped_model_deadline(recorder, stage_deadline):
            recorder.generate_json(name="verify_selected_candidate_bodies", instructions="synthetic",
                                   input_data={}, schema={})
            self.assertEqual(recorder.deadline_at, stage_deadline)
        self.assertEqual(recorder.deadline_at, original_deadline)
        self.assertGreater(requests[0]["timeout_budget_ms"], 4000)
        self.assertLessEqual(requests[0]["timeout_budget_ms"], 5000)

        with self.assertRaisesRegex(RuntimeError, "synthetic"):
            with _scoped_model_deadline(recorder, stage_deadline):
                raise RuntimeError("synthetic")
        self.assertEqual(recorder.deadline_at, original_deadline)

    def test_discovery_scopes_candidate_verification_before_reader_reserve(self):
        deadline = monotonic() + 240
        recorder = MetricsJSONClient(object(), deadline_at=deadline)
        metadata_search = {**empty_search(), "metadata_candidates": [{"candidate_id": "A1"}]}
        captured = []

        def select(_source, _analysis, _metadata, _ranking, **kwargs):
            captured.append(kwargs["client"].deadline_at)
            return {"search": {**metadata_search, "candidates": [], "fetch_failures": []},
                    "selection": {"event_baseline_ids": [], "same_event_ids": [],
                                  "assessments": [], "claim_comparisons": []}}

        with (patch("server.analysis.v2.pipeline.analyze_source_article", return_value=source_analysis()),
              patch("server.analysis.v2.pipeline.search_candidate_metadata", return_value=metadata_search) as search,
              patch("server.analysis.v2.pipeline.rank_comparison_candidates", return_value={"claim_candidates": []}),
              patch("server.analysis.v2.pipeline.select_verified_comparisons", side_effect=select)):
            result = run_discovery_pipeline(SOURCE_URL, client=recorder, _prepared_source=article(),
                                            _deadline_at=deadline)
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(len(captured), 1)
        self.assertLessEqual(captured[0], deadline - 75)
        self.assertEqual(recorder.deadline_at, deadline)
        self.assertEqual(search.call_args.kwargs["google_fallback_deadline_at"], deadline - 105)

    def test_verification_workers_keep_reserve_without_shortening_source_worker(self):
        raw = object.__new__(OpenRouterJSONClient)
        raw.model, raw.task_models = "synthetic-model", {}
        raw.generate_json = lambda **request: {"budget_ms": request["timeout_budget_ms"]}
        recorder = MetricsJSONClient(raw, deadline_at=monotonic() + 180)
        stage_deadline = monotonic() + 5

        with _scoped_model_deadline(recorder, stage_deadline) as verification:
            with ThreadPoolExecutor(max_workers=2) as executor:
                verify = executor.submit(verification.generate_json, name="verify_selected_candidate_bodies")
                source = executor.submit(recorder.generate_json, name="source_content_units")
                verify_budget = verify.result()["budget_ms"]
                source_budget = source.result()["budget_ms"]

        self.assertGreater(verify_budget, 3_000)
        self.assertLessEqual(verify_budget, 5_000)
        self.assertGreater(source_budget, 170_000)
        self.assertEqual(recorder.deadline_at, recorder._base_deadline_at)

    def test_shared_openrouter_recorder_bounds_total_provider_fanout(self):
        raw = object.__new__(OpenRouterJSONClient)
        raw.model, raw.task_models = "synthetic-model", {}
        gate, four_started, lock = Event(), Event(), Lock()
        active, peak = 0, 0

        def generate(**_request):
            nonlocal active, peak
            with lock:
                active += 1
                peak = max(peak, active)
                if active == 4:
                    four_started.set()
            try:
                self.assertTrue(gate.wait(2))
                return {}
            finally:
                with lock:
                    active -= 1

        raw.generate_json = generate
        recorder = MetricsJSONClient(raw, deadline_at=monotonic() + 10)
        with ThreadPoolExecutor(max_workers=5) as executor:
            futures = [executor.submit(recorder.generate_json, name="source_content_units")
                       for _ in range(5)]
            try:
                self.assertTrue(four_started.wait(2))
                with lock:
                    self.assertEqual(active, 4)
            finally:
                gate.set()
            for future in futures:
                future.result()
        self.assertEqual(peak, 4)

    def test_live_reader_source_prefetch_overlaps_discovery(self):
        raw = object.__new__(OpenRouterJSONClient)
        raw.model, raw.task_models = "synthetic-model", {}
        raw.temperature, raw.seed = 1.0, None
        raw.usage_summary = lambda: {}
        source_started, discovery_can_finish = Event(), Event()
        expected = ([{"sentence_index": 0, "scope": "central", "unit_ids": ["u1"]}],
                    [{"unit_id": "u1"}], 1)

        def extract(*_args):
            source_started.set()
            self.assertTrue(discovery_can_finish.wait(2))
            return expected

        def discover(_url, **kwargs):
            callback = kwargs["_after_candidate_search"]
            self.assertIsNotNone(callback)
            callback(article(), source_analysis())
            self.assertTrue(source_started.wait(2))
            discovery_can_finish.set()
            return {"completed_stage": 4, "execution_status": "success", "status": "ready",
                    "comparison_status": "available", "errors": [], "warnings": [],
                    "source_article": article(), "source_analysis": source_analysis(),
                    "search": {"candidates": []}}

        def reader(discovery, **kwargs):
            self.assertEqual(kwargs["_source_unit_future"].result(timeout=2), expected)
            return discovery

        with (patch("server.analysis.v2.pipeline.prepare_article", return_value=article()),
              patch("server.analysis.v2.pipeline.run_discovery_pipeline", side_effect=discover),
              patch("server.analysis.reader_analysis._extract_source_units", side_effect=extract),
              patch("server.analysis.v2.pipeline.complete_reader_analysis", side_effect=reader)):
            result = run_analysis_pipeline(SOURCE_URL, client=raw, use_cache=False,
                                           analysis_profile="reader")
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["execution_status"], "success")

    def test_failed_prefetch_preserves_verified_discovery_as_partial(self):
        future = Future()
        future.set_exception(TimeoutError("synthetic provider timeout"))
        discovery = {"completed_stage": 4, "execution_status": "success", "status": "ready",
                     "comparison_status": "available", "errors": [], "warnings": [],
                     "source_article": article(), "source_analysis": source_analysis(),
                     "search": {"candidates": []}, "display_selection": {"selected_article_ids": []}}
        result = complete_reader_analysis(discovery, _source_unit_future=future)
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(result["execution_status"], "partial")
        self.assertIsNone(result["reader_analysis"])


class DiscoveryStatusTests(SimpleTestCase):
    def _discover(self, metadata_search):
        with (patch("server.analysis.v2.pipeline.analyze_source_article", return_value=source_analysis()),
              patch("server.analysis.v2.pipeline.search_candidate_metadata", return_value=metadata_search)):
            return run_discovery_pipeline(SOURCE_URL, client=object(),
                                          _prepared_source=article())

    def test_successful_empty_search_is_no_matches(self):
        result = self._discover(empty_search())
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["status"], "no_candidates")
        self.assertEqual(result["comparison_status"], "no_matches")
        self.assertEqual(result["execution_status"], "success")
        self.assertEqual(result["errors"], [])

    def test_failed_search_is_unavailable_not_no_matches(self):
        result = self._discover(empty_search(failure=True))
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["status"], "search_failed")
        self.assertEqual(result["comparison_status"], "unavailable")
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual(result["errors"][0]["stage"], "search")

    def test_google_fallback_candidate_still_needs_body_and_same_event_verification(self):
        grounded = {"title": "다른 언론사 보도", "description": "",
                    "link": "https://other.example.com/report", "originallink": "",
                    "search_provided_at": "", "search_provider": "google_grounding"}
        ranking = {"claim_candidates": [{"claim_id": "C1", "selected_candidate_ids": ["A1"],
                                          "reserve_candidate_ids": [], "candidate_profiles": [
                                              {"candidate_id": "A1", "relevance": "direct", "focus": "계획"}]}]}
        fetched, verified = [], []

        def fetch(rows):
            fetched.extend(row["candidate_id"] for row in rows)
            return {"candidates": [article(row["candidate_id"]) for row in rows], "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            verified.extend(row["candidate_id"] for row in rows)
            return {"assessments": [{"candidate_id": row["candidate_id"],
                                     "event_relation": "same_event", "event_focus": "primary",
                                     "claim_matches": [{"claim_id": "C1", "relation": "same",
                                                        "evidence": {"sentence_index": 0, "quote": "새 계획"}}]}
                                    for row in rows]}

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.pipeline.analyze_source_article", return_value=source_analysis()),
              patch("server.analysis.v2.article_search.search_naver_news",
                    side_effect=OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", return_value=[grounded]),
              patch("server.analysis.v2.pipeline.rank_comparison_candidates", return_value=ranking),
              patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = run_discovery_pipeline(SOURCE_URL, client=object(), _prepared_source=article(),
                                            _deadline_at=monotonic() + 240)
        self.assertEqual(result["status"], "ready")
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual(fetched, ["A1"])
        self.assertEqual(verified, ["A1"])
        self.assertEqual(result["selection"]["same_event_ids"], ["A1"])
        self.assertEqual(result["search"]["metadata_candidates"][0]["search_matches"][0]["provider"],
                         "google_grounding")
        self.assertEqual(result["errors"][0]["http_status"], 429)

    def test_invalid_option_stops_before_source_or_model_calls(self):
        with patch("server.analysis.v2.pipeline.prepare_article") as prepare:
            result = run_discovery_pipeline(SOURCE_URL, max_per_claim=0, client=object())
        prepare.assert_not_called()
        self.assertEqual(result["completed_stage"], 0)
        self.assertEqual(result["execution_status"], "failed")
        self.assertEqual(result["errors"][0]["stage"], "input_validation")


class GroundingTests(SimpleTestCase):
    @staticmethod
    def _valid_source_response():
        return {
            "core_event": "시청의 계획 발표",
            "event_evidence": {"sentence_index": 0, "quote": "시청은 새 계획을 발표했다"},
            "event_query": "시청 계획 발표",
            "claims": [{"claim": "시청이 계획을 발표했다", "claim_kind": "reported_fact",
                        "speaker": "", "evidence": {"sentence_index": 0, "quote": "시청은 새 계획을 발표했다"},
                        "selection_basis": ["event_relevance"], "search_query": "시청 새 계획"}],
        }

    @staticmethod
    def _valid_scope_review():
        return {
            "primary_event": "시청의 계획 발표",
            "primary_anchor": {"scope": "title", "sentence_index": None, "quote": "새 계획 발표"},
            "proposed_event_relation": "same",
            "event_queries": [{"query": "시청 새 계획 발표",
                               "query_anchor": {"scope": "title", "sentence_index": None,
                                                "quote": "새 계획 발표"}}],
            "claim_scopes": {"C1": {"scope": "central", "reason": "제목의 계획 발표를 직접 설명한다",
                                    "scope_evidence": {"sentence_index": 0,
                                                       "quote": "시청은 새 계획을 발표했다"}}},
        }

    def test_source_schema_failure_retries_once_with_unchanged_article(self):
        client = SequenceClient([{"core_event": "필수 필드 누락"}, self._valid_source_response(),
                                 self._valid_scope_review()])

        result = analyze_source_article(article(), client=client)

        self.assertEqual(result["claims"][0]["claim_id"], "C1")
        self.assertEqual([request["name"] for request in client.requests],
                         ["source_event_claims_queries", "repair_source_event_claims_queries",
                          "review_source_event_scope"])
        self.assertEqual(client.requests[0]["input_data"], client.requests[1]["input_data"])

    def test_source_invalid_json_output_retries_once_without_reusing_bad_output(self):
        client = SequenceClient([ModelResponseError("SECRET_SENTINEL raw output"),
                                 self._valid_source_response(), self._valid_scope_review()])

        result = analyze_source_article(article(), client=client)

        self.assertEqual(result["claims"][0]["claim_id"], "C1")
        self.assertEqual(len(client.requests), 3)
        self.assertEqual(client.requests[0]["input_data"], client.requests[1]["input_data"])
        self.assertNotIn("previous_response", client.requests[1]["input_data"])

    def test_source_grounding_failure_retries_once_with_unchanged_article(self):
        invalid = self._valid_source_response()
        invalid["claims"][0]["evidence"]["quote"] = "원문에 없는 주장"
        client = SequenceClient([invalid, self._valid_source_response(), self._valid_scope_review()])

        result = analyze_source_article(article(), client=client)

        self.assertEqual(result["claims"][0]["claim_id"], "C1")
        self.assertEqual([request["name"] for request in client.requests],
                         ["source_event_claims_queries", "repair_source_event_claims_queries",
                          "review_source_event_scope"])
        self.assertEqual(client.requests[0]["input_data"], client.requests[1]["input_data"])

    def test_source_schema_then_grounding_failure_never_makes_third_call(self):
        invalid = self._valid_source_response()
        invalid["claims"][0]["evidence"]["quote"] = "원문에 없는 주장"
        client = SequenceClient([{"core_event": "필수 필드 누락"}, invalid])

        with self.assertRaises(ModelResponseError):
            analyze_source_article(article(), client=client)

        self.assertEqual(len(client.requests), 2)

    def test_scope_review_excludes_grounded_but_separate_second_story(self):
        body = ("영화 가을이 박스오피스 1위에 올랐다.\n\n"
                "한편 배우를 괴롭힌 사건이 경찰에 접수됐다.")
        source = {**article(body=body), "title": "영화 가을 박스오피스 1위",
                  **structure_article_body(body)}
        proposed = {
            "core_event": "영화 가을의 박스오피스 1위",
            "event_evidence": {"sentence_index": 0, "quote": "박스오피스 1위에 올랐다"},
            "event_query": "영화 가을 박스오피스 1위",
            "claims": [
                {"claim": "영화 가을이 박스오피스 1위에 올랐다", "claim_kind": "reported_fact",
                 "speaker": "", "evidence": {"sentence_index": 0, "quote": "박스오피스 1위에 올랐다"},
                 "selection_basis": ["event_relevance"], "search_query": "영화 가을 1위"},
                {"claim": "배우를 괴롭힌 사건이 경찰에 접수됐다", "claim_kind": "reported_fact",
                 "speaker": "", "evidence": {"sentence_index": 1, "quote": "경찰에 접수됐다"},
                 "selection_basis": ["event_relevance"], "search_query": "배우 경찰 사건"},
            ],
        }
        review = {
            "primary_event": "영화 가을의 박스오피스 1위",
            "primary_anchor": {"scope": "title", "sentence_index": None,
                               "quote": "영화 가을 박스오피스 1위"},
            "proposed_event_relation": "same",
            "event_queries": [{"query": "영화 가을 박스오피스 1위",
                               "query_anchor": {"scope": "title", "sentence_index": None,
                                                "quote": "영화 가을 박스오피스 1위"}},
                              {"query": "영화 가을 흥행 1위",
                               "query_anchor": {"scope": "body", "sentence_index": 0,
                                                "quote": "영화 가을이 박스오피스 1위에 올랐다"}}],
            "claim_scopes": {
                "C1": {"scope": "central", "reason": "제목의 영화 흥행 결과다",
                       "scope_evidence": {"sentence_index": 0, "quote": "박스오피스 1위에 올랐다"}},
                "C2": {"scope": "separate_topic", "reason": "영화 흥행과 별개의 경찰 사건이다",
                       "scope_evidence": {"sentence_index": 1, "quote": "경찰에 접수됐다"}},
            },
        }
        client = SequenceClient([proposed, review])

        result = analyze_source_article(source, client=client)

        self.assertEqual([claim["claim"] for claim in result["claims"]],
                         ["영화 가을이 박스오피스 1위에 올랐다"])
        self.assertEqual(result["source_scope_review"]["excluded_claim_count"], 1)
        self.assertEqual(result["source_scope_review"]["claim_decisions"][1]["scope"], "separate_topic")
        self.assertEqual(result["event_queries"], ["영화 가을 박스오피스 1위", "영화 가을 흥행 1위"])

    def test_scope_review_rejects_unverified_primary_event(self):
        review = self._valid_scope_review()
        review["proposed_event_relation"] = "uncertain"
        client = SequenceClient([self._valid_source_response(), review, review])
        with self.assertRaises(ModelResponseError):
            analyze_source_article(article(), client=client)
        self.assertEqual([request["name"] for request in client.requests],
                         ["source_event_claims_queries", "review_source_event_scope",
                          "repair_review_source_event_scope"])

    def test_invalid_optional_query_anchor_does_not_erase_verified_source_claim(self):
        review = self._valid_scope_review()
        review["event_queries"] = [{
            "query": "다른 화제 검색어",
            "query_anchor": {"scope": "body", "sentence_index": 1, "quote": "새 계획"},
        }]
        client = SequenceClient([self._valid_source_response(), review])

        result = analyze_source_article(article(), client=client)

        self.assertEqual(result["claims"][0]["claim_id"], "C1")
        self.assertEqual(result["event_queries"], ["새 계획 발표"])
        self.assertEqual(result["source_scope_review"]["rejected_event_query_count"], 1)

    def test_grounded_primary_event_correction_replans_once(self):
        body = ("영화 가을이 박스오피스 1위에 올랐다.\n\n"
                "한편 배우를 괴롭힌 사건이 경찰에 접수됐다.")
        source = {**article(body=body), "title": "영화 가을 박스오피스 1위",
                  **structure_article_body(body)}
        secondary_plan = {
            "core_event": "배우 관련 경찰 사건", "event_query": "배우 경찰 사건",
            "event_evidence": {"sentence_index": 1, "quote": "경찰에 접수됐다"},
            "claims": [{"claim": "배우 관련 사건이 경찰에 접수됐다", "claim_kind": "reported_fact",
                        "speaker": "", "evidence": {"sentence_index": 1, "quote": "경찰에 접수됐다"},
                        "selection_basis": ["event_relevance"], "search_query": "배우 경찰 사건"}],
        }
        primary_plan = {
            "core_event": "영화 가을의 박스오피스 1위", "event_query": "영화 가을 1위",
            "event_evidence": {"sentence_index": 0, "quote": "박스오피스 1위에 올랐다"},
            "claims": [{"claim": "영화 가을이 박스오피스 1위에 올랐다", "claim_kind": "reported_fact",
                        "speaker": "", "evidence": {"sentence_index": 0, "quote": "박스오피스 1위에 올랐다"},
                        "selection_basis": ["event_relevance"], "search_query": "영화 가을 1위"}],
        }
        review = {
            "primary_event": "영화 가을의 박스오피스 1위",
            "primary_anchor": {"scope": "title", "sentence_index": None,
                               "quote": "영화 가을 박스오피스 1위"},
            "event_queries": [{"query": "영화 가을 박스오피스 1위",
                               "query_anchor": {"scope": "title", "sentence_index": None,
                                                "quote": "영화 가을 박스오피스 1위"}}],
            "proposed_event_relation": "different",
            "claim_scopes": {"C1": {"scope": "separate_topic", "reason": "경찰 사건은 영화 흥행과 다르다",
                                    "scope_evidence": {"sentence_index": 1, "quote": "경찰에 접수됐다"}}},
        }
        second_review = deepcopy(review)
        second_review["proposed_event_relation"] = "same"
        second_review["claim_scopes"]["C1"] = {
            "scope": "central", "reason": "제목의 박스오피스 결과다",
            "scope_evidence": {"sentence_index": 0, "quote": "박스오피스 1위에 올랐다"}}
        client = SequenceClient([secondary_plan, review, primary_plan, second_review])

        result = analyze_source_article(source, client=client)

        self.assertEqual(result["core_event"], "영화 가을의 박스오피스 1위")
        self.assertEqual(result["event_query"], "영화 가을 박스오피스 1위")
        self.assertEqual([claim["claim"] for claim in result["claims"]],
                         ["영화 가을이 박스오피스 1위에 올랐다"])
        self.assertTrue(result["source_scope_review"]["replanned_after_event_correction"])
        self.assertEqual([request["name"] for request in client.requests],
                         ["source_event_claims_queries", "review_source_event_scope",
                          "replan_source_event_claims_queries", "review_source_event_scope"])
        self.assertEqual(client.requests[2]["input_data"]["primary_event_hint"]["event"],
                         "영화 가을의 박스오피스 1위")

    def test_scope_evidence_from_other_topic_cannot_confirm_claim(self):
        body = ("영화 가을이 박스오피스 1위에 올랐다.\n\n"
                "한편 배우를 괴롭힌 사건이 경찰에 접수됐다.")
        source = {**article(body=body), "title": "영화 가을 박스오피스 1위",
                  **structure_article_body(body)}
        plan = {
            "core_event": "영화 가을의 박스오피스 1위", "event_query": "영화 가을 1위",
            "event_evidence": {"sentence_index": 0, "quote": "박스오피스 1위에 올랐다"},
            "claims": [{"claim": "배우 관련 사건이 경찰에 접수됐다", "claim_kind": "reported_fact",
                        "speaker": "", "evidence": {"sentence_index": 1, "quote": "경찰에 접수됐다"},
                        "selection_basis": ["event_relevance"], "search_query": "배우 경찰 사건"}],
        }
        review = {
            "primary_event": "영화 가을의 박스오피스 1위",
            "primary_anchor": {"scope": "title", "sentence_index": None,
                               "quote": "영화 가을 박스오피스 1위"},
            "event_queries": [{"query": "영화 가을 박스오피스 1위",
                               "query_anchor": {"scope": "title", "sentence_index": None,
                                                "quote": "영화 가을 박스오피스 1위"}}],
            "proposed_event_relation": "same",
            "claim_scopes": {"C1": {"scope": "central", "reason": "영화 흥행과 관련 있다",
                                    "scope_evidence": {"sentence_index": 0,
                                                       "quote": "박스오피스 1위에 올랐다"}}},
        }
        client = SequenceClient([plan, review, review])

        with self.assertRaises(ModelResponseError):
            analyze_source_article(source, client=client)

        self.assertEqual([request["name"] for request in client.requests],
                         ["source_event_claims_queries", "review_source_event_scope",
                          "repair_review_source_event_scope"])

    def test_metadata_unrelated_label_remains_recoverable_reserve(self):
        metadata = [{"candidate_id": key, "title": key, "description": "",
                     "urls": [f"https://news.example.com/{key}"]} for key in ("A1", "A2", "A3")]
        profiles = [{"candidate_id": "A1", "relevance": "direct", "focus": "발표"},
                    {"candidate_id": "A2", "relevance": "context", "focus": "배경"},
                    {"candidate_id": "A3", "relevance": "unrelated", "focus": ""}]

        ranked = prioritize_diverse_candidates(metadata, profiles)

        self.assertEqual(ranked["ranked_candidate_ids"], ["A1", "A2", "A3"])

    def _response(self, *, event_quote, claim_quote):
        return {"assessments": {"A1": {
            "event_relation": "same_event",
            "event_evidence": {"sentence_index": 0, "quote": event_quote},
            "event_focus": "primary",
            "focus_evidence": {"sentence_index": 0, "quote": "시청은 새 계획을 발표했다"},
            "claim_matches": {"C1": {"relation": "same",
                                     "evidence": {"sentence_index": 0, "quote": claim_quote},
                                     "relation_evidence": []}},
        }}}

    def test_fabricated_event_evidence_withholds_positive_match(self):
        client = StubClient(self._response(event_quote="존재하지 않는 사건", claim_quote="새 계획"))
        result = verify_candidate_articles(article(), source_analysis(), [article("A1")], client=client)
        assessment = result["assessments"][0]
        self.assertEqual(client.calls, 1)
        self.assertEqual(assessment["event_relation"], "uncertain")
        self.assertEqual(assessment["claim_matches"], [])
        self.assertIn({"claim_id": None, "reason": "unverified_event"},
                      assessment["validation_warnings"])

    def test_fabricated_claim_quote_downgrades_relation(self):
        client = StubClient(self._response(event_quote="시청은 새 계획을 발표했다",
                                           claim_quote="존재하지 않는 주장"))
        result = verify_candidate_articles(article(), source_analysis(), [article("A1")], client=client)
        assessment = result["assessments"][0]
        self.assertEqual(assessment["event_relation"], "same_event")
        match = assessment["claim_matches"][0]
        self.assertEqual(match["relation"], "uncertain")
        self.assertIsNone(match["evidence"])
        self.assertEqual(match["proposed_relation"], "same")
        self.assertIn({"claim_id": "C1", "reason": "invalid_claim_evidence"},
                      assessment["validation_warnings"])

    def test_ambiguous_repeated_quote_downgrades_claim_relation(self):
        candidate = article("A1", "시청은 새 계획을 발표했다. 이 계획은 내일 시행된다.")
        client = StubClient(self._response(event_quote="시청은 새 계획을 발표했다",
                                           claim_quote="계획"))
        assessment = verify_candidate_articles(article(), source_analysis(),
                                               [candidate], client=client)["assessments"][0]
        self.assertEqual(assessment["event_relation"], "same_event")
        self.assertEqual(assessment["claim_matches"][0]["relation"], "uncertain")
        self.assertEqual(assessment["claim_matches"][0]["validation_warning"],
                         "invalid_claim_evidence")

    def test_missing_candidate_id_in_model_response_is_rejected(self):
        client = StubClient({"assessments": {}})
        with self.assertRaises(ModelResponseError):
            verify_candidate_articles(article(), source_analysis(), [article("A1")], client=client)
        self.assertEqual(client.calls, 2)  # One fixed-input schema repair, then reject.

    def test_truncated_candidate_response_is_not_repeated_at_same_limit(self):
        client = SequenceClient([ModelResponseTruncatedError("synthetic truncation")])
        with self.assertRaises(ModelResponseTruncatedError):
            verify_candidate_articles(article(), source_analysis(), [article("A1")], client=client)
        self.assertEqual(len(client.requests), 1)
        self.assertEqual(client.requests[0]["name"], "verify_selected_candidate_bodies")
        self.assertEqual(client.requests[0]["max_output_tokens"], 16384)

    def test_passing_mention_cannot_become_same_event_comparison(self):
        body = "시청은 가을 축제를 개최했다.\n새 계획도 발표했다고 짧게 언급했다."
        candidate = {**article("A1", body=body), "title": "시청 가을 축제 개최",
                     **structure_article_body(body)}
        client = StubClient({"assessments": {"A1": {
            "event_relation": "same_event",
            "event_evidence": {"sentence_index": 1, "quote": "새 계획도 발표했다고"},
            "event_focus": "passing_mention",
            "focus_evidence": {"sentence_index": 0, "quote": "가을 축제를 개최했다"},
            "claim_matches": {"C1": {"relation": "same",
                                     "evidence": {"sentence_index": 1, "quote": "새 계획도 발표했다고"},
                                     "relation_evidence": []}},
        }}})

        assessment = verify_candidate_articles(article(), source_analysis(),
                                               [candidate], client=client)["assessments"][0]

        self.assertEqual(assessment["event_relation"], "different_event")
        self.assertEqual(assessment["claim_matches"], [])
        self.assertIn({"claim_id": None, "reason": "insufficient_event_focus"},
                      assessment["validation_warnings"])


class CandidateSearchTests(SimpleTestCase):
    def test_naver_article_variants_share_identity_but_other_query_ids_remain(self):
        self.assertEqual(normalize_news_url("https://n.news.naver.com/article/001/0000001?utm_source=x"),
                         normalize_news_url("https://news.naver.com/main/read.naver?oid=001&aid=0000001"))
        self.assertNotEqual(normalize_news_url("https://news.example.com/story?id=1&utm_source=x"),
                            normalize_news_url("https://news.example.com/story?id=2"))

    def test_source_alias_is_excluded_and_cross_query_candidate_is_merged(self):
        source = article()
        analysis = source_analysis()
        analysis["event_query"] = "시청 발표"
        analysis["claims"][0]["search_query"] = "새 계획"
        shared = {"title": "계획", "description": "새 계획", "link": "https://news.example.com/shared?utm_source=naver",
                  "originallink": "https://news.example.com/shared", "search_provided_at": ""}
        by_query = {
            "시청 발표": [{"title": "원문", "description": "", "link": SOURCE_URL,
                       "originallink": SOURCE_URL, "search_provided_at": ""}, shared],
            "새 계획": [shared],
        }
        with patch("server.analysis.v2.article_search.search_naver_news",
                   side_effect=lambda query, _display: by_query[query]):
            result = search_candidate_metadata(source, analysis)
        self.assertEqual(result["search_status"], "success")
        self.assertEqual(result["discovered_count"], 1)
        self.assertEqual(len(result["metadata_candidates"]), 1)
        candidate = result["metadata_candidates"][0]
        self.assertEqual(candidate["candidate_id"], "A1")
        self.assertTrue(candidate["discovered_by_event"])
        self.assertEqual({row["scope"] for row in candidate["search_matches"]}, {"event", "claim"})

    def test_one_failed_query_retains_successful_candidates_with_partial_status(self):
        analysis = source_analysis()
        analysis["event_query"] = "시청 발표"
        analysis["claims"][0]["search_query"] = "새 계획"
        def search(query, _display):
            if query == "새 계획":
                raise OperationError("검색 시간 초과", code="SEARCH_TIMEOUT", retryable=True)
            return [{"title": "후보", "description": "계획", "link": "https://news.example.com/other",
                     "originallink": "", "search_provided_at": ""}]
        with patch("server.analysis.v2.article_search.search_naver_news", side_effect=search):
            result = search_candidate_metadata(article(), analysis)
        self.assertEqual(result["search_status"], "partial")
        self.assertEqual([row["candidate_id"] for row in result["metadata_candidates"]], ["A1"])
        self.assertEqual(result["errors"][0]["code"], "SEARCH_TIMEOUT")

    def test_candidate_fetch_tries_second_url_and_keeps_stable_id(self):
        metadata = [{"candidate_id": "A7", "urls": ["https://news.example.com/broken",
                    "https://news.example.com/working"], "title": "대체 제목", "description": "설명",
                    "search_provided_at": "", "discovered_by_event": True, "search_matches": []}]
        def fetch(url):
            if url.endswith("broken"):
                raise OperationError("수집 실패", code="ARTICLE_FETCH_FAILED")
            return article("unused")
        with patch("server.analysis.v2.article_search.prepare_article", side_effect=fetch):
            result = fetch_candidate_articles(metadata)
        self.assertEqual(result["fetch_attempt_count"], 1)
        self.assertEqual(result["fetch_failures"], [])
        self.assertEqual(result["candidates"][0]["candidate_id"], "A7")
        self.assertEqual(result["candidates"][0]["fetch_attempt_errors"][0]["code"],
                         "ARTICLE_FETCH_FAILED")


class CandidateRefillTests(SimpleTestCase):
    @staticmethod
    def _two_candidate_case():
        metadata = [{"candidate_id": key, "title": f"{key} 기사",
                     "description": "계획 발표", "urls": [f"https://news.example.com/{key}"],
                     "discovered_by_event": False} for key in ("A1", "A2")]
        ranking = {"claim_candidates": [{"claim_id": "C1", "selected_candidate_ids": ["A1", "A2"],
                                          "reserve_candidate_ids": [],
                                          "candidate_profiles": [
                                              {"candidate_id": key, "relevance": "direct", "focus": key}
                                              for key in ("A1", "A2")]}]}
        return {**empty_search(), "metadata_candidates": metadata}, ranking

    def test_single_candidate_batches_keep_both_grounded_comparisons(self):
        metadata_search, ranking = self._two_candidate_case()
        requests = []

        def fetch(rows):
            return {"candidates": [article(row["candidate_id"]) for row in rows], "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            requests.append([row["candidate_id"] for row in rows])
            key = rows[0]["candidate_id"]
            return {"assessments": [{"candidate_id": key, "event_relation": "same_event",
                                     "event_focus": "primary", "claim_matches": [{"claim_id": "C1",
                                     "relation": "same", "evidence": {"sentence_index": 0,
                                     "quote": "새 계획"}}]}]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(article(), source_analysis(), metadata_search, ranking,
                                                 max_per_claim=2, event_baseline_limit=0,
                                                 verification_batch_size=1, client=object())
        self.assertEqual(requests, [["A1"], ["A2"]])
        self.assertEqual(result["selection"]["verification_llm_calls"], 2)
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A1", "A2"])
        self.assertEqual(result["search"]["errors"], [])

    def test_truncated_batch_splits_and_keeps_successful_candidates(self):
        metadata_search, ranking = self._two_candidate_case()
        requests = []

        def fetch(rows):
            return {"candidates": [article(row["candidate_id"]) for row in rows], "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            ids = [row["candidate_id"] for row in rows]
            requests.append(ids)
            if len(ids) > 1:
                raise ModelResponseTruncatedError("synthetic truncation")
            key = ids[0]
            return {"assessments": [{"candidate_id": key, "event_relation": "same_event",
                                     "event_focus": "primary", "claim_matches": [{"claim_id": "C1",
                                     "relation": "same", "evidence": {"sentence_index": 0,
                                     "quote": "새 계획"}}]}]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(article(), source_analysis(), metadata_search, ranking,
                                                 max_per_claim=2, event_baseline_limit=0, client=object())
        self.assertEqual(requests, [["A1", "A2"], ["A1"], ["A2"]])
        self.assertEqual(result["selection"]["verification_llm_calls"], 3)
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A1", "A2"])
        self.assertEqual(result["search"]["errors"], [])

    def test_truncated_batch_isolates_failed_candidate(self):
        metadata_search, ranking = self._two_candidate_case()
        requests = []

        def fetch(rows):
            return {"candidates": [article(row["candidate_id"]) for row in rows], "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            ids = [row["candidate_id"] for row in rows]
            requests.append(ids)
            if len(ids) > 1 or ids == ["A2"]:
                raise ModelResponseTruncatedError("synthetic truncation")
            return {"assessments": [{"candidate_id": "A1", "event_relation": "same_event",
                                     "event_focus": "primary", "claim_matches": [{"claim_id": "C1",
                                     "relation": "same", "evidence": {"sentence_index": 0,
                                     "quote": "새 계획"}}]}]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(article(), source_analysis(), metadata_search, ranking,
                                                 max_per_claim=2, event_baseline_limit=0, client=object())
        self.assertEqual(requests, [["A1", "A2"], ["A1"], ["A2"]])
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A1"])
        self.assertEqual([(row["candidate_id"], row["code"]) for row in result["search"]["errors"]],
                         [("A2", "ANALYSIS_RESPONSE_INVALID")])
        self.assertEqual(next(row for row in result["selection"]["assessments"]
                              if row["candidate_id"] == "A2")["verification_status"], "unverified")

    def test_truncated_batch_defers_singletons_after_deadline(self):
        metadata_search, ranking = self._two_candidate_case()
        state = {"stop": False, "calls": 0}

        def fetch(rows):
            return {"candidates": [article(row["candidate_id"]) for row in rows], "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            state["calls"] += 1
            state["stop"] = True
            raise ModelResponseTruncatedError("synthetic truncation")

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(article(), source_analysis(), metadata_search, ranking,
                                                 max_per_claim=2, event_baseline_limit=0, client=object(),
                                                 should_stop=lambda: state["stop"])
        self.assertEqual(state["calls"], 1)
        self.assertEqual(result["selection"]["verification_llm_calls"], 1)
        self.assertEqual([row["candidate_id"] for row in result["search"]["errors"]], ["A1", "A2"])
        self.assertTrue(all(row["reason"] == "verification_deferred"
                            for row in result["selection"]["claim_attempts"]))

    def test_truncated_batch_rate_limit_defers_remaining_and_following_batch(self):
        metadata = [{"candidate_id": key, "title": f"{key} 기사", "description": "계획 발표",
                     "urls": [f"https://news.example.com/{key}"],
                     "discovered_by_event": key != "A1"} for key in ("A1", "A2", "A3", "A4")]
        metadata_search = {**empty_search(), "metadata_candidates": metadata}
        ranking = {"claim_candidates": [{"claim_id": "C1", "selected_candidate_ids": ["A1"],
                                          "reserve_candidate_ids": ["A2", "A3", "A4"],
                                          "candidate_profiles": [{"candidate_id": key, "relevance": "direct",
                                                                  "focus": "계획"} for key in
                                                                 ("A1", "A2", "A3", "A4")]}]}
        requests = []

        def fetch(rows):
            return {"candidates": [article(row["candidate_id"]) for row in rows], "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            ids = [row["candidate_id"] for row in rows]
            requests.append(ids)
            if len(ids) > 1:
                raise ModelResponseTruncatedError("synthetic truncation")
            if ids == ["A1"]:
                raise ModelHTTPError(429)
            self.fail("Rate limit should defer remaining candidates")

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(article(), source_analysis(), metadata_search, ranking,
                                                 max_per_claim=1, event_baseline_limit=3,
                                                 verification_batch_size=3, client=object())
        self.assertEqual(requests, [["A1", "A2", "A3"], ["A1"]])
        self.assertEqual(result["selection"]["verification_llm_calls"], 2)
        self.assertEqual([(row["candidate_id"], row["code"]) for row in result["search"]["errors"]],
                         [(key, "MODEL_RATE_LIMITED") for key in ("A1", "A2", "A3", "A4")])
        self.assertEqual(result["selection"]["verification_rounds"][0]["deferred_ids"],
                         ["A2", "A3", "A4"])

    def test_timeout_at_candidate_deadline_is_deferred(self):
        metadata_search, ranking = self._two_candidate_case()
        state = {"stop": False}

        def fetch(rows):
            return {"candidates": [article(row["candidate_id"]) for row in rows], "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            state["stop"] = True
            raise TimeoutError("synthetic phase deadline")

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(article(), source_analysis(), metadata_search, ranking,
                                                 max_per_claim=2, event_baseline_limit=0, client=object(),
                                                 should_stop=lambda: state["stop"])
        self.assertEqual(result["selection"]["verification_llm_calls"], 1)
        self.assertEqual(result["selection"]["verification_rounds"][0]["deferred_ids"], ["A1", "A2"])
        self.assertTrue(all(row["reason"] == "verification_deferred"
                            for row in result["selection"]["claim_attempts"]))

    def test_rejected_initial_candidate_is_replaced_by_verified_reserve(self):
        metadata = [{"candidate_id": candidate_id, "title": f"{candidate_id} 기사",
                     "description": "계획 발표", "urls": [f"https://news.example.com/{candidate_id}"],
                     "discovered_by_event": False} for candidate_id in ("A1", "A2")]
        metadata_search = {**empty_search(), "metadata_candidates": metadata}
        ranking = {"claim_candidates": [{"claim_id": "C1", "selected_candidate_ids": ["A1"],
                                          "reserve_candidate_ids": ["A2"],
                                          "candidate_profiles": [
                                              {"candidate_id": key, "relevance": "direct", "focus": key}
                                              for key in ("A1", "A2")]}]}
        fetched = []

        def fetch(rows):
            fetched.extend(row["candidate_id"] for row in rows)
            return {"candidates": [article(row["candidate_id"]) for row in rows],
                    "fetch_failures": []}

        def verify(_source, _analysis, rows, *, client):
            assessments = []
            for row in rows:
                key = row["candidate_id"]
                match = ({"claim_id": "C1", "relation": "same_issue",
                          "evidence": {"sentence_index": 0, "quote": "새 계획"}}
                         if key == "A2" else {"claim_id": "C1", "relation": "not_found", "evidence": None})
                assessments.append({"candidate_id": key,
                                    "event_relation": "same_event" if key == "A2" else "different_event",
                                    "claim_matches": [match]})
            return {"assessments": assessments}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(article(), source_analysis(), metadata_search, ranking,
                                                 max_per_claim=1, event_baseline_limit=0, client=object())
        selected = result["selection"]["claim_comparisons"][0]["articles"]
        self.assertEqual(fetched, ["A1", "A2"])
        self.assertEqual([item["candidate_id"] for item in selected], ["A2"])
        self.assertEqual(selected[0]["relation"], "same_issue")
        self.assertEqual(result["selection"]["same_event_ids"], ["A2"])
        self.assertEqual([row["reason"] for row in result["selection"]["claim_attempts"]],
                         ["different_event", "verified"])


class RubricRecoveryTests(SimpleTestCase):
    def test_rubric_timeout_preserves_verified_stage_four_comparison(self):
        discovery = {
            "completed_stage": 4, "status": "ready", "comparison_status": "available",
            "execution_status": "success", "errors": [], "warnings": [],
            "source_article": article(), "source_analysis": source_analysis(),
            "search": {**empty_search(), "candidates": [article("A1")], "fetch_failures": []},
            "selection": {"claim_comparisons": [{"claim_id": "C1", "articles": [
                {"candidate_id": "A1", "relation": "same", "evidence": {"quote": "새 계획"}}]}],
                "same_event_ids": ["A1"], "event_baseline_ids": ["A1"], "assessments": []},
        }
        with patch("server.analysis.v2.pipeline.analyze_selected_articles",
                   side_effect=TimeoutError("rubric model timed out")):
            result = complete_rubric_analysis(discovery, client=object())
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual(result["errors"][0]["stage"], "rubric_analysis")
        self.assertEqual(result["selection"], discovery["selection"])

    def test_information_disagreement_keeps_quote_but_withholds_relation(self):
        context = _verified_comparison_context(article("A1"), source_analysis()["claims"], [
            {"claim_id": "C1", "relation": "same", "relation_evidence": [],
             "evidence": {"sentence_index": 0, "quote": "새 계획"}},
        ])
        annotation = {"claim_coverage": [{"claim_id": "C1", "relation": "not_found",
                                          "mentions": []}]}
        result = retain_verified_comparison_context(annotation, context)
        coverage = result["claim_coverage"][0]
        self.assertEqual(coverage["verification_context"][0]["quote"], "새 계획")
        self.assertEqual(coverage["information_relation"], "not_found")
        self.assertEqual(coverage["relation"], "uncertain")
        self.assertEqual(result["coverage_diagnostics"][0]["verification_relation"], "same")

    def test_rubric_refuses_article_outside_verified_same_event_set(self):
        discovery = {
            "source_article": article(), "source_analysis": source_analysis(),
            "search": {"candidates": [article("A1")]},
            "selection": {"claim_comparisons": [{"claim_id": "C1", "articles": [
                {"candidate_id": "A1", "relation": "same", "evidence": {"quote": "새 계획"}}]}],
                "same_event_ids": [], "event_baseline_ids": [], "assessments": []},
        }
        with self.assertRaisesRegex(ValueError, "동일 사건으로 확인된 후보"):
            analyze_selected_articles(discovery, client=object())

    def test_retry_clears_only_resolved_rubric_error(self):
        discovery = {
            "completed_stage": 4, "status": "no_candidates", "comparison_status": "no_matches",
            "execution_status": "partial", "errors": [
                make_error_record(OperationError("루브릭 실패", code="MODEL_TIMEOUT"),
                                  stage="rubric_analysis")],
            "warnings": [], "source_article": article(), "source_analysis": source_analysis(),
            "search": {**empty_search(), "candidates": [], "fetch_failures": []},
            "selection": {"claim_comparisons": [{"claim_id": "C1", "articles": []}],
                          "same_event_ids": [], "event_baseline_ids": [], "assessments": []},
        }
        with patch("server.analysis.v2.pipeline.analyze_selected_articles",
                   return_value={"warnings": ["근거를 재확인하세요"], "claim_analyses": []}):
            result = complete_rubric_analysis(discovery, client=object())
        self.assertEqual(result["completed_stage"], 5)
        self.assertEqual(result["execution_status"], "success")
        self.assertEqual(result["comparison_status"], "no_matches")
        self.assertEqual(result["errors"], [])
        self.assertEqual(result["resolved_errors"][0]["stage"], "rubric_analysis")
        self.assertEqual(result["warnings"], ["근거를 재확인하세요"])


class ExpressionBoundaryTests(SimpleTestCase):
    def test_direct_speech_is_excluded_but_reporter_verb_can_be_scored(self):
        source = article(body="시장은 계획을 개선하겠다고 밝혔다.")
        candidate = article("A1", body="시장은 “계획을 개선하겠다”고 말했다.")
        claim = {"claim_id": "C1", "claim": "시장이 계획을 개선하겠다고 밝혔다",
                 "evidence": {"sentence_index": 0, "quote": source["body"]}}
        source_mention = {"scope": "body", "sentence_index": 0, "quote": source["body"]}
        candidate_mention = {"scope": "body", "sentence_index": 0, "quote": candidate["body"]}
        speech = "계획을 개선하겠다"
        speech_start = candidate["body"].index(speech)
        direct_voice = {"claim_ids": ["C1"], "speech_kind": "direct", "speaker": "시장",
                        "speech_evidence": {"scope": "body", "sentence_index": 0, "quote": speech},
                        "direct_quote_range": {"scope": "body", "start": speech_start,
                                               "end": speech_start + len(speech)}}
        def annotation(article_id, mention, voices):
            return {"article_id": article_id, "claim_coverage": [{
                "claim_id": "C1", "relation": "same", "mentions": [mention],
                "headline_evidence": None, "verification_context": []}],
                "backgrounds": [], "forecasts": [], "voices": voices}
        annotations = {"S0": annotation("S0", source_mention, []),
                       "A1": annotation("A1", candidate_mention, [direct_voice])}
        def finding(*, quote, marker, baseline_quote, baseline_marker, voice, quoted_use,
                    category, markedness):
            return {"article_id": "A1", "evidence": {"scope": "body", "sentence_index": 0, "quote": quote},
                    "marker": marker, "category": category, "target": "계획", "polarity": "none",
                    "voice": voice, "quoted_use": quoted_use, "name_kind": "other",
                    "baseline_article_id": "S0", "baseline_evidence": {"scope": "body",
                        "sentence_index": 0, "quote": baseline_quote},
                    "baseline_marker": baseline_marker, "semantic_relation": "equivalent",
                    "markedness": markedness, "markedness_reason": "실제 원문 표현을 비교한 상대 판단",
                    "description": "모형이 제안한 표현 차이"}
        proposed = [
            finding(quote=speech, marker="개선하겠다", baseline_quote="계획을 개선하겠다고",
                    baseline_marker="개선하겠다고", voice="direct_quote", quoted_use="speech",
                    category="C", markedness=2),
            finding(quote="말했다", marker="말했다", baseline_quote="밝혔다",
                    baseline_marker="밝혔다", voice="reporter", quoted_use="none",
                    category="A", markedness=0),
            finding(quote="시장은", marker="시장은", baseline_quote="시장은",
                    baseline_marker="시장은", voice="direct_quote", quoted_use="speech",
                    category="B", markedness=2),
        ]
        result = compare_claim_expressions(claim, {"S0": source, "A1": candidate}, annotations,
                                           client=StubClient({"findings": proposed}))
        self.assertEqual(len(result["excluded"]), 1)
        self.assertEqual(result["excluded"][0]["reason"], "direct_quote_recorded_in_I5")
        self.assertIsNone(result["excluded"][0]["markedness"])
        self.assertEqual([(row["category"], row["markedness"]) for row in result["findings"]],
                         [("A", 0)])
        self.assertEqual([row["reason"] for row in result["invalid_findings"]],
                         ["direct_speech_range_not_grounded"])


class FullRubricPathTests(SimpleTestCase):
    @staticmethod
    def _span(quote, *, scope="body"):
        return {"scope": scope, "sentence_index": 0 if scope == "body" else None,
                "quote": quote}

    @classmethod
    def _information(cls, *, source):
        lead = "시청은 새 계획을 발표했다"
        response = {
            "lead_evidence": cls._span(lead),
            "claim_coverage": [{"claim_id": "C1", "relation": "same",
                                "mentions": [cls._span(lead)],
                                "headline_evidence": cls._span("새 계획 발표", scope="title"),
                                "relation_evidence": [{"relation": "same", "relation_owner": "reporter",
                                                       "speaker": "", "speech_kind": "none",
                                                       "evidence": cls._span(lead),
                                                       "attribution_evidence": None}]}],
            "backgrounds": [], "forecasts": [], "voices": [],
        }
        if source:
            response["backgrounds"] = [{"claim_ids": ["C1"], "content": "예산 확보",
                                        "target": "계획", "polarity": "positive",
                                        "evidence": cls._span("예산은 확보됐다")}]
        else:
            response["forecasts"] = [{"claim_ids": ["C1"], "content": "다음 달 시행 가능성",
                                      "evidence": cls._span("다음 달 시행할 수 있다"),
                                      "speculation_markers": [cls._span("수 있다")]}]
            response["voices"] = [{"claim_ids": ["C1"], "speaker": "시장", "affiliation": "",
                                   "identity_kind": "unknown", "speech_kind": "direct",
                                   "speech_evidence": cls._span("다음 달 시행할 수 있다"),
                                   "attribution_evidence": cls._span("시장은"),
                                   "identity_evidence": None, "affiliation_evidence": None}]
        return response

    def _case(self):
        source = article(body="시청은 새 계획을 발표했다; 예산은 확보됐다.")
        candidate = article("A1", body="시청은 새 계획을 발표했다; 시장은 “다음 달 시행할 수 있다”고 말했다.")
        match = {"claim_id": "C1", "relation": "same",
                 "evidence": {"sentence_index": 0, "quote": "시청은 새 계획을 발표했다"},
                 "relation_evidence": []}
        discovery = {"source_article": source, "source_analysis": source_analysis(),
                     "search": {"candidates": [candidate]},
                     "selection": {"claim_comparisons": [{"claim_id": "C1", "articles": [
                         {"candidate_id": "A1", "title": candidate["title"],
                          "url": candidate["url"], "relation": "same",
                          "evidence": match["evidence"]}]}],
                         "same_event_ids": ["A1"], "event_baseline_ids": ["A1"],
                         "assessments": [{"candidate_id": "A1", "claim_matches": [match]}]}}
        client = RubricQueueClient({"S0": self._information(source=True),
                                   "A1": self._information(source=False)}, {"findings": []})
        return discovery, client

    def _two_claim_case(self):
        discovery, client = self._case()
        second_claim = deepcopy(discovery["source_analysis"]["claims"][0])
        second_claim["claim_id"] = "C2"
        discovery["source_analysis"]["claims"].append(second_claim)
        second_comparison = deepcopy(discovery["selection"]["claim_comparisons"][0])
        second_comparison["claim_id"] = "C2"
        discovery["selection"]["claim_comparisons"].append(second_comparison)
        second_match = deepcopy(discovery["selection"]["assessments"][0]["claim_matches"][0])
        second_match["claim_id"] = "C2"
        discovery["selection"]["assessments"][0]["claim_matches"].append(second_match)
        for information in client.information.values():
            second_coverage = deepcopy(information["claim_coverage"][0])
            second_coverage["claim_id"] = "C2"
            information["claim_coverage"].append(second_coverage)
        return discovery, client

    def test_full_stage_five_assembles_i1_through_i5_and_a_through_g(self):
        discovery, client = self._case()
        rubric = analyze_selected_articles(discovery, client=client)
        self.assertEqual(client.calls, [("article_information_I1_I5", "S0"),
                                        ("article_information_I1_I5", "A1"),
                                        ("claim_expression_A_G", None)])
        self.assertEqual(rubric["llm_calls"], 3)
        claim = rubric["claim_analyses"][0]
        self.assertEqual(claim["selected_article_ids"], ["A1"])
        self.assertEqual([row["presence"] for row in claim["information"]["I1"]],
                         ["found", "found"])
        self.assertEqual(claim["information_by_article"]["S0"]["I2"]["body_mention_count"], 1)
        self.assertEqual(len(claim["information_by_article"]["S0"]["I3"]["items"]), 1)
        self.assertEqual(len(claim["information_by_article"]["A1"]["I4"]["items"]), 1)
        self.assertEqual(claim["information_by_article"]["A1"]["I5"]["direct_count"], 1)
        self.assertEqual(set(claim["expression_by_article"]["A1"]), set("ABCDEFG"))
        self.assertEqual(rubric["rubric"]["judgment_status"], "provisional")
        self.assertFalse(rubric["rubric"]["aggregate_article_score"])

    def test_invalid_article_quote_is_repaired_once_before_rubric_assembly(self):
        discovery, client = self._case()
        client.repair_information["A1"] = deepcopy(client.information["A1"])
        client.information["A1"]["forecasts"][0]["evidence"]["quote"] = "원문에 없는 전망"
        token = analysis_id.set("safe-diagnostic-123")
        try:
            with self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING") as captured:
                rubric = analyze_selected_articles(discovery, client=client)
        finally:
            analysis_id.reset(token)
        self.assertEqual(rubric["llm_calls"], 4)
        self.assertEqual(client.calls[2], ("repair_article_information_I1_I5", "A1"))
        self.assertEqual(len(client.requests[2]["input_data"]["grounding_errors"]), 1)
        self.assertEqual(rubric["article_annotations"]["A1"]["llm_calls"], 2)
        self.assertEqual(len(rubric["claim_analyses"][0]["information_by_article"]["A1"]["I4"]["items"]), 1)
        self.assertIn('"analysis_id":"safe-diagnostic-123"', captured.output[0])
        self.assertIn('"attempt":"initial"', captured.output[0])
        self.assertIn('"reason_code":"QUOTE_NOT_UNIQUE"', captured.output[0])
        self.assertNotIn("원문에 없는 전망", " ".join(captured.output))

    def test_information_schema_failure_uses_one_fixed_input_repair(self):
        discovery, client = self._case()
        client.repair_information["A1"] = deepcopy(client.information["A1"])
        client.information["A1"] = {"lead_evidence": None, "SECRET_SENTINEL": "model text"}

        with self.assertLogs("server.analysis.v2.llm_analysis", level="WARNING") as captured:
            rubric = analyze_selected_articles(discovery, client=client)

        self.assertEqual(rubric["rubric"]["annotation_status"], "complete")
        self.assertEqual(rubric["article_annotations"]["A1"]["llm_calls"], 2)
        self.assertEqual(rubric["llm_calls"], 4)
        first, repair = client.requests[1:3]
        self.assertEqual(first["name"], "article_information_I1_I5")
        self.assertEqual(repair["name"], "repair_article_information_I1_I5")
        self.assertEqual(repair["input_data"], first["input_data"])
        self.assertNotIn("previous_response", repair["input_data"])
        self.assertIn("오류 종류=required", repair["instructions"])
        self.assertIn("스키마 위치=<root>", repair["instructions"])
        self.assertNotIn("SECRET_SENTINEL", repair["instructions"])
        self.assertIn('"reason_code":"SCHEMA_INVALID"', captured.output[0])
        self.assertIn('"attempt":"initial"', captured.output[0])
        self.assertNotIn("SECRET_SENTINEL", " ".join(captured.output))

    def test_schema_repair_then_invalid_evidence_does_not_trigger_third_call(self):
        discovery, client = self._case()
        client.repair_information["A1"] = deepcopy(client.information["A1"])
        client.repair_information["A1"]["forecasts"][0]["evidence"]["quote"] = "원문에 없는 전망"
        client.information["A1"] = {"lead_evidence": None}

        with self.assertLogs("server.analysis.v2", level="WARNING") as captured:
            rubric = analyze_selected_articles(discovery, client=client)

        self.assertEqual([name for name, article_id in client.calls if article_id == "A1"],
                         ["article_information_I1_I5", "repair_article_information_I1_I5"])
        self.assertEqual(rubric["article_annotation_failures"][0]["article_id"], "A1")
        self.assertNotIn("A1", rubric["article_annotations"])
        logs = " ".join(captured.output)
        self.assertIn('"reason_code":"SCHEMA_INVALID"', logs)
        self.assertIn('"reason_code":"QUOTE_NOT_UNIQUE"', logs)
        self.assertIn('"attempt":"repair"', logs)

    def test_source_schema_failure_remains_explicit_and_fatal_to_rubric(self):
        discovery, client = self._case()
        client.information["S0"] = {"lead_evidence": None}
        client.repair_information["S0"] = {"lead_evidence": None}
        discovery.update(completed_stage=4, status="ready", comparison_status="available",
                         execution_status="success", errors=[], warnings=[])

        with self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING") as captured:
            result = complete_rubric_analysis(discovery, client=client)

        self.assertEqual(result["execution_status"], "partial")
        self.assertIsNone(result["rubric_analysis"])
        self.assertEqual(result["errors"][0]["code"], "ANALYSIS_RESPONSE_INVALID")
        self.assertEqual(client.calls, [("article_information_I1_I5", "S0"),
                                        ("repair_article_information_I1_I5", "S0")])
        self.assertIn("rubric_source_annotation_failed article_id=S0", captured.output[0])

    def test_related_model_outage_isolated_from_source_information(self):
        discovery, client = self._case()
        original = client.generate_json

        def fail_related(**request):
            if (request["name"] == "article_information_I1_I5"
                    and request["input_data"]["article_id"] == "A1"):
                raise ModelUnavailableError("SECRET_SENTINEL provider response")
            return original(**request)

        client.generate_json = fail_related
        with self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING") as captured:
            rubric = analyze_selected_articles(discovery, client=client)

        self.assertEqual(set(rubric["article_annotations"]), {"S0"})
        self.assertEqual(rubric["article_annotation_failures"][0]["article_id"], "A1")
        self.assertEqual(rubric["article_annotation_failures"][0]["reason"], "model_unavailable")
        self.assertTrue(rubric["article_annotation_failures"][0]["retryable"])
        self.assertEqual([row["article_id"] for row in rubric["claim_analyses"][0]["information"]["I1"]],
                         ["S0"])
        self.assertNotIn("SECRET_SENTINEL", " ".join(captured.output + rubric["warnings"]))

    def test_expression_schema_failure_isolated_per_claim_after_one_repair(self):
        discovery, client = self._two_claim_case()
        original = client.generate_json
        expression_requests = []

        def fail_first_expression(**request):
            if request["name"].endswith("claim_expression_A_G"):
                expression_requests.append(deepcopy(request))
                return {"findings": "invalid"} if request["input_data"]["claim"]["claim_id"] == "C1" else {"findings": []}
            return original(**request)

        client.generate_json = fail_first_expression
        with self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING"):
            rubric = analyze_selected_articles(discovery, client=client)

        self.assertEqual([request["name"] for request in expression_requests],
                         ["claim_expression_A_G", "repair_claim_expression_A_G", "claim_expression_A_G"])
        self.assertEqual(expression_requests[0]["input_data"], expression_requests[1]["input_data"])
        self.assertEqual(rubric["expression_failures"][0]["claim_id"], "C1")
        self.assertEqual(rubric["claim_analyses"][0]["expression"]["status"], "unverified")
        self.assertEqual(rubric["claim_analyses"][1]["expression"]["status"], "analyzed")

    def test_unrepaired_related_quote_keeps_only_grounded_rubric_rows(self):
        discovery, client = self._case()
        bad = deepcopy(client.information["A1"])
        bad["forecasts"][0]["evidence"]["quote"] = "원문에 없는 전망"
        client.information["A1"] = bad
        client.repair_information["A1"] = deepcopy(bad)
        discovery.update(completed_stage=4, status="ready", comparison_status="available",
                         execution_status="success", errors=[], warnings=[])
        with self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING") as captured:
            result = complete_rubric_analysis(discovery, client=client)
        self.assertEqual(result["completed_stage"], 5)
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual(result["errors"][0]["stage"], "rubric_analysis")
        self.assertEqual(result["errors"][0]["code"], "RUBRIC_ARTICLE_UNVERIFIED")
        self.assertEqual(result["errors"][0]["candidate_id"], "A1")
        self.assertEqual(result["rubric_analysis"]["rubric"]["annotation_status"], "partial")
        self.assertEqual([row["article_id"] for row in result["rubric_analysis"]["claim_analyses"][0]["information"]["I1"]],
                         ["S0"])
        self.assertEqual(result["rubric_analysis"]["claim_analyses"][0]["unverified_article_ids"], ["A1"])
        self.assertEqual(result["selection"], discovery["selection"])
        self.assertIn("I1~I5와 A~G는 미판정", format_analysis_result(result))
        logs = " ".join(captured.output)
        self.assertEqual([name for name, article_id in client.calls if article_id == "A1"],
                         ["article_information_I1_I5", "repair_article_information_I1_I5"])
        self.assertIn('"attempt":"initial"', logs)
        self.assertIn('"attempt":"repair"', logs)
        self.assertEqual(logs.count('"reason_code":"QUOTE_NOT_UNIQUE"'), 2)
        self.assertIn("article_id=A1", logs)
        self.assertIn("reason=validation_failed", logs)
        self.assertNotIn("원문에 없는 전망", logs)

    def test_invalid_related_article_preserves_other_verified_article(self):
        discovery, client = self._case()
        second = deepcopy(discovery["search"]["candidates"][0])
        second.update(candidate_id="A2", url="https://news.example.com/a2")
        discovery["search"]["candidates"].append(second)
        discovery["selection"]["same_event_ids"].append("A2")
        discovery["selection"]["event_baseline_ids"].append("A2")
        discovery["selection"]["claim_comparisons"][0]["articles"].append(
            {"candidate_id": "A2", "title": second["title"], "url": second["url"],
             "relation": "same",
             "evidence": {"sentence_index": 0, "quote": "시청은 새 계획을 발표했다"}})
        second_assessment = deepcopy(discovery["selection"]["assessments"][0])
        second_assessment["candidate_id"] = "A2"
        discovery["selection"]["assessments"].append(second_assessment)
        client.information["A2"] = self._information(source=False)
        bad = deepcopy(client.information["A1"])
        bad["forecasts"][0]["evidence"]["quote"] = "원문에 없는 전망"
        client.information["A1"] = bad
        client.repair_information["A1"] = deepcopy(bad)
        discovery.update(completed_stage=4, status="ready", comparison_status="available",
                         execution_status="success", errors=[], warnings=[])
        with self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING"):
            result = complete_rubric_analysis(discovery, client=client)
        rubric = result["rubric_analysis"]
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual(set(rubric["article_annotations"]), {"S0", "A2"})
        self.assertEqual([row["article_id"] for row in rubric["claim_analyses"][0]["information"]["I1"]],
                         ["S0", "A2"])
        self.assertEqual(rubric["claim_analyses"][0]["selected_article_ids"], ["A2"])
        self.assertEqual(rubric["claim_analyses"][0]["unverified_article_ids"], ["A1"])
        self.assertEqual(rubric["article_annotation_failures"][0]["article_id"], "A1")

    def test_expression_failure_preserves_information_and_next_claim(self):
        discovery, client = self._two_claim_case()
        discovery.update(completed_stage=4, status="ready", comparison_status="available",
                         execution_status="success", errors=[], warnings=[])
        original = compare_claim_expressions

        def fail_first_claim(claim, articles, annotations, *, client):
            if claim["claim_id"] == "C1":
                raise ModelResponseError("SECRET_SENTINEL model output")
            return original(claim, articles, annotations, client=client)

        with (patch("server.analysis.v2.rubric_analysis.compare_claim_expressions",
                    side_effect=fail_first_claim),
              self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING") as captured):
            result = complete_rubric_analysis(discovery, client=client)
        rubric = result["rubric_analysis"]
        first, second = rubric["claim_analyses"]
        self.assertEqual(result["completed_stage"], 5)
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(rubric["rubric"]["annotation_status"], "partial")
        self.assertEqual(result["errors"][0]["code"], "RUBRIC_EXPRESSION_UNVERIFIED")
        self.assertEqual(result["errors"][0]["claim_id"], "C1")
        self.assertEqual([row["article_id"] for row in first["information"]["I1"]], ["S0", "A1"])
        self.assertEqual(first["expression"]["status"], "unverified")
        self.assertEqual(first["expression"]["findings"], [])
        self.assertEqual(first["expression_by_article"]["A1"]["A"]["status"], "unverified")
        self.assertEqual(second["expression"]["status"], "analyzed")
        self.assertEqual(rubric["expression_failures"][0]["claim_id"], "C1")
        self.assertIn("A~G 근거를 검증하지 못해 미판정", format_analysis_result(result))
        self.assertIn("claim_id=C1", captured.output[0])
        self.assertNotIn("SECRET_SENTINEL", " ".join(captured.output + result["warnings"]))

    def test_expression_transient_errors_remain_retryable_without_logging_exception_text(self):
        class RateLimitCause(Exception):
            code = 429

        for failure_kind, error_code in (("unavailable", "MODEL_UNAVAILABLE"),
                                         ("rate_limit", "MODEL_RATE_LIMITED")):
            with self.subTest(failure_kind=failure_kind):
                discovery, client = self._two_claim_case()
                discovery.update(completed_stage=4, status="ready", comparison_status="available",
                                 execution_status="success", errors=[], warnings=[])
                original = compare_claim_expressions

                def fail_first_claim(claim, articles, annotations, *, client):
                    if claim["claim_id"] == "C1":
                        if failure_kind == "rate_limit":
                            raise RuntimeError("SECRET_SENTINEL model response") from RateLimitCause(
                                "SECRET_SENTINEL provider response")
                        raise ModelUnavailableError("SECRET_SENTINEL model response")
                    return original(claim, articles, annotations, client=client)

                with (patch("server.analysis.v2.rubric_analysis.compare_claim_expressions",
                            side_effect=fail_first_claim),
                      self.assertLogs("server.analysis.v2.rubric_analysis", level="WARNING") as captured):
                    result = complete_rubric_analysis(discovery, client=client)
                self.assertEqual(result["execution_status"], "partial")
                self.assertEqual(result["errors"][0]["code"], "RUBRIC_EXPRESSION_UNVERIFIED")
                self.assertTrue(result["errors"][0]["retryable"])
                self.assertEqual(result["rubric_analysis"]["expression_failures"][0]["reason"],
                                 "model_unavailable")
                self.assertEqual(result["rubric_analysis"]["claim_analyses"][1]["expression"]["status"],
                                 "analyzed")
                self.assertIn(f"error_code={error_code}", captured.output[0])
                self.assertNotIn("SECRET_SENTINEL", " ".join(captured.output + result["warnings"] +
                                                          [str(result["errors"])]))

    def test_pipeline_failure_log_omits_exception_message(self):
        discovery, client = self._case()
        discovery.update(completed_stage=4, status="ready", comparison_status="available",
                         execution_status="success", errors=[], warnings=[])
        with (patch("server.analysis.v2.pipeline.analyze_selected_articles",
                    side_effect=ModelResponseError("SECRET_SENTINEL article/model content")),
              self.assertLogs("server.analysis.v2.pipeline", level="WARNING") as captured):
            result = complete_rubric_analysis(discovery, client=client)
        self.assertEqual(result["errors"][0]["code"], "ANALYSIS_RESPONSE_INVALID")
        self.assertIn("stage=rubric_analysis", captured.output[0])
        self.assertIn("error_type=ModelResponseError", captured.output[0])
        self.assertIn("error_code=ANALYSIS_RESPONSE_INVALID", captured.output[0])
        self.assertNotIn("SECRET_SENTINEL", captured.output[0])

    def test_source_annotation_cannot_drop_existing_grounded_claim(self):
        discovery, client = self._case()
        coverage = client.information["S0"]["claim_coverage"][0]
        coverage.update(relation="not_found", mentions=[], headline_evidence=None,
                        relation_evidence=[])
        with self.assertRaisesRegex(ModelResponseError, "입력 기사 정보 분석"):
            analyze_selected_articles(discovery, client=client)
        self.assertNotIn(("claim_expression_A_G", None), client.calls)

    def test_invalid_source_annotation_remains_fatal_to_rubric(self):
        discovery, client = self._case()
        source = client.information["S0"]["claim_coverage"][0]
        source.update(relation="not_found", mentions=[], headline_evidence=None,
                      relation_evidence=[])
        discovery.update(completed_stage=4, status="ready", comparison_status="available",
                         execution_status="success", errors=[], warnings=[])
        result = complete_rubric_analysis(discovery, client=client)
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["execution_status"], "partial")
        self.assertIsNone(result["rubric_analysis"])
        self.assertEqual(result["errors"][0]["code"], "ANALYSIS_RESPONSE_INVALID")

    def test_full_pipeline_snapshot_reuse_replay_and_corruption_recovery(self):
        discovery, client = self._case()
        metadata_search = {"queries": [], "metadata_candidates": [{"candidate_id": "A1"}],
                           "discovered_count": 1, "errors": [], "search_status": "success"}
        verified = {"search": {**metadata_search, "candidates": discovery["search"]["candidates"],
                               "fetch_failures": []}, "selection": discovery["selection"]}
        ranking = {"claim_candidates": [{"claim_id": "C1", "validation_warnings": []}]}
        with (TemporaryDirectory() as directory,
              patch("server.analysis.v2.pipeline.prepare_article", return_value=discovery["source_article"]),
              patch("server.analysis.v2.pipeline.analyze_source_article", return_value=source_analysis()),
              patch("server.analysis.v2.pipeline.search_candidate_metadata", return_value=metadata_search) as search,
              patch("server.analysis.v2.pipeline.rank_comparison_candidates", return_value=ranking) as rank,
              patch("server.analysis.v2.pipeline.select_verified_comparisons", return_value=verified) as select):
            first = run_analysis_pipeline(SOURCE_URL, client=client, cache_dir=directory)
            self.assertEqual(first["completed_stage"], 5)
            self.assertEqual(first["comparison_status"], "available")
            self.assertEqual(first["execution_status"], "success")
            self.assertEqual(first["reproducibility"]["execution"]["model_calls"], 3)
            snapshot = first["reproducibility"]["snapshot_path"]
            self.assertIsNotNone(snapshot)
            cold_client = RubricQueueClient(client.information, client.expression)
            cached = run_analysis_pipeline(SOURCE_URL, client=cold_client, cache_dir=directory)
            self.assertTrue(cached["reproducibility"]["cache_hit"])
            self.assertEqual(cached["reproducibility"]["execution"]["model_calls"], 0)
            self.assertEqual(cold_client.calls, [])
            search.assert_called_once()
            rank.assert_called_once()
            select.assert_called_once()
            replay_client = RubricQueueClient(client.information, client.expression)
            replay = evaluate_analysis_snapshot(snapshot, client=replay_client)
            self.assertEqual(replay["model_calls"], 3)
            self.assertEqual(replay["same_response_count"], 3)
            self.assertTrue(all(row["schema_valid"] for row in replay["requests"]))
            snapshot_file = Path(snapshot)
            saved = json.loads(snapshot_file.read_text(encoding="utf-8"))
            saved["result"]["status"] = "tampered"
            snapshot_file.write_text(json.dumps(saved), encoding="utf-8")
            rerun_client = RubricQueueClient(client.information, client.expression)
            rerun = run_analysis_pipeline(SOURCE_URL, client=rerun_client, cache_dir=directory)
            self.assertFalse(rerun["reproducibility"]["cache_hit"])
            self.assertEqual(rerun["status"], "ready")
            self.assertEqual(len(rerun_client.calls), 3)
            self.assertEqual(search.call_count, 2)

    def test_cached_analysis_expires_when_news_comparison_is_stale(self):
        key = "cache-key"
        with TemporaryDirectory() as directory:
            path = Path(directory)
            (path / f"{key}.latest.json").write_text(json.dumps({
                "snapshot_file": f"{key}.{'0' * 64}.json"}), encoding="utf-8")
            bundle = {"request_key": key, "result": {"reproducibility": {"created_at": ""}}}
            with patch("server.analysis.v2.pipeline._read_snapshot", return_value=bundle):
                for age, expected in ((timedelta(seconds=20), bundle),
                                      (timedelta(minutes=10), None),
                                      (timedelta(seconds=-20), None)):
                    bundle["result"]["reproducibility"]["created_at"] = (
                        datetime.now(timezone.utc) - age).isoformat()
                    self.assertEqual(_load_cached_analysis(path, key), expected)
