import uuid

from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [("server", "0002_analysisrecord")]

    operations = [
        migrations.CreateModel(
            name="UserAccount",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("username", models.CharField(max_length=64, unique=True)),
                ("password_hash", models.CharField(max_length=256)),
                ("is_developer", models.BooleanField(default=False)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
            ],
            options={"db_table": "frameless_user_account"},
        ),
        migrations.CreateModel(
            name="AuthSession",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("token_hash", models.CharField(max_length=64, unique=True)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("expires_at", models.DateTimeField(db_index=True)),
                ("user", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="sessions", to="server.useraccount")),
            ],
            options={"db_table": "frameless_auth_session"},
        ),
        migrations.AddField(
            model_name="analysisrecord",
            name="user",
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.CASCADE,
                                    related_name="analysis_records", to="server.useraccount"),
        ),
        migrations.AddIndex(
            model_name="analysisrecord",
            index=models.Index(fields=["user", "-updated_at"], name="frameless_history_user_recent"),
        ),
    ]
