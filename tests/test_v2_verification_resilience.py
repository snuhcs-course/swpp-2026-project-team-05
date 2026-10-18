"""Synthetic regression checks for bounded candidate verification failures."""

import json
from unittest.mock import patch

from django.test import SimpleTestCase

from server.analysis.v2.comparison_selection import select_verified_comparisons
from server.analysis.v2.pipeline import run_discovery_pipeline


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
        with (patch("server.analysis.v2.pipeline.monotonic", side_effect=lambda: 2 if expired else 0),
              patch("server.analysis.v2.pipeline.prepare_article", return_value=_article()),
              patch("server.analysis.v2.pipeline.analyze_source_article", return_value=_analysis()),
              patch("server.analysis.v2.pipeline.search_candidate_metadata", return_value=search),
              patch("server.analysis.v2.pipeline.rank_comparison_candidates", return_value=ranking),
              patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = run_discovery_pipeline("https://news.example.com/source", max_per_claim=2,
                                            event_baseline_limit=2, verification_batch_size=1,
                                            _deadline_at=1, client=object())

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
