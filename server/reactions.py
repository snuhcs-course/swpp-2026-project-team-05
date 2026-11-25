"""Current article reactions and bounded, independent Home highlights."""

from datetime import timedelta
import re
from uuid import UUID

from django.db import transaction
from django.db.models import Count, Max, OuterRef, Subquery
from django.utils import timezone

from .models import Article, ArticleReaction, ArticleVisibility, ReactionKind, UserAccount
from .news_queries import article_summary_payload, get_article, hydrate_story_keys


LEADING_TAG = r"(\[[^\[\]]+\]|\([^()]+\)|【[^【】]+】)"
BREAKING_TITLE = r"^\s*(" + LEADING_TAG + r"\s*)*(\[\s*속보\s*\]|\(\s*속보\s*\)|【\s*속보\s*】)"


def is_breaking_title(title: str) -> bool:
    return bool(re.match(BREAKING_TITLE, title))


def _readable():
    return Article.objects.filter(visibility=ArticleVisibility.VISIBLE, publisher__is_active=True)


def reaction_payload(*, article_id, user):
    if not _readable().filter(pk=article_id).exists():
        return None
    counts = dict.fromkeys(ReactionKind.values, 0)
    counts.update({row["kind"]: row["count"] for row in ArticleReaction.objects.filter(
        article_id=article_id,
    ).values("kind").annotate(count=Count("pk"))})
    selected = (ArticleReaction.objects.filter(article_id=article_id, user=user)
                .values_list("kind", flat=True).first()) if user is not None else None
    return {"article_id": str(article_id), "counts": counts, "my_reaction": selected}


@transaction.atomic
def set_reaction(*, article_id, user, kind):
    # A short per-account lock also serializes create/delete when no reaction row exists yet.
    UserAccount.objects.select_for_update().get(pk=user.pk)
    if not _readable().filter(pk=article_id).exists():
        return None
    rows = ArticleReaction.objects.filter(article_id=article_id, user=user)
    if kind is None:
        rows.delete()
    else:
        row = rows.first()
        if row is None:
            ArticleReaction.objects.create(article_id=article_id, user=user, kind=kind, reacted_at=timezone.now())
        elif row.kind != kind:
            row.kind, row.reacted_at = kind, timezone.now()
            row.save(update_fields=["kind", "reacted_at"])
    return reaction_payload(article_id=article_id, user=user)


def highlights_payload(*, user):
    now = timezone.now()
    eligible = _readable().filter(is_feed_visible=True)
    breaking = eligible.filter(published_at__gt=now - timedelta(hours=2), published_at__lte=now,
                               title__regex=BREAKING_TITLE).order_by("-published_at", "id").first()
    breaking_payload = None
    if breaking is not None:
        # Reuse existing personalization without hydrating any other article bodies.
        article = get_article(breaking.pk, user=user)
        if article is not None:
            breaking_payload = {"article": article_summary_payload(article),
                                "expires_at": breaking.published_at + timedelta(hours=2)}

    recent = ArticleReaction.objects.filter(
        reacted_at__gte=now - timedelta(hours=24), reacted_at__lte=now,
        article__visibility=ArticleVisibility.VISIBLE, article__publisher__is_active=True,
    )
    clusters = recent.filter(article__story_cluster_id__in=eligible.filter(
        story_cluster__isnull=False).values("story_cluster_id"))
    singles = recent.filter(article__story_cluster__isnull=True,
                            article_id__in=eligible.filter(story_cluster__isnull=True).values("pk"))
    if breaking is not None:
        if breaking.story_cluster_id:
            clusters = clusters.exclude(article__story_cluster_id=breaking.story_cluster_id)
        else:
            singles = singles.exclude(article_id=breaking.pk)

    latest = eligible.filter(story_cluster_id=OuterRef("article__story_cluster_id")).order_by(
        "-published_at").values("published_at")[:1]
    grouped = list(clusters.values("article__story_cluster_id").annotate(
        participants=Count("user_id", distinct=True), latest=Subquery(latest),
    ).order_by("-participants", "-latest", "article__story_cluster_id")[:1])
    ungrouped = list(singles.values("article_id").annotate(
        participants=Count("user_id", distinct=True), latest=Max("article__published_at"),
    ).order_by("-participants", "-latest", "article_id")[:1])
    candidates = [(f"cluster:{row['article__story_cluster_id']}", row) for row in grouped]
    candidates += [(f"article:{row['article_id']}", row) for row in ungrouped]
    issue = None
    if candidates:
        key, winner = min(candidates, key=lambda pair: (
            -pair[1]["participants"], -pair[1]["latest"].timestamp(), pair[0]))
        # Normalized UUID strings stay the same on PostgreSQL and SQLite.
        kind, raw_id = key.split(":", 1)
        stories = hydrate_story_keys([f"{kind}:{UUID(raw_id)}"], user=user)
        if stories:
            issue = {"story": stories[0], "reaction_user_count": winner["participants"]}
    return {"generated_at": now, "breaking": breaking_payload, "issue": issue}
