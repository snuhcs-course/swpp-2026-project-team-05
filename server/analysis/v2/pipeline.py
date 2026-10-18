"""Search metadata, choose candidates, then fetch/verify/refill and analyze."""

from __future__ import annotations

import ast
import json
import os
import tempfile
import re
from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from typing import Any, Callable

from .article_fetch import validate_article_url
from .article_input import prepare_article
from .article_search import search_candidate_metadata
from .comparison_selection import select_verified_comparisons
from .llm_analysis import analyze_source_article, rank_comparison_candidates
from .model_client import (GeminiJSONClient, SnapshotJSONClient, canonical_json,
                          get_generation_settings, get_missing_configuration)
from .rubric_analysis import analyze_selected_articles
from .result_output import (OperationError, make_error_record, add_result_error,
                           make_failure_result, write_json_atomic, resolve_stage_errors)


PIPELINE_VERSION = "metadata-select-verify-attribution-diversity-errors-v4"
PROMPT_VERSION = "grounded-expression-pairs-v3"
RUBRIC_VERSION = "0.2.1"
# AI-assisted integration: use a writable cache path in read-only deployments.
DEFAULT_CACHE_DIR = Path(os.getenv("FRAMELESS_ANALYSIS_CACHE_DIR") or (Path(tempfile.gettempdir()) / "frameless-analysis-v2"))


def _digest(value: Any) -> str:
    return sha256(canonical_json(value).encode("utf-8")).hexdigest()


def _analysis_versions() -> dict:
    """Invalidate saved results when analysis logic changes, without hashing API keys."""
    directory = Path(__file__).resolve().parent
    logic = {}
    for name in ("pipeline.py", "llm_analysis.py", "rubric_analysis.py", "comparison_selection.py",
                 "article_input.py", "article_fetch.py", "article_search.py", "model_client.py", "result_output.py"):
        source = (directory / name).read_text(encoding="utf-8-sig")
        if name == "model_client.py":
            tree = ast.parse(source)
            tree.body = [node for node in tree.body if not (
                isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id in {
                    "GEMINI_API_KEY", "NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET"} for t in node.targets))]
            source = ast.unparse(tree)
        logic[name] = sha256(source.encode("utf-8")).hexdigest()
    return {"pipeline": PIPELINE_VERSION, "prompt": PROMPT_VERSION, "rubric": RUBRIC_VERSION,
            "logic_sha256": _digest(logic)}


def _atomic_json(path: Path, value: dict) -> None:
    write_json_atomic(path, value)


def _read_snapshot(path: Path) -> dict:
    bundle = json.loads(path.read_text(encoding="utf-8-sig"))
    payload = {key: bundle[key] for key in ("settings", "versions", "requests", "result")}
    if bundle.get("format_version") != 1 or bundle.get("payload_sha256") != _digest(payload):
        raise ValueError("저장된 분석 스냅샷의 형식이나 검증 값이 올바르지 않습니다.")
    if bundle["result"].get("completed_stage") != 5:
        raise ValueError("완료된 5단계 스냅샷이 필요합니다.")
    return bundle


def _load_cached_analysis(directory: Path, key: str) -> dict | None:
    try:
        index = json.loads((directory / (key + ".latest.json")).read_text(encoding="utf-8-sig"))
        filename = index["snapshot_file"]
        if not re.fullmatch(re.escape(key) + r"\.[0-9a-f]{64}\.json", filename):
            return None
        bundle = _read_snapshot(directory / filename)
        return bundle if bundle.get("request_key") == key else None
    except (OSError, ValueError, KeyError, TypeError):
        return None


def _validate_pipeline_options(options: dict) -> None:
    for name, lower, upper in (
        ("max_claims", 1, 5), ("max_candidates", 1, 100), ("max_per_claim", 1, 3),
        ("display_per_query", 1, 100), ("event_baseline_limit", 0, 20), ("verification_batch_size", 1, 5),
    ):
        value = options[name]
        if type(value) is not int or not lower <= value <= upper:
            raise ValueError(f"{name}은 {lower}~{upper}의 정수여야 합니다.")


def _initial_result(url: str) -> dict:
    result = make_failure_result({}, url=url if isinstance(url, str) else "")
    result.update(status="pending", execution_status="success", errors=[])
    return result


def _record_failure(result: dict, exc: Exception, stage: str) -> dict:
    # A rubric or snapshot failure cannot erase already verified discovery.
    # Android can still show the grounded comparison with a partial notice.
    comparison_status = result.get("comparison_status")
    result["status"] = stage + "_failed"
    if not (stage in {"rubric_analysis", "cache_save"}
            and result.get("completed_stage", 0) >= 4
            and comparison_status in {"available", "no_matches", "not_requested"}):
        result["comparison_status"] = "unavailable"
    return add_result_error(result, make_error_record(exc, stage=stage),
                            fatal=not isinstance(result.get("source_analysis"), dict))


def _finish_discovery(result: dict) -> dict:
    collection, selection = result["search"], result["selection"]
    for error in collection.get("errors", []) + collection.get("fetch_failures", []):
        if not isinstance(error, dict) or not {"stage", "code", "message", "retryable"} <= error.keys():
            context = {key: error[key] for key in ("candidate_id", "url", "query") if isinstance(error, dict) and key in error}
            error = make_error_record(OperationError("이전 실행에서 관련 기사 수집·검색에 실패했습니다.",
                                      code="ARTICLE_FETCH_FAILED" if "candidate_id" in context else "SEARCH_FAILED"),
                                      stage="candidate_fetch" if "candidate_id" in context else "search", **context)
        add_result_error(result, error)
    for query in collection.get("queries", []):
        if query.get("status") == "search_failed" and not any(
            e.get("stage") == "search" and e.get("query") == query.get("query") for e in result["errors"]
        ):
            add_result_error(result, make_error_record(OperationError("이전 실행에서 해당 검색어의 검색에 실패했습니다.",
                             code="SEARCH_FAILED"), stage="search", query=query.get("query", "")))
    result["execution_status"] = "partial" if result["errors"] else "success"
    if not result["source_analysis"]["claims"]:
        result["status"], result["comparison_status"] = "no_claims", "not_requested"
    elif collection.get("search_status") == "failed":
        result["status"], result["comparison_status"] = "search_failed", "unavailable"
    elif not collection["candidates"]:
        result["status"] = "no_candidates"
        result["comparison_status"] = "unavailable" if result["errors"] else "no_matches"
    elif not selection["same_event_ids"]:
        result["status"] = "no_same_event"
        result["comparison_status"] = "unavailable" if result["errors"] else "no_matches"
    else:
        result["status"] = "ready"
        result["comparison_status"] = ("available" if any(r["articles"] for r in selection["claim_comparisons"])
                                       else "unavailable" if result["errors"] else "no_matches")
    return result


def run_discovery_pipeline(url: str, *, max_claims: int = 5, max_candidates: int = 20,
                           max_per_claim: int = 3, display_per_query: int = 10,
                           event_baseline_limit: int = 5, verification_batch_size: int = 3,
                           client: Any = None, progress: Callable[[str], None] | None = None,
                           _prepared_source: dict | None = None) -> dict:
    """Return discovery or available data with structured operation errors."""
    result, stage, owned = _initial_result(url), "input_validation", False
    notify = progress or (lambda message: None)
    try:
        url = validate_article_url(url)
        _validate_pipeline_options({"max_claims": max_claims, "max_candidates": max_candidates,
                                   "max_per_claim": max_per_claim, "display_per_query": display_per_query,
                                   "event_baseline_limit": event_baseline_limit,
                                   "verification_batch_size": verification_batch_size})
        stage = "source_fetch"
        if _prepared_source is None:
            notify("1/4 입력 기사 수집·문장 번호 부여")
            source = prepare_article(url)
        else:
            source = _prepared_source
        result.update(source_article=source, completed_stage=1)
        stage = "configuration"
        if client is None:
            if "GEMINI_API_KEY" in get_missing_configuration():
                raise OperationError("Gemini API 키를 설정해 주세요.", code="GEMINI_CONFIGURATION_MISSING")
            client = GeminiJSONClient()
            owned = True
        stage = "source_analysis"
        notify("2/4 LLM 호출 1: 핵심 사건·주장·검색어 생성")
        analysis = analyze_source_article(source, max_claims=max_claims, client=client)
        result.update(source_analysis=analysis, completed_stage=2)
        metadata_search = {"queries": [], "metadata_candidates": [], "discovered_count": 0,
                           "errors": [], "search_status": "not_run"}
        stage = "search"
        if analysis["claims"]:
            notify("3/4 네이버 검색·URL 중복 제거 (제목·설명만 수집)")
            metadata_search = search_candidate_metadata(source, analysis, max_candidates=max_candidates,
                                                         display_per_query=display_per_query)
        result["search"].update(metadata_search)
        result["completed_stage"] = 3
        for error in metadata_search.get("errors", []):
            add_result_error(result, error)
        stage = "candidate_selection"
        if metadata_search["metadata_candidates"]:
            notify("4/4 LLM 후보 분류·Python 선택: 주장 관련성·강조점·표현 다양성 (본문 검증 전)")
        ranking = rank_comparison_candidates(source, analysis, metadata_search["metadata_candidates"],
                                              max_per_claim=max_per_claim, client=client)
        result["candidate_ranking"] = ranking
        stage = "candidate_collection"
        verified = select_verified_comparisons(source, analysis, metadata_search, ranking,
                                               max_per_claim=max_per_claim, event_baseline_limit=event_baseline_limit,
                                               verification_batch_size=verification_batch_size, client=client, progress=notify)
        collection, selection = verified["search"], verified["selection"]
        warnings = []
        if any(query["status"] == "search_failed" for query in collection["queries"]):
            warnings.append("일부 검색어의 검색에 실패했습니다. queries의 status를 확인하세요.")
        if collection["fetch_failures"]:
            warnings.append("일부 후보 본문을 수집하지 못했습니다. fetch_failures를 확인하세요.")
        if analysis["claims"] and not selection["event_baseline_ids"]:
            warnings.append("사건 검색에서 확보한 동일 사건 기사가 없습니다. 포함·누락 비교 자료가 불충분합니다.")
        if any(row.get("validation_warnings") for row in selection["assessments"]):
            warnings.append("일부 후보의 판정 근거가 부족해 미확인으로 처리했습니다. assessments의 validation_warnings를 확인하세요.")
        if any(row.get("validation_warnings") for row in ranking["claim_candidates"]):
            warnings.append("일부 후보의 분류·강조점이 미확인입니다. 미확인 강조점은 다양성 가점에서 제외하고 후보는 보존합니다. candidate_ranking의 validation_warnings를 확인하세요.")
        result.update(completed_stage=4, search=collection, selection=selection,
                      pipeline_strategy="metadata_select_fetch_verify_refill", warnings=warnings)
        return _finish_discovery(result)
    except Exception as exc:
        return _record_failure(result, exc, stage)
    finally:
        if owned:
            try:
                client.close()
            except Exception as exc:
                add_result_error(result, make_error_record(exc, stage="client_close"))


def complete_rubric_analysis(discovery: dict, *, client: Any = None,
                            progress: Callable[[str], None] | None = None) -> dict:
    """Resume a stage-4 result and keep discovery if a later operation fails."""
    if not isinstance(discovery, dict):
        return make_failure_result(make_error_record(
            OperationError("완료된 4단계 기사 분석 JSON이 필요합니다.", code="INPUT_RESULT_INVALID"), stage="input_validation"))
    result = deepcopy(discovery)
    if (not isinstance(result.get("errors", []), list) or not isinstance(result.get("warnings", []), list)
            or any(not isinstance(e, dict) or not {"stage", "code", "message", "retryable"} <= e.keys()
                   for e in result.get("errors", []))):
        return make_failure_result(make_error_record(
            OperationError("입력 분석 JSON의 오류·안내 목록 형식이 올바르지 않습니다.", code="INPUT_RESULT_INVALID"),
            stage="input_validation"))
    result.setdefault("errors", [])
    result.setdefault("warnings", [])
    result.setdefault("execution_status", "partial" if result["errors"] else "success")
    if result["execution_status"] == "failed":
        return result
    valid = all(isinstance(result.get(key), dict) for key in ("source_article", "source_analysis", "search", "selection"))
    if valid:
        analysis = result["source_analysis"]
        valid = isinstance(analysis.get("claims"), list) and isinstance(analysis.get("core_event"), str)
        valid = valid and all(isinstance(c, dict) and all(key in c for key in
                              ("claim_id", "claim", "claim_kind", "speaker", "evidence"))
                              and isinstance(c["evidence"], dict) and isinstance(c["evidence"].get("quote"), str)
                              for c in analysis.get("claims", []))
        valid = valid and all(isinstance(result["selection"].get(key), list) for key in
                              ("claim_comparisons", "same_event_ids", "event_baseline_ids", "assessments"))
        valid = valid and isinstance(result["search"].get("candidates"), list)
    if not valid:
        return make_failure_result(make_error_record(
            OperationError("완료된 4단계 기사 분석 JSON이 필요합니다.", code="INPUT_RESULT_INVALID"), stage="input_validation"))
    try:
        rubric = analyze_selected_articles(discovery, client=client, progress=progress)
        result.update(completed_stage=5, rubric_analysis=rubric,
                      warnings=list(result["warnings"]) + rubric["warnings"])
        resolve_stage_errors(result, "rubric_analysis")
        return _finish_discovery(result)
    except Exception as exc:
        result["rubric_analysis"] = None
        return _record_failure(result, exc, "rubric_analysis")


def run_analysis_pipeline(url: str, *, max_claims: int = 5, max_candidates: int = 20,
                          max_per_claim: int = 3, display_per_query: int = 10,
                          event_baseline_limit: int = 5, verification_batch_size: int = 3,
                          client: Any = None, progress: Callable[[str], None] | None = None,
                          use_cache: bool = True, force_refresh: bool = False,
                          cache_dir: str | Path | None = None) -> dict:
    """Return success, partial data or a structured failure for every operation."""
    result, stage, owned = _initial_result(url), "input_validation", False
    options = {"max_claims": max_claims, "max_candidates": max_candidates, "max_per_claim": max_per_claim,
               "display_per_query": display_per_query, "event_baseline_limit": event_baseline_limit,
               "verification_batch_size": verification_batch_size}
    notify = progress or (lambda _: None)
    try:
        url = validate_article_url(url)
        _validate_pipeline_options(options)
        if type(use_cache) is not bool or type(force_refresh) is not bool:
            raise ValueError("use_cache·force_refresh must be bool")
        stage = "source_fetch"
        notify("1/4 입력 기사 수집·문장 번호 부여")
        source = prepare_article(url)
        result.update(source_article=source, completed_stage=1)
        stage = "configuration"
        settings = (get_generation_settings() if client is None else get_generation_settings(
            getattr(client, "model", "injected:" + type(client).__qualname__),
            temperature=getattr(client, "temperature", None), seed=getattr(client, "seed", None)))
        versions = _analysis_versions()
        source_hash = _digest(source)
        key = _digest({"source_sha256": source_hash, "settings": settings, "versions": versions, "options": options})
        directory = Path(cache_dir) if cache_dir is not None else DEFAULT_CACHE_DIR
        if use_cache and not force_refresh:
            bundle = _load_cached_analysis(directory, key)
            if bundle is not None:
                result = deepcopy(bundle["result"])
                result["reproducibility"]["cache_hit"] = True
                result["reproducibility"]["execution"] = {"model_calls": 0, "task_counts": {}, "sdk_retries_included": False}
                notify("저장된 동일 입력·설정의 분석 재사용 (LLM·검색 호출 없음)")
                return result
        if client is None:
            if "GEMINI_API_KEY" in get_missing_configuration():
                raise OperationError("Gemini API 키를 설정해 주세요.", code="GEMINI_CONFIGURATION_MISSING")
            client = GeminiJSONClient(**settings)
            owned = True
        recorder = SnapshotJSONClient(client, settings)
        stage = "discovery"
        discovery = run_discovery_pipeline(url, **options, client=recorder, progress=progress,
                                           _prepared_source=source)
        result = discovery
        if discovery["completed_stage"] == 4:
            stage = "rubric_analysis"
            result = complete_rubric_analysis(discovery, client=recorder, progress=progress)
        stage = "cache_save"
        execution = {"model_calls": len(recorder.records),
                     "task_counts": dict(Counter(r["name"] for r in recorder.records)), "sdk_retries_included": False}
        snapshot_id = _digest({"settings": settings, "versions": versions,
                               "requests": recorder.records, "result": result})
        snapshot_path = directory / (key + "." + snapshot_id + ".json")
        result["reproducibility"] = {
            "settings": settings, "versions": versions, "source_sha256": source_hash,
            "candidate_snapshot_sha256": _digest(result["search"]), "request_key": key,
            "snapshot_id": snapshot_id, "snapshot_path": str(snapshot_path.resolve()) if use_cache else None,
            "created_at": datetime.now(timezone.utc).isoformat(), "cache_hit": False,
            "original_execution": execution, "execution": dict(execution),
        }
        if use_cache:
            partial = result["execution_status"] != "success"
            if partial:
                result["reproducibility"]["cache_reuse_disabled_reason"] = "partial_operation_failure"
            payload = {"settings": settings, "versions": versions, "requests": recorder.records, "result": result}
            try:
                _atomic_json(snapshot_path, {"format_version": 1, "request_key": key, "snapshot_id": snapshot_id,
                                             **payload, "payload_sha256": _digest(payload)})
                if not partial:
                    _atomic_json(directory / (key + ".latest.json"), {"snapshot_file": snapshot_path.name})
            except (OSError, ValueError, TypeError) as exc:
                add_result_error(result, make_error_record(exc, stage="cache_save", path=str(snapshot_path)))
                result["warnings"].append("분석은 완료했지만 로컬 스냅샷을 저장하지 못했습니다.")
                result["reproducibility"]["snapshot_path"] = None
        return result
    except Exception as exc:
        return _record_failure(result, exc, stage)
    finally:
        if owned:
            try:
                client.close()
            except Exception as exc:
                add_result_error(result, make_error_record(exc, stage="client_close"))


def evaluate_analysis_snapshot(snapshot_path: str | Path, *, client: Any = None,
                               temperature: float | None = None, seed: int | None = None,
                               progress: Callable[[str], None] | None = None) -> dict:
    """Repeat each saved exact model input without fetching/searching or using result cache."""
    from jsonschema import ValidationError, validate
    bundle = _read_snapshot(Path(snapshot_path))
    original = bundle["settings"]
    settings = get_generation_settings(original["model"],
        temperature=original["temperature"] if temperature is None else temperature,
        seed=original["seed"] if seed is None else seed)
    owned, notify = client is None, progress or (lambda _: None)
    if client is not None and any(getattr(client, name, settings[name]) != settings[name]
                                  for name in ("model", "temperature", "seed")):
        raise ValueError("평가 클라이언트의 모델·temperature·seed가 지정한 평가 설정과 다릅니다.")
    if owned:
        client = GeminiJSONClient(**settings)
    rows = []
    try:
        for index, record in enumerate(bundle["requests"], 1):
            notify(f"고정 입력 평가 {index}/{len(bundle['requests'])}: {record['name']}")
            response = client.generate_json(**deepcopy(record["request"]))
            try:
                validate(response, record["request"]["schema"])
                schema_valid = True
            except ValidationError:
                schema_valid = False
            rows.append({"request_id": record["request_id"], "name": record["name"],
                         "same_response": response == record["response"], "schema_valid": schema_valid,
                         "response": response})
    finally:
        if owned:
            client.close()
    return {"snapshot_id": bundle["snapshot_id"], "original_settings": original,
            "evaluation_settings": settings, "model_calls": len(rows), "requests": rows,
            "same_response_count": sum(row["same_response"] for row in rows),
            "scope": "identical_saved_model_inputs; schema_and_response_equality_only; no_semantic_accuracy_score"}
