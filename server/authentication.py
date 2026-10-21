"""Password and opaque bearer-token helpers for the lightweight app account API."""

from __future__ import annotations

from datetime import timedelta
from hashlib import sha256
import secrets

from django.contrib.auth.hashers import check_password, make_password
from django.db import transaction
from django.utils import timezone

from .models import AuthSession, UserAccount


SESSION_DAYS = 30


def user_from_request(request) -> UserAccount | None:
    header = request.META.get("HTTP_AUTHORIZATION", "")
    if not header.startswith("Bearer "):
        return None
    token = header.removeprefix("Bearer ").strip()
    if not token:
        return None
    digest = sha256(token.encode("utf-8")).hexdigest()
    session = (AuthSession.objects.select_related("user")
               .filter(token_hash=digest, expires_at__gt=timezone.now()).first())
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


def authenticate(username: str, password: str) -> UserAccount | None:
    user = UserAccount.objects.filter(username=username).first()
    return user if user and check_password(password, user.password_hash) else None


def provision_developer(username: str, password: str) -> UserAccount:
    user, _ = UserAccount.objects.update_or_create(
        username=username,
        defaults={"password_hash": make_password(password), "is_developer": True},
    )
    return user
