"""Offline contract checks for source-wide Reader analysis."""

from __future__ import annotations

import unittest
from collections import Counter
from threading import Barrier, Lock, local
from unittest.mock import patch

from server.analysis.reader_analysis import (
    analyze_reader_content, _review_absence, _safe_expression_marker,
)
from server.analysis.reader_contract import build_reader
from server.analysis.v2.article_input import structure_article_body
from server.analysis.v2.model_client import MetricsJSONClient, ModelResponseError, OpenRouterJSONClient


def article(article_id: str, body: str) -> dict:
    return {"candidate_id": article_id, "title": f"{article_id} 기사",
            "url": f"https://example.com/{article_id}", "publisher": article_id,
            "body": body, **structure_article_body(body)}


def fixture() -> dict:
    source = article("S0", "시청은 새 다리를 착공했다.\n예산은 100억원이다.")
    a = article("A", "시청은 새 다리 공사를 밀어붙였다.\n주민은 기대한다고 말했다.")
    b = article("B", "시청은 새 다리 공사를 시작했다.\n공사 현장에 인력이 배치됐다.")
    return {
        "source_article": source,
        "source_analysis": {"core_event": "시청의 새 다리 착공", "claims": [{
            "claim_id": "C1", "claim": "시청은 새 다리를 착공했다",
            "evidence": {"sentence_index": 0, "quote": "시청은 새 다리를 착공했다"},
        }]},
        "selection": {"same_event_ids": ["A", "B"],
                      "independent_same_event_ids": ["A", "B"],
                      "duplicate_article_groups": [], "source_duplicate_article_ids": []},
        "search": {"candidates": [a, b]},
    }


class ReaderClient:
    def __init__(self, *, refute: bool = False, fail_article: str | None = None,
                 bad_ledger: bool = False, review_uncertain: bool = False,
                 alignment_difference: bool = True,
                 expression_mode: str = "none") -> None:
        self.refute = refute
        self.fail_article = fail_article
        self.bad_ledger = bad_ledger
        self.review_uncertain = review_uncertain
        self.alignment_difference = alignment_difference
        self.expression_mode = expression_mode
        self.calls = []

    def generate_json(self, **request: object) -> dict:
        name = request["name"]
        self.calls.append(name)
        data = request["input_data"]
        if name in {"source_content_units", "repair_source_content_units"}:
            rows = [{"sentence_index": 0, "scope": "central", "units": [{
                "content": "시청이 새 다리를 착공했다", "kind": "reported_fact",
                "evidence": {"sentence_index": 0, "quote": "시청은 새 다리를 착공했다"},
            }]}, {"sentence_index": 1, "scope": "central", "units": [{
                "content": "새 다리 예산은 100억원이다", "kind": "reported_fact",
                "evidence": {"sentence_index": 1, "quote": "예산은 100억원이다"},
            }]}]
            return {"sentences": rows[:1] if self.bad_ledger else rows}
        if name in {"related_content_alignment", "repair_related_content_alignment"}:
            key = data["article_id"]
            if key == self.fail_article:
                raise TimeoutError("model unavailable")
            units = data["source_units"]
            rows = []
            for unit in units:
                first = unit["source_evidence"]["sentence_index"] == 0
                relation = ("refutes" if self.refute and key == "B" and not first else
                            "reframes" if first and key == "A" else "same" if first else "absent")
                evidence = ({"sentence_index": 0, "quote":
                             "시청은 새 다리 공사를 밀어붙였다" if key == "A" else
                             "시청은 새 다리 공사를 시작했다"} if first else
                            {"sentence_index": 1, "quote": "공사 현장에 인력이 배치됐다"}
                            if relation == "refutes" else None)
                difference = ([{"code": "C", "source_marker": {"sentence_index": 0, "quote": "착공했다"},
                                "related_marker": {"sentence_index": 0, "quote": "밀어붙였다"},
                                "note": "행위 동사가 더 강한 평가를 담는다", "voice": "reporter"}]
                              if first and key == "A" and self.alignment_difference else [])
                rows.append({"unit_id": unit["unit_id"], "relation": relation,
                             "related_evidence": evidence, "difference_summary": "표현 차이" if difference else "",
                             "expression_differences": difference})
            return {"unit_alignments": rows}
        if name == "expression_pair_A_G":
            if self.expression_mode == "fail":
                raise TimeoutError("model unavailable")
            if self.expression_mode == "none":
                return {"findings": []}
            pair = next(pair for pair in data["pairs"] if pair["article_id"] == "A"
                        and pair["source_sentence_index"] == 0)
            return {"findings": [{
                "pair_id": pair["pair_id"], "unit_id": pair["units"][0]["unit_id"],
                "code": "C", "source_marker": {"sentence_index": 0, "quote":
                    "없는 표현" if self.expression_mode == "invalid" else "착공했다"},
                "related_marker": {"sentence_index": 0, "quote": "밀어붙였다"},
                "note": "행위 동사의 강조가 다르다", "voice": "reporter",
                "quoted_use": "none", "name_kind": "other", "semantic_relation": "equivalent",
            }]}
        if name == "i1_absence_review":
            return {"checks": [{"unit_id": unit["unit_id"], "article_checks": {
                key: {"status": "uncertain" if self.review_uncertain else "absent", "evidence": None}
                for key in data["articles"]}} for unit in data["source_units"]]}
        raise AssertionError(f"unexpected task: {name}")


class ReaderAnalysisTests(unittest.TestCase):
    def test_uncached_openrouter_alignments_run_together_with_ordered_results_and_usage(self):
        class ConcurrentReaderClient(ReaderClient, OpenRouterJSONClient):
            def __init__(self):
                ReaderClient.__init__(self)
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self._usage_lock = Lock()
                self._usage_totals = Counter()
                self._metered_responses = self._cost_reported_responses = 0
                self._barrier = Barrier(2)
                self._active_lock = Lock()
                self._active = self.max_active = 0

            def generate_json(self, **request):
                self.last_call_usage = {}
                self.last_call_finish_reason = None
                if request["name"] == "related_content_alignment":
                    with self._active_lock:
                        self._active += 1
                        self.max_active = max(self.max_active, self._active)
                    try:
                        self._barrier.wait(timeout=5)
                    finally:
                        with self._active_lock:
                            self._active -= 1
                    tokens = 10 if request["input_data"]["article_id"] == "A" else 20
                    self._capture_usage({"usage": {"prompt_tokens": tokens,
                                                    "completion_tokens": 2,
                                                    "total_tokens": tokens + 2,
                                                    "cost": tokens / 1000}})
                    self.last_call_finish_reason = "stop"
                return ReaderClient.generate_json(self, **request)

        provider = ConcurrentReaderClient()
        recorder = MetricsJSONClient(provider)
        result = analyze_reader_content(fixture(), ["A", "B"], client=recorder)
        self.assertEqual(provider.max_active, 2)
        self.assertEqual(list(result["article_alignments"]), ["A", "B"])
        self.assertEqual(result["llm_calls"], 5)
        summary = recorder.execution_summary()
        self.assertEqual(summary["attempted_calls"], 5)
        self.assertEqual(summary["model_calls"], 5)
        self.assertEqual(summary["provider_usage"]["metered_responses"], 2)
        self.assertEqual(summary["provider_usage"]["input_tokens"], 30)
        self.assertAlmostEqual(summary["provider_usage"]["reported_cost_usd"], 0.03)
        provider.fail_article = "B"
        failed = analyze_reader_content(fixture(), ["A", "B"], client=MetricsJSONClient(provider))
        self.assertEqual(list(failed["article_alignments"]), ["A"])
        self.assertEqual([row["article_id"] for row in failed["article_failures"]], ["B"])
        self.assertEqual(failed["i1_status"], "withheld")

    def test_full_article_alignment_and_source_only_star(self):
        raw = fixture()
        client = ReaderClient()
        result = analyze_reader_content(raw, ["A", "B"], client=client)
        self.assertEqual(result["coverage"], "all_source_sentences_reviewed")
        self.assertEqual(len(result["source_sentence_ledger"]), 2)
        self.assertEqual(len(result["source_units"]), 2)
        self.assertEqual(result["i1_status"], "complete")
        self.assertEqual(len(result["i1_stars"]), 1)
        self.assertEqual(result["i1_stars"][0]["checked_article_count"], 2)
        self.assertEqual(client.calls, ["source_content_units", "related_content_alignment",
                                        "related_content_alignment", "expression_pair_A_G",
                                        "i1_absence_review"])
        raw["reader_analysis"] = result
        reader = build_reader(raw, ["A", "B"], {row["candidate_id"]: row for row in raw["search"]["candidates"]},
                              {"S0": 0, "A": 1, "B": 2}, {})
        self.assertEqual(reader["coverage"], "all_source_sentences_reviewed")
        self.assertEqual(reader["highlights"][0]["categories"], ["C"])
        self.assertEqual(reader["highlights"][0]["pairs"][0]["article_id"], "A")
        self.assertEqual(reader["i1_stars"][0]["source_span"]["start"],
                         reader["source_article"]["sentences"][1]["start"])
        self.assertIn("이번 비교 기사 2건", reader["i1_stars"][0]["label"])

    def test_refutation_counts_as_observed_and_prevents_star(self):
        raw = fixture()
        result = analyze_reader_content(raw, ["A", "B"], client=ReaderClient(refute=True))
        self.assertEqual(result["i1_stars"], [])
        self.assertEqual(result["i1_status"], "complete")

    def test_expression_recall_adds_valid_grounded_pair_and_deduplicates(self):
        raw = fixture()
        recalled = analyze_reader_content(raw, ["A", "B"], client=ReaderClient(
            alignment_difference=False, expression_mode="valid"))
        a_row = recalled["article_alignments"]["A"]["unit_alignments"][0]
        self.assertEqual([item["code"] for item in a_row["expression_differences"]], ["C"])
        self.assertEqual(recalled["expression_review"]["accepted_finding_count"], 1)
        existing = analyze_reader_content(raw, ["A", "B"], client=ReaderClient(expression_mode="valid"))
        a_row = existing["article_alignments"]["A"]["unit_alignments"][0]
        self.assertEqual(len(a_row["expression_differences"]), 1)
        self.assertEqual(existing["expression_review"]["accepted_finding_count"], 0)

    def test_expression_recall_withholds_invalid_marker_or_failed_call(self):
        raw = fixture()
        invalid = analyze_reader_content(raw, ["A", "B"], client=ReaderClient(
            alignment_difference=False, expression_mode="invalid"))
        self.assertEqual(invalid["article_alignments"]["A"]["unit_alignments"][0]["expression_differences"], [])
        self.assertEqual(invalid["expression_review"]["withheld_finding_count"], 1)
        failed = analyze_reader_content(raw, ["A", "B"], client=ReaderClient(
            alignment_difference=False, expression_mode="fail"))
        self.assertEqual(failed["expression_review"]["status"], "failed")
        self.assertEqual(failed["i1_status"], "complete")

    def test_direct_speech_marker_is_withheld_but_short_scare_quote_can_be_named(self):
        quoted = article("Q", '기자는 "졸속"이라고 말했다.')
        span = {"start": quoted["body"].index("졸속"),
                "end": quoted["body"].index("졸속") + 2, "sentence_index": 0}
        self.assertFalse(_safe_expression_marker(quoted, span, "E", "none"))
        self.assertFalse(_safe_expression_marker(quoted, span, "F", "scare_quote"))
        label = article("L", '기사에는 "개악" 법안이라는 명칭이 등장했다.')
        label_span = {"start": label["body"].index("개악"),
                      "end": label["body"].index("개악") + 2, "sentence_index": 0}
        self.assertTrue(_safe_expression_marker(label, label_span, "F", "scare_quote"))

    def test_expression_pass_is_bounded_and_reports_unreviewed_pairs(self):
        with patch("server.analysis.reader_analysis.EXPRESSION_PAIR_LIMIT", 1):
            result = analyze_reader_content(fixture(), ["A", "B"], client=ReaderClient())
        self.assertEqual(result["expression_review"]["reviewed_pair_count"], 1)
        self.assertEqual(result["expression_review"]["omitted_pair_count"], 1)
        self.assertEqual(result["expression_review"]["status"], "partial")

    def test_title_only_possible_match_withholds_star(self):
        raw = fixture()
        raw["search"]["candidates"][0]["title"] = "새 다리 예산 100억원"
        result = analyze_reader_content(raw, ["A", "B"], client=ReaderClient())
        self.assertEqual(result["i1_stars"], [])
        self.assertEqual(result["i1_status"], "partial")
        self.assertEqual(len(result["i1_title_ambiguous_unit_ids"]), 1)

    def test_absence_review_accepts_grounded_title_evidence_as_observed(self):
        raw = fixture()
        raw["search"]["candidates"][0]["title"] = "예산은 100억원이다"
        source = raw["source_article"]
        unit = {"unit_id": "u1", "content": "새 다리 예산은 100억원이다",
                "source_evidence": {"sentence_index": 1, "quote": "예산은 100억원이다"}}
        related = {row["candidate_id"]: row for row in raw["search"]["candidates"]}

        class TitleReviewClient:
            def generate_json(self, **request):
                return {"checks": [{"unit_id": "u1", "article_checks": {
                    "A": {"status": "observed", "evidence": {
                        "scope": "title", "sentence_index": None, "quote": "예산은 100억원이다"}},
                    "B": {"status": "absent", "evidence": None},
                }}]}

        checks, calls = _review_absence(source, related, [unit], [["A"], ["B"]], TitleReviewClient())
        self.assertEqual(calls, 1)
        self.assertEqual(checks["u1"]["A"], "observed")

    def test_duplicate_articles_do_not_satisfy_two_independent_comparisons(self):
        raw = fixture()
        raw["selection"]["duplicate_article_groups"] = [["A", "B"]]
        result = analyze_reader_content(raw, ["A", "B"], client=ReaderClient())
        self.assertEqual(len(result["independent_article_groups"]), 1)
        self.assertEqual(result["i1_stars"], [])
        self.assertEqual(result["i1_status"], "withheld")

    def test_failed_or_uncertain_comparison_withholds_star(self):
        raw = fixture()
        failed = analyze_reader_content(raw, ["A", "B"], client=ReaderClient(fail_article="B"))
        self.assertEqual(failed["i1_stars"], [])
        self.assertEqual(failed["i1_status"], "withheld")
        self.assertEqual(failed["article_failures"][0]["article_id"], "B")
        uncertain = analyze_reader_content(raw, ["A", "B"], client=ReaderClient(review_uncertain=True))
        self.assertEqual(uncertain["i1_stars"], [])

    def test_incomplete_source_ledger_is_rejected_even_after_repair(self):
        with self.assertRaises(ModelResponseError):
            analyze_reader_content(fixture(), ["A", "B"], client=ReaderClient(bad_ledger=True))


if __name__ == "__main__":
    unittest.main()
