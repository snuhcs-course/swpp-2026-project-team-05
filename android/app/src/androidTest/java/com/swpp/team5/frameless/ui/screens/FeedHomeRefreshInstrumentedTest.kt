package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsUser
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedHomeRefreshInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val publisher = PublisherItem("yna", "연합뉴스", null)
    private val title = "‘기준금리 0.25%p 인하’…한은, “물가 경로 점검” [종합]"
    private val session = AppSession(NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry")

    private var tutorialOpens = 0
    private var searchOpens = 0
    private var savedOpens = 0
    private var selectedId: String? = null

    private fun setFeed() {
        val state = NewsFeedState(
            publishers = listOf(publisher),
            items = listOf(ArticleSummary(
                "article-1", publisher, title, NewsCategory.ECONOMY,
                "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY
            ))
        )
        compose.setContent {
            NewsFeedScreen(
                state, session, rememberLazyListState(),
                onCategory = {}, onSearchFilter = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = { searchOpens += 1 }, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = { savedOpens += 1 }, onArticle = { selectedId = it }, onLogin = {}, onLogout = {},
                onTutorialOpen = { tutorialOpens += 1 }
            )
        }
    }

    @Test
    fun headerMenuOffersOnlyTutorialReplay() {
        setFeed()
        compose.onNodeWithContentDescription("기사 링크로 분석하기").assertDoesNotExist()
        compose.onNodeWithContentDescription("튜토리얼 다시 보기").assertDoesNotExist()

        compose.onNodeWithContentDescription("메뉴").assertIsDisplayed().performClick()
        compose.onNodeWithText("기사 링크로 분석하기").assertDoesNotExist()
        compose.onNodeWithContentDescription("튜토리얼 다시 보기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, tutorialOpens) }
    }

    @Test
    fun appBarKeepsSearchSavedAndAccountDirectlyReachable() {
        setFeed()
        compose.onNodeWithContentDescription("기사 검색").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("저장한 기사").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, searchOpens); assertEquals(1, savedOpens) }
        compose.onNodeWithContentDescription("계정 메뉴").assertIsDisplayed().performClick()
        compose.onNodeWithText("reader").assertIsDisplayed()
    }

    @Test
    fun redesignedRowShowsExactTitleCategoryMetadataAndNoInventedBadges() {
        setFeed()
        compose.onNodeWithText(title).assertIsDisplayed()
        // The cover keeps its category kicker and publisher, without a publication time.
        compose.onNode(hasTestTag(FEATURED_ARTICLE_TAG) and hasText("경제")).assertIsDisplayed()
        compose.onNode(hasTestTag(FEATURED_ARTICLE_TAG) and hasText("연합뉴스"))
            .assertIsDisplayed()
        compose.onAllNodesWithText("비교 가능", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("오늘의 비교 이슈", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("개 언론사", substring = true).assertCountEquals(0)

        compose.onNodeWithText(title).performClick()
        compose.runOnIdle { assertEquals("article-1", selectedId) }
    }
}
