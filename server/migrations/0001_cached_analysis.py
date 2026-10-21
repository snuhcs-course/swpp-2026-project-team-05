from django.db import migrations, models


class Migration(migrations.Migration):
    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name="CachedAnalysis",
            fields=[
                ("key", models.CharField(max_length=64, primary_key=True, serialize=False)),
                ("source_url", models.URLField(max_length=2048)),
                ("result", models.JSONField()),
                ("execution_status", models.CharField(max_length=16)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("expires_at", models.DateTimeField(db_index=True)),
            ],
            options={"db_table": "frameless_cached_analysis"},
        ),
    ]
