import uuid

from django.db import migrations, models


def _distinct_text(values):
    seen = set()
    result = []
    for value in values:
        text = (value or "").strip()
        if text and text not in seen:
            seen.add(text)
            result.append(text)
    return result


def forward(apps, schema_editor):
    SharedProposition = apps.get_model("server", "SharedProposition")
    ExpressionFinding = apps.get_model("server", "ExpressionFinding")
    FindingComparison = apps.get_model("server", "FindingComparison")

    for proposition in SharedProposition.objects.all().iterator():
        findings = list(
            ExpressionFinding.objects.filter(shared_proposition_id=proposition.id)
            .order_by("display_order", "id")
        )
        for index, finding in enumerate(findings):
            notes = _distinct_text(
                FindingComparison.objects.filter(expression_finding_id=finding.id)
                .order_by("display_rank", "id")
                .values_list("comparison_summary", flat=True)
            )
            overview = "\n\n".join(notes) or (finding.explanation or "").strip()
            if index == 0:
                target = proposition
            else:
                target = SharedProposition.objects.create(
                    id=uuid.uuid4(),
                    story_cluster_id=proposition.story_cluster_id,
                    normalized_proposition=proposition.normalized_proposition,
                    comparison_overview=overview,
                )
                finding.shared_proposition_id = target.id
                finding.save(update_fields=["shared_proposition"])
            if target.comparison_overview != overview:
                target.comparison_overview = overview
                target.save(update_fields=["comparison_overview"])

    FindingComparison.objects.update(comparison_summary="")


def backward(apps, schema_editor):
    SharedProposition = apps.get_model("server", "SharedProposition")
    ExpressionFinding = apps.get_model("server", "ExpressionFinding")
    FindingComparison = apps.get_model("server", "FindingComparison")

    propositions = list(
        SharedProposition.objects.all().order_by(
            "story_cluster_id", "normalized_proposition", "created_at", "id"
        )
    )
    canonical = {}
    for proposition in propositions:
        key = (proposition.story_cluster_id, proposition.normalized_proposition)
        keep = canonical.setdefault(key, proposition)
        findings = list(ExpressionFinding.objects.filter(shared_proposition_id=proposition.id))
        for finding in findings:
            FindingComparison.objects.filter(expression_finding_id=finding.id).update(
                comparison_summary=proposition.comparison_overview or finding.explanation
            )
            if proposition.id != keep.id:
                finding.shared_proposition_id = keep.id
                finding.save(update_fields=["shared_proposition"])
        if proposition.id != keep.id:
            proposition.delete()


class Migration(migrations.Migration):
    dependencies = [
        ("server", "0009_user_publisher_follow"),
    ]

    operations = [
        migrations.RemoveConstraint(
            model_name="sharedproposition",
            name="frameless_cluster_proposition_uniq",
        ),
        migrations.AddField(
            model_name="sharedproposition",
            name="comparison_overview",
            field=models.TextField(blank=True, default=""),
        ),
        migrations.AlterField(
            model_name="findingcomparison",
            name="comparison_summary",
            field=models.TextField(blank=True, default=""),
        ),
        migrations.RunPython(forward, backward),
    ]
