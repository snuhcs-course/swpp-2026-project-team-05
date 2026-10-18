"""Download one news URL and return its main article body as plain text."""

from __future__ import annotations

import ipaddress
import json
import re
import socket
from html import unescape
from html.parser import HTMLParser
from http.client import HTTPConnection, HTTPSConnection, HTTPException, IncompleteRead
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPHandler, HTTPSHandler, HTTPRedirectHandler, ProxyHandler, Request, build_opener




MAX_ARTICLE_BYTES = 4 * 1024 * 1024
_YNA_HOSTS = frozenset({"yna.co.kr", "www.yna.co.kr"})
_YNA_CONTACT = re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}")


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


def _public_addresses(url: str) -> tuple[str, ...]:
    """Resolve every address for this hop; reject mixed public/private answers."""
    parsed = urlsplit(validate_article_url(url))
    port = 443 if parsed.scheme == "https" else 80
    addresses = []
    try:
        answers = socket.getaddrinfo(parsed.hostname, port, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise ArticleFetchError("기사 서버 주소를 확인하지 못했습니다.", stage="dns") from exc
    for _family, _type, _proto, _name, sockaddr in answers:
        address = sockaddr[0]
        if not ipaddress.ip_address(address).is_global:
            raise ArticleFetchError("공개 네트워크 주소가 아닌 기사 서버에는 연결할 수 없습니다.",
                                    stage="unsafe_address")
        if address not in addresses:
            addresses.append(address)
    if not addresses:
        raise ArticleFetchError("기사 서버 주소를 확인하지 못했습니다.", stage="dns")
    return tuple(addresses)


def _pinned_connection(connection_type, host: str, addresses: tuple[str, ...], **kwargs):
    """Keep the original Host/SNI while connecting only to vetted numeric IPs."""
    connection = connection_type(host, **kwargs)

    def connect(address, timeout, source_address):
        last_error = None
        for ip in addresses:
            try:
                return socket.create_connection((ip, address[1]), timeout, source_address)
            except OSError as exc:
                last_error = exc
        raise last_error or OSError("No public address could be reached")

    connection._create_connection = connect
    return connection


class _PublicHTTPHandler(HTTPHandler):
    def http_open(self, request):
        if request.has_proxy():
            raise ArticleFetchError("기사 서버에 안전하게 연결할 수 없습니다.", stage="unsafe_proxy")
        addresses = _public_addresses(request.full_url)
        return self.do_open(lambda host, **kwargs: _pinned_connection(
            HTTPConnection, host, addresses, **kwargs), request)


class _PublicHTTPSHandler(HTTPSHandler):
    def https_open(self, request):
        if request._tunnel_host:
            raise ArticleFetchError("기사 서버에 안전하게 연결할 수 없습니다.", stage="unsafe_proxy")
        addresses = _public_addresses(request.full_url)
        return self.do_open(lambda host, **kwargs: _pinned_connection(
            HTTPSConnection, host, addresses, context=self._context, **kwargs), request)


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


def _extract_yna_article_body(html: str) -> str | None:
    """Use YNA's article paragraphs, excluding its nearby summary and tooltips.

    Trafilatura may mistake the three-line summary and the Google source tooltip
    above ``#articleWrap`` for article text. YNA keeps the actual story as direct
    paragraph children of ``.story-news``; captions and related links are nested
    in other elements. A missing or changed structure falls back to Trafilatura.
    """
    from lxml import etree, html as lxml_html

    try:
        document = lxml_html.fromstring(html)
    except (etree.ParserError, ValueError):
        return None
    paragraphs = document.xpath(
        '//*[@id="articleWrap"]//div['
        'contains(concat(" ", normalize-space(@class), " "), " story-news ")'
        ']/p[not(contains(concat(" ", normalize-space(@class), " "), '
        '" txt-copyright "))]'
    )
    body = []
    for paragraph in paragraphs:
        text = " ".join(paragraph.text_content().split())
        if text and not _YNA_CONTACT.fullmatch(text):
            body.append(text)
    return "\n".join(body) or None


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
    # Arbitrary user URLs cannot rely on a proxy's independent DNS/egress policy.
    # This only changes article requests; it does not alter machine proxy settings.
    opener = build_opener(ProxyHandler({}), _PublicRedirectHandler(),
                          _PublicHTTPHandler(), _PublicHTTPSHandler())
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

    hostname = urlsplit(url).hostname
    body = (_extract_yna_article_body(html) if hostname in _YNA_HOSTS else None)
    if not body:
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
