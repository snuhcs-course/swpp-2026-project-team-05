"""Choose a bounded set of verified articles for the Android comparison view."""

from __future__ import annotations

from itertools import combinations


def select_display_article_ids(selection: dict, limit: int) -> list[str]:
    """Cover as many source claims as possible before favoring repeated matches.

    Discovery can verify a different article for every source claim, while the
    current Android contract displays at most ``limit`` related articles.  The
    set is small (at most 15 selected comparisons plus event baselines), so
    checking every combination avoids a greedy choice that hides a claim's
    only match. All IDs here already came from verified same-event candidates.
    """
    if type(limit) is not int or not 1 <= limit <= 3:
        raise ValueError("display article limit must be an integer between 1 and 3")
    first_seen: dict[str, int] = {}
    claims_by_article: dict[str, set[str]] = {}
    mentions: dict[str, int] = {}
    for row in selection["claim_comparisons"]:
        for article in row["articles"]:
            key = article["candidate_id"]
            first_seen.setdefault(key, len(first_seen))
            claims_by_article.setdefault(key, set()).add(row["claim_id"])
            mentions[key] = mentions.get(key, 0) + 1
    baseline_ids = set(selection["event_baseline_ids"])
    for key in selection["event_baseline_ids"]:
        first_seen.setdefault(key, len(first_seen))
    if not first_seen:
        return []

    candidates = list(first_seen)
    if len(candidates) <= limit:
        chosen = candidates
    else:
        chosen = max(combinations(candidates, limit), key=lambda group: (
            len(set().union(*(claims_by_article.get(key, set()) for key in group))),
            sum(mentions.get(key, 0) for key in group),
            sum(key in baseline_ids for key in group),
            -sum(first_seen[key] for key in group),
        ))
    return sorted(chosen, key=lambda key: (-mentions.get(key, 0), first_seen[key]))
