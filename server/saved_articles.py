"""User-scoped saved-article mutations and deterministic pagination."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from django.core import signing
from django.db import transaction
from django.db.models import Q
from django.utils.dateparse import parse_datetime

from .models import Article, ArticleVisibility, SavedArticle, UserAccount
from .news_queries import article_summary_payload


CURSOR_SALT = "frameless.saved-articles.v1"


class InvalidSavedCursor(ValueError):
    pass


def encode_cursor(row: SavedArticle) -> str:
    return signing.dumps(
        {
            "v": 1,
            "saved_at": row.saved_at.isoformat(),
            "id": str(row.id),
        },
        salt=CURSOR_SALT,
        compress=True,
    )


def decode_cursor(value: str) -> tuple[datetime, UUID]:
    try:
        payload = signing.loads(value, salt=CURSOR_SALT, max_age=60 * 60 * 24 * 7)
        if payload.get("v") != 1:
            raise ValueError
        saved_at = parse_datetime(str(payload["saved_at"]))
        row_id = UUID(str(payload["id"]))
        if saved_at is None or saved_at.tzinfo is None:
            raise ValueError
        return saved_at, row_id
    except (signing.BadSignature, KeyError, TypeError, ValueError) as exc:
        raise InvalidSavedCursor from exc


def list_saved_articles(
    *, user: UserAccount, cursor: str | None, limit: int,
) -> dict:
    queryset = (SavedArticle.objects
                .filter(
                    user=user,
                    article__visibility=ArticleVisibility.VISIBLE,
                    article__is_feed_visible=True,
                    article__publisher__is_active=True,
                )
                .select_related("article__publisher", "article__story_cluster")
                .order_by("-saved_at", "-id"))
    if cursor:
        saved_at, row_id = decode_cursor(cursor)
        queryset = queryset.filter(
            Q(saved_at__lt=saved_at) | Q(saved_at=saved_at, id__lt=row_id)
        )
    rows = list(queryset[:limit + 1])
    has_more = len(rows) > limit
    visible = rows[:limit]
    return {
        "items": [
            article_summary_payload(
                row.article,
                is_saved=True,
                saved_at=row.saved_at,
            )
            for row in visible
        ],
        "next_cursor": encode_cursor(visible[-1]) if has_more and visible else None,
    }


@transaction.atomic
def save_article(*, user: UserAccount, article_id: UUID) -> SavedArticle | None:
    article = (Article.objects.select_for_update()
               .filter(
                   id=article_id,
                   visibility=ArticleVisibility.VISIBLE,
                   is_feed_visible=True,
                   publisher__is_active=True,
               )
               .first())
    if article is None:
        return None
    row, _ = SavedArticle.objects.get_or_create(user=user, article=article)
    return row


def remove_saved_article(*, user: UserAccount, article_id: UUID) -> None:
    SavedArticle.objects.filter(user=user, article_id=article_id).delete()
