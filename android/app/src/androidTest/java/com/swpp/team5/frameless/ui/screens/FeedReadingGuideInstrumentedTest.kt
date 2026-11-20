package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import com.swpp.team5.frameless.ui.viewmodel.SearchFilter
import com.swpp.team5.frameless.ui.viewmodel.SearchSectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedReadingGuideInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val yna = PublisherItem("yna", "연합뉴스", null)
    private val lead = ArticleSummary(
        "lead-1", yna, "첫 번째 기사 제목", NewsCategory.SOCIETY,
        "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY
    )
    private val second = ArticleSummary(
        "row-2", yna, "두 번째 기사 제목", NewsCategory.ECONOMY,
        "2026-10-31T11:00:00+09:00", StoredAnalysisState.READY
    )

    private var tutorialOpens = 0
    private var linkOpens = 0
    private var articleOpens = 0

    private fun render(state: () -> NewsFeedState) {
        compose.setContent {
            NewsFeedScreen(
                state(), null, rememberLazyListState(),
                onCategory = {}, onSearchFilter = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = { articleOpens += 1 }, onLogin = {}, onLogout = {},
                onLinkOpen = { linkOpens += 1 }, onTutorialOpen = { tutorialOpens += 1 }
            )
        }
    }

    @Test
    fun guideAppearsAboveTheCoverInTheNormalFeedAndReplaysTheTutorialOnce() {
        render { NewsFeedState(publishers = listOf(yna), items = listOf(lead, second)) }

        compose.onNode(
            hasContentDescription(READING_GUIDE_DESCRIPTION) and
                hasText("FRAMELESS") and
                hasText("같은 사건, 다른 표현") and
                hasText("보라색 표현을 누르면 다른 보도의 표현을 함께 볼 수 있어요.")
        ).assertIsDisplayed()
        val guideBottom = compose.onNodeWithContentDescription(READING_GUIDE_DESCRIPTION)
            .fetchSemanticsNode().boundsInRoot.bottom
        val coverTop = compose.onNodeWithTag(FEATURED_ARTICLE_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue("guide should sit above the featured cover", guideBottom <= coverTop)

        compose.onNodeWithContentDescription(READING_GUIDE_DESCRIPTION).performClick()
        compose.runOnIdle {
            assertEquals(1, tutorialOpens)
            assertEquals(0, linkOpens)
            assertEquals(0, articleOpens)
        }
    }

    @Test
    fun guideIsAbsentWhileSearching() {
        val state = mutableStateOf(NewsFeedState(
            publishers = listOf(yna), items = listOf(lead, second),
            isSearchOpen = true, searchInput = "기사"
        ))
        render { state.value }
        compose.onAllNodesWithContentDescription(READING_GUIDE_DESCRIPTION).assertCountEquals(0)
        compose.onAllNodesWithText("같은 사건, 다른 표현").assertCountEquals(0)

        state.value = state.value.copy(
            activeSearchQuery = "기사",
            searchSections = mapOf(
                SearchFilter.ALL to SearchSectionState(SearchFilter.ALL, listOf(lead, second), totalCount = 2)
            )
        )
        compose.onAllNodesWithContentDescription(READING_GUIDE_DESCRIPTION).assertCountEquals(0)
        compose.runOnIdle { assertEquals(0, tutorialOpens) }
    }

    @Test
    fun guideIsAbsentOfflineAndWhenTheFeedIsEmpty() {
        val state = mutableStateOf(
            NewsFeedState(publishers = listOf(yna), items = listOf(lead, second), isOffline = true)
        )
        render { state.value }
        compose.onAllNodesWithContentDescription(READING_GUIDE_DESCRIPTION).assertCountEquals(0)

        state.value = NewsFeedState(publishers = listOf(yna), items = emptyList())
        compose.onAllNodesWithContentDescription(READING_GUIDE_DESCRIPTION).assertCountEquals(0)
    }
}
