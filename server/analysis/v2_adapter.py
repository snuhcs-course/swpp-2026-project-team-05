"""Adapt the claim-centred v2 pipeline to the Android comparison contract.

The adapter preserves verified source passages and provisional rubric status. It
does not turn an unselected article into a negative match or invent P3 questions.
"""

# AI-assisted integration code (Iteration 2); review with contract tests before use.
from __future__ import annotations

from typing import Any

from .v2.display_selection import select_display_article_ids


class PipelineContractError(ValueError):
    """The pipeline returned data that cannot be shown without misattribution."""


RELATION_LABELS = {
    "same": "같은 주장",
    "opposes": "반박",
    "different_interpretation": "다른 해석",
    "same_issue": "같은 쟁점의 다른 내용",
    "uncertain": "판정 불확실",
}


def _mapping(value: Any, name: str) -> dict:
    if not isinstance(value, dict):
        raise PipelineContractError(f"{name} must be an object")
    return value


def _rows(value: Any, name: str) -> list[dict]:
    if not isinstance(value, list) or any(not isinstance(row, dict) for row in value):
        raise PipelineContractError(f"{name} must be an array of objects")
    return value


def _quote(value: Any) -> str:
    return value.get("quote", "") if isinstance(value, dict) and isinstance(value.get("quote"), str) else ""


def _article_ids(raw: dict, max_related: int) -> tuple[list[str], dict[str, dict]]:
    selection = _mapping(raw.get("selection"), "selection")
    search = _mapping(raw.get("search"), "search")
    candidates = _rows(search.get("candidates"), "search.candidates")
    catalog = {}
    for article in candidates:
        key = article.get("candidate_id")
        if not isinstance(key, str) or not key or key in catalog:
            raise PipelineContractError("candidate IDs must be distinct nonempty strings")
        catalog[key] = article

    same_event = selection.get("same_event_ids")
    baseline = selection.get("event_baseline_ids")
    if not isinstance(same_event, list) or not isinstance(baseline, list):
        raise PipelineContractError("verified article IDs are missing")
    verified = set(same_event)
    comparisons = _rows(selection.get("claim_comparisons"), "selection.claim_comparisons")
    for row in comparisons:
        seen_for_claim = set()
        for article in _rows(row.get("articles"), "claim_comparisons.articles"):
            key = article.get("candidate_id")
            if not isinstance(key, str) or key not in catalog or key not in verified:
                raise PipelineContractError("selected article is not a verified same-event candidate")
            if key in seen_for_claim:
                raise PipelineContractError("duplicate article in one claim comparison")
            seen_for_claim.add(key)
    for key in baseline:
        if not isinstance(key, str) or key not in catalog or key not in verified:
            raise PipelineContractError("baseline article is not a verified same-event candidate")
    return select_display_article_ids(selection, max_related), catalog


def _position_summary(row: dict) -> str:
    places = [item.get("position", "") for item in row.get("positions", []) if isinstance(item, dict)]
    parts = []
    if row.get("headline_evidence"):
        parts.append("제목")
    if "lead" in places:
        parts.append("리드")
    if "body" in places:
        parts.append("본문")
    place = "·".join(parts) if parts else "대응 구간 미확인"
    count = row.get("body_sentence_count")
    if isinstance(count, int):
        place += f", 식별된 본문 문장 {count}개"
    first = row.get("first_body_fraction")
    if isinstance(first, (int, float)) and 0 <= first <= 1:
        place += f", 첫 근거 본문 약 {round(first * 100)}% 지점"
    return place


def _rubric_items(rubric: dict, index_by_id: dict[str, int],
                  withheld_relations: dict[tuple[str, str], dict[str, str]]) -> dict:
    meta = rubric.get("rubric") if isinstance(rubric.get("rubric"), dict) else {}
    information, expression = [], []
    claim_rows = _rows(rubric.get("claim_analyses", []), "rubric_analysis.claim_analyses")
    failed_articles = list(dict.fromkeys(row.get("article_id") for row in _rows(
        rubric.get("article_annotation_failures", []), "rubric_analysis.article_annotation_failures")
        if isinstance(row.get("article_id"), str)))
    failed_set = set(failed_articles)
    failed_claims = list(dict.fromkeys(row.get("claim_id") for row in _rows(
        rubric.get("expression_failures", []), "rubric_analysis.expression_failures")
        if isinstance(row.get("claim_id"), str) and row.get("claim_id")))
    for claim_row in claim_rows:
        expr = claim_row.get("expression")
        if (isinstance(expr, dict) and expr.get("status") == "unverified"
                and isinstance(claim_row.get("claim_id"), str) and claim_row["claim_id"]
                and claim_row["claim_id"] not in failed_claims):
            failed_claims.append(claim_row["claim_id"])
    failed_claim_set = set(failed_claims)
    for claim_row in claim_rows:
        claim_id = claim_row.get("claim_id")
        if not isinstance(claim_id, str):
            continue
        fields = claim_row.get("information") if isinstance(claim_row.get("information"), dict) else {}
        i1_rows = [row for row in fields.get("I1", [])
                   if isinstance(row, dict) and row.get("article_id") not in failed_set]
        presence_by_id = {row.get("article_id"): row.get("presence") for row in i1_rows}
        quote_by_id = {row.get("article_id"): next(
            (_quote(e) for e in row.get("evidence", []) if _quote(e)),
            _quote(row.get("headline_evidence")),
        ) for row in i1_rows}
        for row in i1_rows:
            key = row.get("article_id")
            if key not in index_by_id or key == "S0":
                continue
            presence = row.get("presence", "uncertain")
            relation = row.get("relation", "uncertain")
            evidence = next((_quote(e) for e in row.get("evidence", []) if _quote(e)), "")
            evidence = evidence or _quote(row.get("headline_evidence"))
            withheld = withheld_relations.get((claim_id, key))
            if withheld:
                evidence = evidence or withheld["evidence"]
                if withheld["reason"] == "stage_disagreement":
                    value = "초기 검증과 후속 정보 분석의 관계 판정이 달라 보류했어요. 대응 원문은 확인해 주세요."
                else:
                    value = "후속 정보 분석에서 대응 구절을 찾았지만 초기 비교에서 확인되지 않아 관계 판정을 보류했어요."
            elif presence == "not_found":
                value = "수집한 기사 본문에서 이 입력 주장에 대응하는 구절을 찾지 못했어요. 누락 의도는 판정하지 않아요."
            elif presence == "found":
                owner = row.get("relation_owner")
                if owner == "attributed_speaker":
                    subject = f"{row.get('speaker') or '발언자 미상'}의 인용 발언"
                elif owner == "reporter":
                    subject = "기사 본문 서술"
                else:
                    subject = "발언 주체 확인이 필요한 구절"
                value = f"입력 주장과 대응하는 구절을 찾았어요. 관계: {RELATION_LABELS.get(relation, '판정 불확실')} ({subject})."
            else:
                value = "이 입력 주장과의 대응 여부를 확인하지 못했어요."
            information.append({"code": "I1", "claim_id": claim_id, "article_index": index_by_id[key],
                                "sentence": evidence or None, "value": value})

        # AI-assisted integration: show an explicit unknown state, without inferring absence or a score.
        for key in failed_articles:
            if key in index_by_id and key != "S0" and key not in presence_by_id:
                information.append({"code": "I1", "claim_id": claim_id, "article_index": index_by_id[key],
                                    "sentence": None,
                                    "value": "이 기사의 루브릭 근거를 검증하지 못했어요. 주장 포함·누락과 표현 차이는 미판정입니다."})

        i2_rows = [row for row in fields.get("I2", [])
                   if isinstance(row, dict) and row.get("article_id") not in failed_set]
        source_i2 = next((row for row in i2_rows if row.get("article_id") == "S0"), None)
        for row in i2_rows:
            key = row.get("article_id")
            if (key not in index_by_id or key == "S0" or source_i2 is None
                    or presence_by_id.get("S0") != "found" or presence_by_id.get(key) != "found"):
                continue
            current, source = _position_summary(row), _position_summary(source_i2)
            if current != source:
                evidence = _quote(row.get("headline_evidence")) or quote_by_id.get(key, "")
                information.append({"code": "I2", "claim_id": claim_id, "article_index": index_by_id[key],
                                    "sentence": evidence or None,
                                    "value": f"이 기사: {current}. 입력 기사: {source}."})

        for code, item_key in (("I3", "items"), ("I4", "items"), ("I5", "voices")):
            for row in fields.get(code, []):
                if (not isinstance(row, dict) or row.get("article_id") not in index_by_id
                        or row.get("article_id") in failed_set):
                    continue
                article_index = index_by_id[row["article_id"]]
                for item in row.get(item_key, []):
                    if not isinstance(item, dict):
                        continue
                    if code == "I3":
                        evidence = _quote(item.get("evidence"))
                        value = item.get("content", "")
                    elif code == "I4":
                        evidence = _quote(item.get("evidence"))
                        value = item.get("content", "")
                        markers = [_quote(marker) for marker in item.get("speculation_markers", [])]
                        markers = [marker for marker in markers if marker]
                        if isinstance(value, str) and markers:
                            value += " · 추측 표현 근거: " + ", ".join(markers)
                    else:
                        evidence = _quote(item.get("speech_evidence"))
                        kind = "직접 인용" if item.get("speech_kind") == "direct" else "간접 인용"
                        value = f"{item.get('speaker') or '발언자 미상'}의 {kind}"
                        if item.get("affiliation"):
                            value += f" · {item['affiliation']}"
                        attribution = _quote(item.get("attribution_evidence"))
                        if attribution:
                            value += f" · 발언자 표기 근거: {attribution}"
                    if not isinstance(value, str) or not value.strip() or not evidence:
                        continue
                    information.append({"code": code, "claim_id": claim_id, "article_index": article_index,
                                        "sentence": evidence, "value": value,
                                        "target": item.get("target"), "polarity": item.get("polarity")})

        expr = claim_row.get("expression") if isinstance(claim_row.get("expression"), dict) else {}
        if claim_id in failed_claim_set:
            # AI-assisted resilience: an A-G failure cannot become a stale scored card.
            continue
        for item in expr.get("findings", []):
            if (not isinstance(item, dict) or item.get("article_id") not in index_by_id
                    or item.get("article_id") in failed_set):
                continue
            code = item.get("category")
            if code not in set("ABCDEFG") or item.get("status") == "excluded":
                continue
            marker = item.get("marker", "")
            context = _quote(item.get("context_evidence")) or _quote(item.get("evidence"))
            if not isinstance(marker, str) or not marker or marker not in context:
                continue
            baseline_id = item.get("baseline_article_id")
            baseline = item.get("baseline_evidence")
            baseline_text = _quote(baseline)
            baseline_source = "outlet" if baseline_id in index_by_id and baseline_text else ""
            markedness = item.get("markedness")
            if type(markedness) is not int or not 0 <= markedness <= 2:
                markedness = None
            status = item.get("status", "needs_review")
            if baseline_source == "" and status == "provisional":
                status, markedness = "needs_comparison", None
            expression.append({
                "claim_id": claim_id, "article_index": index_by_id[item["article_id"]],
                "sentence": context, "span": marker,
                "categories": [{"code": code, "markedness": markedness}],
                "baseline": {"source": baseline_source,
                             "text": baseline_text if baseline_source else "",
                             "article_index": index_by_id.get(baseline_id) if baseline_source else None},
                "target": item.get("target"),
                "polarity": item.get("polarity") if item.get("polarity") in {"positive", "negative", "none"} else "none",
                "status": status,
                "note": item.get("markedness_reason") or item.get("description") or "잠정 분석이며 추가 검토가 필요해요.",
            })
    return {"version": meta.get("version", "0.2.1"), "judgment_status": "provisional",
            "questions": [], "information_differences": information,
            "expression_differences": expression,
            "expression_unverified_claim_ids": failed_claims}


def adapt_v2_result(raw: dict, *, max_related: int, requested_url: str) -> dict:
    """Return display data without changing what the v2 pipeline established."""
    if type(max_related) is not int or not 1 <= max_related <= 3:
        raise ValueError("max_related must be 1..3")
    raw = _mapping(raw, "pipeline result")
    source = _mapping(raw.get("source_article"), "source_article")
    analysis = _mapping(raw.get("source_analysis"), "source_analysis")
    claims = _rows(analysis.get("claims"), "source_analysis.claims")
    claim_ids = [row.get("claim_id") for row in claims]
    if any(not isinstance(key, str) or not key for key in claim_ids) or len(set(claim_ids)) != len(claim_ids):
        raise PipelineContractError("source claim IDs must be distinct")
    selected_ids, catalog = _article_ids(raw, max_related)
    index_by_id = {"S0": 0, **{key: i for i, key in enumerate(selected_ids, 1)}}
    selection = raw["selection"]
    comparison_rows = _rows(selection.get("claim_comparisons"), "selection.claim_comparisons")
    by_claim = {row.get("claim_id"): row for row in comparison_rows}
    if len(by_claim) != len(comparison_rows) or set(by_claim) != set(claim_ids):
        raise PipelineContractError("claim comparison IDs do not match source claims")

    source_url = source.get("url") or requested_url
    source_title = source.get("title") or source_url
    rubric = raw.get("rubric_analysis")
    rubric_claims = (_rows(rubric.get("claim_analyses", []), "rubric_analysis.claim_analyses")
                     if isinstance(rubric, dict) else [])
    rubric_partial = isinstance(rubric, dict) and bool(
        rubric.get("article_annotation_failures") or rubric.get("expression_failures")
        or any(isinstance(row.get("expression"), dict) and row["expression"].get("status") == "unverified"
               for row in rubric_claims))
    later_i1 = {}
    if isinstance(rubric, dict):
        for claim_row in rubric_claims:
            fields = claim_row.get("information")
            if isinstance(fields, dict):
                for row in _rows(fields.get("I1", []), "rubric_analysis.I1"):
                    later_i1[(claim_row.get("claim_id"), row.get("article_id"))] = row
    issues = []
    downgraded = False
    withheld_relations: dict[tuple[str, str], dict[str, str]] = {}
    for index, claim in enumerate(claims):
        claim_id = claim["claim_id"]
        source_quote = _quote(claim.get("evidence"))
        if not source_quote:
            raise PipelineContractError("source claim is missing verified evidence")
        members = [{"article_index": 0, "title": source_title, "url": source_url,
                    "source_claim_index": index, "relation_to_source": "source",
                    "evidence": {"quote": source_quote}}]
        selected_for_claim = set()
        for article in _rows(by_claim[claim_id].get("articles"), "claim_comparisons.articles"):
            key = article.get("candidate_id")
            if key not in index_by_id:
                continue  # Verified, but outside the global max_related display limit.
            selected_for_claim.add(key)
            evidence = _quote(article.get("evidence"))
            if not evidence:
                raise PipelineContractError("selected comparison is missing verified evidence")
            candidate = catalog[key]
            later = later_i1.get((claim_id, key))
            earlier_relation = article.get("relation", "uncertain")
            disagreement = later is not None and (
                later.get("coverage_disagreement") is not None
                or later.get("presence") != "found"
                or later.get("relation") != earlier_relation
            )
            downgraded |= disagreement
            if disagreement:
                withheld_relations[(claim_id, key)] = {
                    "reason": "stage_disagreement", "evidence": evidence,
                }
            members.append({"article_index": index_by_id[key],
                            "candidate_id": key,
                            "title": article.get("title") or candidate.get("title") or key,
                            "url": article.get("url") or candidate.get("url") or "",
                            "source_claim_index": index,
                            "relation_to_source": "uncertain" if disagreement else earlier_relation,
                            "relation_owner": "uncertain" if disagreement else article.get("relation_owner", "uncertain"),
                            "speaker": article.get("speaker", ""),
                            "speech_kind": article.get("speech_kind", "none"),
                            "has_multiple_voices": article.get("has_multiple_voices", False),
                            "relation_warning": "후속 분석에서 관계를 확인하지 못했어요." if disagreement else None,
                            "evidence": {"quote": evidence}})
        # The full-article annotation can find a grounded passage in any
        # displayed article that the earlier claim selector did not accept.
        # Expose it as unresolved rather than falsely reporting no match.
        for key in selected_ids:
            if key in selected_for_claim:
                continue
            later = later_i1.get((claim_id, key))
            if not later or later.get("presence") != "found":
                continue
            evidence = next((_quote(item) for item in later.get("evidence", []) if _quote(item)), "")
            evidence = evidence or _quote(later.get("headline_evidence"))
            if not evidence:
                continue
            candidate = catalog[key]
            downgraded = True
            withheld_relations[(claim_id, key)] = {
                "reason": "later_found", "evidence": evidence,
            }
            members.append({"article_index": index_by_id[key], "candidate_id": key,
                            "title": candidate.get("title") or key, "url": candidate.get("url") or "",
                            "source_claim_index": index, "relation_to_source": "uncertain",
                            "relation_owner": "uncertain", "speaker": later.get("speaker", ""),
                            "speech_kind": later.get("speech_kind", "none"),
                            "has_multiple_voices": later.get("has_multiple_voices", False),
                            "relation_warning": "후속 분석에서 대응 구절을 찾았지만 초기 검증과 관계가 일치하지 않아요.",
                            "evidence": {"quote": evidence}})
        issues.append({"claim_id": claim_id, "issue": claim.get("claim") or claim_id,
                       "members": members})

    related_articles = [{"article_index": index_by_id[key], "candidate_id": key,
                         "title": catalog[key].get("title") or key,
                         "url": catalog[key].get("url") or ""} for key in selected_ids]
    safe_errors = [{name: row.get(name) for name in ("stage", "code", "message", "retryable")}
                   for row in raw.get("errors", []) if isinstance(row, dict)]
    warnings = list(raw.get("warnings", []))
    if downgraded:
        warnings.append("일부 기사 문장의 관계 판정이 후속 정보 분석과 달라 보류했습니다.")
    has_displayed_relation = any(len(issue["members"]) > 1 for issue in issues)
    comparison_status = raw.get("comparison_status", "available")
    if downgraded and has_displayed_relation and comparison_status == "no_matches":
        comparison_status = "available"
    result = {
        "source_analysis": {**analysis, "title": source_title, "url": source_url},
        "related_articles": related_articles,
        "comparison": {"issues": issues},
        "analysis_status": {"status": raw.get("status"),
                            "execution_status": "partial" if downgraded or rubric_partial else raw.get("execution_status", "success"),
                            "comparison_status": comparison_status,
                            "warnings": warnings, "errors": safe_errors},
    }
    if isinstance(rubric, dict):
        result["rubric"] = _rubric_items(rubric, index_by_id, withheld_relations)
    return result
