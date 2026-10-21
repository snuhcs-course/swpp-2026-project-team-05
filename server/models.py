"""Database models for temporary results and per-device analysis history."""

import uuid

from django.db import models


class CachedAnalysis(models.Model):
    key = models.CharField(max_length=64, primary_key=True)
    source_url = models.URLField(max_length=2048)
    result = models.JSONField()
    execution_status = models.CharField(max_length=16)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(db_index=True)

    class Meta:
        db_table = "frameless_cached_analysis"


class UserAccount(models.Model):
    """A small first-party account, deliberately independent of Django admin."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    username = models.CharField(max_length=64, unique=True)
    password_hash = models.CharField(max_length=256)
    is_developer = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "frameless_user_account"


class AuthSession(models.Model):
    """Only a SHA-256 digest of a bearer token is kept in PostgreSQL."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(UserAccount, on_delete=models.CASCADE, related_name="sessions")
    token_hash = models.CharField(max_length=64, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(db_index=True)

    class Meta:
        db_table = "frameless_auth_session"


class AnalysisRecord(models.Model):
    """The latest usable analysis of one source URL for an account."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(UserAccount, null=True, blank=True, on_delete=models.CASCADE,
                             related_name="analysis_records")
    # Used only to claim records created before account login was added.
    owner_key = models.CharField(max_length=64)
    source_url = models.URLField(max_length=2048)
    source_url_hash = models.CharField(max_length=64)
    source_title = models.CharField(max_length=512)
    result = models.JSONField()
    execution_status = models.CharField(max_length=16)
    result_version = models.CharField(max_length=32, default="v1")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "frameless_analysis_record"
        ordering = ["-updated_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["owner_key", "source_url_hash"],
                name="frameless_history_owner_url_uniq",
            )
        ]
        indexes = [
            models.Index(fields=["owner_key", "-updated_at"], name="frameless_history_recent"),
        ]
