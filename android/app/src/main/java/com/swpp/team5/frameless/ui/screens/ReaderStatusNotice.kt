package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.AnalysisStatus
import com.swpp.team5.frameless.data.ReaderResult

internal data class ReaderStatusNotice(val headline: String, val detail: String)

/** A pipeline warning does not necessarily mean the Reader's sentence review was incomplete. */
internal fun readerStatusNotice(reader: ReaderResult, status: AnalysisStatus?): ReaderStatusNotice? {
    val fullReview = reader.version == "0.2" && reader.coverage == "all_source_sentences_reviewed"
    val hasPartialPipeline = status?.executionStatus in setOf("partial", "failed")
    if (fullReview && !hasPartialPipeline && reader.articleFailureCount == 0 &&
        reader.i1Status !in setOf("partial", "withheld")) return null

    val headline = when {
        reader.articleFailureCount > 0 -> "일부 기사를 비교하지 못했어요."
        !fullReview -> "문장 비교가 일부만 끝났어요."
        reader.i1Status == "partial" -> "별표 표시를 일부 확인하지 못했어요."
        reader.i1Status == "withheld" -> "별표 판정을 보류했어요."
        status?.executionStatus == "partial" && status.errorStages == setOf("search") ->
            "관련 기사 검색이 일부 실패했어요."
        status?.executionStatus == "partial" && status.errorStages == setOf("candidate_fetch") ->
            "일부 관련 기사 본문을 가져오지 못했어요."
        status?.executionStatus == "partial" &&
            status.errorStages == setOf("search", "candidate_fetch") ->
            "일부 관련 기사를 확보하지 못했어요."
        else -> "일부 분석 결과를 확인하지 못했어요."
    }
    return ReaderStatusNotice(
        headline = headline,
        detail = "확인된 범위의 결과만 표시해요. 표시가 없어도 차이가 없다는 뜻은 아니에요."
    )
}
