from pathlib import Path

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

    def test_publishers_are_complete_and_stable(self):
        publishers = self.dataset["publishers"]
        self.assertEqual(len(publishers), 56)
        self.assertEqual(len({row["code"] for row in publishers}), 56)
        self.assertIn("아주경제", {row["name_ko"] for row in publishers})
        self.assertIn("IT조선", {row["name_ko"] for row in publishers})

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
