package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Oracles: specs/testing/reader-comparison-summary.spec.md (Draft). */
class ReaderComparisonSummaryTest {
    private val overview = "한 기사는 징계라고 쓰고 다른 기사는 중징계라고 전해요."

    private fun page(code: String, name: String, vararg markers: String) = ComparisonArticleUi(
        articleId = "article-$code",
        publisher = PublisherItem(code, name, null),
        title = "$name 기사",
        publishedAt = "2026-11-18T10:00:00+09:00",
        originalUrl = "https://example.com/$code",
        body = "본문",
        evidence = markers.map { marker ->
            ComparisonEvidenceUi(
                scope = "BODY",
                sentenceRange = TextRange(0, 2),
                sentenceText = "본문",
                markerRange = TextRange(0, 2),
                markerText = marker,
                owner = "REPORTER",
                speakerLabel = null,
                summary = "요약"
            )
        }
    )

    private fun finding(
        id: String,
        marker: String,
        comparisons: List<ComparisonArticleUi>,
        scope: String = "BODY",
        start: Int = 0,
        categories: List<String> = listOf("C")
    ) = FindingUi(
        id = id,
        categories = categories,
        scope = scope,
        sentenceRange = TextRange(start, start + marker.length),
        sentenceText = marker,
        markerRange = TextRange(start, start + marker.length),
        markerText = marker,
        explanation = "$marker 설명",
        comparisonOverview = overview,
        comparisons = comparisons
    )

    @Test
    fun sectionIsHiddenWithoutAValidComparison() {
        val sourceOnly = finding("source-only", "징계", emptyList())
        val blankTarget = finding("blank-target", "거세게", listOf(page("kbs", "KBS", "  ")))
        val blankSource = finding("blank-source", " ", listOf(page("kbs", "KBS", "중징계")))

        val points = comparisonPoints("연합뉴스", listOf(sourceOnly, blankTarget, blankSource))

        assertTrue(points.isEmpty())
        assertTrue(comparisonPointTexts(points).isEmpty())
        assertTrue(comparisonPoints("연합뉴스", emptyList()).isEmpty())
    }

    @Test
    fun pointUsesCurrentPublisherMarkerAndOneRealComparedMarkerExactly() {
        val point = comparisonPoints("연합뉴스", listOf(
            finding("f1", "G7 정상회의", listOf(page("blank", "MBC", " "), page("kbs", "KBS", "A-3 구역", "탈당권고")))
        )).single()

        // Exact reviewed markers are shown as stored, including identifier-like text.
        assertEquals(MarkerLine("연합뉴스", "G7 정상회의"), point.source)
        assertEquals(MarkerLine("KBS", "A-3 구역"), point.target)
        assertEquals("연합뉴스  “G7 정상회의”", markerLineText(point.source))
    }

    @Test
    fun duplicateComparisonPointIsNotRepeated() {
        val kbs = page("kbs", "KBS", "중징계")
        val first = finding("f1", "징계", listOf(kbs), start = 0)
        val sameLines = finding("f2", "징계", listOf(kbs), start = 20)
        val sameId = finding("f1", "결정", listOf(page("mbc", "MBC", "의결")), start = 30)
        val other = finding("f3", "신속했다", listOf(page("mbc", "MBC", "졸속이었다")), start = 40)

        val points = comparisonPoints("연합뉴스", listOf(other, sameId, sameLines, first))

        assertEquals(listOf("f1", "f3"), points.map { it.finding.id })
        assertEquals(1, comparisonPointTexts(points).count { it == "연합뉴스  “징계”" })
    }

    @Test
    fun atMostTwoPointsInReadingOrder() {
        val late = finding("late", "늦은", listOf(page("a", "가", "나중")), start = 90)
        val early = finding("early", "이른", listOf(page("b", "나", "먼저")), start = 5)
        val title = finding("title", "제목", listOf(page("c", "다", "머리")), scope = "TITLE", start = 50)

        val points = comparisonPoints("연합뉴스", listOf(late, early, title))

        assertEquals(MaxComparisonPoints, points.size)
        assertEquals(listOf("title", "early"), points.map { it.finding.id })
    }

    @Test
    fun sectionTextsNeverContainOverviewExplanationOrCategoryLabels() {
        val codes = listOf("A", "B", "C", "D", "E", "F", "G")
        val finding = finding("f1", "징계", listOf(page("kbs", "KBS", "중징계")), categories = codes)
        val texts = comparisonPointTexts(comparisonPoints("연합뉴스", listOf(finding)))
        val standaloneCode = Regex("(?<![A-Za-z0-9])[A-G](?![A-Za-z0-9])")

        assertEquals(listOf(ComparisonPointsHeading, "연합뉴스  “징계”", "KBS  “중징계”", ComparisonPointAction), texts)
        texts.forEach { text ->
            assertFalse(text.contains(finding.comparisonOverview))
            assertFalse(text.contains(finding.explanation))
            assertFalse("raw code in \"$text\"", standaloneCode.containsMatchIn(text))
            codes.map(::categoryLabel).forEach { label -> assertFalse(text.contains(label)) }
        }
    }

    @Test
    fun tappingAPointOpensTheSameDetailStateAsThePreviewAction() {
        val target = finding("f1", "징계", listOf(page("kbs", "KBS", "중징계"), page("mbc", "MBC", "탈당권고")))

        val fromPreview = previewDetailSelection(FindingSelection(target, detailOpen = false))
        val fromPoint = comparisonPointSelection(target)

        assertEquals(FindingSelection(target, detailOpen = true), fromPreview)
        assertEquals(fromPreview, fromPoint)
        val sourceOnlyPreview = FindingSelection(finding("s", "징계", emptyList()), detailOpen = false)
        assertEquals(sourceOnlyPreview, previewDetailSelection(sourceOnlyPreview))
    }
}
