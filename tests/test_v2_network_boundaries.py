"""Offline checks for v2 article egress and credentialed search redirects."""

from io import BytesIO
import socket
from unittest import TestCase
from unittest.mock import patch, sentinel
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlsplit
from urllib.request import ProxyHandler, Request

from server.analysis.v2.article_fetch import (
    ArticleFetchError, _PublicHTTPHandler, _PublicHTTPSHandler,
    _PublicRedirectHandler, _public_addresses, fetch_article,
)
from server.analysis.v2.article_search import (
    _NEWS_SEARCH_URL, _NoCredentialRedirects, _search_naver_news_sorted, search_naver_news,
)
from server.analysis.v2.result_output import OperationError, make_error_record


def _answer(address: str, port: int = 443):
    if ":" in address:
        return (socket.AF_INET6, socket.SOCK_STREAM, 6, "", (address, port, 0, 0))
    return (socket.AF_INET, socket.SOCK_STREAM, 6, "", (address, port))


class ArticleEgressTests(TestCase):
    def test_unsafe_address_has_stable_error_code_without_host_detail(self):
        error = make_error_record(ArticleFetchError("private upstream detail", stage="unsafe_address"),
                                  stage="candidate_fetch")
        self.assertEqual(error["code"], "ARTICLE_UNSAFE_ADDRESS")
        self.assertNotIn("private upstream detail", str(error))

    def test_dns_rejects_private_and_mixed_answers(self):
        for answers in (["127.0.0.1"], ["8.8.8.8", "10.0.0.4"],
                        ["169.254.169.254"], ["2606:4700:4700::1111", "fc00::1"]):
            with self.subTest(answers=answers), patch(
                "server.analysis.v2.article_fetch.socket.getaddrinfo",
                return_value=[_answer(address) for address in answers],
            ):
                with self.assertRaises(ArticleFetchError) as raised:
                    _public_addresses("https://news.example.com/story")
                self.assertEqual(raised.exception.stage, "unsafe_address")

    def test_dns_resolution_failure_has_explicit_stage(self):
        with patch("server.analysis.v2.article_fetch.socket.getaddrinfo",
                   side_effect=socket.gaierror("private resolver detail")):
            with self.assertRaises(ArticleFetchError) as raised:
                _public_addresses("https://news.example.com/story")
        self.assertEqual(raised.exception.stage, "dns")
        self.assertNotIn("private resolver detail", str(raised.exception))

    def test_direct_https_pins_public_ip_but_keeps_origin_hostname(self):
        request = Request("https://news.example.com/story")
        handler = _PublicHTTPSHandler()
        captured = {}

        def inspect_connection(factory, req):
            connection = factory(req.host, timeout=2)
            captured["host"] = connection.host
            captured["check_hostname"] = connection._context.check_hostname
            with patch("server.analysis.v2.article_fetch.socket.create_connection",
                       return_value=sentinel.socket) as connect:
                self.assertIs(connection._create_connection((req.host, 443), 2, None), sentinel.socket)
            captured["address"] = connect.call_args.args[0]
            return sentinel.response

        with (patch("server.analysis.v2.article_fetch.socket.getaddrinfo",
                    return_value=[_answer("8.8.8.8")]),
              patch.object(handler, "do_open", side_effect=inspect_connection)):
            self.assertIs(handler.https_open(request), sentinel.response)
        self.assertEqual(captured, {"host": "news.example.com", "check_hostname": True,
                                    "address": ("8.8.8.8", 443)})

    def test_direct_http_also_pins_public_ip(self):
        request = Request("http://news.example.com/story")
        handler = _PublicHTTPHandler()

        def inspect_connection(factory, req):
            connection = factory(req.host, timeout=2)
            with patch("server.analysis.v2.article_fetch.socket.create_connection",
                       return_value=sentinel.socket) as connect:
                connection._create_connection((req.host, 80), 2, None)
            self.assertEqual(connection.host, "news.example.com")
            self.assertEqual(connect.call_args.args[0], ("8.8.8.8", 80))
            return sentinel.response

        with (patch("server.analysis.v2.article_fetch.socket.getaddrinfo",
                    return_value=[_answer("8.8.8.8", 80)]),
              patch.object(handler, "do_open", side_effect=inspect_connection)):
            self.assertIs(handler.http_open(request), sentinel.response)

    def test_redirect_target_is_resolved_again_before_connection(self):
        initial = Request("https://news.example.com/first")
        redirected = _PublicRedirectHandler().redirect_request(
            initial, None, 302, "Found", {}, "https://rebound.example/story"
        )
        handler = _PublicHTTPSHandler()
        with (patch("server.analysis.v2.article_fetch.socket.getaddrinfo",
                    return_value=[_answer("10.0.0.1")]),
              patch.object(handler, "do_open") as do_open):
            with self.assertRaises(ArticleFetchError) as raised:
                handler.https_open(redirected)
        self.assertEqual(raised.exception.stage, "unsafe_address")
        do_open.assert_not_called()

    def test_configured_https_proxy_is_rejected(self):
        request = Request("https://news.example.com/story")
        request.set_proxy("proxy.corp.example:8080", "https")
        self.assertEqual(request._tunnel_host, "news.example.com")
        handler = _PublicHTTPSHandler()
        with (patch("server.analysis.v2.article_fetch._public_addresses",
                    side_effect=AssertionError("proxy path must stop before DNS")),
              self.assertRaises(ArticleFetchError) as raised):
            handler.https_open(request)
        self.assertEqual(raised.exception.stage, "unsafe_proxy")

    def test_configured_http_proxy_is_rejected(self):
        request = Request("http://news.example.com/story")
        request.set_proxy("proxy.corp.example:8080", "http")
        self.assertTrue(request.has_proxy())
        handler = _PublicHTTPHandler()
        with (patch("server.analysis.v2.article_fetch._public_addresses",
                    side_effect=AssertionError("proxy path must stop before DNS")),
              self.assertRaises(ArticleFetchError) as raised):
            handler.http_open(request)
        self.assertEqual(raised.exception.stage, "unsafe_proxy")

    def test_fetch_opener_disables_environment_proxies_for_user_url(self):
        with patch("server.analysis.v2.article_fetch.build_opener") as build:
            build.return_value.open.side_effect = ArticleFetchError("synthetic stop", stage="download")
            with self.assertRaises(ArticleFetchError):
                fetch_article("https://news.example.com/story")
        self.assertIsInstance(build.call_args.args[0], ProxyHandler)
        self.assertEqual(build.call_args.args[0].proxies, {})


class SearchCredentialTests(TestCase):
    def test_naver_transient_http_error_retries_once_on_same_safe_origin(self):
        payload = b'{"items": [{"title": "Example", "link": "https://news.example.com/a"}]}'
        failure = HTTPError(_NEWS_SEARCH_URL, 503, "unavailable", {}, None)
        with (patch("server.analysis.v2.article_search.get_naver_credentials",
                    return_value=("synthetic-id", "synthetic-secret")),
              patch("server.analysis.v2.article_search.build_opener") as build,
              patch("server.analysis.v2.article_search.uniform", return_value=0.0),
              patch("server.analysis.v2.article_search.sleep") as pause):
            build.return_value.open.side_effect = [failure, BytesIO(payload)]
            rows = _search_naver_news_sorted("가상 검색어", 10, "sim")
        self.assertEqual([row["title"] for row in rows], ["Example"])
        self.assertEqual(build.return_value.open.call_count, 2)
        pause.assert_called_once_with(0.2)
        for call in build.return_value.open.call_args_list:
            request = call.args[0]
            self.assertEqual(request.full_url.split("?", 1)[0], _NEWS_SEARCH_URL)
            self.assertEqual(request.get_header("X-ncp-apigw-api-key"), "synthetic-secret")

    def test_naver_429_and_403_never_retry_and_log_only_status(self):
        for status in (429, 403):
            with self.subTest(status=status), patch(
                "server.analysis.v2.article_search.get_naver_credentials",
                return_value=("synthetic-id", "synthetic-secret"),
            ), patch("server.analysis.v2.article_search.build_opener") as build, patch(
                "server.analysis.v2.article_search.sleep",
            ) as pause, self.assertLogs("server.analysis.v2.article_search", level="WARNING") as logs:
                build.return_value.open.side_effect = HTTPError(_NEWS_SEARCH_URL, status, "failed", {}, None)
                with self.assertRaises(OperationError) as raised:
                    _search_naver_news_sorted("비밀 검색어", 10, "date")
            self.assertEqual(raised.exception.http_status, status)
            self.assertEqual(raised.exception.code, "SEARCH_HTTP_ERROR")
            self.assertFalse(raised.exception.retryable)
            self.assertEqual(build.return_value.open.call_count, 1)
            pause.assert_not_called()
            self.assertEqual(logs.output, [
                f"WARNING:server.analysis.v2.article_search:naver_search_http_error upstream_status={status}"
            ])

    def test_naver_long_retry_after_does_not_retry_503_early(self):
        with (patch("server.analysis.v2.article_search.get_naver_credentials",
                    return_value=("synthetic-id", "synthetic-secret")),
              patch("server.analysis.v2.article_search.build_opener") as build,
              patch("server.analysis.v2.article_search.sleep") as pause):
            build.return_value.open.side_effect = HTTPError(
                _NEWS_SEARCH_URL, 503, "unavailable", {"Retry-After": "5"}, None)
            with self.assertRaises(OperationError) as raised:
                _search_naver_news_sorted("가상 검색어", 10, "sim")
        self.assertEqual(raised.exception.http_status, 503)
        self.assertEqual(build.return_value.open.call_count, 1)
        pause.assert_not_called()

    def test_naver_short_retry_after_is_honored(self):
        payload = b'{"items": []}'
        with (patch("server.analysis.v2.article_search.get_naver_credentials",
                    return_value=("synthetic-id", "synthetic-secret")),
              patch("server.analysis.v2.article_search.build_opener") as build,
              patch("server.analysis.v2.article_search.sleep") as pause):
            build.return_value.open.side_effect = [
                HTTPError(_NEWS_SEARCH_URL, 503, "unavailable", {"Retry-After": "0.4"}, None),
                BytesIO(payload),
            ]
            rows = _search_naver_news_sorted("가상 검색어", 10, "sim")
        self.assertEqual(rows, [])
        pause.assert_called_once_with(0.4)

    def test_naver_one_sort_success_remains_usable_after_other_sort_retries(self):
        payload = b'{"items": [{"title": "Example", "link": "https://news.example.com/a"}]}'

        def open_sorted(request, **_kwargs):
            sort = parse_qs(urlsplit(request.full_url).query)["sort"][0]
            if sort == "date":
                raise HTTPError(_NEWS_SEARCH_URL, 503, "unavailable", {}, None)
            return BytesIO(payload)

        with (patch("server.analysis.v2.article_search.get_naver_credentials",
                    return_value=("synthetic-id", "synthetic-secret")),
              patch("server.analysis.v2.article_search.build_opener") as build,
              patch("server.analysis.v2.article_search.sleep")):
            build.return_value.open.side_effect = open_sorted
            rows = search_naver_news("가상 검색어")
        self.assertEqual([row["title"] for row in rows], ["Example"])
        self.assertEqual([sort for sort, _error in rows.sort_errors], ["date"])
        self.assertEqual(build.return_value.open.call_count, 3)

    def test_naver_success_uses_fixed_origin_and_keeps_credentials_on_initial_request(self):
        payload = b'{"items": [{"title": "Example", "link": "https://news.example.com/a"}]}'
        with (patch("server.analysis.v2.article_search.get_naver_credentials",
                    return_value=("synthetic-id", "synthetic-secret")),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.side_effect = lambda *_args, **_kwargs: BytesIO(payload)
            rows = search_naver_news("가상 검색어")
        self.assertEqual(build.return_value.open.call_count, 2)
        for call in build.return_value.open.call_args_list:
            request = call.args[0]
            self.assertEqual(request.full_url.split("?", 1)[0], _NEWS_SEARCH_URL)
            self.assertEqual(request.get_header("X-ncp-apigw-api-key"), "synthetic-secret")
        self.assertEqual(rows[0]["title"], "Example")

    def test_naver_redirect_is_not_followed_with_api_headers(self):
        request = Request(_NEWS_SEARCH_URL, headers={"X-NCP-APIGW-API-KEY": "synthetic-secret"})
        self.assertIsNone(_NoCredentialRedirects().redirect_request(
            request, None, 302, "Found", {}, "https://other.example/collect"
        ))

    def test_naver_redirect_becomes_explicit_nonretryable_error(self):
        redirect = HTTPError(_NEWS_SEARCH_URL, 302, "Found", {}, None)
        with (patch("server.analysis.v2.article_search.get_naver_credentials",
                    return_value=("synthetic-id", "synthetic-secret")),
              patch("server.analysis.v2.article_search.build_opener") as build):
            build.return_value.open.side_effect = redirect
            with self.assertRaises(OperationError) as raised:
                search_naver_news("가상 검색어")
        self.assertIsInstance(build.call_args.args[0], _NoCredentialRedirects)
        self.assertEqual(raised.exception.code, "SEARCH_REDIRECT_BLOCKED")
        self.assertFalse(raised.exception.retryable)
