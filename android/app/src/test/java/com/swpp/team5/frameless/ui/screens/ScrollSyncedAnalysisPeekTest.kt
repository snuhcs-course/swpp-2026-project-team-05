package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Oracles: specs/testing/scroll-synced-analysis-peek.spec.md. */
class ScrollSyncedAnalysisPeekTest {
    @Test
    fun selectsNearestEligibleBodyFindingIncludingBandBoundaries() {
        val markers = listOf(
            PeekMarkerPosition("outside-before", 44.9f, 0),
            PeekMarkerPosition("band-start", 45f, 1),
            PeekMarkerPosition("nearest", 54f, 2),
            PeekMarkerPosition("band-end", 65f, 3),
            PeekMarkerPosition("outside-after", 65.1f, 4)
        )

        assertEquals("nearest", selectScrollSyncedPeekCandidate(markers, 0f, 100f))
        assertEquals("band-start", selectScrollSyncedPeekCandidate(
            listOf(markers[0], markers[1]), 0f, 100f
        ))
        assertEquals("band-end", selectScrollSyncedPeekCandidate(
            listOf(markers[3], markers[4]), 0f, 100f
        ))
        assertNull(selectScrollSyncedPeekCandidate(
            listOf(markers[0], markers[4]), 0f, 100f
        ))
        assertNull(selectScrollSyncedPeekCandidate(markers, 10f, 10f))
    }

    @Test
    fun readingOrderBreaksAnEqualDistanceTie() {
        val markers = listOf(
            PeekMarkerPosition("later", 60f, 4),
            PeekMarkerPosition("earlier", 50f, 1)
        )

        assertEquals("earlier", selectScrollSyncedPeekCandidate(markers, 0f, 100f))
    }

    @Test
    fun peekContentKeepsTwoPublisherDiverseExactMarkersAndCountsTheRest() {
        val finding = finding(listOf(
            page("a1", "a", "연합뉴스", "확대했다"),
            page("a2", "a", "연합뉴스", "늘렸다"),
            page("b", "b", "MBC", "넓히고 있다"),
            page("blank", "c", "KBS", " "),
            page("d", "d", "한겨레", "확장 중이다")
        ))

        assertEquals(
            AnalysisPeekContent(
                marker = "빠르게 확장했다",
                explanation = "확장 속도를 강조해요.",
                comparisons = listOf(
                    PeekComparisonExpression("연합뉴스", "확대했다"),
                    PeekComparisonExpression("MBC", "넓히고 있다")
                ),
                remainingPublisherCount = 1
            ),
            analysisPeekContent(finding)
        )
    }

    @Test
    fun sourceOnlyPeekHasNoComparisonRows() {
        assertEquals(emptyList<PeekComparisonExpression>(), analysisPeekContent(finding(emptyList())).comparisons)
        assertEquals(0, analysisPeekContent(finding(emptyList())).remainingPublisherCount)
    }

    private fun finding(comparisons: List<ComparisonArticleUi>) = FindingUi(
        id = "finding",
        categories = listOf("D"),
        scope = "BODY",
        sentenceRange = TextRange(0, 10),
        sentenceText = "빠르게 확장했다",
        markerRange = TextRange(0, 9),
        markerText = "빠르게 확장했다",
        explanation = "확장 속도를 강조해요.",
        comparisonOverview = "표현을 다르게 사용해요.",
        comparisons = comparisons
    )

    private fun page(
        articleId: String,
        publisherCode: String,
        publisherName: String,
        marker: String
    ) = ComparisonArticleUi(
        articleId = articleId,
        publisher = PublisherItem(publisherCode, publisherName, null),
        title = "기사",
        publishedAt = "2026-11-21T10:00:00+09:00",
        originalUrl = "https://example.com/$articleId",
        body = "기사 본문",
        evidence = listOf(ComparisonEvidenceUi(
            scope = "BODY",
            sentenceRange = TextRange(0, 5),
            sentenceText = "기사 본문",
            markerRange = TextRange(0, marker.length.coerceAtMost(5)),
            markerText = marker,
            owner = "REPORTER",
            speakerLabel = null,
            summary = ""
        ))
    )
}

