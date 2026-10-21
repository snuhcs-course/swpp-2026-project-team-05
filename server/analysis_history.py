"""Per-installation analysis history, kept separate from the short-lived cache."""

from __future__ import annotations

import logging
from hashlib import sha256
from uuid import UUID

from django.db import DatabaseError

from .analysis.diagnostics import log_event
from .models import AnalysisRecord, UserAccount


logger = logging.getLogger("server.views")
CLIENT_ID_HEADER = "HTTP_X_FRAMELESS_CLIENT_ID"
MAX_HISTORY_ITEMS = 50


class HistoryUnavailableError(RuntimeError):
    """The history database could not complete a user-facing read."""


def owner_key_for_request(request) -> str | None:
    """Return a stable server-side digest for a valid random installation UUID."""
    supplied = request.META.get(CLIENT_ID_HEADER, "").strip()
    try:
        client_id = str(UUID(supplied))
    except (ValueError, AttributeError):
        return None
    return sha256(client_id.encode("utf-8")).hexdigest()


def account_owner_key(user: UserAccount) -> str:
    return sha256(f"user:{user.id}".encode("utf-8")).hexdigest()


def save_analysis_record(user: UserAccount | None, source_url: str, result: dict) -> str | None:
    """Upsert a usable result for an authenticated account without blocking analysis."""
    if not user:
        return None
    status = result.get("analysis_status")
    if not isinstance(status, dict) or status.get("comparison_status") not in {"available", "no_matches"}:
        return None
    execution_status = status.get("execution_status")
    if execution_status not in {"success", "partial"}:
        return None
    source = result.get("source_analysis")
    if not isinstance(source, dict):
        return None
    title = str(source.get("title") or source_url).strip()[:512]
    if not title:
        return None
    try:
        record, _ = AnalysisRecord.objects.update_or_create(
            owner_key=account_owner_key(user),
            source_url_hash=sha256(source_url.encode("utf-8")).hexdigest(),
            defaults={
                "user": user,
                "source_url": source_url,
                "source_title": title,
                "result": result,
                "execution_status": execution_status,
                "result_version": "v1",
            },
        )
        log_event(logger, "analysis_history_saved", record_id=str(record.id))
        return str(record.id)
    except (DatabaseError, TypeError, ValueError) as exc:
        log_event(logger, "analysis_history_write_failed", level=logging.WARNING,
                  error_type=type(exc).__name__)
        return None


def list_analysis_records(user: UserAccount, limit: int) -> list[dict]:
    """Return lightweight history rows; the full JSON is fetched per selection."""
    try:
        return list(
            AnalysisRecord.objects.filter(user=user)
            .values("id", "source_url", "source_title", "execution_status", "result_version",
                    "created_at", "updated_at")[:limit]
        )
    except DatabaseError as exc:
        log_event(logger, "analysis_history_list_failed", level=logging.WARNING,
                  error_type=type(exc).__name__)
        raise HistoryUnavailableError from exc


def load_analysis_record(user: UserAccount, record_id: UUID) -> dict | None:
    try:
        return (
            AnalysisRecord.objects.filter(user=user, id=record_id)
            .values("result").first()
        )
    except DatabaseError as exc:
        log_event(logger, "analysis_history_load_failed", level=logging.WARNING,
                  error_type=type(exc).__name__)
        raise HistoryUnavailableError from exc


def claim_legacy_history(user: UserAccount, legacy_owner_key: str | None) -> int:
    """Attach pre-login per-installation results to the account on its first login."""
    if not legacy_owner_key:
        return 0
    try:
        records = list(AnalysisRecord.objects.filter(user__isnull=True, owner_key=legacy_owner_key))
        account_key = account_owner_key(user)
        claimed = 0
        for record in records:
            conflict = AnalysisRecord.objects.filter(
                owner_key=account_key, source_url_hash=record.source_url_hash
            ).exclude(id=record.id).first()
            if conflict:
                if record.updated_at > conflict.updated_at:
                    conflict.source_url = record.source_url
                    conflict.source_title = record.source_title
                    conflict.result = record.result
                    conflict.execution_status = record.execution_status
                    conflict.result_version = record.result_version
                    conflict.save()
                record.delete()
            else:
                record.user = user
                record.owner_key = account_key
                record.save(update_fields=["user", "owner_key", "updated_at"])
                claimed += 1
        return claimed
    except DatabaseError as exc:
        log_event(logger, "analysis_history_claim_failed", level=logging.WARNING,
                  error_type=type(exc).__name__)
        return 0
