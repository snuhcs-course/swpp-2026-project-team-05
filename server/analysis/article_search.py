"""Use Naver only to discover article URLs, then run the existing analysis."""

from __future__ import annotations

import json
import os
import re
from html import unescape
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen

from .article_fetch import fetch_article
from .llm_analysis import analyze_article, compare_issue_passages
from .model_client import GeminiJSONClient


_NEWS_SEARCH_URL = "https://naverapihub.apigw.ntruss.com/search/v1/news"


def _plain_text(value: str) -> str:
    return re.sub(r"<[^>]+>", "", unescape(value or "")).strip()


def _url_key(url: str) -> str:
    parsed = urlsplit(url)
    if parsed.hostname in ("n.news.naver.com", "news.naver.com"):
        match = re.search(r"/article/(\d+)/(\d+)", parsed.path)
        if match:
            return f"naver:{match.group(1)}:{match.group(2)}"
    return f"{parsed.hostname or ''}{parsed.path.rstrip('/')}".lower()


def build_search_query(core_event: str, title: str) -> str:
    """Build a short search query from words already in the title and event."""
    if not core_event.strip() and not title.strip():
        raise ValueError("A title or core event is required")
    stopwords = {"기사", "것", "그것", "이번", "지난", "오늘", "밝혔다", "전했다"}
    generic_prefixes = ("논란", "발언", "관련", "보도", "대해", "대한")
    title_words = re.findall(r"[가-힣A-Za-z0-9]{2,}", title)
    event_words = re.findall(r"[가-힣A-Za-z0-9]{2,}", core_event)
    words = title_words + event_words
    terms = []
    for word in words:
        if word in stopwords or word.startswith(generic_prefixes) or word in terms or word.isdigit():
            continue
        terms.append(word)
        if len(terms) == 4:
            break
    if not terms:
        raise ValueError("The article title and event contain no usable search terms")
    return " ".join(terms)


def search_naver_news(query: str, display: int = 50) -> list[dict]:
    """Return candidate metadata from the Naver News Search API."""
    client_id = os.getenv("NAVER_CLIENT_ID", "").strip()
    client_secret = os.getenv("NAVER_CLIENT_SECRET", "").strip()
    if not client_id or not client_secret:
        raise RuntimeError("Set NAVER_CLIENT_ID and NAVER_CLIENT_SECRET in the environment")
    if not query.strip():
        raise ValueError("Search query must not be empty")
    if not 1 <= display <= 100:
        raise ValueError("display must be between 1 and 100")
    url = _NEWS_SEARCH_URL + "?" + urlencode({"query": query, "display": display, "start": 1, "sort": "sim", "format": "json"})
    request = Request(url, headers={
        "X-NCP-APIGW-API-KEY-ID": client_id,
        "X-NCP-APIGW-API-KEY": client_secret,
        "Accept": "application/json",
    })
    try:
        with urlopen(request, timeout=10) as response:
            payload = json.load(response)
    except HTTPError as exc:
        raise RuntimeError(f"Naver News Search API returned HTTP {exc.code}") from exc
    except (URLError, TimeoutError) as exc:
        raise RuntimeError("Could not reach the Naver News Search API") from exc
    items = payload.get("items")
    if not isinstance(items, list):
        raise ValueError("Naver News Search API returned no item list")
    return [
        {
            "title": _plain_text(item.get("title", "")),
            "description": _plain_text(item.get("description", "")),
            "link": unescape(item.get("link", "")),
            "originallink": unescape(item.get("originallink", "")),
            "published_at": item.get("pubDate", ""),
        }
        for item in items if isinstance(item, dict)
    ]


def find_related_articles(
    core_event: str,
    title: str,
    *,
    source_url: str = "",
    max_candidates: int = 20,
) -> list[dict]:
    """Return Naver search candidates after URL deduplication only."""
    if max_candidates < 1:
        raise ValueError("max_candidates must be positive")
    query = build_search_query(core_event, title)
    search_results = search_naver_news(query)
    seen = {_url_key(source_url)} if source_url else set()
    candidates = []
    for item in search_results:
        if len(candidates) >= max_candidates:
            break
        urls = [url for url in (item["link"], item["originallink"])
                if urlsplit(url).scheme in ("http", "https") and urlsplit(url).hostname]
        keys = {_url_key(url) for url in urls}
        if not urls or keys & seen:
            continue
        seen.update(keys)
        candidates.append({
            "url": urls[0],
            "fallback_url": urls[1] if len(urls) > 1 else "",
            "title": item["title"],
            "description": item["description"],
            "published_at": item["published_at"],
        })
    return candidates


def _same_event_indices(source: dict, candidates: list[dict]) -> set[int]:
    """Check whether fetched candidate bodies describe the source's core event."""
    response = GeminiJSONClient().generate_json(
        name="same_news_event",
        instructions=(
            "원 기사의 핵심 사건과 동일한 구체적 사건을 다루는 후보의 번호만 반환하세요. "
            "인물·주제가 같아도 행동, 대상 또는 사건 시점이 다르면 제외하세요. "
            "후속 사건이나 원 기사의 부수적인 화제만 다루는 기사도 제외하세요. "
            "일치 여부가 불분명하면 제외하고, 발행 시각이 가깝다는 이유만으로 일치시키지 마세요."
        ),
        input_data={
            "source": {
                "title": source["title"],
                "core_event": source["core_event"],
                "lead": source["body"][:800],
            },
            "candidates": [
                {"index": index, "title": item["title"], "lead": item["body"][:800]}
                for index, item in enumerate(candidates)
            ],
        },
        schema={
            "type": "object",
            "properties": {"matching_indices": {"type": "array", "items": {"type": "integer"}}},
            "required": ["matching_indices"],
            "additionalProperties": False,
        },
        max_output_tokens=512,
    )
    indices = response.get("matching_indices")
    if not isinstance(indices, list):
        raise ValueError("Gemini returned no event match list")
    return {index for index in indices if type(index) is int and 0 <= index < len(candidates)}


def analyze_related_articles(source_url: str, max_related: int = 3) -> dict:
    """Fully analyze the source; compare only retrieved passages from related news."""
    if max_related < 1:
        raise ValueError("max_related must be positive")
    source = fetch_article(source_url)
    first = analyze_article(source["body"], title=source["title"], url=source["url"])
    related = find_related_articles(first["core_event"], first["title"], source_url=source["url"])
    matched_articles = []
    screened = []
    batch_size = max(5, max_related * 2)
    for start in range(0, len(related), batch_size):
        if len(matched_articles) >= max_related:
            break
        fetched = []
        for candidate in related[start:start + batch_size]:
            article = None
            for url in (candidate["url"], candidate["fallback_url"]):
                if not url:
                    continue
                try:
                    article = fetch_article(url)
                    break
                except (RuntimeError, ValueError):
                    continue
            if article is None:
                screened.append({"url": candidate["url"], "status": "fetch_failed"})
                continue
            article["title"] = article["title"] or candidate["title"]
            fetched.append(article)
        if not fetched:
            continue
        matched = _same_event_indices(first, fetched)
        for index, article in enumerate(fetched):
            is_match = index in matched
            screened.append({"url": article["url"], "status": "same_event" if is_match else "different_or_uncertain"})
            if not is_match or len(matched_articles) >= max_related:
                continue
            matched_articles.append(article)
    comparison = compare_issue_passages(first, matched_articles)
    return {
        "source_analysis": first,
        "related_articles": matched_articles,
        "candidates": related,
        "screened": screened,
        "comparison": comparison,
    }


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Test NAVER API HUB news search")
    parser.add_argument("query", help="News search keywords")
    args = parser.parse_args()
    for item in search_naver_news(args.query, display=5):
        print(f"{item['title']}\n{item['link']}\n")
