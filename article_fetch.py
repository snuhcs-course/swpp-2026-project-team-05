"""Download one news URL and return its main article body as plain text."""

from __future__ import annotations

import os
from html import unescape
from html.parser import HTMLParser
from urllib.parse import urlsplit
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener

from network_tls import configure_tls


configure_tls()

DEFAULT_ARTICLE_HOSTS = "n.news.naver.com,news.naver.com,m.news.naver.com"
MAX_ARTICLE_BYTES = 4 * 1024 * 1024


def validate_article_url(url: str) -> str:
    """Allow only explicitly trusted news hosts, including after redirects."""
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
    if parsed.username or parsed.password or port is not None:
        raise ValueError("사용자 정보나 별도 포트가 있는 URL은 지원하지 않습니다.")
    allowed = {
        host.strip().lower()
        for host in os.getenv("ALLOWED_ARTICLE_HOSTS", DEFAULT_ARTICLE_HOSTS).split(",")
        if host.strip()
    }
    if parsed.hostname.lower() not in allowed:
        raise ValueError("현재 허용된 뉴스 사이트의 기사 URL만 분석할 수 있습니다.")
    return url


class _AllowedRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        validate_article_url(new_url)
        return super().redirect_request(request, response, code, message, headers, new_url)


class _TitleParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.meta_title = ""
        self.page_title = ""
        self._inside_title = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "meta" and attributes.get("property") == "og:title":
            self.meta_title = unescape(attributes.get("content") or "").strip()
        elif tag == "title":
            self._inside_title = True

    def handle_endtag(self, tag: str) -> None:
        if tag == "title":
            self._inside_title = False

    def handle_data(self, data: str) -> None:
        if self._inside_title:
            self.page_title += data


def fetch_article(url: str) -> dict[str, str]:
    """Return a news page's title and main body from one HTTP(S) request."""
    url = validate_article_url(url)

    try:
        from trafilatura import extract
    except ImportError as exc:
        raise RuntimeError("Install the article extractor: pip install trafilatura") from exc

    request = Request(url, headers={
        "User-Agent": "Mozilla/5.0 (compatible; FrameLESS/1.0)",
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Encoding": "identity",
    })
    opener = build_opener(_AllowedRedirectHandler())
    try:
        with opener.open(request, timeout=15) as response:
            if response.headers.get_content_type() not in ("text/html", "application/xhtml+xml"):
                raise RuntimeError("The article URL did not return HTML")
            raw_html = response.read(MAX_ARTICLE_BYTES + 1)
            if len(raw_html) > MAX_ARTICLE_BYTES:
                raise RuntimeError("The article page is too large")
            charset = response.headers.get_content_charset() or "utf-8"
            html = raw_html.decode(charset, errors="replace")
    except (HTTPError, URLError, TimeoutError) as exc:
        raise RuntimeError("Could not download the article page") from exc

    body = extract(html, url=url, include_comments=False)
    if not body or not body.strip():
        raise RuntimeError("Could not find article text on this page")
    parser = _TitleParser()
    parser.feed(html)
    title = parser.meta_title or unescape(parser.page_title).strip()
    return {"url": url, "title": title, "body": body.strip()}


def fetch_article_body(url: str) -> str:
    """Return only article text for existing callers."""
    return fetch_article(url)["body"]


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Print the body of one news URL")
    parser.add_argument("url", help="Article URL")
    args = parser.parse_args()
    print(fetch_article_body(args.url))
