"""Regression checks for the model failures seen during article comparison."""

import os
from types import SimpleNamespace
from unittest.mock import patch

from django.test import SimpleTestCase
from google.genai.errors import ServerError

from server.analysis.llm_analysis import compare_issue_passages
from server.analysis.model_client import GeminiJSONClient, ModelUnavailableError


class GeminiClientTests(SimpleTestCase):
    @patch.dict(os.environ, {"GOOGLE_API_KEY": "test-key"})
    @patch("google.genai.Client")
    def test_transient_server_errors_are_retried(self, client_constructor):
        GeminiJSONClient()

        options = client_constructor.call_args.kwargs["http_options"].retry_options
        self.assertEqual(options.attempts, 3)
        self.assertIn(503, options.http_status_codes)

    @patch.dict(os.environ, {"GOOGLE_API_KEY": "test-key"})
    @patch("google.genai.Client")
    def test_exhausted_server_error_is_identified(self, client_constructor):
        client_constructor.return_value.models.generate_content.side_effect = ServerError(
            503,
            {"error": {"code": 503, "message": "unavailable", "status": "UNAVAILABLE"}},
        )

        with self.assertRaises(ModelUnavailableError):
            GeminiJSONClient().generate_json(
                name="test",
                instructions="test",
                input_data={},
                schema={"type": "object"},
            )

    @patch.dict(os.environ, {"GOOGLE_API_KEY": "test-key"})
    @patch("google.genai.Client")
    def test_model_log_has_usage_but_not_article_text(self, client_constructor):
        client_constructor.return_value.models.generate_content.return_value = SimpleNamespace(
            text='{"ok":true}',
            candidates=[SimpleNamespace(finish_reason="STOP")],
            usage_metadata=SimpleNamespace(
                prompt_token_count=12,
                candidates_token_count=5,
                total_token_count=17,
            ),
        )

        with self.assertLogs("server.analysis.model_client", level="INFO") as captured:
            GeminiJSONClient().generate_json(
                name="test",
                instructions="private instructions",
                input_data={"body": "private article text"},
                schema={"type": "object"},
            )

        logs = "\n".join(captured.output)
        self.assertIn('"event":"model_completed"', logs)
        self.assertIn('"total_tokens":17', logs)
        self.assertNotIn("private instructions", logs)
        self.assertNotIn("private article text", logs)


class ComparisonCallTests(SimpleTestCase):
    @patch("server.analysis.llm_analysis.GeminiJSONClient")
    def test_unmatched_issue_does_not_trigger_another_model_call(self, client_constructor):
        client_constructor.return_value.generate_json.return_value = {
            "results": [{"issue_index": 0, "matches": []}]
        }
        sentence = "정부는 병역특례 제도를 바꾼다."
        source = {
            "title": "원문",
            "url": "https://example.com/source",
            "sentences": [{"sentence_index": 0, "text": sentence}],
            "claims": [{
                "claim_index": 0,
                "claim": "정부는 병역특례 제도를 바꾼다",
                "claim_kind": "reported_fact",
                "speaker": "",
                "target": "",
                "evidence": {"sentence_index": 0, "quote": sentence},
            }],
        }
        related = [{"title": "다른 기사", "url": "https://example.com/related", "body": sentence}]

        result = compare_issue_passages(source, related)

        client_constructor.return_value.generate_json.assert_called_once()
        self.assertEqual(len(result["issues"][0]["not_found_in"]), 1)
