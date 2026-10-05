"""Request-scoped diagnostics without article text, prompts, or credentials."""

from __future__ import annotations

import json
import logging
from contextlib import contextmanager
from contextvars import ContextVar
from time import perf_counter
from typing import Iterator


analysis_id: ContextVar[str] = ContextVar("analysis_id", default="-")
_stage: ContextVar[str] = ContextVar("analysis_stage", default="-")


def log_event(logger: logging.Logger, event: str, *, level: int = logging.INFO, **fields: object) -> None:
    payload = {"analysis_id": analysis_id.get(), "event": event}
    if _stage.get() != "-":
        payload["stage"] = _stage.get()
    payload.update(fields)
    logger.log(level, "analysis %s", json.dumps(payload, ensure_ascii=False, separators=(",", ":")))


@contextmanager
def analysis_stage(logger: logging.Logger, name: str, **fields: object) -> Iterator[None]:
    token = _stage.set(name)
    started = perf_counter()
    log_event(logger, "stage_started", **fields)
    try:
        yield
    except Exception as exc:
        log_event(
            logger,
            "stage_failed",
            level=logging.ERROR,
            duration_ms=round((perf_counter() - started) * 1000),
            error_type=type(exc).__name__,
            **fields,
        )
        logger.exception("Analysis stage failed: analysis_id=%s stage=%s", analysis_id.get(), name)
        raise
    else:
        log_event(
            logger,
            "stage_completed",
            duration_ms=round((perf_counter() - started) * 1000),
            **fields,
        )
    finally:
        _stage.reset(token)
