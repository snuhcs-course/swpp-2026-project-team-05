"""Offline regression tests for the five-stage article comparison pipeline."""

# AI-assisted Iteration 2 tests use invented articles and never call external services.
from copy import deepcopy
from unittest.mock import patch

from django.test import SimpleTestCase

from server.analysis.v2.comparison_selection import select_verified_comparisons
from server.analysis.v2.llm_analysis import verify_candidate_articles
from server.analysis.v2.pipeline import complete_rubric_analysis, run_discovery_pipeline
from server.analysis.v2.result_output import OperationError, make_error_record
from server.analysis.v2.rubric_analysis import (
    _verified_comparison_context, analyze_selected_articles, retain_verified_comparison_context,
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

    def test_invalid_option_stops_before_source_or_model_calls(self):
        with patch("server.analysis.v2.pipeline.prepare_article") as prepare:
            result = run_discovery_pipeline(SOURCE_URL, max_per_claim=0, client=object())
        prepare.assert_not_called()
        self.assertEqual(result["completed_stage"], 0)
        self.assertEqual(result["execution_status"], "failed")
        self.assertEqual(result["errors"][0]["stage"], "input_validation")


class GroundingTests(SimpleTestCase):
    def _response(self, *, event_quote, claim_quote):
        return {"assessments": {"A1": {
            "event_relation": "same_event",
            "event_evidence": {"sentence_index": 0, "quote": event_quote},
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


class CandidateRefillTests(SimpleTestCase):
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
