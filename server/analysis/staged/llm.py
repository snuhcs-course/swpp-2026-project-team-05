"""The staged analysis's model calls: OpenRouter chat completions with a strict JSON schema and a reasoning effort.

The key is OPENROUTER_API_KEY; the model is FRAMELESS_STAGED_MODEL (default google/gemini-3.8-flash). Prompts carry
article sentences, so neither prompts nor answers are logged.
"""

from __future__ import annotations

import json
import os
import time

import httpx

URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "google/gemini-3.8-flash"
RETRY_STATUS = {408, 429, 500, 502, 503, 504}


class StagedModelError(RuntimeError):
    """A call that failed after its retries; the message names the status, never the prompt."""


def model_name() -> str:
    return os.getenv("FRAMELESS_STAGED_MODEL", "").strip() or DEFAULT_MODEL


def configured() -> bool:
    return bool(os.getenv("OPENROUTER_API_KEY", "").strip())


def ask(step: str, prompt: str, schema: dict, effort: str, *, timeout: float = 120.0, attempts: int = 3) -> dict:
    """One JSON answer that follows `schema`. Rate limits and server errors are retried with a short backoff."""
    request = {"model": model_name(), "messages": [{"role": "user", "content": prompt}],
               "response_format": {"type": "json_schema", "json_schema": {"name": step, "strict": True, "schema": schema}},
               "provider": {"require_parameters": True}, "reasoning": {"effort": effort}, "max_tokens": 32768}
    headers = {"Authorization": f"Bearer {os.environ.get('OPENROUTER_API_KEY', '').strip()}"}
    for attempt in range(attempts):
        try:
            response = httpx.post(URL, json=request, headers=headers, timeout=timeout)
        except httpx.HTTPError as exc:
            if attempt == attempts - 1:
                raise StagedModelError(f"{step}: {type(exc).__name__}") from None
            time.sleep(2 * 2 ** attempt)
            continue
        if response.status_code == 200:
            break
        if response.status_code not in RETRY_STATUS or attempt == attempts - 1:
            raise StagedModelError(f"{step}: HTTP {response.status_code}")
        time.sleep(2 * 2 ** attempt)
    try:
        choice = response.json()["choices"][0]
        if choice.get("finish_reason") == "length":
            raise StagedModelError(f"{step}: answer cut at the token limit")
        return json.loads(choice["message"]["content"])
    except (KeyError, IndexError, TypeError, ValueError):
        raise StagedModelError(f"{step}: no JSON answer") from None
