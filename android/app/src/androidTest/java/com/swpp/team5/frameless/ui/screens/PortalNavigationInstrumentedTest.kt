package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherArticlePreview
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.data.StorySummary
import com.swpp.team5.frameless.ui.theme.FrameLESSTheme
import com.swpp.team5.frameless.ui.viewmodel.NewsSection
import com.swpp.team5.frameless.ui.viewmodel.PortalDiscoveryState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PortalNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val publisher = PublisherItem("yna", "연합뉴스", null, recentArticles = listOf(
        PublisherArticlePreview("a1", "정부, 새 정책 발표", "2026-11-24T09:00:00+09:00"),
        PublisherArticlePreview("a2", "국회, 예산안 심의", "2026-11-24T08:00:00+09:00")
    ))

    @Test
    fun rootTabsExposeSelectionAndKeepTheirActions() {
        compose.setContent {
            var selected by remember { mutableStateOf(PortalTab.HOME) }
            FrameLESSTheme { PortalBottomBar(selected) { selected = it } }
        }
        PortalTab.entries.forEach { tab ->
            compose.onNodeWithContentDescription("${tab.label} 탭").performClick().assertIsSelected()
        }
    }

    @Test
    fun newsTabsNameTheirScopeAndExposeSelection() {
        compose.setContent {
            var section by remember { mutableStateOf(NewsSection.NEWS) }
            FrameLESSTheme { Directory(section, onSection = { section = it }) }
        }
        compose.onAllNodesWithText("뉴스").assertCountEquals(1)
        compose.onNodeWithContentDescription("전체 뉴스 탭").assertIsSelected()
        compose.onNodeWithContentDescription("언론사 탭").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("정치 탭").performClick().assertIsSelected()
    }

    @Test
    fun publisherIdentityAndHeadlinesKeepTheirDestinations() {
        var selectedPublisher: String? = null
        var selectedArticle: String? = null
        compose.setContent {
            FrameLESSTheme {
                Directory(NewsSection.PUBLISHERS,
                    onPublisher = { selectedPublisher = it.code },
                    onArticle = { selectedArticle = it })
            }
        }
        compose.onNodeWithTag("publisher-logo-yna", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("연합뉴스 기사 보기").performClick()
        compose.onNodeWithText("국회, 예산안 심의").performClick()
        compose.runOnIdle {
            assertEquals("yna", selectedPublisher)
            assertEquals("a2", selectedArticle)
        }
    }

    @Test
    fun managementTogglesSubscriptionsWithoutLeavingTheList() {
        compose.setContent {
            var followed by remember { mutableStateOf(false) }
            FrameLESSTheme {
                PortalFollowedPublishersScreen(listOf(publisher.copy(isFollowed = followed)),
                    isSaving = false, error = null, onBack = {},
                    onToggle = { _, next -> followed = next })
            }
        }
        compose.onNodeWithTag("subscription-toggle-yna").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("subscription-management").assertIsDisplayed()
        compose.onAllNodesWithText("변경사항 저장").assertCountEquals(0)
    }

    @Test
    fun managementPreservesConfirmedStateOnFailure() {
        var mutations = 0
        val saving = mutableStateOf(true)
        compose.setContent {
            FrameLESSTheme {
                PortalFollowedPublishersScreen(listOf(publisher),
                    isSaving = saving.value, error = if (saving.value) null else "저장하지 못했어요",
                    onBack = {}, onToggle = { _, _ -> mutations++ }, pendingPublisherCode = "yna")
            }
        }
        compose.onNodeWithTag("subscription-toggle-yna").assertIsNotEnabled().assertIsOff()
        compose.runOnIdle { saving.value = false }
        compose.onNodeWithTag("subscription-save-error").assertIsDisplayed()
        compose.onNodeWithTag("subscription-toggle-yna").assertIsOff()
        compose.runOnIdle { assertEquals(0, mutations) }
    }

    @Test
    fun homeShowsPublicThenSubscribedNews() {
        var selectedArticle: String? = null
        val public = story("a1", "전체 언론사의 주요 뉴스")
        val subscribed = story("a2", "구독한 언론사의 새 기사")
        compose.setContent {
            FrameLESSTheme {
                PortalHomeScreen(stories = listOf(public), followedPublisherCount = 1,
                    isLoading = false, error = null, listState = rememberLazyListState(),
                    onSearch = {}, onArticle = { selectedArticle = it }, onProfile = {},
                    onSubscriptions = {}, onRetry = {}, subscribedStories = listOf(subscribed), isSignedIn = true)
            }
        }
        compose.onNodeWithText("주요 뉴스").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구독한 언론사 뉴스").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구독한 언론사의 새 기사").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("a2", selectedArticle) }
    }

    @Composable
    private fun Directory(
        section: NewsSection,
        onSection: (NewsSection) -> Unit = {},
        onPublisher: (PublisherItem) -> Unit = {},
        onArticle: (String) -> Unit = {}
    ) = PortalNewsScreen(
        state = PortalDiscoveryState(newsSection = section), publishers = listOf(publisher), session = null,
        listState = rememberLazyListState(), onSection = onSection, onSearch = {}, onTutorial = {},
        onRefresh = {}, onRetry = {}, onLoadMore = {}, onArticle = onArticle, onPublisher = onPublisher,
        onPublisherBack = {}, onPublisherRetry = {}, onPublisherLoadMore = {}, onSubscription = { _, _ -> }
    )

    private fun story(id: String, title: String): StorySummary {
        val article = ArticleSummary(id, publisher, title, NewsCategory.POLITICS,
            "2026-11-24T09:00:00+09:00", StoredAnalysisState.READY)
        // One topic can occur in both independent sections, with a different representative.
        return StorySummary("cluster:same-topic", article, 1, listOf(publisher), article.publishedAt)
    }
}
