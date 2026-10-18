"""Readable, claim-centered presentation of discovery and rubric results."""

import json
from pathlib import Path
from uuid import uuid4
from urllib.error import URLError


class OperationError(RuntimeError):
    """An expected service failure with a safe, machine-readable description."""

    def __init__(self, message: str, *, code: str, retryable: bool = False,
                 http_status: int | None = None):
        super().__init__(message)
        self.code, self.retryable, self.http_status = code, retryable, http_status


def make_error_record(exc: Exception, *, stage: str, **context) -> dict:
    """Describe a failure without exposing request headers or raw exception text."""
    chain, current = [], exc
    while current is not None and all(current is not item for item in chain):
        chain.append(current)
        current = current.__cause__
    http_status = next((getattr(item, "http_status", None) or getattr(item, "code", None)
                        for item in chain if isinstance(getattr(item, "http_status", None)
                        or getattr(item, "code", None), int)), None)
    timeout = any(isinstance(item, TimeoutError) or isinstance(getattr(item, "reason", None), TimeoutError)
                  for item in chain)
    retryable = timeout or bool(http_status and (http_status in (408, 429) or http_status >= 500))
    if isinstance(exc, OperationError):
        code, message, retryable = exc.code, str(exc), exc.retryable
    elif stage in {"source_fetch", "candidate_fetch"}:
        fetch_stage = getattr(exc, "stage", "")
        if timeout:
            code, message = "ARTICLE_TIMEOUT", "기사 본문을 가져오는 시간이 초과됐습니다."
        elif http_status:
            code, message = "ARTICLE_HTTP_ERROR", f"기사 서버가 HTTP {http_status}를 반환했습니다."
        elif fetch_stage == "incomplete_response" or any(type(item).__name__ == "IncompleteRead" for item in chain):
            code, message, retryable = "ARTICLE_RESPONSE_INTERRUPTED", "기사 응답이 도중에 끊어졌습니다.", True
        elif fetch_stage == "extraction":
            code, message = "ARTICLE_BODY_NOT_FOUND", "페이지에서 기사 본문을 찾지 못했습니다."
        elif fetch_stage == "content_type":
            code, message = "ARTICLE_UNSUPPORTED_CONTENT", "기사 주소가 HTML 페이지를 반환하지 않았습니다."
        elif fetch_stage == "size_limit":
            code, message = "ARTICLE_SIZE_LIMIT", "기사 페이지가 수집 용량 제한을 초과했습니다."
        else:
            code, message = "ARTICLE_FETCH_FAILED", "기사 본문을 가져오지 못했습니다."
            retryable = fetch_stage == "download" or isinstance(exc, (OSError, URLError))
    elif stage == "input_file":
        if isinstance(exc, json.JSONDecodeError):
            code, message = "INPUT_JSON_INVALID", "입력 파일의 JSON 형식이 올바르지 않습니다."
        elif isinstance(exc, UnicodeError):
            code, message = "INPUT_ENCODING_INVALID", "입력 JSON 파일을 UTF-8로 저장해 주세요."
        elif isinstance(exc, FileNotFoundError):
            code, message = "INPUT_FILE_NOT_FOUND", "입력 JSON 파일을 찾지 못했습니다."
        else:
            code, message = "INPUT_FILE_UNREADABLE", "입력 JSON 파일을 읽지 못했습니다."
    elif stage in {"output_save", "cache_save"}:
        code = "OUTPUT_WRITE_FAILED" if stage == "output_save" else "CACHE_WRITE_FAILED"
        message = ("분석 결과 파일을 저장하지 못했습니다." if stage == "output_save" else
                   "분석 결과는 반환했지만 캐시 파일을 저장하지 못했습니다.")
    elif stage == "input_validation":
        code, message = "INVALID_INPUT", "기사 URL 또는 실행 옵션이 올바르지 않습니다."
    elif stage == "configuration":
        code, message = "CONFIGURATION_ERROR", "API 설정 또는 실행 환경을 확인해 주세요."
    elif http_status == 429:
        code, message = "MODEL_RATE_LIMITED", "모델 API 호출량 제한에 도달했습니다. 잠시 후 재시도해 주세요."
    elif http_status:
        code, message = "MODEL_HTTP_ERROR", f"모델 API 호출이 실패했습니다 (HTTP {http_status})."
    elif timeout:
        code, message = "MODEL_TIMEOUT", "모델 API 응답 시간이 초과됐습니다."
    elif type(exc).__name__ == "ModelUnavailableError":
        code, message, retryable = "MODEL_UNAVAILABLE", "모델 API가 일시적으로 응답하지 않습니다.", True
    elif type(exc).__name__ == "ModelResponseError":
        code, message = "ANALYSIS_RESPONSE_INVALID", "분석 응답의 형식 또는 근거 검증에 실패했습니다."
    else:
        code, message = "PROCESSING_FAILED", "해당 처리 단계에서 오류가 발생했습니다."
    record = {"stage": stage, "code": code, "message": message, "retryable": bool(retryable),
              "error_type": type(exc).__name__, **context}
    if http_status is not None:
        record["http_status"] = http_status
    return record


def add_result_error(result: dict, error: dict, *, fatal: bool = False) -> dict:
    """Keep available data while distinguishing an unresolved failure from no matches."""
    errors = result.setdefault("errors", [])
    if error not in errors:
        errors.append(error)
    if result.get("execution_status") != "failed":
        result["execution_status"] = "failed" if fatal else "partial"
    return result


def resolve_stage_errors(result: dict, stage: str) -> dict:
    """Move a recovered stage's old errors out of the active error list."""
    recovered = [error for error in result.get("errors", []) if error["stage"] == stage]
    if recovered:
        result["errors"] = [error for error in result["errors"] if error["stage"] != stage]
        result.setdefault("resolved_errors", []).extend(recovered)
        if result.get("execution_status") != "failed":
            result["execution_status"] = "partial" if result["errors"] else "success"
    return result


def make_failure_result(error: dict, *, url: str = "") -> dict:
    """Return the same top-level fields when processing cannot start."""
    return {"completed_stage": 0, "status": "failed", "execution_status": "failed", "errors": [error],
            "source_article": {"url": url, "title": "", "body": "", "publisher": "", "published_at": ""},
            "source_analysis": None, "candidate_ranking": None, "selection": None, "rubric_analysis": None,
            "search": {"queries": [], "metadata_candidates": [], "candidates": [], "fetch_failures": [],
                       "discovered_count": 0, "fetch_attempt_count": 0, "search_status": "not_run", "errors": []},
            "comparison_status": "unavailable", "warnings": []}


def write_json_atomic(path: Path, value: dict, *, indent: int | None = None) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Keep the temporary filename short even for long cache snapshot names.
    temporary = path.with_name(".write-" + uuid4().hex + ".tmp")
    try:
        temporary.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, allow_nan=False, indent=indent), encoding="utf-8")
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def save_analysis_result(result: dict, path: str | Path) -> dict:
    """Return analysis even if writing the requested output file fails."""
    try:
        outgoing = {**result, "errors": list(result.get("errors", []))}
        if "resolved_errors" in result:
            outgoing["resolved_errors"] = list(result["resolved_errors"])
        resolve_stage_errors(outgoing, "output_save")
        write_json_atomic(Path(path), outgoing, indent=2)
        for key in ("execution_status", "errors", "resolved_errors"):
            if key in outgoing:
                result[key] = outgoing[key]
    except (OSError, ValueError, TypeError) as exc:
        add_result_error(result, make_error_record(exc, stage="output_save", path=str(path)))
    return result


def load_discovery_result(path: str | Path) -> dict:
    """Read UTF-8 JSON with or without BOM, or return a structured input error."""
    try:
        result = json.loads(Path(path).read_text(encoding="utf-8-sig"))
        if not isinstance(result, dict):
            raise OperationError("입력 파일에는 기사 분석 결과 객체가 필요합니다.", code="INPUT_JSON_INVALID")
        return result
    except (OSError, ValueError, RuntimeError, TypeError) as exc:
        return make_failure_result(make_error_record(exc, stage="input_file", path=str(path)))


RELATION_LABELS = {
    "same": "같은 주장", "opposes": "반박", "different_interpretation": "다른 해석",
    "same_issue": "같은 쟁점의 다른 내용", "not_found": "대응 내용을 찾지 못함", "uncertain": "판정 불확실",
}


def format_relation_with_owner(row: dict) -> str:
    """Label the content relation without assigning a quoted stance to an outlet."""
    label = RELATION_LABELS[row["relation"]]
    if row.get("has_multiple_voices"):
        return label + " · 복수 주체의 근거(상세 확인)"
    if row.get("relation_owner") == "attributed_speaker":
        kind = "직접" if row.get("speech_kind") == "direct" else "간접"
        return f"{row.get('speaker') or '발언자 미상'}의 {kind} 인용: {label}"
    if row.get("relation_owner") == "reporter":
        return "기자의 서술: " + label
    if row["relation"] in {"not_found", "uncertain"}:
        return label
    return label + " · 귀속 미확인"


def format_analysis_result(result: dict, *, show_details: bool = False) -> str:
    """Show source wording followed by each comparison; full evidence remains in JSON."""
    analysis, selection = result.get("source_analysis"), result.get("selection") or {}
    state = result.get("execution_status", "partial" if result.get("errors") else "success")
    source, search = result.get("source_article") or {}, result.get("search") or {}
    lines = ["실행 상태: " + {"success": "정상 완료", "partial": "일부 완료", "failed": "실패"}.get(state, state)]
    if source.get("title") or source.get("url"):
        lines.append(f"입력 기사: {source.get('title') or source.get('url')}")
    if state == "failed" or not isinstance(analysis, dict):
        _append_operation_errors(lines, result)
        return "\n".join(lines)
    rubric = result.get("rubric_analysis")
    rows = {r["claim_id"]: r for r in rubric["claim_analyses"]} if rubric else {}
    lines.extend([f"핵심 사건: {analysis['core_event']}",
                  f"핵심 주장 {len(analysis['claims'])}개 · 검색 후보 {len(search.get('metadata_candidates', search.get('candidates', [])))}개"
                  f" · 본문 수집 {len(search.get('candidates', []))}개 · 동일 사건 기사 {len(selection.get('same_event_ids', []))}개"])
    for claim in analysis["claims"]:
        lines.extend(["", "─" * 60, f"{claim['claim_id']}. {claim['claim']}",
                      f"입력 원문: {claim['evidence']['quote']}"])
        if claim["speaker"]:
            lines.append(f"발언자: {claim['speaker']}")
        row = rows.get(claim["claim_id"])
        if row:
            _append_expression(lines, row, "S0", prefix="입력 표현")
        comparison = next((r for r in selection.get("claim_comparisons", []) if r["claim_id"] == claim["claim_id"]), {"articles": []})
        if not comparison["articles"]:
            lines.append("검색·수집 또는 처리 실패로 비교 결과를 제공하지 못했습니다." if
                         result.get("comparison_status") == "unavailable" else "비교 기사를 선택하지 못했습니다.")
        for index, article in enumerate(comparison["articles"], 1):
            key = article["candidate_id"]
            lines.extend(["", f"  {index}) [{key}] {article['title']}"])
            if row:
                coverage = next((r for r in row["information"]["I1"] if r["article_id"] == key), None)
                if coverage is None:
                    # AI-assisted resilience: show only the earlier verified comparison for this article.
                    lines.extend(["     루브릭 근거를 검증하지 못해 I1~I5와 A~G는 미판정입니다.",
                                  f"     앞 단계 관계: {format_relation_with_owner(article)}",
                                  f"     앞 단계 원문: {article['evidence']['quote']}",
                                  f"     링크: {article['url']}"])
                    continue
                evidence = coverage["evidence"] or coverage.get("verification_context", [])
                actual_quote = evidence[0]["quote"] if evidence else (
                    coverage["headline_evidence"]["quote"] if coverage["headline_evidence"] else "대응 원문을 찾지 못함")
                lines.append(f"     다른 기사 원문: {actual_quote}")
                placement = next(r for r in row["information"]["I2"] if r["article_id"] == key)
                position = "/".join(dict.fromkeys(p["position"] for p in placement["positions"]))
                position = position.replace("lead", "리드").replace("body", "본문") or "본문 대응 없음"
                if placement["headline_evidence"]:
                    position = "제목/" + position
                lines.append(f"     정보: {format_relation_with_owner(coverage)} · {position} · 관련 문장 구간 {placement['body_sentence_count']}개")
                if coverage.get("coverage_disagreement"):
                    lines.append("     재확인 필요: 앞 단계에서 찾은 대응 원문과 정보 분석 판정이 불일치합니다.")
                _append_expression(lines, row, key, prefix="     표현")
                if show_details:
                    _append_information_details(lines, row, key)
            else:
                lines.extend([f"     관계: {format_relation_with_owner(article)}",
                              f"     다른 기사 원문: {article['evidence']['quote']}"])
            lines.append(f"     링크: {article['url']}")
        if row:
            missing = [r["article_id"] for r in row["information"]["I1"] if r["presence"] == "not_found"]
            if missing:
                lines.append("  사건 기준 기사에서 대응 내용을 찾지 못함: " + ", ".join(missing) + " (이유 미판정)")
            if show_details:
                lines.append("  입력 기사의 정보 구성:")
                _append_information_details(lines, row, "S0")
    if rubric:
        lines.extend(["", "※ 0~2는 기준 원문에 대한 잠정적 상대 표지성입니다. 0이 절대 중립을 뜻하지 않습니다.",
                      "※ 직접 인용된 말은 I5로 분리하며, P1~P6 제안은 적용하지 않았습니다."])
    _append_operation_errors(lines, result)
    if result.get("warnings"):
        lines.append("\n확인 사항:")
        lines.extend(f"- {warning}" for warning in result["warnings"])
    return "\n".join(lines)


def _append_operation_errors(lines: list[str], result: dict) -> None:
    if not result.get("errors"):
        return
    lines.extend(["", "발생한 오류:"])
    for error in result["errors"]:
        target = error.get("candidate_id") or error.get("query") or error.get("path") or error.get("url")
        suffix = f" · 대상: {target}" if target else ""
        retry = "재시도 가능" if error["retryable"] else "주소·설정·파일 확인 필요"
        lines.append(f"- [{error['stage']}/{error['code']}] {error['message']}{suffix} · {retry}")


def _append_expression(lines: list[str], row: dict, article_id: str, *, prefix: str) -> None:
    if row["expression"]["status"] == "unverified":
        # AI-assisted resilience: a failed model step is not evidence of no expression difference.
        lines.append(f"{prefix}: A~G 근거를 검증하지 못해 미판정 · 차이 없음으로 해석하지 마세요")
        return
    findings = [f for f in row["expression"]["findings"] if f["article_id"] == article_id]
    if not findings:
        references = list(dict.fromkeys(f["baseline_evidence"]["quote"] for f in row["expression"]["findings"]
                                        if f["baseline_article_id"] == article_id and f["baseline_evidence"]))
        text = ("비교 기준 원문: " + ", ".join(f"‘{quote}’" for quote in references) if references else
                "같은 명제의 비교 자료 부족" if row["expression"]["status"] == "no_comparison_pair" else
                "LLM이 A~G 판정 근거를 반환하지 않음 · 표현 차이의 부재를 뜻하지 않음")
        lines.append(f"{prefix}: {text}")
    for finding in findings:
        baseline = finding["baseline_evidence"]
        score = str(finding["markedness"]) if finding["markedness"] is not None else "보류"
        reference = f" ↔ [{finding['baseline_article_id']}] ‘{baseline['quote']}’" if baseline else ""
        lines.append(f"{prefix}: [{finding['category']}] ‘{finding['evidence']['quote']}’{reference} · 표지성 {score}")
        if finding["markedness"] is None:
            reasons = {"mixed_reporter_and_quote_span": "기자 서술과 직접 인용 구간이 섞여 있어 분리 필요",
                       "baseline_mixes_reporter_and_quote": "비교 기준에 기자 서술과 직접 인용이 섞여 있음",
                       "no_equivalent_claim_pair": "같은 명제의 실제 기준 표현을 확정하지 못함",
                       "forecast_and_observed_context_mismatch": "미래 전망 표지와 관측 사실의 비교로, 같은 명제인지 재확인 필요",
                       "baseline_is_direct_quote": "비교 기준이 발언자의 직접 인용임",
                       "voice_or_quote_use_uncertain": "표현의 귀속 또는 따옴표 용도 불확실",
                       "relative_markedness_uncertain": "상대 표지성 판정 불확실",
                       "markedness_justification_missing": "상대 점수에 대한 설명이 없음",
                       "one_sided_usage_not_established": "일방적 용어 사용 여부 미확정"}
            lines.append(f"{prefix} 보류 이유: {reasons.get(finding['reason'], finding['reason'])}")
    if not findings:
        candidates = [p for p in row["expression"].get("surface_differences", [])
                      if p["article_id"] == article_id or p["source_article_id"] == article_id]
        if candidates:
            pair = candidates[0]
            verb_pair = next((p for p in candidates if p.get("source_reporting_verb_candidates")
                              and p.get("comparison_reporting_verb_candidates")), None)
            if verb_pair:
                left = ", ".join(v["quote"] for v in verb_pair["source_reporting_verb_candidates"])
                right = ", ".join(v["quote"] for v in verb_pair["comparison_reporting_verb_candidates"])
                lines.append(f"{prefix} 인용 밖 동사 후보(A·의미와 값 미확정): ‘{left}’ ↔ ‘{right}’")
            lines.append(f"{prefix} 문구 차이 후보(의미·A~G 미확정): "
                         f"[{pair['source_article_id']}] ‘{pair['source_evidence']['quote']}’ ↔ "
                         f"[{pair['article_id']}] ‘{pair['evidence']['quote']}’")
        rejected = [f for f in row["expression"]["excluded"] if f["article_id"] == article_id]
        if rejected:
            reasons = {"outside_annotated_claim_passage": "대응 주장 문맥 밖의 표현",
                       "direct_quote_recorded_in_I5": "발언자의 직접 인용(I5)",
                       "proper_or_official_name": "고유명사·공식 명칭"}
            # AI-assisted integration: keep this formatter compatible with Python 3.11 runtimes.
            lines.append(f"{prefix} 제외: " + ", ".join(dict.fromkeys(reasons.get(f['reason'], f['reason']) for f in rejected)))
    invalid = [f for f in row["expression"].get("invalid_findings", []) if f["article_id"] == article_id]
    if invalid:
        lines.append(f"{prefix}: 원문·비교 기준 검증 실패 {len(invalid)}건은 판정에서 제외")
    quotes = next(r for r in row["information"]["I5"] if r["article_id"] == article_id)["quote_markers"]
    if quotes:
        lines.append(f"{prefix}: 발언 안의 표지 {len(quotes)}건은 I5에 별도 기록")


def _append_information_details(lines: list[str], row: dict, article_id: str) -> None:
    coverage = next(r for r in row["information"]["I1"] if r["article_id"] == article_id)
    for relation in coverage.get("relation_evidence", []):
        lines.append(f"     관계 근거: {format_relation_with_owner(relation)} — {relation['evidence']['quote']}")
    for code, label in (("I3", "배경"), ("I4", "추측·전망")):
        for item in next(r for r in row["information"][code] if r["article_id"] == article_id)["items"]:
            lines.append(f"     {code} {label}: {item['content']}")
            lines.append(f"       근거: {item['evidence']['quote']}")
            if code == "I4":
                markers = ", ".join(m["quote"] for m in item["speculation_markers"]) or "명시적 표지를 찾지 못함"
                lines.append(f"       추측 표지: {markers}")
    voice_row = next(r for r in row["information"]["I5"] if r["article_id"] == article_id)
    for voice in voice_row["voices"]:
        kind = "직접" if voice["speech_kind"] == "direct" else "간접"
        identity = {"named": "실명", "anonymous": "익명", "unknown": "미상"}[voice["identity_kind"]]
        lines.append(f"     I5 {kind} 인용: {voice['speaker']} ({identity}) — {voice['speech_evidence']['quote']}")
    for marker in voice_row["quote_markers"]:
        lines.append(f"       발언 표지 [{marker['category']}]: {marker['evidence']['quote']} (매체 A~G에서 제외)")
