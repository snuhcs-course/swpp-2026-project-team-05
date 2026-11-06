import uuid

from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [("server", "0001_cached_analysis")]

    operations = [
        migrations.CreateModel(
            name="AnalysisRecord",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("owner_key", models.CharField(max_length=64)),
                ("source_url", models.URLField(max_length=2048)),
                ("source_url_hash", models.CharField(max_length=64)),
                ("source_title", models.CharField(max_length=512)),
                ("result", models.JSONField()),
                ("execution_status", models.CharField(max_length=16)),
                ("result_version", models.CharField(default="v1", max_length=32)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
            ],
            options={"db_table": "frameless_analysis_record", "ordering": ["-updated_at"]},
        ),
        migrations.AddConstraint(
            model_name="analysisrecord",
            constraint=models.UniqueConstraint(
                fields=("owner_key", "source_url_hash"),
                name="frameless_history_owner_url_uniq",
            ),
        ),
        migrations.AddIndex(
            model_name="analysisrecord",
            index=models.Index(fields=["owner_key", "-updated_at"], name="frameless_history_recent"),
        ),
    ]
