"""Fetch and verify metadata-selected candidates, refilling rejected slots."""

from __future__ import annotations

from typing import Any, Callable

from .article_search import fetch_candidate_articles
from .llm_analysis import COMPARABLE_RELATIONS, prioritize_diverse_candidates, verify_candidate_articles
from .model_client import ModelResponseError
from .result_output import make_error_record


def select_verified_comparisons(source: dict, analysis: dict, metadata_search: dict, ranking: dict, *,
                                max_per_claim: int = 3, event_baseline_limit: int = 5,
                                verification_batch_size: int = 3, client: Any = None,
                                progress: Callable[[str], None] | None = None,
                                should_stop: Callable[[], bool] | None = None) -> dict:
    """Confirm ranked candidates and return the existing search/selection structures.

    Each article body is downloaded and verified at most once in this run.
    Event baseline slots are independent of whether an article mentions a claim.
    A stop checkpoint returns verified work with deferred candidates recorded.
    """
    for value, lower, upper, name in (
        (max_per_claim, 1, 3, "max_per_claim"), (event_baseline_limit, 0, 20, "event_baseline_limit"),
        (verification_batch_size, 1, 5, "verification_batch_size"),
    ):
        if type(value) is not int or not lower <= value <= upper:
            raise ValueError(f"{name}은 {lower}~{upper}의 정수여야 합니다.")
    metadata = metadata_search["metadata_candidates"]
    metadata_map = {m["candidate_id"]: m for m in metadata}
    claim_map = {c["claim_id"]: c for c in analysis["claims"]}
    ranks = {r["claim_id"]: r for r in ranking["claim_candidates"]}
    if (len(metadata_map) != len(metadata) or len(claim_map) != len(analysis["claims"])
            or len(ranks) != len(ranking["claim_candidates"]) or set(ranks) != set(claim_map)):
        raise ValueError("후보·주장 ID 또는 주장별 후보 순위가 올바르지 않습니다.")
    queues = {}
    for key, rank in ranks.items():
        queue = rank["selected_candidate_ids"] + rank["reserve_candidate_ids"]
        if (len(rank["selected_candidate_ids"]) > max_per_claim or len(queue) != len(set(queue))
                or not set(queue) <= set(metadata_map)):
            raise ValueError("주장별 최초·예비 후보 ID가 올바르지 않습니다.")
        queues[key] = queue
    accepted = {key: [] for key in claim_map}
    baseline_queue = [m["candidate_id"] for m in metadata if m["discovered_by_event"]]
    baseline_position, baseline_ids = 0, []
    articles, assessed, failed = {}, {}, {}
    verification_failed, verification_deferred, verification_errors = set(), set(), []
    attempted_ids, claim_attempts, baseline_attempts, rounds = [], [], [], []
    notify = progress or (lambda _: None)
    verification_calls = 0

    def mark_unverified(ids: list[str], reason: str, exc: Exception) -> None:
        for key in ids:
            if key in verification_failed or key in verification_deferred:
                continue
            (verification_failed if reason == "verification_failed" else verification_deferred).add(key)
            verification_errors.append(make_error_record(exc, stage="candidate_verification", candidate_id=key))
            if key in articles:
                assessed[key] = {"candidate_id": key, "event_relation": "uncertain",
                                 "event_evidence": None, "claim_matches": [],
                                 "verification_status": "unverified",
                                 "validation_warnings": [{"claim_id": None, "reason": reason}]}

    while claim_map:
        round_number = len(rounds) + 1
        reused, targets, diversity_notes = {}, {}, {}
        for key, queue in queues.items():
            targets[key] = []
            picked = [a["candidate_id"] for a in accepted[key]]
            if len(picked) >= max_per_claim:
                continue
            matches = {}
            for candidate_id, assessment in assessed.items():
                match = next((m for m in assessment["claim_matches"] if m["claim_id"] == key), None)
                if (assessment["event_relation"] == "same_event" and match
                        and match["relation"] in COMPARABLE_RELATIONS and match["evidence"] is not None):
                    matches[candidate_id] = match
            # Compare verified reuse with unread reserves under the same rule.
            # Already verified identical wording must not fill every vacancy first.
            eligible = [candidate_id for candidate_id in queue if candidate_id not in failed
                        and (candidate_id not in assessed or candidate_id in matches)]
            eligible.extend(m["candidate_id"] for m in metadata
                            if m["candidate_id"] in matches and m["candidate_id"] not in eligible)
            profile_map = {row["candidate_id"]: row for row in ranks[key].get("candidate_profiles", [])}
            profiles = []
            for candidate_id in dict.fromkeys(picked + eligible):
                profile = profile_map.get(candidate_id, {"candidate_id": candidate_id,
                                                         "relevance": "direct", "focus": ""})
                if candidate_id in matches and profile["relevance"] in ("context", "unrelated"):
                    profile = {**profile, "relevance": "same_issue"}
                profiles.append(profile)
            diverse = prioritize_diverse_candidates(metadata, profiles, selected_candidate_ids=picked)
            next_ids = diverse["ranked_candidate_ids"][:max_per_claim - len(picked)]
            diversity_notes[key] = diverse["selection_notes"][:len(next_ids)]
            for candidate_id in next_ids:
                if candidate_id not in matches:
                    targets[key].append(candidate_id)
                    continue
                match, article = matches[candidate_id], articles[candidate_id]
                accepted[key].append({"candidate_id": candidate_id, "title": article["title"], "url": article["url"],
                                      "relation": match["relation"], "evidence": match["evidence"],
                                      **{field: match.get(field, [] if field == "relation_evidence" else
                                                         False if field == "has_multiple_voices" else
                                                         "" if field == "speaker" else "uncertain")
                                         for field in ("relation_evidence", "relation_owner", "speaker", "speech_kind", "has_multiple_voices")}})
                reused.setdefault(key, []).append(candidate_id)
                claim_attempts.append({"round": round_number, "claim_id": key, "candidate_id": candidate_id,
                                       "status": "accepted", "reason": "verified",
                                       "selection_basis": "verified_body_reuse"})
        if reused:
            notify(f"4단계 {round_number}차: 표현 다양성을 고려해 검증된 기사 재사용 {reused}")
        baseline_stop = min(baseline_position + event_baseline_limit - len(baseline_ids), len(baseline_queue))
        baseline_targets = baseline_queue[baseline_position:baseline_stop]
        baseline_position = baseline_stop
        proposed = list(dict.fromkeys([key for ids in targets.values() for key in ids] + baseline_targets))
        if not proposed:
            if reused:
                rounds.append({"round": round_number, "claim_targets": targets, "event_baseline_targets": [],
                               "fetched_ids": [], "verified_ids": [], "unverified_ids": [],
                               "deferred_ids": [], "reused_verified_candidates": reused,
                               "diversity_selection_notes": diversity_notes})
            break
        stop_after_round = bool(should_stop and should_stop())
        pending = [] if stop_after_round else [key for key in proposed if key not in articles and key not in failed]
        if stop_after_round:
            deferred = [key for key in proposed if key not in assessed and key not in failed]
            mark_unverified(deferred, "verification_deferred", TimeoutError())
            if not deferred:
                verification_errors.append(make_error_record(TimeoutError(), stage="candidate_verification"))
        elif pending:
            notify(f"4단계 {round_number}차: 선택 후보 {len(pending)}개 본문 수집")
            fetched = fetch_candidate_articles([metadata_map[key] for key in pending])
            attempted_ids.extend(pending)
            articles.update({a["candidate_id"]: a for a in fetched["candidates"]})
            failed.update({f["candidate_id"]: f for f in fetched["fetch_failures"]})
            if set(pending) != {a["candidate_id"] for a in fetched["candidates"]} | {
                f["candidate_id"] for f in fetched["fetch_failures"]
            }:
                raise RuntimeError("일부 후보의 본문 수집 결과 또는 실패 기록이 빠졌습니다.")
        unchecked = [key for key in proposed if key in articles and key not in assessed]
        for start in range(0, len(unchecked), verification_batch_size):
            if stop_after_round:
                break
            if should_stop and should_stop():
                mark_unverified(unchecked[start:], "verification_deferred", TimeoutError())
                stop_after_round = True
                break
            batch_ids = unchecked[start:start + verification_batch_size]
            notify(f"4단계 {round_number}차: 사건·주장 검증 [{', '.join(batch_ids)}]")
            verification_calls += 1
            try:
                result = verify_candidate_articles(source, analysis, [articles[key] for key in batch_ids], client=client)
                batch_assessments = {a["candidate_id"]: a for a in result["assessments"]}
                if set(batch_assessments) != set(batch_ids) or len(batch_assessments) != len(result["assessments"]):
                    raise ModelResponseError("선택 후보의 검증 결과가 누락되었거나 중복되었습니다.")
            except (RuntimeError, TimeoutError) as exc:
                # A later failed batch must not erase already grounded comparisons.
                # Placeholders are explicitly unverified and cannot enter matches.
                mark_unverified(batch_ids, "verification_failed", exc)
                continue
            assessed.update(batch_assessments)
        for claim_id, ids in targets.items():
            for key in ids:
                assessment = assessed.get(key)
                match = next((m for m in assessment["claim_matches"] if m["claim_id"] == claim_id), None) if assessment else None
                reason = ("fetch_failed" if key in failed else
                          "verification_failed" if key in verification_failed else
                          "verification_deferred" if key in verification_deferred else
                          "unverified" if assessment is None else
                          "different_event" if assessment["event_relation"] == "different_event" else
                          "uncertain_event" if assessment["event_relation"] == "uncertain" else
                          "no_claim_match" if not match or match["relation"] == "not_found" else
                          "uncertain_claim" if match["relation"] == "uncertain" else "verified")
                is_accepted = (reason == "verified" and match["relation"] in COMPARABLE_RELATIONS
                               and match["evidence"] is not None)
                claim_attempts.append({"round": round_number, "claim_id": claim_id, "candidate_id": key,
                                       "status": "accepted" if is_accepted else "rejected", "reason": reason,
                                       "selection_basis": "initial_metadata_choice" if key in ranks[claim_id]["selected_candidate_ids"] else "ranked_reserve"})
                if is_accepted:
                    article = articles[key]
                    accepted[claim_id].append({"candidate_id": key, "title": article["title"], "url": article["url"],
                                               "relation": match["relation"], "evidence": match["evidence"],
                                               **{field: match.get(field, [] if field == "relation_evidence" else
                                                                  False if field == "has_multiple_voices" else
                                                                  "" if field == "speaker" else "uncertain")
                                                  for field in ("relation_evidence", "relation_owner", "speaker", "speech_kind", "has_multiple_voices")}})
        for key in baseline_targets:
            assessment = assessed.get(key)
            is_accepted = bool(assessment and assessment["event_relation"] == "same_event")
            baseline_attempts.append({"round": round_number, "candidate_id": key,
                                      "status": "accepted" if is_accepted else "rejected",
                                      "reason": "same_event" if is_accepted else
                                                "fetch_failed" if key in failed else
                                                "verification_failed" if key in verification_failed else
                                                "verification_deferred" if key in verification_deferred else
                                                "unverified" if assessment is None else
                                                assessment["event_relation"]})
            if is_accepted:
                baseline_ids.append(key)
        rounds.append({"round": round_number, "claim_targets": targets, "event_baseline_targets": baseline_targets,
                       "fetched_ids": pending,
                       "verified_ids": [key for key in unchecked
                                        if key not in verification_failed and key not in verification_deferred],
                       "unverified_ids": [key for key in unchecked
                                          if key in verification_failed or key in verification_deferred],
                       "deferred_ids": [key for key in proposed if key in verification_deferred],
                       "reused_verified_candidates": reused,
                       "diversity_selection_notes": diversity_notes})
        if stop_after_round:
            break
    ordered_assessments = [assessed[m["candidate_id"]] for m in metadata if m["candidate_id"] in assessed]
    selection = {
        "assessments": ordered_assessments,
        "same_event_ids": [a["candidate_id"] for a in ordered_assessments if a["event_relation"] == "same_event"],
        "event_baseline_ids": baseline_ids,
        "claim_comparisons": [{"claim_id": key, "claim": claim["claim"], "articles": accepted[key]}
                              for key, claim in claim_map.items()],
        "claim_attempts": claim_attempts, "event_baseline_attempts": baseline_attempts,
        "verification_rounds": rounds, "verification_llm_calls": verification_calls,
        "exhausted_claim_ids": [key for key in claim_map if len(accepted[key]) < max_per_claim],
    }
    search = {**metadata_search, "errors": list(metadata_search.get("errors", [])) + verification_errors,
              "candidates": [articles[key] for key in attempted_ids if key in articles],
              "fetch_failures": [failed[key] for key in attempted_ids if key in failed],
              "fetch_attempt_count": len(attempted_ids),
              "unused_candidate_ids": [m["candidate_id"] for m in metadata if m["candidate_id"] not in attempted_ids]}
    return {"search": search, "selection": selection}
