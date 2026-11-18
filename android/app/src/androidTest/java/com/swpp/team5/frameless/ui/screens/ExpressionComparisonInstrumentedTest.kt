package com.swpp.team5.frameless.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.data.TextRange
import com.swpp.team5.frameless.ui.viewmodel.ArticleReaderState
import org.junit.Rule
import org.junit.Test

/** Oracles: specs/testing/paired-expression-comparison.spec.md (Draft). */
class ExpressionComparisonInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val source = PublisherItem("pressian", "프레시안", null)
    private val body = "중심에 섰던"
    private val overview = "프레시안은 ‘중심에 섰던’ 인물로 쓴 반면, 다른 보도는 관여 정도를 다르게 적었습니다."

    private fun comparison(code: String, name: String, sentence: String, marker: String): ComparisonArticleUi {
        val start = sentence.indexOf(marker).coerceAtLeast(0)
        return ComparisonArticleUi(
            articleId = "article-$code",
            publisher = PublisherItem(code, name, null),
            title = "$name 비교 기사",
            publishedAt = "2026-11-15T13:49:00+09:00",
            originalUrl = "https://example.com/$code",
            body = sentence,
            evidence = listOf(ComparisonEvidenceUi(
                scope = "BODY",
                sentenceRange = TextRange(0, sentence.length),
                sentenceText = sentence,
                markerRange = TextRange(start, start + marker.length),
                markerText = marker,
                owner = "REPORTER",
                speakerLabel = null,
                summary = "같은 내용을 다르게 적었어요."
            ))
        )
    }

    private fun openSheet(comparisons: List<ComparisonArticleUi>) {
        val finding = FindingUi(
            id = "finding-1",
            categories = listOf("C"),
            scope = "BODY",
            sentenceRange = TextRange(0, body.length),
            sentenceText = body,
            markerRange = TextRange(0, body.length),
            markerText = body,
            explanation = "관여 정도를 강하게 표현해요.",
            comparisonOverview = overview,
            comparisons = comparisons
        )
        val article = ArticleDetail(
            "article-source", source, "진형구 전 고검장 별세", body,
            NewsCategory.POLITICS, "2026-11-15T14:58:00+09:00",
            "https://example.com/source", StoredAnalysisState.READY
        )
        compose.setContent {
            NewsReaderScreen(
                state = ArticleReaderState(
                    articleId = article.id,
                    article = article,
                    analysis = ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", listOf(finding)),
                    analysisEnabled = true
                ),
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }
        compose.onNodeWithText(body).performTouchInput { click(Offset(24f, centerY)) }
        compose.onNodeWithText("곳과 비교", substring = true).performClick()
    }

    @Test
    fun cardShowsBothSidesBelowTheUnchangedHeadingAndOverviewAndFollowsThePager() {
        openSheet(listOf(
            comparison("news1", "뉴스1", "파업 유도 사건에 연루되기도 했다.", "연루되기도 했다"),
            comparison("hani", "한겨레", "파업 유도 사건에 관여했던 진 전 고검장은", "관여했던")
        ))

        compose.onNodeWithText("같은 내용을 다른 기사에서는").assertIsDisplayed()
        compose.onAllNodesWithText(overview).assertCountEquals(1)
        compose.onNodeWithText("표현 비교").assertIsDisplayed()
        compose.onNodeWithText("현재 기사").assertIsDisplayed()
        compose.onNodeWithText("프레시안").assertIsDisplayed()
        compose.onNodeWithText("“$body”").assertIsDisplayed()
        compose.onNodeWithText("같은 내용, 다른 표현").assertIsDisplayed()
        compose.onNodeWithText("다른 보도").assertIsDisplayed()
        compose.onNodeWithText("현재 비교 기사").assertIsDisplayed()
        compose.onNodeWithText("뉴스1").assertIsDisplayed()
        compose.onNodeWithText("“연루되기도 했다”").assertIsDisplayed()
        compose.onAllNodesWithText("“관여했던”").assertCountEquals(0)
        compose.onNodeWithText("1 / 2").assertIsDisplayed()

        compose.onNodeWithContentDescription("다음 비교 기사").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithText("“$body”").assertIsDisplayed()
        compose.onNodeWithText("한겨레").assertIsDisplayed()
        compose.onNodeWithText("“관여했던”").assertIsDisplayed()
        compose.onAllNodesWithText("“연루되기도 했다”").assertCountEquals(0)
        compose.onAllNodesWithText(overview).assertCountEquals(1)
        compose.onNodeWithText("같은 내용을 다른 기사에서는").assertIsDisplayed()
    }

    @Test
    fun cardIsHiddenOnAPageWithoutUsableEvidence() {
        val blank = comparison("kbs", "KBS", "표시할 표현이 없는 근거 문장.", "").let { page ->
            page.copy(evidence = page.evidence.map { it.copy(markerText = "   ") })
        }
        openSheet(listOf(blank, comparison("news1", "뉴스1", "파업 유도 사건에 연루되기도 했다.", "연루되기도 했다")))

        compose.onNodeWithText("같은 내용을 다른 기사에서는").assertIsDisplayed()
        compose.onAllNodesWithText(overview).assertCountEquals(1)
        compose.onAllNodesWithText("표현 비교").assertCountEquals(0)

        compose.onNodeWithContentDescription("다음 비교 기사").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithText("표현 비교").assertIsDisplayed()
        compose.onNodeWithText("“연루되기도 했다”").assertIsDisplayed()
    }
}
