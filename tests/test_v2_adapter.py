"""Offline contract cases for the integrated LLM and Android response."""

# AI-assisted Iteration 2 tests; all article content here is synthetic.
import json
import os
from pathlib import Path
from unittest.mock import patch

from django.test import Client, SimpleTestCase

from server.analysis.v2_adapter import PipelineContractError, adapt_v2_result
from server.analysis.v2.pipeline import complete_rubric_analysis


SOURCE_URL = "https://news.example.com/source"


def pipeline_result():
    return {
        "status": "ready", "execution_status": "success", "comparison_status": "available",
        "errors": [], "warnings": [],
        "source_article": {"title": "입력 기사", "url": SOURCE_URL},
        "source_analysis": {"core_event": "같은 사건", "claims": [
            {"claim_id": "C1", "claim": "관객 수가 늘었다", "evidence": {"quote": "관객 수가 늘었다"}},
            {"claim_id": "C2", "claim": "새 계획이 발표됐다", "evidence": {"quote": "새 계획이 발표됐다"}},
        ]},
        "search": {"candidates": [
            {"candidate_id": "A", "title": "A 기사", "url": "https://news.example.com/a"},
            {"candidate_id": "B", "title": "B 기사", "url": "https://news.example.com/b"},
            {"candidate_id": "C", "title": "C 기사", "url": "https://news.example.com/c"},
        ]},
        "selection": {
            "same_event_ids": ["A", "B", "C"], "event_baseline_ids": ["C"],
            "claim_comparisons": [
                {"claim_id": "C1", "articles": [
                    {"candidate_id": "A", "title": "A 기사", "url": "https://news.example.com/a",
                     "relation": "same", "evidence": {"quote": "A 기사 관객 수가 늘었다"}},
                    {"candidate_id": "B", "title": "B 기사", "url": "https://news.example.com/b",
                     "relation": "same_issue", "evidence": {"quote": "B 기사는 다른 수치를 보도했다"}},
                ]},
                {"claim_id": "C2", "articles": [
                    {"candidate_id": "A", "title": "A 기사", "url": "https://news.example.com/a",
                     "relation": "opposes", "evidence": {"quote": "A 기사는 계획을 부인했다"}},
                ]},
            ],
        },
        "rubric_analysis": {
            "rubric": {"version": "0.2.1", "judgment_status": "provisional"},
            "claim_analyses": [{
                "claim_id": "C1",
                "information": {
                    "I1": [
                        {"article_id": "S0", "presence": "found", "relation": "same",
                         "evidence": [{"quote": "관객 수가 늘었다"}]},
                        {"article_id": "A", "presence": "found", "relation": "same",
                         "evidence": [{"quote": "A 기사 관객 수가 늘었다"}]},
                        {"article_id": "B", "presence": "found", "relation": "same_issue",
                         "evidence": [{"quote": "B 기사는 다른 수치를 보도했다"}]},
                    ],
                    "I2": [
                        {"article_id": "S0", "positions": [{"position": "lead"}],
                         "body_sentence_count": 1, "headline_evidence": None},
                        {"article_id": "A", "positions": [{"position": "body"}],
                         "body_sentence_count": 2, "headline_evidence": None},
                        {"article_id": "B", "positions": [{"position": "body"}],
                         "body_sentence_count": 1, "headline_evidence": None},
                    ],
                    "I3": [{"article_id": "A", "items": [
                        {"content": "과거 사례를 설명했다", "evidence": {"quote": "A 기사는 과거 사례를 설명했다"}}
                    ]}],
                    "I4": [{"article_id": "A", "items": []}],
                    "I5": [{"article_id": "B", "voices": [
                        {"speaker": "관계자", "speech_kind": "direct",
                         "speech_evidence": {"quote": "관계자는 새 기록이라고 말했다"}}
                    ]}],
                },
                "expression": {"findings": [
                    {"article_id": "A", "category": "D", "marker": "급증",
                     "context_evidence": {"quote": "관객이 급증했다"},
                     "baseline_article_id": "S0", "baseline_evidence": {"quote": "관객이 늘었다"},
                     "markedness": 2, "status": "provisional", "polarity": "none",
                     "markedness_reason": "급증은 늘었다보다 강하다"},
                    {"article_id": "B", "category": "F", "marker": "개악",
                     "context_evidence": {"quote": "이를 개악이라고 불렀다"},
                     "baseline_article_id": None, "baseline_evidence": None,
                     "markedness": None, "status": "needs_review", "polarity": "negative"},
                ], "excluded": [
                    {"article_id": "B", "category": "E", "status": "excluded",
                     "reason": "direct_quote_recorded_in_I5"},
                ]},
            }],
        },
    }


class AdapterTests(SimpleTestCase):
    def test_android_golden_fixture_matches_python_adapter(self):
        fixture = Path(__file__).resolve().parents[1] / "android/app/src/test/resources/v2_android_response.json"
        self.assertEqual(
            json.loads(fixture.read_text(encoding="utf-8")),
            adapt_v2_result(pipeline_result(), max_related=2, requested_url=SOURCE_URL),
        )

    def test_evidence_ids_relations_and_global_related_limit(self):
        result = adapt_v2_result(pipeline_result(), max_related=2, requested_url=SOURCE_URL)
        self.assertEqual([r["candidate_id"] for r in result["related_articles"]], ["A", "B"])
        self.assertEqual(result["source_analysis"]["title"], "입력 기사")
        first, second = result["comparison"]["issues"]
        self.assertEqual([first["claim_id"], second["claim_id"]], ["C1", "C2"])
        self.assertEqual([m["article_index"] for m in first["members"]], [0, 1, 2])
        self.assertEqual([m["article_index"] for m in second["members"]], [0, 1])
        self.assertEqual(first["members"][2]["relation_to_source"], "same_issue")
        self.assertEqual(second["members"][1]["evidence"]["quote"], "A 기사는 계획을 부인했다")
        self.assertEqual(first["members"][1]["url"], "https://news.example.com/a")

    def test_rubric_keeps_provisional_and_withheld_findings(self):
        rubric = adapt_v2_result(pipeline_result(), max_related=2, requested_url=SOURCE_URL)["rubric"]
        self.assertEqual(rubric["questions"], [])  # P3 has not been adopted.
        self.assertEqual(rubric["judgment_status"], "provisional")
        expressions = rubric["expression_differences"]
        self.assertEqual(expressions[0]["categories"][0]["markedness"], 2)
        self.assertIsNone(expressions[1]["categories"][0]["markedness"])
        self.assertEqual(expressions[1]["status"], "needs_review")
        self.assertEqual(expressions[1]["baseline"]["source"], "")
        self.assertEqual(len(expressions), 2)  # Direct quote is excluded from A-G.
        information = rubric["information_differences"]
        self.assertTrue(any(row["code"] == "I2" and row["sentence"] == "A 기사 관객 수가 늘었다" for row in information))
        self.assertTrue(any(row["code"] == "I5" and row["sentence"] == "관계자는 새 기록이라고 말했다" for row in information))

    def test_later_coverage_disagreement_withholds_relation_and_i2(self):
        raw = pipeline_result()
        i1 = raw["rubric_analysis"]["claim_analyses"][0]["information"]["I1"][2]
        i1.update(presence="uncertain", relation="uncertain", evidence=[],
                  coverage_disagreement={"reason": "verified_passage_not_resolved_in_information_analysis"})
        i2 = raw["rubric_analysis"]["claim_analyses"][0]["information"]["I2"][2]
        i2.update(positions=[], body_sentence_count=0)
        result = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)
        member = result["comparison"]["issues"][0]["members"][2]
        self.assertEqual(member["relation_to_source"], "uncertain")
        self.assertEqual(result["analysis_status"]["execution_status"], "partial")
        self.assertFalse(any(row["code"] == "I2" and row["article_index"] == 2
                             for row in result["rubric"]["information_differences"]))

    def test_no_match_baseline_later_found_is_displayed_as_unresolved(self):
        raw = pipeline_result()
        raw["comparison_status"] = "no_matches"
        for row in raw["selection"]["claim_comparisons"]:
            row["articles"] = []
        raw["rubric_analysis"]["claim_analyses"][0]["information"]["I1"].append(
            {"article_id": "C", "presence": "found", "relation": "same_issue",
             "evidence": [{"quote": "C 기사는 관객 수의 다른 면을 보도했다"}]})
        result = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)
        member = result["comparison"]["issues"][0]["members"][1]
        self.assertEqual(member["candidate_id"], "C")
        self.assertEqual(member["relation_to_source"], "uncertain")
        self.assertEqual(result["analysis_status"]["comparison_status"], "available")
        self.assertEqual(result["analysis_status"]["execution_status"], "partial")

    def test_partial_rubric_failure_preserves_verified_comparison(self):
        raw = pipeline_result()
        raw["rubric_analysis"] = None
        raw["execution_status"] = "partial"
        raw["errors"] = [{"stage": "rubric_analysis", "code": "MODEL_TIMEOUT",
                          "message": "루브릭 단계가 시간 초과됐어요.", "retryable": True,
                          "private_context": "must not leave server"}]
        result = adapt_v2_result(raw, max_related=1, requested_url=SOURCE_URL)
        self.assertNotIn("rubric", result)
        self.assertEqual(result["comparison"]["issues"][0]["members"][1]["article_index"], 1)
        self.assertEqual(result["analysis_status"]["execution_status"], "partial")
        self.assertNotIn("private_context", result["analysis_status"]["errors"][0])

    def test_unverified_candidate_and_missing_evidence_are_rejected(self):
        raw = pipeline_result()
        raw["selection"]["same_event_ids"] = ["B", "C"]
        with self.assertRaises(PipelineContractError):
            adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)
        raw = pipeline_result()
        raw["selection"]["claim_comparisons"][0]["articles"][0]["evidence"] = None
        with self.assertRaises(PipelineContractError):
            adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)


class IntegratedApiTests(SimpleTestCase):
    def setUp(self):
        self.client = Client()
        env = patch.dict(os.environ, {"FRAMELESS_PIPELINE": "v2", "GEMINI_API_KEY": "test-key",
                                   "NAVER_CLIENT_ID": "test-id", "NAVER_CLIENT_SECRET": "test-secret"})
        env.start()
        self.addCleanup(env.stop)

    @patch("server.views.analyze_related_articles_v2")
    def test_v2_route_returns_android_contract(self, analyze):
        analyze.return_value = adapt_v2_result(pipeline_result(), max_related=2, requested_url=SOURCE_URL)
        response = self.client.post("/api/analyze", data=json.dumps({"url": SOURCE_URL, "max_related": 2}),
                                    content_type="application/json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["comparison"]["issues"][0]["claim_id"], "C1")
        analyze.assert_called_once_with(SOURCE_URL, max_related=2)

    @patch("server.views.analyze_related_articles_v2")
    def test_v2_unavailable_is_not_reported_as_no_matches(self, analyze):
        from server.views import PipelineResultError
        analyze.side_effect = PipelineResultError("comparison_unavailable", "비교할 수 없어요.", retryable=True)
        response = self.client.post("/api/analyze", data=json.dumps({"url": SOURCE_URL}),
                                    content_type="application/json")
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["error"]["code"], "comparison_unavailable")
        self.assertEqual(response.headers["Retry-After"], "30")

    @patch("server.analysis.v2.pipeline.analyze_selected_articles", side_effect=RuntimeError("rubric timed out"))
    def test_rubric_stage_failure_keeps_verified_comparison(self, _rubric):
        discovery = pipeline_result()
        discovery.update(completed_stage=4, rubric_analysis=None)
        discovery["source_analysis"]["core_event"] = "같은 사건"
        for claim in discovery["source_analysis"]["claims"]:
            claim.update(claim_kind="fact", speaker="")
        discovery["selection"]["assessments"] = []
        partial = complete_rubric_analysis(discovery, client=object())
        self.assertEqual(partial["execution_status"], "partial")
        self.assertEqual(partial["comparison_status"], "available")
        self.assertIsNone(partial["rubric_analysis"])
        with patch("server.analysis.v2.pipeline.run_analysis_pipeline", return_value=partial):
            response = self.client.post("/api/analyze", data=json.dumps({"url": SOURCE_URL}),
                                        content_type="application/json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["analysis_status"]["execution_status"], "partial")
        self.assertNotIn("rubric", response.json())
