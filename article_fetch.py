"""Download one news URL and return its main article body as plain text."""

from __future__ import annotations

import ipaddress
import json
from html import unescape
from html.parser import HTMLParser
from http.client import HTTPException, IncompleteRead
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener




MAX_ARTICLE_BYTES = 4 * 1024 * 1024


class ArticleFetchError(RuntimeError):
    """Distinguish HTTP/download failures from a missing extractable body."""

    def __init__(self, message: str, *, stage: str, http_status: int | None = None):
        super().__init__(message)
        self.stage = stage
        self.http_status = http_status


def validate_article_url(url: str) -> str:
    """Reject malformed and local-network URLs before fetching an article."""
    if not isinstance(url, str) or not url.strip():
        raise ValueError("기사 URL을 입력해 주세요.")
    url = url.strip()
    try:
        parsed = urlsplit(url)
        port = parsed.port
    except ValueError as exc:
        raise ValueError("올바른 기사 URL이 아닙니다.") from exc
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise ValueError("http 또는 https 기사 URL을 입력해 주세요.")
    if parsed.username is not None or parsed.password is not None or port is not None:
        raise ValueError("사용자 정보나 별도 포트가 있는 URL은 지원하지 않습니다.")
    hostname = parsed.hostname.rstrip(".").lower()
    if hostname == "localhost" or hostname.endswith((".local", ".internal", ".localhost")):
        raise ValueError("로컬 네트워크 주소는 기사 URL로 사용할 수 없습니다.")
    try:
        ipaddress.ip_address(hostname)
    except ValueError:
        pass
    else:
        raise ValueError("IP 주소는 기사 URL로 사용할 수 없습니다.")
    if "." not in hostname:
        raise ValueError("공개 웹사이트의 기사 URL을 입력해 주세요.")
    return url


class _PublicRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        validate_article_url(new_url)
        return super().redirect_request(request, response, code, message, headers, new_url)


class _TitleParser(HTMLParser):
    def __init__(self, url: str = "") -> None:
        super().__init__()
        self.meta_title = ""
        self.page_title = ""
        self.publisher = ""
        self.site_name = ""
        self.published_at = ""
        self._inside_title = False
        self._inside_json_ld = False
        self._json_ld_parts: list[str] = []
        self._naver_article = urlsplit(url).hostname in ("n.news.naver.com", "news.naver.com")

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "meta":
            key = (attributes.get("property") or attributes.get("name") or "").lower()
            value = unescape(attributes.get("content") or "").strip()
            if key == "og:title":
                self.meta_title = value
            elif key == "og:site_name":
                self.site_name = value
            elif key in ("article:published_time", "datepublished") and not self.published_at:
                self.published_at = value
            elif self._naver_article and key == "og:article:author":
                # Naver supplies the publisher here, e.g. 'MBN | 네이버'.
                self.publisher = value.split(" | ", 1)[0].strip()
        elif tag == "title":
            self._inside_title = True
        elif tag == "script" and (attributes.get("type") or "").lower() == "application/ld+json":
            self._inside_json_ld = True
            self._json_ld_parts = []
        if (
            self._naver_article and not self.published_at
            and "_ARTICLE_DATE_TIME" in (attributes.get("class") or "").split()
        ):
            # Preserve the timestamp as supplied; do not substitute update time.
            self.published_at = (attributes.get("data-date-time") or "").strip()

    def handle_endtag(self, tag: str) -> None:
        if tag == "title":
            self._inside_title = False
        elif tag == "script" and self._inside_json_ld:
            self._inside_json_ld = False
            try:
                self._read_article_metadata(json.loads("".join(self._json_ld_parts)))
            except (ValueError, RecursionError):
                # Malformed optional metadata must not prevent body extraction.
                pass

    def handle_data(self, data: str) -> None:
        if self._inside_title:
            self.page_title += data
        elif self._inside_json_ld:
            self._json_ld_parts.append(data)

    def _read_article_metadata(self, value: object) -> None:
        if isinstance(value, list):
            for item in value:
                self._read_article_metadata(item)
        elif isinstance(value, dict):
            types = value.get("@type", [])
            if isinstance(types, str):
                types = [types]
            if isinstance(types, list) and any(
                kind in ("Article", "NewsArticle", "ReportageNewsArticle") for kind in types
            ):
                published = value.get("datePublished")
                if not self.published_at and isinstance(published, str):
                    self.published_at = published.strip()
                publisher = value.get("publisher")
                if isinstance(publisher, dict):
                    publisher = publisher.get("name")
                if not self.publisher and isinstance(publisher, str):
                    self.publisher = publisher.strip()
            self._read_article_metadata(value.get("@graph"))


def fetch_article(url: str) -> dict[str, str]:
    """Return a news page's title and main body from one HTTP(S) request."""
    url = validate_article_url(url)

    try:
        from trafilatura import extract
        from trafilatura.utils import decode_file
    except ImportError as exc:
        raise RuntimeError("Install the article extractor: pip install trafilatura") from exc

    request = Request(url, headers={
        "User-Agent": "Mozilla/5.0 (compatible; FrameLESS/1.0)",
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Encoding": "identity",
    })
    opener = build_opener(_PublicRedirectHandler())
    try:
        with opener.open(request, timeout=15) as response:
            if response.headers.get_content_type() not in ("text/html", "application/xhtml+xml"):
                raise ArticleFetchError("기사 URL의 응답이 HTML이 아닙니다.", stage="content_type")
            raw_html = response.read(MAX_ARTICLE_BYTES + 1)
            if len(raw_html) > MAX_ARTICLE_BYTES:
                raise ArticleFetchError("기사 페이지가 수집 용량 제한을 초과했습니다.", stage="size_limit")
            length = response.headers.get("Content-Length", "")
            if length.isdigit() and len(raw_html) < int(length):
                raise ArticleFetchError("기사 응답이 도중에 끊어졌습니다.", stage="incomplete_response")
            charset = response.headers.get_content_charset()
            try:
                html = raw_html.decode(charset) if charset else decode_file(raw_html)
            except (LookupError, UnicodeDecodeError):
                html = decode_file(raw_html)
    except HTTPError as exc:
        raise ArticleFetchError(f"기사 서버가 HTTP {exc.code}를 반환했습니다.",
                                stage="http", http_status=exc.code) from exc
    except IncompleteRead as exc:
        raise ArticleFetchError("기사 응답이 도중에 끊어졌습니다.", stage="incomplete_response") from exc
    except (URLError, OSError, HTTPException) as exc:
        raise ArticleFetchError("기사 서버에 연결하지 못했거나 응답 시간이 초과됐습니다.",
                                stage="download") from exc

    body = extract(html, url=url, include_comments=False)
    if not body or not body.strip():
        raise ArticleFetchError("HTML에서 기사 본문을 찾지 못했습니다.", stage="extraction")
    parser = _TitleParser(url)
    parser.feed(html)
    title = parser.meta_title or unescape(parser.page_title).strip()
    return {
        "url": url,
        "title": title,
        "body": body.strip(),
        "publisher": parser.publisher,
        "site_name": parser.site_name,
        "published_at": parser.published_at,
    }


def fetch_article_body(url: str) -> str:
    """Return only article text for existing callers."""
    return fetch_article(url)["body"]


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Print the body of one news URL")
    parser.add_argument("url", help="Article URL")
    args = parser.parse_args()
    print(fetch_article_body(args.url))
