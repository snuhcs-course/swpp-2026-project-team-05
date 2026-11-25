"""Bounded recent audience and shared Home ranks; no lifetime click log."""

from __future__ import annotations

import logging
import time
from datetime import timedelta
from uuid import UUID

from django.db import DatabaseError, connection, transaction
from django.db.models import Case, CharField, Count, F, IntegerField, Max, Q, Value, When
from django.db.models.functions import Cast, Concat
from django.utils import timezone
from django.utils.crypto import salted_hmac
from django.utils.dateparse import parse_datetime

from .models import Article, ArticleView, ArticleVisibility, TrendState
from .news_queries import hydrate_story_keys

logger = logging.getLogger(__name__)
WINDOW = timedelta(hours=3)
REFRESH = timedelta(minutes=5)
MAX_AGE = timedelta(minutes=15)
BACKOFF = timedelta(minutes=1)
TOP = 5


class TrendsUnavailable(Exception):
    pass


def viewer_key(*, user, guest_id: str) -> str:
    identity = f"user:{user.id}" if user is not None else f"guest:{UUID(guest_id)}"
    return salted_hmac("frameless.recent-views.v1", identity, algorithm="sha256").hexdigest()


def record_view(*, article_id, key: str) -> bool:
    if not Article.objects.filter(
        pk=article_id, visibility=ArticleVisibility.VISIBLE, publisher__is_active=True,
    ).exists():
        return False
    now = timezone.now()
    article_pk = Article._meta.pk.get_db_prep_value(article_id, connection)
    timestamp = connection.ops.adapt_datetimefield_value(now)
    with connection.cursor() as cursor:
        cursor.execute(
            "INSERT INTO frameless_article_view (article_id, viewer_key, last_viewed_at) "
            "VALUES (%s, %s, %s) ON CONFLICT (viewer_key, article_id) DO UPDATE SET "
            "last_viewed_at = CASE WHEN frameless_article_view.last_viewed_at < excluded.last_viewed_at "
            "THEN excluded.last_viewed_at ELSE frameless_article_view.last_viewed_at END",
            [article_pk, key, timestamp],
        )
    return True


def _eligible():
    return Article.objects.filter(
        visibility=ArticleVisibility.VISIBLE, is_feed_visible=True, publisher__is_active=True,
    )


def latest_keys(limit: int, exclude: set[str]) -> list[str]:
    if limit <= 0:
        return []
    clusters = [key.split(":", 1)[1] for key in exclude if key.startswith("cluster:")]
    singles = [key.split(":", 1)[1] for key in exclude if key.startswith("article:")]
    articles = _eligible()
    grouped = (articles.filter(story_cluster__isnull=False).exclude(story_cluster_id__in=clusters)
               .values("story_cluster_id").annotate(latest=Max("published_at"))
               .order_by("-latest", "story_cluster_id")[:limit])
    ungrouped = (articles.filter(story_cluster__isnull=True).exclude(pk__in=singles)
                 .values("id", "published_at").order_by("-published_at", "id")[:limit])
    positions = [(f"cluster:{row['story_cluster_id']}", row["latest"]) for row in grouped]
    positions += [(f"article:{row['id']}", row["published_at"]) for row in ungrouped]
    return [key for key, _ in sorted(positions, key=lambda row: (-row[1].timestamp(), row[0]))[:limit]]


def _recent(snapshot: dict | None, now) -> bool:
    return bool(snapshot and now - parse_datetime(snapshot["calculated_at"]) <= MAX_AGE)


def _popular(snapshot: dict | None) -> list[dict]:
    return [row for row in (snapshot or {}).get("items", []) if row["kind"] == "POPULAR"]


def calculate_snapshot(now, previous: dict | None) -> dict:
    prior_ranks = {row["story_key"]: row["rank"] for row in _popular(previous)} if _recent(previous, now) else {}
    eligible = _eligible()
    rows = ArticleView.objects.filter(
        last_viewed_at__gte=now - WINDOW,
        article__visibility=ArticleVisibility.VISIBLE, article__publisher__is_active=True,
    ).filter(
        Q(article__story_cluster_id__in=eligible.filter(story_cluster__isnull=False).values("story_cluster_id")) |
        Q(article_id__in=eligible.filter(story_cluster__isnull=True).values("id")),
    ).annotate(story_key=Case(
        When(article__story_cluster__isnull=False,
             then=Concat(Value("cluster:"), Cast(F("article__story_cluster_id"), CharField()))),
        default=Concat(Value("article:"), Cast(F("article_id"), CharField())),
        output_field=CharField(),
    )).values("story_key").annotate(viewer_count=Count("viewer_key", distinct=True))
    # UUID casts on SQLite omit hyphens; normalize only the handful of selected keys below.
    sqlite = connection.vendor == "sqlite"
    prior_case = [When(story_key=(k.replace("-", "") if sqlite else k), then=Value(v))
                  for k, v in prior_ranks.items()]
    rows = rows.annotate(previous_order=Case(*prior_case, default=Value(TOP + 1), output_field=IntegerField()))
    selected = list(rows.order_by("-viewer_count", "previous_order", "story_key")[:TOP])
    items = []
    for rank, row in enumerate(selected, 1):
        kind, raw_id = row["story_key"].split(":", 1)
        items.append({"story_key": f"{kind}:{UUID(raw_id)}", "kind": "POPULAR",
                      "rank": rank, "viewer_count": row["viewer_count"]})
    items += [{"story_key": key, "kind": "LATEST", "rank": None, "viewer_count": None}
              for key in latest_keys(TOP - len(items), {row["story_key"] for row in items})]
    return {"calculated_at": now.isoformat(), "items": items}


def _statement_timeout(milliseconds: int):
    if connection.vendor == "postgresql":
        with connection.cursor() as cursor:
            cursor.execute("SELECT set_config('statement_timeout', %s, true)", [str(milliseconds)])


def _state(now) -> TrendState:
    state = TrendState.objects.get(pk=1)
    if state.next_refresh_at > now:
        return state
    with transaction.atomic():
        queryset = TrendState.objects.filter(pk=1)
        if connection.features.has_select_for_update_skip_locked:
            queryset = queryset.select_for_update(skip_locked=True)
        locked = queryset.first()
        if locked is None or locked.next_refresh_at > now:
            return state
        try:
            with transaction.atomic():
                _statement_timeout(2000)
                snapshot = calculate_snapshot(now, locked.current_snapshot)
                _statement_timeout(0)
        except DatabaseError:
            # The savepoint has rolled back, so recording backoff is still legal.
            locked.next_refresh_at = now + BACKOFF
            locked.save(update_fields=["next_refresh_at"])
            logger.warning("trend_refresh_failed", exc_info=False)
            return locked
        locked.previous_snapshot = locked.current_snapshot
        locked.current_snapshot = snapshot
        locked.next_refresh_at = now + REFRESH
        locked.save(update_fields=["previous_snapshot", "current_snapshot", "next_refresh_at"])
        return locked


def trend_payload(*, user) -> dict:
    now = timezone.now()
    state = _state(now)
    current = state.current_snapshot
    if not _recent(current, now):
        raise TrendsUnavailable()
    entries = current["items"]
    stories = {row["story_key"]: row for row in hydrate_story_keys(
        [entry["story_key"] for entry in entries], user=user,
    )}
    valid = [entry for entry in entries if entry["story_key"] in stories]
    removed = len(valid) != len(entries)
    if removed:
        fillers = latest_keys(TOP - len(valid), set(stories))
        for story in hydrate_story_keys(fillers, user=user):
            stories[story["story_key"]] = story
            valid.append({"story_key": story["story_key"], "kind": "LATEST", "rank": None})
    previous = state.previous_snapshot
    comparable = (not removed and len(_popular(current)) == TOP and
                  _recent(previous, now) and len(_popular(previous)) == TOP)
    ranks = {row["story_key"]: row["rank"] for row in _popular(previous)} if comparable else {}
    items = []
    for row in valid:
        prior = ranks.get(row["story_key"])
        movement = None
        if comparable:
            movement = "NEW" if prior is None else (
                "UP" if prior > row["rank"] else "DOWN" if prior < row["rank"] else None)
        items.append({"kind": row["kind"], "rank": row["rank"], "previous_rank": prior,
                      "movement": movement, "story": stories[row["story_key"]]})
    return {"window_hours": 3, "calculated_at": current["calculated_at"],
            "is_stale": now - parse_datetime(current["calculated_at"]) >= REFRESH,
            "items": items}


def cleanup_views(*, budget_seconds: float = 20, batch_size: int = 1000) -> dict:
    cutoff = timezone.now() - WINDOW
    started = time.monotonic()
    deleted = 0
    while time.monotonic() - started < budget_seconds:
        with transaction.atomic():
            _statement_timeout(1000)
            expired = ArticleView.objects.filter(last_viewed_at__lt=cutoff).order_by("last_viewed_at", "pk")
            if connection.features.has_select_for_update_skip_locked:
                expired = expired.select_for_update(skip_locked=True)
            ids = list(expired.values_list("pk", flat=True)[:batch_size])
            if not ids:
                break
            count, _ = ArticleView.objects.filter(pk__in=ids, last_viewed_at__lt=cutoff).delete()
            deleted += count
    remaining = ArticleView.objects.filter(last_viewed_at__lt=cutoff).exists()
    result = {"deleted": deleted, "remaining": remaining, "duration_seconds": round(time.monotonic() - started, 3)}
    logger.info("trend_cleanup %s", result)
    return result
