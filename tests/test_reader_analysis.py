"""Offline contract checks for source-wide Reader analysis."""

from __future__ import annotations

import unittest
from collections import Counter
from threading import Barrier, Event, Lock, local
from time import monotonic, sleep
from unittest.mock import patch

from server.analysis.reader_analysis import (
    analyze_reader_content, _align_article, _alignment_chunk, _extract_source_units, _neutral_a_pair,
    _review_absence, _review_expression_pairs, _safe_expression_marker,
)
from server.analysis.reader_contract import build_reader
from server.analysis.v2.article_input import structure_article_body
from server.analysis.v2.model_client import (
    MetricsJSONClient, ModelHTTPError, ModelResponseError, ModelResponseTruncatedError,
    OpenRouterJSONClient,
)


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


def expression_fixture(count: int = 9) -> tuple[dict, dict, list[dict], dict]:
    source = article("S0", "\n".join(f"시청은 새 다리 {i}를 착공했다." for i in range(count)))
    related = article("A", "\n".join(f"시청은 새 다리 {i} 공사를 밀어붙였다." for i in range(count)))
    units = [{"unit_id": f"u_{i}", "content": f"새 다리 {i} 착공",
              "source_evidence": {"sentence_index": i, "quote": source["sentences"][i]["text"]}}
             for i in range(count)]
    alignments = {"A": {"unit_alignments": [{
        "unit_id": f"u_{i}", "relation": "same",
        "related_evidence": {"sentence_index": i, "quote": related["sentences"][i]["text"]},
        "expression_differences": [],
    } for i in range(count)]}}
    return source, {"A": related}, units, alignments


def expression_findings(pairs: list[dict]) -> dict:
    return {"findings": [{
        "pair_id": pair["pair_id"], "unit_id": pair["units"][0]["unit_id"],
        "code": "C", "source_marker": {
            "sentence_index": pair["source_sentence_index"], "quote": "착공했다"},
        "related_marker": {
            "sentence_index": pair["related_sentence_index"], "quote": "밀어붙였다"},
        "note": "행위를 더 강하게 표현했다", "voice": "reporter", "quoted_use": "none",
        "name_kind": "other", "semantic_relation": "equivalent",
    } for pair in pairs]}


class ChunkReviewClient:
    def __init__(self, *, fail_at: int | None = None) -> None:
        self.fail_at = fail_at
        self.requests = []

    def generate_json(self, **request) -> dict:
        self.requests.append(request)
        pairs = request["input_data"]["pairs"]
        if pairs[0]["source_sentence_index"] == self.fail_at:
            raise ModelResponseTruncatedError("synthetic output limit")
        return expression_findings(pairs)


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
    def test_same_event_supporting_sentences_feed_expression_and_i1_but_separate_event_does_not(self):
        source = article("S0", "시청은 새 다리 공사를 시작했다.\n주민들은 공사 일정에 반발했다.\n"
                         "시청은 야간 작업을 허용했다.\n인근 체육관은 지난해 문을 열었다.")
        related_a = article("A", "시청은 새 다리 공사를 시작했다.\n주민들은 공사 일정에 거세게 반발했다.")
        related_b = article("B", "시청은 새 다리 공사를 시작했다.\n주민들은 공사 일정에 반발했다.")
        raw = {"source_article": source,
               "source_analysis": {"core_event": "시청의 새 다리 공사", "claims": [{
                   "claim_id": "C1", "claim": "시청이 새 다리 공사를 시작했다",
                   "evidence": {"sentence_index": 0, "quote": source["sentences"][0]["text"]}}]},
               "selection": {"same_event_ids": ["A", "B"],
                             "independent_same_event_ids": ["A", "B"],
                             "duplicate_article_groups": [], "source_duplicate_article_ids": []},
               "search": {"candidates": [related_a, related_b]}}

        class SupportingClient:
            def generate_json(self, **request):
                name, data = request["name"], request["input_data"]
                if name == "source_content_units":
                    scopes = ("central", "supporting", "supporting", "separate")
                    return {"sentences": [{
                        "sentence_index": item["sentence_index"], "scope": scopes[item["sentence_index"]],
                        "units": [] if item["sentence_index"] == 3 else [{
                            "content": item["text"], "kind": "reported_fact",
                            "evidence": {"sentence_index": item["sentence_index"], "quote": item["text"]},
                        }],
                    } for item in data["article"]["sentences"]]}
                if name == "related_content_alignment":
                    key = data["article_id"]
                    rows = []
                    for unit in data["source_units"]:
                        index = unit["source_evidence"]["sentence_index"]
                        observed = index in (0, 1)
                        related = related_a if key == "A" else related_b
                        rows.append({
                            "unit_id": unit["unit_id"], "relation": "same" if observed else "absent",
                            "related_evidence": ({"sentence_index": index,
                                                  "quote": related["sentences"][index]["text"]}
                                                 if observed else None),
                            "difference_summary": "반발의 정도가 다르다" if key == "A" and index == 1 else "",
                            "expression_differences": ([{
                                "code": "D", "source_marker": {"sentence_index": 1, "quote": "반발했다"},
                                "related_marker": {"sentence_index": 1, "quote": "거세게 반발했다"},
                                "note": "반발의 정도를 더 강하게 썼다", "voice": "reporter",
                            }] if key == "A" and index == 1 else []),
                        })
                    return {"unit_alignments": rows}
                if name == "i1_absence_review":
                    return {"checks": [{"unit_id": unit["unit_id"], "article_checks": {
                        key: {"status": "absent", "evidence": None} for key in data["articles"]}}
                        for unit in data["source_units"]]}
                if name == "expression_pair_A_G":
                    return {"findings": []}
                raise AssertionError(name)

        result = analyze_reader_content(raw, ["A", "B"], client=SupportingClient())
        self.assertEqual([row["scope"] for row in result["source_sentence_ledger"]],
                         ["central", "supporting", "supporting", "separate"])
        self.assertEqual([unit["source_evidence"]["sentence_index"] for unit in result["source_units"]],
                         [0, 1, 2])
        self.assertEqual(result["i1_status"], "complete")
        self.assertEqual([star["source_evidence"]["sentence_index"] for star in result["i1_stars"]], [2])
        raw["reader_analysis"] = result
        reader = build_reader(raw, ["A", "B"], {"A": related_a, "B": related_b},
                              {"S0": 0, "A": 1, "B": 2}, {})
        self.assertEqual([row["source_span"]["start"] for row in reader["highlights"]],
                         [reader["source_article"]["sentences"][1]["start"]])
        self.assertEqual(reader["highlights"][0]["categories"], ["D"])
        self.assertEqual(reader["i1_stars"][0]["source_span"]["start"],
                         reader["source_article"]["sentences"][2]["start"])
        self.assertFalse(any(row["unit_ids"] for row in result["source_sentence_ledger"][3:]))

    def test_source_chunk_boundary_passes_neighbor_context_without_output_ids(self):
        source, analysis = self._source_unit_case(10)

        class ContextClient:
            def __init__(self):
                self.requests = []

            def generate_json(self, **request):
                self.requests.append(request)
                return ReaderAnalysisTests._source_unit_response(request)

        client = ContextClient()
        ledger, units, _ = _extract_source_units(source, analysis, client)
        self.assertEqual([row["sentence_index"] for row in ledger], list(range(10)))
        self.assertEqual(len(units), 10)
        self.assertEqual([[row["sentence_index"] for row in request["input_data"]["article"]["sentences"]]
                          for request in client.requests], [list(range(5)), list(range(5, 10))])
        self.assertEqual([[row["sentence_index"] for row in request["input_data"]["context_sentences"]]
                          for request in client.requests], [[5, 6], [3, 4]])
        self.assertEqual([request["schema"]["properties"]["sentences"]["items"]
                          ["properties"]["sentence_index"]["enum"] for request in client.requests],
                         [list(range(5)), list(range(5, 10))])

    def test_supporting_scope_without_grounded_unit_cannot_silently_pass(self):
        source = article("S0", "시청은 새 다리 공사를 시작했다.\n시청은 야간 작업을 허용했다.")
        analysis = {"core_event": "시청의 새 다리 공사", "claims": [{
            "claim": "시청이 공사를 시작했다",
            "evidence": {"sentence_index": 0, "quote": source["sentences"][0]["text"]}}]}

        class EmptySupportingClient:
            def __init__(self):
                self.calls = []

            def generate_json(self, **request):
                self.calls.append(request["name"])
                return {"sentences": [{
                    "sentence_index": 0, "scope": "central", "units": [{
                        "content": source["sentences"][0]["text"], "kind": "reported_fact",
                        "evidence": {"sentence_index": 0, "quote": source["sentences"][0]["text"]},
                    }]}, {"sentence_index": 1, "scope": "supporting", "units": []}]}

        client = EmptySupportingClient()
        with self.assertRaises(ModelResponseError):
            _extract_source_units(source, analysis, client)
        self.assertEqual(client.calls, ["source_content_units", "repair_source_content_units"])

    def test_alignment_truncation_splits_units_and_keeps_complete_article(self):
        source, catalog, units, _ = expression_fixture(4)
        units = [{**unit, "kind": "reported_fact"} for unit in units]
        related = catalog["A"]

        class TruncatingClient:
            def __init__(self):
                self.batch_sizes = []

            def generate_json(self, **request):
                chosen = request["input_data"]["source_units"]
                self.batch_sizes.append(len(chosen))
                if len(chosen) == 4:
                    raise ModelResponseTruncatedError("synthetic output limit")
                return {"unit_alignments": [{
                    "unit_id": unit["unit_id"], "relation": "same",
                    "related_evidence": {"sentence_index": index, "quote": related["sentences"][index]["text"]},
                    "difference_summary": "", "expression_differences": [],
                } for unit in chosen for index in [unit["source_evidence"]["sentence_index"]]]}

        client = TruncatingClient()
        rows, calls = _align_article(source, related, units, "A", client)
        self.assertEqual((calls, client.batch_sizes), (3, [4, 2, 2]))
        self.assertEqual([row["unit_id"] for row in rows], [unit["unit_id"] for unit in units])

    def test_34_source_units_require_two_complete_batches_for_each_related_article(self):
        source, catalog, units, _ = expression_fixture(34)
        catalog["B"] = article("B", catalog["A"]["body"])
        units = [{**unit, "kind": "reported_fact"} for unit in units]

        class BatchClient:
            def __init__(self):
                self.batches = []

            def generate_json(self, **request):
                data = request["input_data"]
                chosen = data["source_units"]
                self.batches.append((data["article_id"], len(chosen)))
                related = catalog[data["article_id"]]
                return {"unit_alignments": [{
                    "unit_id": unit["unit_id"], "relation": "same",
                    "related_evidence": {"sentence_index": unit["source_evidence"]["sentence_index"],
                                         "quote": related["sentences"][unit["source_evidence"]["sentence_index"]]["text"]},
                    "difference_summary": "", "expression_differences": [],
                } for unit in chosen]}

        client = BatchClient()
        for key in ("A", "B"):
            rows, calls = _align_article(source, catalog[key], units, key, client)
            self.assertEqual((len(rows), calls), (34, 2))
        self.assertEqual(client.batches, [("A", 18), ("A", 16), ("B", 18), ("B", 16)])

    def test_single_unit_alignment_truncation_retries_larger_then_fails_closed(self):
        source, catalog, units, _ = expression_fixture(1)
        unit = {**units[0], "kind": "reported_fact"}
        related = catalog["A"]

        class LargerOutputClient:
            def __init__(self, *, always_truncate=False):
                self.limits = []
                self.always_truncate = always_truncate

            def generate_json(self, **request):
                self.limits.append(request["max_output_tokens"])
                if self.always_truncate or request["max_output_tokens"] == 8192:
                    raise ModelResponseTruncatedError("synthetic output limit")
                return {"unit_alignments": [{
                    "unit_id": unit["unit_id"], "relation": "same",
                    "related_evidence": {"sentence_index": 0, "quote": related["sentences"][0]["text"]},
                    "difference_summary": "", "expression_differences": [],
                }]}

        recovered = LargerOutputClient()
        rows, calls = _align_article(source, related, [unit], "A", recovered)
        self.assertEqual((len(rows), calls, recovered.limits), (1, 2, [8192, 16384]))
        failed = LargerOutputClient(always_truncate=True)
        with self.assertRaises(ModelResponseTruncatedError):
            _align_article(source, related, [unit], "A", failed)
        self.assertEqual(failed.limits, [8192, 16384])

    def test_prefetched_source_units_preserve_reader_result_without_repeating_call(self):
        baseline = analyze_reader_content(fixture(), ["A", "B"], client=ReaderClient())
        discovery = fixture()
        client = ReaderClient()
        precomputed = _extract_source_units(
            discovery["source_article"], discovery["source_analysis"], client)
        with patch("server.analysis.reader_analysis._extract_source_units",
                   side_effect=AssertionError("source extraction must not run twice")):
            result = analyze_reader_content(
                discovery, ["A", "B"], client=client, precomputed_source_units=precomputed)
        self.assertEqual(result, baseline)

    @staticmethod
    def _source_unit_case(sentence_count: int) -> tuple[dict, dict]:
        source = article("S0", "\n".join(
            f"시청은 {i}번 다리의 공사를 시작했다." for i in range(sentence_count)))
        analysis = {"core_event": "다리 공사", "claims": [{
            "claim": "시청은 0번 다리의 공사를 시작했다",
            "evidence": {"sentence_index": 0, "quote": source["sentences"][0]["text"]},
        }]}
        return source, analysis

    @staticmethod
    def _source_unit_response(request: dict) -> dict:
        return {"sentences": [{
            "sentence_index": sentence["sentence_index"], "scope": "central",
            "units": [{"content": sentence["text"], "kind": "reported_fact",
                       "evidence": {"sentence_index": sentence["sentence_index"],
                                    "quote": sentence["text"]}}],
        } for sentence in request["input_data"]["article"]["sentences"]]}

    def test_source_truncation_splits_and_preserves_every_grounded_sentence(self):
        source, analysis = self._source_unit_case(4)
        analysis["claims"][0]["evidence"] = {"sentence_index": 3,
                                            "quote": source["sentences"][3]["text"]}

        class TruncatingClient:
            def __init__(self):
                self.requests = []

            def generate_json(self, **request):
                self.requests.append(request)
                indexes = [row["sentence_index"] for row in request["input_data"]["article"]["sentences"]]
                if len(indexes) == 4:
                    raise ModelResponseTruncatedError("synthetic output limit")
                return ReaderAnalysisTests._source_unit_response(request)

        client = TruncatingClient()
        ledger, units, calls = _extract_source_units(source, analysis, client)
        self.assertEqual(calls, 3)
        self.assertEqual([[row["sentence_index"] for row in request["input_data"]["article"]["sentences"]]
                          for request in client.requests], [[0, 1, 2, 3], [0, 1], [2, 3]])
        self.assertEqual([request["schema"]["properties"]["sentences"]["items"]
                          ["properties"]["sentence_index"]["enum"] for request in client.requests],
                         [[0, 1, 2, 3], [0, 1], [2, 3]])
        self.assertEqual([(request["schema"]["properties"]["sentences"]["minItems"],
                           request["schema"]["properties"]["sentences"]["maxItems"])
                          for request in client.requests], [(4, 4), (2, 2), (2, 2)])
        self.assertEqual([row["sentence_index"] for row in ledger], [0, 1, 2, 3])
        self.assertEqual([row["source_evidence"]["sentence_index"] for row in units], [0, 1, 2, 3])
        self.assertTrue(all(row["unit_ids"] for row in ledger))

    def test_single_source_sentence_truncation_has_one_larger_bounded_retry(self):
        source, analysis = self._source_unit_case(1)

        class SingletonClient:
            def __init__(self, *, always_truncated=False):
                self.always_truncated = always_truncated
                self.requests = []

            def generate_json(self, **request):
                self.requests.append(request)
                if self.always_truncated or request["max_output_tokens"] == 8192:
                    raise ModelResponseTruncatedError("synthetic output limit")
                return ReaderAnalysisTests._source_unit_response(request)

        client = SingletonClient()
        ledger, units, calls = _extract_source_units(source, analysis, client)
        self.assertEqual((len(ledger), len(units), calls), (1, 1, 2))
        self.assertEqual([request["max_output_tokens"] for request in client.requests], [8192, 16384])
        failed = SingletonClient(always_truncated=True)
        with self.assertRaises(ModelResponseTruncatedError):
            _extract_source_units(source, analysis, failed)
        self.assertEqual([request["max_output_tokens"] for request in failed.requests], [8192, 16384])

    def test_source_repair_truncation_splits_and_counts_both_failed_calls(self):
        source, analysis = self._source_unit_case(2)

        class RepairTruncatingClient:
            def __init__(self):
                self.requests = []

            def generate_json(self, **request):
                self.requests.append(request)
                sentences = request["input_data"]["article"]["sentences"]
                if len(sentences) == 2 and request["name"] == "source_content_units":
                    return {"sentences": [{"sentence_index": row["sentence_index"],
                                            "scope": "central", "units": []} for row in sentences]}
                if len(sentences) == 2:
                    raise ModelResponseTruncatedError("synthetic repair output limit")
                return ReaderAnalysisTests._source_unit_response(request)

        client = RepairTruncatingClient()
        ledger, units, calls = _extract_source_units(source, analysis, client)
        self.assertEqual((len(ledger), len(units), calls), (2, 2, 4))
        self.assertEqual([request["name"] for request in client.requests],
                         ["source_content_units", "repair_source_content_units",
                          "source_content_units", "source_content_units"])
        self.assertEqual([len(request["input_data"]["article"]["sentences"])
                          for request in client.requests], [2, 2, 1, 1])

    def test_non_truncation_source_errors_never_split_the_chunk(self):
        source, analysis = self._source_unit_case(4)
        for error, expected_calls in ((ModelHTTPError(429), 1), (TimeoutError("synthetic"), 1),
                                      (ModelResponseError("synthetic invalid output"), 2)):
            with self.subTest(error=type(error).__name__):
                requests = []

                class FailingClient:
                    def generate_json(self, **request):
                        requests.append(request)
                        raise error

                with self.assertRaises(type(error)):
                    _extract_source_units(source, analysis, FailingClient())
                self.assertEqual(len(requests), expected_calls)
                self.assertTrue(all(len(request["input_data"]["article"]["sentences"]) == 4
                                    for request in requests))

    def test_ten_source_sentences_use_two_balanced_chunks(self):
        source, analysis = self._source_unit_case(10)

        class RecordingClient:
            def __init__(self):
                self.requests = []

            def generate_json(self, **request):
                self.requests.append(request)
                return ReaderAnalysisTests._source_unit_response(request)

        client = RecordingClient()
        ledger, units, calls = _extract_source_units(source, analysis, client)
        self.assertEqual([len(request["input_data"]["article"]["sentences"])
                          for request in client.requests], [5, 5])
        self.assertEqual([row["sentence_index"] for row in ledger], list(range(10)))
        self.assertEqual([row["source_evidence"]["sentence_index"] for row in units], list(range(10)))
        self.assertEqual(calls, 2)

    def test_thirteen_to_sixteen_source_sentences_keep_original_two_chunks(self):
        class RecordingClient:
            def __init__(self):
                self.requests = []

            def generate_json(self, **request):
                self.requests.append(request)
                return ReaderAnalysisTests._source_unit_response(request)

        for count in (13, 16):
            with self.subTest(count=count):
                source, analysis = self._source_unit_case(count)
                client = RecordingClient()
                ledger, units, calls = _extract_source_units(source, analysis, client)
                self.assertEqual([len(request["input_data"]["article"]["sentences"])
                                  for request in client.requests], [8, count - 8])
                self.assertEqual((len(ledger), len(units), calls), (count, count, 2))

    def test_live_source_chunks_overlap_and_merge_in_sentence_order(self):
        class ConcurrentSourceClient(OpenRouterJSONClient):
            def __init__(self):
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self._usage_lock = Lock()
                self._usage_totals = Counter()
                self._metered_responses = self._cost_reported_responses = 0
                self._barrier = Barrier(2)
                self._lock = Lock()
                self.active = self.max_active = 0
                self.completed = []

            def generate_json(self, **request):
                first = request["input_data"]["article"]["sentences"][0]["sentence_index"]
                with self._lock:
                    self.active += 1
                    self.max_active = max(self.max_active, self.active)
                try:
                    if first < 16:
                        self._barrier.wait(timeout=5)
                    if first == 0:
                        sleep(0.05)
                    with self._lock:
                        self.completed.append(first)
                    return ReaderAnalysisTests._source_unit_response(request)
                finally:
                    with self._lock:
                        self.active -= 1

        source, analysis = self._source_unit_case(17)
        provider = ConcurrentSourceClient()
        recorder = MetricsJSONClient(provider)
        ledger, units, calls = _extract_source_units(source, analysis, recorder)
        self.assertEqual(provider.max_active, 2)
        self.assertEqual(provider.completed[:2], [8, 0])
        self.assertEqual([row["sentence_index"] for row in ledger], list(range(17)))
        self.assertEqual([row["source_evidence"]["sentence_index"] for row in units], list(range(17)))
        self.assertEqual((calls, recorder.execution_summary()["model_calls"]), (3, 3))

    def test_failed_source_wave_never_starts_later_chunks(self):
        class FailingSourceClient(OpenRouterJSONClient):
            def __init__(self):
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self._barrier = Barrier(2)
                self._lock = Lock()
                self.started = []

            def generate_json(self, **request):
                first = request["input_data"]["article"]["sentences"][0]["sentence_index"]
                with self._lock:
                    self.started.append(first)
                if first < 16:
                    self._barrier.wait(timeout=5)
                if first == 0:
                    raise ModelHTTPError(429)
                return ReaderAnalysisTests._source_unit_response(request)

        source, analysis = self._source_unit_case(17)
        provider = FailingSourceClient()
        with self.assertRaises(ModelHTTPError):
            _extract_source_units(source, analysis, MetricsJSONClient(provider))
        self.assertCountEqual(provider.started, [0, 8])

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

    def test_three_displayed_articles_align_concurrently_without_changing_result_order(self):
        class ThreeWayReaderClient(ReaderClient, OpenRouterJSONClient):
            def __init__(self):
                ReaderClient.__init__(self)
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self._barrier = Barrier(3)
                self._lock = Lock()
                self.active = self.max_active = 0
                self.completed = []

            def generate_json(self, **request):
                self.last_call_usage = {}
                self.last_call_finish_reason = None
                if request["name"] == "related_content_alignment":
                    key = request["input_data"]["article_id"]
                    with self._lock:
                        self.active += 1
                        self.max_active = max(self.max_active, self.active)
                    try:
                        self._barrier.wait(timeout=5)
                        if key == "A":
                            sleep(0.05)
                        with self._lock:
                            self.completed.append(key)
                    finally:
                        with self._lock:
                            self.active -= 1
                return ReaderClient.generate_json(self, **request)

        raw = fixture()
        raw["search"]["candidates"].append(article(
            "C", "시청은 새 다리 공사를 시작했다.\n건설업체는 장비를 배치했다."))
        raw["selection"]["same_event_ids"].append("C")
        raw["selection"]["independent_same_event_ids"].append("C")
        serial = analyze_reader_content(raw, ["A", "B", "C"], client=ReaderClient())
        provider = ThreeWayReaderClient()
        parallel = analyze_reader_content(raw, ["A", "B", "C"], client=MetricsJSONClient(provider))
        self.assertEqual(provider.max_active, 3)
        self.assertEqual(provider.completed[-1], "A")
        self.assertEqual(list(parallel["article_alignments"]), ["A", "B", "C"])
        self.assertEqual(parallel, serial)

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
                                        "related_content_alignment", "i1_absence_review",
                                        "expression_pair_A_G"])
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

    def test_neutral_reporting_verbs_are_not_a_differences_in_either_pass(self):
        source = article("S0", "관계자는 조치를 말했습니다.")
        related = article("A", "관계자는 조치를 설명했습니다.")
        unit = {"unit_id": "u1", "content": "관계자가 조치를 언급했다",
                "kind": "attributed_statement",
                "source_evidence": {"sentence_index": 0, "quote": source["sentences"][0]["text"]}}

        class NeutralVerbClient:
            def generate_json(self, **request):
                if request["name"] == "related_content_alignment":
                    return {"unit_alignments": [{
                        "unit_id": "u1", "relation": "same",
                        "related_evidence": {"sentence_index": 0, "quote": related["sentences"][0]["text"]},
                        "difference_summary": "보도 동사의 어휘가 다르다",
                        "expression_differences": [{
                            "code": "A", "source_marker": {"sentence_index": 0, "quote": "말했습니다"},
                            "related_marker": {"sentence_index": 0, "quote": "설명했습니다"},
                            "note": "보도 동사가 다르다", "voice": "reporter",
                        }],
                    }]}
                pair = request["input_data"]["pairs"][0]
                return {"findings": [{
                    "pair_id": pair["pair_id"], "unit_id": "u1", "code": "A",
                    "source_marker": {"sentence_index": 0, "quote": "말했습니다"},
                    "related_marker": {"sentence_index": 0, "quote": "설명했습니다"},
                    "note": "보도 동사가 다르다", "voice": "reporter",
                    "quoted_use": "none", "name_kind": "other",
                    "semantic_relation": "equivalent",
                }]}

        rows, _ = _alignment_chunk(source, related, [unit], "A", NeutralVerbClient())
        self.assertEqual(rows[0]["expression_differences"], [])
        diagnostic, _ = _review_expression_pairs(
            source, {"A": related}, [unit], {"A": {"unit_alignments": rows}}, NeutralVerbClient())
        self.assertEqual(diagnostic["accepted_finding_count"], 0)
        self.assertEqual(diagnostic["withheld_finding_count"], 1)
        self.assertFalse(_neutral_a_pair("A", "강조했습니다", "말했습니다"))
        self.assertFalse(_neutral_a_pair("C", "말했습니다", "설명했습니다"))

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

    def test_expression_chunks_review_all_pairs_with_bounded_outputs(self):
        source, catalog, units, alignments = expression_fixture()
        client = ChunkReviewClient()
        diagnostic, calls = _review_expression_pairs(source, catalog, units, alignments, client)
        self.assertEqual([len(request["input_data"]["pairs"]) for request in client.requests], [3, 3, 3])
        self.assertTrue(all(request["max_output_tokens"] == 8192 for request in client.requests))
        self.assertEqual(diagnostic["status"], "complete")
        self.assertEqual(diagnostic["eligible_pair_count"], 9)
        self.assertEqual(diagnostic["reviewed_pair_count"], 9)
        self.assertEqual(diagnostic["accepted_finding_count"], 9)
        self.assertEqual(diagnostic["successful_chunk_count"], 3)
        self.assertEqual(diagnostic["attempted_model_call_count"], calls)
        self.assertEqual(calls, 3)
        self.assertTrue(all(len(row["expression_differences"]) == 1
                            for row in alignments["A"]["unit_alignments"]))

    def test_final_two_pair_initial_batch_keeps_output_budget(self):
        source, catalog, units, alignments = expression_fixture(count=8)
        client = ChunkReviewClient()
        diagnostic, calls = _review_expression_pairs(source, catalog, units, alignments, client)
        self.assertEqual([len(request["input_data"]["pairs"]) for request in client.requests], [3, 3, 2])
        self.assertEqual([request["max_output_tokens"] for request in client.requests],
                         [8192, 8192, 8192])
        self.assertEqual((diagnostic["status"], calls), ("complete", 3))

    def test_failed_expression_chunk_keeps_other_grounded_findings(self):
        source, catalog, units, alignments = expression_fixture()
        client = ChunkReviewClient(fail_at=3)
        diagnostic, calls = _review_expression_pairs(source, catalog, units, alignments, client)
        self.assertEqual(diagnostic["status"], "partial")
        self.assertEqual(diagnostic["reviewed_pair_count"], 8)
        self.assertEqual(diagnostic["failed_pair_count"], 1)
        self.assertEqual(diagnostic["successful_chunk_count"], 2)
        self.assertEqual(diagnostic["failed_chunk_count"], 1)
        self.assertEqual(diagnostic["attempted_model_call_count"], calls)
        self.assertEqual(calls, 5)
        self.assertEqual([len(request["input_data"]["pairs"]) for request in client.requests],
                         [3, 3, 1, 2, 3])
        self.assertEqual([request["max_output_tokens"] for request in client.requests],
                         [8192, 8192, 8192, 8192, 8192])
        self.assertEqual([bool(row["expression_differences"])
                          for row in alignments["A"]["unit_alignments"]],
                         [True] * 3 + [False] + [True] * 5)

    def test_truncated_expression_chunk_recovers_all_pairs_in_smaller_calls(self):
        class TruncatingBatchClient(ChunkReviewClient):
            def generate_json(self, **request):
                pairs = request["input_data"]["pairs"]
                if len(pairs) == 3 and pairs[0]["source_sentence_index"] == 3:
                    self.requests.append(request)
                    raise ModelResponseTruncatedError("synthetic output limit")
                return super().generate_json(**request)

        source, catalog, units, alignments = expression_fixture()
        client = TruncatingBatchClient()
        diagnostic, calls = _review_expression_pairs(source, catalog, units, alignments, client)
        self.assertEqual(diagnostic["status"], "complete")
        self.assertEqual(diagnostic["reviewed_pair_count"], 9)
        self.assertEqual(diagnostic["failed_pair_count"], 0)
        self.assertEqual(diagnostic["accepted_finding_count"], 9)
        self.assertEqual(diagnostic["attempted_model_call_count"], calls)
        self.assertEqual(calls, 5)
        self.assertEqual([len(request["input_data"]["pairs"]) for request in client.requests],
                         [3, 3, 1, 2, 3])
        self.assertEqual([request["max_output_tokens"] for request in client.requests],
                         [8192, 8192, 8192, 8192, 8192])
        self.assertTrue(all(len(row["expression_differences"]) == 1
                            for row in alignments["A"]["unit_alignments"]))

    def test_expression_truncation_near_deadline_skips_recovery(self):
        class TruncatingOpenRouter(OpenRouterJSONClient):
            def __init__(self):
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self.calls = 0

            def generate_json(self, **request):
                self.calls += 1
                raise ModelResponseTruncatedError("synthetic output limit")

        source, catalog, units, alignments = expression_fixture(count=3)
        provider = TruncatingOpenRouter()
        diagnostic, calls = _review_expression_pairs(
            source, catalog, units, alignments,
            MetricsJSONClient(provider, deadline_at=monotonic() + 5))
        self.assertEqual((provider.calls, calls), (1, 1))
        self.assertEqual(diagnostic["status"], "failed")
        self.assertEqual(diagnostic["failed_pair_count"], 3)
        self.assertEqual(diagnostic["attempted_model_call_count"], 1)

    def test_rate_limit_during_truncation_recovery_stops_later_calls(self):
        class RateLimitedRecoveryClient(ChunkReviewClient):
            def generate_json(self, **request):
                pairs = request["input_data"]["pairs"]
                first = pairs[0]["source_sentence_index"]
                if first == 0 and len(pairs) == 3:
                    self.requests.append(request)
                    raise ModelResponseTruncatedError("synthetic output limit")
                if first == 0 and len(pairs) == 1:
                    self.requests.append(request)
                    raise ModelHTTPError(429)
                return super().generate_json(**request)

        source, catalog, units, alignments = expression_fixture()
        client = RateLimitedRecoveryClient()
        diagnostic, calls = _review_expression_pairs(source, catalog, units, alignments, client)
        self.assertEqual([request["input_data"]["pairs"][0]["source_sentence_index"]
                          for request in client.requests], [0, 0])
        self.assertEqual((calls, diagnostic["attempted_model_call_count"]), (2, 2))
        self.assertEqual(diagnostic["failed_pair_count"], 3)
        self.assertEqual(diagnostic["deferred_pair_count"], 6)
        self.assertEqual(diagnostic["status"], "failed")

    def test_parallel_expression_chunks_merge_deterministically_and_meter_cost(self):
        class ConcurrentChunkClient(ChunkReviewClient, OpenRouterJSONClient):
            def __init__(self):
                ChunkReviewClient.__init__(self)
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self._usage_lock = Lock()
                self._usage_totals = Counter()
                self._metered_responses = self._cost_reported_responses = 0
                self._barrier = Barrier(3)
                self._active_lock = Lock()
                self._active = self.max_active = 0
                self.completed = []

            def generate_json(self, **request):
                first = request["input_data"]["pairs"][0]["source_sentence_index"]
                with self._active_lock:
                    self._active += 1
                    self.max_active = max(self.max_active, self._active)
                try:
                    if first in {0, 3, 6}:
                        self._barrier.wait(timeout=5)
                    if first == 0:
                        sleep(0.05)
                    self._capture_usage({"usage": {"prompt_tokens": 10,
                                                    "completion_tokens": 2,
                                                    "total_tokens": 12,
                                                    "cost": 0.01}})
                    self.last_call_finish_reason = "stop"
                    with self._active_lock:
                        self.completed.append(first)
                    return ChunkReviewClient.generate_json(self, **request)
                finally:
                    with self._active_lock:
                        self._active -= 1

        source, catalog, units, serial_alignments = expression_fixture()
        serial, _ = _review_expression_pairs(source, catalog, units, serial_alignments, ChunkReviewClient())
        source, catalog, units, parallel_alignments = expression_fixture()
        provider = ConcurrentChunkClient()
        recorder = MetricsJSONClient(provider)
        parallel, calls = _review_expression_pairs(source, catalog, units, parallel_alignments, recorder)
        self.assertEqual(provider.max_active, 3)
        self.assertCountEqual(provider.completed[:2], [3, 6])
        self.assertEqual(provider.completed[2], 0)
        self.assertEqual(parallel, serial)
        self.assertEqual(parallel_alignments, serial_alignments)
        self.assertEqual(calls, 3)
        summary = recorder.execution_summary()
        self.assertEqual(summary["attempted_calls"], 3)
        self.assertEqual(summary["model_calls"], 3)
        self.assertEqual(summary["provider_usage"]["metered_responses"], 3)
        self.assertAlmostEqual(summary["provider_usage"]["reported_cost_usd"], 0.03)

    def test_parallel_expression_review_stops_followup_chunks_after_429(self):
        class RateLimitedOpenRouter(OpenRouterJSONClient):
            def __init__(self):
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self._barrier = Barrier(3)
                self.first_indexes = []
                self._lock = Lock()

            def generate_json(self, **request):
                first = request["input_data"]["pairs"][0]["source_sentence_index"]
                with self._lock:
                    self.first_indexes.append(first)
                self._barrier.wait(timeout=5)
                if first == 0:
                    raise ModelHTTPError(429)
                return expression_findings(request["input_data"]["pairs"])

        source, catalog, units, alignments = expression_fixture(count=12)
        provider = RateLimitedOpenRouter()
        diagnostic, calls = _review_expression_pairs(
            source, catalog, units, alignments, MetricsJSONClient(provider))
        self.assertCountEqual(provider.first_indexes, [0, 3, 6])
        self.assertEqual(diagnostic["status"], "partial")
        self.assertEqual(diagnostic["attempted_pair_count"], 9)
        self.assertEqual(diagnostic["reviewed_pair_count"], 6)
        self.assertEqual(diagnostic["failed_pair_count"], 3)
        self.assertEqual(diagnostic["deferred_pair_count"], 3)
        self.assertEqual(diagnostic["attempted_model_call_count"], calls)
        self.assertEqual(calls, 3)

    def test_parallel_429_stops_split_retries_but_keeps_completed_pair(self):
        rate_limit_seen = Event()

        class ConcurrentRateLimitClient(OpenRouterJSONClient):
            def __init__(self):
                self.model = "synthetic-model"
                self.task_models = {}
                self._thread_state = local()
                self.initial_barrier = Barrier(3)
                self.child_started = Event()
                self._lock = Lock()
                self.calls = []

            def generate_json(self, **request):
                pairs = request["input_data"]["pairs"]
                first, size = pairs[0]["source_sentence_index"], len(pairs)
                with self._lock:
                    self.calls.append((first, size))
                if size == 3:
                    self.initial_barrier.wait(timeout=5)
                if first == 0:
                    self.assert_child_started()
                    raise ModelHTTPError(429)
                if first == 3 and size == 3:
                    raise ModelResponseTruncatedError("synthetic output limit")
                if first == 3 and size == 1:
                    self.child_started.set()
                    if not rate_limit_seen.wait(timeout=5):
                        raise AssertionError("429 event was not propagated")
                return expression_findings(pairs)

            def assert_child_started(self):
                if not self.child_started.wait(timeout=5):
                    raise AssertionError("split retry never started")

        source, catalog, units, alignments = expression_fixture(count=12)
        provider = ConcurrentRateLimitClient()
        with patch("server.analysis.reader_analysis.Event", return_value=rate_limit_seen):
            diagnostic, calls = _review_expression_pairs(
                source, catalog, units, alignments, MetricsJSONClient(provider))
        self.assertCountEqual(provider.calls, [(0, 3), (3, 3), (6, 3), (3, 1)])
        self.assertEqual((calls, diagnostic["attempted_model_call_count"]), (4, 4))
        self.assertEqual(diagnostic["attempted_pair_count"], 9)
        self.assertEqual(diagnostic["reviewed_pair_count"], 4)
        self.assertEqual(diagnostic["failed_pair_count"], 5)
        self.assertEqual(diagnostic["deferred_pair_count"], 3)
        self.assertEqual(diagnostic["accepted_finding_count"], 4)
        self.assertEqual(diagnostic["status"], "partial")
        self.assertEqual([bool(row["expression_differences"])
                          for row in alignments["A"]["unit_alignments"]],
                         [False] * 3 + [True] + [False] * 2 + [True] * 3 + [False] * 3)

    def test_expression_review_without_candidates_makes_no_model_call(self):
        source, _, _, _ = expression_fixture()
        client = ChunkReviewClient()
        diagnostic, calls = _review_expression_pairs(source, {}, [], {}, client)
        self.assertEqual(diagnostic["status"], "no_candidates")
        self.assertEqual(diagnostic["chunk_count"], 0)
        self.assertEqual(diagnostic["attempted_model_call_count"], 0)
        self.assertEqual(client.requests, [])
        self.assertEqual(calls, 0)

    def test_shared_headline_number_does_not_preempt_full_title_review(self):
        raw = fixture()
        raw["search"]["candidates"][0]["title"] = "시청, 다른 공사에 과태료 100억원"
        client = ReaderClient()
        result = analyze_reader_content(raw, ["A", "B"], client=client)
        self.assertEqual(len(result["i1_stars"]), 1)
        self.assertEqual(result["i1_status"], "complete")
        self.assertIn("i1_absence_review", client.calls)

    def test_grounded_title_observation_resolves_candidate_without_star(self):
        raw = fixture()
        raw["search"]["candidates"][0]["title"] = "새 다리 예산은 100억원이다"

        class TitleObservedClient(ReaderClient):
            def generate_json(self, **request):
                if request["name"] == "i1_absence_review":
                    unit = request["input_data"]["source_units"][0]
                    return {"checks": [{"unit_id": unit["unit_id"], "article_checks": {
                        "A": {"status": "observed", "evidence": {
                            "scope": "title", "sentence_index": None, "quote": "예산은 100억원이다"}},
                        "B": {"status": "absent", "evidence": None},
                    }}]}
                return super().generate_json(**request)

        result = analyze_reader_content(raw, ["A", "B"], client=TitleObservedClient())
        self.assertEqual(result["i1_stars"], [])
        self.assertEqual(result["i1_status"], "complete")

    def test_related_only_is_reviewed_for_atomic_content_absence(self):
        class RelatedOnlyClient(ReaderClient):
            def generate_json(self, **request):
                response = super().generate_json(**request)
                if request["name"] == "related_content_alignment" and request["input_data"]["article_id"] == "A":
                    row = response["unit_alignments"][1]
                    row["relation"] = "related_only"
                    row["related_evidence"] = {"sentence_index": 1, "quote": "주민은 기대한다고 말했다"}
                return response

        client = RelatedOnlyClient()
        result = analyze_reader_content(fixture(), ["A", "B"], client=client)
        self.assertEqual(len(result["i1_stars"]), 1)
        self.assertEqual(result["i1_status"], "complete")
        self.assertIn("i1_absence_review", client.calls)

    def test_first_pass_uncertain_cannot_become_star_or_complete(self):
        class UncertainClient(ReaderClient):
            def generate_json(self, **request):
                response = super().generate_json(**request)
                if request["name"] == "related_content_alignment" and request["input_data"]["article_id"] == "A":
                    response["unit_alignments"][1]["relation"] = "uncertain"
                return response

        result = analyze_reader_content(fixture(), ["A", "B"], client=UncertainClient())
        self.assertEqual(result["i1_stars"], [])
        self.assertEqual(result["i1_status"], "partial")

    def test_absence_review_chunks_and_salvages_valid_single_units(self):
        raw = fixture()
        source = raw["source_article"]
        related = {row["candidate_id"]: row for row in raw["search"]["candidates"]}
        units = [{"unit_id": f"u{i}", "content": f"새 다리 예산 항목 {i}",
                  "source_evidence": {"sentence_index": 1, "quote": "예산은 100억원이다"}}
                 for i in range(3)]

        class ChunkClient:
            def __init__(self):
                self.batches = []

            def generate_json(self, **request):
                ids = [row["unit_id"] for row in request["input_data"]["source_units"]]
                self.batches.append(ids)
                if ids == ["u0", "u1"]:
                    raise ModelResponseTruncatedError("synthetic output limit")
                if ids == ["u1"]:
                    raise TimeoutError("synthetic missing unit")
                return {"checks": [{"unit_id": key, "article_checks": {
                    article_id: {"status": "absent", "evidence": None} for article_id in related}}
                    for key in ids]}

        client = ChunkClient()
        checks, calls = _review_absence(source, related, units, [["A"], ["B"]], client)
        self.assertEqual(set(checks), {"u0", "u2"})
        self.assertEqual(calls, 4)
        self.assertEqual(client.batches, [["u0", "u1"], ["u0"], ["u1"], ["u2"]])

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
