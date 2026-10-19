package com.swpp.team5.frameless.data

import com.swpp.team5.frameless.ui.screens.nonOverlapping
import com.swpp.team5.frameless.ui.screens.centeredReaderOffset
import com.swpp.team5.frameless.ui.screens.preferredFocusPair
import com.swpp.team5.frameless.ui.screens.relatedReaderText
import com.swpp.team5.frameless.ui.screens.readerTailPadding
import com.swpp.team5.frameless.ui.screens.sheetInstruction
import com.swpp.team5.frameless.ui.screens.sourceReaderText
import com.swpp.team5.frameless.ui.screens.targets
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContractTest {
    private val sourceBody = "첫 문장 😀이다.\n두 번째 문장이다."
    private val relatedBody = "첫 대응 표현이다.\n다음 줄이다."

    @Test
    fun parsesGroundedReaderWithUtf16PositionsAndExplicitCategoriesOnly() {
        val result = AnalysisApi.parseAnalysis(root(), "https://source.example/article")
        val reader = requireNotNull(result.reader)
        val target = reader.targets().single()
        val pair = target.pairs.single()

        assertEquals("첫 문장 😀이다.", sourceBody.substring(target.span.start, target.span.end))
        assertEquals("첫 대응 표현이다.", relatedBody.substring(pair.relatedSpan.start, pair.relatedSpan.end))
        assertEquals("😀", sourceBody.substring(
            pair.expressionDifferences.single().sourceMarkerSpan.start,
            pair.expressionDifferences.single().sourceMarkerSpan.end
        ))
        assertEquals(listOf("A"), target.codes)
        assertTrue(sourceReaderText(reader, reader.targets().nonOverlapping()).text.contains("〔A〕"))
        assertTrue(reader.i1Stars.isEmpty())
    }

    @Test
    fun rejectsFalseMarkerAndKeepsNeutralAlignmentInternal() {
        val root = root()
        val marker = firstDifference(root).getJSONObject("related_marker_span")
        marker.put("start", relatedBody.length + 1)
        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)

        assertEquals(1, reader.highlights.size)
        assertTrue(reader.highlights.single().pairs.single().expressionDifferences.isEmpty())
        assertEquals(1, reader.pairs.size)
        assertTrue(reader.targets().isEmpty())
        assertFalse(sourceReaderText(reader, reader.targets()).text.contains("〔A〕"))
    }

    @Test
    fun invalidRelatedSentenceCannotBecomeClickableEvidence() {
        val root = root()
        firstPair(root).getJSONObject("related_span").put("end", relatedBody.length + 10)
        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)

        assertTrue(reader.highlights.isEmpty())
        assertEquals(sourceBody, reader.sourceArticle.body)
    }

    @Test
    fun unsupportedOffsetsOrMissingSourceBodyFallsBackToLegacyUi() {
        val badUnit = root()
        badUnit.getJSONObject("reader").put("offset_unit", "codepoint")
        assertNull(AnalysisApi.parseAnalysis(badUnit, "https://source.example/article").reader)

        val noBody = root()
        noBody.getJSONObject("reader").getJSONObject("source_article").remove("body")
        assertNull(AnalysisApi.parseAnalysis(noBody, "https://source.example/article").reader)
    }

    @Test
    fun newFullReaderAcceptsCategoryPairWithoutLegacyClaimIdAndVerifiedStar() {
        val root = root()
        val reader = root.getJSONObject("reader")
        reader.put("version", "0.2")
        reader.put("coverage", "all_source_sentences_reviewed")
        reader.put("i1_status", "complete")
        reader.getJSONArray("highlights").getJSONObject(0).remove("claim_id")
        firstPair(root).put("alignment_scope", "source_content_unit")
            .put("relation", "reframes")
            .put("difference_summary", "같은 발표를 독자 관점에서 구성")
        reader.put("i1_stars", JSONArray().put(JSONObject()
            .put("source_sentence_id", "S0-S1")
            .put("source_span", span(sourceBody.indexOf("두 번째"), sourceBody.length))
            .put("checked_article_count", 2)
            .put("observed_count", 0)
            .put("label", "이번 비교 기사 2건에서는 확인되지 않음")))

        val parsed = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertEquals("all_source_sentences_reviewed", parsed.coverage)
        assertNull(parsed.highlights.single().claimId)
        assertEquals(listOf("A"), parsed.targets().single().codes)
        assertEquals("같은 발표를 독자 관점에서 구성", parsed.highlights.single().pairs.single().differenceSummary)
        assertEquals(1, parsed.i1Stars.size)
        assertEquals(2, parsed.i1Stars.single().checkedArticleCount)
        assertTrue(sourceReaderText(parsed, parsed.targets()).text.contains("★"))
    }

    @Test
    fun partialI1KeepsIndividuallyVerifiedStarAndIncompleteStatus() {
        val root = root()
        val reader = root.getJSONObject("reader")
        reader.put("version", "0.2")
        reader.put("coverage", "all_source_sentences_reviewed")
        reader.put("i1_status", "partial")
        reader.put("i1_stars", JSONArray().put(JSONObject()
            .put("source_sentence_id", "S0-S1")
            .put("source_span", span(sourceBody.indexOf("두 번째"), sourceBody.length))
            .put("checked_article_count", 2)
            .put("observed_count", 0)
            .put("label", "이번 비교 기사 2건에서는 확인되지 않음")))

        val parsed = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertEquals("partial", parsed.i1Status)
        assertEquals(1, parsed.i1Stars.size)
        assertTrue(sourceReaderText(parsed, parsed.targets()).text.contains("★"))
    }

    @Test
    fun starOnlyResultShowsI1WithoutMakingNeutralAlignmentTappable() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        val neutral = JSONObject(firstPair(root).toString())
            .put("alignment_scope", "source_content_unit")
            .put("expression_differences", JSONArray())
        rawReader.put("version", "0.2")
            .put("coverage", "all_source_sentences_reviewed")
            .put("highlights", JSONArray())
            .put("pairs", JSONArray().put(neutral))
            .put("i1_status", "complete")
            .put("i1_stars", JSONArray().put(JSONObject()
                .put("source_sentence_id", "S0-S1")
                .put("source_span", span(sourceBody.indexOf("두 번째"), sourceBody.length))
                .put("checked_article_count", 2)
                .put("observed_count", 0)
                .put("label", "이번 비교 기사 2건에서는 확인되지 않음")))

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertEquals(1, reader.pairs.size)
        assertTrue(reader.targets().isEmpty())
        val text = sourceReaderText(reader, reader.targets())
        assertEquals("$sourceBody\u2060★", text.text)
        assertTrue(text.getStringAnnotations("reader_target", 0, text.length).isEmpty())
    }

    @Test
    fun i1StarStaysWithUtf16SentenceEndBeforeFollowingNewline() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        rawReader.put("i1_status", "complete")
        rawReader.put("i1_stars", JSONArray().put(JSONObject()
            .put("source_sentence_id", "S0-S0")
            .put("source_span", span(0, sourceBody.indexOf('\n')))
            .put("checked_article_count", 2)
            .put("observed_count", 0)
            .put("label", "이번 비교 기사 2건에서는 확인되지 않음")))

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        val text = sourceReaderText(reader, reader.targets()).text
        assertTrue(text.contains("첫 문장 😀이다.\u2060★ 〔A〕\n두 번째 문장이다."))
        assertFalse(text.contains("😀이다.\n★"))
    }

    @Test
    fun withheldI1DoesNotShowServerStarPayload() {
        val root = root()
        val reader = root.getJSONObject("reader")
        reader.put("i1_status", "withheld")
        reader.put("i1_stars", JSONArray().put(JSONObject()
            .put("source_sentence_id", "S0-S0")
            .put("source_span", span(0, sourceBody.indexOf('\n')))
            .put("checked_article_count", 2)
            .put("observed_count", 0)
            .put("label", "wrong")))
        assertTrue(requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
            .i1Stars.isEmpty())
    }

    @Test
    fun fullReaderWithZeroHighlightsStillKeepsExtractedArticle() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        rawReader.put("version", "0.2")
        rawReader.put("coverage", "all_source_sentences_reviewed")
        rawReader.put("highlights", JSONArray())

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertEquals(sourceBody, reader.sourceArticle.body)
        assertTrue(reader.targets().isEmpty())
        assertEquals(sourceBody, sourceReaderText(reader, reader.targets()).text)
    }

    @Test
    fun fullReaderWithOnlyNeutralPairsShowsUnannotatedSourceAndNoTapTarget() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        val pair = JSONObject(firstPair(root).toString())
            .put("alignment_scope", "source_content_unit")
            .put("expression_differences", JSONArray())
        rawReader.put("version", "0.2")
            .put("coverage", "all_source_sentences_reviewed")
            .put("highlights", JSONArray())
            .put("pairs", JSONArray().put(pair))

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertTrue(reader.highlights.isEmpty())
        assertEquals(1, reader.pairs.size)
        assertTrue(reader.targets().isEmpty())
        val sourceText = sourceReaderText(reader, reader.targets())
        assertEquals(sourceBody, sourceText.text)
        assertTrue(sourceText.spanStyles.isEmpty())
        assertTrue(sourceText.getStringAnnotations("reader_target", 0, sourceText.length).isEmpty())
        val relatedText = relatedReaderText(reader.relatedArticles.single(), reader.pairs)
        assertEquals(relatedBody, relatedText.text)
        assertTrue(relatedText.spanStyles.isEmpty())
    }

    @Test
    fun fullReaderMergesDuplicatePairIdsAndSourceSentenceTargets() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        val highlighted = JSONObject(firstPair(root).toString())
            .put("alignment_scope", "source_content_unit")
        firstPair(root).put("alignment_scope", "source_content_unit")
        val neutral = JSONObject(highlighted.toString())
            .put("pair_id", "P2")
            .put("expression_differences", JSONArray())
        rawReader.put("version", "0.2")
            .put("pairs", JSONArray().put(highlighted).put(neutral))

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        val target = reader.targets().single()
        assertEquals(2, reader.pairs.size)
        assertEquals(1, target.pairs.size)
        assertEquals(listOf("A"), target.codes)
        assertEquals(1, reader.targets().nonOverlapping().size)
    }

    @Test
    fun oneAgSentenceKeepsMultipleMarkedArticlesButSkipsNeutralArticle() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        val marked = JSONObject(firstPair(root).toString())
            .put("alignment_scope", "source_content_unit")
        val secondMarked = JSONObject(marked.toString())
            .put("pair_id", "P2")
            .put("article_id", "R2")
            .put("article_index", 2)
            .put("related_sentence_id", "R2-S0")
        val neutral = JSONObject(marked.toString())
            .put("pair_id", "P3")
            .put("expression_differences", JSONArray())
        rawReader.put("version", "0.2")
            .put("highlights", JSONArray())
            .put("pairs", JSONArray().put(marked).put(secondMarked).put(neutral))
            .getJSONArray("related_articles")
            .put(article("R2", 2, "두 번째 비교 기사", "https://related.example/second", relatedBody))

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertEquals(3, reader.pairs.size)
        val target = reader.targets().single()
        assertEquals(listOf(1, 2), target.pairs.map { it.articleIndex })
        assertEquals(listOf("A"), target.codes)
        assertTrue(sourceReaderText(reader, reader.targets()).text.contains("〔A〕"))
    }

    @Test
    fun fullReaderTrustsCanonicalTopLevelPairWhenNestedPairIdConflicts() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        firstPair(root).put("alignment_scope", "source_content_unit")
        val canonical = JSONObject(firstPair(root).toString())
            .put("expression_differences", JSONArray())
        rawReader.put("version", "0.2")
            .put("pairs", JSONArray().put(canonical))

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertEquals(1, reader.pairs.size)
        assertTrue(reader.targets().isEmpty())
    }

    @Test
    fun invalidTopLevelEvidenceCannotCreateClickableSentence() {
        val root = root()
        val rawReader = root.getJSONObject("reader")
        val pair = JSONObject(firstPair(root).toString())
            .put("alignment_scope", "source_content_unit")
            .put("expression_differences", JSONArray())
        pair.getJSONObject("related_evidence_span").put("text", "원문과 다른 텍스트")
        rawReader.put("version", "0.2")
            .put("highlights", JSONArray())
            .put("pairs", JSONArray().put(pair))

        val reader = requireNotNull(AnalysisApi.parseAnalysis(root, "https://source.example/article").reader)
        assertTrue(reader.pairs.isEmpty())
        assertTrue(reader.targets().isEmpty())
        assertEquals(sourceBody, sourceReaderText(reader, reader.targets()).text)
    }

    @Test
    fun markedPairWinsSheetFocusAgainstEarlierNeutralPair() {
        val reader = requireNotNull(AnalysisApi.parseAnalysis(root(), "https://source.example/article").reader)
        val marked = reader.pairs.single().copy(relatedSpan = ReaderSpan(15, 20))
        val neutral = marked.copy(id = "P2", relatedSpan = ReaderSpan(0, 5),
            expressionDifferences = emptyList())

        assertEquals(marked, listOf(neutral, marked).preferredFocusPair())
        assertNull(listOf(neutral).preferredFocusPair())
        val relatedText = relatedReaderText(reader.relatedArticles.single(), listOf(neutral, marked))
        assertEquals(2, relatedText.spanStyles.size)
    }

    @Test
    fun relatedSheetUsesFullExtractedBodyWithOriginalSentenceSeparators() {
        val reader = requireNotNull(AnalysisApi.parseAnalysis(root(), "https://source.example/article").reader)
        val original = reader.relatedArticles.single()
        val first = original.sentences[0]
        val secondText = original.sentences[1].text

        for (separator in listOf("\n", "\n\n", " ")) {
            val body = first.text + separator + secondText
            val secondStart = first.span.end + separator.length
            val article = original.copy(body = body, sentences = listOf(
                first,
                original.sentences[1].copy(
                    span = ReaderSpan(secondStart, body.length),
                    paragraphIndex = if (separator == " ") 0 else 1
                )
            ))
            assertEquals(body, relatedReaderText(article, reader.pairs).text)
        }
    }

    @Test
    fun sheetInstructionsOnlyOfferToExpandWhileCollapsed() {
        assertEquals("위로 끌어 펼치기", sheetInstruction(pageCount = 1, expanded = false))
        assertEquals("", sheetInstruction(pageCount = 1, expanded = true))
        assertEquals("좌우로 다른 기사 보기 · 위로 끌어 펼치기",
            sheetInstruction(pageCount = 2, expanded = false))
        assertEquals("좌우로 다른 기사 보기", sheetInstruction(pageCount = 2, expanded = true))
    }

    @Test
    fun focusScrollCentersMatchedSentenceWithinVisiblePeekAndClampsAtArticleEdges() {
        assertEquals(500, centeredReaderOffset(sentenceCenter = 700f, visibleHeight = 400, maxScroll = 1000))
        assertEquals(0, centeredReaderOffset(sentenceCenter = 50f, visibleHeight = 400, maxScroll = 1000))
        assertEquals(600, centeredReaderOffset(sentenceCenter = 950f, visibleHeight = 400, maxScroll = 600))
        assertEquals(900, readerTailPadding(viewportHeight = 1000, visibleHeight = 200))
        assertEquals(500, readerTailPadding(viewportHeight = 1000, visibleHeight = 1000))
    }

    private fun root(): JSONObject {
        val sourceEnd = sourceBody.indexOf('\n')
        val relatedEnd = relatedBody.indexOf('\n')
        val sourceEmoji = sourceBody.indexOf("😀")
        val relatedMarker = relatedBody.indexOf("대응")
        val sourceArticle = article("S0", 0, "입력 기사", "https://source.example/article", sourceBody)
        val relatedArticle = article("R1", 1, "비교 기사", "https://related.example/article", relatedBody)
        val pair = JSONObject()
            .put("pair_id", "P1")
            .put("article_id", "R1")
            .put("article_index", 1)
            .put("source_sentence_id", "S0-S0")
            .put("related_sentence_id", "R1-S0")
            .put("source_span", span(0, sourceEnd))
            .put("related_span", span(0, relatedEnd))
            .put("source_evidence_span", markedSpan(0, sourceEnd, sourceBody))
            .put("related_evidence_span", markedSpan(0, relatedEnd, relatedBody))
            .put("alignment_scope", "equivalent_expression")
            .put("relation", "same")
            .put("expression_differences", JSONArray().put(JSONObject()
                .put("code", "A")
                .put("status", "provisional")
                .put("note", "표현의 구체성이 다릅니다")
                .put("source_marker_span", markedSpan(sourceEmoji, sourceEmoji + 2, sourceBody))
                .put("related_marker_span", markedSpan(relatedMarker, relatedMarker + 2, relatedBody))))
        val highlight = JSONObject()
            .put("claim_id", "C1")
            .put("source_sentence_id", "S0-S0")
            .put("source_span", span(0, sourceEnd))
            .put("pairs", JSONArray().put(pair))
        val reader = JSONObject()
            .put("version", "0.1")
            .put("offset_unit", "utf16")
            .put("scope", "extracted_article_text")
            .put("source_article", sourceArticle)
            .put("related_articles", JSONArray().put(relatedArticle))
            .put("highlights", JSONArray().put(highlight))
            .put("i1_status", "not_computed")
            .put("i1_stars", JSONArray())
        return JSONObject()
            .put("source_analysis", JSONObject()
                .put("title", "입력 기사")
                .put("url", "https://source.example/article")
                .put("claims", JSONArray()))
            .put("comparison", JSONObject().put("issues", JSONArray()))
            .put("related_articles", JSONArray())
            .put("reader", reader)
    }

    private fun article(id: String, index: Int, title: String, url: String, body: String): JSONObject {
        val firstEnd = body.indexOf('\n')
        return JSONObject()
            .put("article_id", id)
            .put("article_index", index)
            .put("title", title)
            .put("url", url)
            .put("body", body)
            .put("sentences", JSONArray()
                .put(JSONObject().put("sentence_id", "$id-S0").put("paragraph_index", 0)
                    .put("start", 0).put("end", firstEnd).put("text", body.substring(0, firstEnd)))
                .put(JSONObject().put("sentence_id", "$id-S1").put("paragraph_index", 1)
                    .put("start", firstEnd + 1).put("end", body.length)
                    .put("text", body.substring(firstEnd + 1))))
    }

    private fun span(start: Int, end: Int): JSONObject = JSONObject().put("start", start).put("end", end)
    private fun markedSpan(start: Int, end: Int, body: String): JSONObject =
        span(start, end).put("text", body.substring(start, end))
    private fun firstPair(root: JSONObject): JSONObject = root.getJSONObject("reader")
        .getJSONArray("highlights").getJSONObject(0).getJSONArray("pairs").getJSONObject(0)
    private fun firstDifference(root: JSONObject): JSONObject = firstPair(root)
        .getJSONArray("expression_differences").getJSONObject(0)
}
