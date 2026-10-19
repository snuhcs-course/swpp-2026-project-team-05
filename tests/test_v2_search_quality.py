"""Offline checks for quality-oriented Korean news candidate retrieval."""

from io import BytesIO
import json
from threading import Event
from time import monotonic
from unittest import TestCase
from unittest.mock import patch
from urllib.error import HTTPError

from server.analysis.v2.article_search import (
    _GOOGLE_INTERACTIONS_URL, search_candidate_metadata, search_google_grounded_news,
    search_naver_news,
)
from server.analysis.v2.result_output import OperationError


SOURCE = {"url": "https://news.example.com/source"}


def _row(slug: str, sort: str) -> dict:
    return {
        "title": f"{slug} 기사", "description": "같은 사건의 보도",
        "link": f"https://news.example.com/{slug}", "originallink": "",
        "search_provided_at": "", "search_sort": sort,
    }


class SearchQualityTests(TestCase):
    def test_relevance_and_recency_interleave_and_keep_provenance(self):
        pages = {"sim": [_row("sim-1", "sim"), _row("sim-2", "sim")],
                 "date": [_row("date-1", "date"), _row("sim-1", "date")]}
        with patch("server.analysis.v2.article_search._search_naver_news_sorted",
                   side_effect=lambda _query, _display, sort: pages[sort]):
            result = search_candidate_metadata(SOURCE, {"event_query": "청계천 수질 기준 초과",
                                                        "claims": []})
        candidates = result["metadata_candidates"]
        self.assertEqual([row["urls"][0].rsplit("/", 1)[-1] for row in candidates],
                         ["sim-1", "date-1", "sim-2"])
        self.assertEqual(result["discovered_count"], 3)
        self.assertEqual({match["sort"] for match in candidates[0]["search_matches"]},
                         {"sim", "date"})
        self.assertEqual({match["provider"] for match in candidates[0]["search_matches"]},
                         {"naver"})

    def test_optional_event_variants_merge_with_claim_query_without_broad_fallback(self):
        analysis = {
            "event_query": "서울시 청계천 수질 기준 초과",
            "event_queries": ["서울시 청계천 수질 기준 초과", "청계천 수질 측정 기준 초과",
                              "청계천 수질 측정 기준 초과"],
            "claims": [{"claim_id": "C1", "search_query": "청계천 수질 측정 기준 초과"}],
        }
        with patch("server.analysis.v2.article_search.search_naver_news", return_value=[]) as search:
            result = search_candidate_metadata(SOURCE, analysis)
        self.assertCountEqual([call.args[0] for call in search.call_args_list],
                              ["서울시 청계천 수질 기준 초과", "청계천 수질 측정 기준 초과"])
        self.assertEqual(result["queries"][1]["sources"],
                         [{"scope": "event", "claim_id": None},
                          {"scope": "claim", "claim_id": "C1"}])

    def test_all_three_alternate_event_queries_are_searched_and_traced(self):
        stale_query = "서울시 전혀 다른 사건 발표"
        reviewed = ["청계천 수질 측정 기준 초과", "서울시 청계천 수질 조사 결과",
                    "청계천 수질 기준 초과 발표"]
        shared = _row("shared", "sim")
        with patch("server.analysis.v2.article_search.search_naver_news",
                   return_value=[shared]) as search:
            result = search_candidate_metadata(SOURCE, {
                "event_query": stale_query, "event_queries": reviewed,
                "claims": [],
            })
        expected = reviewed
        self.assertCountEqual([call.args[0] for call in search.call_args_list], expected)
        self.assertEqual([row["query"] for row in result["queries"]], expected)
        self.assertEqual(result["discovered_count"], 1)
        candidate = result["metadata_candidates"][0]
        self.assertTrue(candidate["discovered_by_event"])
        self.assertEqual([row["query"] for row in candidate["search_matches"]], expected)

    def test_saturated_first_event_query_does_not_crowd_out_later_variants(self):
        queries = ["서울시 청계천 수질 발표", "청계천 수질 조사 결과", "청계천 오염 기준 초과"]
        pages = {query: [_row(f"variant-{index}-{position}", "sim") for position in range(20)]
                 for index, query in enumerate(queries)}
        with patch("server.analysis.v2.article_search.search_naver_news",
                   side_effect=lambda query, _display: pages[query]):
            result = search_candidate_metadata(SOURCE, {"event_query": queries[0],
                                                        "event_queries": queries, "claims": []})
        slugs = [row["urls"][0].rsplit("/", 1)[-1] for row in result["metadata_candidates"]]
        self.assertEqual(len(slugs), 20)
        self.assertEqual(slugs[:6], ["variant-0-0", "variant-1-0", "variant-2-0",
                                     "variant-0-1", "variant-1-1", "variant-2-1"])
        self.assertEqual([sum(slug.startswith(f"variant-{index}-") for slug in slugs)
                          for index in range(3)], [7, 7, 6])

    def test_event_round_robin_preserves_claim_and_grounded_slots(self):
        queries = ["서울시 청계천 수질 발표", "청계천 수질 조사 결과", "청계천 오염 기준 초과"]
        claim_query = "청계천 탁도 원인 분석"
        pages = {query: [_row(f"event-{index}-{position}", "sim") for position in range(10)]
                 for index, query in enumerate(queries)}
        pages[claim_query] = [_row("claim-specific", "sim")]
        grounded = {**_row("web-specific", ""), "search_provider": "google_grounding"}
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "1"}),
              patch("server.analysis.v2.article_search.search_naver_news",
                    side_effect=lambda query, _display: pages[query]),
              patch("server.analysis.v2.article_search.search_google_grounded_news",
                    return_value=[grounded])):
            result = search_candidate_metadata(SOURCE, {"event_query": queries[0],
                "event_queries": queries, "claims": [{"claim_id": "C1", "search_query": claim_query}]},
                max_candidates=8, event_reserve=3)
        slugs = [row["urls"][0].rsplit("/", 1)[-1] for row in result["metadata_candidates"]]
        self.assertEqual(slugs, ["event-0-0", "event-1-0", "event-2-0", "web-specific",
                                 "claim-specific", "event-0-1", "event-1-1", "event-2-1"])
        self.assertEqual([row["candidate_id"] for row in result["metadata_candidates"]],
                         [f"A{index}" for index in range(1, 9)])

    def test_one_failed_sort_keeps_candidates_and_reports_partial_status(self):
        def fetch(_query, _display, sort):
            if sort == "sim":
                raise OperationError("일시 장애", code="SEARCH_HTTP_ERROR", retryable=True)
            return [_row("recent", "date")]

        with patch("server.analysis.v2.article_search._search_naver_news_sorted", side_effect=fetch):
            result = search_candidate_metadata(SOURCE, {"event_query": "청계천 수질 기준 초과",
                                                        "claims": []})
        self.assertEqual(result["search_status"], "partial")
        self.assertEqual(result["metadata_candidates"][0]["search_matches"][0]["sort"], "date")
        self.assertEqual(result["errors"][0]["sort"], "sim")
        self.assertEqual(result["errors"][0]["provider"], "naver")

    def test_both_failed_sorts_raise_search_error(self):
        with patch("server.analysis.v2.article_search._search_naver_news_sorted",
                   side_effect=OperationError("일시 장애", code="SEARCH_HTTP_ERROR")):
            with self.assertRaises(OperationError):
                search_naver_news("청계천 수질 기준 초과")

    def test_naver_sorts_run_concurrently_but_return_in_stable_order(self):
        date_started = Event()

        def search(_query, _display, sort):
            if sort == "sim":
                self.assertTrue(date_started.wait(1), "date search did not start concurrently")
                return [_row("relevant", "sim")]
            date_started.set()
            return [_row("recent", "date")]

        with patch("server.analysis.v2.article_search._search_naver_news_sorted", side_effect=search):
            rows = search_naver_news("청계천 수질 기준 초과")
        self.assertEqual([row["title"] for row in rows], ["relevant 기사", "recent 기사"])

    def test_queries_run_concurrently_but_candidate_ids_follow_query_plan(self):
        second_started = Event()
        first, second = "청계천 수질 조사", "청계천 수질 기준 초과"

        def search(query, _display):
            if query == first:
                self.assertTrue(second_started.wait(1), "second query did not start concurrently")
                return [_row("first", "sim")]
            second_started.set()
            return [_row("second", "date")]

        with patch("server.analysis.v2.article_search.search_naver_news", side_effect=search):
            result = search_candidate_metadata(SOURCE, {"event_query": first,
                                                        "event_queries": [first, second], "claims": []})
        self.assertEqual([(row["candidate_id"], row["title"])
                          for row in result["metadata_candidates"]],
                         [("A1", "first 기사"), ("A2", "second 기사")])

    def test_google_grounding_uses_only_direct_search_citations_and_stateless_request(self):
        response = {"interaction": {"steps": [
            {"type": "google_search_call", "arguments": {"queries": ["청계천 수질"]}},
            {"type": "model_output", "content": [{"type": "text",
                "text": "URL처럼 보이는 https://news.example.com/invented",
                "annotations": [
                    {"type": "url_citation", "url": "https://news.example.com/real",
                     "title": "실제 인용 기사"},
                    {"type": "url_citation", "url": "https://vertexaisearch.cloud.google.com/redirect",
                     "title": "검색 리다이렉트"},
                    {"type": "url_citation", "url": "http://127.0.0.1/private",
                     "title": "로컬 주소"},
                ]}]},
        ]}}
        payload = BytesIO(json.dumps(response).encode("utf-8"))
        with (patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.return_value = payload
            rows = search_google_grounded_news(
                {**SOURCE, "title": "청계천 수질 조사"},
                {"event_query": "청계천 수질 조사", "core_event": "청계천 수질 조사 발표"},
            )
        request = build.return_value.open.call_args.args[0]
        body = json.loads(request.data)
        self.assertEqual(request.full_url, _GOOGLE_INTERACTIONS_URL)
        self.assertFalse(body["store"])
        self.assertEqual(body["tools"], [{"type": "google_search"}])
        self.assertEqual([row["link"] for row in rows], ["https://news.example.com/real"])
        self.assertEqual(rows[0]["description"], "")

    def test_google_prompt_uses_reviewed_event_instead_of_stale_initial_event(self):
        response = {"interaction": {"steps": [{"type": "google_search_call"}]}}
        with (patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.return_value = BytesIO(json.dumps(response).encode("utf-8"))
            search_google_grounded_news(
                {**SOURCE, "title": "청계천 수질 조사"},
                {"event_query": "무관한 경찰 사건", "event_queries": ["청계천 수질 기준 초과"],
                 "core_event": "무관한 경찰 사건", "source_scope_review": {
                     "primary_event": "청계천 수질 기준 초과 발표"}},
            )
        request = build.return_value.open.call_args.args[0]
        prompt = json.loads(request.data)["input"]
        self.assertIn("청계천 수질 기준 초과 발표", prompt)
        self.assertNotIn("무관한 경찰 사건", prompt)

    def test_google_annotation_without_real_search_call_is_not_accepted(self):
        response = {"interaction": {"steps": [{"type": "model_output", "content": [
            {"type": "text", "text": "기사", "annotations": [{
                "type": "url_citation", "url": "https://news.example.com/uncorroborated",
                "title": "검색되지 않은 링크"}]}]}]}}
        with (patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.return_value = BytesIO(json.dumps(response).encode("utf-8"))
            rows = search_google_grounded_news(
                {**SOURCE, "title": "청계천 수질 조사"},
                {"event_query": "청계천 수질 조사", "core_event": "청계천 수질 조사 발표"},
            )
        self.assertEqual(rows, [])

    def test_all_naver_429_uses_grounded_citation_and_keeps_search_errors(self):
        response = {"interaction": {"steps": [
            {"type": "google_search_call", "arguments": {"queries": ["청계천 수질"]}},
            {"type": "model_output", "content": [{"type": "text", "text": "인용 기사",
                "annotations": [{"type": "url_citation", "url": "https://other.example.com/report",
                                 "title": "다른 언론사 기사"}]}]},
        ]}}
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news",
                    side_effect=OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.return_value = BytesIO(json.dumps(response).encode("utf-8"))
            result = search_candidate_metadata(SOURCE, {"event_query": "청계천 수질 조사", "claims": []})
        self.assertEqual(result["search_status"], "partial")
        self.assertEqual([(row["provider"], row["status"]) for row in result["queries"]],
                         [("naver", "search_failed"), ("google_grounding", "searched")])
        self.assertEqual([(row["provider"], row["http_status"]) for row in result["errors"]],
                         [("naver", 429)])
        candidate = result["metadata_candidates"][0]
        self.assertEqual(candidate["urls"], ["https://other.example.com/report"])
        self.assertTrue(candidate["discovered_by_event"])
        self.assertEqual(candidate["search_matches"][0]["provider"], "google_grounding")
        self.assertEqual(build.return_value.open.call_args.kwargs["timeout"], 30.0)

    def test_google_fallback_failure_and_empty_citations_preserve_naver_failures(self):
        analysis = {"event_query": "청계천 수질 조사", "claims": []}
        naver_error = OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news",
                    side_effect=OperationError("Google unavailable", code="GOOGLE_SEARCH_HTTP_ERROR"))):
            failed = search_candidate_metadata(SOURCE, analysis)
        self.assertEqual(failed["search_status"], "failed")
        self.assertEqual([row["provider"] for row in failed["errors"]], ["naver", "google_grounding"])
        self.assertEqual(failed["metadata_candidates"], [])

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", return_value=[])):
            empty = search_candidate_metadata(SOURCE, analysis)
        self.assertEqual(empty["search_status"], "partial")
        self.assertEqual(empty["metadata_candidates"], [])
        self.assertEqual([row["provider"] for row in empty["errors"]], ["naver"])

    def test_naver_partial_with_candidate_or_all_empty_success_skips_fallback(self):
        queries = ["청계천 수질 조사", "청계천 수질 발표"]
        def partial_search(query, _display):
            if query == queries[0]:
                return [_row("naver", "sim")]
            raise OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=partial_search),
              patch("server.analysis.v2.article_search.get_gemini_api_key") as key,
              patch("server.analysis.v2.article_search.search_google_grounded_news") as google):
            partial = search_candidate_metadata(SOURCE, {"event_query": queries[0],
                                                         "event_queries": queries, "claims": []})
        self.assertEqual(partial["search_status"], "partial")
        self.assertEqual(partial["metadata_candidates"][0]["title"], "naver 기사")
        key.assert_not_called()
        google.assert_not_called()

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", return_value=[]),
              patch("server.analysis.v2.article_search.search_google_grounded_news") as google):
            empty = search_candidate_metadata(SOURCE, {"event_query": queries[0], "claims": []})
        self.assertEqual(empty["search_status"], "success")
        google.assert_not_called()

    def test_naver_partial_failure_without_external_candidate_uses_fallback(self):
        queries = ["청계천 수질 조사", "청계천 수질 발표"]

        def empty_partial_search(query, _display):
            if query == queries[0]:
                return []
            raise OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=empty_partial_search),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news",
                    return_value=[{**_row("grounded", ""), "search_provider": "google_grounding"}]) as google):
            empty_partial = search_candidate_metadata(SOURCE, {"event_query": queries[0],
                                                               "event_queries": queries, "claims": []})
        self.assertEqual(empty_partial["search_status"], "partial")
        self.assertEqual(empty_partial["metadata_candidates"][0]["title"], "grounded 기사")
        google.assert_called_once()

        def source_only_partial_search(query, _display):
            if query == queries[0]:
                return [{**_row("source", "sim"), "link": SOURCE["url"]}]
            raise OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=source_only_partial_search),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news",
                    return_value=[{**_row("grounded", ""), "search_provider": "google_grounding"}]) as google):
            source_only = search_candidate_metadata(SOURCE, {"event_query": queries[0],
                                                             "event_queries": queries, "claims": []})
        self.assertEqual([row["title"] for row in source_only["metadata_candidates"]], ["grounded 기사"])
        google.assert_called_once()

    def test_naver_failed_sort_with_empty_other_sort_uses_fallback(self):
        def sorted_search(_query, _display, sort):
            if sort == "sim":
                raise OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
            return []

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search._search_naver_news_sorted", side_effect=sorted_search),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news",
                    return_value=[{**_row("grounded", ""), "search_provider": "google_grounding"}]) as google):
            result = search_candidate_metadata(SOURCE, {"event_query": "청계천 수질 조사", "claims": []})
        self.assertEqual(result["search_status"], "partial")
        self.assertEqual(result["errors"][0]["sort"], "sim")
        self.assertEqual(result["metadata_candidates"][0]["title"], "grounded 기사")
        google.assert_called_once()

    def test_google_fallback_skips_short_deadline_and_bounds_request_timeout(self):
        analysis = {"event_query": "청계천 수질 조사", "claims": []}
        naver_error = OperationError("NAVER quota", code="SEARCH_HTTP_ERROR", http_status=429)
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key") as key,
              patch("server.analysis.v2.article_search.search_google_grounded_news") as google):
            skipped = search_candidate_metadata(SOURCE, analysis,
                                                google_fallback_deadline_at=monotonic() + 4)
        self.assertEqual(skipped["search_status"], "failed")
        self.assertEqual(len(skipped["queries"]), 1)
        key.assert_not_called()
        google.assert_not_called()

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value=None),
              patch("server.analysis.v2.article_search.search_google_grounded_news") as google):
            unconfigured = search_candidate_metadata(SOURCE, analysis,
                                                     google_fallback_deadline_at=monotonic() + 45)
        self.assertEqual(unconfigured["search_status"], "failed")
        google.assert_not_called()

        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "0"}),
              patch("server.analysis.v2.article_search.search_naver_news", side_effect=naver_error),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.search_google_grounded_news", return_value=[]) as google):
            search_candidate_metadata(SOURCE, analysis, google_fallback_deadline_at=monotonic() + 15)
        timeout = google.call_args.kwargs["timeout_seconds"]
        self.assertGreaterEqual(timeout, 5)
        self.assertLess(timeout, 15)

    def test_optional_google_failure_keeps_naver_candidate_and_partial_status(self):
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "1"}),
              patch("server.analysis.v2.article_search.search_naver_news",
                    return_value=[_row("naver", "sim")]),
              patch("server.analysis.v2.article_search.search_google_grounded_news",
                    side_effect=OperationError("검색 실패", code="GOOGLE_SEARCH_HTTP_ERROR"))):
            result = search_candidate_metadata(SOURCE, {"event_query": "청계천 수질 조사",
                                                        "claims": []})
        self.assertEqual(result["search_status"], "partial")
        self.assertEqual(result["metadata_candidates"][0]["title"], "naver 기사")
        self.assertEqual(result["errors"][0]["provider"], "google_grounding")

    def test_optional_google_candidate_keeps_provider_provenance(self):
        grounded = {**_row("google", ""), "search_provider": "google_grounding"}
        with (patch.dict("os.environ", {"ENABLE_GOOGLE_GROUNDED_SEARCH": "1"}),
              patch("server.analysis.v2.article_search.search_naver_news", return_value=[]),
              patch("server.analysis.v2.article_search.search_google_grounded_news",
                    return_value=[grounded])):
            result = search_candidate_metadata(SOURCE, {"event_query": "청계천 수질 조사",
                                                        "claims": []})
        matches = result["metadata_candidates"][0]["search_matches"]
        self.assertEqual(matches, [{"query": "청계천 수질 조사", "scope": "event",
                                    "claim_id": None, "provider": "google_grounding"}])

    def test_invalid_google_model_setting_is_rejected_without_value_in_error(self):
        bad_setting = "invalid model name with spaces"
        with (patch.dict("os.environ", {"GOOGLE_GROUNDED_SEARCH_MODEL": bad_setting}),
              patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            with self.assertRaises(OperationError) as raised:
                search_google_grounded_news(
                    {**SOURCE, "title": "청계천 수질 조사"},
                    {"event_query": "청계천 수질 조사", "core_event": "청계천 수질 조사 발표"},
                )
        self.assertEqual(raised.exception.code, "GOOGLE_SEARCH_CONFIGURATION_INVALID")
        self.assertNotIn(bad_setting, str(raised.exception))
        build.assert_not_called()

    def test_invalid_google_response_does_not_expose_response_text(self):
        with (patch("server.analysis.v2.article_search.get_gemini_api_key", return_value="synthetic-key"),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.return_value = BytesIO(b"upstream-sensitive-error-content")
            with self.assertRaises(OperationError) as raised:
                search_google_grounded_news(
                    {**SOURCE, "title": "청계천 수질 조사"},
                    {"event_query": "청계천 수질 조사", "core_event": "청계천 수질 조사 발표"},
                )
        self.assertEqual(raised.exception.code, "GOOGLE_SEARCH_RESPONSE_INVALID")
        self.assertNotIn("upstream-sensitive-error-content", str(raised.exception))

    def test_google_http_failure_logs_only_numeric_upstream_status(self):
        for status in (400, 403, 429):
            with (self.subTest(status=status),
                  patch("server.analysis.v2.article_search.get_gemini_api_key",
                        return_value="synthetic-secret-key"),
                  patch("server.analysis.v2.article_search.build_opener") as build,
                  self.assertLogs("server.analysis.v2.article_search", level="WARNING") as logs):
                build.return_value.open.side_effect = HTTPError(
                    "https://example.com/sensitive-url", status, "sensitive upstream reason", {},
                    BytesIO(b"sensitive upstream body"))
                with self.assertRaises(OperationError) as raised:
                    search_google_grounded_news(
                        {**SOURCE, "title": "sensitive article title"},
                        {"event_query": "sensitive search query", "claims": []},
                    )
            self.assertEqual(raised.exception.code, "GOOGLE_SEARCH_HTTP_ERROR")
            self.assertEqual(raised.exception.http_status, status)
            self.assertEqual(logs.output, [
                f"WARNING:server.analysis.v2.article_search:google_search_http_error upstream_status={status}"
            ])
