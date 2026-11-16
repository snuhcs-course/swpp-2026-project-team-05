"""Versioned account and precomputed-news read API."""

from __future__ import annotations

import json
import os
import re
import threading
from hashlib import sha256
from uuid import uuid4

from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import RequestDataTooBig, ValidationError
from django.db import IntegrityError, transaction
from django.http import HttpResponse, JsonResponse
from django.utils import timezone
from django.utils.cache import patch_vary_headers
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET, require_POST, require_http_methods

from .authentication import (
    authenticate,
    create_account,
    create_session,
    normalize_email,
    normalize_username,
    session_from_request,
    user_from_request,
)
from .analysis.staged import llm as staged_llm
from .analysis.staged.link import LinkAnalysisError, analyze_link
from .analysis.v2.article_fetch import validate_article_url
from .article_search import MAX_QUERY_LENGTH, MIN_QUERY_LENGTH, normalize_search_query
from .models import AnalysisRecord, Article, AuthSession, NewsCategory, Publisher, UserAccount
from .news_queries import (
    InvalidCursor,
    article_analysis_payload,
    article_detail_payload,
    get_article,
    list_articles,
    list_publishers,
)
from .saved_articles import (
    InvalidSavedCursor,
    list_saved_articles,
    remove_saved_article,
    save_article,
)
from .publisher_follows import InvalidPublisherCodes, replace_followed_publishers


MAX_BODY_BYTES = 8192
USERNAME_RE = re.compile(r"^[\w가-힣]{3,30}$", re.UNICODE)


def _request_id() -> str:
    return uuid4().hex[:12]


def _error(code: str, message: str, status: int, *, fields: dict | None = None) -> JsonResponse:
    error = {"code": code, "message": message}
    if fields:
        error["fields"] = fields
    return JsonResponse({"error": error, "request_id": _request_id()}, status=status,
                        json_dumps_params={"ensure_ascii": False})


def _body(request) -> tuple[dict, JsonResponse | None]:
    if request.content_type != "application/json":
        return {}, _error("VALIDATION_ERROR", "JSON 요청만 사용할 수 있습니다.", 415)
    try:
        raw = request.body
    except RequestDataTooBig:
        return {}, _error("VALIDATION_ERROR", "요청 본문이 너무 큽니다.", 413)
    if len(raw) > MAX_BODY_BYTES:
        return {}, _error("VALIDATION_ERROR", "요청 본문이 너무 큽니다.", 413)
    try:
        payload = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError):
        return {}, _error("VALIDATION_ERROR", "올바른 JSON을 보내주세요.", 400)
    if not isinstance(payload, dict):
        return {}, _error("VALIDATION_ERROR", "JSON 객체를 보내주세요.", 400)
    return payload, None


def _user_payload(user: UserAccount) -> dict:
    return {
        "id": str(user.id),
        "email": user.email,
        "username": user.username,
        "is_developer": user.is_developer,
    }


def _session_payload(user: UserAccount) -> dict:
    token, session = create_session(user)
    return {"token": token, "expires_at": session.expires_at, "user": _user_payload(user)}


def _auth(request) -> tuple[UserAccount | None, JsonResponse | None]:
    user = user_from_request(request)
    if user is None:
        return None, _error("AUTH_REQUIRED", "로그인이 필요합니다.", 401)
    return user, None


def _optional_auth(request) -> tuple[UserAccount | None, JsonResponse | None]:
    """Authenticate a supplied bearer token while allowing a missing header."""
    header = request.META.get("HTTP_AUTHORIZATION", "")
    if not header:
        return None, None
    session = session_from_request(request)
    if session is None:
        return None, _error("AUTH_REQUIRED", "로그인이 만료됐어요. 다시 로그인해 주세요.", 401)
    return session.user, None


def _credential_varying(response: JsonResponse) -> JsonResponse:
    patch_vary_headers(response, ("Authorization",))
    return response


@csrf_exempt
@require_POST
def register(request):
    payload, error = _body(request)
    if error:
        return error
    email = normalize_email(str(payload.get("email") or ""))
    username = normalize_username(str(payload.get("username") or ""))
    password = str(payload.get("password") or "")
    confirmation = str(payload.get("password_confirmation") or "")
    fields: dict[str, str] = {}
    if not email:
        fields["email"] = "이메일을 입력해 주세요."
    if not USERNAME_RE.fullmatch(username):
        fields["username"] = "사용자 이름은 한글·영문·숫자·밑줄 3~30자로 입력해 주세요."
    if password != confirmation:
        fields["password_confirmation"] = "비밀번호가 일치하지 않습니다."
    if not password:
        fields["password"] = "비밀번호를 입력해 주세요."
    else:
        try:
            validate_password(password)
        except ValidationError:
            fields["password"] = "비밀번호는 10자 이상이며 너무 흔하거나 숫자로만 이루어지면 안 됩니다."
    if fields:
        return _error("VALIDATION_ERROR", "입력 내용을 확인해 주세요.", 400, fields=fields)
    try:
        with transaction.atomic():
            user = create_account(email=email, username=username, password=password)
    except ValidationError as exc:
        normalized_fields = {
            key: messages[0] if isinstance(messages, list) else str(messages)
            for key, messages in getattr(exc, "message_dict", {"email": exc.messages}).items()
        }
        return _error("VALIDATION_ERROR", "입력 내용을 확인해 주세요.", 400,
                      fields=normalized_fields)
    except IntegrityError:
        fields = {}
        if UserAccount.objects.filter(email__iexact=email).exists():
            fields["email"] = "이미 사용 중인 이메일입니다."
        if UserAccount.objects.filter(username__iexact=username).exists():
            fields["username"] = "이미 사용 중인 사용자 이름입니다."
        return _error("VALIDATION_ERROR", "이미 사용 중인 계정 정보입니다.", 409,
                      fields=fields or None)
    return JsonResponse(_session_payload(user), status=201,
                        json_dumps_params={"ensure_ascii": False})


@csrf_exempt
@require_POST
def login(request):
    payload, error = _body(request)
    if error:
        return error
    identifier = str(payload.get("identifier") or payload.get("username") or "").strip()
    password = str(payload.get("password") or "")
    if not identifier or not password:
        return _error("VALIDATION_ERROR", "이메일 또는 사용자 이름과 비밀번호를 입력해 주세요.", 400)
    user = authenticate(identifier, password)
    if user is None:
        return _error("INVALID_CREDENTIALS", "로그인 정보를 확인해 주세요.", 401)
    return JsonResponse(_session_payload(user), json_dumps_params={"ensure_ascii": False})


@csrf_exempt
@require_POST
def logout(request):
    session = session_from_request(request)
    if session:
        session.delete()
    return HttpResponse(status=204)


@require_GET
def me(request):
    user, error = _auth(request)
    if error:
        return error
    return JsonResponse({"user": _user_payload(user)}, json_dumps_params={"ensure_ascii": False})


@require_GET
def publishers(request):
    user, error = _optional_auth(request)
    if error:
        return error
    return _credential_varying(JsonResponse(
        {"items": list_publishers(user=user)},
        json_dumps_params={"ensure_ascii": False},
    ))


@csrf_exempt
@require_http_methods(["PUT"])
def followed_publishers(request):
    user, error = _auth(request)
    if error:
        return error
    payload, error = _body(request)
    if error:
        return error
    raw_codes = payload.get("publisher_codes")
    if not isinstance(raw_codes, list) or any(
        not isinstance(code, str) or not code.strip() for code in raw_codes
    ):
        return _error(
            "VALIDATION_ERROR",
            "언론사 목록을 확인해 주세요.",
            400,
            fields={"publisher_codes": "언론사 코드 배열을 보내주세요."},
        )
    codes = [code.strip() for code in raw_codes]
    try:
        selected = replace_followed_publishers(user=user, codes=codes)
    except InvalidPublisherCodes:
        return _error(
            "VALIDATION_ERROR",
            "팔로우할 수 없는 언론사가 포함되어 있습니다.",
            400,
            fields={"publisher_codes": "현재 제공 중인 언론사만 선택해 주세요."},
        )
    return JsonResponse({"publisher_codes": selected}, json_dumps_params={"ensure_ascii": False})


@require_GET
def articles(request):
    user, error = _optional_auth(request)
    if error:
        return error
    category = request.GET.get("category") or None
    if category and category not in NewsCategory.values:
        return _error("VALIDATION_ERROR", "지원하지 않는 기사 분야입니다.", 400,
                      fields={"category": "정치 또는 사회를 선택해 주세요."})
    scope = request.GET.get("scope") or "ALL"
    if scope not in {"ALL", "FOLLOWING"}:
        return _error(
            "VALIDATION_ERROR",
            "지원하지 않는 피드 범위입니다.",
            400,
            fields={"scope": "ALL 또는 FOLLOWING을 선택해 주세요."},
        )
    if scope == "FOLLOWING" and user is None:
        return _error("AUTH_REQUIRED", "팔로잉 기사는 로그인 후 볼 수 있어요.", 401)
    publisher_codes = list(dict.fromkeys(request.GET.getlist("publisher")))
    if publisher_codes:
        known = set(Publisher.objects.filter(code__in=publisher_codes, is_active=True)
                    .values_list("code", flat=True))
        if set(publisher_codes) != known:
            return _error("VALIDATION_ERROR", "지원하지 않는 언론사가 포함되어 있습니다.", 400)
    query = normalize_search_query(request.GET.get("q"))
    if query and not MIN_QUERY_LENGTH <= len(query) <= MAX_QUERY_LENGTH:
        return _error(
            "VALIDATION_ERROR",
            "검색어를 확인해 주세요.",
            400,
            fields={"q": f"검색어는 {MIN_QUERY_LENGTH}자 이상 {MAX_QUERY_LENGTH}자 이하여야 합니다."},
        )
    try:
        limit = int(request.GET.get("limit", "20"))
    except ValueError:
        return _error("VALIDATION_ERROR", "limit은 정수여야 합니다.", 400)
    if not 1 <= limit <= 50:
        return _error("VALIDATION_ERROR", "limit은 1에서 50 사이여야 합니다.", 400)
    try:
        payload = list_articles(
            user=user,
            category=category,
            publisher_codes=publisher_codes,
            query=query or None,
            cursor=request.GET.get("cursor") or None,
            limit=limit,
            scope=scope,
        )
    except InvalidCursor:
        return _error("INVALID_CURSOR", "기사 목록을 처음부터 다시 불러와 주세요.", 400)
    return _credential_varying(JsonResponse(
        payload, json_dumps_params={"ensure_ascii": False},
    ))


@require_GET
def article_detail(request, article_id):
    user, error = _optional_auth(request)
    if error:
        return error
    article = get_article(article_id, user=user)
    if article is None:
        return _error("NOT_FOUND", "기사를 찾을 수 없습니다.", 404)
    return _credential_varying(JsonResponse(
        article_detail_payload(article), json_dumps_params={"ensure_ascii": False},
    ))


@require_GET
def article_analysis(request, article_id):
    _, error = _optional_auth(request)
    if error:
        return error
    article = get_article(article_id)
    if article is None:
        return _error("NOT_FOUND", "기사를 찾을 수 없습니다.", 404)
    return _credential_varying(JsonResponse(
        article_analysis_payload(article), json_dumps_params={"ensure_ascii": False},
    ))


@require_GET
def saved_articles(request):
    user, error = _auth(request)
    if error:
        return error
    try:
        limit = int(request.GET.get("limit", "20"))
    except ValueError:
        return _error("VALIDATION_ERROR", "limit은 정수여야 합니다.", 400)
    if not 1 <= limit <= 50:
        return _error("VALIDATION_ERROR", "limit은 1에서 50 사이여야 합니다.", 400)
    try:
        payload = list_saved_articles(
            user=user,
            cursor=request.GET.get("cursor") or None,
            limit=limit,
        )
    except InvalidSavedCursor:
        return _error("INVALID_CURSOR", "저장 목록을 처음부터 다시 불러와 주세요.", 400)
    return JsonResponse(payload, json_dumps_params={"ensure_ascii": False})


_link_analysis_slot = threading.BoundedSemaphore(1)
LINK_RESULT_VERSION = "link-v6"


def _link_limits() -> tuple[int, int]:
    """(per client, all non-developer clients) new link analyses per day; each is a paid model run."""
    def read(name: str, default: int) -> int:
        try:
            return max(0, int(os.getenv(name, "").strip() or default))
        except ValueError:
            return default
    return read("FRAMELESS_LINK_ANALYSIS_PER_CLIENT_DAILY", 3), read("FRAMELESS_LINK_ANALYSIS_DAILY", 30)


def _link_client_key(request) -> str:
    """A hash of the caller's address (the first X-Forwarded-For entry behind Vercel); the address is not stored."""
    forwarded = request.META.get("HTTP_X_FORWARDED_FOR", "").split(",")[0].strip()
    address = forwarded or request.META.get("REMOTE_ADDR", "") or "unknown"
    return "link:" + sha256(address.encode("utf-8")).hexdigest()[:40]


def _link_usage_today(owner_key: str | None = None) -> int:
    start = timezone.localtime().replace(hour=0, minute=0, second=0, microsecond=0)
    rows = AnalysisRecord.objects.filter(result_version=LINK_RESULT_VERSION, created_at__gte=start)
    return rows.filter(owner_key=owner_key).count() if owner_key else rows.count()


def _record_link_use(owner_key: str, user: UserAccount | None, url: str, outcome: str) -> None:
    """One row per paid run, never tied to an account, so it stays out of every reader's history. Each attempt
    gets its own hash so a retried link counts again."""
    AnalysisRecord.objects.create(
        user=None, owner_key=owner_key, source_url=url, source_title="",
        source_url_hash=sha256(f"{url}\n{uuid4().hex}".encode("utf-8")).hexdigest(),
        result={"outcome": outcome, "signed_in": user is not None}, execution_status=outcome,
        result_version=LINK_RESULT_VERSION,
    )


@csrf_exempt
@require_POST
def link_analyses(request):
    """Analyse a pasted article link (staged A-G analysis) and answer the stored source article's id.

    Anyone may call it, signed in or not. Each new analysis searches other outlets and calls a paid model, so a
    caller that is not a developer account has a daily limit per client address and all of them share a daily
    total; developer accounts have no limit. A link that is already stored answers at once with status 'existing'
    and never counts. One analysis runs at a time; it takes one to two minutes."""
    user, error = _optional_auth(request)
    if error:
        return error
    developer = bool(user and user.is_developer)
    payload, error = _body(request)
    if error:
        return error
    url = payload.get("url")
    if not isinstance(url, str) or not url.strip() or len(url) > 2048:
        return _error("VALIDATION_ERROR", "기사 링크를 입력해 주세요.", 400, fields={"url": "기사 링크를 입력해 주세요."})
    try:
        url = validate_article_url(url.strip())
    except ValueError as exc:
        return _error("VALIDATION_ERROR", str(exc), 400, fields={"url": str(exc)})
    client_key = None if developer else _link_client_key(request)
    if not developer:
        stored = Article.objects.filter(canonical_url=url).first()
        if stored is not None:
            return JsonResponse({"article_id": str(stored.id), "status": "existing",
                                 "highlight_count": stored.findings.count()}, json_dumps_params={"ensure_ascii": False})
        per_client, total = _link_limits()
        if _link_usage_today(client_key) >= per_client or _link_usage_today() >= total:
            return _error("DAILY_LIMIT", "오늘 링크 분석 횟수를 모두 썼어요. 내일 다시 시도해 주세요.", 429)
    if not _link_analysis_slot.acquire(blocking=False):
        response = _error("BUSY", "다른 기사를 분석하고 있어요. 잠시 후 다시 시도해 주세요.", 429)
        response["Retry-After"] = "30"
        return response
    try:
        result = analyze_link(url)
        if client_key and result.get("status") == "created":
            _record_link_use(client_key, user, url, "created")
    except LinkAnalysisError as exc:
        # A failed run that reached discovery or the model still cost a run; an unreadable link or a server
        # without a model key did not.
        spent = exc.code != "source_unavailable" and (exc.code != "analysis_unavailable" or staged_llm.configured())
        if client_key and spent:
            _record_link_use(client_key, user, url, "failed")
        response = _error(exc.code.upper(), str(exc), 503 if exc.retryable else 422)
        if exc.retryable:
            response["Retry-After"] = "30"
        return response
    finally:
        _link_analysis_slot.release()
    return JsonResponse(result, json_dumps_params={"ensure_ascii": False})


@csrf_exempt
@require_http_methods(["PUT", "DELETE"])
def saved_article(request, article_id):
    user, error = _auth(request)
    if error:
        return error
    if request.method == "DELETE":
        remove_saved_article(user=user, article_id=article_id)
        return HttpResponse(status=204)
    row = save_article(user=user, article_id=article_id)
    if row is None:
        return _error("NOT_FOUND", "저장할 수 있는 기사를 찾을 수 없습니다.", 404)
    return JsonResponse({
        "article_id": str(row.article_id),
        "is_saved": True,
        "saved_at": row.saved_at,
    })
