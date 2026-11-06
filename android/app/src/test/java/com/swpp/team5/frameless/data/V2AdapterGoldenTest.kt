package com.swpp.team5.frameless.data

import com.swpp.team5.frameless.ui.state.ComparisonModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// AI-assisted cross-language test: this JSON is emitted by tests/test_v2_adapter.py's synthetic pipeline.
class V2AdapterGoldenTest {
    @Test
    fun pythonAdapterOutputIsConsumableByTheAndroidComparisonScreen() {
        val json = javaClass.classLoader!!.getResourceAsStream("v2_android_response.json")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Missing v2 Android response fixture")

        val analysis = AnalysisApi.parseAnalysis(
            JSONObject(json), "https://news.example.com/source"
        )
        val model = ComparisonModel(analysis)

        assertEquals("입력 기사", analysis.sourceTitle)
        assertEquals("https://news.example.com/source", analysis.sourceUrl)
        assertEquals(listOf(0, 1, 2), analysis.articles.map { it.articleIndex })
        assertEquals("https://news.example.com/a", analysis.articles[1].url)
        assertEquals("https://news.example.com/b", analysis.articles[2].url)
        assertEquals(listOf("C1", "C2"), analysis.issues.map { it.claimId })
        assertEquals("B 기사는 다른 수치를 보도했다", analysis.issues[0].members[2].quote)
        assertEquals(Relation.SAME_ISSUE, analysis.issues[0].members[2].relation)
        assertEquals("success", analysis.analysisStatus?.executionStatus)
        assertEquals("available", analysis.analysisStatus?.comparisonStatus)
        assertTrue(analysis.relatedWithoutMatches.isEmpty())
        assertFalse(model.noMatches)

        assertFalse(model.questionMode)
        assertEquals(2, model.groupCount)
        assertEquals(1, model.issues[0].sameOutlets)
        assertEquals(1, model.issues[0].otherOutlets)
        assertEquals(1, model.issues[1].diffOutlets)
        assertTrue(model.issues[1].notFound.isEmpty()) // Selected comparisons are not omission judgments.

        val rubric = analysis.rubric!!
        assertEquals("0.2.1", rubric.version)
        assertEquals("provisional", rubric.judgmentStatus)
        assertEquals(0, rubric.droppedCount)
        assertTrue(rubric.questions.isEmpty())
        assertEquals(listOf("I1", "I1", "I2", "I2", "I3", "I5"), rubric.informations.map { it.code })
        assertNull(rubric.informations.first { it.code == "I3" }.target)
        assertNull(rubric.informations.first { it.code == "I5" }.target)
        assertEquals(listOf("D", "F"), rubric.expressions.map { it.categories.single().code })
        assertNull(rubric.expressions[0].target)
        assertEquals(2, rubric.expressions[0].maxMarkedness)
        assertFalse(rubric.expressions[0].held)
        assertNull(rubric.expressions[1].maxMarkedness)
        assertTrue(rubric.expressions[1].held)
        assertEquals("needs_review", rubric.expressions[1].status)
        assertEquals(6, model.issues[0].rubric.informations.size)
        assertEquals(2, model.issues[0].rubric.expressions.size)
        assertTrue(model.issues[1].rubric.isEmpty)
    }
}
