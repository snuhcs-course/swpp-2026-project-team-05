"""Analyse one article link with the staged analysis and store it as a reader-only cluster.

  1. fetch the source article; a link already stored opens as it is (no model calls)
  2. find same-event articles from other outlets (v2 discovery: claims, Naver search, verification)
  3. keep up to MAX_RELATED of them that are not stored yet (an import would move a stored article to this cluster)
  4. run the staged A-G analysis and import the result (news_import.import_dataset), hidden from the feed
"""

from __future__ import annotations

from typing import Callable

from django.db import transaction
from django.utils import timezone

MAX_RELATED = 3


class LinkAnalysisError(RuntimeError):
    """A link that could not be analysed, with a code and a message for the reader."""

    def __init__(self, code: str, message: str, *, retryable: bool = False) -> None:
        super().__init__(message)
        self.code, self.retryable = code, retryable


DISCOVERY_MESSAGES = {
    "no_claims": "기사에서 비교할 내용을 찾지 못했어요.",
    "search_failed": "다른 언론사 기사를 검색하지 못했어요. 잠시 후 다시 시도해 주세요.",
    "no_candidates": "같은 사건을 다룬 다른 언론사 기사를 찾지 못했어요.",
    "no_same_event": "같은 사건을 다룬 다른 언론사 기사를 찾지 못했어요.",
}


def _as_article(value: dict) -> dict:
    from ..v2.article_input import structure_article_body

    body = value.get("body") or ""
    sentences = value.get("sentences") or (structure_article_body(body)["sentences"] if body.strip() else [])
    return {"title": value.get("title") or "", "body": body, "sentences": sentences,
            "publisher": value.get("publisher") or "", "url": value.get("url") or "",
            "published_at": value.get("published_at") or ""}


def analyze_link(url: str, *, ask: Callable | None = None, discover: Callable | None = None,
                 progress: Callable[[str], None] | None = None) -> dict:
    """{'article_id', 'status': 'existing' | 'created', 'highlight_count'}; raises LinkAnalysisError."""
    from ...models import Article
    from ...news_import import dataset_digest, import_dataset
    from ..v2.article_fetch import ArticleFetchError
    from ..v2.article_input import prepare_article
    from ..v2.display_selection import select_display_article_ids
    from ..v2.pipeline import run_discovery_pipeline
    from . import llm
    from .analysis import analyze
    from .dataset import build_dataset

    notify = progress or (lambda _message: None)
    try:
        source = prepare_article(url)
    except (ArticleFetchError, ValueError):
        raise LinkAnalysisError("source_unavailable", "기사 본문을 가져오지 못했어요. 링크를 확인해 주세요.") from None
    stored = Article.objects.filter(canonical_url=source.get("url") or url).first()
    if stored is not None:
        return {"article_id": str(stored.id), "status": "existing", "highlight_count": stored.findings.count()}

    notify("다른 언론사 기사 찾는 중")
    discovery = (discover or run_discovery_pipeline)(url, _prepared_source=source)
    status = discovery.get("status")
    if status != "ready":
        raise LinkAnalysisError(status or "discovery_failed",
                                DISCOVERY_MESSAGES.get(status, "다른 언론사 기사를 찾지 못했어요. 잠시 후 다시 시도해 주세요."),
                                retryable=status in (None, "search_failed"))
    catalog = {row["candidate_id"]: row for row in discovery["search"]["candidates"]}
    ordered = select_display_article_ids(discovery["selection"], len(catalog), discovery["search"]["candidates"])
    related = []
    for key in ordered:
        row = catalog.get(key)
        if row is None or Article.objects.filter(canonical_url=row.get("url") or "").exists():
            continue
        try:
            article = _as_article(row if row.get("body") else prepare_article(row["url"]))
        except (ArticleFetchError, ValueError, KeyError):
            continue
        if article["sentences"] and article["url"] != source.get("url"):
            related.append(article)
        if len(related) == MAX_RELATED:
            break
    if not related:
        raise LinkAnalysisError("no_same_event", DISCOVERY_MESSAGES["no_same_event"])

    notify("표현 비교 분석 중")
    articles = {"S": _as_article(source), **{f"A{k}": a for k, a in enumerate(related, 1)}}
    try:
        shown = analyze(articles, ask or llm.ask)
    except llm.StagedModelError:
        raise LinkAnalysisError("analysis_unavailable", "분석 서비스가 응답하지 않아요. 잠시 후 다시 시도해 주세요.",
                                retryable=True) from None
    dataset = build_dataset(articles, shown, now=timezone.now().isoformat())
    with transaction.atomic():
        import_dataset(dataset, dataset_hash=dataset_digest(dataset))
    article = Article.objects.get(canonical_url=articles["S"]["url"])
    return {"article_id": str(article.id), "status": "created",
            "highlight_count": len(dataset["clusters"][0]["findings"])}
