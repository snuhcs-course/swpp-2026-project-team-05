package com.swpp.team5.frameless.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpressionPairInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private fun comparison(id: String, publisher: String, body: String, marker: String): ComparisonArticleUi {
        val start = body.indexOf(marker)
        return ComparisonArticleUi(
            id, PublisherItem(id, publisher, null), "$publisher 기사 제목", "2026-11-19T09:00:00+09:00",
            "https://example.com/$id", body,
            listOf(ComparisonEvidenceUi(
                "BODY", TextRange(0, body.length), body, TextRange(start, start + marker.length), marker,
                "REPORTER", null, "표시하지 않는 요약"
            ))
        )
    }

    private val sourceSentence = "회사는 올해 사업을 빠르게 확장했다."
    private val sourceMarker = "빠르게 확장했다"
    private val first = comparison("cmp-1", "MBC", "회사는 사업 영역을 넓히고 있다.", "넓히고 있다")
    private val second = comparison("cmp-2", "한겨레", "회사가 사업 범위를 키우는 중이다.", "키우는 중이다")
    private val finding = FindingUi(
        "f1", listOf("D"), "BODY", TextRange(0, sourceSentence.length), sourceSentence,
        TextRange(sourceSentence.indexOf(sourceMarker), sourceSentence.indexOf(sourceMarker) + sourceMarker.length),
        sourceMarker, "설명", "세 기사가 확장 상태를 서로 다른 단어로 전해요.", listOf(first, second)
    )

    @Test
    fun bothPhrasesAppearTogetherWithOutletsAndThePagerSwapsOnlyTheComparedSide() {
        var opened: ComparisonArticleUi? = null
        var dismissed = false
        compose.setContent {
            FindingDetailSheet(
                finding = finding,
                onDismiss = { dismissed = true },
                onOpenArticle = { opened = it },
                sourcePublisher = "연합뉴스"
            )
        }

        compose.onNodeWithText("같은 내용을 다른 기사에서는").assertIsDisplayed()
        compose.onAllNodesWithText(finding.comparisonOverview).assertCountEquals(1)
        compose.onNode(hasText(PAIR_CURRENT_ROLE) and hasText("연합뉴스") and hasText("“$sourceMarker”"))
            .assertIsDisplayed()
        compose.onNode(hasText(PAIR_COMPARED_ROLE) and hasText("MBC") and hasText("“넓히고 있다”"))
            .assertIsDisplayed()
        compose.onAllNodesWithText("표시하지 않는 요약").assertCountEquals(0)

        compose.onNodeWithContentDescription("다음 비교 기사").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNode(hasText(PAIR_COMPARED_ROLE) and hasText("한겨레") and hasText("“키우는 중이다”"))
            .assertIsDisplayed()
        compose.onNode(hasText(PAIR_CURRENT_ROLE) and hasText("“$sourceMarker”")).assertIsDisplayed()

        compose.onNodeWithText("이 기사 읽기").performClick()
        compose.waitUntil { opened != null }
        assertEquals("cmp-2", opened?.articleId)
        assertTrue(dismissed)
    }

    @Test
    fun eachSideReadsAsOneUnitAndTheConnectorAddsNoExtraSpokenText() {
        compose.setContent {
            FindingDetailSheet(finding = finding, onDismiss = {}, sourcePublisher = "연합뉴스")
        }

        listOf(
            PAIR_CURRENT_ROLE to "“$sourceMarker”",
            PAIR_COMPARED_ROLE to "“넓히고 있다”"
        ).forEach { (role, phrase) ->
            compose.onNode(hasText(role) and hasText(phrase))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }
        compose.onNodeWithText(PAIR_HEADING).assertIsDisplayed()
        compose.onNodeWithText(PAIR_CONNECTOR).assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun comparisonSheetOmitsFullBodyAndKeepsOriginalArticleActions() {
        val bodyOnlyText = "전문에만 있는 별도의 문단입니다."
        val page = first.copy(body = first.body + "\n\n" + bodyOnlyText.repeat(30))
        var opened: ComparisonArticleUi? = null
        var dismissed = false
        compose.setContent {
            FindingDetailSheet(
                finding = finding.copy(comparisons = listOf(page)),
                onDismiss = { dismissed = true },
                onOpenArticle = { opened = it },
                sourcePublisher = "연합뉴스"
            )
        }
        compose.onAllNodesWithText(bodyOnlyText, substring = true).assertCountEquals(0)
        compose.onNode(hasText(PAIR_COMPARED_ROLE) and hasText("“넓히고 있다”"))
            .assertIsDisplayed()
        compose.onNodeWithText(page.title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("원문 보기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 기사 읽기").performScrollTo().performClick()
        compose.waitUntil { opened != null }
        assertEquals(page, opened)
        assertTrue(dismissed)
    }

    @Test
    fun longPhrasesStopAtTwoLines() {
        val longMarker = "회사가 여러 지역에서 동시에 사업 영역을 넓히며 새로운 시장에 공격적으로 진입하고 있다는 평가"
        val longBody = "업계에서는 $longMarker 가 나온다."
        val longFinding = finding.copy(
            sentenceText = longBody, markerText = longMarker,
            comparisons = listOf(comparison("cmp-long", "MBC", longBody, longMarker))
        )
        compose.setContent {
            FindingDetailSheet(finding = longFinding, onDismiss = {}, sourcePublisher = "연합뉴스")
        }

        val nodes = compose.onAllNodesWithText("“$longMarker”", useUnmergedTree = true)
        nodes.assertCountEquals(2)
        repeat(2) { index ->
            val results = mutableListOf<TextLayoutResult>()
            nodes[index].fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.single()
            assertTrue("phrase should be at most two lines", layout.lineCount <= 2)
            assertTrue("long phrase should be ellipsized", layout.hasVisualOverflow)
        }
    }
}
