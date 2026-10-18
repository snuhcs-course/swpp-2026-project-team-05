"""Search with LLM-generated queries, deduplicate URLs, and collect full bodies."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from itertools import zip_longest
import json
import os
import re
from html import unescape
from http.client import HTTPException
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qsl, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from .model_client import _checked_model_name, get_gemini_api_key, get_naver_credentials
from .article_fetch import validate_article_url
from .article_input import prepare_article
from .result_output import OperationError, make_error_record


_NEWS_SEARCH_URL = "https://naverapihub.apigw.ntruss.com/search/v1/news"
_GOOGLE_INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"


class _SearchRows(list):
    """A normal result list with nonfatal per-sort retrieval errors."""

    def __init__(self, rows: list[dict], sort_errors: list[tuple[str, OperationError]]) -> None:
        super().__init__(rows)
        self.sort_errors = sort_errors


class _NoCredentialRedirects(HTTPRedirectHandler):
    """Do not forward search API credentials to a redirected origin."""

    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


def _plain_text(value: str) -> str:
    return unescape(re.sub(r"<[^>]+>", "", value)).strip() if isinstance(value, str) else ""


def normalize_news_url(url: str) -> str:
    """Deduplication key: retain article-ID query parameters, drop known trackers."""
    parsed = urlsplit(url)
    hostname = (parsed.hostname or "").lower()
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    if hostname in ("n.news.naver.com", "news.naver.com", "m.entertain.naver.com", "entertain.naver.com",
                    "m.sports.naver.com", "sports.naver.com"):
        match = re.search(r"/article/(\d+)/(\d+)", parsed.path)
        if match:
            return f"naver:{match.group(1)}:{match.group(2)}"
        if query.get("oid") and query.get("aid"):
            return f"naver:{query['oid']}:{query['aid']}"
    pairs = [(key, value) for key, value in parse_qsl(parsed.query, keep_blank_values=True)
             if not key.lower().startswith("utm_")
             and key.lower() not in {"input", "fbclid", "gclid", "nclick"}]
    suffix = "?" + urlencode(sorted(pairs)) if pairs else ""
    return hostname + parsed.path.rstrip("/") + suffix


def _search_naver_news_sorted(query: str, display: int, sort: str) -> list[dict]:
    """Retrieve one ordered NAVER result page without following credentialed redirects."""
    if not isinstance(query, str) or not query.strip():
        raise ValueError("검색어가 비어 있습니다.")
    if type(display) is not int or not 1 <= display <= 100:
        raise ValueError("display는 1~100의 정수여야 합니다.")
    if sort not in ("sim", "date"):
        raise ValueError("sort는 sim 또는 date여야 합니다.")
    client_id, client_secret = get_naver_credentials()
    if not client_id or not client_secret:
        raise OperationError("NAVER API HUB의 Client ID·Secret을 설정해 주세요.", code="NAVER_CONFIGURATION_MISSING")
    request = Request(_NEWS_SEARCH_URL + "?" + urlencode({
        "query": query.strip(), "display": display, "start": 1, "sort": sort, "format": "json",
    }), headers={"X-NCP-APIGW-API-KEY-ID": client_id,
                 "X-NCP-APIGW-API-KEY": client_secret, "Accept": "application/json"})
    try:
        with build_opener(_NoCredentialRedirects()).open(request, timeout=10) as response:
            payload = json.load(response)
    except HTTPError as exc:
        if 300 <= exc.code < 400:
            raise OperationError("네이버 검색 API의 리다이렉트를 허용하지 않습니다.",
                                 code="SEARCH_REDIRECT_BLOCKED", http_status=exc.code) from exc
        raise OperationError(f"네이버 뉴스 검색이 실패했습니다 (HTTP {exc.code}).",
                             code="SEARCH_HTTP_ERROR", http_status=exc.code,
                             retryable=exc.code in (408, 429) or exc.code >= 500) from exc
    except (json.JSONDecodeError, UnicodeError) as exc:
        raise OperationError("네이버 검색 API가 올바른 JSON 응답을 반환하지 않았습니다.",
                             code="SEARCH_RESPONSE_INVALID", retryable=True) from exc
    except (URLError, OSError, HTTPException) as exc:
        timeout = isinstance(exc, TimeoutError) or isinstance(getattr(exc, "reason", None), TimeoutError)
        raise OperationError("네이버 뉴스 검색 시간이 초과됐습니다." if timeout else
                             "네이버 뉴스 검색 API에 연결하지 못했거나 응답이 끊어졌습니다.",
                             code="SEARCH_TIMEOUT" if timeout else "SEARCH_CONNECTION_FAILED", retryable=True) from exc
    if not isinstance(payload, dict) or not isinstance(payload.get("items"), list):
        raise OperationError("네이버 검색 응답에 기사 목록이 없습니다.", code="SEARCH_RESPONSE_INVALID", retryable=True)
    if any(not isinstance(item, dict) or any(item.get(key) is not None and not isinstance(item[key], str)
               for key in ("title", "description", "link", "originallink", "pubDate")) for item in payload["items"]):
        raise OperationError("네이버 검색 응답의 기사 정보 형식이 올바르지 않습니다.",
                             code="SEARCH_RESPONSE_INVALID", retryable=True)
    return [{"title": _plain_text(item.get("title", "")),
             "description": _plain_text(item.get("description", "")),
             "link": unescape(item.get("link") or ""),
             "originallink": unescape(item.get("originallink") or ""),
             "search_provided_at": item.get("pubDate") or "", "search_sort": sort}
            for item in payload["items"] if isinstance(item, dict)]


def search_naver_news(query: str, display: int = 10) -> list[dict]:
    """Combine relevance and recency results so a recent event can be found by either."""
    pages, errors = [], []
    sorts = ("sim", "date")
    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(_search_naver_news_sorted, query, display, sort) for sort in sorts]
        for sort, future in zip(sorts, futures):
            try:
                pages.append(future.result())
            except OperationError as exc:
                errors.append((sort, exc))
                pages.append([])
    if len(errors) == 2:
        raise errors[0][1]
    # Interleave instead of appending: metadata selection reserves the first event
    # candidates, and a long relevance page should not crowd out recent reporting.
    return _SearchRows([item for pair in zip_longest(*pages) for item in pair if item is not None], errors)


def _google_grounding_enabled() -> bool:
    return os.getenv("ENABLE_GOOGLE_GROUNDED_SEARCH", "").strip().lower() in ("1", "true", "yes")


def _reviewed_event_queries(analysis: dict) -> list[str]:
    """Prefer grounded review queries; the planner's original may target a side topic."""
    reviewed = analysis.get("event_queries")
    if isinstance(reviewed, list):
        queries = []
        for value in reviewed:
            if not isinstance(value, str):
                continue
            query = " ".join(value.split())
            if 2 <= len(query) <= 80 and query not in queries:
                queries.append(query)
        if queries:
            return queries[:3]
    return [" ".join(analysis["event_query"].split())]


def search_google_grounded_news(source: dict, analysis: dict, *, max_urls: int = 10) -> list[dict]:
    """Discover candidate URLs from actual Google Search citation annotations.

    The model's prose and URL-looking strings are never used as article URLs.
    Google Search citations only nominate candidates; the regular article fetch
    and same-event verification stages must still inspect their complete bodies.
    """
    if type(max_urls) is not int or not 1 <= max_urls <= 20:
        raise ValueError("max_urls는 1~20의 정수여야 합니다.")
    api_key = get_gemini_api_key()
    if not api_key:
        raise OperationError("Google 검색용 Gemini API 설정이 없습니다.", code="GOOGLE_SEARCH_CONFIGURATION_MISSING")
    try:
        model = _checked_model_name(os.getenv("GOOGLE_GROUNDED_SEARCH_MODEL", "").strip()
                                    or "gemini-3.8-flash")
    except ValueError as exc:
        raise OperationError("Google 검색 모델 설정이 올바르지 않습니다.",
                             code="GOOGLE_SEARCH_CONFIGURATION_INVALID") from exc
    query_text = " / ".join(_reviewed_event_queries(analysis))
    review = analysis.get("source_scope_review")
    reviewed_event = review.get("primary_event") if isinstance(review, dict) else None
    event = reviewed_event or analysis.get("core_event") or ""
    prompt = (
        "다음 한국어 뉴스 기사의 제목·사건·검색어를 참고해 같은 구체적인 사건을 실질적으로 "
        "보도한 다른 언론사 기사들을 웹에서 검색하세요. 같은 인물이나 넓은 주제만 같은 "
        "별개 사건은 제외하세요. 한국어 검색어를 달리해 여러 언론사의 개별 기사 URL을 "
        "찾고, 찾은 기사마다 한 줄씩 출처 인용을 붙여 설명하세요. 검색 결과에 없는 "
        "URL이나 기사를 만들지 마세요. 이 단계의 답변은 후보 발견에만 사용됩니다.\n"
        f"원본 제목: {str(source.get('title') or '')[:200]}\n"
        f"중심 사건: {str(event)[:300]}\n"
        f"검색어: {query_text[:400]}\n"
        f"원본 URL(후보에서 제외): {source['url']}"
    )
    request = Request(_GOOGLE_INTERACTIONS_URL,
                      data=json.dumps({"model": model, "input": prompt, "store": False,
                                       "tools": [{"type": "google_search"}]}, ensure_ascii=False).encode("utf-8"),
                      headers={"x-goog-api-key": api_key, "Content-Type": "application/json",
                               "Accept": "application/json"}, method="POST")
    try:
        with build_opener(_NoCredentialRedirects()).open(request, timeout=30) as response:
            raw = response.read(2_000_001)
        if len(raw) > 2_000_000:
            raise OperationError("Google 검색 응답이 너무 큽니다.", code="GOOGLE_SEARCH_RESPONSE_INVALID")
        payload = json.loads(raw)
    except HTTPError as exc:
        if 300 <= exc.code < 400:
            raise OperationError("Google 검색 API의 리다이렉트를 허용하지 않습니다.",
                                 code="GOOGLE_SEARCH_REDIRECT_BLOCKED", http_status=exc.code) from exc
        raise OperationError(f"Google 검색이 실패했습니다 (HTTP {exc.code}).",
                             code="GOOGLE_SEARCH_HTTP_ERROR", http_status=exc.code,
                             retryable=exc.code in (408, 429) or exc.code >= 500) from exc
    except (json.JSONDecodeError, UnicodeError) as exc:
        raise OperationError("Google 검색 API가 올바른 JSON 응답을 반환하지 않았습니다.",
                             code="GOOGLE_SEARCH_RESPONSE_INVALID", retryable=True) from exc
    except (URLError, OSError, HTTPException) as exc:
        timeout = isinstance(exc, TimeoutError) or isinstance(getattr(exc, "reason", None), TimeoutError)
        raise OperationError("Google 검색 시간이 초과됐습니다." if timeout else
                             "Google 검색 API에 연결하지 못했거나 응답이 끊어졌습니다.",
                             code="GOOGLE_SEARCH_TIMEOUT" if timeout else "GOOGLE_SEARCH_CONNECTION_FAILED",
                             retryable=True) from exc
    interaction = payload.get("interaction", payload) if isinstance(payload, dict) else None
    steps = interaction.get("steps") if isinstance(interaction, dict) else None
    if not isinstance(steps, list):
        raise OperationError("Google 검색 응답에 검증 가능한 단계 정보가 없습니다.",
                             code="GOOGLE_SEARCH_RESPONSE_INVALID")
    if not any(isinstance(step, dict) and step.get("type") == "google_search_call" for step in steps):
        return []
    rows, seen = [], set()
    source_key = normalize_news_url(source["url"])
    for step in steps:
        if not isinstance(step, dict) or step.get("type") != "model_output":
            continue
        for block in step.get("content") or []:
            if not isinstance(block, dict) or block.get("type") != "text":
                continue
            for annotation in block.get("annotations") or []:
                if not isinstance(annotation, dict) or annotation.get("type") != "url_citation":
                    continue
                try:
                    url = validate_article_url(annotation.get("url"))
                except ValueError:
                    continue
                host = (urlsplit(url).hostname or "").lower()
                # Legacy grounding redirects and search result pages are not
                # directly extractable news articles.
                if host == "google.com" or host.endswith(".google.com"):
                    continue
                key = normalize_news_url(url)
                if key == source_key or key in seen:
                    continue
                seen.add(key)
                title = _plain_text(annotation.get("title", "")) or host
                rows.append({"title": title[:200], "description": "", "link": url,
                             "originallink": "", "search_provided_at": "",
                             "search_provider": "google_grounding"})
                if len(rows) >= max_urls:
                    return rows
    return rows


def _query_plan(analysis: dict) -> list[dict]:
    queries = {}
    entries = [(query, {"scope": "event", "claim_id": None})
               for query in _reviewed_event_queries(analysis)]
    entries += [(claim["search_query"], {"scope": "claim", "claim_id": claim["claim_id"]})
                for claim in analysis["claims"]]
    for query, source in entries:
        query = " ".join(query.split())
        if not query:
            raise ValueError("LLM 검색어가 비어 있습니다.")
        record = queries.setdefault(query, {"query": query, "sources": [], "provider": "naver"})
        if source not in record["sources"]:
            record["sources"].append(source)
    return list(queries.values())


def _valid_urls(item: dict) -> list[str]:
    urls = []
    for value in (item.get("link"), item.get("originallink")):
        try:
            url = validate_article_url(value)
        except ValueError:
            continue
        if url not in urls:
            urls.append(url)
    return urls


def _select_candidate_metadata(pool: list[dict], claim_ids: list[str], limit: int, reserve: int,
                               *, event_queries: list[str]) -> list[dict]:
    """Share event slots across query variants before claim and web allocations."""
    event_queues = [[item for item in pool if any(
        match.get("scope") == "event" and match.get("query") == query
        and match.get("provider") == "naver" for match in item["search_matches"])]
        for query in event_queries]
    event_order, event_seen = [], set()
    event_positions = [0] * len(event_queues)
    while True:
        added = False
        for index, queue in enumerate(event_queues):
            while event_positions[index] < len(queue) and id(queue[event_positions[index]]) in event_seen:
                event_positions[index] += 1
            if event_positions[index] < len(queue):
                item = queue[event_positions[index]]
                event_positions[index] += 1
                event_order.append(item)
                event_seen.add(id(item))
                added = True
        if not added:
            break
    selected = event_order[:min(reserve, limit)]
    # When enabled, preserve a small quota for independently found web results.
    # Full-body verification will decide whether these are actually same-event.
    grounded = [item for item in pool if any(match.get("provider") == "google_grounding"
                                             for match in item["search_matches"])]
    grounded_added = 0
    for item in grounded:
        if len(selected) >= limit or grounded_added >= 2:
            break
        if item not in selected:
            selected.append(item)
            grounded_added += 1
    queues = [[item for item in pool if any(source["claim_id"] == claim_id
                                          for source in item["search_matches"])]
              for claim_id in claim_ids]
    positions = [0] * len(queues)
    while len(selected) < limit:
        added = False
        for index, queue in enumerate(queues):
            while positions[index] < len(queue) and queue[positions[index]] in selected:
                positions[index] += 1
            if positions[index] < len(queue) and len(selected) < limit:
                selected.append(queue[positions[index]])
                positions[index] += 1
                added = True
        if not added:
            break
    # After the per-claim allocation, fill remaining slots from all event
    # variants in the same deterministic order, then from the full pool.
    for item in event_order + pool:
        if len(selected) >= limit:
            break
        if item not in selected:
            selected.append(item)
    return selected


def _fetch_candidate(item: dict) -> tuple[dict | None, dict | None]:
    attempts = []
    for url in item["urls"]:
        try:
            article = prepare_article(url)
        except Exception as exc:
            attempts.append(make_error_record(exc, stage="candidate_fetch", candidate_id=item["candidate_id"], url=url))
            continue
        article["title"] = article["title"] or item["title"]
        return ({**article, "candidate_id": item["candidate_id"],
                 "search_description": item["description"],
                 "search_provided_at": item["search_provided_at"],
                 "discovered_by_event": item["discovered_by_event"],
                 "search_matches": item["search_matches"], "fetch_attempt_errors": attempts}, None)
    error = (dict(attempts[-1]) if attempts else
             make_error_record(OperationError("본문을 수집할 기사 URL이 없습니다.", code="CANDIDATE_URL_MISSING"),
                               stage="candidate_fetch", candidate_id=item["candidate_id"]))
    return (None, {**error, "url": item["urls"][0] if item["urls"] else "", "status": "fetch_failed", "attempts": attempts})


def search_candidate_metadata(source: dict, analysis: dict, *, max_candidates: int = 20,
                              display_per_query: int = 10, event_reserve: int = 5) -> dict:
    """Search metadata without bodies; provider-tagged queries expose external search status.

    The optional Google Interactions call is not part of the JSON client's
    model execution_summary, so callers must not treat that count as complete.
    """
    if type(max_candidates) is not int or not 1 <= max_candidates <= 100:
        raise ValueError("max_candidates는 1~100의 정수여야 합니다.")
    if type(event_reserve) is not int or event_reserve < 1:
        raise ValueError("event_reserve는 양의 정수여야 합니다.")
    if type(display_per_query) is not int or not 1 <= display_per_query <= 100:
        raise ValueError("display_per_query는 1~100의 정수여야 합니다.")
    queries, pool = _query_plan(analysis), []
    if _google_grounding_enabled():
        queries.append({"query": _reviewed_event_queries(analysis)[0],
                        "sources": [{"scope": "event", "claim_id": None}],
                        "provider": "google_grounding"})
    source_keys = {normalize_news_url(source["url"])}
    def run_query(record: dict) -> tuple[list[dict] | None, RuntimeError | None]:
        provider = record.get("provider", "naver")
        try:
            items = (search_google_grounded_news(source, analysis, max_urls=min(10, max_candidates))
                     if provider == "google_grounding" else
                     search_naver_news(record["query"], display_per_query))
        except RuntimeError as exc:
            return None, exc
        return items, None

    # Each query is independent. Keep a bounded number of concurrent requests,
    # then consume responses in query-plan order for stable candidate IDs/ranks.
    with ThreadPoolExecutor(max_workers=min(4, len(queries))) as executor:
        query_results = list(executor.map(run_query, queries))
    errors, succeeded = [], 0
    for record, (items, failure) in zip(queries, query_results):
        provider = record.get("provider", "naver")
        if failure is not None:
            exc = failure
            error = make_error_record(exc, stage="search", query=record["query"], provider=provider)
            errors.append(error)
            record.update({"status": "search_failed", "result_count": 0, "error": error})
            continue
        assert items is not None
        succeeded += 1
        sort_errors = [make_error_record(exc, stage="search", query=record["query"],
                                         provider="naver", sort=sort)
                       for sort, exc in getattr(items, "sort_errors", [])]
        errors.extend(sort_errors)
        record.update({"status": "partial" if sort_errors else "searched", "result_count": len(items)})
        if sort_errors:
            record["sort_errors"] = sort_errors
        for item in items:
            urls = _valid_urls(item)
            keys = {normalize_news_url(url) for url in urls}
            if not urls:
                continue
            if keys & source_keys:
                source_keys.update(keys)
                continue
            found = [owner for owner in pool if owner["keys"] & keys]
            if found:
                target = found[0]
                for other in found[1:]:
                    target["keys"].update(other["keys"])
                    target["urls"] = list(dict.fromkeys(target["urls"] + other["urls"]))
                    target["search_matches"] += [match for match in other["search_matches"]
                                                 if match not in target["search_matches"]]
                    target["discovered_by_event"] |= other["discovered_by_event"]
                    pool.remove(other)
            else:
                target = {"urls": [], "keys": set(), "title": item["title"],
                          "description": item["description"], "search_provided_at": item["search_provided_at"],
                          "search_matches": [], "discovered_by_event": False}
                pool.append(target)
            target["keys"].update(keys)
            target["urls"] = list(dict.fromkeys(target["urls"] + urls))
            for provenance in record["sources"]:
                match = {"query": record["query"], **provenance,
                         "provider": item.get("search_provider", provider)}
                if provider == "naver":
                    match["sort"] = item.get("search_sort", "sim")
                if match not in target["search_matches"]:
                    target["search_matches"].append(match)
            target["discovered_by_event"] |= any(provenance["scope"] == "event" for provenance in record["sources"])
    pool = [item for item in pool if not item["keys"] & source_keys]
    selected = _select_candidate_metadata(pool, [claim["claim_id"] for claim in analysis["claims"]],
                                          max_candidates, event_reserve,
                                          event_queries=_reviewed_event_queries(analysis))
    for position, item in enumerate(selected, 1):
        item["candidate_id"] = f"A{position}"
    return {"queries": queries, "metadata_candidates": [
                {key: value for key, value in item.items() if key != "keys"} for item in selected],
            "discovered_count": len(pool), "errors": errors,
            "search_status": "failed" if errors and not succeeded else "partial" if errors else "success"}


def fetch_candidate_articles(metadata: list[dict]) -> dict:
    """Download only the chosen metadata rows, preserving their stable IDs."""
    ids = [item["candidate_id"] for item in metadata]
    if len(ids) != len(set(ids)):
        raise ValueError("본문 수집 대상의 후보 ID가 중복되었습니다.")
    candidates, failures = [], []
    if metadata:
        with ThreadPoolExecutor(max_workers=min(4, len(metadata))) as executor:
            for article, failure in executor.map(_fetch_candidate, metadata):
                if article is not None:
                    candidates.append(article)
                if failure is not None:
                    failures.append(failure)
    return {"candidates": candidates, "fetch_failures": failures, "fetch_attempt_count": len(metadata), "errors": failures}


def collect_candidate_articles(source: dict, analysis: dict, *, max_candidates: int = 20,
                               display_per_query: int = 10, event_reserve: int = 5) -> dict:
    """Legacy eager collection; the main pipeline now selects metadata first."""
    search = search_candidate_metadata(source, analysis, max_candidates=max_candidates,
                                       display_per_query=display_per_query, event_reserve=event_reserve)
    fetched = fetch_candidate_articles(search["metadata_candidates"])
    return {**search, **fetched, "errors": search["errors"] + fetched["errors"]}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="NAVER API HUB 뉴스 검색 확인")
    parser.add_argument("query")
    for item in search_naver_news(parser.parse_args().query):
        print(item["title"], item["link"])
