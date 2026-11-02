import unicodedata

from django.test import SimpleTestCase

from server.article_search import MAX_EXCERPT_LENGTH, normalize_search_query, search_excerpt


class ArticleSearchTextTests(SimpleTestCase):
    def test_query_is_trimmed_whitespace_collapsed_and_unicode_normalized(self):
        decomposed = unicodedata.normalize("NFD", "메타")
        self.assertEqual(normalize_search_query(f"  {decomposed}   신용등급  "), "메타 신용등급")

    def test_excerpt_is_bounded_and_contains_the_grounded_match(self):
        body = "앞 문맥 " * 80 + "글로벌 신용평가사" + " 뒤 문맥" * 80
        excerpt = search_excerpt(body, "글로벌 신용평가사")

        self.assertIsNotNone(excerpt)
        self.assertLessEqual(len(excerpt), MAX_EXCERPT_LENGTH)
        self.assertIn("글로벌 신용평가사", excerpt)
        self.assertTrue(excerpt.startswith("…"))
        self.assertTrue(excerpt.endswith("…"))

    def test_excerpt_is_none_without_a_body_match(self):
        self.assertIsNone(search_excerpt("기사 본문", "없는 검색어"))
