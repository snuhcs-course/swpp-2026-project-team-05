package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.AnalysisStatus
import com.swpp.team5.frameless.data.ReaderArticle
import com.swpp.team5.frameless.data.ReaderResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderStatusNoticeTest {
    private fun reader(
        coverage: String = "all_source_sentences_reviewed",
        i1Status: String = "complete",
        articleFailureCount: Int = 0
    ) = ReaderResult(
        version = "0.2",
        coverage = coverage,
        sourceArticle = ReaderArticle("S0", 0, "기사", null, "본문", emptyList()),
        relatedArticles = emptyList(),
        highlights = emptyList(),
        pairs = emptyList(),
        i1Stars = emptyList(),
        i1Status = i1Status,
        articleFailureCount = articleFailureCount
    )

    private fun partial(vararg errorStages: String) = AnalysisStatus(
        executionStatus = "partial",
        comparisonStatus = "available",
        warnings = emptyList(),
        errorStages = errorStages.toSet()
    )

    @Test
    fun completedReaderWithSearchFailureDoesNotClaimSentenceComparisonIsIncomplete() {
        val notice = readerStatusNotice(reader(), partial("search"))!!

        assertEquals("관련 기사 검색이 일부 실패했어요.", notice.headline)
        assertFalse(notice.headline.contains("문장 비교"))
        assertEquals(
            "확인된 범위의 결과만 표시해요. 표시가 없어도 차이가 없다는 뜻은 아니에요.",
            notice.detail
        )
    }

    @Test
    fun sentenceReviewAndI1FailuresTakePriorityOverSearchFailure() {
        assertEquals(
            "문장 비교가 일부만 끝났어요.",
            readerStatusNotice(reader(coverage = "verified_displayed_claims_only"), partial("search"))?.headline
        )
        assertEquals(
            "별표 표시를 일부 확인하지 못했어요.",
            readerStatusNotice(reader(i1Status = "partial"), partial("search"))?.headline
        )
        assertEquals(
            "일부 기사를 비교하지 못했어요.",
            readerStatusNotice(reader(articleFailureCount = 1), partial("search"))?.headline
        )
    }

    @Test
    fun mixedOrUnknownPipelineErrorsKeepANonspecificWarning() {
        assertEquals(
            "일부 분석 결과를 확인하지 못했어요.",
            readerStatusNotice(reader(), partial("search", "rubric_analysis"))?.headline
        )
        assertEquals(
            "일부 분석 결과를 확인하지 못했어요.",
            readerStatusNotice(reader(), partial())?.headline
        )
    }

    @Test
    fun completeReaderAndSuccessfulPipelineShowNoWarning() {
        assertNull(readerStatusNotice(reader(), AnalysisStatus("success", "available", emptyList())))
    }
}
