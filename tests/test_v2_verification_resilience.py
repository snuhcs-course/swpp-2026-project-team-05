"""Synthetic regression checks for bounded candidate verification failures."""

import json
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from threading import Barrier, Event, Lock
from time import monotonic, sleep
from unittest.mock import patch

from django.test import SimpleTestCase

from server.analysis.v2.comparison_selection import select_verified_comparisons
from server.analysis.v2.model_client import MetricsJSONClient, OpenRouterJSONClient, SnapshotJSONClient
from server.analysis.v2.pipeline import run_discovery_pipeline
from server.analysis.v2.result_output import OperationError, make_error_record
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


def _live_metrics_client():
    # Only the production wrapper/client types are needed; these tests patch
    # verification before any network access or credential lookup.
    return MetricsJSONClient(object.__new__(OpenRouterJSONClient))


class VerificationResilienceTests(SimpleTestCase):
    def test_next_body_fetch_overlaps_first_verdict_and_is_reused_by_refill(self):
        next_started, release_fetch = Event(), Event()
        fetched = []

        def fetch(rows):
            key = rows[0]["candidate_id"]
            fetched.append(key)
            if key == "A2":
                next_started.set()
                release_fetch.wait(timeout=3)
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            if key == "A1":
                self.assertTrue(next_started.wait(timeout=3))
                release_fetch.set()
                return {"assessments": [{"candidate_id": key, "event_relation": "different_event",
                                         "event_evidence": None, "claim_matches": []}]}
            return {"assessments": [_verified(key)]}

        try:
            with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
                  patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
                result = select_verified_comparisons(
                    _article(), _analysis(), _search(("A1", "A2")), _ranking(("A1",), ("A2",)),
                    max_per_claim=1, event_baseline_limit=0, verification_batch_size=1,
                    client=_live_metrics_client())
        finally:
            release_fetch.set()

        self.assertEqual(Counter(fetched), Counter(("A1", "A2")))
        self.assertEqual([row["candidate_id"] for row in result["selection"]["assessments"]], ["A1", "A2"])
        self.assertEqual(result["selection"]["claim_comparisons"][0]["articles"][0]["candidate_id"], "A2")
        self.assertEqual(result["search"]["fetch_attempt_count"], 2)
        self.assertEqual(result["search"]["prefetch_discarded_ids"], [])

    def test_unused_speculative_fetch_failure_does_not_change_rate_limit_result(self):
        next_started, release_fetch = Event(), Event()
        fetched = []

        def fetch(rows):
            key = rows[0]["candidate_id"]
            fetched.append(key)
            if key == "A2":
                next_started.set()
                release_fetch.wait(timeout=3)
                raise RuntimeError("speculative body failure")
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            self.assertTrue(next_started.wait(timeout=3))
            raise OperationError("한도 초과", code="MODEL_RATE_LIMITED", retryable=True, http_status=429)

        try:
            with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
                  patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
                started = monotonic()
                result = select_verified_comparisons(
                    _article(), _analysis(), _search(("A1", "A2")), _ranking(("A1",), ("A2",)),
                    max_per_claim=1, event_baseline_limit=0, verification_batch_size=1,
                    client=_live_metrics_client())
                elapsed = monotonic() - started
        finally:
            release_fetch.set()

        self.assertLess(elapsed, 2.0)
        self.assertEqual(Counter(fetched), Counter(("A1", "A2")))
        self.assertEqual(result["search"]["fetch_attempt_count"], 1)
        self.assertEqual(result["search"]["fetch_failures"], [])
        self.assertEqual(result["search"]["prefetch_discarded_ids"], ["A2"])
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A2"])

    def test_queued_speculative_fetch_cancelled_before_start_is_not_discarded(self):
        started, release_fetch = Event(), Event()
        fetched = []

        @contextmanager
        def single_worker_prefetch(enabled, *, max_workers=1):
            if not enabled:
                yield None
                return
            executor = ThreadPoolExecutor(max_workers=1)
            try:
                yield executor
            finally:
                executor.shutdown(wait=False, cancel_futures=True)

        def fetch(rows):
            key = rows[0]["candidate_id"]
            fetched.append(key)
            if key == "A2":
                started.set()
                release_fetch.wait(timeout=3)
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            self.assertTrue(started.wait(timeout=3))
            raise OperationError("한도 초과", code="MODEL_RATE_LIMITED", retryable=True, http_status=429)

        try:
            with (patch("server.analysis.v2.comparison_selection._prefetch_executor",
                        side_effect=single_worker_prefetch),
                  patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
                  patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
                result = select_verified_comparisons(
                    _article(), _analysis(), _search(("A1", "A2", "A3", "A4")),
                    _ranking(("A1",), ("A2", "A3", "A4")), max_per_claim=1,
                    event_baseline_limit=0, verification_batch_size=1, client=_live_metrics_client())
        finally:
            release_fetch.set()

        self.assertEqual(fetched, ["A1", "A2"])
        self.assertEqual(result["search"]["fetch_attempt_count"], 1)
        self.assertEqual(result["search"]["prefetch_discarded_ids"], ["A2"])
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A2", "A3", "A4"])

    def test_prefetched_fetch_failure_counts_once_only_when_selected(self):
        fetched, verified = [], []

        def fetch(rows):
            key = rows[0]["candidate_id"]
            fetched.append(key)
            if key == "A2":
                failure = make_error_record(OperationError("본문 수집 실패", code="ARTICLE_FETCH_FAILED"),
                                            stage="candidate_fetch", candidate_id=key)
                return {"candidates": [], "fetch_failures": [failure]}
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            verified.append(key)
            return {"assessments": [{"candidate_id": key, "event_relation": "different_event",
                                     "event_evidence": None, "claim_matches": []}]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), _search(("A1", "A2")), _ranking(("A1",), ("A2",)),
                max_per_claim=1, event_baseline_limit=0, verification_batch_size=1,
                client=_live_metrics_client())

        self.assertEqual(Counter(fetched), Counter(("A1", "A2")))
        self.assertEqual(verified, ["A1"])
        self.assertEqual(result["search"]["fetch_attempt_count"], 2)
        self.assertEqual([row["candidate_id"] for row in result["search"]["fetch_failures"]], ["A2"])
        self.assertEqual(result["search"]["prefetch_discarded_ids"], [])

    def test_live_singletons_overlap_but_apply_in_candidate_order(self):
        barrier, lock = Barrier(3), Lock()
        active, maximum, completed = 0, 0, []

        def verify(_source, _analysis, rows, *, client):
            nonlocal active, maximum
            key = rows[0]["candidate_id"]
            with lock:
                active += 1
                maximum = max(maximum, active)
            if key in {"A1", "A2", "A3"}:
                barrier.wait(timeout=3)
            if key == "A1":
                sleep(0.03)
            with lock:
                completed.append(key)
                active -= 1
            return {"assessments": [_verified(key)]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), _search(("A1", "A2", "A3")),
                _ranking(("A1", "A2", "A3")), max_per_claim=3,
                event_baseline_limit=0, verification_batch_size=1, client=_live_metrics_client())

        self.assertEqual(maximum, 3)
        self.assertCountEqual(completed, ["A1", "A2", "A3"])
        self.assertGreater(completed.index("A1"), completed.index("A2"))
        self.assertEqual([row["candidate_id"] for row in result["selection"]["assessments"]],
                         ["A1", "A2", "A3"])
        self.assertEqual(result["selection"]["verification_rounds"][0]["verified_ids"],
                         ["A1", "A2", "A3"])
        self.assertEqual(result["selection"]["verification_llm_calls"], 3)

    def test_quality_pool_singletons_overlap_without_changing_best_article(self):
        barrier = Barrier(2)

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            if key in {"A2", "A3"}:
                barrier.wait(timeout=3)
            assessment = _verified(key)
            if key == "A3":
                assessment["claim_matches"][0]["relation"] = "same"
            return {"assessments": [assessment]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), _search(("A1", "A2", "A3")),
                _ranking(("A1",), ("A2", "A3")), max_per_claim=1,
                event_baseline_limit=0, verification_batch_size=1, client=_live_metrics_client())

        self.assertEqual(result["selection"]["claim_comparisons"][0]["articles"][0]["candidate_id"], "A3")
        self.assertEqual(result["selection"]["verification_llm_calls"], 3)

    def test_next_quality_batch_fetch_overlaps_current_verification(self):
        next_fetch_started = Event()
        fetched_batches = []

        def fetch(rows):
            ids = [row["candidate_id"] for row in rows]
            fetched_batches.append(ids)
            if ids == ["A5"]:
                next_fetch_started.set()
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            ids = [row["candidate_id"] for row in rows]
            if ids[0] in {"A2", "A3", "A4"}:
                self.assertTrue(next_fetch_started.wait(timeout=3))
            return {"assessments": [_verified(key) for key in ids]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), _search(("A1", "A2", "A3", "A4", "A5")),
                _ranking(("A1",), ("A2", "A3", "A4", "A5")), max_per_claim=1,
                event_baseline_limit=0, verification_batch_size=1, client=_live_metrics_client())

        self.assertEqual(Counter(key for batch in fetched_batches for key in batch),
                         Counter(("A1", "A2", "A3", "A4", "A5")))
        self.assertIn(["A5"], fetched_batches)
        self.assertEqual([row["candidate_id"] for row in result["selection"]["assessments"]],
                         ["A1", "A2", "A3", "A4", "A5"])
        self.assertEqual(result["search"]["fetch_attempt_count"], 5)
        self.assertEqual(result["search"]["prefetch_discarded_ids"], [])
        self.assertEqual(result["selection"]["verification_llm_calls"], 5)

    def test_rate_limit_discards_prefetch_without_launching_later_batch(self):
        fetched_batches = []
        next_fetch_started, release_fetch = Event(), Event()

        def fetch(rows):
            ids = [row["candidate_id"] for row in rows]
            fetched_batches.append(ids)
            if ids == ["A5"]:
                next_fetch_started.set()
                release_fetch.wait(timeout=5)
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            if key == "A2":
                self.assertTrue(next_fetch_started.wait(timeout=3))
                raise OperationError("한도 초과", code="MODEL_RATE_LIMITED", retryable=True,
                                     http_status=429)
            return {"assessments": [_verified(key)]}

        try:
            with (patch("server.analysis.v2.comparison_selection.MAX_PARALLEL_VERIFICATIONS", 2),
                  patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
                  patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
                started = monotonic()
                result = select_verified_comparisons(
                    _article(), _analysis(), _search(("A1", "A2", "A3", "A4", "A5", "A6", "A7")),
                    _ranking(("A1",), ("A2", "A3", "A4", "A5", "A6", "A7")), max_per_claim=1,
                    event_baseline_limit=0, verification_batch_size=1, client=_live_metrics_client())
                elapsed = monotonic() - started
        finally:
            release_fetch.set()

        # A4/A5 started before the 429 was known. They never become attempted
        # candidates, and the later A6/A7 batch is not fetched.
        self.assertLess(elapsed, 2.0)
        self.assertEqual(Counter(key for batch in fetched_batches for key in batch),
                         Counter(("A1", "A2", "A3", "A4", "A5")))
        self.assertEqual(result["search"]["fetch_attempt_count"], 3)
        self.assertEqual(result["search"]["prefetch_discarded_ids"], ["A4", "A5"])
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A4", "A5", "A6", "A7"])
        self.assertEqual([row["candidate_id"] for row in result["selection"]["assessments"]],
                         ["A1", "A2", "A3"])
        self.assertEqual(result["selection"]["assessments"][1]["verification_status"], "unverified")

    def test_deadline_does_not_wait_for_inflight_quality_prefetch(self):
        next_fetch_started, release_fetch, expired = Event(), Event(), Event()

        def fetch(rows):
            if [row["candidate_id"] for row in rows] == ["A5"]:
                next_fetch_started.set()
                release_fetch.wait(timeout=5)
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            if key == "A2":
                self.assertTrue(next_fetch_started.wait(timeout=3))
                expired.set()
            return {"assessments": [_verified(key)]}

        try:
            with (patch("server.analysis.v2.comparison_selection.MAX_PARALLEL_VERIFICATIONS", 2),
                  patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
                  patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
                started = monotonic()
                result = select_verified_comparisons(
                    _article(), _analysis(), _search(("A1", "A2", "A3", "A4", "A5")),
                    _ranking(("A1",), ("A2", "A3", "A4", "A5")), max_per_claim=1,
                    event_baseline_limit=0, verification_batch_size=1,
                    should_stop=expired.is_set, client=_live_metrics_client())
                elapsed = monotonic() - started
        finally:
            release_fetch.set()

        self.assertLess(elapsed, 2.0)
        self.assertEqual(result["search"]["fetch_attempt_count"], 3)
        self.assertEqual(result["search"]["prefetch_discarded_ids"], ["A4", "A5"])
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A4", "A5"])

    def test_deadline_arriving_during_prefetch_wait_returns_without_fetch_completion(self):
        next_fetch_started, release_fetch, expired = Event(), Event(), Event()
        wait_calls = []

        def fetch(rows):
            if [row["candidate_id"] for row in rows] == ["A5"]:
                next_fetch_started.set()
                release_fetch.wait(timeout=5)
            return _fetch(rows)

        def verify(_source, _analysis, rows, *, client):
            if rows[0]["candidate_id"] == "A2":
                self.assertTrue(next_fetch_started.wait(timeout=3))
            return {"assessments": [_verified(rows[0]["candidate_id"])]}

        def expire_while_waiting(futures, *, timeout):
            wait_calls.append(timeout)
            expired.set()
            return set(), set(futures)

        try:
            with (patch("server.analysis.v2.comparison_selection.MAX_PARALLEL_VERIFICATIONS", 2),
                  patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=fetch),
                  patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify),
                  patch("server.analysis.v2.comparison_selection.wait", side_effect=expire_while_waiting)):
                started = monotonic()
                result = select_verified_comparisons(
                    _article(), _analysis(), _search(("A1", "A2", "A3", "A4", "A5")),
                    _ranking(("A1",), ("A2", "A3", "A4", "A5")), max_per_claim=1,
                    event_baseline_limit=0, verification_batch_size=1,
                    should_stop=expired.is_set, client=_live_metrics_client())
                elapsed = monotonic() - started
        finally:
            release_fetch.set()

        self.assertEqual(wait_calls, [0.1])
        self.assertLess(elapsed, 2.0)
        self.assertEqual(result["search"]["fetch_attempt_count"], 3)
        self.assertEqual(result["search"]["prefetch_discarded_ids"], ["A4", "A5"])
        self.assertEqual(result["search"]["unused_candidate_ids"], ["A4", "A5"])

    def test_parallel_rate_limit_keeps_in_flight_result_and_defers_later_candidate(self):
        barrier = Barrier(3)
        calls = []
        search = _search(("A1", "A2", "A3", "A4"))
        search["metadata_candidates"][-1]["discovered_by_event"] = True

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            calls.append(key)
            barrier.wait(timeout=3)
            if key == "A1":
                raise OperationError("한도 초과", code="MODEL_RATE_LIMITED", retryable=True,
                                     http_status=429)
            return {"assessments": [_verified(key)]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), search,
                _ranking(("A1", "A2", "A3"), ("A4",)), max_per_claim=3,
                event_baseline_limit=1, verification_batch_size=1, client=_live_metrics_client())

        self.assertCountEqual(calls, ["A1", "A2", "A3"])
        self.assertEqual([row["candidate_id"] for row in result["selection"]["claim_comparisons"][0]["articles"]],
                         ["A2", "A3"])
        self.assertEqual(result["selection"]["verification_rounds"][0]["deferred_ids"], ["A4"])
        self.assertEqual([row["candidate_id"] for row in result["search"]["errors"]], ["A1", "A4"])
        self.assertEqual(result["selection"]["verification_llm_calls"], 3)

    def test_parallel_deadline_keeps_started_verifications_and_defers_next_wave(self):
        barrier, expired = Barrier(3), Event()
        calls = []
        search = _search(("A1", "A2", "A3", "A4"))
        search["metadata_candidates"][-1]["discovered_by_event"] = True

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            calls.append(key)
            barrier.wait(timeout=3)
            expired.set()
            return {"assessments": [_verified(key)]}

        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify)):
            result = select_verified_comparisons(
                _article(), _analysis(), search,
                _ranking(("A1", "A2", "A3"), ("A4",)), max_per_claim=3,
                event_baseline_limit=1, verification_batch_size=1,
                should_stop=expired.is_set, client=_live_metrics_client())

        self.assertCountEqual(calls, ["A1", "A2", "A3"])
        self.assertEqual(result["selection"]["verification_rounds"][0]["verified_ids"], ["A1", "A2", "A3"])
        self.assertEqual(result["selection"]["verification_rounds"][0]["deferred_ids"], ["A4"])
        self.assertEqual(result["selection"]["verification_llm_calls"], 3)

    def test_snapshot_client_keeps_serial_verification_order(self):
        calls = []

        def verify(_source, _analysis, rows, *, client):
            key = rows[0]["candidate_id"]
            calls.append(key)
            return {"assessments": [_verified(key)]}

        snapshot = SnapshotJSONClient(object.__new__(OpenRouterJSONClient), settings={})
        with (patch("server.analysis.v2.comparison_selection.fetch_candidate_articles", side_effect=_fetch),
              patch("server.analysis.v2.comparison_selection.verify_candidate_articles", side_effect=verify),
              patch("server.analysis.v2.comparison_selection.ThreadPoolExecutor",
                    side_effect=AssertionError("Snapshot verification must be serial"))):
            result = select_verified_comparisons(
                _article(), _analysis(), _search(("A1", "A2")),
                _ranking(("A1", "A2")), max_per_claim=2,
                event_baseline_limit=0, verification_batch_size=1, client=snapshot)

        self.assertEqual(calls, ["A1", "A2"])
        self.assertEqual(result["selection"]["verification_llm_calls"], 2)

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
