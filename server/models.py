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


class AnalysisRecord(models.Model):
    """The latest usable analysis of one source URL for one app installation."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    # The API receives a random installation ID; only its SHA-256 digest is stored.
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
