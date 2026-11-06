package com.swpp.team5.frameless.ui.state

import com.swpp.team5.frameless.data.AnalysisApi
import com.swpp.team5.frameless.data.AnalysisApiTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// AI-assisted state tests: every UI group and card must stay traceable to a server issue and article.
class ComparisonModelTest {
    @Test
    fun groupsAndClipsKeepTheServerArticleAndRelation() {
        val result = AnalysisApi.parseAnalysis(
            AnalysisApiTest.responseWithMatches(), "https://requested.example/article"
        )
        val model = ComparisonModel(result)

        assertFalse(model.noMatches)
        assertFalse(model.questionMode)
        assertEquals("주장", model.noun)
        assertEquals(2, model.groupCount)
        assertEquals(listOf(AnalysisApiTest.SOURCE_QUOTE, "지급은 다음 달 시작된다."),
            model.inputSentences.map { it.quote })

        val budgetSentence = model.inputSentences.first()
        assertEquals(listOf(0), model.groupsFor(budgetSentence))
        assertEquals(listOf(1, 2), model.matchesFor(budgetSentence).map { it.article.articleIndex })
        assertEquals(listOf("same", "opposes"), model.matchesFor(budgetSentence).map { it.relationRaw })
        assertTrue(model.groupsFor(model.inputSentences.last()).isEmpty())

        val budgetIssue = model.issues.first()
        assertEquals(1, budgetIssue.sameOutlets)
        assertEquals(1, budgetIssue.diffOutlets)
        assertTrue(budgetIssue.notFound.isEmpty())
        assertEquals("https://related.example/first", budgetIssue.same.single().article.url)
        assertEquals(listOf("첫 번째 기사"), model.issues.last().notFound.map { it.title })
        assertEquals(listOf("시민단체는 우선순위를 다시 논의해야 한다고 했다."),
            model.unlinkedClips.map { it.quote })
    }

    @Test
    fun optionalRubricIsAttachedOnlyWhenItsEvidenceMatchesAnIssue() {
        val root = AnalysisApiTest.responseWithMatches().put("rubric", JSONObject("""
            {
              "version": "0.2.1",
              "questions": [],
              "expression_differences": [{
                "article_index": 0,
                "sentence": "${AnalysisApiTest.SOURCE_QUOTE}",
                "span": "늘린다",
                "categories": [{"code": "A", "markedness": 1}],
                "baseline": {"source": "outlet", "text": "증액한다"},
                "polarity": "none"
              }],
              "information_differences": [{
                "article_index": 1,
                "code": "I1",
                "sentence": "예산이 늘어날 예정이다.",
                "value": "예산 확대 설명"
              }]
            }
        """.trimIndent()))
        val model = ComparisonModel(AnalysisApi.parseAnalysis(root, "https://requested.example/article"))

        assertEquals(listOf("늘린다"), model.issues.first().rubric.expressions.map { it.e.span })
        assertEquals(listOf("I1"), model.issues.first().rubric.informations.map { it.i.code })
        assertTrue(model.issues.last().rubric.isEmpty)
        assertTrue(model.unassignedRubric.isEmpty)
        assertFalse(model.questionMode)
    }

    @Test
    fun textHelpersDoNotTreatQuotedSpeechAsPublisherExpression() {
        val sentence = "정부는 “지원 예산을 늘린다”고 밝혔다."
        assertTrue(insideDirectQuote(sentence, "늘린다"))
        assertFalse(insideDirectQuote(sentence, "밝혔다"))
        assertEquals("예산을 늘린다", cleanQuote("  “예산을 늘린다”  "))
        assertEquals("‘예산’을 늘린다", cleanQuote("  “‘예산’을 늘린다”  "))
        assertTrue(sameText("  “예산을 늘린다”  ", "예산을 늘린다"))
        assertFalse(sameText("예산 확대", "예산 삭감"))
    }

    @Test
    fun directQuoteRubricIsShownAsQuotedInformationRatherThanPublisherTone() {
        val sentence = "정부는 “지원 예산을 늘린다”고 밝혔다."
        val root = AnalysisApiTest.responseWithMatches()
        root.getJSONObject("source_analysis").getJSONArray("claims")
            .getJSONObject(0).getJSONObject("evidence").put("quote", sentence)
        root.getJSONObject("comparison").getJSONArray("issues").getJSONObject(0)
            .getJSONArray("members").getJSONObject(0).getJSONObject("evidence").put("quote", sentence)
        root.put("rubric", JSONObject("""
            {
              "version": "0.2.1",
              "expression_differences": [
                {"article_index": 0, "sentence": "$sentence", "span": "늘린다",
                 "categories": [{"code": "A", "markedness": 1}],
                 "baseline": {"source": "outlet", "text": "증액한다"}, "polarity": "none"},
                {"article_index": 0, "sentence": "$sentence", "span": "밝혔다",
                 "categories": [{"code": "A", "markedness": 1}],
                 "baseline": {"source": "outlet", "text": "전했다"}, "polarity": "none"}
              ]
            }
        """.trimIndent()))

        val model = ComparisonModel(AnalysisApi.parseAnalysis(root, "https://requested.example/article"))

        assertEquals(listOf("늘린다"), model.issues.first().rubric.quotedExpressions.map { it.e.span })
        assertEquals(listOf("밝혔다"), model.issues.first().rubric.expressions.map { it.e.span })
        assertEquals(listOf("밝혔다"), model.inputExpressions.map { it.span })
    }

    @Test
    fun questionModeUsesExplicitServerAnswersWhenProvided() {
        val root = AnalysisApiTest.responseWithMatches().put("rubric", JSONObject("""
            {
              "version": "0.2.1",
              "questions": [{
                "id": "Q1", "question": "예산이 늘어나는가?",
                "answers": [
                  {"article_index": 0, "status": "answered", "summary": "증액 발표",
                   "evidence_quotes": ["${AnalysisApiTest.SOURCE_QUOTE}"]},
                  {"article_index": 1, "status": "answered", "summary": "증액 전망",
                   "evidence_quotes": ["예산이 늘어날 예정이다."]},
                  {"article_index": 2, "status": "absent", "summary": "",
                   "evidence_quotes": [], "absence_reason": "unknown", "relevance": "issue_relevant"}
                ]
              }]
            }
        """.trimIndent()))

        val model = ComparisonModel(AnalysisApi.parseAnalysis(root, "https://requested.example/article"))

        assertTrue(model.questionMode)
        assertEquals("쟁점", model.noun)
        assertEquals(1, model.groupCount)
        assertEquals(listOf(0), model.groupsFor(model.inputSentences.first()))
        assertEquals("예산이 늘어나는가?", model.questions.single().question)
        assertEquals(3, model.questions.single().answers.size)
        assertEquals(1, model.questions.single().sameOutlets)
        assertEquals(1, model.questions.single().diffOutlets)
        assertEquals("unknown", model.questions.single().answers.last().answer.absenceReason)
    }

    @Test
    fun unavailableComparisonStatusIsNotMisreportedAsNoMatches() {
        val root = AnalysisApiTest.responseWithoutMatches().put("analysis_status", JSONObject("""
            {"execution_status": "partial", "comparison_status": "unavailable",
             "warnings": ["comparison stage failed"]}
        """.trimIndent()))

        val result = AnalysisApi.parseAnalysis(root, "https://requested.example/article")

        assertEquals("partial", result.analysisStatus?.executionStatus)
        assertEquals(listOf("comparison stage failed"), result.analysisStatus?.warnings)
        assertFalse(ComparisonModel(result).noMatches)
        root.getJSONObject("analysis_status").put("comparison_status", "no_matches")
        assertTrue(ComparisonModel(AnalysisApi.parseAnalysis(root, "https://requested.example/article")).noMatches)
    }

    @Test
    fun v2ClaimIdPreventsTheSameSentenceFromAttachingRubricToAnotherClaim() {
        val root = AnalysisApiTest.responseWithMatches()
        val issues = root.getJSONObject("comparison").getJSONArray("issues")
        issues.getJSONObject(0).put("claim_id", "C1")
        issues.getJSONObject(1).put("claim_id", "C2")
        issues.getJSONObject(1).getJSONArray("members").getJSONObject(0)
            .getJSONObject("evidence").put("quote", AnalysisApiTest.SOURCE_QUOTE)
        root.put("rubric", JSONObject("""
            {
              "version": "0.2.1",
              "expression_differences": [{
                "article_index": 0, "claim_id": "C1",
                "sentence": "${AnalysisApiTest.SOURCE_QUOTE}", "span": "늘린다",
                "categories": [{"code": "A", "markedness": null}],
                "baseline": {"source": "outlet", "text": "증액한다"},
                "polarity": "none", "status": "needs_review"
              }]
            }
        """.trimIndent()))

        val model = ComparisonModel(AnalysisApi.parseAnalysis(root, "https://requested.example/article"))

        assertEquals(listOf("늘린다"), model.issues.first().rubric.expressions.map { it.e.span })
        assertTrue(model.issues.last().rubric.isEmpty)
        assertNull(model.issues.first().rubric.expressions.single().e.maxMarkedness)
        assertTrue(model.issues.first().rubric.expressions.single().e.held)
    }

    @Test
    fun expressionFailureMarksOnlyItsClaimAndWithholdsStaleExpressionCards() {
        val root = AnalysisApiTest.responseWithMatches()
        val issues = root.getJSONObject("comparison").getJSONArray("issues")
        issues.getJSONObject(0).put("claim_id", "C1")
        issues.getJSONObject(1).put("claim_id", "C2")
        root.put("rubric", JSONObject("""
            {
              "version": "0.2.1",
              "expression_unverified_claim_ids": ["C1"],
              "expression_differences": [
                {
                  "article_index": 0, "claim_id": "C1",
                  "sentence": "${AnalysisApiTest.SOURCE_QUOTE}", "span": "늘린다",
                  "categories": [{"code": "A", "markedness": 2}],
                  "baseline": {"source": "outlet", "text": "증액한다"},
                  "polarity": "none", "status": "provisional"
                },
                {
                  "article_index": 0, "claim_id": "C2",
                  "sentence": "지급은 다음 달 시작된다.", "span": "시작된다",
                  "categories": [{"code": "A", "markedness": 1}],
                  "baseline": {"source": "outlet", "text": "진행된다"},
                  "polarity": "none", "status": "provisional"
                }
              ]
            }
        """.trimIndent()))

        val model = ComparisonModel(AnalysisApi.parseAnalysis(root, "https://requested.example/article"))

        assertTrue(model.issues[0].expressionUnverified)
        assertTrue(model.issues[0].rubric.expressions.isEmpty())
        assertFalse(model.issues[1].expressionUnverified)
        assertEquals(listOf("시작된다"), model.issues[1].rubric.expressions.map { it.e.span })
        assertTrue(model.unassignedRubric.isEmpty)
    }
}
