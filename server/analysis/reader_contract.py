"""Additive full-text Reader data from already grounded v2 results.

Every offset exposed here is a zero-based, end-exclusive UTF-16 offset into
the *extracted* ``body`` returned in the same article object. A claim match is
only a verified relation to the source claim; it is not automatically an A-G
expression judgment or a statement that the two sentences are equivalent.
"""

from __future__ import annotations

from hashlib import sha256
from typing import Any


def _id(prefix: str, *parts: object) -> str:
    digest = sha256("\x1f".join(str(part) for part in parts).encode("utf-8")).hexdigest()[:20]
    return f"{prefix}_{digest}"


def _article(value: dict, article_id: str, article_index: int) -> tuple[dict, list[int]] | None:
    body, rows = value.get("body"), value.get("sentences")
    if not isinstance(body, str) or not body or not isinstance(rows, list) or not rows:
        return None
    prefix = [0]
    for character in body:
        prefix.append(prefix[-1] + (2 if ord(character) > 0xFFFF else 1))
    url = value.get("url") if isinstance(value.get("url"), str) else ""
    title = value.get("title") if isinstance(value.get("title"), str) else ""
    sentences = []
    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            return None
        start, end = row.get("start"), row.get("end")
        if (row.get("sentence_index") != index or type(start) is not int or type(end) is not int
                or not 0 <= start < end <= len(body) or body[start:end] != row.get("text")
                or type(row.get("paragraph_index")) is not int):
            return None
        sentences.append({
            "sentence_id": _id("s", url, start, end, row["text"]),
            "paragraph_index": row["paragraph_index"],
            "start": prefix[start], "end": prefix[end], "text": row["text"],
        })
    return ({"article_id": article_id, "article_index": article_index,
             "title": title, "url": url,
             "publisher": value.get("publisher") if isinstance(value.get("publisher"), str) else "",
             "published_at": value.get("published_at") if isinstance(value.get("published_at"), str) else "",
             "body": body, "sentences": sentences}, prefix)


def _span(article: tuple[dict, list[int]], evidence: Any) -> tuple[dict, dict] | None:
    if not isinstance(evidence, dict) or evidence.get("scope", "body") != "body":
        return None
    displayed, prefix = article
    body, sentences = displayed["body"], displayed["sentences"]
    index, start, end, quote = (evidence.get(key) for key in ("sentence_index", "start", "end", "quote"))
    if (type(index) is not int or not 0 <= index < len(sentences)
            or type(start) is not int or type(end) is not int
            or not isinstance(quote, str) or not quote
            or not 0 <= start < end <= len(body) or body[start:end] != quote):
        return None
    sentence = sentences[index]
    if not sentence["start"] <= prefix[start] < prefix[end] <= sentence["end"]:
        return None
    exact = {"start": prefix[start], "end": prefix[end], "text": quote}
    whole = {"start": sentence["start"], "end": sentence["end"]}
    return whole, exact


def build_reader(raw: dict, selected_ids: list[str], catalog: dict[str, dict],
                 index_by_id: dict[str, int], withheld_relations: dict[tuple[str, str], dict]) -> dict | None:
    """Show source and displayed fetched articles, and only verified sentence pairs.

    A-G findings are attached only if both grounded marker spans belong to the
    exact source/related sentence pair and the model explicitly checked the
    expressions as equivalent. I1 prevalence and stars are not calculated here.
    """
    source = _article(raw["source_article"], "S0", 0)
    if source is None:
        return None  # Legacy or partial pipeline result has no safe full-text body.
    articles = {"S0": source}
    for key in selected_ids:
        item = _article(catalog[key], key, index_by_id[key])
        if item is not None:
            articles[key] = item

    if isinstance(raw.get("reader_analysis"), dict):
        return _build_full_reader(raw["reader_analysis"], articles, selected_ids, index_by_id)

    rubric = raw.get("rubric_analysis")
    failed_articles = set()
    failed_claims = set()
    rubric_claims = {}
    if isinstance(rubric, dict):
        failed_articles = {row.get("article_id") for row in rubric.get("article_annotation_failures", [])
                           if isinstance(row, dict)}
        failed_claims = {row.get("claim_id") for row in rubric.get("expression_failures", [])
                         if isinstance(row, dict)}
        rubric_claims = {row.get("claim_id"): row for row in rubric.get("claim_analyses", [])
                         if isinstance(row, dict) and isinstance(row.get("claim_id"), str)}

    highlights = []
    comparison_by_claim = {row["claim_id"]: row for row in raw["selection"]["claim_comparisons"]}
    for claim in raw["source_analysis"]["claims"]:
        claim_id = claim["claim_id"]
        matches = {row["candidate_id"]: row for row in comparison_by_claim[claim_id]["articles"]
                   if row.get("candidate_id") in articles
                   and (claim_id, row["candidate_id"]) not in withheld_relations}
        source_span = _span(source, claim.get("evidence"))
        if source_span is None:
            continue
        # Keyed by (source sentence, related article, related sentence) so one
        # category does not duplicate a sheet card already backed by I1.
        pairs: dict[tuple[int, str, int], dict] = {}

        def add_pair(key: str, source_index: int, related_index: int,
                     left_exact: dict, right_exact: dict, alignment_scope: str) -> dict:
            left_sentence = source[0]["sentences"][source_index]
            right_sentence = articles[key][0]["sentences"][related_index]
            pair_key = (source_index, key, related_index)
            if pair_key not in pairs:
                pairs[pair_key] = {
                    "pair_id": _id("p", claim_id, left_sentence["sentence_id"], right_sentence["sentence_id"]),
                    "article_id": key, "article_index": index_by_id[key],
                    "source_sentence_id": left_sentence["sentence_id"],
                    "related_sentence_id": right_sentence["sentence_id"],
                    "source_span": {"start": left_sentence["start"], "end": left_sentence["end"]},
                    "related_span": {"start": right_sentence["start"], "end": right_sentence["end"]},
                    "source_evidence_span": left_exact, "related_evidence_span": right_exact,
                    "relation": matches[key].get("relation", "uncertain"),
                    "alignment_scope": alignment_scope, "expression_differences": [],
                }
            return pairs[pair_key]

        source_index = claim["evidence"]["sentence_index"]
        for key, match in matches.items():
            related_span = _span(articles[key], match.get("evidence"))
            if related_span is None:
                continue
            add_pair(key, source_index, match["evidence"]["sentence_index"],
                     source_span[1], related_span[1], "verified_claim_relation")

        rubric_row = rubric_claims.get(claim_id)
        expression = rubric_row.get("expression") if isinstance(rubric_row, dict) else None
        if (claim_id not in failed_claims and isinstance(expression, dict)
                and expression.get("status") != "unverified"):
            for finding in expression.get("findings", []):
                if (not isinstance(finding, dict) or finding.get("status") != "provisional"
                        or finding.get("semantic_relation") != "equivalent"
                        or not isinstance(finding.get("category"), str)
                        or finding["category"] not in set("ABCDEFG")):
                    continue
                first, second = finding.get("article_id"), finding.get("baseline_article_id")
                key = second if first == "S0" else first if second == "S0" else None
                if key not in matches or key in failed_articles:
                    continue
                left = finding.get("evidence") if first == "S0" else finding.get("baseline_evidence")
                right = finding.get("baseline_evidence") if first == "S0" else finding.get("evidence")
                left_span, right_span = _span(source, left), _span(articles[key], right)
                if left_span is None or right_span is None:
                    continue
                source_index, related_index = left["sentence_index"], right["sentence_index"]
                pair = add_pair(key, source_index, related_index, left_span[1], right_span[1],
                                "equivalent_expression")
                pair["alignment_scope"] = "equivalent_expression"
                category = finding["category"]
                detail = {"code": category, "status": "provisional",
                          "source_marker_span": left_span[1], "related_marker_span": right_span[1],
                          "note": finding.get("markedness_reason") or finding.get("description") or ""}
                if detail not in pair["expression_differences"]:
                    pair["expression_differences"].append(detail)

        # A source sentence may carry more than one claim, but each highlight
        # still names the claim whose verified evidence opened these pairs.
        grouped: dict[str, dict] = {}
        for pair in pairs.values():
            sentence_id = pair["source_sentence_id"]
            if sentence_id not in grouped:
                grouped[sentence_id] = {
                    "claim_id": claim_id, "source_sentence_id": sentence_id,
                    "source_span": pair["source_span"], "pairs": [],
                }
            grouped[sentence_id]["pairs"].append(pair)
        highlights.extend(grouped.values())

    return {
        "version": "0.1", "offset_unit": "utf16", "scope": "extracted_article_text",
        "coverage": "verified_displayed_claims_only", "i1_status": "not_computed",
        "source_article": source[0],
        "related_articles": [articles[key][0] for key in selected_ids if key in articles],
        "highlights": highlights, "i1_stars": [],
    }


def _build_full_reader(analysis: dict, articles: dict[str, tuple[dict, list[int]]],
                       selected_ids: list[str], index_by_id: dict[str, int]) -> dict:
    """Project checked source-unit alignment into UTF-16 spans."""
    source = articles["S0"]
    units = {row["unit_id"]: row for row in analysis.get("source_units", [])
             if isinstance(row, dict) and isinstance(row.get("unit_id"), str)}
    alignments = analysis.get("article_alignments", {})
    pairs, highlights = [], {}
    for key in selected_ids:
        if key not in articles or not isinstance(alignments.get(key), dict):
            continue
        for row in alignments[key].get("unit_alignments", []):
            if not isinstance(row, dict) or row.get("relation") not in {"same", "refutes", "reframes"}:
                continue
            unit = units.get(row.get("unit_id"))
            if unit is None:
                continue
            left = _span(source, unit.get("source_evidence"))
            right = _span(articles[key], row.get("related_evidence"))
            if left is None or right is None:
                continue
            source_index = unit["source_evidence"]["sentence_index"]
            related_index = row["related_evidence"]["sentence_index"]
            source_sentence = source[0]["sentences"][source_index]
            related_sentence = articles[key][0]["sentences"][related_index]
            differences = []
            for difference in row.get("expression_differences", []):
                if not isinstance(difference, dict) or difference.get("code") not in set("ABCDEFG"):
                    continue
                source_marker = _span(source, difference.get("source_marker"))
                related_marker = _span(articles[key], difference.get("related_marker"))
                if (source_marker is None or related_marker is None
                        or difference["source_marker"]["sentence_index"] != source_index
                        or difference["related_marker"]["sentence_index"] != related_index):
                    continue
                differences.append({"code": difference["code"], "status": "provisional",
                                    "source_marker_span": source_marker[1],
                                    "related_marker_span": related_marker[1],
                                    "note": difference.get("note", "")})
            pair = {"pair_id": _id("p", unit["unit_id"], source_sentence["sentence_id"],
                                   related_sentence["sentence_id"]),
                    "unit_id": unit["unit_id"], "article_id": key,
                    "article_index": index_by_id[key],
                    "source_sentence_id": source_sentence["sentence_id"],
                    "related_sentence_id": related_sentence["sentence_id"],
                    "source_span": left[0], "related_span": right[0],
                    "source_evidence_span": left[1], "related_evidence_span": right[1],
                    "relation": row["relation"], "alignment_scope": "source_content_unit",
                    "difference_summary": row.get("difference_summary", ""),
                    "expression_differences": differences}
            pairs.append(pair)
            if differences:
                sentence_id = source_sentence["sentence_id"]
                if sentence_id not in highlights:
                    highlights[sentence_id] = {"claim_id": sentence_id,
                                               "source_sentence_id": sentence_id,
                                               "source_span": left[0],
                                               "source_evidence_span": left[1],
                                               "categories": [], "pairs": []}
                highlights[sentence_id]["pairs"].append(pair)
                for difference in differences:
                    if difference["code"] not in highlights[sentence_id]["categories"]:
                        highlights[sentence_id]["categories"].append(difference["code"])

    grouped_stars = {}
    if analysis.get("i1_status") in {"complete", "partial"}:
        for row in analysis.get("i1_stars", []):
            if not isinstance(row, dict) or row.get("status") != "distinctive_in_checked_articles":
                continue
            unit = units.get(row.get("unit_id"))
            if unit is None:
                continue
            span = _span(source, unit.get("source_evidence"))
            if span is None:
                continue
            sentence = source[0]["sentences"][unit["source_evidence"]["sentence_index"]]
            star = grouped_stars.setdefault(sentence["sentence_id"], {
                "source_sentence_id": sentence["sentence_id"], "source_span": span[0],
                "unit_ids": [], "distinctive_contents": [], "source_evidence_spans": [],
                "checked_article_count": row.get("checked_article_count", 0),
                "observed_count": 0,
                "label": f"이번 비교 기사 {row.get('checked_article_count', 0)}건에서는 확인되지 않음",
            })
            star["unit_ids"].append(unit["unit_id"])
            star["distinctive_contents"].append(unit.get("content", ""))
            star["source_evidence_spans"].append(span[1])

    return {
        "version": "0.2", "offset_unit": "utf16", "scope": "extracted_article_text",
        "coverage": analysis.get("coverage", "unknown"),
        "i1_status": analysis.get("i1_status", "withheld"),
        "source_article": source[0],
        "related_articles": [articles[key][0] for key in selected_ids if key in articles],
        "source_sentence_ledger": analysis.get("source_sentence_ledger", []),
        "highlights": list(highlights.values()), "pairs": pairs,
        "i1_stars": list(grouped_stars.values()),
        "article_failures": analysis.get("article_failures", []),
        "warnings": analysis.get("warnings", []),
    }
