"""Read-only projections for the versioned FrameLESS news API."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime
from uuid import UUID

from django.core import signing
from django.db.models import BooleanField, Exists, F, Max, OuterRef, Prefetch, Q, Value, Window
from django.db.models.functions import RowNumber
from django.utils.dateparse import parse_datetime

from .article_search import apply_article_search, search_excerpt
from .economic_lens import economic_payload
from .event_issues import frame_payload
from .models import (
    AnalysisStatus,
    Article,
    ArticleVisibility,
    ExpressionFinding,
    FindingComparison,
    Publisher,
    SavedArticle,
    TextScope,
    UserAccount,
    UserPublisherFollow,
)


CURSOR_SALT = "frameless.news-feed.v2"
STORY_CURSOR_SALT = "frameless.story-feed.v1"


class InvalidCursor(ValueError):
    pass


def _followed_publisher_codes(user: UserAccount | None) -> list[str]:
    if user is None:
        return []
    return list(
        UserPublisherFollow.objects
        .filter(user=user, publisher__is_active=True)
        .order_by("publisher__code")
        .values_list("publisher__code", flat=True)
    )


def publisher_payload(publisher: Publisher) -> dict:
    return {
        "code": publisher.code,
        "name": publisher.name_ko,
        "logo_url": publisher.logo_url,
    }


def _analysis_state(article: Article) -> str:
    return article.story_cluster.analysis_status if article.story_cluster_id else AnalysisStatus.NOT_STARTED


def article_summary_payload(
    article: Article,
    search_query: str | None = None,
    *,
    is_saved: bool | None = None,
    saved_at: datetime | None = None,
) -> dict:
    payload = {
        "id": str(article.id),
        "publisher": publisher_payload(article.publisher),
        "title": article.title,
        "category": article.category,
        "published_at": article.published_at,
        "analysis_state": _analysis_state(article),
        "is_saved": (
            bool(getattr(article, "is_saved", False))
            if is_saved is None else is_saved
        ),
    }
    if saved_at is not None:
        payload["saved_at"] = saved_at
    if search_query:
        payload["search_excerpt"] = search_excerpt(article.body_text, search_query)
    return payload


def _context_fingerprint(*, query: str | None, category: str | None,
                         publisher_codes: list[str], scope: str,
                         followed_publisher_codes: list[str]) -> str:
    canonical = json.dumps(
        {
            "q": query or "",
            "category": category or "",
            "publishers": sorted(publisher_codes),
            "scope": scope,
            "follows": sorted(followed_publisher_codes),
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode()).hexdigest()[:24]


def encode_cursor(article: Article, *, context: str, search_rank: int | None) -> str:
    return signing.dumps(
        {
            "v": 2,
            "context": context,
            "rank": search_rank,
            "published_at": article.published_at.isoformat(),
            "id": str(article.id),
        },
        salt=CURSOR_SALT,
        compress=True,
    )


def decode_cursor(value: str, *, context: str, search_active: bool) -> tuple[int | None, datetime, UUID]:
    try:
        payload = signing.loads(value, salt=CURSOR_SALT, max_age=60 * 60 * 24 * 7)
        if payload.get("v") != 2 or payload.get("context") != context:
            raise ValueError
        rank = payload.get("rank")
        if search_active:
            if type(rank) is not int or not 0 <= rank <= 2:
                raise ValueError
        elif rank is not None:
            raise ValueError
        published_at = parse_datetime(str(payload["published_at"]))
        article_id = UUID(str(payload["id"]))
        if published_at is None or published_at.tzinfo is None:
            raise ValueError
        return rank, published_at, article_id
    except (signing.BadSignature, KeyError, TypeError, ValueError) as exc:
        raise InvalidCursor from exc


def list_publishers(*, user: UserAccount | None) -> list[dict]:
    followed = (
        Exists(UserPublisherFollow.objects.filter(user=user, publisher_id=OuterRef("pk")))
        if user is not None else Value(False, output_field=BooleanField())
    )
    rows = (Publisher.objects
            .filter(
                is_active=True,
                articles__visibility=ArticleVisibility.VISIBLE,
                articles__is_feed_visible=True,
            )
            .annotate(is_followed=followed)
            .distinct()
            .order_by("display_order", "name_ko", "code"))
    publisher_rows = list(rows)
    recent_rows = (
        Article.objects
        .filter(
            publisher_id__in=[row.id for row in publisher_rows],
            visibility=ArticleVisibility.VISIBLE,
            is_feed_visible=True,
        )
        .annotate(
            publisher_rank=Window(
                expression=RowNumber(),
                partition_by=[F("publisher_id")],
                order_by=[F("published_at").desc(), F("id").desc()],
            )
        )
        .filter(publisher_rank__lte=3)
        .order_by("publisher_id", "-published_at", "-id")
    )
    recent_by_publisher: dict[UUID, list[dict]] = {}
    for article in recent_rows:
        recent_by_publisher.setdefault(article.publisher_id, []).append({
            "id": str(article.id),
            "title": article.title,
            "published_at": article.published_at,
        })
    payloads = []
    for row in publisher_rows:
        payload = publisher_payload(row)
        payload["is_followed"] = bool(row.is_followed)
        payload["recent_articles"] = recent_by_publisher.get(row.id, [])
        payloads.append(payload)
    return payloads


def list_articles(*, user: UserAccount | None, category: str | None,
                  publisher_codes: list[str], query: str | None,
                  cursor: str | None, limit: int, scope: str = "ALL") -> dict:
    is_saved = (
        Exists(SavedArticle.objects.filter(user=user, article_id=OuterRef("pk")))
        if user is not None else Value(False, output_field=BooleanField())
    )
    followed_publisher_codes = (
        _followed_publisher_codes(user) if scope == "FOLLOWING" else []
    )
    queryset = (Article.objects
                .filter(
                    visibility=ArticleVisibility.VISIBLE,
                    is_feed_visible=True,
                    publisher__is_active=True,
                )
                .annotate(is_saved=is_saved)
                .select_related("publisher", "story_cluster"))
    if category:
        queryset = queryset.filter(category=category)
    if publisher_codes:
        queryset = queryset.filter(publisher__code__in=publisher_codes)
    if scope == "FOLLOWING":
        queryset = queryset.filter(publisher__code__in=followed_publisher_codes)
    if query:
        queryset = apply_article_search(queryset, query).order_by(
            "search_rank", "-published_at", "-id"
        )
        # Count the exact searchable corpus before applying the continuation
        # cursor. This is the stable total for the active corpus or category
        # filter rather than the size of the currently loaded page.
        total_count = queryset.count()
    else:
        queryset = queryset.order_by("-published_at", "-id")

    context = _context_fingerprint(
        query=query,
        category=category,
        publisher_codes=publisher_codes,
        scope=scope,
        followed_publisher_codes=followed_publisher_codes,
    )
    if cursor:
        rank, published_at, article_id = decode_cursor(
            cursor, context=context, search_active=bool(query),
        )
        position = (
            Q(published_at__lt=published_at)
            | Q(published_at=published_at, id__lt=article_id)
        )
        if query:
            queryset = queryset.filter(Q(search_rank__gt=rank) | Q(search_rank=rank) & position)
        else:
            queryset = queryset.filter(position)
    rows = list(queryset[:limit + 1])
    has_more = len(rows) > limit
    visible = rows[:limit]
    payload = {
        "items": [article_summary_payload(row, query) for row in visible],
        "next_cursor": (
            encode_cursor(
                visible[-1],
                context=context,
                search_rank=getattr(visible[-1], "search_rank", None),
            )
            if has_more and visible else None
        ),
    }
    if query:
        payload["total_count"] = total_count
    return payload


def _story_context(*, user: UserAccount | None, category: str | None,
                   scope: str, followed_publisher_codes: list[str]) -> str:
    canonical = json.dumps(
        {
            "category": category or "",
            "scope": scope,
            "viewer": str(user.id) if user is not None else "anonymous",
            "follows": followed_publisher_codes,
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode()).hexdigest()[:24]


def _encode_story_cursor(*, latest_published_at: datetime, story_key: str,
                         context: str) -> str:
    return signing.dumps(
        {
            "v": 1,
            "context": context,
            "latest_published_at": latest_published_at.isoformat(),
            "story_key": story_key,
        },
        salt=STORY_CURSOR_SALT,
        compress=True,
    )


def _decode_story_cursor(value: str, *, context: str) -> tuple[datetime, str]:
    try:
        payload = signing.loads(
            value, salt=STORY_CURSOR_SALT, max_age=60 * 60 * 24 * 7,
        )
        if payload.get("v") != 1 or payload.get("context") != context:
            raise ValueError
        latest = parse_datetime(str(payload["latest_published_at"]))
        story_key = str(payload["story_key"])
        if latest is None or latest.tzinfo is None:
            raise ValueError
        prefix, raw_id = story_key.split(":", 1)
        if prefix not in {"cluster", "article"}:
            raise ValueError
        UUID(raw_id)
        return latest, story_key
    except (signing.BadSignature, KeyError, TypeError, ValueError) as exc:
        raise InvalidCursor from exc


def _representative_article(*, story_key: str, articles: list[Article],
                            user: UserAccount | None,
                            followed_publisher_codes: set[str]) -> Article:
    newest_by_publisher: dict[str, Article] = {}
    for article in sorted(articles, key=lambda row: (row.published_at, row.id), reverse=True):
        newest_by_publisher.setdefault(article.publisher.code, article)
    candidates = list(newest_by_publisher.values())
    followed_candidates = [
        article for article in candidates
        if article.publisher.code in followed_publisher_codes
    ]
    if followed_candidates:
        candidates = followed_candidates
    viewer_key = str(user.id) if user is not None else "anonymous"
    return min(
        candidates,
        key=lambda article: hashlib.sha256(
            f"{story_key}:{viewer_key}:{article.publisher.code}".encode()
        ).hexdigest(),
    )


def list_stories(*, user: UserAccount | None, category: str | None,
                 cursor: str | None, limit: int, scope: str = "ALL") -> dict:
    """Return one bounded discovery item per cluster before pagination."""
    followed_codes = _followed_publisher_codes(user)
    followed_set = set(followed_codes)
    context = _story_context(
        user=user,
        category=category,
        scope=scope,
        followed_publisher_codes=followed_codes,
    )
    cursor_latest: datetime | None = None
    cursor_key: str | None = None
    if cursor:
        cursor_latest, cursor_key = _decode_story_cursor(cursor, context=context)

    eligible = Article.objects.filter(
        visibility=ArticleVisibility.VISIBLE,
        is_feed_visible=True,
        publisher__is_active=True,
    )
    if category:
        eligible = eligible.filter(category=category)
    if scope == "FOLLOWING":
        eligible = eligible.filter(publisher__code__in=followed_codes)

    clustered = (
        eligible.filter(story_cluster__isnull=False)
        .values("story_cluster_id")
        .annotate(latest_published_at=Max("published_at"))
    )
    singletons = eligible.filter(story_cluster__isnull=True)

    if cursor_latest is not None and cursor_key is not None:
        cursor_prefix, cursor_raw_id = cursor_key.split(":", 1)
        cursor_uuid = UUID(cursor_raw_id)
        clustered_after = Q(latest_published_at__lt=cursor_latest)
        singleton_after = Q(published_at__lt=cursor_latest)
        if cursor_prefix == "cluster":
            clustered_after |= Q(
                latest_published_at=cursor_latest,
                story_cluster_id__gt=cursor_uuid,
            )
        else:
            singleton_after |= Q(published_at=cursor_latest, id__gt=cursor_uuid)
            clustered_after |= Q(latest_published_at=cursor_latest)
        clustered = clustered.filter(clustered_after)
        singletons = singletons.filter(singleton_after)

    cluster_positions = list(
        clustered.order_by("-latest_published_at", "story_cluster_id")[:limit + 1]
    )
    singleton_positions = list(
        singletons.values("id", "published_at")
        .order_by("-published_at", "id")[:limit + 1]
    )
    positions = [
        {
            "story_key": f"cluster:{row['story_cluster_id']}",
            "latest_published_at": row["latest_published_at"],
            "cluster_id": row["story_cluster_id"],
            "article_id": None,
        }
        for row in cluster_positions
    ] + [
        {
            "story_key": f"article:{row['id']}",
            "latest_published_at": row["published_at"],
            "cluster_id": None,
            "article_id": row["id"],
        }
        for row in singleton_positions
    ]
    positions.sort(key=lambda row: (-row["latest_published_at"].timestamp(), row["story_key"]))
    visible_positions = positions[:limit]
    has_more = len(positions) > limit

    items = _hydrate_story_positions(
        visible_positions, user=user, category=category, scope=scope, followed_set=followed_set,
    )
    next_cursor = None
    if has_more and visible_positions:
        last = visible_positions[-1]
        next_cursor = _encode_story_cursor(
            latest_published_at=last["latest_published_at"],
            story_key=last["story_key"], context=context,
        )
    return {"items": items, "next_cursor": next_cursor}


def hydrate_story_keys(story_keys: list[str], *, user: UserAccount | None) -> list[dict]:
    """Hydrate only the bounded keys selected by a discovery projection."""
    positions = []
    for key in story_keys:
        kind, raw_id = key.split(":", 1)
        positions.append({
            "story_key": key,
            "cluster_id": UUID(raw_id) if kind == "cluster" else None,
            "article_id": UUID(raw_id) if kind == "article" else None,
        })
    return _hydrate_story_positions(
        positions, user=user, followed_set=set(_followed_publisher_codes(user)),
    )


def _hydrate_story_positions(visible_positions: list[dict], *, user: UserAccount | None,
                             category: str | None = None, scope: str = "ALL",
                             followed_set: set[str]) -> list[dict]:
    cluster_ids = [row["cluster_id"] for row in visible_positions if row["cluster_id"]]
    singleton_ids = [row["article_id"] for row in visible_positions if row["article_id"]]
    coverage = Article.objects.filter(
        Q(story_cluster_id__in=cluster_ids) | Q(id__in=singleton_ids),
        visibility=ArticleVisibility.VISIBLE,
        is_feed_visible=True,
        publisher__is_active=True,
    )
    if category:
        coverage = coverage.filter(category=category)
    coverage_rows = list(coverage.select_related("publisher", "story_cluster").defer(
        "body_text", "economic_context", "frame_context", "story_cluster__issue_table",
    ))
    articles_by_story: dict[str, list[Article]] = {}
    for article in coverage_rows:
        story_key = (
            f"cluster:{article.story_cluster_id}"
            if article.story_cluster_id else f"article:{article.id}"
        )
        articles_by_story.setdefault(story_key, []).append(article)

    representative_by_story: dict[str, Article] = {}
    for position in visible_positions:
        story_key = position["story_key"]
        candidates = articles_by_story.get(story_key, [])
        if scope == "FOLLOWING":
            candidates = [
                article for article in candidates
                if article.publisher.code in followed_set
            ]
        if not candidates:
            continue
        representative_by_story[story_key] = _representative_article(
            story_key=story_key,
            articles=candidates,
            user=user,
            followed_publisher_codes=followed_set,
        )

    saved_ids = set()
    if user is not None:
        saved_ids = set(
            SavedArticle.objects.filter(
                user=user,
                article_id__in=[article.id for article in representative_by_story.values()],
            ).values_list("article_id", flat=True)
        )

    items = []
    for position in visible_positions:
        story_key = position["story_key"]
        if story_key not in representative_by_story:
            continue
        articles = articles_by_story[story_key]
        representative = representative_by_story[story_key]
        publishers = {
            article.publisher.code: article.publisher for article in articles
        }
        ordered_publishers = sorted(
            publishers.values(), key=lambda publisher: (
                publisher.display_order, publisher.name_ko, publisher.code,
            ),
        )
        items.append({
            "story_key": story_key,
            "representative_article": article_summary_payload(
                representative,
                is_saved=representative.id in saved_ids,
            ),
            "publisher_count": len(ordered_publishers),
            "publishers": [publisher_payload(row) for row in ordered_publishers],
            "latest_published_at": max(article.published_at for article in articles),
        })

    return items


def get_article(article_id: UUID, *, user: UserAccount | None = None) -> Article | None:
    queryset = Article.objects.filter(
        id=article_id,
        visibility=ArticleVisibility.VISIBLE,
        publisher__is_active=True,
    )
    if user is not None:
        queryset = queryset.annotate(
            is_saved=Exists(
                SavedArticle.objects.filter(user=user, article_id=OuterRef("pk"))
            )
        )
    return (queryset
            .select_related("publisher", "story_cluster")
            .first())


def article_detail_payload(article: Article) -> dict:
    payload = article_summary_payload(article)
    payload.update({
        "body": article.body_text,
        "original_url": article.canonical_url,
        "content_hash": article.content_hash,
    })
    return payload


def _exact_span(text: str, start: int, end: int, quote: str) -> bool:
    return 0 <= start < end <= len(text) and text[start:end] == quote


def _valid_finding(finding: ExpressionFinding, article: Article) -> bool:
    text = article.title if finding.source_scope == TextScope.TITLE else article.body_text
    return (
        _exact_span(text, finding.source_sentence_start, finding.source_sentence_end,
                    finding.source_sentence_text)
        and _exact_span(text, finding.marker_start, finding.marker_end, finding.marker_text)
        and finding.source_sentence_start <= finding.marker_start
        and finding.marker_end <= finding.source_sentence_end
    )


def _valid_comparison(row: FindingComparison, source_article: Article) -> bool:
    text = row.target_article.title if row.target_scope == TextScope.TITLE else row.target_article.body_text
    return (
        row.target_article.visibility == ArticleVisibility.VISIBLE
        and row.target_article.publisher.is_active
        and row.target_article_id != source_article.id
        and row.target_article.story_cluster_id == source_article.story_cluster_id
        and _exact_span(text, row.target_sentence_start, row.target_sentence_end,
                    row.target_sentence_text)
        and _exact_span(text, row.target_marker_start, row.target_marker_end, row.target_marker_text)
        and row.target_sentence_start <= row.target_marker_start
        and row.target_marker_end <= row.target_sentence_end
    )


def _comparison_pages(finding: ExpressionFinding, source_article: Article) -> list[dict]:
    by_article: dict[UUID, list[FindingComparison]] = {}
    for row in finding.comparisons.all():
        if _valid_comparison(row, source_article):
            by_article.setdefault(row.target_article_id, []).append(row)

    pages: list[dict] = []
    used_publishers: set[UUID] = set()
    for rows in by_article.values():
        rows.sort(key=lambda row: (row.display_rank, str(row.id)))
        article = rows[0].target_article
        if article.publisher_id in used_publishers:
            continue
        used_publishers.add(article.publisher_id)
        pages.append({
            "article_id": str(article.id),
            "publisher": publisher_payload(article.publisher),
            "title": article.title,
            "published_at": article.published_at,
            "original_url": article.canonical_url,
            "body": article.body_text,
            "evidence": [
                {
                    "scope": row.target_scope,
                    "sentence": {
                        "start": row.target_sentence_start,
                        "end": row.target_sentence_end,
                        "text": row.target_sentence_text,
                    },
                    "marker": {
                        "start_in_sentence": row.target_marker_start - row.target_sentence_start,
                        "end_in_sentence": row.target_marker_end - row.target_sentence_start,
                        "text": row.target_marker_text,
                    },
                    "target_owner": row.target_owner,
                    "target_speaker_label": row.target_speaker_label,
                    "comparison_summary": row.comparison_summary,
                }
                for row in rows
            ],
        })
        if len(pages) == 3:
            break
    return pages


def article_analysis_payload(article: Article) -> dict:
    cluster = article.story_cluster
    if cluster is None:
        return {
            "state": AnalysisStatus.NOT_STARTED,
            "version": "",
            "source_content_hash": article.content_hash,
            "findings": [],
            "economic": None,
            "frame": None,
        }

    comparison_queryset = (FindingComparison.objects
                           .filter(target_article__visibility=ArticleVisibility.VISIBLE,
                                   target_article__publisher__is_active=True)
                           .select_related("target_article", "target_article__publisher")
                           .order_by("display_rank", "id"))
    finding_queryset = (ExpressionFinding.objects
                        .filter(article=article)
                        .select_related("shared_proposition")
                        .prefetch_related(Prefetch("comparisons", queryset=comparison_queryset))
                        .order_by("display_order", "source_sentence_start", "marker_start"))

    findings = []
    for finding in finding_queryset:
        if not _valid_finding(finding, article):
            continue
        findings.append({
            "id": str(finding.id),
            "categories": finding.categories,
            "source": {
                "scope": finding.source_scope,
                "sentence": {
                    "start": finding.source_sentence_start,
                    "end": finding.source_sentence_end,
                    "text": finding.source_sentence_text,
                },
                "marker": {
                    "start_in_sentence": finding.marker_start - finding.source_sentence_start,
                    "end_in_sentence": finding.marker_end - finding.source_sentence_start,
                    "text": finding.marker_text,
                },
            },
            "explanation": finding.explanation,
            "comparison_overview": (
                finding.shared_proposition.comparison_overview
                if finding.shared_proposition_id else ""
            ),
            "comparison_articles": _comparison_pages(finding, article),
        })
    return {
        "state": cluster.analysis_status,
        "version": cluster.analysis_version,
        "source_content_hash": article.content_hash,
        "findings": findings,
        "economic": economic_payload(article, publisher_payload),
        "frame": frame_payload(article, publisher_payload),
    }
