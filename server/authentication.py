"""Password and opaque bearer-token helpers for the lightweight app account API."""

from __future__ import annotations

from datetime import timedelta
from hashlib import sha256
import secrets

from django.contrib.auth.hashers import check_password, make_password
from django.core.exceptions import ValidationError
from django.core.validators import validate_email
from django.db import transaction
from django.db.models import Q
from django.utils import timezone

from .models import AuthSession, UserAccount
from .auth_throttle import CredentialsChanged, reserve_account, reserve_ip


SESSION_DAYS = 30


class AuthenticationNoStoreMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)
        if request.path.startswith(("/api/auth/", "/api/v1/auth/", "/api/v1/web/auth/")):
            response["Cache-Control"] = "no-store"
        return response


def session_from_request(request) -> AuthSession | None:
    header = request.META.get("HTTP_AUTHORIZATION", "")
    if not header.startswith("Bearer "):
        return None
    token = header.removeprefix("Bearer ").strip()
    if not token:
        return None
    digest = sha256(token.encode("utf-8")).hexdigest()
    return (AuthSession.objects.select_related("user")
            .filter(token_hash=digest, expires_at__gt=timezone.now()).first())


def user_from_request(request) -> UserAccount | None:
    session = session_from_request(request)
    return session.user if session else None


def create_session(user: UserAccount) -> tuple[str, AuthSession]:
    token = secrets.token_urlsafe(32)
    now = timezone.now()
    with transaction.atomic():
        current = UserAccount.objects.select_for_update().filter(pk=user.pk).first()
        if current is None or current.password_hash != user.password_hash:
            raise CredentialsChanged
        AuthSession.objects.filter(user=user, expires_at__lte=now).delete()
        session = AuthSession.objects.create(
            user=user,
            token_hash=sha256(token.encode("utf-8")).hexdigest(),
            expires_at=now + timedelta(days=SESSION_DAYS),
        )
    return token, session


def normalize_email(value: str) -> str:
    return value.strip().lower()


def normalize_username(value: str) -> str:
    return value.strip().lower()


def authenticate(identifier: str, password: str, *, request=None) -> UserAccount | None:
    if request is not None:
        reserve_ip(request)
    normalized = identifier.strip().lower()
    user = UserAccount.objects.filter(
        Q(username__iexact=normalized) | Q(email__iexact=normalized)
    ).first()
    if request is not None:
        reserve_account(normalized, user)
    if user is None:
        # Match current password-hasher work, without sleeping or storing the result.
        make_password(password)
        return None
    return user if check_password(password, user.password_hash) else None


def login_credentials(payload, *, legacy=False):
    identifier = payload.get("username", "") if legacy else payload.get("identifier", payload.get("username", ""))
    password = payload.get("password", "")
    if not isinstance(identifier, str) or not isinstance(password, str):
        return None
    identifier = identifier.strip()
    if not identifier or len(identifier) > 254 or not password:
        return None
    return identifier, password


def create_account(*, email: str, username: str, password: str) -> UserAccount:
    """Create an ordinary account after API-level field validation."""
    normalized_email = normalize_email(email)
    normalized_username = normalize_username(username)
    validate_email(normalized_email)
    if UserAccount.objects.filter(email__iexact=normalized_email).exists():
        raise ValidationError({"email": "이미 사용 중인 이메일입니다."})
    if UserAccount.objects.filter(username__iexact=normalized_username).exists():
        raise ValidationError({"username": "이미 사용 중인 사용자 이름입니다."})
    return UserAccount.objects.create(
        email=normalized_email,
        username=normalized_username,
        password_hash=make_password(password),
        is_developer=False,
    )


@transaction.atomic
def provision_developer(username: str, password: str) -> UserAccount:
    user, _ = UserAccount.objects.update_or_create(
        username=username,
        defaults={"password_hash": make_password(password), "is_developer": True},
    )
    AuthSession.objects.filter(user=user).delete()
    return user
