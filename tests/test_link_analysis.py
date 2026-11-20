"""Link analysis: the endpoint contract, the stored reader-only cluster, and the staged analysis with a fake model.

Specification: specs/testing/link-analysis.spec.md. No network and no model: analyze_link, the discovery pipeline
and the model are replaced where a test needs them.
"""

import json
import re
from unittest import mock

from django.contrib.auth.hashers import make_password
from django.test import Client, TestCase, override_settings

from server import api_v1
from server.analysis.staged import analysis as staged
from server.analysis.staged.dataset import build_dataset
from server.analysis.staged.link import LinkAnalysisError, analyze_link
from server.authentication import create_session
from server.models import AnalysisRecord, Article, ExpressionFinding, FindingComparison, Publisher, UserAccount
from server.news_import import dataset_digest, import_dataset
from server.news_queries import list_articles

URL = "https://n.news.naver.com/mnews/article/001/0000000001"


def _article(url, publisher, sentences, title="제목"):
    body, rows = "", []
    for n, text in enumerate(sentences):
        start = len(body)
        body += text + "\n"
        rows.append({"sentence_index": n, "paragraph_index": n, "text": text, "start": start, "end": start + len(text)})
    return {"title": title, "body": body, "sentences": rows, "publisher": publisher, "url": url,
            "published_at": "2026-11-05T10:00:00+09:00"}


SOURCE = _article(URL, "가신문", ["더불어민주당은 이날 본회의에서 특검 법안을 강행 처리했다.",
                                  "국민의힘 의원들은 표결에 앞서 본회의장을 모두 떠났다."])
OTHER = _article("https://n.news.naver.com/mnews/article/002/0000000002", "나일보",
                 ["더불어민주당은 이날 본회의에서 특검 법안을 처리했다.",
                  "국민의힘 의원들은 표결에 앞서 본회의장을 모두 떠났다."])


def fake_model(step, prompt, schema, effort):
    """S2: every pair is the same event. S4a: the candidate «강행» against the comparison's «처리했다.». S4b: the
    expression with 강행 is the stronger one."""
    if step == "s2":
        pairs = re.findall(r"### (P\d+)", prompt)
        return {"pairs": [{"pair": p, "actor": "same", "act": "same", "scene": "same", "kind_l": "fact", "kind_r": "fact"}
                          for p in pairs]}
    if step == "s4a":
        out = []
        for block in re.split(r"(?=### P\d+)", prompt.split("# 짝과 후보", 1)[1]):
            head = re.match(r"### (P\d+)", block)
            if not head:
                continue
            right = re.search(r"^R: (.*)$", block, re.M).group(1)
            target = next((m.group(1) for m in re.finditer(r"(R\d+):(\S+)", right) if "처리했다" in m.group(2)), None)
            cands = [{"id": cid, "cp": [target] if target and "강행" in text else [], "who": "rep",
                      "kind": "att" if "강행" in text else "other"}
                     for cid, text in re.findall(r"- (C\d+) \([LR][\d-]+[LR]?\d*\) «([^»]*)»", block)]
            out.append({"pair": head.group(1), "candidates": cands})
        return {"pairs": out}
    items = []
    for n, first in re.findall(r"### (\d+)\n문장 1: ([^\n]*)", prompt):
        items.append({"n": int(n), "same": "y", "str": "1" if "[[강행" in first else "2", "seen": "y"})
    return {"items": items}


class StagedAnalysisTests(TestCase):
    def test_plain_predicates_on_both_sides_are_no_difference(self):
        a, b = staged.eojeols("명단이 홈페이지에 노출됐던 것으로 알려졌다."), staged.eojeols("명단이 사이트에 게시된 상태였다.")
        self.assertEqual(staged.word_list(a, [2], b, [2]), ("hide",))

    def test_an_evaluative_word_keeps_a_plain_predicate(self):
        a, b = staged.eojeols("명단이 무더기로 노출된 상태였다."), staged.eojeols("명단이 노출된 상태였다.")
        self.assertIsNone(staged.word_list(a, [1, 2], b, [1]))

    def test_a_bare_number_is_not_a_highlight_but_a_number_with_degree_word_is(self):
        words = staged.eojeols("물가가 1.4% 올랐고 3곳뿐이었다.")
        self.assertTrue(staged.numeric(words[1]))
        self.assertFalse(staged.numeric(words[3]))

    def test_one_evaluative_word_against_a_plain_report_is_shown_with_code_and_explanation(self):
        shown = staged.analyze({"S": SOURCE, "A1": OTHER}, fake_model)
        self.assertEqual(len(shown), 1)
        item = shown[0]
        self.assertIn("강행", item["source_text"])
        self.assertEqual(item["marked_side"], "source")
        self.assertEqual(item["code"], "C")
        self.assertIn("가신문", item["explanation"])

    def test_no_model_answer_shows_nothing(self):
        self.assertEqual(staged.analyze({"S": SOURCE, "A1": OTHER}, lambda *a: {}), [])


class StoredClusterTests(TestCase):
    def test_imported_cluster_opens_in_the_reader_but_not_in_the_feed(self):
        articles = {"S": SOURCE, "A1": OTHER}
        dataset = build_dataset(articles, staged.analyze(articles, fake_model), now="2026-11-05T12:00:00+09:00")
        import_dataset(dataset, dataset_hash=dataset_digest(dataset))
        source = Article.objects.get(canonical_url=URL)
        self.assertFalse(Article.objects.filter(is_feed_visible=True).exists())
        self.assertEqual(list_articles(user=None, category=None, publisher_codes=[], query=None, cursor=None,
                                       limit=20, scope="all")["items"], [])
        finding = ExpressionFinding.objects.get(article=source)
        self.assertEqual(source.body_text[finding.marker_start:finding.marker_end], finding.marker_text)
        comparison = FindingComparison.objects.get(expression_finding=finding)
        self.assertEqual(comparison.target_article.body_text[comparison.target_marker_start:comparison.target_marker_end],
                         comparison.target_marker_text)

    def test_a_stored_link_answers_at_once_without_discovery(self):
        articles = {"S": SOURCE, "A1": OTHER}
        dataset = build_dataset(articles, staged.analyze(articles, fake_model), now="2026-11-05T12:00:00+09:00")
        import_dataset(dataset, dataset_hash=dataset_digest(dataset))
        discover = mock.Mock()
        with mock.patch.dict("os.environ", {"OPENROUTER_API_KEY": ""}), \
                mock.patch("server.analysis.v2.article_input.prepare_article") as prepare:
            result = analyze_link(URL, ask=fake_model, discover=discover)
        self.assertEqual(result["status"], "existing")
        self.assertEqual(result["article_id"], str(Article.objects.get(canonical_url=URL).id))
        prepare.assert_not_called()
        discover.assert_not_called()

    def test_a_new_link_without_the_staged_model_key_is_unavailable(self):
        with mock.patch.dict("os.environ", {"OPENROUTER_API_KEY": ""}), \
                mock.patch("server.analysis.v2.article_input.prepare_article", return_value=SOURCE):
            with self.assertRaises(LinkAnalysisError) as raised:
                analyze_link(URL)
        self.assertEqual(raised.exception.code, "analysis_unavailable")
        self.assertTrue(raised.exception.retryable)

    def test_runtime_import_preserves_existing_publisher_metadata(self):
        articles = {"S": SOURCE, "A1": OTHER}
        dataset = build_dataset(articles, staged.analyze(articles, fake_model),
                                now="2026-11-05T12:00:00+09:00")
        source_publisher = dataset["publishers"][0]
        existing = Publisher.objects.create(
            code=source_publisher["code"],
            name_ko="검수된 언론사명",
            domain="publisher.example.com",
            logo_url="https://publisher.example.com/logo.png",
            display_order=42,
        )

        import_dataset(
            dataset,
            dataset_hash=dataset_digest(dataset),
            preserve_existing_publishers=True,
        )

        existing.refresh_from_db()
        self.assertEqual(existing.name_ko, "검수된 언론사명")
        self.assertEqual(existing.domain, "publisher.example.com")
        self.assertEqual(existing.logo_url, "https://publisher.example.com/logo.png")
        self.assertEqual(existing.display_order, 42)

    def test_no_same_event_article_is_an_error_for_the_reader(self):
        with mock.patch("server.analysis.v2.article_input.prepare_article", return_value=SOURCE):
            with self.assertRaises(LinkAnalysisError) as raised:
                analyze_link(URL, ask=fake_model, discover=lambda url, **kw: {"status": "no_same_event"})
        self.assertEqual(raised.exception.code, "no_same_event")
        self.assertFalse(Article.objects.exists())


@override_settings(ROOT_URLCONF="server.urls")
class LinkAnalysesEndpointTests(TestCase):
    def setUp(self):
        self.client = Client()
        user = UserAccount.objects.create(email="reader@example.com", username="reader",
                                          password_hash=make_password("safe-password-123"),
                                          is_developer=True)
        token, _ = create_session(user)
        self.auth = {"HTTP_AUTHORIZATION": f"Bearer {token}"}
        ordinary = UserAccount.objects.create(email="ordinary@example.com", username="ordinary",
                                              password_hash=make_password("safe-password-123"))
        ordinary_token, _ = create_session(ordinary)
        self.ordinary_auth = {"HTTP_AUTHORIZATION": f"Bearer {ordinary_token}"}

    def post(self, body, **headers):
        return self.client.post("/api/v1/link-analyses", data=json.dumps(body), content_type="application/json", **headers)

    def test_signed_in_reader_gets_the_stored_article_id(self):
        answer = {"article_id": "a1", "status": "created", "highlight_count": 3}
        with mock.patch.dict("os.environ", {"OPENROUTER_API_KEY": "test"}), \
                mock.patch.object(api_v1, "analyze_link", return_value=answer) as run:
            response = self.post({"url": URL}, **self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), answer)
        run.assert_called_once_with(URL)

    def test_a_guest_and_an_ordinary_account_can_analyse_a_link(self):
        answer = {"article_id": "a1", "status": "created", "highlight_count": 3}
        with mock.patch.object(api_v1, "analyze_link", return_value=answer) as run:
            guest = self.post({"url": URL})
            ordinary = self.post({"url": URL}, REMOTE_ADDR="10.0.0.2", **self.ordinary_auth)
        self.assertEqual((guest.status_code, ordinary.status_code), (200, 200))
        self.assertEqual(guest.json(), answer)
        self.assertEqual(run.call_count, 2)
        self.assertFalse(AnalysisRecord.objects.filter(user__isnull=False).exists())

    def test_a_client_that_is_not_a_developer_stops_at_its_daily_limit(self):
        answer = {"article_id": "a1", "status": "created", "highlight_count": 3}
        limits = {"FRAMELESS_LINK_ANALYSIS_PER_CLIENT_DAILY": "2", "FRAMELESS_LINK_ANALYSIS_DAILY": "30"}
        with mock.patch.dict("os.environ", limits), \
                mock.patch.object(api_v1, "analyze_link", return_value=answer) as run:
            answers = [self.post({"url": URL}), self.post({"url": URL}, **self.ordinary_auth), self.post({"url": URL})]
            other_client = self.post({"url": URL}, REMOTE_ADDR="10.0.0.9")
        self.assertEqual([r.status_code for r in answers], [200, 200, 429])
        self.assertEqual(answers[2].json()["error"]["code"], "DAILY_LIMIT")
        self.assertEqual(other_client.status_code, 200)
        self.assertEqual(run.call_count, 3)

    def test_every_client_that_is_not_a_developer_shares_one_daily_total(self):
        answer = {"article_id": "a1", "status": "created", "highlight_count": 3}
        limits = {"FRAMELESS_LINK_ANALYSIS_PER_CLIENT_DAILY": "5", "FRAMELESS_LINK_ANALYSIS_DAILY": "1"}
        with mock.patch.dict("os.environ", limits), mock.patch.object(api_v1, "analyze_link", return_value=answer):
            first = self.post({"url": URL}, REMOTE_ADDR="10.0.0.1")
            second = self.post({"url": URL}, REMOTE_ADDR="10.0.0.2")
        self.assertEqual((first.status_code, second.status_code), (200, 429))

    def test_a_developer_account_has_no_daily_limit(self):
        answer = {"article_id": "a1", "status": "created", "highlight_count": 3}
        limits = {"FRAMELESS_LINK_ANALYSIS_PER_CLIENT_DAILY": "0", "FRAMELESS_LINK_ANALYSIS_DAILY": "0"}
        with mock.patch.dict("os.environ", limits), \
                mock.patch.object(api_v1, "analyze_link", return_value=answer) as run:
            answers = [self.post({"url": URL}, **self.auth) for _ in range(3)]
        self.assertEqual([r.status_code for r in answers], [200, 200, 200])
        self.assertEqual(run.call_count, 3)
        self.assertFalse(AnalysisRecord.objects.exists())

    def test_a_stored_link_opens_even_after_the_daily_limit(self):
        articles = {"S": SOURCE, "A1": OTHER}
        dataset = build_dataset(articles, staged.analyze(articles, fake_model), now="2026-11-05T12:00:00+09:00")
        import_dataset(dataset, dataset_hash=dataset_digest(dataset))
        limits = {"FRAMELESS_LINK_ANALYSIS_PER_CLIENT_DAILY": "0", "FRAMELESS_LINK_ANALYSIS_DAILY": "0"}
        with mock.patch.dict("os.environ", limits), mock.patch.object(api_v1, "analyze_link") as run:
            response = self.post({"url": URL})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "existing")
        self.assertEqual(response.json()["article_id"], str(Article.objects.get(canonical_url=URL).id))
        run.assert_not_called()

    def test_a_link_that_cannot_be_read_does_not_use_up_the_limit(self):
        answer = {"article_id": "a1", "status": "created", "highlight_count": 3}
        limits = {"FRAMELESS_LINK_ANALYSIS_PER_CLIENT_DAILY": "1", "FRAMELESS_LINK_ANALYSIS_DAILY": "30"}
        unreadable = LinkAnalysisError("source_unavailable", "기사 본문을 가져오지 못했어요.")
        with mock.patch.dict("os.environ", limits):
            with mock.patch.object(api_v1, "analyze_link", side_effect=unreadable):
                failed = self.post({"url": URL})
            with mock.patch.object(api_v1, "analyze_link", return_value=answer):
                created = self.post({"url": URL})
        self.assertEqual((failed.status_code, created.status_code), (422, 200))

    def test_a_missing_or_local_link_is_rejected(self):
        with mock.patch.dict("os.environ", {"OPENROUTER_API_KEY": "test"}):
            self.assertEqual(self.post({"url": ""}, **self.auth).status_code, 400)
            self.assertEqual(self.post({"url": "http://localhost/a"}, **self.auth).status_code, 400)
            self.assertEqual(self.post({}, **self.auth).status_code, 400)

    def test_an_analysis_error_reaches_the_reader(self):
        with mock.patch.dict("os.environ", {"OPENROUTER_API_KEY": "test"}), \
                mock.patch.object(api_v1, "analyze_link", side_effect=LinkAnalysisError("no_same_event", "없어요")):
            response = self.post({"url": URL}, **self.auth)
        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json()["error"]["code"], "NO_SAME_EVENT")
        with mock.patch.dict("os.environ", {"OPENROUTER_API_KEY": "test"}), \
                mock.patch.object(api_v1, "analyze_link",
                                  side_effect=LinkAnalysisError("analysis_unavailable", "잠시 후", retryable=True)):
            response = self.post({"url": URL}, **self.auth)
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response["Retry-After"], "30")

    def test_one_analysis_at_a_time(self):
        api_v1._link_analysis_slot.acquire()
        try:
            with mock.patch.dict("os.environ", {"OPENROUTER_API_KEY": "test"}):
                response = self.post({"url": URL}, **self.auth)
        finally:
            api_v1._link_analysis_slot.release()
        self.assertEqual(response.status_code, 429)
