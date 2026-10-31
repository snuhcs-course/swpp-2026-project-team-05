from copy import deepcopy
from unittest.mock import patch

from django.test import SimpleTestCase, TestCase

from server.models import Article, Publisher, StoryCluster
from server.news_import import DatasetError, hydrate_articles, import_dataset, locate_unique


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
