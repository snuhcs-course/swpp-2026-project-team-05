"""Keep news-site controls and summaries out of the Reader's source text."""

from email.message import Message
from io import BytesIO
from unittest import TestCase
from unittest.mock import patch

from server.analysis.v2.article_fetch import _extract_yna_article_body, fetch_article


YNA_HTML = """<html><head>
<meta property="og:title" content="예시 기사 제목">
</head><body>
<header class="title-article01">
  <article class="story-summary">
    <p>알래스카 LNG 투자 합의가 없다는 세 줄 요약</p>
    <p>전체 내용을 이해하기 위해서는 기사 본문과 함께 읽어야 합니다.</p>
  </article>
  <div class="tlp-source01"><p>구글 검색에서 연합뉴스 기사를 우선적으로 보여줍니다.</p></div>
</header>
<article id="articleWrap"><div class="story-news article">
  <div class="writer-zone01"><p>기자 프로필과 구독 안내</p></div>
  <figure><figcaption><p>기사 사진의 설명 문구</p></figcaption></figure>
  <p>첫 번째 본문은 사고를 설명하고 관계자의 발언을 정확하게 전합니다.</p>
  <aside><p>광고와 추천 콘텐츠</p></aside>
  <p>두 번째 본문은 실제로 알래스카 LNG 투자 발언을 다룹니다.</p>
  <p>reporter@yna.co.kr<br></p>
  <div class="related-zone"><p>관련 뉴스 목록</p></div>
  <p class="txt-copyright adrs">저작권과 재배포 안내</p>
</div></article>
</body></html>"""

EXPECTED_BODY = (
    "첫 번째 본문은 사고를 설명하고 관계자의 발언을 정확하게 전합니다.\n\n"
    "두 번째 본문은 실제로 알래스카 LNG 투자 발언을 다룹니다."
)


class YnaExtractionTests(TestCase):
    def test_only_story_paragraphs_are_kept_in_source_order(self):
        self.assertEqual(_extract_yna_article_body(YNA_HTML), EXPECTED_BODY)

    def test_line_break_inside_story_paragraph_is_not_flattened(self):
        html = ('<article id="articleWrap"><div class="story-news">'
                '<p>인용문 첫 줄\n <span>이어서</span><br><br>인용문 둘째 줄</p><p>다음 문단</p>'
                '</div></article>')
        self.assertEqual(_extract_yna_article_body(html),
                         "인용문 첫 줄 이어서\n\n인용문 둘째 줄\n\n다음 문단")

    def test_fetch_yna_prefers_story_paragraphs_over_generic_extractor(self):
        response = BytesIO(YNA_HTML.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract") as generic_extract):
            build.return_value.open.return_value = response
            article = fetch_article("https://www.yna.co.kr/view/AKR20261002029600001")
        self.assertEqual(article["body"], EXPECTED_BODY)
        self.assertEqual(article["title"], "예시 기사 제목")
        generic_extract.assert_not_called()

    def test_missing_yna_structure_falls_back_to_generic_extraction(self):
        self.assertIsNone(_extract_yna_article_body("<html><body>다른 형식</body></html>"))

    def test_yna_like_hostname_does_not_trigger_site_selector(self):
        response = BytesIO(YNA_HTML.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract", return_value="generic result") as generic_extract):
            build.return_value.open.return_value = response
            article = fetch_article("https://www.yna.co.kr.example.com/view/story")
        self.assertEqual(article["body"], "generic result")
        generic_extract.assert_called_once()
