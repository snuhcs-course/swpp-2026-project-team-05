package com.swpp.team5.frameless.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsAnalysisMapperTest {
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
            marker = ApiMarker(4, 6, "전격"), explanation = "강조", comparisons = emptyList()
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
            explanation = "속도를 강조한다.", comparisons = listOf(target)
        )

        val mapped = ApiArticleAnalysis(StoredAnalysisState.READY, "v1", listOf(finding)).toUi(article)

        assertEquals("빠르게", mapped.findings.single().comparisons.single().evidence.single().markerText)
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
