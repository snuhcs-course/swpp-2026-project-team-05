"""Versioned account and precomputed-news read API."""

from __future__ import annotations

import json
import re
from hashlib import sha256
from uuid import uuid4

from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import RequestDataTooBig, ValidationError
from django.db import IntegrityError, transaction
from django.http import HttpResponse, JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET, require_POST

from .authentication import (
    authenticate,
    create_account,
    create_session,
    normalize_email,
    normalize_username,
    session_from_request,
    user_from_request,
)
from .models import AuthSession, NewsCategory, Publisher, UserAccount
from .news_queries import (
    InvalidCursor,
    article_analysis_payload,
    article_detail_payload,
    get_article,
    list_articles,
    list_publishers,
)


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
    _, error = _auth(request)
    if error:
        return error
    return JsonResponse({"items": list_publishers()}, json_dumps_params={"ensure_ascii": False})


@require_GET
def articles(request):
    _, error = _auth(request)
    if error:
        return error
    category = request.GET.get("category") or None
    if category and category not in NewsCategory.values:
        return _error("VALIDATION_ERROR", "지원하지 않는 기사 분야입니다.", 400,
                      fields={"category": "정치 또는 사회를 선택해 주세요."})
    publisher_codes = list(dict.fromkeys(request.GET.getlist("publisher")))
    if publisher_codes:
        known = set(Publisher.objects.filter(code__in=publisher_codes, is_active=True)
                    .values_list("code", flat=True))
        if set(publisher_codes) != known:
            return _error("VALIDATION_ERROR", "지원하지 않는 언론사가 포함되어 있습니다.", 400)
    try:
        limit = int(request.GET.get("limit", "20"))
    except ValueError:
        return _error("VALIDATION_ERROR", "limit은 정수여야 합니다.", 400)
    if not 1 <= limit <= 50:
        return _error("VALIDATION_ERROR", "limit은 1에서 50 사이여야 합니다.", 400)
    try:
        payload = list_articles(
            category=category,
            publisher_codes=publisher_codes,
            cursor=request.GET.get("cursor") or None,
            limit=limit,
        )
    except InvalidCursor:
        return _error("INVALID_CURSOR", "기사 목록을 처음부터 다시 불러와 주세요.", 400)
    return JsonResponse(payload, json_dumps_params={"ensure_ascii": False})


@require_GET
def article_detail(request, article_id):
    _, error = _auth(request)
    if error:
        return error
    article = get_article(article_id)
    if article is None:
        return _error("NOT_FOUND", "기사를 찾을 수 없습니다.", 404)
    return JsonResponse(article_detail_payload(article), json_dumps_params={"ensure_ascii": False})


@require_GET
def article_analysis(request, article_id):
    _, error = _auth(request)
    if error:
        return error
    article = get_article(article_id)
    if article is None:
        return _error("NOT_FOUND", "기사를 찾을 수 없습니다.", 404)
    return JsonResponse(article_analysis_payload(article), json_dumps_params={"ensure_ascii": False})
