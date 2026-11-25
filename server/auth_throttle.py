"""Shared authentication admission limits, backed by short-lived PostgreSQL rows."""

from datetime import timedelta
from functools import wraps
from hashlib import sha256
import hmac
import ipaddress
import math
from uuid import uuid4

from django.conf import settings
from django.db import DatabaseError, transaction
from django.http import JsonResponse
from django.utils import timezone

from .models import AuthThrottleBucket


CLEANUP_BATCH = 100


class RateLimited(Exception):
    def __init__(self, retry_after):
        self.retry_after = max(1, math.ceil(retry_after))


class CredentialsChanged(Exception):
    """The account changed after password verification and before session creation."""


def client_ip(request):
    # On Vercel, do not fall back from a missing canonical header to caller input.
    value = request.META.get(
        "HTTP_X_VERCEL_FORWARDED_FOR" if settings.AUTH_TRUST_VERCEL_PROXY else "REMOTE_ADDR", ""
    )
    if not isinstance(value, str):
        return "unknown"
    try:
        address = ipaddress.ip_address(value.strip())
        if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped:
            address = address.ipv4_mapped
        return str(address)
    except ValueError:
        return "unknown"


def key_hash(scope, identity):
    return hmac.new(settings.SECRET_KEY.encode(), f"{scope}:{identity}".encode(), sha256).hexdigest()


def cleanup_expired(now):
    """Maintenance is bounded and cannot poison the caller's transaction."""
    try:
        with transaction.atomic():
            expired = list(AuthThrottleBucket.objects.filter(expires_at__lte=now)
                           .order_by("expires_at", "pk").values_list("pk", flat=True)[:CLEANUP_BATCH])
            # Recheck expiry: a concurrent admission may have reused an expired row.
            AuthThrottleBucket.objects.filter(pk__in=expired, expires_at__lte=now).delete()
    except DatabaseError:
        pass


def reserve(scope, identity, *, limit, window, now=None):
    now = now or timezone.now()
    digest = key_hash(scope, identity)
    with transaction.atomic():
        bucket, _ = AuthThrottleBucket.objects.select_for_update().get_or_create(
            scope=scope, key_hash=digest,
            defaults={"count": 0, "expires_at": now + timedelta(seconds=window)},
        )
        if bucket.expires_at <= now:
            bucket.count = 0
            bucket.expires_at = now + timedelta(seconds=window)
        if bucket.count >= limit:
            raise RateLimited((bucket.expires_at - now).total_seconds())
        bucket.count += 1
        bucket.save(update_fields=["count", "expires_at"])


def reserve_ip(request, *, registration=False):
    now = timezone.now()
    reserve(
        "register_ip" if registration else "login_ip", client_ip(request),
        limit=settings.AUTH_REGISTER_IP_LIMIT if registration else settings.AUTH_LOGIN_IP_LIMIT,
        window=settings.AUTH_REGISTER_WINDOW_SECONDS if registration else settings.AUTH_LOGIN_WINDOW_SECONDS,
        now=now,
    )
    cleanup_expired(now)


def account_identity(identifier, user):
    return f"account:{user.pk}" if user else f"unknown:{identifier.strip().lower()}"


def reserve_account(identifier, user):
    reserve("login_account", account_identity(identifier, user),
            limit=settings.AUTH_LOGIN_ACCOUNT_LIMIT, window=settings.AUTH_LOGIN_WINDOW_SECONDS)


def complete_login(user):
    AuthThrottleBucket.objects.filter(scope="login_account", key_hash=key_hash(
        "login_account", account_identity("", user))).delete()


def auth_errors(view):
    """Safe errors for both API generations; never print database/auth arguments."""
    @wraps(view)
    def guarded(request, *args, **kwargs):
        try:
            return view(request, *args, **kwargs)
        except RateLimited as failure:
            response = JsonResponse({"error": {
                "code": "RATE_LIMITED", "message": "요청이 많아요. 잠시 후 다시 시도해 주세요."
            }, "request_id": uuid4().hex[:12]}, status=429)
            response["Retry-After"] = str(failure.retry_after)
            return response
        except CredentialsChanged:
            return JsonResponse({"error": {
                "code": "INVALID_CREDENTIALS", "message": "로그인 정보를 확인해 주세요."
            }, "request_id": uuid4().hex[:12]}, status=401)
        except DatabaseError:
            return JsonResponse({"error": {
                "code": "AUTH_UNAVAILABLE", "message": "지금은 로그인 요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요."
            }, "request_id": uuid4().hex[:12]}, status=503)
    return guarded
