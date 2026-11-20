#!/usr/bin/env python3
"""Read-only, dataset-aware smoke test for the deployed FrameLESS API.

The Iteration 3 smoke test intentionally pins six reviewed stories.  This
release smoke has a different job: verify that the *currently deployed* public
corpus still satisfies the Reader contract after the production dataset has
changed.  It discovers eligible articles, validates stored evidence offsets and
comparison links, and writes the exact sampled IDs to a JSON evidence file.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import statistics
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable


DEFAULT_BASE_URL = "https://frameless-team05-api.vercel.app"
CATEGORIES = ("POLITICS", "SOCIETY", "ECONOMY")


def request_json(base_url: str, path: str) -> tuple[dict[str, Any], float]:
    started = time.perf_counter()
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        headers={
            "Accept": "application/json",
            "User-Agent": "FrameLESS-release-smoke/2",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)
    return payload, (time.perf_counter() - started) * 1000


def _scope_text(article: dict[str, Any], scope: str) -> str:
    return article.get("title", "") if scope == "TITLE" else article.get("body", "")


def _validate_span(
    container: str,
    span: Any,
    label: str,
    errors: list[str],
) -> None:
    if not isinstance(span, dict):
        errors.append(f"{label}: missing span")
        return
    start, end, text = span.get("start"), span.get("end"), span.get("text")
    if not isinstance(start, int) or not isinstance(end, int) or not isinstance(text, str):
        errors.append(f"{label}: invalid span fields")
        return
    if start < 0 or end < start or end > len(container):
        errors.append(f"{label}: offset out of bounds")
    elif container[start:end] != text:
        errors.append(f"{label}: stored text does not match offsets")


def validate_article(
    detail: dict[str, Any], analysis: dict[str, Any]
) -> tuple[list[str], list[str], int, int]:
    """Return errors, comparison target IDs, finding count, comparison count."""
    errors: list[str] = []
    targets: list[str] = []

    article_id = detail.get("id")
    if not isinstance(article_id, str) or not article_id:
        errors.append("article: missing id")
    for field in ("title", "body", "original_url", "content_hash"):
        if not isinstance(detail.get(field), str) or not detail[field].strip():
            errors.append(f"article: missing {field}")
    publisher = detail.get("publisher")
    if not isinstance(publisher, dict) or not publisher.get("name"):
        errors.append("article: missing publisher")

    if analysis.get("state") != "READY":
        errors.append("analysis: state is not READY")
    if not analysis.get("version"):
        errors.append("analysis: missing version")
    if analysis.get("source_content_hash") != detail.get("content_hash"):
        errors.append("analysis: source hash does not match article")

    findings = analysis.get("findings")
    if not isinstance(findings, list):
        errors.append("analysis: findings is not a list")
        findings = []

    comparison_count = 0
    for finding_index, finding in enumerate(findings):
        prefix = f"finding[{finding_index}]"
        if not isinstance(finding, dict):
            errors.append(f"{prefix}: not an object")
            continue
        if not finding.get("id"):
            errors.append(f"{prefix}: missing id")
        categories = finding.get("categories")
        if not isinstance(categories, list) or not categories:
            errors.append(f"{prefix}: missing categories")
        if not isinstance(finding.get("explanation"), str) or not finding["explanation"].strip():
            errors.append(f"{prefix}: missing explanation")

        source = finding.get("source")
        if not isinstance(source, dict):
            errors.append(f"{prefix}.source: missing object")
        else:
            scope = source.get("scope", "BODY")
            container = _scope_text(detail, scope)
            sentence = source.get("sentence")
            _validate_span(container, sentence, f"{prefix}.source.sentence", errors)
            marker = source.get("marker")
            sentence_text = sentence.get("text", "") if isinstance(sentence, dict) else ""
            if not isinstance(marker, dict):
                errors.append(f"{prefix}.source.marker: missing span")
            else:
                local_span = {
                    "start": marker.get("start_in_sentence"),
                    "end": marker.get("end_in_sentence"),
                    "text": marker.get("text"),
                }
                _validate_span(
                    sentence_text, local_span, f"{prefix}.source.marker", errors
                )

        comparisons = finding.get("comparison_articles")
        if not isinstance(comparisons, list):
            errors.append(f"{prefix}: comparison_articles is not a list")
            continue
        comparison_count += len(comparisons)
        for comparison_index, comparison in enumerate(comparisons):
            comparison_prefix = f"{prefix}.comparison[{comparison_index}]"
            if not isinstance(comparison, dict):
                errors.append(f"{comparison_prefix}: not an object")
                continue
            target_id = comparison.get("article_id")
            if not isinstance(target_id, str) or not target_id:
                errors.append(f"{comparison_prefix}: missing article_id")
            else:
                targets.append(target_id)
                if target_id == article_id:
                    errors.append(f"{comparison_prefix}: points to source article")
            for field in ("title", "body", "original_url"):
                if not isinstance(comparison.get(field), str) or not comparison[field].strip():
                    errors.append(f"{comparison_prefix}: missing {field}")
            compared_publisher = comparison.get("publisher")
            if not isinstance(compared_publisher, dict) or not compared_publisher.get("name"):
                errors.append(f"{comparison_prefix}: missing publisher")
            evidence = comparison.get("evidence")
            if not isinstance(evidence, list) or not evidence:
                errors.append(f"{comparison_prefix}: missing evidence")
                continue
            for evidence_index, item in enumerate(evidence):
                evidence_prefix = f"{comparison_prefix}.evidence[{evidence_index}]"
                if not isinstance(item, dict):
                    errors.append(f"{evidence_prefix}: not an object")
                    continue
                scope = item.get("scope", "BODY")
                container = _scope_text(comparison, scope)
                sentence = item.get("sentence")
                _validate_span(container, sentence, f"{evidence_prefix}.sentence", errors)
                marker = item.get("marker")
                sentence_text = sentence.get("text", "") if isinstance(sentence, dict) else ""
                if not isinstance(marker, dict):
                    errors.append(f"{evidence_prefix}.marker: missing span")
                else:
                    local_span = {
                        "start": marker.get("start_in_sentence"),
                        "end": marker.get("end_in_sentence"),
                        "text": marker.get("text"),
                    }
                    _validate_span(
                        sentence_text, local_span, f"{evidence_prefix}.marker", errors
                    )

    return errors, list(dict.fromkeys(targets)), len(findings), comparison_count


def iter_feed(base_url: str) -> Iterable[dict[str, Any]]:
    seen: set[str] = set()
    for category in CATEGORIES:
        cursor: str | None = None
        while True:
            query = {"category": category, "scope": "ALL", "limit": "20"}
            if cursor:
                query["cursor"] = cursor
            page, _ = request_json(
                base_url, "/api/v1/articles?" + urllib.parse.urlencode(query)
            )
            for article in page.get("items", []):
                article_id = article.get("id")
                if article_id and article_id not in seen:
                    seen.add(article_id)
                    yield article
            cursor = page.get("next_cursor")
            if not cursor:
                break


def round_robin_articles(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_category = {
        category: [item for item in candidates if item.get("category") == category]
        for category in CATEGORIES
    }
    ordered: list[dict[str, Any]] = []
    for index in range(max((len(items) for items in by_category.values()), default=0)):
        for category in CATEGORIES:
            if index < len(by_category[category]):
                ordered.append(by_category[category][index])
    return ordered


def run(base_url: str, sample_size: int) -> tuple[dict[str, Any], bool]:
    health, health_ms = request_json(base_url, "/api/health")
    failures: list[str] = []
    if health.get("status") != "ok" or health.get("analysis_ready") is not True:
        failures.append("health endpoint is not analysis-ready")

    candidates = list(iter_feed(base_url))
    category_counts = {
        category: sum(1 for item in candidates if item.get("category") == category)
        for category in CATEGORIES
    }
    selected: list[dict[str, Any]] = []
    scan_errors: list[dict[str, str]] = []

    # Scan the categories round-robin.  Feed order is category-local, so a
    # straight iteration would fill a six-case sample entirely from Politics
    # whenever that category has enough eligible articles.
    scan_order = round_robin_articles(candidates)

    for summary in scan_order:
        if len(selected) >= sample_size:
            break
        article_id = summary.get("id")
        if not article_id:
            continue
        try:
            detail, detail_ms = request_json(base_url, f"/api/v1/articles/{article_id}")
            analysis, analysis_ms = request_json(
                base_url, f"/api/v1/articles/{article_id}/analysis"
            )
        except Exception as exc:  # live smoke must retain request failures
            scan_errors.append({"article_id": str(article_id), "error": type(exc).__name__})
            continue

        errors, targets, finding_count, comparison_count = validate_article(detail, analysis)
        if errors or finding_count == 0 or comparison_count == 0 or not targets:
            continue

        target_id = targets[0]
        target_errors: list[str] = []
        target_ms = 0.0
        try:
            target_detail, target_detail_ms = request_json(
                base_url, f"/api/v1/articles/{target_id}"
            )
            target_analysis, target_analysis_ms = request_json(
                base_url, f"/api/v1/articles/{target_id}/analysis"
            )
            target_ms = target_detail_ms + target_analysis_ms
            if target_detail.get("id") != target_id or not target_detail.get("body", "").strip():
                target_errors.append("comparison target detail is incomplete")
            if target_analysis.get("state") != "READY":
                target_errors.append("comparison target analysis is not READY")
        except Exception as exc:
            target_errors.append(f"comparison target request failed: {type(exc).__name__}")
        if target_errors:
            continue

        selected.append(
            {
                "article_id": article_id,
                "title": detail["title"],
                "publisher": detail["publisher"]["name"],
                "category": detail["category"],
                "content_hash": detail["content_hash"],
                "analysis_version": analysis["version"],
                "finding_count": finding_count,
                "comparison_article_count": comparison_count,
                "checked_comparison_article_id": target_id,
                "detail_ms": round(detail_ms),
                "analysis_ms": round(analysis_ms),
                "comparison_target_ms": round(target_ms),
            }
        )

    if len(selected) < sample_size:
        failures.append(
            f"only {len(selected)} structurally valid comparison-backed articles found; "
            f"required {sample_size}"
        )

    pair_latencies = [item["detail_ms"] + item["analysis_ms"] for item in selected]
    dataset_material = "\n".join(
        sorted(
            f"{item['article_id']}:{item['content_hash']}:{item['analysis_version']}"
            for item in selected
        )
    )
    result = {
        "schema_version": 1,
        "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "base_url": base_url.rstrip("/"),
        "health": {
            "status": health.get("status"),
            "analysis_ready": health.get("analysis_ready"),
            "latency_ms": round(health_ms),
        },
        "feed_visible_articles": len(candidates),
        "feed_category_counts": category_counts,
        "sample_size_required": sample_size,
        "sample_size_observed": len(selected),
        "sample_fingerprint": hashlib.sha256(dataset_material.encode()).hexdigest(),
        "median_detail_plus_analysis_ms": (
            round(statistics.median(pair_latencies)) if pair_latencies else None
        ),
        "max_detail_plus_analysis_ms": max(pair_latencies) if pair_latencies else None,
        "cases": selected,
        "scan_request_errors": scan_errors,
        "failures": failures,
        "result": "PASS" if not failures else "FAIL",
    }
    return result, not failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--sample-size", type=int, default=6)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.sample_size < 1:
        parser.error("--sample-size must be positive")

    result, passed = run(args.base_url, args.sample_size)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    print(rendered)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
