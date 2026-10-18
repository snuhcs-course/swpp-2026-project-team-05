package com.swpp.team5.frameless.ui.components

import com.swpp.team5.frameless.data.AnalysisApi
import com.swpp.team5.frameless.data.AnalysisApiTest
import com.swpp.team5.frameless.data.Relation
import com.swpp.team5.frameless.ui.state.ComparisonModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// AI-assisted provenance tests: comparison evidence must not turn attributed speech into outlet stance.
class SourceEvidenceProvenanceTest {
    @Test
    fun attributedSpeakerAndMixedVoiceSurviveParsingAndReachTheCard() {
        val root = AnalysisApiTest.responseWithMatches()
        val member = root.getJSONObject("comparison").getJSONArray("issues")
            .getJSONObject(0).getJSONArray("members").getJSONObject(1)
        member.put("relation_owner", "attributed_speaker")
            .put("speaker", "관계자")
            .put("speech_kind", "direct")
            .put("has_multiple_voices", true)
            .put("relation_warning", "후속 검토가 필요해요.")

        val result = AnalysisApi.parseAnalysis(root, "https://requested.example/article")
        val parsed = result.issues.first().members[1]
        val clip = ComparisonModel(result).issues.first().same.single()

        assertEquals("attributed_speaker", parsed.relationOwnerRaw)
        assertEquals("관계자", parsed.speaker)
        assertEquals("direct", parsed.speechKindRaw)
        assertTrue(parsed.hasMultipleVoices)
        assertEquals("후속 검토가 필요해요.", parsed.relationWarning)
        assertEquals(parsed.relationOwnerRaw, clip.relationOwnerRaw)
        assertEquals(parsed.speaker, clip.speaker)
        assertEquals(parsed.speechKindRaw, clip.speechKindRaw)
        assertTrue(clip.hasMultipleVoices)
        assertEquals(parsed.relationWarning, clip.relationWarning)

        val labels = provenanceLines(clip)
        assertTrue(labels.any { it.contains("관계자 · 직접 인용") })
        assertTrue(labels.any { it.contains("매체의 입장을 뜻하지 않아요") })
        assertTrue(labels.any { it.contains("여러 발화 주체") })
        assertTrue(labels.any { it.contains("후속 검토가 필요해요") })
    }

    @Test
    fun reporterAndUncertainOwnershipAreLabeledDifferently() {
        val root = AnalysisApiTest.responseWithMatches()
        val members = root.getJSONObject("comparison").getJSONArray("issues")
            .getJSONObject(0).getJSONArray("members")
        members.getJSONObject(1).put("relation_owner", "reporter")
            .put("speech_kind", "none")
        members.getJSONObject(2).put("relation_owner", "uncertain")
            .put("speech_kind", "uncertain")
            .put("relation_to_source", "uncertain")

        val model = ComparisonModel(AnalysisApi.parseAnalysis(root, "https://requested.example/article"))
        val reporter = model.issues.first().same.single()
        val uncertain = model.issues.first().other.single()

        assertEquals(listOf("관계 판정 주체: 기자 서술"), provenanceLines(reporter))
        assertEquals(Relation.UNCERTAIN, Relation.from(uncertain.relationRaw))
        assertEquals("관계 판정 불확실", Relation.from(uncertain.relationRaw).label)
        assertTrue(provenanceLines(uncertain).any { it.contains("매체의 입장으로 해석하지 마세요") })
        assertFalse(model.issues.first().diff.any { it.quote == uncertain.quote })
    }

    @Test
    fun missingLegacyProvenanceAlsoStaysUnattributed() {
        val result = AnalysisApi.parseAnalysis(
            AnalysisApiTest.responseWithMatches(), "https://requested.example/article"
        )
        val clip = ComparisonModel(result).issues.first().same.single()

        assertTrue(provenanceLines(clip).any { it.contains("발화 주체 확인 필요") })
        assertTrue(provenanceLines(clip).any { it.contains("매체의 입장으로 해석하지 마세요") })
    }
}
