package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Oracles: specs/testing/paired-expression-comparison.spec.md (Draft). */
class ExpressionComparisonTest {
    private fun evidence(marker: String, sentence: String) = ComparisonEvidenceUi(
        scope = "BODY",
        sentenceRange = TextRange(0, sentence.length),
        sentenceText = sentence,
        markerRange = TextRange(0, marker.length),
        markerText = marker,
        owner = "REPORTER",
        speakerLabel = null,
        summary = "요약"
    )

    private fun page(code: String, name: String, vararg evidence: ComparisonEvidenceUi) = ComparisonArticleUi(
        articleId = "article-$code",
        publisher = PublisherItem(code, name, null),
        title = "$name 기사",
        publishedAt = "2026-11-18T10:00:00+09:00",
        originalUrl = "https://example.com/$code",
        body = "본문",
        evidence = evidence.toList()
    )

    private fun finding(marker: String, sentence: String, vararg pages: ComparisonArticleUi) = FindingUi(
        id = "finding-1",
        categories = listOf("C"),
        scope = "BODY",
        sentenceRange = TextRange(0, sentence.length),
        sentenceText = sentence,
        markerRange = TextRange(0, marker.length),
        markerText = marker,
        explanation = "설명",
        comparisonOverview = "개요",
        comparisons = pages.toList()
    )

    private val sourceSentence = "1999년 '한국조폐공사 파업 유도 사건'의 중심에 섰던 진형구 전 대전고검장이 별세했다."
    private val news1 = page("news1", "뉴스1",
        evidence("연루되기도 했다", "'지폐공사 노조 파업 유도 사건'에 연루되기도 했다."))
    private val hani = page("hani", "한겨레",
        evidence("  ", "표시할 표현이 없는 근거"),
        evidence("관여했던", "파업 유도 사건에 관여했던 진 전 고검장은"))

    @Test
    fun sourceAndTargetUseExactStoredData() {
        val comparison = expressionComparisonFor("프레시안", finding("중심에 섰던", sourceSentence, news1), 0)!!

        assertEquals(ExpressionSide("프레시안", "중심에 섰던", sourceSentence), comparison.source)
        assertEquals(
            ExpressionSide("뉴스1", "연루되기도 했다", "'지폐공사 노조 파업 유도 사건'에 연루되기도 했다."),
            comparison.target
        )
    }

    @Test
    fun targetFollowsTheSelectedPageWhileSourceStaysFixed() {
        val finding = finding("중심에 섰던", sourceSentence, news1, hani)

        val first = expressionComparisonFor("프레시안", finding, 0)!!
        val second = expressionComparisonFor("프레시안", finding, 1)!!

        assertEquals(first.source, second.source)
        assertEquals("뉴스1", first.target.publisher)
        // The first evidence with a non-blank marker on that page.
        assertEquals(ExpressionSide("한겨레", "관여했던", "파업 유도 사건에 관여했던 진 전 고검장은"), second.target)
    }

    @Test
    fun blankSourceOrTargetHidesTheCard() {
        val blankTargetPage = page("kbs", "KBS", evidence("", "문장"), evidence("   ", "문장"))
        val noEvidencePage = page("mbc", "MBC")

        assertNull(expressionComparisonFor("  ", finding("중심에 섰던", sourceSentence, news1), 0))
        assertNull(expressionComparisonFor("프레시안", finding("  ", sourceSentence, news1), 0))
        assertNull(expressionComparisonFor("프레시안", finding("중심에 섰던", sourceSentence, blankTargetPage), 0))
        assertNull(expressionComparisonFor("프레시안", finding("중심에 섰던", sourceSentence, noEvidencePage), 0))
        assertNull(expressionComparisonFor("프레시안", finding("중심에 섰던", sourceSentence), 0))
        assertNull(expressionComparisonFor("프레시안", finding("중심에 섰던", sourceSentence, news1), 1))
    }

    @Test
    fun identifierLikeRealEvidenceStaysVisible() {
        val target = page("kbs", "KBS", evidence("A-3 구역", "A-3 구역에서 회의가 열렸다."))
        val comparison = expressionComparisonFor(
            "연합뉴스", finding("G7 정상회의", "G7 정상회의가 열렸다.", target), 0
        )!!

        assertEquals("G7 정상회의", comparison.source.marker)
        assertEquals("G7 정상회의가 열렸다.", comparison.source.sentence)
        assertEquals("A-3 구역", comparison.target.marker)
        assertEquals("A-3 구역에서 회의가 열렸다.", comparison.target.sentence)
    }
}
