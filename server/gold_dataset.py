"""Convert the reviewed A-G gold set into the lean product dataset schema.

The source files intentionally retain evaluation-only rows (negative examples,
absent alignments, and equal-wording alignments).  Only reviewed expression
differences are projected into the product tables.
"""

from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from urllib.parse import urlsplit

from .models import ArticleVisibility, ExpressionOwner, NewsCategory, TextScope
from .news_import import DatasetError


GOLD_RUBRIC = "v5"
GOLD_KIND = "gold"
ANALYSIS_VERSION = "gold-v5-product-v1"

# The first release exposes two broad sections.  This mapping is reviewed data,
# not an inferred classifier result, and can be split when the product adds more
# feed sections.
POLITICS_CLUSTERS = frozenset({
    "b00", "b01", "b02", "b03", "b04", "b05", "b06", "b14", "b16",
    "b18", "b19", "b20", "b21", "b22", "b23", "b30", "b31",
})

KNOWN_PUBLISHER_CODES = {
    "연합뉴스": "yna",
    "뉴스1": "news1",
    "뉴시스": "newsis",
    "MBC": "mbc",
    "SBS": "sbs",
    "YTN": "ytn",
    "한겨레": "hani",
    "경향신문": "khan",
    "KBS": "kbs",
    "조선일보": "chosun",
    "중앙일보": "joongang",
    "동아일보": "donga",
}

PUBLISHER_NAME_BY_DOMAIN = {
    "www.ajunews.com": "아주경제",
    "it.chosun.com": "IT조선",
}


def _required(mapping: dict, key: str, context: str):
    value = mapping.get(key)
    if value is None or value == "":
        raise DatasetError(f"{context}: missing {key}")
    return value


def _publisher_name(article: dict, context: str) -> str:
    name = str(article.get("publisher") or "").strip()
    if name:
        return name
    host = urlsplit(_required(article, "url", context)).netloc.lower()
    try:
        return PUBLISHER_NAME_BY_DOMAIN[host]
    except KeyError as exc:
        raise DatasetError(f"{context}: publisher is missing for {host}") from exc


def _fallback_publisher_code(name: str) -> str:
    digest = hashlib.sha1(name.encode("utf-8")).hexdigest()[:10]
    return f"outlet-{digest}"


def _timestamp(value: str, context: str) -> str:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise DatasetError(f"{context}: invalid published_at") from exc
    return parsed.isoformat() + ("+09:00" if parsed.tzinfo is None else "")


def load_gold_documents(path: Path) -> list[dict]:
    """Load a directory of reviewed JSON files in stable name order."""
    if not path.is_dir():
        raise DatasetError(f"gold dataset directory does not exist: {path}")
    documents: list[dict] = []
    for file_path in sorted(path.glob("*.json")):
        try:
            document = json.loads(file_path.read_bytes())
        except (OSError, json.JSONDecodeError) as exc:
            raise DatasetError(f"{file_path.name}: invalid JSON") from exc
        if document.get("name") != file_path.stem:
            raise DatasetError(f"{file_path.name}: name must match the filename")
        if document.get("kind") != GOLD_KIND or document.get("rubric") != GOLD_RUBRIC:
            raise DatasetError(f"{file_path.name}: expected {GOLD_KIND}/{GOLD_RUBRIC}")
        documents.append(document)
    if not documents:
        raise DatasetError("gold dataset directory contains no JSON files")
    return documents


def _validate_marker(*, evidence: dict, marker: dict, context: str) -> None:
    sentence = _required(evidence, "quote", context)
    sentence_start = _required(evidence, "start", context)
    marker_start = _required(marker, "start", context)
    marker_end = _required(marker, "end", context)
    relative_start = marker_start - sentence_start
    relative_end = marker_end - sentence_start
    if not (0 <= relative_start < relative_end <= len(sentence)):
        raise DatasetError(f"{context}: marker is outside its evidence sentence")
    if sentence[relative_start:relative_end] != _required(marker, "quote", context):
        raise DatasetError(f"{context}: marker text does not match reviewed offsets")


def _article_key(document_name: str, article_id: str) -> str:
    return f"{document_name}-{article_id.lower()}"


def _publisher_rows(documents: list[dict]) -> tuple[list[dict], dict[str, str]]:
    domains_by_name: dict[str, set[str]] = defaultdict(set)
    for document in documents:
        articles = {"source": document["source_article"], **document["related_articles"]}
        for article_id, article in articles.items():
            context = f"{document['name']} article {article_id}"
            name = _publisher_name(article, context)
            domains_by_name[name].add(urlsplit(_required(article, "url", context)).netloc.lower())

    code_by_name: dict[str, str] = {}
    used_codes: set[str] = set()
    rows: list[dict] = []
    for order, name in enumerate(sorted(domains_by_name), start=1):
        code = KNOWN_PUBLISHER_CODES.get(name, _fallback_publisher_code(name))
        if code in used_codes:
            raise DatasetError(f"publisher code collision: {code}")
        used_codes.add(code)
        code_by_name[name] = code
        domains = sorted(domains_by_name[name])
        non_naver = [domain for domain in domains if domain != "n.news.naver.com"]
        rows.append({
            "code": code,
            "name_ko": name,
            "domain": non_naver[0] if non_naver else domains[0],
            "display_order": order,
        })
    return rows, code_by_name


def _convert_articles(document: dict, code_by_name: dict[str, str]) -> list[dict]:
    document_name = document["name"]
    category = (NewsCategory.POLITICS if document_name in POLITICS_CLUSTERS
                else NewsCategory.SOCIETY)
    raw_articles = {"source": document["source_article"], **document["related_articles"]}
    rows: list[dict] = []
    for article_id, raw in raw_articles.items():
        context = f"{document_name} article {article_id}"
        name = _publisher_name(raw, context)
        rows.append({
            "key": _article_key(document_name, article_id),
            "publisher": code_by_name[name],
            "canonical_url": _required(raw, "url", context),
            # Bodies and canonical page titles are hydrated before validation.
            "title": "",
            "body_text": "",
            "category": category,
            "published_at": _timestamp(_required(raw, "published_at", context), context),
            "visibility": ArticleVisibility.VISIBLE,
        })
    return rows


def _convert_findings(document: dict) -> tuple[list[dict], list[dict]]:
    document_name = document["name"]
    grouped: dict[tuple[int, int, str], dict] = {}
    for related_id, alignment in document["article_alignments"].items():
        context = f"{document_name} alignment {related_id}"
        if alignment.get("status") != GOLD_KIND:
            raise DatasetError(f"{context}: status must be {GOLD_KIND}")
        if related_id not in document["related_articles"]:
            raise DatasetError(f"{context}: unknown related article")
        for unit in alignment.get("unit_alignments", []):
            unit_context = f"{context} unit {unit.get('unit_id', '<unknown>')}"
            relation = unit.get("relation")
            if relation not in {"same", "absent"}:
                raise DatasetError(f"{unit_context}: unsupported relation {relation}")
            differences = unit.get("expression_differences", [])
            if relation == "absent":
                if differences:
                    raise DatasetError(f"{unit_context}: absent unit cannot contain differences")
                continue
            source_evidence = _required(unit, "source_evidence", unit_context)
            related_evidence = _required(unit, "related_evidence", unit_context)
            for difference in differences:
                difference_context = f"{unit_context} expression difference"
                if difference.get("status") != GOLD_KIND:
                    raise DatasetError(f"{difference_context}: status must be {GOLD_KIND}")
                source_marker = _required(difference, "source_marker", difference_context)
                target_marker = _required(difference, "related_marker", difference_context)
                _validate_marker(
                    evidence=source_evidence, marker=source_marker,
                    context=f"{difference_context} source",
                )
                _validate_marker(
                    evidence=related_evidence, marker=target_marker,
                    context=f"{difference_context} target",
                )
                key = (
                    int(_required(source_marker, "start", difference_context)),
                    int(_required(source_marker, "end", difference_context)),
                    str(_required(source_marker, "quote", difference_context)),
                )
                group = grouped.setdefault(key, {
                    "source_sentence_text": source_evidence["quote"],
                    "categories": set(),
                    "notes": [],
                    "comparisons": [],
                    "unit_ids": set(),
                })
                if group["source_sentence_text"] != source_evidence["quote"]:
                    raise DatasetError(
                        f"{difference_context}: one source marker spans different sentences"
                    )
                code = str(_required(difference, "code", difference_context))
                if code not in set("ABCDEFG"):
                    raise DatasetError(f"{difference_context}: unsupported code {code}")
                note = str(_required(difference, "note", difference_context)).strip()
                group["categories"].add(code)
                group["notes"].append(note)
                group["unit_ids"].add(_required(unit, "unit_id", unit_context))
                group["comparisons"].append({
                    "target_article": _article_key(document_name, related_id),
                    "target_scope": TextScope.BODY,
                    "target_sentence_text": related_evidence["quote"],
                    "target_marker_text": target_marker["quote"],
                    "target_owner": ExpressionOwner.REPORTER,
                    "target_speaker_label": None,
                    "comparison_summary": note,
                })

    propositions: list[dict] = []
    findings: list[dict] = []
    proposition_by_sentence: dict[str, str] = {}
    ordered = sorted(grouped.items(), key=lambda item: item[0])
    for display_order, ((marker_start, marker_end, marker_text), group) in enumerate(ordered):
        finding_key = f"finding-{marker_start}-{marker_end}"
        sentence = group["source_sentence_text"]
        proposition_key = proposition_by_sentence.get(sentence)
        if proposition_key is None:
            proposition_key = f"proposition-{marker_start}-{marker_end}"
            proposition_by_sentence[sentence] = proposition_key
            propositions.append({
                "key": proposition_key,
                # The exact reviewed source sentence is a conservative proposition
                # label. It is internal and never presented as generated copy.
                "normalized_proposition": sentence,
            })
        comparisons = []
        for rank, comparison in enumerate(group["comparisons"]):
            comparisons.append({**comparison, "display_rank": rank})
        findings.append({
            "key": finding_key,
            "article": _article_key(document_name, "source"),
            "source_scope": TextScope.BODY,
            "source_sentence_text": group["source_sentence_text"],
            "marker_text": marker_text,
            "categories": sorted(group["categories"]),
            "explanation": group["notes"][0],
            "display_order": display_order,
            "shared_proposition": proposition_key,
            "comparisons": comparisons,
        })
    return propositions, findings


def build_product_dataset(documents: list[dict]) -> dict:
    """Build a schema-v1 product projection from reviewed gold documents."""
    names = [document.get("name") for document in documents]
    if len(names) != len(set(names)):
        raise DatasetError("gold dataset contains duplicate document names")
    publisher_rows, code_by_name = _publisher_rows(documents)
    clusters = []
    for document in documents:
        propositions, findings = _convert_findings(document)
        name = document["name"]
        source = document["source_article"]
        clusters.append({
            "key": f"gold-v5-{name}",
            "internal_label": _required(source, "title", f"{name} source article"),
            "category": (NewsCategory.POLITICS if name in POLITICS_CLUSTERS
                         else NewsCategory.SOCIETY),
            "articles": _convert_articles(document, code_by_name),
            "propositions": propositions,
            "findings": findings,
        })
    return {
        "schema_version": 1,
        "analysis_version": ANALYSIS_VERSION,
        "publishers": publisher_rows,
        "clusters": clusters,
    }
