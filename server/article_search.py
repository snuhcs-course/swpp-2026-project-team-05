"""Server-side article-search rules shared by the feed API."""

from __future__ import annotations

import re
import unicodedata

from django.db.models import Case, IntegerField, Q, Value, When


MAX_QUERY_LENGTH = 100
MIN_QUERY_LENGTH = 2
MAX_EXCERPT_LENGTH = 180


def normalize_search_query(raw: str | None) -> str:
    """Return the stable query used by validation, caching, and cursors."""
    if not raw:
        return ""
    normalized = unicodedata.normalize("NFC", raw.strip())
    return re.sub(r"\s+", " ", normalized)


def apply_article_search(queryset, query: str):
    """Filter and tier article matches without materializing the corpus."""
    matches = (
        Q(title__icontains=query)
        | Q(publisher__name_ko__icontains=query)
        | Q(body_text__icontains=query)
    )
    rank = Case(
        When(title__icontains=query, then=Value(0)),
        When(publisher__name_ko__icontains=query, then=Value(1)),
        default=Value(2),
        output_field=IntegerField(),
    )
    return queryset.filter(matches).annotate(search_rank=rank)


def search_excerpt(body: str, query: str) -> str | None:
    """Build one bounded, plain-text excerpt around the first body match."""
    match = re.search(re.escape(query), body, flags=re.IGNORECASE)
    if match is None:
        return None
    if len(body) <= MAX_EXCERPT_LENGTH:
        return body

    # Reserve space for ellipses before choosing a window around the match.
    leading_context = (MAX_EXCERPT_LENGTH - len(query)) // 2
    start = max(0, match.start() - leading_context)
    end = min(len(body), start + MAX_EXCERPT_LENGTH)
    if end == len(body):
        start = max(0, end - MAX_EXCERPT_LENGTH)

    prefix = "…" if start > 0 else ""
    suffix = "…" if end < len(body) else ""
    available = MAX_EXCERPT_LENGTH - len(prefix) - len(suffix)
    if end - start > available:
        end = start + available
    return f"{prefix}{body[start:end]}{suffix}"
