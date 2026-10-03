"""Small JSON adapter around the existing article comparison pipeline."""

import json
import logging
import os
import secrets
import threading

from django.core.exceptions import RequestDataTooBig
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET, require_POST

from article_fetch import validate_article_url
from article_search import analyze_related_articles


logger = logging.getLogger(__name__)
MAX_BODY_BYTES = 8192
_analysis_slot = threading.BoundedSemaphore(1)


def _error(code: str, message: str, status: int) -> JsonResponse:
    return JsonResponse(
        {"error": {"code": code, "message": message}},
        status=status,
        json_dumps_params={"ensure_ascii": False},
    )


@require_GET
def health(request):
    return JsonResponse({"status": "ok"})


@csrf_exempt  # This endpoint uses a bearer token and no browser cookie session.
@require_POST
def analyze(request):
    token = os.getenv("API_ACCESS_TOKEN", "")
    if not token:
        logger.error("API_ACCESS_TOKEN is not configured")
        return _error("service_unavailable", "서버 인증 설정이 완료되지 않았습니다.", 503)

    authorization = request.headers.get("Authorization", "")
    prefix = "Bearer "
    provided = authorization[len(prefix):] if authorization.startswith(prefix) else ""
    if not provided or not secrets.compare_digest(provided, token):
        return _error("unauthorized", "유효한 인증 토큰이 필요합니다.", 401)

    if request.content_type != "application/json":
        return _error("invalid_content_type", "Content-Type은 application/json이어야 합니다.", 415)
    try:
        body = request.body
    except RequestDataTooBig:
        return _error("request_too_large", "요청 본문이 너무 큽니다.", 413)
    if len(body) > MAX_BODY_BYTES:
        return _error("request_too_large", "요청 본문이 너무 큽니다.", 413)
    try:
        payload = json.loads(body)
    except (UnicodeDecodeError, json.JSONDecodeError):
        return _error("invalid_json", "올바른 JSON 본문을 보내주세요.", 400)
    if not isinstance(payload, dict):
        return _error("invalid_request", "JSON 객체를 보내주세요.", 400)

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

    if not _analysis_slot.acquire(blocking=False):
        response = _error("busy", "다른 기사를 분석 중입니다. 잠시 후 다시 시도해 주세요.", 429)
        response["Retry-After"] = "30"
        return response
    try:
        try:
            result = analyze_related_articles(url.strip(), max_related=max_related)
        except Exception:
            logger.exception("Article analysis failed")
            return _error("analysis_failed", "기사를 분석하지 못했습니다. 잠시 후 다시 시도해 주세요.", 502)
    finally:
        _analysis_slot.release()
    return JsonResponse(result, json_dumps_params={"ensure_ascii": False})
