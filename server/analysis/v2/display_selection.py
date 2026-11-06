"""Choose a bounded set of verified articles for the Android comparison view."""

from __future__ import annotations

from itertools import combinations
import re
import unicodedata
from urllib.parse import urlsplit


def _publisher(article: dict) -> str:
    name = article.get("publisher", "")
    if isinstance(name, str) and name.strip():
        return re.sub(r"[^0-9a-z가-힣]", "", unicodedata.normalize("NFKC", name).casefold())
    url = article.get("url")
    host = urlsplit(url if isinstance(url, str) else "").hostname or ""
    return host.removeprefix("www.").casefold()


def _same_publisher(first: str, second: str) -> bool:
    if not first or not second:
        return False
    if first == second:
        return True
    shorter, longer = sorted((first, second), key=len)
    return len(shorter) >= 4 and shorter in longer


def _body_grams(article: dict) -> set[str]:
    body = article.get("body", "")
    if not isinstance(body, str):
        return set()
    normalized = re.sub(r"\s+", "", unicodedata.normalize("NFKC", body)).casefold()
    return {normalized[index:index + 5] for index in range(max(0, len(normalized) - 4))}


def select_display_article_ids(selection: dict, limit: int,
                               candidate_articles: list[dict] | None = None) -> list[str]:
    """Cover as many source claims as possible before favoring repeated matches.

    Discovery can verify a different article for every source claim, while the
    current Android contract displays at most ``limit`` related articles.  The
    set is small (at most 15 selected comparisons plus event baselines), so
    checking every combination avoids a greedy choice that hides a claim's
    only match. All IDs here already came from verified same-event candidates.
    When verified bodies are available, avoid filling the small comparison set
    with syndicated copies of one article and prefer independent publishers.
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

    catalog = {article["candidate_id"]: article for article in candidate_articles or []
               if isinstance(article, dict) and isinstance(article.get("candidate_id"), str)}
    publishers = {key: _publisher(catalog[key]) for key in first_seen if key in catalog}
    grams = {key: _body_grams(catalog[key]) for key in first_seen if key in catalog}

    def diversity(group: tuple[str, ...]) -> tuple[int, int]:
        if not candidate_articles:
            return (0, 0)
        publisher_groups = []
        for key in group:
            publisher = publishers.get(key, "")
            if publisher and not any(_same_publisher(publisher, seen) for seen in publisher_groups):
                publisher_groups.append(publisher)
        independent = len(publisher_groups)
        distinct_pairs = 0
        for left, right in combinations(group, 2):
            a, b = grams.get(left, set()), grams.get(right, set())
            if not a or not b or len(a & b) / len(a | b) < 0.86:
                distinct_pairs += 1
        return (distinct_pairs, independent)

    candidates = list(first_seen)
    if len(candidates) <= limit:
        chosen = candidates
    else:
        chosen = max(combinations(candidates, limit), key=lambda group: (
            len(set().union(*(claims_by_article.get(key, set()) for key in group))),
            diversity(group)[1],
            diversity(group)[0],
            sum(mentions.get(key, 0) for key in group),
            sum(key in baseline_ids for key in group),
            -sum(first_seen[key] for key in group),
        ))
    return sorted(chosen, key=lambda key: (-mentions.get(key, 0), first_seen[key]))
