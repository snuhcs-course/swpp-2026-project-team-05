from django.test import SimpleTestCase

from scripts.smoke_release_api import round_robin_articles, validate_article


class ReleaseSmokeValidationTests(SimpleTestCase):
    def setUp(self):
        body = "첫 문장입니다. 비교할 표현이 있습니다."
        target_body = "다른 문장입니다. 대응 표현이 있습니다."
        self.detail = {
            "id": "article-a",
            "publisher": {"code": "a", "name": "언론사 A"},
            "title": "기사 A",
            "category": "POLITICS",
            "body": body,
            "original_url": "https://example.com/a",
            "content_hash": "hash-a",
        }
        sentence = "비교할 표현이 있습니다."
        sentence_start = body.index(sentence)
        target_sentence = "대응 표현이 있습니다."
        target_start = target_body.index(target_sentence)
        self.analysis = {
            "state": "READY",
            "version": "v1",
            "source_content_hash": "hash-a",
            "findings": [
                {
                    "id": "finding-a",
                    "categories": ["D"],
                    "source": {
                        "scope": "BODY",
                        "sentence": {
                            "start": sentence_start,
                            "end": sentence_start + len(sentence),
                            "text": sentence,
                        },
                        "marker": {
                            "start_in_sentence": 0,
                            "end_in_sentence": 3,
                            "text": "비교할",
                        },
                    },
                    "explanation": "두 기사의 표현 강도가 다릅니다.",
                    "comparison_articles": [
                        {
                            "article_id": "article-b",
                            "publisher": {"code": "b", "name": "언론사 B"},
                            "title": "기사 B",
                            "body": target_body,
                            "original_url": "https://example.com/b",
                            "evidence": [
                                {
                                    "scope": "BODY",
                                    "sentence": {
                                        "start": target_start,
                                        "end": target_start + len(target_sentence),
                                        "text": target_sentence,
                                    },
                                    "marker": {
                                        "start_in_sentence": 0,
                                        "end_in_sentence": 2,
                                        "text": "대응",
                                    },
                                }
                            ],
                        }
                    ],
                }
            ],
        }

    def test_accepts_grounded_comparison_backed_article(self):
        errors, targets, findings, comparisons = validate_article(
            self.detail, self.analysis
        )

        self.assertEqual(errors, [])
        self.assertEqual(targets, ["article-b"])
        self.assertEqual(findings, 1)
        self.assertEqual(comparisons, 1)

    def test_rejects_stale_source_offsets(self):
        self.analysis["findings"][0]["source"]["sentence"]["start"] = 0

        errors, _, _, _ = validate_article(self.detail, self.analysis)

        self.assertIn(
            "finding[0].source.sentence: stored text does not match offsets", errors
        )

    def test_rejects_comparison_without_retrievable_identity(self):
        self.analysis["findings"][0]["comparison_articles"][0]["article_id"] = ""

        errors, targets, _, _ = validate_article(self.detail, self.analysis)

        self.assertIn("finding[0].comparison[0]: missing article_id", errors)
        self.assertEqual(targets, [])

    def test_release_sample_scans_categories_round_robin(self):
        candidates = [
            {"id": "p1", "category": "POLITICS"},
            {"id": "p2", "category": "POLITICS"},
            {"id": "s1", "category": "SOCIETY"},
            {"id": "e1", "category": "ECONOMY"},
        ]

        ordered = round_robin_articles(candidates)

        self.assertEqual([item["id"] for item in ordered], ["p1", "s1", "e1", "p2"])
