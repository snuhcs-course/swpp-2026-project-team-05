"""Small JSON adapter around the existing article comparison pipeline."""

import json
import logging
import os
import threading
from time import perf_counter
from urllib.parse import urlsplit
from uuid import uuid4

from django.conf import settings
from django.core.exceptions import RequestDataTooBig
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET, require_POST

from .analysis.article_fetch import validate_article_url
from .analysis.article_search import analyze_related_articles
from .analysis.diagnostics import analysis_id, log_event
from .analysis.model_client import ModelUnavailableError, get_gemini_api_key
from .analysis.v2_adapter import PipelineContractError, adapt_v2_result
from .analysis_history import (HistoryUnavailableError, MAX_HISTORY_ITEMS,
                               claim_legacy_history, list_analysis_records, load_analysis_record,
                               owner_key_for_request, save_analysis_record)
from .authentication import authenticate, create_session, user_from_request
from .result_cache import cache_key, load_result, save_result


logger = logging.getLogger(__name__)
MAX_BODY_BYTES = 8192
_analysis_slot = threading.BoundedSemaphore(1)


class PipelineResultError(RuntimeError):
    """A v2 result that cannot support a comparison response."""

    def __init__(self, code: str, message: str, *, retryable: bool = False):
        super().__init__(message)
        self.code = code
        self.retryable = retryable


def analyze_related_articles_v2(source_url: str, max_related: int = 3) -> dict:
    """Run the full-text Reader pipeline and preserve the Android API contract."""
    # AI-assisted Iteration 2 integration; the v2 output is checked by adapter tests.
    from .analysis.v2.pipeline import run_analysis_pipeline

    raw = run_analysis_pipeline(
        source_url,
        max_claims=3,
        # Two provider results plus two independent web results are enough to
        # fill the three Reader slots while preserving time for A-G and I1.
        max_candidates=4,
        max_per_claim=min(max_related, 2),
        event_baseline_limit=2,
        # Full article bodies can exhaust the model output budget when grouped.
        # Verify each Reader candidate separately before selecting comparisons.
        verification_batch_size=1,
        max_rubric_articles=max_related,
        analysis_profile="reader",
        use_cache=False,  # Serverless function files are not persistent storage.
    )
    if not isinstance(raw, dict):
        raise PipelineResultError("analysis_failed", "분석 결과를 읽지 못했어요.")
    errors = raw.get("errors") if isinstance(raw.get("errors"), list) else []
    # Ignore cleanup and earlier recoverable discovery errors when explaining
    # a fatal source failure. Those records can be appended after the cause.
    terminal_error = next((row for row in reversed(errors) if isinstance(row, dict) and
                           row.get("stage") not in {"client_close", "cache_save", "search",
                                                    "candidate_fetch", "candidate_verification"}), {})
    if not terminal_error:
        terminal_error = next((row for row in reversed(errors) if isinstance(row, dict)), {})
    if raw.get("execution_status") == "failed" or not isinstance(raw.get("source_analysis"), dict):
        raise PipelineResultError(
            str(terminal_error.get("code") or "analysis_failed").lower(),
            str(terminal_error.get("message") or "기사를 분석하지 못했어요. 잠시 후 다시 시도해 주세요."),
            retryable=bool(terminal_error.get("retryable")),
        )
    if raw.get("comparison_status") == "unavailable":
        raise PipelineResultError(
            "comparison_unavailable",
            "비교 기사를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
            retryable=any(bool(row.get("retryable")) for row in errors if isinstance(row, dict)),
        )
    try:
        return adapt_v2_result(raw, max_related=max_related, requested_url=source_url)
    except PipelineContractError as exc:
        log_event(logger, "adapter_failed", level=logging.ERROR,
                  error_code="INVALID_ANALYSIS_RESULT", error_type=type(exc).__name__)
        raise PipelineResultError("invalid_analysis_result", "분석 결과를 표시하지 못했어요. 잠시 후 다시 시도해 주세요.") from None


def _missing_api_keys() -> list[str]:
    missing = []
    pipeline_name = os.getenv("FRAMELESS_PIPELINE", "v2").strip().lower()
    direct_gemini = bool(get_gemini_api_key())
    openrouter = bool(os.getenv("OPENROUTER_API_KEY", "").strip())
    if not direct_gemini and (pipeline_name == "v1" or not openrouter):
        missing.append("GOOGLE_API_KEY or GEMINI_API_KEY" if pipeline_name == "v1" else
                       "OPENROUTER_API_KEY or GOOGLE_API_KEY or GEMINI_API_KEY")
    return missing


def _error(code: str, message: str, status: int) -> JsonResponse:
    log_event(logger, "request_error", level=logging.WARNING, error_code=code, status_code=status)
    return JsonResponse(
        {"error": {"code": code, "message": message}},
        status=status,
        json_dumps_params={"ensure_ascii": False},
    )


def _json_body(request) -> tuple[dict, JsonResponse | None]:
    if request.content_type != "application/json":
        return {}, _error("invalid_content_type", "Content-Type은 application/json이어야 합니다.", 415)
    try:
        body = request.body
    except RequestDataTooBig:
        return {}, _error("request_too_large", "요청 본문이 너무 큽니다.", 413)
    if len(body) > MAX_BODY_BYTES:
        return {}, _error("request_too_large", "요청 본문이 너무 큽니다.", 413)
    try:
        payload = json.loads(body)
    except (UnicodeDecodeError, json.JSONDecodeError):
        return {}, _error("invalid_json", "올바른 JSON 본문을 보내주세요.", 400)
    if not isinstance(payload, dict):
        return {}, _error("invalid_request", "JSON 객체를 보내주세요.", 400)
    return payload, None


@require_GET
def health(request):
    missing = _missing_api_keys()
    return JsonResponse({
        "status": "ok",
        "analysis_ready": not missing,
        "missing_configuration": missing,
    })


def _history_user_or_error(request):
    user = user_from_request(request)
    if user:
        return user, None
    return None, _error("authentication_required", "로그인 후 분석 기록을 사용할 수 있습니다.", 401)


@csrf_exempt
@require_POST
def login(request):
    body, error = _json_body(request)
    if error:
        return error
    username = str(body.get("username") or "").strip()
    password = str(body.get("password") or "")
    if not username or not password:
        return _error("invalid_credentials", "아이디와 비밀번호를 입력해 주세요.", 400)
    user = authenticate(username, password)
    if user is None:
        return _error("invalid_credentials", "아이디 또는 비밀번호가 올바르지 않습니다.", 401)
    token, session = create_session(user)
    claimed = claim_legacy_history(user, owner_key_for_request(request))
    return JsonResponse({"token": token, "expires_at": session.expires_at,
                         "user": {"id": str(user.id), "username": user.username,
                                  "is_developer": user.is_developer},
                         "claimed_records": claimed}, json_dumps_params={"ensure_ascii": False})


@require_GET
def me(request):
    user, error = _history_user_or_error(request)
    if error:
        return error
    return JsonResponse({"id": str(user.id), "username": user.username,
                         "is_developer": user.is_developer})


@require_GET
def analyses(request):
    user, error = _history_user_or_error(request)
    if error:
        return error
    raw_limit = request.GET.get("limit", "20")
    try:
        limit = int(raw_limit)
    except ValueError:
        return _error("invalid_limit", "limit은 정수여야 합니다.", 400)
    if not 1 <= limit <= MAX_HISTORY_ITEMS:
        return _error("invalid_limit", f"limit은 1에서 {MAX_HISTORY_ITEMS} 사이여야 합니다.", 400)
    try:
        rows = list_analysis_records(user, limit)
    except HistoryUnavailableError:
        return _error("history_unavailable", "분석 기록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", 503)
    return JsonResponse({"analyses": rows}, json_dumps_params={"ensure_ascii": False})


@require_GET
def analysis_detail(request, record_id):
    user, error = _history_user_or_error(request)
    if error:
        return error
    try:
        row = load_analysis_record(user, record_id)
    except HistoryUnavailableError:
        return _error("history_unavailable", "분석 기록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", 503)
    if row is None:
        return _error("analysis_not_found", "분석 기록을 찾을 수 없습니다.", 404)
    return JsonResponse(row["result"], json_dumps_params={"ensure_ascii": False})


@csrf_exempt  # The JSON API does not use cookie-based authentication.
@require_POST
def analyze(request):
    request_id = uuid4().hex[:12]
    token = analysis_id.set(request_id)
    started = perf_counter()
    log_event(logger, "request_started")
    try:
        response = _analyze_request(request)
        response["X-Analysis-ID"] = request_id
        log_event(
            logger,
            "request_completed",
            status_code=response.status_code,
            duration_ms=round((perf_counter() - started) * 1000),
        )
        return response
    except Exception as exc:
        # Python tracebacks can contain model or article text in exception
        # arguments. Keep the correlation ID and exception class only.
        log_event(logger, "request_unhandled", level=logging.ERROR,
                  error_code="REQUEST_UNHANDLED", error_type=type(exc).__name__)
        response = _error("analysis_failed", "기사를 분석하지 못했습니다. 잠시 후 다시 시도해 주세요.", 502)
        response["X-Analysis-ID"] = request_id
        log_event(logger, "request_completed", status_code=502,
                  duration_ms=round((perf_counter() - started) * 1000))
        return response
    finally:
        analysis_id.reset(token)


def _analyze_request(request):
    payload, error = _json_body(request)
    if error:
        return error

    url = payload.get("url")
    if not isinstance(url, str) or len(url) > 2048:
        return _error("invalid_url", "기사 URL을 문자열로 보내주세요.", 400)
    try:
        validate_article_url(url)
    except ValueError as exc:
        return _error("invalid_url", str(exc), 400)

    max_related = payload.get("max_related", 3)
    if type(max_related) is not int or not 1 <= max_related <= 3:
        return _error("invalid_max_related", "max_related는 1에서 3 사이의 정수여야 합니다.", 400)

    missing = _missing_api_keys()
    if missing:
        return _error(
            "missing_configuration",
            "서버 환경변수 설정이 필요합니다: " + ", ".join(missing),
            503,
        )

    pipeline_name = os.getenv("FRAMELESS_PIPELINE", "v2").strip().lower()
    if pipeline_name not in {"v1", "v2"}:
        return _error("invalid_pipeline_config", "서버 분석 설정을 확인해 주세요.", 503)
    if pipeline_name == "v1" and (settings.IS_PRODUCTION or os.getenv("FRAMELESS_ALLOW_LEGACY_V1") != "1"):
        # v1 is kept for explicit local Iteration 1 replay. Its fetch path does
        # not pin public DNS results, so a default or public deployment fails closed.
        return _error("invalid_pipeline_config", "이 서버에서는 v1 분석을 사용할 수 없습니다.", 503)
    log_event(logger, "request_validated", article_host=urlsplit(url).hostname,
              max_related=max_related, pipeline=pipeline_name)
    source_url = url.strip()
    history_user = user_from_request(request)
    result_cache_key = cache_key(source_url, max_related) if pipeline_name == "v2" else None
    cached = load_result(result_cache_key)
    if cached is not None:
        record_id = save_analysis_record(history_user, source_url, cached)
        response = JsonResponse(cached, json_dumps_params={"ensure_ascii": False})
        response["X-Analysis-Cache"] = "HIT"
        if record_id:
            response["X-Analysis-Record-ID"] = record_id
        return response
    if not _analysis_slot.acquire(blocking=False):
        response = _error("busy", "다른 기사를 분석 중입니다. 잠시 후 다시 시도해 주세요.", 429)
        response["Retry-After"] = "30"
        return response
    try:
        try:
            analysis_function = analyze_related_articles_v2 if pipeline_name == "v2" else analyze_related_articles
            result = analysis_function(source_url, max_related=max_related)
        except PipelineResultError as exc:
            response = _error(exc.code, str(exc), 503 if exc.retryable else 502)
            if exc.retryable:
                response["Retry-After"] = "30"
            return response
        except ModelUnavailableError:
            response = _error(
                "analysis_unavailable",
                "분석 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.",
                503,
            )
            response["Retry-After"] = "30"
            return response
        except Exception as exc:
            log_event(logger, "pipeline_unhandled", level=logging.ERROR,
                      error_code="ANALYSIS_FAILED", error_type=type(exc).__name__)
            return _error("analysis_failed", "기사를 분석하지 못했습니다. 잠시 후 다시 시도해 주세요.", 502)
    finally:
        _analysis_slot.release()
    save_result(result_cache_key, source_url, result)
    record_id = save_analysis_record(history_user, source_url, result)
    response = JsonResponse(result, json_dumps_params={"ensure_ascii": False})
    if result_cache_key:
        response["X-Analysis-Cache"] = "MISS"
    if record_id:
        response["X-Analysis-Record-ID"] = record_id
    return response
