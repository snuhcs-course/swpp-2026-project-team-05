"""Short-lived PostgreSQL cache for API results, without model transcripts."""

from __future__ import annotations

import json
import logging
import os
from datetime import timedelta
from functools import lru_cache
from hashlib import sha256
from pathlib import Path

from django.conf import settings
from django.db import DatabaseError
from django.utils import timezone

from .analysis.diagnostics import log_event


logger = logging.getLogger("server.views")
FULL_RESULT_TTL_SECONDS = 300
PARTIAL_RESULT_TTL_SECONDS = 60


@lru_cache(maxsize=1)
def _code_fingerprint() -> str:
    """Invalidate cached output after any server analysis or adapter code change."""
    server_dir = Path(__file__).resolve().parent
    paths = sorted((server_dir / "analysis").rglob("*.py"))
    paths.extend(server_dir / name for name in ("views.py", "result_cache.py"))
    digest = sha256()
    for path in paths:
        digest.update(str(path.relative_to(server_dir)).encode("utf-8"))
        digest.update(path.read_bytes())
    return digest.hexdigest()


def cache_key(source_url: str, max_related: int) -> str | None:
    if not settings.DATABASE_URL:
        return None
    try:
        from .analysis.v2.pipeline import _default_model_settings

        payload = {
            "url": source_url.strip(),
            "max_related": max_related,
            "code": _code_fingerprint(),
            "model": _default_model_settings(),
            "verifier_reasoning_effort": os.getenv("OPENROUTER_VERIFIER_REASONING_EFFORT", "medium"),
            "google_grounded_search": os.getenv("ENABLE_GOOGLE_GROUNDED_SEARCH", ""),
            "google_grounded_search_model": os.getenv("GOOGLE_GROUNDED_SEARCH_MODEL", ""),
        }
        return sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False,
                                 separators=(",", ":")).encode("utf-8")).hexdigest()
    except (OSError, TypeError, ValueError) as exc:
        log_event(logger, "result_cache_key_failed", level=logging.WARNING,
                  error_type=type(exc).__name__)
        return None


def load_result(key: str | None) -> dict | None:
    if not key:
        return None
    from .models import CachedAnalysis

    try:
        result = (CachedAnalysis.objects.filter(key=key, expires_at__gt=timezone.now())
                  .values_list("result", flat=True).first())
    except DatabaseError as exc:
        log_event(logger, "result_cache_read_failed", level=logging.WARNING,
                  error_type=type(exc).__name__)
        return None
    if isinstance(result, dict):
        log_event(logger, "result_cache_hit")
        return result
    log_event(logger, "result_cache_miss")
    return None


def save_result(key: str | None, source_url: str, result: dict) -> None:
    if not key:
        return
    status = result.get("analysis_status")
    if not isinstance(status, dict) or status.get("comparison_status") not in {"available", "no_matches"}:
        return
    execution_status = status.get("execution_status")
    ttl = (FULL_RESULT_TTL_SECONDS if execution_status == "success" else
           PARTIAL_RESULT_TTL_SECONDS if execution_status == "partial" else None)
    if ttl is None:
        return

    from .models import CachedAnalysis

    try:
        now = timezone.now()
        CachedAnalysis.objects.update_or_create(
            key=key,
            defaults={"source_url": source_url, "result": result,
                      "execution_status": execution_status,
                      "expires_at": now + timedelta(seconds=ttl)},
        )
        CachedAnalysis.objects.filter(expires_at__lte=now).delete()
        log_event(logger, "result_cache_saved", ttl_seconds=ttl)
    except (DatabaseError, TypeError, ValueError) as exc:
        log_event(logger, "result_cache_write_failed", level=logging.WARNING,
                  error_type=type(exc).__name__)
