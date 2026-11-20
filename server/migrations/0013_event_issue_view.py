from django.db import migrations, models


class Migration(migrations.Migration):
    """Additive: the event issue view (specs/features/event-issue-view.md). Both columns are nullable, so existing
    clusters and articles keep reading as before."""

    dependencies = [
        ('server', '0012_drop_economic_comparison'),
    ]

    operations = [
        migrations.AddField(
            model_name='storycluster',
            name='issue_table',
            field=models.JSONField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='article',
            name='frame_context',
            field=models.JSONField(blank=True, null=True),
        ),
    ]
