from pathlib import Path
import re

from django.test import SimpleTestCase

from server.gold_dataset import build_product_dataset, load_gold_documents


class GoldDatasetConversionTests(SimpleTestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        fixture_path = Path(__file__).resolve().parents[1] / "server" / "fixtures" / "gold_v5"
        cls.documents = load_gold_documents(fixture_path)
        cls.dataset = build_product_dataset(cls.documents)

    def test_complete_reviewed_set_is_projected(self):
        clusters = self.dataset["clusters"]
        self.assertEqual(len(self.documents), 30)
        self.assertEqual(len(clusters), 30)
        self.assertEqual(sum(len(row["articles"]) for row in clusters), 178)
        self.assertEqual(sum(len(row["findings"]) for row in clusters), 91)
        self.assertEqual(sum(len(row["propositions"]) for row in clusters), 86)
        self.assertEqual(
            sum(len(finding["comparisons"])
                for cluster in clusters for finding in cluster["findings"]),
            160,
        )
        self.assertEqual(sum(not row["findings"] for row in clusters), 4)
        self.assertEqual(
            sum(
                article["is_feed_visible"]
                for cluster in clusters
                for article in cluster["articles"]
            ),
            30,
        )
        self.assertTrue(all(
            cluster["articles"][0]["is_feed_visible"]
            for cluster in clusters
        ))

    def test_publishers_are_complete_and_stable(self):
        publishers = self.dataset["publishers"]
        self.assertEqual(len(publishers), 56)
        self.assertEqual(len({row["code"] for row in publishers}), 56)
        self.assertIn("아주경제", {row["name_ko"] for row in publishers})
        self.assertIn("IT조선", {row["name_ko"] for row in publishers})

    def test_reviewed_feed_opening_order(self):
        expected = {
            "gold-v5-b08": 6,
            "gold-v5-b12": 5,
            "gold-v5-b16": 4,
            "gold-v5-b33": 3,
            "gold-v5-b34": 2,
            "gold-v5-b11": 1,
        }
        actual = {}
        for cluster in self.dataset["clusters"]:
            for article in cluster["articles"]:
                if article["feed_priority"]:
                    self.assertTrue(article["is_feed_visible"])
                    actual[cluster["key"]] = article["feed_priority"]
        self.assertEqual(actual, expected)

    def test_product_projection_keeps_only_displayable_gold_differences(self):
        findings = [
            finding
            for cluster in self.dataset["clusters"]
            for finding in cluster["findings"]
        ]
        self.assertTrue(findings)
        for finding in findings:
            self.assertTrue(finding["categories"])
            self.assertTrue(finding["source_sentence_text"])
            self.assertTrue(finding["marker_text"])
            self.assertTrue(finding["comparisons"])
            for comparison in finding["comparisons"]:
                self.assertTrue(comparison["target_sentence_text"])
                self.assertTrue(comparison["target_marker_text"])
                self.assertTrue(comparison["comparison_summary"])

    def test_product_copy_does_not_expose_evaluation_article_ids(self):
        internal_reference = re.compile(
            r"(?<![A-Za-z0-9])(?:A\d+(?![A-Za-z0-9])|S(?=는|#))"
        )
        for cluster in self.dataset["clusters"]:
            for finding in cluster["findings"]:
                self.assertIsNone(internal_reference.search(finding["explanation"]))
                for comparison in finding["comparisons"]:
                    self.assertIsNone(
                        internal_reference.search(comparison["comparison_summary"])
                    )
