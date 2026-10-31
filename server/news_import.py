"""Validation and persistence for reviewed news-analysis datasets."""

from __future__ import annotations

import hashlib
import json
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from django.db import transaction
from django.utils import timezone
from django.utils.dateparse import parse_datetime

from .models import (
    AnalysisStatus,
    Article,
    ArticleVisibility,
    ExpressionFinding,
    ExpressionOwner,
    FindingComparison,
    NewsCategory,
    Publisher,
    SharedProposition,
    StoryCluster,
    TextScope,
)


DATASET_NAMESPACE = uuid.UUID("b2884fa3-2a04-4e68-a3ec-8c4325c4ee7c")


class DatasetError(ValueError):
    """The reviewed dataset is incomplete or internally inconsistent."""


@dataclass(frozen=True)
class ImportCounts:
    publishers: int = 0
    clusters: int = 0
    articles: int = 0
    propositions: int = 0
    findings: int = 0
    comparisons: int = 0


def _required(mapping: dict, key: str, context: str):
    value = mapping.get(key)
    if value is None or value == "":
        raise DatasetError(f"{context}: missing {key}")
    return value


def _stable_uuid(kind: str, key: str) -> uuid.UUID:
    return uuid.uuid5(DATASET_NAMESPACE, f"{kind}:{key}")


def _parse_timestamp(value: str, context: str) -> datetime:
    parsed = parse_datetime(value)
    if parsed is None:
        raise DatasetError(f"{context}: invalid ISO-8601 timestamp")
    if timezone.is_naive(parsed):
        parsed = timezone.make_aware(parsed, timezone.get_current_timezone())
    return parsed


def locate_unique(text: str, quote: str, context: str) -> tuple[int, int]:
    """Locate a reviewed quote and reject ambiguous or missing grounding."""
    start = text.find(quote)
    if start < 0:
        raise DatasetError(f"{context}: quote not found: {quote!r}")
    if text.find(quote, start + 1) >= 0:
        raise DatasetError(f"{context}: quote is not unique: {quote!r}")
    return start, start + len(quote)


def _scope_text(article: Article, scope: str, context: str) -> str:
    if scope == TextScope.TITLE:
        return article.title
    if scope == TextScope.BODY:
        return article.body_text
    raise DatasetError(f"{context}: scope must be TITLE or BODY")


def load_dataset(path: Path) -> tuple[dict, str]:
    raw = path.read_bytes()
    try:
        dataset = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise DatasetError(f"invalid JSON: {exc}") from exc
    if dataset.get("schema_version") != 1:
        raise DatasetError("schema_version must be 1")
    if not isinstance(dataset.get("publishers"), list) or not isinstance(dataset.get("clusters"), list):
        raise DatasetError("publishers and clusters must be arrays")
    return dataset, hashlib.sha256(raw).hexdigest()


def dataset_digest(dataset: dict) -> str:
    canonical = json.dumps(dataset, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode()).hexdigest()


def article_content_hash(title: str, body_text: str) -> str:
    """Hash the immutable article snapshot used to ground reviewed findings."""
    return hashlib.sha256(f"{title}\0{body_text}".encode()).hexdigest()


def hydrate_articles(dataset: dict, *, max_workers: int = 5) -> dict:
    """Fetch only article snapshots that are absent from the reviewed dataset."""
    from .analysis.v2.article_fetch import fetch_article

    hydrated = deepcopy(dataset)
    article_items = [
        article
        for cluster in hydrated["clusters"]
        for article in cluster.get("articles", [])
        if not article.get("title") or not article.get("body_text")
    ]

    if not article_items:
        return hydrated

    def fetch(item: dict) -> tuple[dict, dict]:
        context = f"article {item.get('key', '<unknown>')}"
        url = _required(item, "canonical_url", context)
        return item, fetch_article(url)

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = [executor.submit(fetch, item) for item in article_items]
        for future in as_completed(futures):
            item, fetched = future.result()
            item["title"] = _required(fetched, "title", f"article {item['key']}")
            item["body_text"] = _required(fetched, "body", f"article {item['key']}")
            if fetched.get("published_at"):
                item["published_at"] = fetched["published_at"]
    return hydrated


@transaction.atomic
def import_dataset(dataset: dict, *, dataset_hash: str, dry_run: bool = False) -> ImportCounts:
    publisher_by_code: dict[str, Publisher] = {}
    for item in dataset["publishers"]:
        code = _required(item, "code", "publisher")
        publisher, _ = Publisher.objects.update_or_create(
            code=code,
            defaults={
                "name_ko": _required(item, "name_ko", f"publisher {code}"),
                "domain": _required(item, "domain", f"publisher {code}"),
                "logo_url": item.get("logo_url") or None,
                "is_active": item.get("is_active", True),
                "display_order": item.get("display_order", 0),
            },
        )
        publisher_by_code[code] = publisher

    article_total = proposition_total = finding_total = comparison_total = 0
    for cluster_item in dataset["clusters"]:
        cluster_key = _required(cluster_item, "key", "cluster")
        context = f"cluster {cluster_key}"
        category = _required(cluster_item, "category", context)
        if category not in NewsCategory.values:
            raise DatasetError(f"{context}: unsupported category {category}")

        cluster_id = _stable_uuid("cluster", cluster_key)
        cluster, _ = StoryCluster.objects.update_or_create(
            id=cluster_id,
            defaults={
                "internal_label": _required(cluster_item, "internal_label", context),
                "category": category,
                "analysis_status": AnalysisStatus.UPDATING,
            },
        )

        article_by_key: dict[str, Article] = {}
        article_items = cluster_item.get("articles")
        if not isinstance(article_items, list) or not article_items:
            raise DatasetError(f"{context}: articles must be a non-empty array")
        for article_item in article_items:
            article_key = _required(article_item, "key", context)
            article_context = f"{context} article {article_key}"
            publisher_code = _required(article_item, "publisher", article_context)
            try:
                publisher = publisher_by_code[publisher_code]
            except KeyError as exc:
                raise DatasetError(f"{article_context}: unknown publisher {publisher_code}") from exc
            canonical_url = _required(article_item, "canonical_url", article_context)
            title = _required(article_item, "title", article_context)
            body = _required(article_item, "body_text", article_context)
            content_hash = article_content_hash(title, body)
            existing = Article.objects.filter(canonical_url=canonical_url).only(
                "id", "title", "body_text", "content_hash"
            ).first()
            if existing is not None and (
                existing.title != title
                or existing.body_text != body
                or existing.content_hash != content_hash
            ):
                raise DatasetError(
                    f"{article_context}: stored article title or body changed for {canonical_url}; "
                    "review and import it as a new dataset revision"
                )
            article, _ = Article.objects.update_or_create(
                canonical_url=canonical_url,
                defaults={
                    "publisher": publisher,
                    "story_cluster": cluster,
                    "source_article_id": article_item.get("source_article_id", ""),
                    "title": title,
                    "body_text": body,
                    "content_hash": content_hash,
                    "category": category,
                    "published_at": _parse_timestamp(
                        _required(article_item, "published_at", article_context), article_context
                    ),
                    "visibility": article_item.get("visibility", ArticleVisibility.VISIBLE),
                },
            )
            article_by_key[article_key] = article
            article_total += 1

        # The dataset is the reviewed current analysis for this cluster. Old product rows
        # remain readable until this transaction reaches this point, then are replaced atomically.
        ExpressionFinding.objects.filter(article__story_cluster=cluster).delete()
        SharedProposition.objects.filter(story_cluster=cluster).delete()

        proposition_by_key: dict[str, SharedProposition] = {}
        for proposition_item in cluster_item.get("propositions", []):
            proposition_key = _required(proposition_item, "key", context)
            proposition = SharedProposition(
                id=_stable_uuid("proposition", f"{cluster_key}:{proposition_key}"),
                story_cluster=cluster,
                normalized_proposition=_required(
                    proposition_item, "normalized_proposition", f"{context} proposition {proposition_key}"
                ),
            )
            proposition.full_clean()
            proposition.save()
            proposition_by_key[proposition_key] = proposition
            proposition_total += 1

        for index, finding_item in enumerate(cluster_item.get("findings", [])):
            finding_key = _required(finding_item, "key", context)
            finding_context = f"{context} finding {finding_key}"
            try:
                article = article_by_key[_required(finding_item, "article", finding_context)]
            except KeyError as exc:
                raise DatasetError(f"{finding_context}: unknown source article") from exc
            scope = _required(finding_item, "source_scope", finding_context)
            selected_text = _scope_text(article, scope, finding_context)
            sentence = _required(finding_item, "source_sentence_text", finding_context)
            marker = _required(finding_item, "marker_text", finding_context)
            sentence_start, sentence_end = locate_unique(selected_text, sentence, f"{finding_context} sentence")
            relative_start, relative_end = locate_unique(sentence, marker, f"{finding_context} marker")
            proposition_key = finding_item.get("shared_proposition")
            if proposition_key and proposition_key not in proposition_by_key:
                raise DatasetError(f"{finding_context}: unknown shared proposition {proposition_key}")
            finding = ExpressionFinding(
                id=_stable_uuid("finding", f"{cluster_key}:{finding_key}"),
                article=article,
                source_scope=scope,
                source_sentence_start=sentence_start,
                source_sentence_end=sentence_end,
                source_sentence_text=sentence,
                marker_start=sentence_start + relative_start,
                marker_end=sentence_start + relative_end,
                marker_text=marker,
                categories=_required(finding_item, "categories", finding_context),
                explanation=_required(finding_item, "explanation", finding_context),
                display_order=finding_item.get("display_order", index),
                shared_proposition=proposition_by_key.get(proposition_key),
            )
            finding.full_clean()
            finding.save()
            finding_total += 1

            for rank, comparison_item in enumerate(finding_item.get("comparisons", [])):
                comparison_context = f"{finding_context} comparison {rank}"
                try:
                    target = article_by_key[
                        _required(comparison_item, "target_article", comparison_context)
                    ]
                except KeyError as exc:
                    raise DatasetError(f"{comparison_context}: unknown target article") from exc
                target_scope = _required(comparison_item, "target_scope", comparison_context)
                target_text = _scope_text(target, target_scope, comparison_context)
                target_sentence = _required(
                    comparison_item, "target_sentence_text", comparison_context
                )
                target_marker = _required(comparison_item, "target_marker_text", comparison_context)
                sentence_start, sentence_end = locate_unique(
                    target_text, target_sentence, f"{comparison_context} sentence"
                )
                relative_start, relative_end = locate_unique(
                    target_sentence, target_marker, f"{comparison_context} marker"
                )
                comparison = FindingComparison(
                    id=_stable_uuid("comparison", f"{cluster_key}:{finding_key}:{rank}"),
                    expression_finding=finding,
                    target_article=target,
                    target_scope=target_scope,
                    target_sentence_start=sentence_start,
                    target_sentence_end=sentence_end,
                    target_sentence_text=target_sentence,
                    target_marker_start=sentence_start + relative_start,
                    target_marker_end=sentence_start + relative_end,
                    target_marker_text=target_marker,
                    target_owner=_required(comparison_item, "target_owner", comparison_context),
                    target_speaker_label=comparison_item.get("target_speaker_label") or None,
                    comparison_summary=_required(
                        comparison_item, "comparison_summary", comparison_context
                    ),
                    display_rank=comparison_item.get("display_rank", rank),
                )
                comparison.full_clean()
                comparison.save()
                comparison_total += 1

        cluster.analysis_status = AnalysisStatus.READY
        cluster.analysis_version = dataset.get("analysis_version", "reviewed-v1")
        cluster.analysis_input_hash = dataset_hash
        cluster.analyzed_at = timezone.now()
        cluster.save(
            update_fields=[
                "analysis_status", "analysis_version", "analysis_input_hash",
                "analyzed_at", "updated_at",
            ]
        )

    if dry_run:
        transaction.set_rollback(True)
    return ImportCounts(
        publishers=len(publisher_by_code),
        clusters=len(dataset["clusters"]),
        articles=article_total,
        propositions=proposition_total,
        findings=finding_total,
        comparisons=comparison_total,
    )
