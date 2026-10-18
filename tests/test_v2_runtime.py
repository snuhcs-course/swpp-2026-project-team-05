"""Offline checks for bounded and privacy-conscious v2 execution."""

import json
from copy import deepcopy
from pathlib import Path
from tempfile import TemporaryDirectory
from time import monotonic
from types import SimpleNamespace
from unittest.mock import Mock, patch

import httpx
from django.test import SimpleTestCase

from server.analysis.diagnostics import analysis_id
from server.analysis.v2.model_client import GeminiJSONClient, MetricsJSONClient, ModelUnavailableError
from server.analysis.v2.pipeline import (_initial_result, _record_failure,
                                         complete_rubric_analysis, run_analysis_pipeline)
from server.analysis.v2.result_output import OperationError, make_error_record


URL = "https://news.example.com/source"
SOURCE = {"url": URL, "title": "가상 기사", "body": "가상의 본문", "publisher": "가상일보",
          "published_at": "", "sentences": []}


class _FakeClient:
    model = "fake-model"
    temperature = 1.0
    seed = 42

    def __init__(self):
        self.calls = 0

    def generate_json(self, **request):
        self.calls += 1
        return {"ok": True}


class RuntimeTests(SimpleTestCase):
    def test_metrics_client_does_not_copy_model_transcripts(self):
        # AI-assisted synthetic test: the wrapper keeps counters, never prompt/body/response copies.
        client = _FakeClient()
        recorder = MetricsJSONClient(client)
        response = recorder.generate_json(name="source_analysis", instructions="PROMPT_SECRET",
                                          input_data={"body": "ARTICLE_SECRET"}, schema={})
        self.assertEqual(response, {"ok": True})
        self.assertFalse(hasattr(recorder, "records"))
        self.assertNotIn("PROMPT_SECRET", repr(vars(recorder)))
        self.assertNotIn("ARTICLE_SECRET", repr(vars(recorder)))
        self.assertEqual(recorder.execution_summary()["model_calls"], 1)
        self.assertEqual(recorder.execution_summary()["attempted_calls"], 1)

    def test_metrics_count_failed_attempt_without_retaining_input(self):
        client = _FakeClient()
        client.generate_json = Mock(side_effect=RuntimeError("MODEL_SECRET"))
        recorder = MetricsJSONClient(client)
        with self.assertRaises(RuntimeError):
            recorder.generate_json(name="bad name ARTICLE_SECRET", input_data={"body": "ARTICLE_SECRET"})
        summary = recorder.execution_summary()
        self.assertEqual((summary["attempted_calls"], summary["failed_calls"], summary["model_calls"]),
                         (1, 1, 0))
        self.assertEqual(summary["task_counts"], {})
        self.assertNotIn("ARTICLE_SECRET", repr(recorder.attempts))

    def test_expired_budget_stops_before_model_call(self):
        client = _FakeClient()
        recorder = MetricsJSONClient(client, deadline_at=monotonic() - 1)
        with self.assertRaises(TimeoutError):
            recorder.generate_json(name="source_analysis", input_data={})
        self.assertEqual(client.calls, 0)

    def test_failure_log_rejects_unknown_dynamic_error_code(self):
        token = analysis_id.set("synthetic123")
        try:
            with self.assertLogs("server.analysis.v2.pipeline", level="WARNING") as captured:
                _record_failure(_initial_result(URL), OperationError("ARTICLE_SECRET", code="ARTICLE_SECRET"),
                                "source_analysis")
        finally:
            analysis_id.reset(token)
        self.assertIn("analysis_id=synthetic123", captured.output[0])
        self.assertIn("error_code=PROCESSING_FAILED", captured.output[0])
        self.assertNotIn("ARTICLE_SECRET", captured.output[0])

    def test_uncached_pipeline_uses_metrics_and_keeps_result_contract(self):
        client = _FakeClient()
        source = deepcopy(SOURCE)
        discovery = {"completed_stage": 4, "status": "ready", "execution_status": "success",
                     "errors": [], "warnings": [], "source_article": source,
                     "source_analysis": {"claims": [], "core_event": "가상 사건"},
                     "search": {"candidates": []}, "selection": {"claim_comparisons": []},
                     "comparison_status": "no_matches"}

        def fake_discovery(*args, **kwargs):
            recorder = kwargs["client"]
            self.assertIsInstance(recorder, MetricsJSONClient)
            self.assertFalse(hasattr(recorder, "records"))
            recorder.generate_json(name="source_analysis", instructions="PROMPT_SECRET",
                                   input_data={"body": "ARTICLE_SECRET"}, schema={})
            return deepcopy(discovery)

        def fake_rubric(value, **kwargs):
            return {**value, "completed_stage": 5, "rubric_analysis": {}}

        token = analysis_id.set("synthetic123")
        try:
            with (TemporaryDirectory() as directory,
                  patch("server.analysis.v2.pipeline.prepare_article", return_value=source),
                  patch("server.analysis.v2.pipeline.run_discovery_pipeline", side_effect=fake_discovery),
                  patch("server.analysis.v2.pipeline.complete_rubric_analysis", side_effect=fake_rubric),
                  self.assertLogs("server.analysis.v2.pipeline", level="INFO") as captured):
                result = run_analysis_pipeline(URL, client=client, use_cache=False, cache_dir=directory)
                self.assertEqual(list(Path(directory).iterdir()), [])
        finally:
            analysis_id.reset(token)
        self.assertEqual(result["completed_stage"], 5)
        self.assertEqual(result["reproducibility"]["execution"]["model_calls"], 1)
        self.assertEqual(result["reproducibility"]["execution"]["attempted_calls"], 1)
        self.assertIsNone(result["reproducibility"]["snapshot_path"])
        self.assertEqual(result["reproducibility"]["snapshot_id_basis"], "result_without_model_transcript")
        self.assertNotIn("PROMPT_SECRET", json.dumps(result))
        logs = "\n".join(captured.output)
        self.assertIn('"analysis_id":"synthetic123"', logs)
        self.assertIn('"stage":"rubric_analysis"', logs)
        self.assertIn('"duration_ms":', logs)
        self.assertIn('"event":"model_execution"', logs)
        self.assertIn('"attempted_calls":1', logs)
        self.assertNotIn("PROMPT_SECRET", logs)
        self.assertNotIn("ARTICLE_SECRET", logs)

    def test_budget_expiry_after_discovery_preserves_stage_four(self):
        client = _FakeClient()
        discovery = {"completed_stage": 4, "status": "ready", "execution_status": "success",
                     "errors": [], "warnings": [], "source_article": deepcopy(SOURCE),
                     "source_analysis": {"claims": [], "core_event": "가상 사건"},
                     "search": {"candidates": []}, "selection": {"claim_comparisons": []},
                     "comparison_status": "available", "rubric_analysis": None}
        with (patch("server.analysis.v2.pipeline.prepare_article", return_value=deepcopy(SOURCE)),
              patch("server.analysis.v2.pipeline.run_discovery_pipeline", return_value=discovery),
              patch("server.analysis.v2.pipeline.complete_rubric_analysis") as rubric,
              patch("server.analysis.v2.pipeline.monotonic", side_effect=[100.0, 100.1, 100.2, 101.0])):
            result = run_analysis_pipeline(URL, client=client, use_cache=False,
                                           execution_budget_seconds=0.5)
        rubric.assert_not_called()
        self.assertEqual(result["completed_stage"], 4)
        self.assertEqual(result["execution_status"], "partial")
        self.assertEqual(result["comparison_status"], "available")
        self.assertEqual(result["errors"][0]["code"], "MODEL_TIMEOUT")

    def test_gemini_call_limits_sdk_timeout_to_remaining_budget(self):
        # No provider call: a local fake captures the SDK config sent by the adapter.
        client = object.__new__(GeminiJSONClient)
        client.model, client.temperature, client.seed = "fake-model", 1.0, 42
        client._client = SimpleNamespace(models=SimpleNamespace(generate_content=Mock(
            return_value=SimpleNamespace(text='{"ok":true}'))))
        result = client.generate_json(name="source_analysis", instructions="synthetic",
                                      input_data={}, schema={"type": "object"},
                                      timeout_budget_ms=45000)
        self.assertEqual(result, {"ok": True})
        config = client._client.models.generate_content.call_args.kwargs["config"]
        self.assertLessEqual(config["http_options"].timeout, 45000)
        self.assertEqual(config["http_options"].retry_options.attempts, 1)

    def test_gemini_transport_failures_have_safe_retryable_codes(self):
        for transport_error, expected_type, expected_code in (
            (httpx.ReadTimeout("MODEL_SECRET"), TimeoutError, "MODEL_TIMEOUT"),
            (httpx.ConnectError("MODEL_SECRET"), ModelUnavailableError, "MODEL_UNAVAILABLE"),
            (httpx.ReadError("MODEL_SECRET"), ModelUnavailableError, "MODEL_UNAVAILABLE"),
            (httpx.RemoteProtocolError("MODEL_SECRET"), ModelUnavailableError, "MODEL_UNAVAILABLE"),
            (httpx.ProxyError("MODEL_SECRET"), ModelUnavailableError, "MODEL_UNAVAILABLE"),
        ):
            with self.subTest(expected_code=expected_code):
                client = object.__new__(GeminiJSONClient)
                client.model, client.temperature, client.seed = "fake-model", 1.0, 42
                client._client = SimpleNamespace(models=SimpleNamespace(generate_content=Mock(
                    side_effect=transport_error)))
                with self.assertRaises(expected_type) as raised:
                    client.generate_json(name="source_analysis", instructions="synthetic",
                                         input_data={}, schema={"type": "object"})
                error = make_error_record(raised.exception, stage="source_analysis")
                self.assertEqual(error["code"], expected_code)
                self.assertTrue(error["retryable"])
                self.assertNotIn("MODEL_SECRET", str(error))

    def test_related_article_unverified_retryability_is_preserved(self):
        discovery = {"completed_stage": 4, "status": "ready", "execution_status": "success",
                     "errors": [], "warnings": [], "source_article": deepcopy(SOURCE),
                     "source_analysis": {"core_event": "가상 사건", "claims": [{
                         "claim_id": "C1", "claim": "가상의 주장", "claim_kind": "reported_fact",
                         "speaker": "", "evidence": {"quote": "가상의 본문"},
                     }]},
                     "search": {"candidates": [{"candidate_id": "A1"}], "errors": [],
                                "fetch_failures": [], "queries": [], "search_status": "success"},
                     "selection": {"claim_comparisons": [{"claim_id": "C1",
                                                           "articles": [{"candidate_id": "A1"}]}],
                                   "same_event_ids": ["A1"], "event_baseline_ids": [], "assessments": []},
                     "comparison_status": "available", "rubric_analysis": None}
        for retryable in (False, True):
            with self.subTest(retryable=retryable), patch(
                "server.analysis.v2.pipeline.analyze_selected_articles",
                return_value={"warnings": [], "article_annotation_failures": [
                    {"article_id": "A1", "retryable": retryable}], "expression_failures": []},
            ):
                result = complete_rubric_analysis(discovery, client=_FakeClient())
            self.assertEqual(result["execution_status"], "partial")
            self.assertEqual(result["errors"][0]["code"], "RUBRIC_ARTICLE_UNVERIFIED")
            self.assertEqual(result["errors"][0]["retryable"], retryable)
