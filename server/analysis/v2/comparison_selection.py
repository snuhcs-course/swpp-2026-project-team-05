"""Fetch and verify metadata-selected candidates, refilling rejected slots."""

from __future__ import annotations

import unicodedata
from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait, wait as wait_for_verification
from contextlib import contextmanager
from typing import Any, Callable

from .article_search import fetch_candidate_articles
from .llm_analysis import COMPARABLE_RELATIONS, prioritize_diverse_candidates, verify_candidate_articles
from .model_client import (MetricsJSONClient, ModelResponseError, ModelResponseTruncatedError,
                           OpenRouterJSONClient, SnapshotJSONClient)
from .result_output import make_error_record


# The first metadata hits are a starting point, not the final quality ranking.
# A bounded later pool can improve body quality and reporting diversity when
# time remains, without making the serverless run unbounded.
MAX_EXTRA_VERIFIED_CANDIDATES = 6
# Candidate verdicts are independent once their bodies are fetched. Three
# concurrent live calls shorten the long-tail wait without changing which
# candidates are checked or how their grounded verdicts are applied.
MAX_PARALLEL_VERIFICATIONS = 3
# A slow verdict must not hold the next wave idle. At most two calls beyond
# the original wave may start before that verdict reveals a 429 or deadline.
MAX_VERIFICATION_SPECULATIVE_CALLS = 2
# Fetch only a few likely next bodies while the first verdicts run. These
# downloads never change candidate order or count as attempted until selected.
MAX_SPECULATIVE_BODY_FETCHES = 3


@contextmanager
def _prefetch_executor(enabled: bool, *, max_workers: int = 1):
    if not enabled:
        yield None
        return
    executor = ThreadPoolExecutor(max_workers=max_workers)
    try:
        yield executor
    finally:
        # A running article fetch cannot be cancelled. Its result is isolated
        # from selection state, so a deadline/429 must not wait for it.
        executor.shutdown(wait=False, cancel_futures=True)


def _body_signature(article: dict) -> tuple[int, frozenset[str]] | None:
    body = article.get("body")
    if not isinstance(body, str):
        return None
    compact = "".join(char for char in unicodedata.normalize("NFKC", body).casefold() if char.isalnum())
    # A short excerpt is not enough evidence that two full reports are copies.
    if len(compact) < 200:
        return None
    return len(compact), frozenset(compact[index:index + 7] for index in range(len(compact) - 6))


def _same_reporting_text(first: tuple[int, frozenset[str]] | None,
                         second: tuple[int, frozenset[str]] | None) -> bool:
    if first is None or second is None:
        return False
    first_len, first_grams = first
    second_len, second_grams = second
    if min(first_len, second_len) / max(first_len, second_len) < 0.9:
        return False
    intersection = len(first_grams & second_grams)
    return bool(intersection and intersection / (len(first_grams) + len(second_grams) - intersection) >= 0.92)


def _grounded_matches(assessment: dict, claim_ids: set[str]) -> dict[str, dict]:
    if assessment.get("event_relation") != "same_event":
        return {}
    return {match["claim_id"]: match for match in assessment.get("claim_matches", [])
            if match.get("claim_id") in claim_ids
            and match.get("relation") in COMPARABLE_RELATIONS
            and match.get("evidence") is not None}


def _comparison_article(candidate_id: str, article: dict, match: dict) -> dict:
    return {"candidate_id": candidate_id, "title": article["title"], "url": article["url"],
            "relation": match["relation"], "evidence": match["evidence"],
            **{field: match.get(field, [] if field == "relation_evidence" else
                               False if field == "has_multiple_voices" else
                               "" if field == "speaker" else "uncertain")
               for field in ("relation_evidence", "relation_owner", "speaker", "speech_kind", "has_multiple_voices")}}


def _independent_same_event_ids(source: dict, metadata: list[dict], articles: dict[str, dict],
                                assessed: dict[str, dict], claim_ids: set[str]) -> tuple[list[str], list[list[str]], list[str]]:
    """Keep one evidence-bearing representative per near-identical full body."""
    ids = [row["candidate_id"] for row in metadata
           if row["candidate_id"] in articles and assessed.get(row["candidate_id"], {}).get("event_relation") == "same_event"]
    signatures = {key: _body_signature(articles[key]) for key in ids}
    source_signature = _body_signature(source)
    source_duplicates = [key for key in ids if _same_reporting_text(signatures[key], source_signature)]
    ids = [key for key in ids if key not in source_duplicates]
    groups: list[list[str]] = []
    for key in ids:
        group = next((group for group in groups if _same_reporting_text(signatures[key], signatures[group[0]])), None)
        if group is None:
            groups.append([key])
        else:
            group.append(key)
    def representative(group: list[str]) -> str:
        return max(group, key=lambda key: (len(_grounded_matches(assessed[key], claim_ids)),
                                           assessed[key].get("event_focus") == "primary", -ids.index(key)))
    return [representative(group) for group in groups], groups, source_duplicates


def select_verified_comparisons(source: dict, analysis: dict, metadata_search: dict, ranking: dict, *,
                                max_per_claim: int = 3, event_baseline_limit: int = 5,
                                verification_batch_size: int = 3, client: Any = None,
                                progress: Callable[[str], None] | None = None,
                                should_stop: Callable[[], bool] | None = None) -> dict:
    """Verify candidates in ranked order, warming a bounded set of likely next bodies."""
    live_singletons = (verification_batch_size == 1 and isinstance(client, MetricsJSONClient)
                       and not isinstance(client, SnapshotJSONClient)
                       and isinstance(client.client, OpenRouterJSONClient))
    with _prefetch_executor(live_singletons, max_workers=MAX_SPECULATIVE_BODY_FETCHES) as body_executor:
        return _select_verified_comparisons(
            source, analysis, metadata_search, ranking, max_per_claim=max_per_claim,
            event_baseline_limit=event_baseline_limit, verification_batch_size=verification_batch_size,
            client=client, progress=progress, should_stop=should_stop, body_executor=body_executor)


def _select_verified_comparisons(source: dict, analysis: dict, metadata_search: dict, ranking: dict, *,
                                 max_per_claim: int, event_baseline_limit: int,
                                 verification_batch_size: int, client: Any,
                                 progress: Callable[[str], None] | None,
                                 should_stop: Callable[[], bool] | None,
                                 body_executor: ThreadPoolExecutor | None) -> dict:
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
    rate_limited = False
    rate_limit_error: Exception | None = None
    # A singleton verdict depends only on the source and that candidate body.
    # Recorded/replay and injected clients retain the original serial order.
    parallel_singletons = body_executor is not None
    body_prefetches = {}
    body_prefetch_order = []

    def fetch_selected(ids: list[str]) -> dict:
        """Use speculative bodies only after the normal selector asks for them."""
        # An initial selected batch still waits for its complete fetch result,
        # exactly as the former synchronous fetch_candidate_articles call did.
        # The later quality-pool prefetch has its own deadline-aware wait.
        fresh_ids = [key for key in ids if key not in body_prefetches]
        fresh = (fetch_candidate_articles([metadata_map[key] for key in fresh_ids]) if fresh_ids else
                 {"candidates": [], "fetch_failures": []})
        results = [fresh] + [body_prefetches[key].result() for key in ids if key in body_prefetches]
        candidates = {row["candidate_id"]: row for result in results for row in result["candidates"]}
        failures = {row["candidate_id"]: row for result in results for row in result["fetch_failures"]}
        return {"candidates": [candidates[key] for key in ids if key in candidates],
                "fetch_failures": [failures[key] for key in ids if key in failures]}

    def warm_next_bodies() -> None:
        if body_executor is None or body_prefetches or rate_limited or (should_stop and should_stop()):
            return
        # Same position-first order used for the later quality pool. A refill
        # can choose differently; such futures remain isolated and are ignored.
        queue_depth = max((len(queue) for queue in queues.values()), default=0)
        likely = []
        for position in range(queue_depth):
            for queue in queues.values():
                if position < len(queue):
                    likely.append(queue[position])
        likely.extend(baseline_queue)
        for key in dict.fromkeys(likely):
            if key in attempted_ids or key in articles or key in failed:
                continue
            body_prefetches[key] = body_executor.submit(fetch_candidate_articles, [metadata_map[key]])
            body_prefetch_order.append(key)
            if len(body_prefetches) >= MAX_SPECULATIVE_BODY_FETCHES:
                break

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

    def verify_response(ids: list[str]) -> dict[str, dict]:
        result = verify_candidate_articles(source, analysis, [articles[key] for key in ids], client=client)
        batch_assessments = {a["candidate_id"]: a for a in result["assessments"]}
        if set(batch_assessments) != set(ids) or len(batch_assessments) != len(result["assessments"]):
            raise ModelResponseError("선택 후보의 검증 결과가 누락되었거나 중복되었습니다.")
        return batch_assessments

    def apply_verification(ids: list[str], outcome: dict[str, dict] | Exception) -> None:
        """Apply independent responses in candidate order, including failures."""
        nonlocal rate_limited, rate_limit_error
        if isinstance(outcome, ModelResponseTruncatedError):
            exc = outcome
            if len(ids) == 1:
                mark_unverified(ids, "verification_failed", exc)
                return
            notify(f"4단계: 잘린 후보 검증 응답을 {len(ids)}개 기사별로 재검증")
            for key in ids:
                if rate_limited:
                    mark_unverified([key], "verification_deferred", rate_limit_error or exc)
                else:
                    verify_batch([key])
        elif isinstance(outcome, (RuntimeError, TimeoutError)):
            exc = outcome
            reason = ("verification_deferred" if isinstance(exc, TimeoutError)
                      and should_stop and should_stop() else "verification_failed")
            mark_unverified(ids, reason, exc)
            if make_error_record(exc, stage="candidate_verification")["code"] == "MODEL_RATE_LIMITED":
                rate_limited = True
                rate_limit_error = exc
        else:
            if isinstance(outcome, Exception):
                raise outcome
            assessed.update(outcome)

    def verify_batch(ids: list[str]) -> None:
        """Split only truncated responses, retaining each grounded assessment."""
        nonlocal verification_calls
        if should_stop and should_stop():
            mark_unverified(ids, "verification_deferred", TimeoutError())
            return
        verification_calls += 1
        try:
            outcome = verify_response(ids)
        except Exception as exc:
            outcome = exc
        apply_verification(ids, outcome)

    def verify_singletons(ids: list[str], round_number: int, *, quality_pool: bool = False) -> bool:
        """Keep three calls busy but apply verdicts only in candidate order."""
        nonlocal verification_calls
        width = MAX_PARALLEL_VERIFICATIONS
        executor = ThreadPoolExecutor(max_workers=width)
        running, completed = {}, {}
        next_launch = next_apply = 0
        stop_after_index = None
        stop_error = None
        stopped_early = False

        def defer_rest() -> bool:
            nonlocal verification_calls, stopped_early
            # A future that never started made no model call. Count running or
            # completed speculative calls, including ones whose verdict is
            # discarded after a later 429/deadline.
            for index, future in running.items():
                if index > stop_after_index and future.cancel():
                    verification_calls -= 1
            mark_unverified(ids[next_apply:], "verification_deferred", stop_error or TimeoutError())
            stopped_early = True
            return True

        try:
            while next_apply < len(ids):
                for index, future in list(running.items()):
                    if not future.done():
                        continue
                    try:
                        completed[index] = future.result()
                    except Exception as exc:
                        completed[index] = exc
                    del running[index]
                if stop_after_index is None and (rate_limited or (should_stop and should_stop())):
                    stop_error = rate_limit_error or TimeoutError()
                    # A deadline between original waves must not make an
                    # already speculative next wave part of the answer.
                    if next_apply and next_apply % width == 0:
                        stop_after_index = next_apply - 1
                    else:
                        stop_after_index = min(next_launch - 1, (next_apply // width + 1) * width - 1)
                if stop_after_index is not None and next_apply > stop_after_index:
                    return defer_rest()
                if next_apply in completed:
                    index = next_apply
                    apply_verification([ids[index]], completed.pop(index))
                    next_apply += 1
                    if rate_limited and stop_after_index is None:
                        # Preserve results from the failure's original wave,
                        # but ignore newer speculative calls and never launch
                        # another one after the 429 is known.
                        stop_after_index = min(next_launch - 1, (index // width + 1) * width - 1)
                        stop_error = rate_limit_error or TimeoutError()
                    continue
                while (stop_after_index is None and next_launch < len(ids)
                       and len(running) < width
                       and next_launch < (next_apply // width + 1) * width
                       + MAX_VERIFICATION_SPECULATIVE_CALLS):
                    if should_stop and should_stop():
                        stop_error = TimeoutError()
                        if next_apply and next_apply % width == 0:
                            stop_after_index = next_apply - 1
                        else:
                            stop_after_index = min(next_launch - 1, (next_apply // width + 1) * width - 1)
                        break
                    key = ids[next_launch]
                    label = "추가 사건·주장 검증" if quality_pool else "사건·주장 검증"
                    notify(f"4단계 {round_number}차: {label} [{key}]")
                    running[next_launch] = executor.submit(verify_response, [key])
                    verification_calls += 1
                    next_launch += 1
                if stop_after_index is not None and next_apply > stop_after_index:
                    return defer_rest()
                relevant = [future for index, future in running.items()
                            if stop_after_index is None or index <= stop_after_index]
                if not relevant:
                    raise RuntimeError("검증 중인 후보의 결과가 누락되었습니다.")
                # Poll only when a soft deadline exists. The quality-pool
                # prefetch uses `wait` separately, so its deadline semantics
                # remain unchanged.
                wait_for_verification(relevant, timeout=0.1 if should_stop else None,
                                      return_when=FIRST_COMPLETED)
            return rate_limited
        finally:
            # A newer speculative call may already be running when a slow
            # earlier call reveals 429/deadline. Its result cannot affect the
            # answer and must not delay the response. At most two such calls
            # can still incur provider cost after their verdict is discarded.
            executor.shutdown(wait=not stopped_early, cancel_futures=stopped_early)

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
            fetched = fetch_selected(pending)
            attempted_ids.extend(pending)
            articles.update({a["candidate_id"]: a for a in fetched["candidates"]})
            failed.update({f["candidate_id"]: f for f in fetched["fetch_failures"]})
            if set(pending) != {a["candidate_id"] for a in fetched["candidates"]} | {
                f["candidate_id"] for f in fetched["fetch_failures"]
            }:
                raise RuntimeError("일부 후보의 본문 수집 결과 또는 실패 기록이 빠졌습니다.")
            warm_next_bodies()
        unchecked = [key for key in proposed if key in articles and key not in assessed]
        if parallel_singletons and not stop_after_round:
            stop_after_round = verify_singletons(unchecked, round_number)
        else:
            for start in range(0, len(unchecked), verification_batch_size):
                if stop_after_round:
                    break
                if should_stop and should_stop():
                    mark_unverified(unchecked[start:], "verification_deferred", TimeoutError())
                    stop_after_round = True
                    break
                batch_ids = unchecked[start:start + verification_batch_size]
                notify(f"4단계 {round_number}차: 사건·주장 검증 [{', '.join(batch_ids)}]")
                verify_batch(batch_ids)
                if rate_limited:
                    # A persistent quota failure is shared across candidates.
                    # Preserve already grounded articles and leave time for the rubric.
                    stop_after_round = True
                    mark_unverified(unchecked[start + len(batch_ids):], "verification_deferred",
                                    rate_limit_error or TimeoutError())
                    break
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
    # Verify a bounded later pool even when the initial per-claim slots filled.
    # Otherwise metadata ordering becomes the final answer regardless of body
    # quality, claim coverage, or syndicated copies.
    extra_ids = []
    queue_depth = max((len(queue) for queue in queues.values()), default=0)
    for position in range(queue_depth):
        for queue in queues.values():
            if position >= len(queue):
                continue
            key = queue[position]
            if key not in attempted_ids and key not in assessed and key not in failed and key not in extra_ids:
                extra_ids.append(key)
                if len(extra_ids) >= MAX_EXTRA_VERIFIED_CANDIDATES:
                    break
        if len(extra_ids) >= MAX_EXTRA_VERIFIED_CANDIDATES:
            break
    if len(extra_ids) < MAX_EXTRA_VERIFIED_CANDIDATES:
        extra_ids.extend(key for key in baseline_queue if key not in attempted_ids and key not in assessed
                         and key not in failed and key not in extra_ids)
        extra_ids = extra_ids[:MAX_EXTRA_VERIFIED_CANDIDATES]
    if rate_limited:
        extra_ids = []
    quality_pool_batch_size = MAX_PARALLEL_VERIFICATIONS if parallel_singletons else verification_batch_size
    prefetch_discarded_ids = []
    # The next quality-pool body fetch is independent of this batch's LLM
    # verdict. Keep only one batch ahead and apply its result in queue order.
    with _prefetch_executor(parallel_singletons and len(extra_ids) > quality_pool_batch_size) as fetch_executor:
        prefetched = None
        prefetched_ids = []

        def discard_prefetch() -> None:
            # A queued future can still be cancelled before any network work.
            # A running/completed future is recorded separately from the
            # candidates actually used for selection and verification.
            if prefetched is not None and not prefetched.cancel():
                prefetch_discarded_ids.extend(prefetched_ids)

        for start in range(0, len(extra_ids), quality_pool_batch_size):
            if rate_limited or (should_stop and should_stop()):
                discard_prefetch()
                break
            round_number = len(rounds) + 1
            batch_ids = extra_ids[start:start + quality_pool_batch_size]
            notify(f"4단계 {round_number}차: 품질 검토 후보 {len(batch_ids)}개 본문 수집")
            if prefetched is not None:
                # A deadline may arrive while the next page is still loading.
                # Stop waiting without treating that speculative fetch as an
                # attempted candidate or a fetch failure.
                if should_stop is not None:
                    while not prefetched.done() and not should_stop():
                        wait((prefetched,), timeout=0.1)
                    if should_stop():
                        discard_prefetch()
                        break
                fetched = prefetched.result()
            else:
                fetched = fetch_selected(batch_ids)
            prefetched = None
            prefetched_ids = []
            attempted_ids.extend(batch_ids)
            articles.update({a["candidate_id"]: a for a in fetched["candidates"]})
            failed.update({f["candidate_id"]: f for f in fetched["fetch_failures"]})
            if set(batch_ids) != {a["candidate_id"] for a in fetched["candidates"]} | {
                f["candidate_id"] for f in fetched["fetch_failures"]
            }:
                raise RuntimeError("일부 후보의 본문 수집 결과 또는 실패 기록이 빠졌습니다.")
            unchecked = [key for key in batch_ids if key in articles]
            next_ids = extra_ids[start + quality_pool_batch_size:start + 2 * quality_pool_batch_size]
            if fetch_executor is not None and unchecked and next_ids and not (should_stop and should_stop()):
                prefetched = fetch_executor.submit(
                    fetch_selected, next_ids)
                prefetched_ids = next_ids
            if unchecked and should_stop and should_stop():
                mark_unverified(unchecked, "verification_deferred", TimeoutError())
            elif unchecked:
                if parallel_singletons:
                    verify_singletons(unchecked, round_number, quality_pool=True)
                else:
                    notify(f"4단계 {round_number}차: 추가 사건·주장 검증 [{', '.join(unchecked)}]")
                    verify_batch(unchecked)
            targets = {claim_id: [key for key in batch_ids if key in queue]
                       for claim_id, queue in queues.items()}
            for claim_id, ids in targets.items():
                for key in ids:
                    assessment = assessed.get(key, {})
                    match = _grounded_matches(assessment, set(claim_map)).get(claim_id)
                    reason = ("fetch_failed" if key in failed else
                              "verification_failed" if key in verification_failed else
                              "verification_deferred" if key in verification_deferred else
                              "different_event" if assessment.get("event_relation") == "different_event" else
                              "uncertain_event" if assessment.get("event_relation") == "uncertain" else
                              "verified" if match else "no_claim_match")
                    claim_attempts.append({"round": round_number, "claim_id": claim_id, "candidate_id": key,
                                           "status": "accepted" if match else "rejected", "reason": reason,
                                           "selection_basis": "quality_pool"})
            for key in batch_ids:
                if key in baseline_queue:
                    baseline_attempts.append({"round": round_number, "candidate_id": key,
                                              "status": "accepted" if assessed.get(key, {}).get("event_relation") == "same_event" else "rejected",
                                              "reason": assessed.get(key, {}).get("event_relation", "unverified")})
            rounds.append({"round": round_number, "claim_targets": targets,
                           "event_baseline_targets": [key for key in batch_ids if key in baseline_queue],
                           "fetched_ids": batch_ids,
                           "verified_ids": [key for key in unchecked if key not in verification_failed
                                            and key not in verification_deferred],
                           "unverified_ids": [key for key in unchecked if key in verification_failed
                                              or key in verification_deferred],
                           "deferred_ids": [key for key in unchecked if key in verification_deferred],
                           "reused_verified_candidates": {}, "diversity_selection_notes": {},
                           "quality_pool": True})
    # Speculative failures and successes are invisible until their candidate
    # was actually selected. Cancel queued work; running fetches may finish.
    for key in body_prefetch_order:
        if key not in attempted_ids:
            # A queued future cancelled before it starts made no HTTP request.
            if not body_prefetches[key].cancel() and key not in prefetch_discarded_ids:
                prefetch_discarded_ids.append(key)
    independent_ids, body_groups, source_duplicates = _independent_same_event_ids(
        source, metadata, articles, assessed, set(claim_map))
    duplicate_groups = [group for group in body_groups if len(group) > 1]
    duplicate_to_group = {key: group for group in body_groups for key in group}
    event_baseline_ids = [key for key in independent_ids if
                          any(metadata_map[member].get("discovered_by_event")
                              for member in duplicate_to_group.get(key, [key]))][:event_baseline_limit]
    final_comparisons = []
    for claim_id, claim in claim_map.items():
        queue_order = {key: position for position, key in enumerate(queues[claim_id])}
        # A copied body can receive inconsistent verifier labels. Keep the
        # member that actually has this claim's grounded evidence rather than
        # transferring another member's evidence or dropping the claim.
        candidates = []
        for group in body_groups:
            evidenced = [key for key in group if claim_id in _grounded_matches(assessed[key], set(claim_map))]
            if evidenced:
                candidates.append(max(evidenced, key=lambda key: (
                    len(_grounded_matches(assessed[key], set(claim_map))),
                    assessed[key].get("event_focus") == "primary",
                    -queue_order.get(key, len(metadata)))))
        selected, covered_claims, publishers = [], set(), set()
        while candidates and len(selected) < max_per_claim:
            def quality(key: str) -> tuple[int, int, int, int, int, int]:
                matches = _grounded_matches(assessed[key], set(claim_map))
                publisher = str(articles[key].get("publisher") or metadata_map[key].get("publisher") or "").casefold().strip()
                relation = matches[claim_id]["relation"]
                relation_weight = 2 if relation in {"same", "opposes"} else 1 if relation == "different_interpretation" else 0
                return (len(set(matches) - covered_claims), int(publisher not in publishers),
                        int(assessed[key].get("event_focus") == "primary"), relation_weight,
                        -queue_order.get(key, len(metadata)), -next(index for index, row in enumerate(metadata)
                                                               if row["candidate_id"] == key))
            key = max(candidates, key=quality)
            candidates.remove(key)
            selected.append(key)
            covered_claims.update(_grounded_matches(assessed[key], set(claim_map)))
            publishers.add(str(articles[key].get("publisher") or metadata_map[key].get("publisher") or "").casefold().strip())
        final_comparisons.append({"claim_id": claim_id, "claim": claim["claim"],
                                  "articles": [_comparison_article(key, articles[key],
                                               _grounded_matches(assessed[key], set(claim_map))[claim_id])
                                               for key in selected]})
    selected_ids = {row["candidate_id"] for comparison in final_comparisons
                    for row in comparison["articles"]}
    same_event_ids = [row["candidate_id"] for row in metadata
                      if row["candidate_id"] in set(independent_ids) | selected_ids]
    ordered_assessments = [assessed[m["candidate_id"]] for m in metadata if m["candidate_id"] in assessed]
    selection = {
        "assessments": ordered_assessments,
        "same_event_ids": same_event_ids,
        "event_baseline_ids": event_baseline_ids,
        "independent_same_event_ids": independent_ids,
        "claim_comparisons": final_comparisons,
        "duplicate_article_groups": duplicate_groups,
        "source_duplicate_article_ids": source_duplicates,
        "warnings": (["같은 본문을 재전재한 기사는 I1 비교 기준에서 한 건으로 취급합니다. "
                      "주장별 근거가 서로 다른 재전재 기사에만 확인된 경우 해당 원문 근거를 보존하며, "
                      "기사별 I1 행 수를 독립 보도 비율로 해석하지 마세요."] if duplicate_groups else [])
                    + (["입력 기사와 본문이 거의 같은 재전재 후보는 독립 비교 기사에서 제외했습니다."]
                       if source_duplicates else []),
        "claim_attempts": claim_attempts, "event_baseline_attempts": baseline_attempts,
        "verification_rounds": rounds, "verification_llm_calls": verification_calls,
        "exhausted_claim_ids": [row["claim_id"] for row in final_comparisons
                                if len(row["articles"]) < max_per_claim],
    }
    # Keep the existing selection-attempt count stable. A speculative fetch
    # discarded at a deadline/429 is visible separately, never as a verified
    # candidate or a body-fetch failure.
    search = {**metadata_search, "errors": list(metadata_search.get("errors", [])) + verification_errors,
              "candidates": [articles[key] for key in attempted_ids if key in articles],
              "fetch_failures": [failed[key] for key in attempted_ids if key in failed],
              "fetch_attempt_count": len(attempted_ids),
              "prefetch_discarded_ids": prefetch_discarded_ids,
              "unused_candidate_ids": [m["candidate_id"] for m in metadata if m["candidate_id"] not in attempted_ids]}
    return {"search": search, "selection": selection}
