"""Offline contract cases for the integrated LLM and Android response."""

# AI-assisted Iteration 2 tests; all article content here is synthetic.
import json
import os
from copy import deepcopy
from pathlib import Path
from unittest.mock import patch

from django.test import Client, SimpleTestCase

from server.analysis.v2_adapter import PipelineContractError, adapt_v2_result
from server.analysis.v2.article_input import structure_article_body
from server.analysis.v2.display_selection import select_display_article_ids
from server.analysis.v2.pipeline import (_finish_discovery, _limit_discovery_for_display,
                                         complete_rubric_analysis, run_analysis_pipeline)


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


def reader_pipeline_result():
    """Synthetic, fully grounded article bodies including one astral character."""
    raw = deepcopy(pipeline_result())
    raw["source_analysis"]["claims"] = raw["source_analysis"]["claims"][:1]
    raw["selection"]["claim_comparisons"] = raw["selection"]["claim_comparisons"][:1]
    raw["selection"]["event_baseline_ids"] = []
    raw["rubric_analysis"] = None
    bodies = {
        "S0": "😀 관객 수가 늘었다. 새로운 계획도 발표됐다.",
        "A": "관객이 급증했다. 다른 사안도 설명했다.",
        "B": "관객 수에는 변화가 없었다.",
        "C": "표시되지 않을 기사 본문이다.",
    }
    raw["source_article"].update(body=bodies["S0"], **structure_article_body(bodies["S0"]))
    for candidate in raw["search"]["candidates"]:
        body = bodies[candidate["candidate_id"]]
        candidate.update(body=body, **structure_article_body(body))

    def evidence(article, quote):
        start = article["body"].index(quote)
        sentence = next(row for row in article["sentences"]
                        if row["start"] <= start < start + len(quote) <= row["end"])
        return {"sentence_index": sentence["sentence_index"],
                "paragraph_index": sentence["paragraph_index"],
                "start": start, "end": start + len(quote), "quote": quote}

    raw["source_analysis"]["claims"][0]["evidence"] = evidence(raw["source_article"], "관객 수가 늘었다")
    for match in raw["selection"]["claim_comparisons"][0]["articles"]:
        candidate = next(row for row in raw["search"]["candidates"]
                         if row["candidate_id"] == match["candidate_id"])
        quote = "관객이 급증했다" if match["candidate_id"] == "A" else "관객 수에는 변화가 없었다"
        match["evidence"] = evidence(candidate, quote)
    return raw, evidence


class AdapterTests(SimpleTestCase):
    def test_reader_exposes_displayed_full_bodies_and_utf16_sentence_pairs(self):
        raw, _ = reader_pipeline_result()
        reader = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        self.assertEqual(reader["scope"], "extracted_article_text")
        self.assertEqual(reader["coverage"], "verified_displayed_claims_only")
        self.assertEqual(reader["offset_unit"], "utf16")
        self.assertEqual(reader["i1_status"], "not_computed")
        self.assertEqual(reader["i1_stars"], [])
        self.assertEqual(reader["source_article"]["body"], raw["source_article"]["body"])
        self.assertEqual([row["article_id"] for row in reader["related_articles"]], ["A", "B"])
        self.assertNotIn("표시되지 않을", json.dumps(reader, ensure_ascii=False))
        self.assertEqual(len(reader["highlights"]), 1)
        highlight = reader["highlights"][0]
        source_sentence = raw["source_article"]["sentences"][0]["text"]
        self.assertEqual(highlight["source_span"], {
            "start": 0, "end": len(source_sentence.encode("utf-16-le")) // 2})
        self.assertEqual(len(highlight["pairs"]), 2)
        first = next(pair for pair in highlight["pairs"] if pair["article_id"] == "A")
        self.assertEqual(first["source_evidence_span"]["text"], "관객 수가 늘었다")
        self.assertEqual(first["source_evidence_span"]["start"], 3)  # 😀 takes two UTF-16 units.
        self.assertEqual(first["related_evidence_span"]["text"], "관객이 급증했다")
        self.assertEqual(first["alignment_scope"], "verified_claim_relation")
        self.assertEqual(first["expression_differences"], [])

    def test_reader_pair_ids_do_not_depend_on_candidate_display_ids(self):
        raw, _ = reader_pipeline_result()
        original = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        original_pair = next(pair for pair in original["highlights"][0]["pairs"] if pair["article_id"] == "A")
        raw["search"]["candidates"][0]["candidate_id"] = "A_changed"
        raw["selection"]["same_event_ids"][0] = "A_changed"
        raw["selection"]["claim_comparisons"][0]["articles"][0]["candidate_id"] = "A_changed"
        renamed = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        changed_pair = next(pair for pair in renamed["highlights"][0]["pairs"]
                            if pair["article_id"] == "A_changed")
        self.assertEqual(original_pair["pair_id"], changed_pair["pair_id"])
        self.assertEqual(original_pair["related_sentence_id"], changed_pair["related_sentence_id"])

    def test_reader_skips_unvalidated_quote_and_withheld_relation(self):
        raw, _ = reader_pipeline_result()
        raw["selection"]["claim_comparisons"][0]["articles"][0]["evidence"]["start"] += 1
        reader = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        self.assertEqual([pair["article_id"] for pair in reader["highlights"][0]["pairs"]], ["B"])

        raw, _ = reader_pipeline_result()
        raw["rubric_analysis"] = {"claim_analyses": [{"claim_id": "C1", "information": {
            "I1": [{"article_id": "A", "presence": "found", "relation": "opposes", "evidence": []}]},
            "expression": {"status": "analyzed", "findings": []}}]}
        reader = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        self.assertEqual([pair["article_id"] for pair in reader["highlights"][0]["pairs"]], ["B"])

    def test_reader_attaches_only_grounded_equivalent_expression_differences(self):
        raw, evidence = reader_pipeline_result()
        source = raw["source_article"]
        related = raw["search"]["candidates"][0]
        finding = {
            "article_id": "A", "baseline_article_id": "S0", "category": "D",
            "semantic_relation": "equivalent", "status": "provisional", "marker": "급증했다",
            "evidence": {"scope": "body", **evidence(related, "급증했다")},
            "baseline_evidence": {"scope": "body", **evidence(source, "늘었다")},
            "context_evidence": evidence(related, "관객이 급증했다"),
            "markedness": 2, "markedness_reason": "급증했다는 늘었다보다 강한 표현",
        }
        raw["rubric_analysis"] = {"claim_analyses": [{"claim_id": "C1", "information": {},
            "expression": {"status": "analyzed", "findings": [finding]}}]}
        reader = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        pair = next(pair for pair in reader["highlights"][0]["pairs"] if pair["article_id"] == "A")
        self.assertEqual(pair["alignment_scope"], "equivalent_expression")
        self.assertEqual(pair["expression_differences"][0]["code"], "D")
        self.assertEqual(pair["expression_differences"][0]["source_marker_span"]["text"], "늘었다")
        self.assertEqual(pair["expression_differences"][0]["related_marker_span"]["text"], "급증했다")

        finding["semantic_relation"] = "uncertain"
        reader = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        pair = next(pair for pair in reader["highlights"][0]["pairs"] if pair["article_id"] == "A")
        self.assertEqual(pair["expression_differences"], [])
        self.assertEqual(pair["alignment_scope"], "verified_claim_relation")

        finding["semantic_relation"] = "equivalent"
        finding["evidence"]["start"] += 1
        reader = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)["reader"]
        pair = next(pair for pair in reader["highlights"][0]["pairs"] if pair["article_id"] == "A")
        self.assertEqual(pair["expression_differences"], [])

    def test_display_selection_covers_distinct_claims_before_repeated_matches(self):
        selection = {"event_baseline_ids": ["E"], "claim_comparisons": [
            {"claim_id": "C1", "articles": [{"candidate_id": "A"}, {"candidate_id": "D"}]},
            {"claim_id": "C2", "articles": [{"candidate_id": "A"}, {"candidate_id": "D"}]},
            {"claim_id": "C3", "articles": [{"candidate_id": "B"}, {"candidate_id": "D"}]},
            {"claim_id": "C4", "articles": [{"candidate_id": "C"}]},
        ]}
        # A and D have more repeated matches, but C is the only C4 evidence.
        self.assertEqual(select_display_article_ids(selection, 2), ["D", "C"])

    def test_display_selection_avoids_syndicated_copies_when_coverage_ties(self):
        selection = {"event_baseline_ids": [], "claim_comparisons": [
            {"claim_id": "C1", "articles": [{"candidate_id": key} for key in ("A", "B", "C")]},
        ]}
        candidates = [
            {"candidate_id": "A", "publisher": "첫 언론사", "body": "시청은 오늘 새 교통 계획을 발표했다. 다음 달부터 시행한다."},
            {"candidate_id": "B", "publisher": "둘째 언론사", "body": "시청은 오늘 새 교통 계획을 발표했다. 다음 달부터 시행한다."},
            {"candidate_id": "C", "publisher": "셋째 언론사", "body": "주민은 새 교통 계획이 생활에 미칠 영향을 설명하며 보완책을 요구했다."},
        ]
        selected = select_display_article_ids(selection, 2, candidates)
        self.assertEqual(len(selected), 2)
        self.assertIn("C", selected)
        self.assertNotEqual(set(selected), {"A", "B"})

    def test_limited_rubric_scope_and_partial_status_are_explicit(self):
        raw = deepcopy(pipeline_result())
        raw["source_analysis"]["claims"].append(
            {"claim_id": "C3", "claim": "세 번째 주장", "evidence": {"quote": "세 번째 주장"}})
        raw["selection"]["claim_comparisons"].append({"claim_id": "C3", "articles": [
            {"candidate_id": "C", "relation": "same", "evidence": {"quote": "C 기사 세 번째 주장"}}]})
        raw["selection"]["event_baseline_ids"] = []
        limited = _limit_discovery_for_display(raw, 1)
        self.assertEqual(limited["display_selection"]["selected_article_ids"], ["A"])
        self.assertEqual(limited["display_selection"]["claims_without_displayed_comparison"], ["C3"])
        self.assertEqual([len(row["articles"]) for row in limited["selection"]["claim_comparisons"]], [1, 1, 0])
        self.assertEqual(_finish_discovery(limited)["execution_status"], "partial")
        self.assertIn("C3", " ".join(limited["warnings"]))

    @patch("server.analysis.v2.pipeline.prepare_article")
    @patch("server.analysis.v2.pipeline.run_discovery_pipeline")
    @patch("server.analysis.v2.pipeline.complete_rubric_analysis")
    def test_full_pipeline_limits_articles_before_rubric_call(self, rubric, discovery, prepare):
        raw = pipeline_result()
        raw["completed_stage"] = 4
        discovery.return_value = raw
        prepare.return_value = raw["source_article"]
        rubric.side_effect = lambda stage_four, **_kwargs: stage_four
        result = run_analysis_pipeline(SOURCE_URL, client=object(), use_cache=False, max_rubric_articles=1)
        seen = rubric.call_args.args[0]
        self.assertEqual(seen["display_selection"]["selected_article_ids"], ["A"])
        self.assertEqual([article["candidate_id"] for row in seen["selection"]["claim_comparisons"]
                          for article in row["articles"]], ["A", "A"])
        self.assertEqual(seen["selection"]["event_baseline_ids"], [])
        self.assertTrue(result["display_selection"]["truncated"])

    @patch("server.analysis.v2.pipeline.prepare_article")
    @patch("server.analysis.v2.pipeline.run_discovery_pipeline")
    @patch("server.analysis.v2.pipeline.complete_rubric_analysis")
    @patch("server.analysis.v2.pipeline.complete_reader_analysis")
    def test_reader_profile_skips_legacy_rubric(self, reader, rubric, discovery, prepare):
        raw = pipeline_result()
        raw["completed_stage"] = 4
        discovery.return_value = raw
        prepare.return_value = raw["source_article"]
        reader.side_effect = lambda stage_four, **_kwargs: stage_four
        run_analysis_pipeline(SOURCE_URL, client=object(), use_cache=False,
                              max_rubric_articles=1, analysis_profile="reader")
        rubric.assert_not_called()
        self.assertEqual(reader.call_args.args[0]["display_selection"]["selected_article_ids"], ["A"])

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
        self.assertEqual(rubric["expression_unverified_claim_ids"], [])
        expressions = rubric["expression_differences"]
        self.assertEqual(expressions[0]["categories"][0]["markedness"], 2)
        self.assertIsNone(expressions[1]["categories"][0]["markedness"])
        self.assertEqual(expressions[1]["status"], "needs_review")
        self.assertEqual(expressions[1]["baseline"]["source"], "")
        self.assertEqual(len(expressions), 2)  # Direct quote is excluded from A-G.
        information = rubric["information_differences"]
        self.assertTrue(any(row["code"] == "I2" and row["sentence"] == "A 기사 관객 수가 늘었다" for row in information))
        self.assertTrue(any(row["code"] == "I5" and row["sentence"] == "관계자는 새 기록이라고 말했다" for row in information))

    def test_partial_rubric_marks_failed_article_unverified_without_scores(self):
        raw = deepcopy(pipeline_result())
        raw["rubric_analysis"]["article_annotation_failures"] = [
            {"article_id": "B", "phase": "article_information_I1_I5",
             "reason": "validation_failed", "error_type": "ModelResponseError"}]
        # Even inconsistent stale rows must not become verified UI cards.
        result = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)
        self.assertEqual(result["analysis_status"]["execution_status"], "partial")
        rubric = result["rubric"]
        failed = [row for row in rubric["information_differences"] if row["article_index"] == 2]
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0]["code"], "I1")
        self.assertIsNone(failed[0]["sentence"])
        self.assertIn("미판정", failed[0]["value"])
        self.assertNotIn("누락 의도", failed[0]["value"])
        self.assertFalse(any(row["article_index"] == 2 for row in rubric["expression_differences"]))
        self.assertTrue(any(row["article_index"] == 1 and row["code"] == "I1"
                            for row in rubric["information_differences"]))

    def test_failed_claim_expression_withholds_stale_scores_but_keeps_information(self):
        raw = deepcopy(pipeline_result())
        later_claim = deepcopy(raw["rubric_analysis"]["claim_analyses"][0])
        later_claim["claim_id"] = "C2"
        raw["rubric_analysis"]["claim_analyses"].append(later_claim)
        raw["rubric_analysis"]["expression_failures"] = [
            {"claim_id": "C1", "phase": "claim_expression_A_G",
             "reason": "validation_failed", "error_type": "ModelResponseError"}]
        raw["warnings"] = ["[C1] A~G는 미판정이며 검증된 I1~I5는 유지합니다."]
        raw["errors"] = [{"stage": "rubric_analysis", "code": "RUBRIC_EXPRESSION_UNVERIFIED",
                          "message": "주장별 A~G 표현 근거를 검증하지 못했습니다.", "retryable": False}]
        # The synthetic raw result deliberately retains stale C1 scores as a defensive contract test.
        result = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)
        self.assertEqual(result["analysis_status"]["execution_status"], "partial")
        self.assertEqual(result["analysis_status"]["errors"][0]["code"], "RUBRIC_EXPRESSION_UNVERIFIED")
        self.assertIn("미판정", result["analysis_status"]["warnings"][0])
        rubric = result["rubric"]
        self.assertEqual(rubric["expression_unverified_claim_ids"], ["C1"])
        self.assertTrue(any(row["claim_id"] == "C1" and row["code"] == "I1"
                            for row in rubric["information_differences"]))
        self.assertFalse(any(row["claim_id"] == "C1" for row in rubric["expression_differences"]))
        self.assertTrue(any(row["claim_id"] == "C2" for row in rubric["expression_differences"]))

    def test_unverified_expression_status_without_failure_row_is_partial(self):
        raw = deepcopy(pipeline_result())
        claim = raw["rubric_analysis"]["claim_analyses"][0]
        claim["expression"]["status"] = "unverified"

        result = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)

        self.assertEqual(result["analysis_status"]["execution_status"], "partial")
        self.assertEqual(result["rubric"]["expression_unverified_claim_ids"], ["C1"])
        self.assertFalse(result["rubric"]["expression_differences"])
        self.assertTrue(any(row["claim_id"] == "C1" and row["code"] == "I1"
                            for row in result["rubric"]["information_differences"]))

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
        withheld_i1 = next(row for row in result["rubric"]["information_differences"]
                           if row["code"] == "I1" and row["claim_id"] == "C1" and row["article_index"] == 2)
        self.assertIn("보류", withheld_i1["value"])
        self.assertEqual(withheld_i1["sentence"], "B 기사는 다른 수치를 보도했다")
        self.assertFalse(any(row["code"] == "I2" and row["article_index"] == 2
                             for row in result["rubric"]["information_differences"]))

    def test_conflicting_found_relations_are_withheld_in_issue_and_i1(self):
        raw = pipeline_result()
        raw["rubric_analysis"]["claim_analyses"][0]["information"]["I1"][1]["relation"] = "opposes"
        result = adapt_v2_result(raw, max_related=2, requested_url=SOURCE_URL)
        member = result["comparison"]["issues"][0]["members"][1]
        self.assertEqual(member["relation_to_source"], "uncertain")
        i1 = next(row for row in result["rubric"]["information_differences"]
                  if row["code"] == "I1" and row["claim_id"] == "C1" and row["article_index"] == 1)
        self.assertIn("보류", i1["value"])
        self.assertNotIn("반박", i1["value"])

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
        i1 = next(row for row in result["rubric"]["information_differences"]
                  if row["code"] == "I1" and row["claim_id"] == "C1" and row["article_index"] == 1)
        self.assertIn("보류", i1["value"])
        self.assertNotIn("같은 쟁점", i1["value"])

    def test_later_found_passage_in_other_claims_display_article_is_not_hidden(self):
        raw = pipeline_result()
        raw["selection"]["event_baseline_ids"] = []
        raw["selection"]["claim_comparisons"][0]["articles"] = []
        raw["selection"]["claim_comparisons"][1]["articles"] = [{
            "candidate_id": "C", "title": "C 기사", "url": "https://news.example.com/c",
            "relation": "same", "evidence": {"quote": "C 기사는 새 계획을 보도했다"}}]
        raw["rubric_analysis"]["claim_analyses"][0]["information"]["I1"].append(
            {"article_id": "C", "presence": "found", "relation": "same_issue",
             "evidence": [{"quote": "C 기사는 관객 수의 다른 면을 보도했다"}]})
        result = adapt_v2_result(raw, max_related=1, requested_url=SOURCE_URL)
        member = result["comparison"]["issues"][0]["members"][1]
        self.assertEqual(member["candidate_id"], "C")
        self.assertEqual(member["relation_to_source"], "uncertain")
        self.assertEqual(member["evidence"]["quote"], "C 기사는 관객 수의 다른 면을 보도했다")
        self.assertEqual(result["analysis_status"]["execution_status"], "partial")
        i1 = next(row for row in result["rubric"]["information_differences"]
                  if row["code"] == "I1" and row["claim_id"] == "C1" and row["article_index"] == 1)
        self.assertIn("보류", i1["value"])

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

    @patch("server.analysis.v2.pipeline.run_analysis_pipeline")
    def test_v2_limits_rubric_to_displayed_article_count(self, pipeline):
        from server.views import analyze_related_articles_v2
        pipeline.return_value = pipeline_result()
        result = analyze_related_articles_v2(SOURCE_URL, max_related=2)
        self.assertEqual(len(result["related_articles"]), 2)
        self.assertEqual(pipeline.call_args.kwargs["max_rubric_articles"], 2)

    @patch("server.analysis.v2.pipeline.run_analysis_pipeline")
    def test_terminal_failure_is_not_masked_by_earlier_partial_error(self, pipeline):
        from server.views import PipelineResultError, analyze_related_articles_v2
        raw = pipeline_result()
        raw["execution_status"] = "failed"
        raw["source_analysis"] = None
        raw["errors"] = [
            {"stage": "search", "code": "SEARCH_TIMEOUT", "message": "검색 지연", "retryable": True},
            {"stage": "source_analysis", "code": "ANALYSIS_RESPONSE_INVALID",
             "message": "분석 형식 오류", "retryable": False},
            {"stage": "client_close", "code": "PROCESSING_FAILED",
             "message": "종료 오류", "retryable": False},
        ]
        pipeline.return_value = raw
        with self.assertRaises(PipelineResultError) as raised:
            analyze_related_articles_v2(SOURCE_URL)
        self.assertEqual(raised.exception.code, "analysis_response_invalid")
        self.assertFalse(raised.exception.retryable)

    @patch("server.analysis.v2.pipeline.run_analysis_pipeline")
    def test_unavailable_comparison_is_retryable_if_any_search_error_can_recover(self, pipeline):
        from server.views import PipelineResultError, analyze_related_articles_v2
        raw = pipeline_result()
        raw["comparison_status"] = "unavailable"
        raw["errors"] = [
            {"stage": "search", "code": "SEARCH_TIMEOUT", "message": "검색 지연", "retryable": True},
            {"stage": "candidate_fetch", "code": "ARTICLE_BODY_NOT_FOUND",
             "message": "본문 없음", "retryable": False},
        ]
        pipeline.return_value = raw
        with self.assertRaises(PipelineResultError) as raised:
            analyze_related_articles_v2(SOURCE_URL)
        self.assertTrue(raised.exception.retryable)

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
