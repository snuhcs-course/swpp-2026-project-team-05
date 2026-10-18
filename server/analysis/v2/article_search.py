"""Search with LLM-generated queries, deduplicate URLs, and collect full bodies."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import json
import re
from html import unescape
from http.client import HTTPException
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qsl, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from .model_client import get_naver_credentials
from .article_fetch import validate_article_url
from .article_input import prepare_article
from .result_output import OperationError, make_error_record


_NEWS_SEARCH_URL = "https://naverapihub.apigw.ntruss.com/search/v1/news"


class _NoCredentialRedirects(HTTPRedirectHandler):
    """Do not forward NAVER API credentials to a redirected origin."""

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


def search_naver_news(query: str, display: int = 10) -> list[dict]:
    """Call NAVER API HUB; return metadata, not article bodies."""
    if not isinstance(query, str) or not query.strip():
        raise ValueError("검색어가 비어 있습니다.")
    if type(display) is not int or not 1 <= display <= 100:
        raise ValueError("display는 1~100의 정수여야 합니다.")
    client_id, client_secret = get_naver_credentials()
    if not client_id or not client_secret:
        raise OperationError("NAVER API HUB의 Client ID·Secret을 설정해 주세요.", code="NAVER_CONFIGURATION_MISSING")
    request = Request(_NEWS_SEARCH_URL + "?" + urlencode({
        "query": query.strip(), "display": display, "start": 1, "sort": "sim", "format": "json",
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
             "search_provided_at": item.get("pubDate") or ""}
            for item in payload["items"] if isinstance(item, dict)]


def _query_plan(analysis: dict) -> list[dict]:
    queries = {}
    entries = [(analysis["event_query"], {"scope": "event", "claim_id": None})]
    entries += [(claim["search_query"], {"scope": "claim", "claim_id": claim["claim_id"]})
                for claim in analysis["claims"]]
    for query, source in entries:
        query = " ".join(query.split())
        if not query:
            raise ValueError("LLM 검색어가 비어 있습니다.")
        record = queries.setdefault(query, {"query": query, "sources": []})
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


def _select_candidate_metadata(pool: list[dict], claim_ids: list[str], limit: int, reserve: int) -> list[dict]:
    """Keep event-search coverage, then allocate slots across claim queries."""
    selected = [item for item in pool if item["discovered_by_event"]][:min(reserve, limit)]
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
    for item in pool:
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
    """Search and deduplicate metadata; do not download any candidate bodies."""
    if type(max_candidates) is not int or not 1 <= max_candidates <= 100:
        raise ValueError("max_candidates는 1~100의 정수여야 합니다.")
    if type(event_reserve) is not int or event_reserve < 1:
        raise ValueError("event_reserve는 양의 정수여야 합니다.")
    if type(display_per_query) is not int or not 1 <= display_per_query <= 100:
        raise ValueError("display_per_query는 1~100의 정수여야 합니다.")
    queries, pool = _query_plan(analysis), []
    source_keys = {normalize_news_url(source["url"])}
    errors, succeeded, index = [], 0, 0
    while index < len(queries):
        record = queries[index]
        index += 1
        try:
            items = search_naver_news(record["query"], display_per_query)
        except RuntimeError as exc:
            error = make_error_record(exc, stage="search", query=record["query"])
            errors.append(error)
            record.update({"status": "search_failed", "result_count": 0, "error": error})
            continue
        succeeded += 1
        record.update({"status": "searched", "result_count": len(items)})
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
                match = {"query": record["query"], **provenance}
                if match not in target["search_matches"]:
                    target["search_matches"].append(match)
            target["discovered_by_event"] |= any(provenance["scope"] == "event" for provenance in record["sources"])
        if index == 1 and len([item for item in pool if item["discovered_by_event"]]) < min(3, max_candidates):
            words = record["query"].split()
            if len(words) > 2:
                shorter = " ".join(words[:2])
                existing = next((query for query in queries if query["query"] == shorter), None)
                if existing is None:
                    queries.insert(1, {"query": shorter, "sources": [{"scope": "event", "claim_id": None}]})
                elif {"scope": "event", "claim_id": None} not in existing["sources"]:
                    existing["sources"].append({"scope": "event", "claim_id": None})
    pool = [item for item in pool if not item["keys"] & source_keys]
    selected = _select_candidate_metadata(pool, [claim["claim_id"] for claim in analysis["claims"]],
                                          max_candidates, event_reserve)
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
