package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionPairTest {
    private fun evidence(scope: String, sentence: String, marker: String): ComparisonEvidenceUi {
        val start = sentence.indexOf(marker).coerceAtLeast(0)
        return ComparisonEvidenceUi(
            scope, TextRange(0, sentence.length), sentence,
            TextRange(start, start + marker.length), marker, "REPORTER", null, "요약은 표시하지 않음"
        )
    }

    private fun page(vararg rows: ComparisonEvidenceUi) = ComparisonArticleUi(
        "cmp-1", PublisherItem("mbc", "MBC", null), "비교 기사 제목", "2026-11-19T09:00:00+09:00",
        "https://example.com/cmp-1", "본문", rows.toList()
    )

    private fun finding(sentence: String, marker: String) = FindingUi(
        "f1", listOf("D"), "BODY", TextRange(0, sentence.length), sentence,
        TextRange(0, marker.length), marker, "", "", emptyList()
    )

    @Test
    fun pairsTheFindingPhraseWithTheFirstBodyEvidenceVerbatim() {
        val pair = expressionPairFor(
            finding("회사는 사업을 빠르게 확장했다.", "빠르게 확장했다"),
            "연합뉴스",
            page(
                evidence("TITLE", "사업 영역 확대", "확대"),
                evidence("BODY", "회사는 사업 영역을 넓히고 있다.", "넓히고 있다"),
                evidence("BODY", "다른 문장이다.", "다른")
            )
        )!!
        assertEquals("연합뉴스", pair.current.publisher)
        assertEquals("빠르게 확장했다", pair.current.phrase)
        assertEquals("회사는 사업을 빠르게 확장했다.", pair.current.context)
        assertEquals("MBC", pair.compared.publisher)
        assertEquals("넓히고 있다", pair.compared.phrase)
        assertEquals("회사는 사업 영역을 넓히고 있다.", pair.compared.context)
        val range = pair.compared.phraseInContext!!
        assertEquals("넓히고 있다", pair.compared.context.substring(range.first, range.last + 1))
    }

    @Test
    fun fallsBackToTitleEvidenceWhenThereIsNoBodyEvidence() {
        val pair = expressionPairFor(finding("확장", "확장"), "연합뉴스",
            page(evidence("TITLE", "사업 영역 확대", "확대")))!!
        assertEquals("확대", pair.compared.phrase)
    }

    @Test
    fun returnsNullInsteadOfInventingAMissingSide() {
        assertNull(expressionPairFor(finding("문장", " "), "연합뉴스",
            page(evidence("BODY", "문장이다", "문장"))))
        assertNull(expressionPairFor(finding("문장", "문장"), "연합뉴스", page()))
        assertNull(expressionPairFor(finding("문장", "문장"), "연합뉴스",
            page(evidence("BODY", "문장이다", ""))))
    }

    @Test
    fun contextThatOnlyRepeatsThePhraseIsDropped() {
        val pair = expressionPairFor(finding("빠르게 확장했다", "빠르게 확장했다"), "연합뉴스",
            page(evidence("BODY", "넓히고 있다", "넓히고 있다")))!!
        assertEquals("", pair.current.context)
        assertNull(pair.current.phraseInContext)
        assertEquals("", pair.compared.context)
    }

    @Test
    fun contextWindowKeepsThePhraseVisibleWithoutAlteringText() {
        val near = contextWindow("앞 문장에서 빠르게 확장했다.", "빠르게")
        assertEquals("앞 문장에서 빠르게 확장했다.", near.first)
        assertEquals(7 until 10, near.second)

        val sentence = "아주 긴 머리말이 이어지는 문장으로 여러 사정을 먼저 설명한 뒤에야 비로소 표현이 등장한다."
        val far = contextWindow(sentence, "표현이 등장한다", lead = 10)
        assertTrue(far.first.startsWith("…"))
        assertTrue(sentence.endsWith(far.first.removePrefix("…")))
        val r = far.second!!
        assertEquals("표현이 등장한다", far.first.substring(r.first, r.last + 1))

        val missing = contextWindow("문장", "없는 표현")
        assertEquals("문장", missing.first)
        assertNull(missing.second)
    }
}
