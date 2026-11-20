from copy import deepcopy
from unittest.mock import patch

from django.test import SimpleTestCase, TestCase

from django.contrib.auth.hashers import make_password

from server.models import Article, Publisher, StoryCluster, UserAccount
from server.news_import import (
    DatasetError,
    hydrate_articles,
    import_dataset,
    locate_unique,
    replace_news_dataset,
)


class NewsDatasetGroundingTests(SimpleTestCase):
    def test_locates_one_exact_quote(self):
        self.assertEqual(locate_unique("앞 문장. 핵심 문장.", "핵심 문장.", "test"), (6, 12))

    def test_rejects_missing_quote(self):
        with self.assertRaises(DatasetError):
            locate_unique("기사 본문", "없는 문장", "test")

    def test_rejects_ambiguous_quote(self):
        with self.assertRaises(DatasetError):
            locate_unique("반복 반복", "반복", "test")


class NewsDatasetImportTests(TestCase):
    def setUp(self):
        self.dataset = {
            "schema_version": 1,
            "analysis_version": "reviewed-test-v1",
            "publishers": [{
                "code": "test-news",
                "name_ko": "테스트뉴스",
                "domain": "news.example.com",
                "display_order": 1,
            }],
            "clusters": [{
                "key": "test-cluster",
                "internal_label": "테스트 사건",
                "category": "POLITICS",
                "articles": [{
                    "key": "article-a",
                    "publisher": "test-news",
                    "canonical_url": "https://news.example.com/a",
                    "title": "테스트 기사",
                    "body_text": "검토된 기사 본문입니다.",
                    "published_at": "2026-10-31T12:00:00+09:00",
                }],
                "propositions": [],
                "findings": [],
            }],
        }

    def test_dry_run_rolls_back_and_identical_import_is_idempotent(self):
        counts = import_dataset(self.dataset, dataset_hash="a" * 64, dry_run=True)
        self.assertEqual(counts.articles, 1)
        self.assertEqual(Publisher.objects.count(), 0)
        self.assertEqual(StoryCluster.objects.count(), 0)
        self.assertEqual(Article.objects.count(), 0)

        import_dataset(self.dataset, dataset_hash="a" * 64)
        import_dataset(self.dataset, dataset_hash="a" * 64)
        self.assertEqual(Publisher.objects.count(), 1)
        self.assertEqual(StoryCluster.objects.count(), 1)
        self.assertEqual(Article.objects.count(), 1)

    def test_changed_body_is_rejected_without_overwriting_reviewed_article(self):
        import_dataset(self.dataset, dataset_hash="a" * 64)
        changed = deepcopy(self.dataset)
        changed["clusters"][0]["articles"][0]["body_text"] = "외부에서 바뀐 본문입니다."

        with self.assertRaisesMessage(DatasetError, "stored article title or body changed"):
            import_dataset(changed, dataset_hash="b" * 64)

        article = Article.objects.get()
        self.assertEqual(article.body_text, "검토된 기사 본문입니다.")
        self.assertEqual(article.story_cluster.analysis_input_hash, "a" * 64)

    def test_changed_title_is_rejected_without_overwriting_reviewed_article(self):
        import_dataset(self.dataset, dataset_hash="a" * 64)
        changed = deepcopy(self.dataset)
        changed["clusters"][0]["articles"][0]["title"] = "외부에서 바뀐 제목"

        with self.assertRaisesMessage(DatasetError, "stored article title or body changed"):
            import_dataset(changed, dataset_hash="b" * 64)

        article = Article.objects.get()
        self.assertEqual(article.title, "테스트 기사")
        self.assertEqual(article.story_cluster.analysis_input_hash, "a" * 64)

    @patch("server.analysis.v2.article_fetch.fetch_article")
    def test_hydration_preserves_frozen_reviewed_snapshot(self, fetch_article):
        hydrated = hydrate_articles(self.dataset)

        fetch_article.assert_not_called()
        article = hydrated["clusters"][0]["articles"][0]
        self.assertEqual(article["title"], "테스트 기사")
        self.assertEqual(article["body_text"], "검토된 기사 본문입니다.")

    def test_replace_is_atomic_and_preserves_accounts(self):
        user = UserAccount.objects.create(
            username="kept-user",
            password_hash=make_password("safe-password-123"),
        )
        import_dataset(self.dataset, dataset_hash="a" * 64)
        old_url = self.dataset["clusters"][0]["articles"][0]["canonical_url"]

        replacement = deepcopy(self.dataset)
        replacement["clusters"][0]["key"] = "replacement-cluster"
        replacement_article = replacement["clusters"][0]["articles"][0]
        replacement_article["canonical_url"] = "https://news.example.com/replacement"
        replacement_article["title"] = "교체 기사"
        replacement_article["body_text"] = "교체된 검토 본문입니다."
        replace_news_dataset(replacement, dataset_hash="b" * 64)

        self.assertTrue(UserAccount.objects.filter(id=user.id).exists())
        self.assertFalse(Article.objects.filter(canonical_url=old_url).exists())
        self.assertTrue(
            Article.objects.filter(canonical_url=replacement_article["canonical_url"]).exists()
        )

    def _economy_dataset(self):
        dataset = deepcopy(self.dataset)
        cluster = dataset["clusters"][0]
        cluster["category"] = "ECONOMY"
        article = cluster["articles"][0]
        article["body_text"] = "금리를 내렸다. 집값엔 큰 영향을 주지 않을 것이란 평가다."
        sentence = "집값엔 큰 영향을 주지 않을 것이란 평가다."
        start = article["body_text"].index(sentence)
        located = {"scope": "BODY", "start": start, "end": start + len(sentence), "text": sentence}
        article["economic_context"] = {
            "event": "한은 기준금리 인하",
            "views": [{"channels": ["housing"], "stance": "LIMITED", "direction": None,
                       "phrase": "집값엔 큰 영향을 주지 않을 것", "sentence": located}],
        }
        return dataset

    def test_economy_cluster_keeps_the_event_and_the_article_view(self):
        import_dataset(self._economy_dataset(), dataset_hash="e" * 64)

        article = Article.objects.get()
        self.assertEqual(article.category, "ECONOMY")
        self.assertEqual(article.economic_context["event"], "한은 기준금리 인하")
        view = article.economic_context["views"][0]
        self.assertEqual((view["channels"], view["stance"], view["phrase"]),
                         (["housing"], "LIMITED", "집값엔 큰 영향을 주지 않을 것"))
        self.assertEqual(view["sentence"]["text"], "집값엔 큰 영향을 주지 않을 것이란 평가다.")

    def test_loan_soundness_and_policy_stance_are_known_channels(self):
        dataset = self._economy_dataset()
        dataset["clusters"][0]["articles"][0]["economic_context"]["views"][0]["channels"] = [
            "credit_quality", "monetary_policy"]
        import_dataset(dataset, dataset_hash="e" * 64)
        self.assertEqual(Article.objects.get().economic_context["views"][0]["channels"],
                         ["credit_quality", "monetary_policy"])

    def test_economic_lens_that_does_not_match_its_article_is_rejected(self):
        view = lambda d: d["clusters"][0]["articles"][0]["economic_context"]["views"][0]  # noqa: E731
        cases = {
            "sentence offsets": lambda d: view(d)["sentence"].update(start=0),
            "phrase not in its sentence": lambda d: view(d).update(phrase="집값이 크게 오른다"),
            "unknown stance": lambda d: view(d).update(stance="SAD"),
            "unknown channel": lambda d: view(d).update(channels=["weather"]),
            "no event": lambda d: d["clusters"][0]["articles"][0]["economic_context"].update(event=""),
            "too many views": lambda d: d["clusters"][0]["articles"][0]["economic_context"]["views"].extend(
                [deepcopy(view(d)), deepcopy(view(d))]),
            "outside an economy cluster": lambda d: d["clusters"][0].update(category="POLITICS"),
        }
        for name, change in cases.items():
            with self.subTest(name):
                dataset = self._economy_dataset()
                change(dataset)
                with self.assertRaises(DatasetError):
                    import_dataset(dataset, dataset_hash="e" * 64)
                self.assertEqual(Article.objects.count(), 0)

    def test_failed_replace_rolls_back_old_news(self):
        import_dataset(self.dataset, dataset_hash="a" * 64)
        invalid = deepcopy(self.dataset)
        invalid["clusters"][0]["articles"][0]["body_text"] = ""

        with self.assertRaises(DatasetError):
            replace_news_dataset(invalid, dataset_hash="b" * 64)

        self.assertEqual(Article.objects.count(), 1)
        self.assertEqual(Article.objects.get().title, "테스트 기사")

    def _issue_dataset(self):
        """Two outlets on one event: a frame on the first, one contested issue with a wording difference, one fact."""
        dataset = deepcopy(self.dataset)
        cluster = dataset["clusters"][0]
        a = cluster["articles"][0]
        a["body_text"] = "국회가 법안을 강행 처리했다. 야당은 반발했다."
        cluster["articles"].append({
            "key": "article-b", "publisher": "test-news", "canonical_url": "https://news.example.com/b",
            "title": "법안 통과", "body_text": "국회가 법안을 통과시켰다. 여당은 개혁이라고 밝혔다.",
            "published_at": "2026-10-31T13:00:00+09:00",
        })
        sentence = "국회가 법안을 강행 처리했다."
        a["frame_context"] = {"frame": "정치", "sentence": {"scope": "BODY", "start": 0, "end": len(sentence),
                                                             "text": sentence}}
        cluster["issue_table"] = {
            "event": "국회, 법안 통과",
            "issues": [{
                "key": "K1", "question": "법안 처리 절차는 정당했나?", "kind": "contested",
                "positions": {"P1": "일방 처리", "P2": "정상 처리"},
                "outlets": [{"article": "article-a", "position": "P1"}, {"article": "article-b", "position": "P2"}],
                "wordings": [{"left": {"article": "article-a", "phrase": "강행 처리했다"},
                              "right": {"article": "article-b", "phrase": "통과시켰다"},
                              "code": "C", "explanation": "같은 행위를 서술하면서 테스트뉴스 쪽이 더 센 말을 골랐습니다."}],
            }],
            "facts": [{"key": "F1", "label": "국회가 법안을 처리했다", "outlets": ["article-a", "article-b"]}],
        }
        return dataset

    def test_issue_table_is_stored_with_article_ids(self):
        import_dataset(self._issue_dataset(), dataset_hash="f" * 64)

        a = Article.objects.get(canonical_url="https://news.example.com/a")
        b = Article.objects.get(canonical_url="https://news.example.com/b")
        self.assertEqual(a.frame_context["frame"], "정치")
        self.assertIsNone(b.frame_context)
        table = a.story_cluster.issue_table
        self.assertEqual(table["event"], "국회, 법안 통과")
        issue = table["issues"][0]
        self.assertEqual(issue["outlets"], [{"article": str(a.id), "position": "P1"},
                                            {"article": str(b.id), "position": "P2"}])
        self.assertEqual((issue["wordings"][0]["left"]["article"], issue["wordings"][0]["right"]["article"]),
                         (str(a.id), str(b.id)))
        self.assertEqual(table["facts"][0]["outlets"], [str(a.id), str(b.id)])

    def test_revision_without_issue_table_clears_it(self):
        import_dataset(self._issue_dataset(), dataset_hash="f" * 64)
        revision = self._issue_dataset()
        del revision["clusters"][0]["issue_table"]
        import_dataset(revision, dataset_hash="g" * 64)
        self.assertIsNone(StoryCluster.objects.get().issue_table)

    def test_issue_table_or_frame_that_does_not_match_its_articles_is_rejected(self):
        issue = lambda d: d["clusters"][0]["issue_table"]["issues"][0]  # noqa: E731
        frame = lambda d: d["clusters"][0]["articles"][0]["frame_context"]  # noqa: E731
        cases = {
            "frame sentence offsets": lambda d: frame(d)["sentence"].update(start=1),
            "no frame": lambda d: frame(d).update(frame=""),
            "unknown article": lambda d: issue(d)["outlets"].append({"article": "article-z", "position": "P1"}),
            "article twice in an issue": lambda d: issue(d)["outlets"].append({"article": "article-a", "position": "F"}),
            "unknown position": lambda d: issue(d)["outlets"][0].update(position="P9"),
            "unknown kind": lambda d: issue(d).update(kind="hot"),
            "phrase not in its article": lambda d: issue(d)["wordings"][0]["left"].update(phrase="날치기"),
            "wording within one article": lambda d: issue(d)["wordings"][0]["right"].update(article="article-a"),
            "unknown code": lambda d: issue(d)["wordings"][0].update(code="H"),
            "duplicate key": lambda d: d["clusters"][0]["issue_table"]["facts"][0].update(key="K1"),
            "no event": lambda d: d["clusters"][0]["issue_table"].update(event=" "),
        }
        for name, change in cases.items():
            with self.subTest(name):
                dataset = self._issue_dataset()
                change(dataset)
                with self.assertRaises(DatasetError):
                    import_dataset(dataset, dataset_hash="f" * 64)
                self.assertEqual(Article.objects.count(), 0)
