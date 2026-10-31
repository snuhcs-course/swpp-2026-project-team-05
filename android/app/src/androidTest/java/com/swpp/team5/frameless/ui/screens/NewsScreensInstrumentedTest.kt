package com.swpp.team5.frameless.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsUser
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.viewmodel.ArticleReaderState
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class NewsScreensInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val publisher = PublisherItem("yna", "연합뉴스", null)

    @Test
    fun feedShowsEditorialRowAndRoutesSelectedArticle() {
        var selectedId: String? = null
        val state = NewsFeedState(
            publishers = listOf(publisher),
            items = listOf(ArticleSummary(
                "article-1", publisher, "정부, 정책 전격 추진", NewsCategory.POLITICS,
                "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY
            ))
        )
        val session = AppSession(NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry")
        compose.setContent {
            val listState = rememberLazyListState()
            NewsFeedScreen(
                state, session, listState,
                onCategory = {}, onPublishers = {}, onRefresh = {}, onRetry = {}, onLoadMore = {},
                onArticle = { selectedId = it }, onLogout = {}
            )
        }

        compose.onNodeWithText("정부, 정책 전격 추진").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("article-1", selectedId) }
        compose.onNodeWithContentDescription("계정 메뉴").performClick()
        compose.onNodeWithText("reader").assertIsDisplayed()
    }

    @Test
    fun readerKeepsArticleReadableAndAnalysisToggleActionableWithoutFindings() {
        var analysisEnabled = true
        val article = ArticleDetail(
            "article-1", publisher, "정부, 정책 전격 추진", "기사 전문입니다.",
            NewsCategory.POLITICS, "2026-10-31T12:00:00+09:00",
            "https://example.com/article-1", StoredAnalysisState.READY
        )
        val state = ArticleReaderState(
            articleId = article.id,
            article = article,
            analysis = ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", emptyList()),
            analysisEnabled = true
        )
        compose.setContent {
            NewsReaderScreen(
                state = remember { state },
                onBack = {}, onRetry = {}, onAnalysisEnabled = { analysisEnabled = it }
            )
        }

        compose.onNodeWithText("기사 전문입니다.").assertIsDisplayed()
        compose.onNodeWithText("분석은 완료되었지만 현재 표시할 표현 특징을 확인하지 못했어요.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("표현 분석 표시").performClick()
        compose.runOnIdle { assertFalse(analysisEnabled) }
    }
}
