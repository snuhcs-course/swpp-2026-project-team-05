"""Keep news-site controls and summaries out of the Reader's source text."""

from email.message import Message
from io import BytesIO
from unittest import TestCase
from unittest.mock import patch

from server.analysis.v2.article_fetch import (
    _extract_naver_article_body,
    _extract_yna_article_body,
    _extract_ytn_article_body,
    _strip_ytn_footer,
    fetch_article,
)
from server.analysis.v2.article_input import structure_article_body


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


YTN_HTML = """<html><head><meta property="og:title" content="예시 YTN 기사"></head><body>
<div class="ad_newsview_text"><div class="icon">AD</div></div>
<div id="CmAdContent" class="paragraph"><span>
첫 문단은 실제 사건의 경위를 설명합니다.<br><br>
둘째 문단은 관련자의 발언을 정확하게 전합니다.<br><br>
YTN 임예진 (imyj77@ytn.co.kr)<br><br>
※ '당신의 제보가 뉴스가 됩니다'<br>
[카카오톡] YTN 검색해 채널 추가<br>
[전화] 02-398-8585<br>
[메일] social@ytn.co.kr</span><br><br>
[저작권자(c) YTN 무단전재, 재배포 및 AI 데이터 활용 금지]
</div>
<div class="ad_newsview_text"><div class="icon">AD</div></div>
</body></html>"""
YTN_STORY = (
    "첫 문단은 실제 사건의 경위를 설명합니다.\n\n"
    "둘째 문단은 관련자의 발언을 정확하게 전합니다."
)


class YtnExtractionTests(TestCase):
    def test_story_container_keeps_paragraphs_and_excludes_ads_and_footer(self):
        body = _extract_ytn_article_body(YTN_HTML)
        self.assertEqual(body, YTN_STORY)
        structured = structure_article_body(body)
        self.assertEqual([item["text"] for item in structured["paragraphs"]],
                         ["첫 문단은 실제 사건의 경위를 설명합니다.",
                          "둘째 문단은 관련자의 발언을 정확하게 전합니다."])
        for sentence in structured["sentences"]:
            self.assertEqual(body[sentence["start"]:sentence["end"]], sentence["text"])

    def test_fetch_ytn_uses_story_container_before_generic_extractor(self):
        response = BytesIO(YTN_HTML.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract") as generic_extract):
            build.return_value.open.return_value = response
            article = fetch_article("https://www.ytn.co.kr/_ln/0101_202610021009348094")
        self.assertEqual(article["body"], YTN_STORY)
        self.assertEqual(article["title"], "예시 YTN 기사")
        generic_extract.assert_not_called()

    def test_ytn_fallback_removes_contact_suffix_only(self):
        self.assertIsNone(_extract_ytn_article_body("<html><body>다른 레이아웃</body></html>"))
        raw = (YTN_STORY + "\nYTN 임예진 (imyj77@ytn.co.kr)\n"
               "※ '당신의 제보가 뉴스가 됩니다'\n"
               "[카카오톡] YTN 검색해 채널 추가\n"
               "[전화] 02-398-8585\n"
               "[메일] social@ytn.co.kr\n"
               "[저작권자(c) YTN 무단전재, 재배포 및 AI 데이터 활용 금지]\nAD")
        self.assertEqual(_strip_ytn_footer(raw), YTN_STORY)

    def test_naver_ytn_publisher_uses_safe_suffix_cleaner(self):
        html = ('<html><head><meta property="og:article:author" '
                'content="YTN | 네이버"></head><body><article>본문</article></body></html>')
        response = BytesIO(html.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        raw = (YTN_STORY + "\nYTN 임예진 (imyj77@ytn.co.kr)\n"
               "※ '당신의 제보가 뉴스가 됩니다'\n[카카오톡] YTN 검색해 채널 추가")
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract", return_value=raw)):
            build.return_value.open.return_value = response
            article = fetch_article("https://n.news.naver.com/article/052/0000000001")
        self.assertEqual(article["publisher"], "YTN")
        self.assertEqual(article["body"], YTN_STORY)

    def test_story_with_isolated_reporter_or_footer_words_is_not_cut(self):
        story = ("기사 속 인물이 YTN 임예진 (imyj77@ytn.co.kr)를 언급했습니다.\n\n"
                 "[전화] 02-398-8585라는 번호를 조사하는 내용입니다.\n\n"
                 "마지막 발언도 기사 내용입니다.")
        self.assertEqual(_strip_ytn_footer(story), story)
        self.assertEqual(_strip_ytn_footer(story + "\nYTN 임예진 (imyj77@ytn.co.kr)"),
                         story + "\nYTN 임예진 (imyj77@ytn.co.kr)")

    def test_similar_hostname_does_not_trigger_ytn_selector_or_cleaner(self):
        response = BytesIO(YTN_HTML.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        generic_body = "평범한 기사의 본문입니다.\n[전화] 02-398-8585\nAD"
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract", return_value=generic_body) as generic_extract):
            build.return_value.open.return_value = response
            article = fetch_article("https://www.ytn.co.kr.example.com/story")
        self.assertEqual(article["body"], generic_body)
        generic_extract.assert_called_once()


NAVER_BODY = (
    "요약 첫째 줄\n요약 둘째 줄\n\n"
    "첫 문단은 실제 사건과 발언 시점을 구체적으로 설명합니다.\n\n"
    "둘째 문단은 관계자가 어떤 이유를 들었는지 구체적으로 전합니다.\n\n"
    "셋째 문단은 강조 표현을 포함합니다.\n"
    "같은 문단의 다음 줄에도 중요한 기사 내용이 이어집니다.\n\n"
    "넷째 문단은 후속 조치와 남은 쟁점을 설명합니다."
)
NAVER_HTML = """<html><head><meta property="og:title" content="예시 네이버 기사"></head><body>
<div class="media_end_head_tools">공유·인쇄 도구</div>
<div id="newsct_article" class="newsct_article _article_body">
<article id="dic_area" class="go_trans _article_content">
<strong class="media_end_summary">요약 첫째 줄<br>요약 둘째 줄</strong>
<span class="end_photo_org"><img alt="사진 대체 텍스트"><em class="img_desc">사진 설명</em></span><br>
첫 문단은 실제 사건과 발언 시점을 구체적으로 설명합니다.<br><br>
둘째 문단은 관계자가 어떤 이유를 들었는지 구체적으로 전합니다.<br><br>
<p>셋째 문단은 <em>강조 표현</em>을 포함합니다.<br>
같은 문단의 다음 줄에도 중요한 기사 내용이 이어집니다.</p>
<p>넷째 문단은 후속 조치와 남은 쟁점을 설명합니다.</p>
</article>
<div class="byline">기자 이름과 이메일</div>
<div class="copyright">저작권과 재배포 안내</div>
</div>
<div class="ad_banner">AD 및 추천 콘텐츠</div>
</body></html>"""


class NaverExtractionTests(TestCase):
    def test_known_layout_preserves_br_and_p_boundaries_without_page_chrome(self):
        body = _extract_naver_article_body(NAVER_HTML)
        self.assertEqual(body, NAVER_BODY)
        for excluded in ("사진 설명", "사진 대체 텍스트", "공유·인쇄 도구",
                         "기자 이름과 이메일", "저작권과 재배포 안내", "추천 콘텐츠"):
            self.assertNotIn(excluded, body)

    def test_fetch_naver_uses_known_layout_and_offsets_match_reader_body(self):
        response = BytesIO(NAVER_HTML.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract") as generic_extract):
            build.return_value.open.return_value = response
            article = fetch_article("https://n.news.naver.com/article/057/0001971678")
        self.assertEqual(article["body"], NAVER_BODY)
        self.assertEqual(article["title"], "예시 네이버 기사")
        generic_extract.assert_not_called()
        structured = structure_article_body(article["body"])
        self.assertEqual(len(structured["paragraphs"]), 7)
        for passage in structured["paragraphs"] + structured["sentences"]:
            self.assertEqual(article["body"][passage["start"]:passage["end"]],
                             passage["text"])

    def test_unknown_naver_layout_falls_back_instead_of_dropping_blocks(self):
        changed_layout = NAVER_HTML.replace(
            '<p>넷째 문단은 후속 조치와 남은 쟁점을 설명합니다.</p>',
            '<div class="unknown_story_widget">넷째 문단은 후속 조치와 남은 쟁점을 설명합니다.</div>',
        )
        self.assertIsNone(_extract_naver_article_body(changed_layout))
        response = BytesIO(changed_layout.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract", return_value="기존 추출기가 복구한 기사 본문") as generic_extract):
            build.return_value.open.return_value = response
            article = fetch_article("https://n.news.naver.com/article/057/0001971678")
        self.assertEqual(article["body"], "기존 추출기가 복구한 기사 본문")
        generic_extract.assert_called_once()

    def test_missing_parent_or_article_marker_rejects_specialized_path(self):
        self.assertIsNone(_extract_naver_article_body(
            NAVER_HTML.replace('class="newsct_article _article_body"', 'class="newsct_article"')))
        self.assertIsNone(_extract_naver_article_body(
            NAVER_HTML.replace('class="go_trans _article_content"', 'class="go_trans"')))

    def test_similar_hostname_uses_generic_extractor(self):
        response = BytesIO(NAVER_HTML.encode("utf-8"))
        response.headers = Message()
        response.headers.add_header("Content-Type", "text/html; charset=utf-8")
        with (patch("server.analysis.v2.article_fetch.build_opener") as build,
              patch("trafilatura.extract", return_value="일반 기사 본문") as generic_extract):
            build.return_value.open.return_value = response
            article = fetch_article("https://n.news.naver.com.example.com/article/057/0001971678")
        self.assertEqual(article["body"], "일반 기사 본문")
        generic_extract.assert_called_once()
