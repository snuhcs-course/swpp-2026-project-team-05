"""Search metadata, choose candidates, then fetch/verify/refill and analyze."""

from __future__ import annotations

import ast
import json
import logging
import math
import os
import tempfile
import re
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from contextvars import ContextVar
from copy import deepcopy
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from time import monotonic, perf_counter
from typing import Any, Callable

from ..diagnostics import analysis_id, log_event, submit_with_context
from .article_fetch import validate_article_url
from .article_input import prepare_article
from .article_search import _google_grounding_enabled, search_candidate_metadata
from .comparison_selection import select_verified_comparisons
from .display_selection import select_display_article_ids
from .llm_analysis import analyze_source_article, rank_comparison_candidates
from .model_client import (GeminiJSONClient, OpenRouterJSONClient, MetricsJSONClient, SnapshotJSONClient,
                          KNOWN_TASKS,
                          canonical_json, get_generation_settings, get_openrouter_generation_settings,
                          get_openrouter_api_key, get_missing_configuration)
from .rubric_analysis import analyze_selected_articles
from .result_output import (OperationError, make_error_record, add_result_error,
                           make_failure_result, write_json_atomic, resolve_stage_errors)


logger = logging.getLogger(__name__)


PIPELINE_VERSION = "metadata-select-verify-reader-v6"
PROMPT_VERSION = "grounded-expression-pairs-v3"
RUBRIC_VERSION = "0.2.1"
# AI-assisted integration: use a writable cache path in read-only deployments.
DEFAULT_CACHE_DIR = Path(os.getenv("FRAMELESS_ANALYSIS_CACHE_DIR") or (Path(tempfile.gettempdir()) / "frameless-analysis-v2"))
# AI-assisted runtime policy: leave a margin under Vercel's 300-second function limit.
DEFAULT_UNCACHED_BUDGET_SECONDS = 240.0
# Source discovery can inspect extra bodies, but the rubric still needs time
# within Vercel's hard function limit. Stop optional verification early.
RUBRIC_RESERVE_SECONDS = 75.0
# Leave room for candidate ranking, body verification, and the reader after an
# outage-only Google search. The search call itself is capped at 30 seconds.
GOOGLE_FALLBACK_POST_SEARCH_RESERVE_SECONDS = 30.0
# Search coverage changes as new articles are published, even when the source
# article body is unchanged. Never treat a local snapshot as timeless I1 data.
DEFAULT_CACHE_MAX_AGE_SECONDS = 300.0
LOG_ERROR_CODES = frozenset({
    "ANALYSIS_RESPONSE_INVALID", "ARTICLE_BODY_IMPLAUSIBLE", "ARTICLE_BODY_NOT_FOUND", "ARTICLE_DNS_FAILED",
    "ARTICLE_FETCH_FAILED", "ARTICLE_HTTP_ERROR", "ARTICLE_RESPONSE_INTERRUPTED",
    "ARTICLE_SIZE_LIMIT", "ARTICLE_TIMEOUT", "ARTICLE_UNSAFE_ADDRESS", "ARTICLE_UNSAFE_PROXY",
    "ARTICLE_UNSUPPORTED_CONTENT", "CACHE_WRITE_FAILED", "CANDIDATE_URL_MISSING",
    "CONFIGURATION_ERROR", "GEMINI_CONFIGURATION_MISSING", "MODEL_CONFIGURATION_MISSING",
    "GOOGLE_SEARCH_CONFIGURATION_MISSING",
    "GOOGLE_SEARCH_RESPONSE_INVALID", "GOOGLE_SEARCH_TIMEOUT", "GOOGLE_SEARCH_CONNECTION_FAILED",
    "GOOGLE_SEARCH_REDIRECT_BLOCKED", "GOOGLE_SEARCH_HTTP_ERROR",
    "GOOGLE_SEARCH_CONFIGURATION_INVALID", "INPUT_ENCODING_INVALID",
    "INPUT_FILE_NOT_FOUND", "INPUT_FILE_UNREADABLE", "INPUT_JSON_INVALID", "INPUT_RESULT_INVALID",
    "INVALID_INPUT", "MODEL_HTTP_ERROR", "MODEL_RATE_LIMITED", "MODEL_TIMEOUT",
    "MODEL_UNAVAILABLE", "NAVER_CONFIGURATION_MISSING", "OUTPUT_WRITE_FAILED",
    "OPENROUTER_SEARCH_CONFIGURATION_MISSING", "OPENROUTER_SEARCH_CONFIGURATION_INVALID",
    "OPENROUTER_SEARCH_RESPONSE_INVALID", "OPENROUTER_SEARCH_NOT_EXECUTED",
    "OPENROUTER_SEARCH_CITATIONS_MISSING",
    "OPENROUTER_SEARCH_TIMEOUT", "OPENROUTER_SEARCH_CONNECTION_FAILED",
    "OPENROUTER_SEARCH_REDIRECT_BLOCKED", "OPENROUTER_SEARCH_HTTP_ERROR",
    "PROCESSING_FAILED", "RUBRIC_ARTICLE_UNVERIFIED", "RUBRIC_EXPRESSION_UNVERIFIED",
    "SEARCH_FAILED", "SEARCH_HTTP_ERROR", "SEARCH_REDIRECT_BLOCKED", "SEARCH_RESPONSE_INVALID",
    "SEARCH_TIMEOUT",
})
LOG_STAGE_NAMES = frozenset({
    "source_fetch", "source_analysis", "search", "candidate_selection",
    "candidate_collection", "discovery", "reader_analysis", "rubric_analysis",
})
_stage_elapsed_ms: ContextVar[dict[str, int] | None] = ContextVar("stage_elapsed_ms", default=None)
LOG_TASK_NAMES = KNOWN_TASKS | {"unknown", "source_analysis"} | {
    "repair_" + name for name in KNOWN_TASKS}


def _ensure_budget(deadline_at: float | None) -> None:
    if deadline_at is not None and monotonic() >= deadline_at:
        raise TimeoutError("분석 실행의 소프트 마감시간을 초과했습니다.")


def _default_model_settings() -> dict:
    if get_openrouter_api_key():
        return {"provider": "openrouter", **get_openrouter_generation_settings()}
    return {"provider": "gemini", **get_generation_settings()}


def _create_model_client(settings: dict):
    args = {name: settings[name] for name in ("model", "temperature", "seed", "task_models")}
    return (OpenRouterJSONClient(**args) if settings.get("provider") == "openrouter"
            else GeminiJSONClient(**args))


@contextmanager
def _timed_stage(stage: str, deadline_at: float | None = None):
    """Log fixed stage metadata; a deadline cannot interrupt an in-flight fetch."""
    started = perf_counter()
    log_event(logger, "stage_started", stage=stage)
    try:
        _ensure_budget(deadline_at)
        yield
    except Exception as exc:
        error = make_error_record(exc, stage=stage)
        duration_ms = round((perf_counter() - started) * 1000)
        elapsed = _stage_elapsed_ms.get()
        if elapsed is not None and stage in LOG_STAGE_NAMES:
            elapsed[stage] = elapsed.get(stage, 0) + duration_ms
        log_event(logger, "stage_failed", level=logging.WARNING, stage=stage,
                  duration_ms=duration_ms,
                  error_type=type(exc).__name__, error_code=_safe_error_code(error["code"]))
        raise
    else:
        # A nested operation may catch its own error and return partial data.
        duration_ms = round((perf_counter() - started) * 1000)
        elapsed = _stage_elapsed_ms.get()
        if elapsed is not None and stage in LOG_STAGE_NAMES:
            elapsed[stage] = elapsed.get(stage, 0) + duration_ms
        log_event(logger, "stage_returned", stage=stage,
                  duration_ms=duration_ms)


@contextmanager
def _scoped_model_deadline(client: Any, deadline_at: float | None):
    """Reserve time for stage 5 even when a stage-4 model call runs long."""
    if deadline_at is None or not isinstance(client, MetricsJSONClient):
        yield client
        return
    with client.scoped_deadline(deadline_at):
        # Verification launches worker threads. Their deadline must be scoped
        # too, while an overlapping Reader source worker keeps the full budget.
        scoped = (_ScopedMetricsJSONClient(client, client.deadline_at)
                  if not isinstance(client, SnapshotJSONClient) else client)
        yield scoped


class _ScopedMetricsJSONClient(MetricsJSONClient):
    """Apply one stage deadline in every verification worker, sharing metrics."""

    def __init__(self, parent: MetricsJSONClient, deadline_at: float):
        super().__init__(parent.client, deadline_at=deadline_at)
        self._parent = parent

    def generate_json(self, **request: Any) -> dict:
        with self._parent.scoped_deadline(self.deadline_at):
            return self._parent.generate_json(**request)

    def check_budget(self) -> None:
        with self._parent.scoped_deadline(self.deadline_at):
            self._parent.check_budget()


def _safe_error_code(value: object) -> str:
    return value if isinstance(value, str) and value in LOG_ERROR_CODES else "PROCESSING_FAILED"


def _log_stage_result(stage: str, result: dict) -> None:
    """Report internally caught failures without recording their text or URLs."""
    codes = sorted({_safe_error_code(error.get("code")) for error in result.get("errors", [])
                    if isinstance(error, dict) and (stage == "discovery" or error.get("stage") == stage)})
    if codes or stage in {"discovery", "rubric_analysis"}:
        execution_status = result.get("execution_status")
        safe_status = (execution_status if isinstance(execution_status, str)
                       and execution_status in {"success", "partial", "failed"} else "unknown")
        log_event(logger, "stage_result", level=logging.INFO if safe_status == "success" and not codes else logging.WARNING,
                  stage=stage,
                  execution_status=safe_status,
                  error_codes=codes)


def _safe_task_counts(value: object) -> dict[str, int]:
    if not isinstance(value, dict):
        return {}
    return dict(sorted((name, count) for name, count in value.items()
                       if isinstance(name, str) and name in LOG_TASK_NAMES
                       and type(count) is int and count >= 0))


def _completion_summary(result: dict, stage_elapsed: dict[str, int],
                        execution: dict | None, duration_ms: int) -> dict:
    """Summarize existing counters without retaining article or model content."""
    status = result.get("execution_status")
    if status not in {"success", "partial", "failed"}:
        status = "unknown"
    reasons = set()
    for error in result.get("errors", []):
        if isinstance(error, dict):
            reasons.add(_safe_error_code(error.get("code")))
    display = result.get("display_selection")
    if isinstance(display, dict) and display.get("truncated") is True:
        reasons.add("DISPLAY_SELECTION_TRUNCATED")
    reader = result.get("reader_analysis")
    if isinstance(reader, dict):
        if reader.get("article_failures"):
            reasons.add("READER_ALIGNMENT_FAILED")
        if reader.get("i1_status") in {"partial", "withheld"}:
            reasons.add("READER_I1_INCOMPLETE")
        expression = reader.get("expression_review")
        if isinstance(expression, dict) and expression.get("status") in {"partial", "failed"}:
            reasons.add("READER_EXPRESSION_INCOMPLETE")
    selection = result.get("selection")
    verification = {}
    if isinstance(selection, dict):
        attempted = selection.get("verification_llm_calls")
        if type(attempted) is int and attempted >= 0:
            verification["attempted_call_count"] = attempted
        detached = selection.get("verification_detached_calls_at_cutoff")
        if type(detached) is int and detached >= 0:
            verification["detached_call_count_at_cutoff"] = detached
        rounds = selection.get("verification_rounds")
        if isinstance(rounds, list):
            deferred = set()
            for row in rounds:
                ids = row.get("deferred_ids") if isinstance(row, dict) else None
                if isinstance(ids, list):
                    deferred.update(candidate_id for candidate_id in ids
                                    if isinstance(candidate_id, str))
            verification["deferred_candidate_count"] = len(deferred)
            if deferred:
                reasons.add("VERIFICATION_DEFERRED")
    if status == "partial" and not reasons:
        reasons.add("UNCLASSIFIED_PARTIAL")
    summary = {
        "execution_status": status,
        "duration_ms": duration_ms,
        # `discovery` contains its child stages; these numbers are not additive.
        "stage_elapsed_ms": {stage: stage_elapsed[stage] for stage in sorted(stage_elapsed)
                             if stage in LOG_STAGE_NAMES and type(stage_elapsed[stage]) is int
                             and stage_elapsed[stage] >= 0},
        "partial_reason_codes": sorted(reasons),
    }
    if verification:
        summary["verification"] = verification
    if isinstance(execution, dict):
        succeeded = _safe_task_counts(execution.get("task_counts"))
        failed = _safe_task_counts(execution.get("task_failure_counts"))
        names = succeeded.keys() | failed.keys()
        summary["model_task_attempt_counts"] = {
            name: succeeded.get(name, 0) + failed.get(name, 0) for name in sorted(names)}
        summary["model_task_elapsed_ms_sum"] = _safe_task_counts(execution.get("task_duration_ms"))
    return summary


def _digest(value: Any) -> str:
    return sha256(canonical_json(value).encode("utf-8")).hexdigest()


def _analysis_versions() -> dict:
    """Invalidate saved results when analysis logic changes, without hashing API keys."""
    directory = Path(__file__).resolve().parent
    logic = {}
    for name in ("pipeline.py", "display_selection.py", "llm_analysis.py", "rubric_analysis.py", "comparison_selection.py",
                 "article_input.py", "article_fetch.py", "article_search.py", "model_client.py", "result_output.py"):
        source = (directory / name).read_text(encoding="utf-8-sig")
        if name == "model_client.py":
            tree = ast.parse(source)
            tree.body = [node for node in tree.body if not (
                isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id in {
                    "GEMINI_API_KEY", "NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET"} for t in node.targets))]
            source = ast.unparse(tree)
        logic[name] = sha256(source.encode("utf-8")).hexdigest()
    reader_path = directory.parent / "reader_analysis.py"
    if reader_path.is_file():
        logic["reader_analysis.py"] = sha256(reader_path.read_bytes()).hexdigest()
    return {"pipeline": PIPELINE_VERSION, "prompt": PROMPT_VERSION, "rubric": RUBRIC_VERSION,
            "logic_sha256": _digest(logic)}


def _atomic_json(path: Path, value: dict) -> None:
    write_json_atomic(path, value)


def _read_snapshot(path: Path) -> dict:
    bundle = json.loads(path.read_text(encoding="utf-8-sig"))
    payload = {key: bundle[key] for key in ("settings", "versions", "requests", "result")}
    if bundle.get("format_version") != 1 or bundle.get("payload_sha256") != _digest(payload):
        raise ValueError("저장된 분석 스냅샷의 형식이나 검증 값이 올바르지 않습니다.")
    if bundle["result"].get("completed_stage") != 5:
        raise ValueError("완료된 5단계 스냅샷이 필요합니다.")
    return bundle


def _load_cached_analysis(directory: Path, key: str) -> dict | None:
    try:
        index = json.loads((directory / (key + ".latest.json")).read_text(encoding="utf-8-sig"))
        filename = index["snapshot_file"]
        if not re.fullmatch(re.escape(key) + r"\.[0-9a-f]{64}\.json", filename):
            return None
        bundle = _read_snapshot(directory / filename)
        if bundle.get("request_key") != key:
            return None
        created = datetime.fromisoformat(bundle["result"]["reproducibility"]["created_at"])
        age = (datetime.now(timezone.utc) - created).total_seconds()
        return bundle if 0 <= age <= DEFAULT_CACHE_MAX_AGE_SECONDS else None
    except (OSError, ValueError, KeyError, TypeError):
        return None


def _validate_pipeline_options(options: dict) -> None:
    for name, lower, upper in (
        ("max_claims", 1, 5), ("max_candidates", 1, 100), ("max_per_claim", 1, 3),
        ("display_per_query", 1, 100), ("event_baseline_limit", 0, 20), ("verification_batch_size", 1, 5),
    ):
        value = options[name]
        if type(value) is not int or not lower <= value <= upper:
            raise ValueError(f"{name}은 {lower}~{upper}의 정수여야 합니다.")


def _initial_result(url: str) -> dict:
    result = make_failure_result({}, url=url if isinstance(url, str) else "")
    result.update(status="pending", execution_status="success", errors=[])
    return result


def _record_failure(result: dict, exc: Exception, stage: str) -> dict:
    # A rubric or snapshot failure cannot erase already verified discovery.
    # Android can still show the grounded comparison with a partial notice.
    comparison_status = result.get("comparison_status")
    result["status"] = stage + "_failed"
    if not (stage in {"rubric_analysis", "reader_analysis", "cache_save"}
            and result.get("completed_stage", 0) >= 4
            and comparison_status in {"available", "no_matches", "not_requested"}):
        result["comparison_status"] = "unavailable"
    error = make_error_record(exc, stage=stage)
    # AI-assisted diagnostics: log only fixed stage/code and exception class, never response text.
    logger.warning("pipeline_stage_failed analysis_id=%s stage=%s error_type=%s error_code=%s",
                   analysis_id.get(), stage, type(exc).__name__, _safe_error_code(error["code"]))
    return add_result_error(result, error,
                            fatal=not isinstance(result.get("source_analysis"), dict))


def _finish_discovery(result: dict) -> dict:
    collection, selection = result["search"], result["selection"]
    for error in collection.get("errors", []) + collection.get("fetch_failures", []):
        if not isinstance(error, dict) or not {"stage", "code", "message", "retryable"} <= error.keys():
            context = {key: error[key] for key in ("candidate_id", "url", "query") if isinstance(error, dict) and key in error}
            error = make_error_record(OperationError("이전 실행에서 관련 기사 수집·검색에 실패했습니다.",
                                      code="ARTICLE_FETCH_FAILED" if "candidate_id" in context else "SEARCH_FAILED"),
                                      stage="candidate_fetch" if "candidate_id" in context else "search", **context)
        add_result_error(result, error)
    for query in collection.get("queries", []):
        if query.get("status") == "search_failed" and not any(
            e.get("stage") == "search" and e.get("query") == query.get("query") for e in result["errors"]
        ):
            add_result_error(result, make_error_record(OperationError("이전 실행에서 해당 검색어의 검색에 실패했습니다.",
                             code="SEARCH_FAILED"), stage="search", query=query.get("query", "")))
    result["execution_status"] = "partial" if (result["errors"] or
        result.get("display_selection", {}).get("truncated")) else "success"
    if not result["source_analysis"]["claims"]:
        result["status"], result["comparison_status"] = "no_claims", "not_requested"
    elif collection.get("search_status") == "failed":
        result["status"], result["comparison_status"] = "search_failed", "unavailable"
    elif not collection["candidates"]:
        result["status"] = "no_candidates"
        result["comparison_status"] = "unavailable" if result["errors"] else "no_matches"
    elif not selection["same_event_ids"]:
        result["status"] = "no_same_event"
        result["comparison_status"] = "unavailable" if result["errors"] else "no_matches"
    else:
        result["status"] = "ready"
        result["comparison_status"] = ("available" if any(r["articles"] for r in selection["claim_comparisons"])
                                       else "unavailable" if result["errors"] else "no_matches")
    return result


def run_discovery_pipeline(url: str, *, max_claims: int = 5, max_candidates: int = 20,
                           max_per_claim: int = 3, display_per_query: int = 10,
                           event_baseline_limit: int = 5, verification_batch_size: int = 3,
                           client: Any = None, progress: Callable[[str], None] | None = None,
                           _prepared_source: dict | None = None,
                           _deadline_at: float | None = None,
                           _after_candidate_search: Callable[[dict, dict], None] | None = None) -> dict:
    """Return discovery or available data with structured operation errors."""
    result, stage, owned = _initial_result(url), "input_validation", False
    notify = progress or (lambda message: None)

    try:
        url = validate_article_url(url)
        _validate_pipeline_options({"max_claims": max_claims, "max_candidates": max_candidates,
                                   "max_per_claim": max_per_claim, "display_per_query": display_per_query,
                                   "event_baseline_limit": event_baseline_limit,
                                   "verification_batch_size": verification_batch_size})
        stage = "source_fetch"
        if _prepared_source is None:
            notify("1/4 입력 기사 수집·문장 번호 부여")
            with _timed_stage(stage, _deadline_at):
                source = prepare_article(url)
        else:
            source = _prepared_source
        result.update(source_article=source, completed_stage=1)
        stage = "configuration"
        if client is None:
            if "MODEL_API_KEY" in get_missing_configuration():
                raise OperationError("모델 API 키를 설정해 주세요.", code="MODEL_CONFIGURATION_MISSING")
            client = _create_model_client(_default_model_settings())
            owned = True
        stage = "source_analysis"
        notify("2/4 LLM 호출 1: 핵심 사건·주장·검색어 생성")
        with _timed_stage(stage, _deadline_at):
            analysis = analyze_source_article(source, max_claims=max_claims, client=client)
        result.update(source_analysis=analysis, completed_stage=2)
        metadata_search = {"queries": [], "metadata_candidates": [], "discovered_count": 0,
                           "errors": [], "search_status": "not_run"}
        stage = "search"
        if analysis["claims"]:
            notify("3/4 네이버 검색·URL 중복 제거 (제목·설명만 수집)")
            with _timed_stage(stage, _deadline_at):
                metadata_search = search_candidate_metadata(source, analysis, max_candidates=max_candidates,
                                                             display_per_query=display_per_query,
                                                             google_fallback_deadline_at=(
                                                                 _deadline_at - RUBRIC_RESERVE_SECONDS
                                                                 - GOOGLE_FALLBACK_POST_SEARCH_RESERVE_SECONDS
                                                                 if _deadline_at is not None else None))
        result["search"].update(metadata_search)
        result["completed_stage"] = 3
        for error in metadata_search.get("errors", []):
            add_result_error(result, error)
        if metadata_search["metadata_candidates"] and _after_candidate_search is not None:
            _after_candidate_search(source, analysis)
        stage = "candidate_selection"
        if metadata_search["metadata_candidates"]:
            notify("4/4 LLM 후보 분류·Python 선택: 주장 관련성·강조점·표현 다양성 (본문 검증 전)")
        with _timed_stage(stage, _deadline_at):
            ranking = rank_comparison_candidates(source, analysis, metadata_search["metadata_candidates"],
                                                  max_per_claim=max_per_claim, client=client)
        result["candidate_ranking"] = ranking
        stage = "candidate_collection"
        with _timed_stage(stage, _deadline_at):
            remaining = max(0.0, _deadline_at - monotonic()) if _deadline_at is not None else 0.0
            rubric_reserve = min(RUBRIC_RESERVE_SECONDS, remaining * 0.5)
            with _scoped_model_deadline(client, _deadline_at - rubric_reserve
                                        if _deadline_at is not None else None) as verification_client:
                verified = select_verified_comparisons(source, analysis, metadata_search, ranking,
                                                       max_per_claim=max_per_claim, event_baseline_limit=event_baseline_limit,
                                                       verification_batch_size=verification_batch_size,
                                                       client=verification_client,
                                                       progress=notify,
                                                       should_stop=(lambda: monotonic() >= _deadline_at - rubric_reserve)
                                                       if _deadline_at is not None else None)
        collection, selection = verified["search"], verified["selection"]
        warnings = list(selection.get("warnings", []))
        if any(query["status"] == "search_failed" for query in collection["queries"]):
            warnings.append("일부 검색어의 검색에 실패했습니다. queries의 status를 확인하세요.")
        if collection["fetch_failures"]:
            warnings.append("일부 후보 본문을 수집하지 못했습니다. fetch_failures를 확인하세요.")
        if analysis["claims"] and not selection["event_baseline_ids"]:
            warnings.append("사건 검색에서 확보한 동일 사건 기사가 없습니다. 포함·누락 비교 자료가 불충분합니다.")
        if any(row.get("validation_warnings") for row in selection["assessments"]):
            warnings.append("일부 후보의 판정 근거가 부족해 미확인으로 처리했습니다. assessments의 validation_warnings를 확인하세요.")
        if any(row.get("validation_warnings") for row in ranking["claim_candidates"]):
            warnings.append("일부 후보의 분류·강조점이 미확인입니다. 미확인 강조점은 다양성 가점에서 제외하고 후보는 보존합니다. candidate_ranking의 validation_warnings를 확인하세요.")
        result.update(completed_stage=4, search=collection, selection=selection,
                      pipeline_strategy="metadata_select_fetch_verify_refill", warnings=warnings)
        return _finish_discovery(result)
    except Exception as exc:
        return _record_failure(result, exc, stage)
    finally:
        if owned:
            try:
                client.close()
            except Exception as exc:
                add_result_error(result, make_error_record(exc, stage="client_close"))


def complete_rubric_analysis(discovery: dict, *, client: Any = None,
                            progress: Callable[[str], None] | None = None) -> dict:
    """Resume a stage-4 result and keep discovery if a later operation fails."""
    if not isinstance(discovery, dict):
        return make_failure_result(make_error_record(
            OperationError("완료된 4단계 기사 분석 JSON이 필요합니다.", code="INPUT_RESULT_INVALID"), stage="input_validation"))
    result = deepcopy(discovery)
    if (not isinstance(result.get("errors", []), list) or not isinstance(result.get("warnings", []), list)
            or any(not isinstance(e, dict) or not {"stage", "code", "message", "retryable"} <= e.keys()
                   for e in result.get("errors", []))):
        return make_failure_result(make_error_record(
            OperationError("입력 분석 JSON의 오류·안내 목록 형식이 올바르지 않습니다.", code="INPUT_RESULT_INVALID"),
            stage="input_validation"))
    result.setdefault("errors", [])
    result.setdefault("warnings", [])
    result.setdefault("execution_status", "partial" if result["errors"] else "success")
    if result["execution_status"] == "failed":
        return result
    valid = all(isinstance(result.get(key), dict) for key in ("source_article", "source_analysis", "search", "selection"))
    if valid:
        analysis = result["source_analysis"]
        valid = isinstance(analysis.get("claims"), list) and isinstance(analysis.get("core_event"), str)
        valid = valid and all(isinstance(c, dict) and all(key in c for key in
                              ("claim_id", "claim", "claim_kind", "speaker", "evidence"))
                              and isinstance(c["evidence"], dict) and isinstance(c["evidence"].get("quote"), str)
                              for c in analysis.get("claims", []))
        valid = valid and all(isinstance(result["selection"].get(key), list) for key in
                              ("claim_comparisons", "same_event_ids", "event_baseline_ids", "assessments"))
        valid = valid and isinstance(result["search"].get("candidates"), list)
    if not valid:
        return make_failure_result(make_error_record(
            OperationError("완료된 4단계 기사 분석 JSON이 필요합니다.", code="INPUT_RESULT_INVALID"), stage="input_validation"))
    try:
        rubric = analyze_selected_articles(discovery, client=client, progress=progress)
        result.update(completed_stage=5, rubric_analysis=rubric,
                      warnings=list(result["warnings"]) + rubric["warnings"])
        resolve_stage_errors(result, "rubric_analysis")
        # AI-assisted resilience: one invalid article must not erase grounded rubric rows.
        for failure in rubric.get("article_annotation_failures", []):
            add_result_error(result, make_error_record(
                OperationError("관련 기사 루브릭 근거를 검증하지 못했습니다.",
                               code="RUBRIC_ARTICLE_UNVERIFIED",
                               retryable=bool(failure.get("retryable"))),
                stage="rubric_analysis", candidate_id=failure["article_id"]))
        for failure in rubric.get("expression_failures", []):
            add_result_error(result, make_error_record(
                OperationError("주장별 A~G 표현 근거를 검증하지 못했습니다.",
                               code="RUBRIC_EXPRESSION_UNVERIFIED",
                               retryable=bool(failure.get("retryable"))),
                stage="rubric_analysis", claim_id=failure["claim_id"]))
        return _finish_discovery(result)
    except Exception as exc:
        result["rubric_analysis"] = None
        return _record_failure(result, exc, "rubric_analysis")


def complete_reader_analysis(discovery: dict, *, client: Any = None,
                             progress: Callable[[str], None] | None = None,
                             _source_unit_future: Any = None) -> dict:
    """Analyze full displayed articles while preserving verified discovery on failure."""
    result = deepcopy(discovery)
    if result.get("completed_stage") != 4:
        return result
    try:
        from ..reader_analysis import analyze_reader_content

        selected = result.get("display_selection", {}).get("selected_article_ids")
        if not isinstance(selected, list):
            selected = select_display_article_ids(
                result["selection"], 3, result["search"]["candidates"])
        source_units = _source_unit_future.result() if _source_unit_future is not None else None
        reader = analyze_reader_content(result, selected, client=client, progress=progress,
                                        precomputed_source_units=source_units)
        result.update(completed_stage=5, reader_analysis=reader,
                      warnings=list(result.get("warnings", [])) + list(reader.get("warnings", [])))
        if (reader.get("article_failures") or reader.get("i1_status") not in {"complete", "no_candidates"}
                or reader.get("expression_review", {}).get("status") in {"failed", "partial"}):
            result["execution_status"] = "partial"
        return result
    except Exception as exc:
        result["reader_analysis"] = None
        return _record_failure(result, exc, "reader_analysis")


def _limit_discovery_for_display(discovery: dict, limit: int) -> dict:
    """Bound expensive rubric calls to the same verified articles Android shows."""
    selected = select_display_article_ids(discovery["selection"], limit,
                                          discovery["search"]["candidates"])
    keep = set(selected)
    selection = discovery["selection"]
    original = {article["candidate_id"] for row in selection["claim_comparisons"]
                for article in row["articles"]} | set(selection["event_baseline_ids"])
    omitted = sorted(original - keep)
    for row in selection["claim_comparisons"]:
        row["articles"] = [article for article in row["articles"] if article["candidate_id"] in keep]
    selection["event_baseline_ids"] = [key for key in selection["event_baseline_ids"] if key in keep]
    uncovered = [row["claim_id"] for row in selection["claim_comparisons"]
                 if not row["articles"] and row["claim_id"] not in selection.get("exhausted_claim_ids", [])]
    discovery["display_selection"] = {
        "article_limit": limit, "selected_article_ids": selected,
        "omitted_article_ids": omitted, "claims_without_displayed_comparison": uncovered,
        "truncated": bool(omitted),
    }
    if omitted:
        discovery.setdefault("warnings", []).append(
            f"화면에 표시할 수 있는 비교 기사 {limit}개를 우선 분석했습니다. "
            "나머지 검증 기사와 그에 연결된 주장 비교는 표시·루브릭 분석에서 제외했습니다.")
        if uncovered:
            discovery["warnings"].append(
                "표시 기사 제한으로 비교 근거가 사라진 입력 주장: " + ", ".join(uncovered))
    return discovery


def run_analysis_pipeline(url: str, *, max_claims: int = 5, max_candidates: int = 20,
                          max_per_claim: int = 3, display_per_query: int = 10,
                          event_baseline_limit: int = 5, verification_batch_size: int = 3,
                          client: Any = None, progress: Callable[[str], None] | None = None,
                          use_cache: bool = True, force_refresh: bool = False,
                          cache_dir: str | Path | None = None,
                          max_rubric_articles: int | None = None,
                          execution_budget_seconds: float | None = None,
                          analysis_profile: str = "legacy") -> dict:
    """Return structured results; uncached calls use a soft 240-second deadline."""
    started = perf_counter()
    stage_elapsed: dict[str, int] = {}
    timing_token = _stage_elapsed_ms.set(stage_elapsed)
    result, stage, owned = _initial_result(url), "input_validation", False
    source_executor, source_future = None, None
    recorder, execution = None, None
    options = {"max_claims": max_claims, "max_candidates": max_candidates, "max_per_claim": max_per_claim,
               "display_per_query": display_per_query, "event_baseline_limit": event_baseline_limit,
               "verification_batch_size": verification_batch_size}
    notify = progress or (lambda _: None)
    try:
        url = validate_article_url(url)
        _validate_pipeline_options(options)
        if max_rubric_articles is not None and (type(max_rubric_articles) is not int or
                                                not 1 <= max_rubric_articles <= 3):
            raise ValueError("max_rubric_articles must be an integer between 1 and 3")
        if analysis_profile not in {"legacy", "reader"}:
            raise ValueError("analysis_profile must be legacy or reader")
        if type(use_cache) is not bool or type(force_refresh) is not bool:
            raise ValueError("use_cache·force_refresh must be bool")
        if execution_budget_seconds is not None and (
            type(execution_budget_seconds) not in (int, float)
            or not math.isfinite(execution_budget_seconds)
            or execution_budget_seconds <= 0
        ):
            raise ValueError("execution_budget_seconds must be a positive finite number")
        budget_seconds = (DEFAULT_UNCACHED_BUDGET_SECONDS if not use_cache else None)
        if execution_budget_seconds is not None:
            budget_seconds = float(execution_budget_seconds)
        deadline_at = monotonic() + budget_seconds if budget_seconds is not None else None
        stage = "source_fetch"
        notify("1/4 입력 기사 수집·문장 번호 부여")
        with _timed_stage(stage, deadline_at):
            source = prepare_article(url)
        result.update(source_article=source, completed_stage=1)
        stage = "configuration"
        if client is None:
            settings = _default_model_settings()
        else:
            provider = getattr(client, "provider", "injected")
            settings_function = (get_openrouter_generation_settings if provider == "openrouter"
                                 else get_generation_settings)
            settings = {"provider": provider, **settings_function(
                getattr(client, "model", "injected:" + type(client).__name__),
                temperature=getattr(client, "temperature", None), seed=getattr(client, "seed", None),
                task_models=getattr(client, "task_models", {}))}
        versions = _analysis_versions()
        source_hash = _digest(source)
        cache_options = {**options, "max_rubric_articles": max_rubric_articles,
                         "analysis_profile": analysis_profile}
        google_grounding = _google_grounding_enabled()
        grounded_model = (os.getenv("GOOGLE_GROUNDED_SEARCH_MODEL", "").strip() or "gemini-3.8-flash") if google_grounding else None
        cache_options["google_grounded_search"] = {"enabled": google_grounding,
                                                   "model": grounded_model if grounded_model and re.fullmatch(
                                                       r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}", grounded_model) else None}
        if execution_budget_seconds is not None:
            cache_options["execution_budget_seconds"] = float(execution_budget_seconds)
        key = _digest({"source_sha256": source_hash, "settings": settings, "versions": versions, "options": cache_options})
        directory = Path(cache_dir) if cache_dir is not None else DEFAULT_CACHE_DIR
        if use_cache and not force_refresh:
            bundle = _load_cached_analysis(directory, key)
            if bundle is not None:
                result = deepcopy(bundle["result"])
                result["reproducibility"]["cache_hit"] = True
                result["reproducibility"]["execution"] = {
                    "model_calls": 0, "task_counts": {}, "sdk_retries_included": False,
                    "attempted_calls": 0, "failed_calls": 0, "model_duration_ms": 0,
                }
                notify("저장된 동일 입력·설정의 분석 재사용 (LLM·검색 호출 없음)")
                return result
        if client is None:
            if "MODEL_API_KEY" in get_missing_configuration():
                raise OperationError("모델 API 키를 설정해 주세요.", code="MODEL_CONFIGURATION_MISSING")
            client = _create_model_client(settings)
            owned = True
        recorder = (SnapshotJSONClient(client, settings, deadline_at=deadline_at) if use_cache
                    else MetricsJSONClient(client, deadline_at=deadline_at))
        after_candidate_search = None
        if analysis_profile == "reader" and not use_cache and isinstance(client, OpenRouterJSONClient):
            # The source ledger needs the source plan, but no comparison result.
            # Overlap it with candidate body verification. Snapshot runs stay
            # serial so their exact request order remains reproducible.
            source_executor = ThreadPoolExecutor(max_workers=1)

            def after_candidate_search(prepared_source: dict, source_analysis: dict) -> None:
                nonlocal source_future
                from ..reader_analysis import _extract_source_units
                source_future = submit_with_context(
                    source_executor, _extract_source_units, prepared_source, source_analysis, recorder)

        stage = "discovery"
        with _timed_stage(stage, deadline_at):
            discovery = run_discovery_pipeline(url, **options, client=recorder, progress=progress,
                                               _prepared_source=source, _deadline_at=deadline_at,
                                               _after_candidate_search=after_candidate_search)
        for failed_stage in ("source_analysis", "search", "candidate_selection",
                             "candidate_fetch", "candidate_collection"):
            _log_stage_result(failed_stage, discovery)
        _log_stage_result(stage, discovery)
        result = discovery
        # Reader annotations cannot be displayed when discovery has no usable
        # comparison. Keep the search failure intact and avoid another model call.
        reader_unavailable = (analysis_profile == "reader"
                              and discovery.get("comparison_status") == "unavailable")
        if discovery["completed_stage"] == 4 and not reader_unavailable:
            if max_rubric_articles is not None:
                discovery = _limit_discovery_for_display(discovery, max_rubric_articles)
            stage = "reader_analysis" if analysis_profile == "reader" else "rubric_analysis"
            with _timed_stage(stage, deadline_at):
                result = (complete_reader_analysis(discovery, client=recorder, progress=progress,
                                                   _source_unit_future=source_future)
                          if analysis_profile == "reader" else
                          complete_rubric_analysis(discovery, client=recorder, progress=progress))
            _log_stage_result(stage, result)
        stage = "cache_save"
        execution = recorder.execution_summary()
        # Detached speculative verification may finish after this snapshot;
        # expose its count so provider_usage is not read as a final total.
        execution["verification_detached_calls_at_cutoff"] = (
            (result.get("selection") or {}).get("verification_detached_calls_at_cutoff", 0))
        log_event(logger, "model_execution", **execution)
        if use_cache:
            snapshot_id = _digest({"settings": settings, "versions": versions,
                                   "requests": recorder.records, "result": result})
        else:
            # AI-assisted privacy boundary: identify the result without retaining model transcripts.
            snapshot_id = _digest({"settings": settings, "versions": versions,
                                   "result": result})
        snapshot_path = directory / (key + "." + snapshot_id + ".json")
        result["reproducibility"] = {
            "settings": settings, "versions": versions, "source_sha256": source_hash,
            "candidate_snapshot_sha256": _digest(result["search"]), "request_key": key,
            "snapshot_id": snapshot_id, "snapshot_path": str(snapshot_path.resolve()) if use_cache else None,
            "created_at": datetime.now(timezone.utc).isoformat(), "cache_hit": False,
            "original_execution": execution, "execution": dict(execution),
        }
        if not use_cache:
            result["reproducibility"]["snapshot_id_basis"] = "result_without_model_transcript"
        if use_cache:
            partial = result["execution_status"] != "success"
            if partial:
                result["reproducibility"]["cache_reuse_disabled_reason"] = "partial_operation_failure"
            payload = {"settings": settings, "versions": versions, "requests": recorder.records, "result": result}
            try:
                _atomic_json(snapshot_path, {"format_version": 1, "request_key": key, "snapshot_id": snapshot_id,
                                             **payload, "payload_sha256": _digest(payload)})
                if not partial:
                    _atomic_json(directory / (key + ".latest.json"), {"snapshot_file": snapshot_path.name})
            except (OSError, ValueError, TypeError) as exc:
                add_result_error(result, make_error_record(exc, stage="cache_save", path=str(snapshot_path)))
                result["warnings"].append("분석은 완료했지만 로컬 스냅샷을 저장하지 못했습니다.")
                result["reproducibility"]["snapshot_path"] = None
        return result
    except Exception as exc:
        return _record_failure(result, exc, stage)
    finally:
        if source_executor is not None:
            source_executor.shutdown(wait=True, cancel_futures=True)
        if owned:
            try:
                client.close()
            except Exception as exc:
                add_result_error(result, make_error_record(exc, stage="client_close"))
        try:
            metrics = execution if execution is not None else (
                recorder.execution_summary() if recorder is not None else None)
            log_event(logger, "analysis_completed", **_completion_summary(
                result, stage_elapsed, metrics, round((perf_counter() - started) * 1000)))
        except Exception as exc:
            # Diagnostics must never replace the analysis response.
            logger.warning("analysis_summary_failed analysis_id=%s error_type=%s",
                           analysis_id.get(), type(exc).__name__)
        finally:
            _stage_elapsed_ms.reset(timing_token)


def evaluate_analysis_snapshot(snapshot_path: str | Path, *, client: Any = None,
                               temperature: float | None = None, seed: int | None = None,
                               progress: Callable[[str], None] | None = None) -> dict:
    """Repeat each saved exact model input without fetching/searching or using result cache."""
    from jsonschema import ValidationError, validate
    bundle = _read_snapshot(Path(snapshot_path))
    original = bundle["settings"]
    provider = original.get("provider", "gemini")
    settings_function = (get_openrouter_generation_settings if provider == "openrouter"
                         else get_generation_settings)
    settings = {"provider": provider, **settings_function(original["model"],
        temperature=original["temperature"] if temperature is None else temperature,
        seed=original["seed"] if seed is None else seed,
        task_models=original.get("task_models", {}))}
    owned, notify = client is None, progress or (lambda _: None)
    if client is not None and any(getattr(client, name, settings[name]) != settings[name]
                                  for name in ("model", "temperature", "seed")):
        raise ValueError("평가 클라이언트의 모델·temperature·seed가 지정한 평가 설정과 다릅니다.")
    if client is not None and hasattr(client, "task_models") and client.task_models != settings["task_models"]:
        raise ValueError("평가 클라이언트의 작업별 모델 설정이 지정한 평가 설정과 다릅니다.")
    if client is not None and hasattr(client, "provider") and client.provider != provider:
        raise ValueError("평가 클라이언트의 모델 제공자가 지정한 평가 설정과 다릅니다.")
    if owned:
        client = _create_model_client(settings)
    rows = []
    try:
        for index, record in enumerate(bundle["requests"], 1):
            notify(f"고정 입력 평가 {index}/{len(bundle['requests'])}: {record['name']}")
            response = client.generate_json(**deepcopy(record["request"]))
            try:
                validate(response, record["request"]["schema"])
                schema_valid = True
            except ValidationError:
                schema_valid = False
            rows.append({"request_id": record["request_id"], "name": record["name"],
                         "same_response": response == record["response"], "schema_valid": schema_valid,
                         "response": response})
    finally:
        if owned:
            client.close()
    return {"snapshot_id": bundle["snapshot_id"], "original_settings": original,
            "evaluation_settings": settings, "model_calls": len(rows), "requests": rows,
            "same_response_count": sum(row["same_response"] for row in rows),
            "scope": "identical_saved_model_inputs; schema_and_response_equality_only; no_semantic_accuracy_score"}
