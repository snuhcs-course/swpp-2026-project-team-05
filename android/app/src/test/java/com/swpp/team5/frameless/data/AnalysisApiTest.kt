package com.swpp.team5.frameless.data

import com.swpp.team5.frameless.ui.state.ComparisonModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// AI-assisted contract tests: fixture fields mirror the Android UI's current backend response contract.
class AnalysisApiTest {
    @Test
    fun networkErrorMessage_hidesCertificateDetailsAndHandlesOtherIoFailures() {
        val certificateFailure = SSLHandshakeException(
            "java.security.cert.CertPathValidatorException: Trust anchor for certification path not found."
        )

        assertEquals(
            "보안 연결을 확인하지 못했어요. 네트워크 설정을 확인하거나 관리자에게 문의해 주세요.",
            AnalysisApi.networkErrorMessage(certificateFailure)
        )
        assertEquals(
            AnalysisApi.networkErrorMessage(certificateFailure),
            AnalysisApi.networkErrorMessage(SSLException("TLS protocol failure"))
        )
        assertFalse(AnalysisApi.networkErrorMessage(certificateFailure).contains("CertPathValidatorException"))
        assertEquals(
            "네트워크 오류가 발생했어요. 연결 상태를 확인하고 다시 시도해 주세요.",
            AnalysisApi.networkErrorMessage(IOException("raw network details"))
        )
    }

    @Test
    fun networkErrorMessage_preservesSpecificConnectionAndTimeoutGuidance() {
        assertEquals(
            "서버에 연결할 수 없어요. 서버 주소와 상태를 확인해줘.",
            AnalysisApi.networkErrorMessage(ConnectException("connection refused"))
        )
        assertEquals(
            "분석 시간이 초과됐어요. 잠시 후 다시 시도해줘.",
            AnalysisApi.networkErrorMessage(SocketTimeoutException("read timed out"))
        )
    }

    @Test
    fun parseAnalysis_preservesEvidenceRelationsAndArticleLinks() {
        val result = AnalysisApi.parseAnalysis(responseWithMatches(), "https://requested.example/article")

        assertEquals("입력 기사", result.sourceTitle)
        assertEquals("https://source.example/article", result.sourceUrl)
        assertEquals("지원 예산 발표", result.coreEvent)
        assertEquals(3, result.relatedArticleCount)
        assertEquals(listOf(0, 1, 2), result.articles.map { it.articleIndex })
        assertEquals("https://related.example/first", result.articles[1].url)
        assertEquals(listOf("쟁점에 연결되지 않은 기사"), result.relatedWithoutMatches)

        val linked = result.issues.first().members.filter { it.articleIndex > 0 }
        assertEquals(listOf(Relation.SAME, Relation.OPPOSES), linked.map { it.relation })
        assertEquals(listOf(0, 0), linked.map { it.sourceClaimIndex })
        assertEquals(listOf("예산이 늘어날 예정이다.", "예산은 아직 확정되지 않았다."), linked.map { it.quote })
        assertEquals(2, result.claims.size)
        assertEquals(setOf(ClaimType.SHARED, ClaimType.DIFFERENT), result.claims.map { it.type }.toSet())
        assertTrue(result.claims.all { it.passages.first().excerpt == SOURCE_QUOTE })
        assertEquals(listOf("지급은 다음 달 시작된다."), result.sourceOnlyClaims.map { it.quote })
        assertEquals(listOf("시민단체는 우선순위를 다시 논의해야 한다고 했다."), result.relatedOnlyMembers.map { it.quote })
        assertNull(result.rubric)
    }

    @Test
    fun parseAnalysis_noCorrespondingSentenceKeepsAConservativeEmptyState() {
        val result = AnalysisApi.parseAnalysis(responseWithoutMatches(), "https://requested.example/article")
        val model = ComparisonModel(result)

        assertEquals("https://requested.example/article", result.sourceUrl)
        assertEquals("대응 문장 없는 기사", result.sourceTitle)
        assertEquals(1, result.relatedArticleCount)
        assertEquals(listOf("비교 기사"), result.relatedWithoutMatches)
        assertEquals(listOf(SOURCE_QUOTE), result.sourceOnlyClaims.map { it.quote })
        assertTrue(result.relatedOnlyMembers.isEmpty())
        assertTrue(model.noMatches)
        assertEquals(0, model.groupCount)
        assertFalse(model.questionMode)
    }

    @Test
    fun parseAnalysis_missingRequiredSectionsFailClearly() {
        val missingSource = assertThrows(IllegalStateException::class.java) {
            AnalysisApi.parseAnalysis(JSONObject("""{"comparison":{"issues":[]}}"""), "https://requested.example/article")
        }
        assertEquals("분석 결과에 입력 기사 정보가 없어요.", missingSource.message)

        val missingComparison = assertThrows(IllegalStateException::class.java) {
            AnalysisApi.parseAnalysis(JSONObject("""{"source_analysis":{"title":"입력 기사"}}"""), "https://requested.example/article")
        }
        assertEquals("분석 결과에 비교 정보가 없어요.", missingComparison.message)
    }

    @Test
    fun parseAnalysis_unknownRelationAndUnsafeUrlAreNotPromotedToEvidenceLinks() {
        val root = responseWithMatches()
        val member = root.getJSONObject("comparison").getJSONArray("issues")
            .getJSONObject(0).getJSONArray("members").getJSONObject(1)
        member.put("relation_to_source", "unverified")
        member.put("url", "javascript:alert(1)")

        val result = AnalysisApi.parseAnalysis(root, "https://requested.example/article")

        assertEquals("unverified", result.issues.first().members[1].relationRaw)
        assertEquals(Relation.OTHER, result.issues.first().members[1].relation)
        assertNull(result.issues.first().members[1].url)
        assertFalse(result.claims.any { it.passages.any { passage -> passage.relationRaw == "unverified" } })
    }

    @Test
    fun parseAnalysis_relatedArticleArrayOrderDoesNotChangeIssueArticleIdentity() {
        val root = responseWithMatches()
        val original = root.getJSONArray("related_articles")
        root.put("related_articles", JSONArray()
            .put(original.getJSONObject(2))
            .put(original.getJSONObject(1))
            .put(original.getJSONObject(0)))

        val result = AnalysisApi.parseAnalysis(root, "https://requested.example/article")

        assertEquals("https://related.example/first", result.articles.first { it.articleIndex == 1 }.url)
        assertEquals("https://related.example/second", result.articles.first { it.articleIndex == 2 }.url)
        assertEquals(listOf("쟁점에 연결되지 않은 기사"), result.relatedWithoutMatches)
    }

    @Test
    fun parseAnalysis_explicitArticleIdKeepsAnUnmatchedArticleAndRejectsConflictingLinks() {
        val root = responseWithMatches()
        val related = root.getJSONArray("related_articles")
        for (i in 0 until related.length()) related.getJSONObject(i).put("article_index", i + 1)

        val result = AnalysisApi.parseAnalysis(root, "https://requested.example/article")
        assertEquals(listOf(0, 1, 2, 3), result.articles.map { it.articleIndex })
        assertEquals("쟁점에 연결되지 않은 기사", result.articles.last().title)

        related.getJSONObject(0).put("url", "https://unrelated.example/wrong")
        val mismatch = assertThrows(IllegalStateException::class.java) {
            AnalysisApi.parseAnalysis(root, "https://requested.example/article")
        }
        assertTrue(mismatch.message.orEmpty().contains("기사 ID가 일치하지 않아요"))
    }

    @Test
    fun parseAnalysis_invalidOptionalRubricItemsAreDroppedWithoutDiscardingComparison() {
        val root = responseWithMatches().put("rubric", JSONObject("""
            {
              "version": "0.2.1",
              "questions": [{"id": "", "question": "질문", "answers": []}],
              "expression_differences": [
                {
                  "article_index": 0,
                  "sentence": "${SOURCE_QUOTE}",
                  "span": "늘린다",
                  "categories": [{"code": "A", "markedness": 1}],
                  "baseline": {"source": "outlet", "text": "증액한다"},
                  "polarity": "none"
                },
                {
                  "article_index": 0,
                  "sentence": "${SOURCE_QUOTE}",
                  "span": "원문에 없는 표현",
                  "categories": [{"code": "A", "markedness": 1}],
                  "baseline": {"source": "outlet", "text": "증액한다"},
                  "polarity": "none"
                }
              ],
              "information_differences": [
                {"article_index": 0, "code": "I1", "value": "예산 증액"},
                {"article_index": 0, "code": "I6", "value": "정의되지 않은 코드"}
              ]
            }
        """.trimIndent()))

        val result = AnalysisApi.parseAnalysis(root, "https://requested.example/article")

        assertEquals(2, result.claims.size)
        assertEquals("0.2.1", result.rubric?.version)
        assertEquals(3, result.rubric?.droppedCount)
        assertTrue(result.rubric!!.questions.isEmpty())
        assertEquals(listOf("늘린다"), result.rubric!!.expressions.map { it.span })
        assertEquals(listOf("I1"), result.rubric!!.informations.map { it.code })
        assertFalse(ComparisonModel(result).questionMode)
    }

    @Test
    fun parseAnalysis_partialRubricKeepsUnjudgedI1WithoutInferringAbsenceOrScore() {
        val root = responseWithMatches()
        val related = root.getJSONArray("related_articles")
        for (i in 0 until related.length()) related.getJSONObject(i).put("article_index", i + 1)
        root.getJSONObject("comparison").getJSONArray("issues").getJSONObject(0).put("claim_id", "C1")
        val unjudged = JSONObject()
            .put("code", "I1")
            .put("claim_id", "C1")
            .put("article_index", 2)
            .put("sentence", JSONObject.NULL)
            .put("value", "이 기사의 루브릭 근거를 검증하지 못했어요. 주장 포함·누락과 표현 차이는 미판정입니다.")
        root.put("rubric", JSONObject()
            .put("version", "0.2.1")
            .put("judgment_status", "provisional")
            .put("questions", JSONArray())
            .put("information_differences", JSONArray().put(unjudged))
            .put("expression_differences", JSONArray()))
        root.put("analysis_status", JSONObject()
            .put("execution_status", "partial")
            .put("comparison_status", "available"))

        val result = AnalysisApi.parseAnalysis(root, "https://requested.example/article")
        val model = ComparisonModel(result)
        val information = result.rubric!!.informations.single()

        assertEquals("partial", result.analysisStatus?.executionStatus)
        assertEquals("available", result.analysisStatus?.comparisonStatus)
        assertEquals(0, result.rubric!!.droppedCount)
        assertEquals("I1", information.code)
        assertEquals("C1", information.claimId)
        assertEquals(2, information.articleIndex)
        assertNull(information.sentence)
        assertTrue(information.value.contains("미판정"))
        assertFalse(information.value.contains("찾지 못했어요"))
        assertEquals(information, model.issues[0].rubric.informations.single().i)
        assertTrue(model.issues.all { it.notFound.isEmpty() })
        assertFalse(model.noMatches)
        assertTrue(result.rubric!!.expressions.isEmpty())
        assertTrue(model.issues[0].rubric.expressions.isEmpty())
    }

    @Test
    fun parseAnalysis_preservesStructuredFailureStagesForHonestReaderStatus() {
        val root = responseWithMatches().put("analysis_status", JSONObject()
            .put("execution_status", "partial")
            .put("comparison_status", "available")
            .put("errors", JSONArray()
                .put(JSONObject().put("stage", "search").put("code", "SEARCH_FAILED"))
                .put(JSONObject().put("stage", "search"))
                .put(JSONObject().put("stage", " "))))

        val status = AnalysisApi.parseAnalysis(root, "https://requested.example/article").analysisStatus!!

        assertEquals(setOf("search"), status.errorStages)
    }

    @Test
    fun parseAnalysis_expressionFailuresKeepOnlyValidClaimIds() {
        val root = responseWithMatches().put("rubric", JSONObject()
            .put("version", "0.2.1")
            .put("expression_unverified_claim_ids", JSONArray()
                .put("C1").put("C1").put(7).put("")))

        val rubric = AnalysisApi.parseAnalysis(root, "https://requested.example/article").rubric!!

        assertEquals(setOf("C1"), rubric.expressionUnverifiedClaimIds)
        assertEquals(2, rubric.droppedCount)
    }

    companion object {
        internal const val SOURCE_QUOTE = "정부는 지원 예산을 늘린다고 밝혔다."

        internal fun responseWithMatches(): JSONObject = JSONObject("""
            {
              "source_analysis": {
                "title": "입력 기사", "url": "https://source.example/article",
                "core_event": "지원 예산 발표",
                "claims": [
                  {"claim": "예산 확대", "evidence": {"quote": "${SOURCE_QUOTE}"}},
                  {"claim": "지급 시기", "evidence": {"quote": "지급은 다음 달 시작된다."}}
                ]
              },
              "related_articles": [
                {"title": "첫 번째 기사", "url": "https://related.example/first"},
                {"title": "두 번째 기사", "url": "https://related.example/second"},
                {"title": "쟁점에 연결되지 않은 기사", "url": "https://related.example/third"}
              ],
              "comparison": {"issues": [
                {"issue": "예산 확대", "members": [
                  {"article_index": 0, "title": "입력 기사", "url": "https://source.example/article", "source_claim_index": 0, "evidence": {"quote": "${SOURCE_QUOTE}"}},
                  {"article_index": 1, "title": "첫 번째 기사", "url": "https://related.example/first", "source_claim_index": 0, "relation_to_source": "same", "evidence": {"quote": "예산이 늘어날 예정이다."}},
                  {"article_index": 2, "title": "두 번째 기사", "url": "https://related.example/second", "source_claim_index": 0, "relation_to_source": "opposes", "evidence": {"quote": "예산은 아직 확정되지 않았다."}}
                ]},
                {"issue": "추가 관점", "members": [
                  {"article_index": 2, "title": "두 번째 기사", "url": "https://related.example/second", "relation_to_source": "other", "evidence": {"quote": "시민단체는 우선순위를 다시 논의해야 한다고 했다."}}
                ]}
              ]}
            }
        """.trimIndent())

        internal fun responseWithoutMatches(): JSONObject = JSONObject("""
            {
              "source_analysis": {
                "title": "대응 문장 없는 기사",
                "claims": [{"claim": "예산 확대", "evidence": {"quote": "${SOURCE_QUOTE}"}}]
              },
              "related_articles": [{"title": "비교 기사", "url": "https://related.example/first"}],
              "comparison": {"issues": []}
            }
        """.trimIndent())
    }
}
