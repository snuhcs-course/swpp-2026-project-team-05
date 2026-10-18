"""Synthetic regression checks for bounded candidate verification failures."""

import json
from unittest.mock import patch

from django.test import SimpleTestCase

from server.analysis.v2.comparison_selection import select_verified_comparisons
from server.analysis.v2.pipeline import run_discovery_pipeline
from server.analysis.v2.result_output import OperationError
from server.analysis.v2_adapter import adapt_v2_result


def _article(candidate_id=None):
    body = "시청은 새 계획을 발표했다."
    return {"url": f"https://news.example.com/{candidate_id or 'source'}",
            "title": f"{candidate_id or '원문'} 기사", "publisher": "가상일보", "published_at": "",
            "body": body, "sentences": [{"sentence_index": 0, "paragraph_index": 0,
                                        "start": 0, "end": len(body), "text": body}],
            **({"candidate_id": candidate_id} if candidate_id else {})}


def _analysis():
    return {"core_event": "시청의 새 계획 발표", "event_query": "시청 새 계획 발표",
            "claims": [{"claim_id": "C1", "claim": "시청이 계획을 발표했다",
                        "claim_kind": "reported_fact", "speaker": "",
                        "evidence": {"sentence_index": 0, "quote": "시청은 새 계획을 발표했다"},
                        "search_query": "시청 새 계획"}]}


def _search(ids, *, discovered_by_event=False):
    return {"queries": [{"query": "시청 새 계획 발표", "status": "searched"}],
            "metadata_candidates": [
                {"candidate_id": key, "title": f"{key} 기사", "description": f"{key} 계획 발표",
                 "urls": [f"https://news.example.com/{key}"], "publisher": f"가상일보 {key}",
                 "discovered_by_event": discovered_by_event} for key in ids],
            "discovered_count": len(ids), "errors": [], "search_status": "success"}


def _ranking(selected, reserve=()):
    ids = list(selected) + list(reserve)
    return {"claim_candidates": [{"claim_id": "C1", "selected_candidate_ids": list(selected),
                                   "reserve_candidate_ids": list(reserve),
                                   "candidate_profiles": [{"candidate_id": key, "relevance": "direct",
                                                           "focus": key} for key in ids]}]}


def _fetch(rows):
    return {"candidates": [_article(row["candidate_id"]) for row in rows], "fetch_failures": []}


def _verified(key):
    evidence = {"sentence_index": 0, "quote": "새 계획을 발표했다"}
    return {"candidate_id": key, "event_relation": "same_event", "event_evidence": evidence,
            "claim_matches": [{"claim_id": "C1", "relation": "same_issue", "evidence": evidence}]}


class VerificationResilienceTests(SimpleTestCase):
    def test_rate_limit_stops_reserve_verification_and_keeps_grounded_article(self):
        calls = []

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            calls.append(key)
            if key == "A2":
                raise OperationError("한도 초과", code="MODEL_RATE_LIMITED", retryable=True,
                                     http_status=429)
            return {"assessments": [_verified(key)]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(_article(), _analysis(), _search(("A1", "A2", "A3")),
                                                 _ranking(("A1", "A2"), ("A3",)), max_per_claim=2,
                                                 event_baseline_limit=0, verification_batch_size=1,
                                                 client=object())

        self.assertEqual(calls, ["A1", "A2"])
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A1"])
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A3"])
        self.assertEqual([row["code"] for row in result["search"]["errors"]], ["MODEL_RATE_LIMITED"])

    def test_full_initial_slot_still_checks_later_better_article(self):
        calls = []

        def verify(_source, _analysis, rows, *, client):
            calls.extend(row["candidate_id"] for row in rows)
            result = []
            for row in rows:
                assessment = _verified(row["candidate_id"])
                if row["candidate_id"] == "A3":
                    assessment["claim_matches"][0]["relation"] = "same"
                result.append(assessment)
            return {"assessments": result}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(_article(), _analysis(), _search(("A1", "A2", "A3")),
                                                 _ranking(("A1",), ("A2", "A3")), max_per_claim=1,
                                                 event_baseline_limit=0, verification_batch_size=1,
                                                 client=object())

        self.assertEqual(calls, ["A1", "A2", "A3"])
        self.assertEqual(result["selection"]["claim_comparisons"][0]["articles"][0]["candidate_id"], "A3")
        self.assertEqual(result["selection"]["claim_comparisons"][0]["articles"][0]["relation"], "same")
        self.assertEqual(result["selection"]["verification_llm_calls"], 3)

    def test_syndicated_full_bodies_count_once_for_i1_baseline(self):
        original = "시청은 새 계획을 발표했다. 자세한 실행 일정과 사업 배경을 설명했다. " * 16
        distinct = "시의회는 해당 계획의 예산과 적용 범위를 검토했다. 여러 주민 의견도 취재했다. " * 16

        def fetch(rows):
            result = _fetch(rows)
            for article in result["candidates"]:
                article["body"] = distinct if article["candidate_id"] == "A3" else original
            return result

        def verify(_source, _analysis, rows, *, client):
            return {"assessments": [_verified(row["candidate_id"]) for row in rows]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), _search(("A1", "A2", "A3"), discovered_by_event=True),
                _ranking(("A1", "A2"), ("A3",)), max_per_claim=2,
                event_baseline_limit=3, verification_batch_size=1, client=object())

        selection = result["selection"]
        self.assertEqual(selection["duplicate_article_groups"], [["A1", "A2"]])
        self.assertEqual(selection["independent_same_event_ids"], ["A1", "A3"])
        self.assertEqual(selection["event_baseline_ids"], ["A1", "A3"])
        self.assertEqual([a["candidate_id"] for a in selection["claim_comparisons"][0]["articles"]], ["A1", "A3"])
        self.assertEqual(len(selection["assessments"]), 3)

    def test_complementary_claim_evidence_is_not_transferred_between_copies(self):
        body = "시청은 새 계획을 발표했다. 사업 계획과 지역 반응을 차례로 정리했다. " * 16
        analysis = _analysis()
        analysis["claims"].append({**analysis["claims"][0], "claim_id": "C2", "claim": "지역 반응이 있었다"})
        ranking = {"claim_candidates": [
            {"claim_id": "C1", "selected_candidate_ids": ["A1"], "reserve_candidate_ids": [],
             "candidate_profiles": [{"candidate_id": "A1", "relevance": "direct", "focus": "계획"}]},
            {"claim_id": "C2", "selected_candidate_ids": ["A2"], "reserve_candidate_ids": [],
             "candidate_profiles": [{"candidate_id": "A2", "relevance": "direct", "focus": "반응"}]},
        ]}

        def fetch(rows):
            result = _fetch(rows)
            for article in result["candidates"]:
                article["body"] = body
            return result

        def verify(_source, _analysis, rows, *, client):
            result = []
            for row in rows:
                assessment = _verified(row["candidate_id"])
                claim_id = "C1" if row["candidate_id"] == "A1" else "C2"
                assessment["claim_matches"][0]["claim_id"] = claim_id
                result.append(assessment)
            return {"assessments": result}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), analysis, _search(("A1", "A2"), discovered_by_event=True), ranking,
                max_per_claim=1, event_baseline_limit=2, verification_batch_size=1, client=object())

        selection = result["selection"]
        self.assertEqual(selection["event_baseline_ids"], ["A1"])
        self.assertEqual(selection["same_event_ids"], ["A1", "A2"])
        self.assertEqual([row["articles"][0]["candidate_id"] for row in selection["claim_comparisons"]],
                         ["A1", "A2"])
        self.assertEqual([row["claim_matches"][0]["claim_id"] for row in selection["assessments"]],
                         ["C1", "C2"])

    def test_source_syndication_is_excluded_and_warning_reaches_adapter(self):
        source = _article()
        source["body"] = "시청은 새 계획을 발표했다. 세부 사업과 예산을 설명했다. " * 18
        search = _search(("A1", "A2"), discovered_by_event=True)
        ranking = _ranking(("A1",), ("A2",))

        def fetch(rows):
            result = _fetch(rows)
            for article in result["candidates"]:
                article["body"] = (source["body"] if article["candidate_id"] == "A1" else
                                   "시의회는 예산 심의를 열었다. 주민과 전문가의 반응을 폭넓게 취재했다. " * 18)
            return result

        def verify(_source, _analysis, rows, *, client):
            return {"assessments": [_verified(row["candidate_id"]) for row in rows]}

        with (patch("server.analysis.v2.pipeline.prepare_article", return_value=source),
              patch("server.analysis.v2.pipeline.analyze_source_article", return_value=_analysis()),
              patch("server.analysis.v2.pipeline.search_candidate_metadata", return_value=search),
              patch("server.analysis.v2.pipeline.rank_comparison_candidates", return_value=ranking),
              patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = run_discovery_pipeline("https://news.example.com/source", max_per_claim=1,
                                            event_baseline_limit=2, verification_batch_size=1,
                                            client=object())

        selection = result["selection"]
        self.assertEqual(selection["source_duplicate_article_ids"], ["A1"])
        self.assertEqual(selection["same_event_ids"], ["A2"])
        self.assertEqual(selection["event_baseline_ids"], ["A2"])
        self.assertEqual(selection["claim_comparisons"][0]["articles"][0]["candidate_id"], "A2")
        self.assertEqual(len(selection["assessments"]), 2)
        self.assertTrue(any("입력 기사와 본문이 거의 같은" in warning for warning in result["warnings"]))
        adapted = adapt_v2_result(result, max_related=1, requested_url="https://news.example.com/source")
        self.assertTrue(any("입력 기사와 본문이 거의 같은" in warning
                            for warning in adapted["analysis_status"]["warnings"]))

    def test_deadline_before_next_round_fetch_preserves_prior_verified_result(self):
        expired = False
        fetched = []

        def fetch(rows):
            fetched.extend(row["candidate_id"] for row in rows)
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            nonlocal expired
            key = rows[0]["candidate_id"]
            if key == "A2":
                expired = True
                return {"assessments": [{"candidate_id": key, "event_relation": "different_event",
                                         "event_evidence": None, "claim_matches": []}]}
            return {"assessments": [_verified(key)]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(_article(), _analysis(), _search(("A1", "A2", "A3")),
                                                 _ranking(("A1", "A2"), ("A3",)), max_per_claim=2,
                                                 event_baseline_limit=0, verification_batch_size=1,
                                                 should_stop=lambda: expired, client=object())

        self.assertEqual(fetched, ["A1", "A2"])
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A1"])
        self.assertEqual([row["reason"] for row in result["selection"]["claim_attempts"]],
                         ["verified", "different_event", "verification_deferred"])
        self.assertEqual(result["selection"]["verification_rounds"][1]["deferred_ids"], ["A3"])
        self.assertEqual([row["candidate_id"] for row in result["selection"]["assessments"]], ["A1", "A2"])
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A3"])
        self.assertEqual(result["search"]["errors"][0]["candidate_id"], "A3")

    def test_deadline_between_batches_keeps_first_verified_comparison_at_stage_four(self):
        expired = False
        calls = []

        def verify(_source, _analysis, rows, *, client):
            nonlocal expired
            key = rows[0]["candidate_id"]
            calls.append(key)
            expired = True
            return {"assessments": [_verified(key)]}

        search = _search(("A1", "A2", "A3"), discovered_by_event=True)
        ranking = _ranking(("A1", "A2"), ("A3",))
        with (patch("server.analysis.v2.pipeline.monotonic", side_effect=lambda: 200 if expired else 0),
              patch("server.analysis.v2.pipeline.prepare_article", return_value=_article()),
              patch("server.analysis.v2.pipeline.analyze_source_article", return_value=_analysis()),
              patch("server.analysis.v2.pipeline.search_candidate_metadata", return_value=search),
              patch("server.analysis.v2.pipeline.rank_comparison_candidates", return_value=ranking),
              patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = run_discovery_pipeline("https://news.example.com/source", max_per_claim=2,
                                            event_baseline_limit=2, verification_batch_size=1,
                                            _deadline_at=100, client=object())

        self.assertEqual(calls, ["A1"])
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["status"], "ready")
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A1"])
        self.assertEqual(result["selection"]["event_baseline_ids"], ["A1"])
        self.assertEqual([row["reason"] for row in result["selection"]["claim_attempts"]],
                         ["verified", "verification_deferred"])
        self.assertEqual([row["reason"] for row in result["selection"]["event_baseline_attempts"]],
                         ["same_event", "verification_deferred"])
        self.assertEqual(result["selection"]["verification_rounds"][0]["deferred_ids"], ["A2"])
        self.assertEqual(result["selection"]["verification_llm_calls"], 1)
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A3"])
        self.assertEqual([row["candidate_id"] for row in result["errors"]], ["A2"])
        self.assertEqual(result["errors"][0]["code"], "MODEL_TIMEOUT")

    def test_internal_verifier_contract_error_is_not_hidden(self):
        def verify(_source, _analysis, rows, *, client):
            return {"unexpected": []}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            with self.assertRaises(KeyError):
                select_verified_comparisons(_article(), _analysis(), _search(("A1",)),
                                            _ranking(("A1",)), max_per_claim=1,
                                            event_baseline_limit=0, verification_batch_size=1,
                                            client=object())

    def test_middle_batch_failure_keeps_prior_and_later_verified_results(self):
        calls = []

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            calls.append(key)
            if key == "A2":
                raise RuntimeError("SECRET_SENTINEL model response")
            return {"assessments": [_verified(key)]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), _search(("A1", "A2", "A3"), discovered_by_event=True),
                _ranking(("A1", "A2", "A3")), max_per_claim=3,
                event_baseline_limit=3, verification_batch_size=1, client=object())

        selection, search = result["selection"], result["search"]
        self.assertEqual(calls, ["A1", "A2", "A3"])
        self.assertEqual([row["candidate_id"] for row in selection["claim_comparisons"][0]["articles"]],
                         ["A1", "A3"])
        self.assertEqual(selection["same_event_ids"], ["A1", "A3"])
        self.assertEqual(selection["event_baseline_ids"], ["A1", "A3"])
        self.assertEqual([row["reason"] for row in selection["claim_attempts"]],
                         ["verified", "verification_failed", "verified"])
        self.assertEqual([row["reason"] for row in selection["event_baseline_attempts"]],
                         ["same_event", "verification_failed", "same_event"])
        self.assertEqual(selection["verification_llm_calls"], 3)
        self.assertEqual(selection["verification_rounds"][0]["verified_ids"], ["A1", "A3"])
        self.assertEqual(selection["verification_rounds"][0]["unverified_ids"], ["A2"])
        unverified = next(row for row in selection["assessments"] if row["candidate_id"] == "A2")
        self.assertEqual(unverified["verification_status"], "unverified")
        self.assertEqual(unverified["claim_matches"], [])
        self.assertEqual(unverified["validation_warnings"],
                         [{"claim_id": None, "reason": "verification_failed"}])
        self.assertEqual(search["errors"][0]["stage"], "candidate_verification")
        self.assertEqual(search["errors"][0]["candidate_id"], "A2")
        self.assertEqual(search["errors"][0]["code"], "PROCESSING_FAILED")
        self.assertNotIn("SECRET_SENTINEL", json.dumps(result, ensure_ascii=False))

    def test_failed_initial_batch_refills_once_from_reserve(self):
        calls = []

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            calls.append(key)
            if key == "A1":
                raise TimeoutError("SECRET_SENTINEL timeout details")
            return {"assessments": [_verified(key)]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(_article(), _analysis(), _search(("A1", "A2")),
                                                 _ranking(("A1",), ("A2",)), max_per_claim=1,
                                                 event_baseline_limit=0, verification_batch_size=1,
                                                 client=object())

        self.assertEqual(calls, ["A1", "A2"])
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A2"])
        self.assertEqual([row["reason"] for row in result["selection"]["claim_attempts"]],
                         ["verification_failed", "verified"])
        self.assertEqual(result["search"]["errors"][0]["code"], "MODEL_TIMEOUT")
        self.assertTrue(result["search"]["errors"][0]["retryable"])
        self.assertNotIn("SECRET_SENTINEL", json.dumps(result, ensure_ascii=False))

    def test_incomplete_batch_response_is_unverified_and_error_is_top_level(self):
        calls = []

        def verify(_source, _analysis, rows, *, client):
            ids = [row["candidate_id"] for row in rows]
            calls.append(ids)
            if ids == ["A1", "A2"]:
                return {"assessments": [_verified("A1")]}
            return {"assessments": [_verified(key) for key in ids]}

        search = _search(("A1", "A2", "A3"))
        ranking = _ranking(("A1", "A2"), ("A3",))
        with (patch("server.analysis.v2.pipeline.prepare_article", return_value=_article()),
              patch("server.analysis.v2.pipeline.analyze_source_article", return_value=_analysis()),
              patch("server.analysis.v2.pipeline.search_candidate_metadata", return_value=search),
              patch("server.analysis.v2.pipeline.rank_comparison_candidates", return_value=ranking),
              patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = run_discovery_pipeline("https://news.example.com/source", max_per_claim=2,
                                            event_baseline_limit=0, verification_batch_size=2,
                                            client=object())

        self.assertEqual(calls, [["A1", "A2"], ["A3"]])
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["status"], "ready")
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A3"])
        self.assertEqual(result["selection"]["same_event_ids"], ["A3"])
        self.assertEqual([row["reason"] for row in result["selection"]["claim_attempts"]],
                         ["verification_failed", "verification_failed", "verified"])
        self.assertEqual([row["candidate_id"] for row in result["errors"]], ["A1", "A2"])
        self.assertTrue(all(row["stage"] == "candidate_verification"
                            and row["code"] == "ANALYSIS_RESPONSE_INVALID" for row in result["errors"]))
        self.assertEqual(result["search"]["errors"], result["errors"])
