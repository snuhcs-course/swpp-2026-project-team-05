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
