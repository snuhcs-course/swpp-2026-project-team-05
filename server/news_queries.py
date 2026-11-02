"""Read-only projections for the versioned FrameLESS news API."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime
from uuid import UUID

from django.core import signing
from django.db.models import Prefetch, Q
from django.utils.dateparse import parse_datetime

from .article_search import apply_article_search, search_excerpt
from .models import (
    AnalysisStatus,
    Article,
    ArticleVisibility,
    ExpressionFinding,
    FindingComparison,
    Publisher,
    TextScope,
)


CURSOR_SALT = "frameless.news-feed.v2"


class InvalidCursor(ValueError):
    pass


def publisher_payload(publisher: Publisher) -> dict:
    return {
        "code": publisher.code,
        "name": publisher.name_ko,
        "logo_url": publisher.logo_url,
    }


def _analysis_state(article: Article) -> str:
    return article.story_cluster.analysis_status if article.story_cluster_id else AnalysisStatus.NOT_STARTED


def _article_summary(article: Article, search_query: str | None = None) -> dict:
    payload = {
        "id": str(article.id),
        "publisher": publisher_payload(article.publisher),
        "title": article.title,
        "category": article.category,
        "published_at": article.published_at,
        "analysis_state": _analysis_state(article),
    }
    if search_query:
        payload["search_excerpt"] = search_excerpt(article.body_text, search_query)
    return payload


def _context_fingerprint(*, query: str | None, category: str | None,
                         publisher_codes: list[str]) -> str:
    canonical = json.dumps(
        {
            "q": query or "",
            "category": category or "",
            "publishers": sorted(publisher_codes),
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


def list_publishers() -> list[dict]:
    rows = (Publisher.objects
            .filter(is_active=True, articles__is_feed_visible=True)
            .distinct())
    return [publisher_payload(row) for row in rows]


def list_articles(*, category: str | None, publisher_codes: list[str], query: str | None,
                  cursor: str | None, limit: int) -> dict:
    queryset = (Article.objects
                .filter(
                    visibility=ArticleVisibility.VISIBLE,
                    is_feed_visible=True,
                    publisher__is_active=True,
                )
                .select_related("publisher", "story_cluster"))
    if category:
        queryset = queryset.filter(category=category)
    if publisher_codes:
        queryset = queryset.filter(publisher__code__in=publisher_codes)
    if query:
        queryset = apply_article_search(queryset, query).order_by(
            "search_rank", "-published_at", "-id"
        )
    else:
        queryset = queryset.order_by("-published_at", "-id")

    context = _context_fingerprint(
        query=query, category=category, publisher_codes=publisher_codes,
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
    return {
        "items": [_article_summary(row, query) for row in visible],
        "next_cursor": (
            encode_cursor(
                visible[-1],
                context=context,
                search_rank=getattr(visible[-1], "search_rank", None),
            )
            if has_more and visible else None
        ),
    }


def get_article(article_id: UUID) -> Article | None:
    return (Article.objects
            .filter(id=article_id, visibility=ArticleVisibility.VISIBLE,
                    publisher__is_active=True)
            .select_related("publisher", "story_cluster")
            .first())


def article_detail_payload(article: Article) -> dict:
    payload = _article_summary(article)
    payload.update({
        "body": article.body_text,
        "original_url": article.canonical_url,
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
        return {"state": AnalysisStatus.NOT_STARTED, "version": "", "findings": []}

    comparison_queryset = (FindingComparison.objects
                           .filter(target_article__visibility=ArticleVisibility.VISIBLE,
                                   target_article__publisher__is_active=True)
                           .select_related("target_article", "target_article__publisher")
                           .order_by("display_rank", "id"))
    finding_queryset = (ExpressionFinding.objects
                        .filter(article=article)
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
            "comparison_articles": _comparison_pages(finding, article),
        })
    return {
        "state": cluster.analysis_status,
        "version": cluster.analysis_version,
        "findings": findings,
    }
