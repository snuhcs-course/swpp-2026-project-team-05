"""Offline checks for task-level model selection in the v2 pipeline."""

import os
from time import monotonic
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import patch

from server.analysis.v2.model_client import (
    GeminiJSONClient, MetricsJSONClient, ModelResponseError, ModelResponseTruncatedError,
    OpenRouterJSONClient, get_generation_settings,
    get_missing_configuration, get_openrouter_generation_settings,
)


class ModelRoutingTests(TestCase):
    @patch.dict(os.environ, {}, clear=True)
    def test_default_routes_deep_analysis_to_quality_model(self):
        settings = get_generation_settings()
        routes = settings["task_models"]
        self.assertEqual(routes["rank_claim_candidates_from_metadata"], "gemini-3.1-flash-lite")
        self.assertEqual(routes["related_content_alignment"], "gemini-3.8-flash")
        self.assertEqual(routes["review_source_event_scope"], "gemini-3.8-flash")

    @patch.dict(os.environ, {"GEMINI_MODEL": "gemini-3.1-flash-lite",
                           "GEMINI_QUALITY_MODEL": "gemini-3.8-flash"}, clear=True)
    def test_specific_override_takes_precedence_over_global_model(self):
        routes = get_generation_settings()["task_models"]
        self.assertEqual(routes["source_event_claims_queries"], "gemini-3.8-flash")
        self.assertEqual(routes["rank_claim_candidates_from_metadata"], "gemini-3.1-flash-lite")

    @patch.dict(os.environ, {"GEMINI_MODEL": "gemini-3.1-flash-lite"}, clear=True)
    def test_legacy_global_lite_does_not_downgrade_quality_route(self):
        routes = get_generation_settings()["task_models"]
        self.assertEqual(routes["replan_source_event_claims_queries"], "gemini-3.8-flash")
        self.assertEqual(routes["verify_selected_candidate_bodies"], "gemini-3.8-flash")
        self.assertEqual(routes["rank_claim_candidates_from_metadata"], "gemini-3.1-flash-lite")

    @patch.dict(os.environ, {"GOOGLE_API_KEY": "synthetic-test-key"}, clear=True)
    @patch("google.genai.Client")
    def test_repair_uses_same_model_as_original_task(self, client_constructor):
        client_constructor.return_value.models.generate_content.return_value = SimpleNamespace(text="{}")
        client = GeminiJSONClient()
        request = {"instructions": "Return an object", "input_data": {}, "schema": {"type": "object"}}
        for name in ("source_event_claims_queries", "repair_source_event_claims_queries",
                     "replan_source_event_claims_queries", "repair_replan_source_event_claims_queries",
                     "rank_claim_candidates_from_metadata"):
            client.generate_json(name=name, **request)
        calls = client_constructor.return_value.models.generate_content.call_args_list
        self.assertEqual([call.kwargs["model"] for call in calls],
                         ["gemini-3.8-flash"] * 4 + ["gemini-3.1-flash-lite"])
        self.assertTrue(all("temperature" not in call.kwargs["config"] for call in calls[:4]))
        self.assertEqual(calls[4].kwargs["config"]["temperature"], 1.0)

    @patch.dict(os.environ, {"OPENROUTER_API_KEY": "synthetic-test-key",
                           "NAVER_CLIENT_ID": "synthetic-id", "NAVER_CLIENT_SECRET": "synthetic-secret"}, clear=True)
    def test_team_key_alone_satisfies_model_configuration(self):
        self.assertEqual(get_missing_configuration(), [])

    @patch.dict(os.environ, {}, clear=True)
    def test_openrouter_default_routes_are_explicit(self):
        settings = get_openrouter_generation_settings()
        self.assertIsNone(settings["seed"])
        self.assertEqual(settings["task_models"]["source_event_claims_queries"],
                         "google/gemini-3.8-flash")
        self.assertEqual(settings["task_models"]["rank_claim_candidates_from_metadata"],
                         "google/gemini-3.1-flash-lite")

    @patch.dict(os.environ, {"OPENROUTER_SEED": "42"}, clear=True)
    def test_openrouter_seed_is_opt_in(self):
        self.assertEqual(get_openrouter_generation_settings()["seed"], 42)
        self.assertIsNone(get_openrouter_generation_settings(seed=None)["seed"])

    @patch.dict(os.environ, {"OPENROUTER_MODEL": "google/gemini-3.8-flash",
                           "OPENROUTER_METADATA_MODEL": "google/gemini-3.8-flash"}, clear=True)
    def test_openrouter_metadata_override(self):
        settings = get_openrouter_generation_settings()
        self.assertEqual(settings["task_models"]["rank_claim_candidates_from_metadata"],
                         "google/gemini-3.8-flash")

    @patch.dict(os.environ, {"OPENROUTER_API_KEY": "synthetic-test-key"}, clear=True)
    @patch("httpx.Client")
    def test_openrouter_request_uses_structured_output_and_repair_route(self, client_constructor):
        response = SimpleNamespace(
            status_code=200, content=b'{"ok":true}',
            json=lambda: {"choices": [{"message": {"content": '{"ok":true}'},
                                         "finish_reason": "stop"}]},
        )
        client_constructor.return_value.post.return_value = response
        client = OpenRouterJSONClient()
        schema = {"type": "object", "properties": {"ok": {"type": "boolean"}},
                  "required": ["ok"], "additionalProperties": False}
        for task in ("source_event_claims_queries", "repair_source_event_claims_queries",
                     "rank_claim_candidates_from_metadata"):
            self.assertEqual(client.generate_json(
                name=task, instructions="Return one value", input_data={"body": "synthetic"},
                schema=schema, max_output_tokens=4096), {"ok": True})
        constructor_args = client_constructor.call_args.kwargs
        self.assertFalse(constructor_args["follow_redirects"])
        self.assertEqual(constructor_args["headers"]["Authorization"], "Bearer synthetic-test-key")
        calls = client_constructor.return_value.post.call_args_list
        self.assertEqual([call.args[0] for call in calls],
                         ["https://openrouter.ai/api/v1/chat/completions"] * 3)
        self.assertEqual([call.kwargs["json"]["model"] for call in calls],
                         ["google/gemini-3.8-flash"] * 2 + ["google/gemini-3.1-flash-lite"])
        self.assertTrue(all(call.kwargs["json"]["response_format"]["json_schema"]["strict"]
                            for call in calls))
        self.assertTrue(all(call.kwargs["json"]["provider"]["require_parameters"]
                            for call in calls))
        self.assertEqual(calls[0].kwargs["json"]["response_format"]["json_schema"]["schema"], schema)
        self.assertTrue(all(call.kwargs["json"]["max_tokens"] == 4096 for call in calls))
        self.assertTrue(all("seed" not in call.kwargs["json"] for call in calls))
        self.assertNotIn("temperature", calls[0].kwargs["json"])
        self.assertIn("temperature", calls[2].kwargs["json"])
        client.close()

    @patch.dict(os.environ, {"OPENROUTER_API_KEY": "synthetic-test-key"}, clear=True)
    @patch("httpx.Client")
    def test_openrouter_usage_and_task_latency_are_recorded_without_content(self, constructor):
        constructor.return_value.post.return_value = SimpleNamespace(
            status_code=200, content=b"synthetic-envelope",
            json=lambda: {"choices": [{"message": {"content": '{"ok":true}'},
                                         "finish_reason": "stop"}],
                          "usage": {"prompt_tokens": 120, "completion_tokens": 80,
                                    "total_tokens": 200, "cost": 0.0045,
                                    "completion_tokens_details": {"reasoning_tokens": 35}}},
        )
        recorder = MetricsJSONClient(OpenRouterJSONClient())
        with self.assertLogs("server.analysis.v2.model_client", level="INFO") as captured:
            result = recorder.generate_json(
                name="source_event_claims_queries", instructions="PROMPT_SECRET",
                input_data={"body": "ARTICLE_SECRET"}, schema={"type": "object"})
        self.assertEqual(result, {"ok": True})
        execution = recorder.execution_summary()
        self.assertEqual(execution["provider_usage"]["metered_responses"], 1)
        self.assertEqual(execution["provider_usage"]["input_tokens"], 120)
        self.assertEqual(execution["provider_usage"]["reasoning_tokens"], 35)
        self.assertEqual(execution["provider_usage"]["reported_cost_usd"], 0.0045)
        self.assertIn("source_event_claims_queries", execution["task_duration_ms"])
        self.assertIn('"finish_reason":"stop"', captured.output[0])
        self.assertNotIn("PROMPT_SECRET", str(captured.output) + repr(execution))
        self.assertNotIn("ARTICLE_SECRET", str(captured.output) + repr(execution))
        recorder.client.close()

    @patch.dict(os.environ, {"OPENROUTER_API_KEY": "synthetic-test-key"}, clear=True)
    @patch("httpx.Client")
    def test_full_body_verification_has_90_second_provider_timeout_only(self, constructor):
        constructor.return_value.post.return_value = SimpleNamespace(
            status_code=200, content=b"synthetic-envelope",
            json=lambda: {"choices": [{"message": {"content": '{}'}, "finish_reason": "stop"}]},
        )
        client = OpenRouterJSONClient()
        request = {"instructions": "synthetic", "input_data": {}, "schema": {"type": "object"}}
        client.generate_json(name="verify_selected_candidate_bodies", **request)
        client.generate_json(name="source_event_claims_queries", **request)
        calls = constructor.return_value.post.call_args_list
        self.assertEqual([call.kwargs["timeout"] for call in calls], [90.0, 60.0])
        client.close()

    @patch.dict(os.environ, {"OPENROUTER_API_KEY": "synthetic-test-key"}, clear=True)
    @patch("httpx.Client")
    def test_length_without_content_is_still_classified_as_truncation(self, constructor):
        constructor.return_value.post.return_value = SimpleNamespace(
            status_code=200, content=b"synthetic-envelope",
            json=lambda: {"choices": [{"message": {}, "finish_reason": "length"}]},
        )
        client = OpenRouterJSONClient()
        with self.assertRaises(ModelResponseTruncatedError):
            client.generate_json(name="verify_selected_candidate_bodies", instructions="synthetic",
                                 input_data={}, schema={"type": "object"})
        self.assertEqual(constructor.return_value.post.call_count, 1)
        client.close()

    @patch.dict(os.environ, {"OPENROUTER_API_KEY": "synthetic-test-key"}, clear=True)
    @patch("httpx.Client")
    def test_pre_provider_timeout_does_not_repeat_previous_call_usage(self, constructor):
        constructor.return_value.post.return_value = SimpleNamespace(
            status_code=200, content=b"synthetic-envelope",
            json=lambda: {"choices": [{"message": {"content": '{"ok":true}'},
                                         "finish_reason": "stop"}],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 2,
                                    "total_tokens": 12, "cost": 0.01}},
        )
        recorder = MetricsJSONClient(OpenRouterJSONClient())
        request = {"name": "related_content_alignment", "instructions": "synthetic",
                   "input_data": {}, "schema": {"type": "object"}}
        recorder.generate_json(**request)
        recorder.deadline_at = monotonic() + 0.5
        with self.assertLogs("server.analysis.v2.model_client", level="INFO") as captured:
            with self.assertRaises(TimeoutError):
                recorder.generate_json(**request)
        self.assertEqual(constructor.return_value.post.call_count, 1)
        self.assertNotIn('"input_tokens"', captured.output[0])
        self.assertNotIn('"reported_cost_usd"', captured.output[0])
        self.assertEqual(recorder.execution_summary()["provider_usage"]["metered_responses"], 1)
        recorder.client.close()

    @patch.dict(os.environ, {"OPENROUTER_API_KEY": "synthetic-test-key"}, clear=True)
    @patch("httpx.Client")
    def test_truncation_keeps_usage_and_reports_safe_finish_reason(self, constructor):
        constructor.return_value.post.return_value = SimpleNamespace(
            status_code=200, content=b"synthetic-envelope",
            json=lambda: {"choices": [{"message": {"content": "ARTICLE_SECRET"},
                                         "finish_reason": "length"}],
                          "usage": {"prompt_tokens": 50, "completion_tokens": 4096,
                                    "total_tokens": 4146, "cost": 0.02}},
        )
        recorder = MetricsJSONClient(OpenRouterJSONClient())
        with self.assertLogs("server.analysis.v2.model_client", level="INFO") as captured:
            with self.assertRaises(ModelResponseError):
                recorder.generate_json(name="claim_expression_A_G", instructions="PROMPT_SECRET",
                                       input_data={}, schema={"type": "object"},
                                       max_output_tokens=4096)
        self.assertEqual(recorder.execution_summary()["task_failure_counts"],
                         {"claim_expression_A_G": 1})
        self.assertEqual(recorder.execution_summary()["provider_usage"]["output_tokens"], 4096)
        self.assertIn('"finish_reason":"length"', captured.output[0])
        self.assertNotIn("ARTICLE_SECRET", str(captured.output))
        recorder.client.close()
