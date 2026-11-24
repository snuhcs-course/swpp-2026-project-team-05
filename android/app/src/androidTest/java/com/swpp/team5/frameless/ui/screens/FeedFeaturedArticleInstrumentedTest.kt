package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import com.swpp.team5.frameless.ui.viewmodel.SearchFilter
import com.swpp.team5.frameless.ui.viewmodel.SearchSectionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedFeaturedArticleInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val yna = PublisherItem("yna", "연합뉴스", null)
    private val hani = PublisherItem("hani", "한겨레", null)
    private val leadTitle = "국회, ‘예산 부수법안’ 처리 일정 합의…본회의 3일 [속보]"
    private val leadExcerpt = "여야 원내대표가 오전 회동에서 일정에 합의했다."
    private val lead = ArticleSummary(
        "lead-1", yna, leadTitle, NewsCategory.POLITICS,
        "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY, searchExcerpt = leadExcerpt
    )
    private val second = ArticleSummary(
        "row-2", hani, "두 번째 기사 제목", NewsCategory.SOCIETY,
        "2026-10-31T11:00:00+09:00", StoredAnalysisState.READY
    )
    private val third = ArticleSummary(
        "row-3", yna, "세 번째 기사 제목", NewsCategory.ECONOMY,
        "2026-10-31T10:00:00+09:00", StoredAnalysisState.READY
    )

    private var selectedId: String? = null

    private fun render(state: () -> NewsFeedState) {
        compose.setContent {
            NewsFeedScreen(
                state(), null, rememberLazyListState(),
                onCategory = {}, onSearchFilter = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = { selectedId = it }, onLogin = {}, onLogout = {}
            )
        }
    }

    @Test
    fun featuredCardShowsTheFirstArticlesExactStoredDataAndOpensIt() {
        render { NewsFeedState(publishers = listOf(yna, hani), items = listOf(lead, second, third)) }

        val card = compose.onNodeWithTag(FEATURED_ARTICLE_TAG)
        card.assertIsDisplayed()
        compose.onNode(hasTestTag(FEATURED_ARTICLE_TAG) and hasText(leadTitle)).assertIsDisplayed()
        compose.onNode(hasTestTag(FEATURED_ARTICLE_TAG) and hasText(leadExcerpt)).assertIsDisplayed()
        compose.onNode(hasTestTag(FEATURED_ARTICLE_TAG) and hasText("정치")).assertIsDisplayed()
        compose.onNode(hasTestTag(FEATURED_ARTICLE_TAG) and hasText("연합뉴스  ·  ", substring = true))
            .assertIsDisplayed()
        compose.onAllNodesWithText("비교 가능", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("오늘의 비교 이슈", substring = true).assertCountEquals(0)

        card.performClick()
        compose.runOnIdle { assertEquals("lead-1", selectedId) }
    }

    @Test
    fun firstArticleIsNotRepeatedInTheListBelow() {
        render { NewsFeedState(publishers = listOf(yna, hani), items = listOf(lead, second, third)) }

        compose.onAllNodesWithText(leadTitle).assertCountEquals(1)
        // The reading guide sits above the cover, so the list may start below the first screen.
        compose.onNodeWithTag("feed-list").performScrollToIndex(1)
        compose.onNodeWithText("최신 기사").assertIsDisplayed()
        compose.onNode(hasTestTag(FEATURED_ARTICLE_TAG) and hasText("두 번째 기사 제목")).assertDoesNotExist()
        compose.onNodeWithTag("feed-list").performScrollToIndex(2)
        compose.onNodeWithText("두 번째 기사 제목").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("row-2", selectedId) }
        compose.onNodeWithTag("feed-list").performScrollToIndex(3)
        compose.onNodeWithText("세 번째 기사 제목").assertIsDisplayed()
    }

    @Test
    fun singleArticleShowsOnlyTheFeaturedCardWithoutAnEmptyLatestSection() {
        render { NewsFeedState(publishers = listOf(yna), items = listOf(lead)) }

        compose.onNodeWithTag(FEATURED_ARTICLE_TAG).assertIsDisplayed()
        compose.onAllNodesWithText(leadTitle).assertCountEquals(1)
        compose.onAllNodesWithText("최신 기사").assertCountEquals(0)
        compose.onAllNodesWithText("새로고침").assertCountEquals(0)
    }

    @Test
    fun offlineFeedHasNoFeaturedCardAndListsEveryArticle() {
        render {
            NewsFeedState(publishers = listOf(yna, hani), items = listOf(lead, second), isOffline = true)
        }

        compose.onAllNodesWithTag(FEATURED_ARTICLE_TAG).assertCountEquals(0)
        compose.onNodeWithText(leadTitle).assertIsDisplayed()
        compose.onNodeWithText("두 번째 기사 제목").assertIsDisplayed()
    }

    @Test
    fun searchModeHasNoFeaturedCard() {
        val state = mutableStateOf(NewsFeedState(
            publishers = listOf(yna, hani),
            items = listOf(lead, second),
            isSearchOpen = true,
            searchInput = "국회"
        ))
        render { state.value }
        compose.onAllNodesWithTag(FEATURED_ARTICLE_TAG).assertCountEquals(0)

        state.value = state.value.copy(
            activeSearchQuery = "국회",
            searchSections = mapOf(
                SearchFilter.ALL to SearchSectionState(SearchFilter.ALL, listOf(lead, second), totalCount = 2)
            )
        )
        compose.onNodeWithText(leadTitle).assertIsDisplayed()
        compose.onAllNodesWithTag(FEATURED_ARTICLE_TAG).assertCountEquals(0)
    }

    @Test
    fun coverAnnouncesAnOpenActionAndHidesItsArtwork() {
        render { NewsFeedState(publishers = listOf(yna, hani), items = listOf(lead, second)) }

        compose.onNodeWithTag(FEATURED_ARTICLE_TAG)
            .assert(SemanticsMatcher("open label is 기사 열기") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == FEATURED_OPEN_LABEL
            })
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun rowsShowCategoryKickerTitleAndBylineAsOneTapTarget() {
        render { NewsFeedState(publishers = listOf(yna, hani), items = listOf(lead, second, third)) }

        compose.onNodeWithTag("feed-list").performScrollToIndex(2)
        compose.onNode(hasText("두 번째 기사 제목") and hasText("사회") and hasText("한겨레  ·  ", substring = true))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("feed-list").performScrollToIndex(3)
        compose.onNode(hasText("세 번째 기사 제목") and hasText("경제") and hasText("연합뉴스  ·  ", substring = true))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals("row-3", selectedId) }
    }
}
