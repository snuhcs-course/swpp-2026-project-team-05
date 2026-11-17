package com.swpp.team5.frameless.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsAnalysisMapperTest {
    /** specs/features/economic-lens.md: the event, this article's view with its ranges, other outlets, contrasts. */
    @Test
    fun economicLensDecodesAndMapsTheViewOntoTheArticle() {
        val body = "😀금리를 내렸다. 집값엔 큰 영향을 주지 않을 것이란 평가다."
        val sentence = "집값엔 큰 영향을 주지 않을 것이란 평가다."
        val start = body.codePointCount(0, body.indexOf(sentence))
        val end = start + sentence.length
        val article = ArticleDetail(
            id = "hk", publisher = PublisherItem("hankyung", "한국경제", null),
            title = "집값 또 오르나", body = body, category = NewsCategory.ECONOMY,
            publishedAt = "2025-05-30T12:00:00+09:00", originalUrl = "https://example.com",
            analysisState = StoredAnalysisState.READY
        )
        val raw = """
            {"state": "READY", "version": "v", "source_content_hash": "h", "findings": [],
             "economic": {"event": "한은, 기준금리 2.50%로 0.25%p 인하",
              "views": [{"channels": ["housing"], "stance": "LIMITED", "direction": null,
                         "phrase": "집값엔 큰 영향을 주지 않을 것",
                         "sentence": {"scope": "BODY", "start": $start, "end": $end, "text": "$sentence"}},
                        {"channels": ["housing"], "stance": "LIMITED", "direction": null, "phrase": "없는 문구",
                         "sentence": {"scope": "BODY", "start": 0, "end": 3, "text": "다른 문장"}}],
              "others": [{"article_id": "yna", "publisher": {"code": "yna", "name": "연합뉴스"}, "title": "한은 인하",
                          "views": [{"channels": ["housing", "household_credit"], "stance": "CONCERN",
                                     "direction": "UP", "phrase": "집값을 자극",
                                     "sentence": {"scope": "TITLE", "start": 0, "end": 6, "text": "집값을 자극"}}]}],
              "contrasts": [{"channel": "housing", "stance": "LIMITED",
                             "others": [{"article_id": "yna", "publisher": {"code": "yna", "name": "연합뉴스"},
                                         "stance": "CONCERN"}]}]}}
        """.trimIndent()

        val lens = decodeArticleAnalysis(raw).toUi(article).economic!!

        assertEquals("한은, 기준금리 2.50%로 0.25%p 인하", lens.event)
        val view = lens.views.first()
        assertEquals(sentence, body.substring(view.sentenceRange!!.start, view.sentenceRange!!.end))
        assertEquals("집값엔 큰 영향을 주지 않을 것", body.substring(view.phraseRange!!.start, view.phraseRange!!.end))
        assertNull(lens.views[1].sentenceRange)
        val other = lens.others.single()
        assertEquals("연합뉴스", other.publisher.name)
        assertEquals(listOf("housing", "household_credit"), other.views.single().channels)
        assertEquals("UP", other.views.single().direction)
        assertNull(other.views.single().sentenceRange)
        assertEquals(listOf("연합뉴스" to "CONCERN"), lens.contrasts.single().others)
        assertNull(decodeArticleAnalysis("""{"state": "READY", "source_content_hash": "h", "findings": []}""")
            .toUi(article).economic)
    }

    @Test
    fun codePointOffsetsConvertAcrossSupplementaryCharacters() {
        val text = "😀정책 전격 추진"
        assertEquals(2, codePointOffsetToUtf16(text, 1))
        assertEquals(7, codePointOffsetToUtf16(text, 6))
        assertNull(codePointOffsetToUtf16(text, -1))
        assertNull(codePointOffsetToUtf16(text, 100))
    }

    @Test
    fun mapperKeepsVerifiedMarkerAndDropsMismatchedMarker() {
        val article = ArticleDetail(
            id = "article", publisher = PublisherItem("yna", "연합뉴스", null),
            title = "😀정책 전격 추진", body = "본문", category = NewsCategory.POLITICS,
            publishedAt = "2026-10-31T12:00:00+09:00", originalUrl = "https://example.com",
            analysisState = StoredAnalysisState.READY
        )
        val valid = ApiFinding(
            id = "valid", categories = listOf("D"), scope = "TITLE",
            sentence = ApiSentence(0, 9, "😀정책 전격 추진"),
            marker = ApiMarker(4, 6, "전격"), explanation = "강조",
            comparisonOverview = "", comparisons = emptyList()
        )
        val invalid = valid.copy(id = "invalid", marker = ApiMarker(4, 6, "오류"))
        val mapped = ApiArticleAnalysis(
            StoredAnalysisState.READY, "v1", listOf(valid, invalid)
        ).toUi(article)

        assertEquals(listOf("valid"), mapped.findings.map { it.id })
        assertEquals("전격", article.title.substring(
            mapped.findings.single().markerRange.start,
            mapped.findings.single().markerRange.end
        ))
        assertEquals(1, mapped.discardedMarkerCount)
        assertTrue(mapped.findings.single().markerRange.start > 4)
    }

    @Test
    fun mapperGroupsOnlyComparisonArticlesWithVerifiedEvidence() {
        val article = ArticleDetail(
            id = "source", publisher = PublisherItem("yna", "연합뉴스", null),
            title = "정책 추진", body = "정부는 정책을 전격 추진했다.",
            category = NewsCategory.POLITICS, publishedAt = "2026-10-31T12:00:00+09:00",
            originalUrl = "https://example.com/source", analysisState = StoredAnalysisState.READY
        )
        val target = ApiComparisonArticle(
            articleId = "target", publisher = PublisherItem("mbc", "MBC", null),
            title = "정책 추진 계획", publishedAt = article.publishedAt,
            originalUrl = "https://example.com/target", body = "정부는 정책을 빠르게 추진했다.",
            evidence = listOf(ApiEvidence(
                scope = "BODY", sentence = ApiSentence(0, 17, "정부는 정책을 빠르게 추진했다."),
                marker = ApiMarker(8, 11, "빠르게"), targetOwner = "REPORTER",
                targetSpeakerLabel = null, comparisonSummary = "속도 표현이 다르다."
            ))
        )
        val finding = ApiFinding(
            id = "f1", categories = listOf("D"), scope = "BODY",
            sentence = ApiSentence(0, 16, article.body), marker = ApiMarker(8, 10, "전격"),
            explanation = "속도를 강조한다.",
            comparisonOverview = "두 언론사가 추진 속도를 다르게 표현한다.",
            comparisons = listOf(target)
        )

        val mapped = ApiArticleAnalysis(StoredAnalysisState.READY, "v1", listOf(finding)).toUi(article)

        assertEquals("빠르게", mapped.findings.single().comparisons.single().evidence.single().markerText)
        assertEquals(
            "두 언론사가 추진 속도를 다르게 표현한다.",
            mapped.findings.single().comparisonOverview
        )
        val invalidTarget = target.copy(evidence = target.evidence.map {
            it.copy(marker = ApiMarker(8, 11, "불일치"))
        })
        val invalid = ApiArticleAnalysis(
            StoredAnalysisState.READY, "v1", listOf(finding.copy(comparisons = listOf(invalidTarget)))
        ).toUi(article)
        assertTrue(invalid.findings.single().comparisons.isEmpty())
        assertEquals(1, invalid.discardedMarkerCount)
    }
}
