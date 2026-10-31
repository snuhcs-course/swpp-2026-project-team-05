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


SESSION_DAYS = 30


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


def authenticate(identifier: str, password: str) -> UserAccount | None:
    normalized = identifier.strip().lower()
    user = UserAccount.objects.filter(
        Q(username__iexact=normalized) | Q(email__iexact=normalized)
    ).first()
    return user if user and check_password(password, user.password_hash) else None


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


def provision_developer(username: str, password: str) -> UserAccount:
    user, _ = UserAccount.objects.update_or_create(
        username=username,
        defaults={"password_hash": make_password(password), "is_developer": True},
    )
    return user
