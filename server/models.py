"""Persistent, short-lived analysis results shared by serverless instances."""

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
