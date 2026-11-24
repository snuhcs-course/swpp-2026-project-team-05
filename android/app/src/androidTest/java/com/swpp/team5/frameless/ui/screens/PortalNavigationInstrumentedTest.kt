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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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

    @Test
    fun homePullRefreshCallsItsRefreshAction() {
        var refreshes = 0
        compose.setContent {
            FrameLESSTheme {
                PortalHomeScreen(listOf(story("a", "주요 소식")), emptyList(), false, null, rememberLazyListState(),
                    onSearch = {}, onArticle = {}, onProfile = {}, onSubscriptions = {}, onRetry = { refreshes++ })
            }
        }
        compose.onNodeWithTag("home-feed").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun trendsDistinguishEmptyFailureAndSparseCorpus() {
        val current = mutableStateOf(com.swpp.team5.frameless.ui.viewmodel.TrendsState(
            page = com.swpp.team5.frameless.data.TrendPage("2026-11-25T09:00:00Z", false, emptyList())))
        compose.setContent {
            FrameLESSTheme {
                PortalHomeScreen(emptyList(), emptyList(), false, null, rememberLazyListState(),
                    onSearch = {}, onArticle = {}, onProfile = {}, onSubscriptions = {}, onRetry = {},
                    trends = current.value)
            }
        }
        compose.onNodeWithText("표시할 뉴스가 없어요").assertIsDisplayed()
        for (count in 1..5) {
            compose.runOnIdle {
                current.value = current.value.copy(page = current.value.page!!.copy(items = (1..count).map {
                    com.swpp.team5.frameless.data.TrendItem("LATEST", null, null, null,
                        story("s$it", "최신 기사 $it"))
                }))
            }
            compose.onNodeWithContentDescription("실시간 트렌드 최신, 최신 기사 1").assertIsDisplayed()
            if (count > 2) compose.onNodeWithContentDescription("실시간 트렌드 펼치기").assertIsDisplayed()
            else compose.onNodeWithContentDescription("실시간 트렌드 펼치기").assertDoesNotExist()
        }
        compose.runOnIdle {
            current.value = com.swpp.team5.frameless.ui.viewmodel.TrendsState(error = "연결을 확인해 주세요")
        }
        compose.onNodeWithText("연결을 확인해 주세요").assertIsDisplayed()
        compose.onNodeWithContentDescription("트렌드 다시 시도").assertIsDisplayed()
    }

    @Test
    fun homeTrendExpansionKeepsHeadlineTargets() {
        var opened: String? = null
        val entries = (1..5).map { index ->
            com.swpp.team5.frameless.data.TrendItem("LATEST", null, null, null,
                story("t$index", "트렌드 기사 $index").copy(storyKey = "article:t$index"))
        }
        compose.setContent {
            FrameLESSTheme {
                PortalHomeScreen(emptyList(), emptyList(), false, null, rememberLazyListState(),
                    onSearch = {}, onArticle = { opened = it }, onProfile = {}, onSubscriptions = {}, onRetry = {},
                    trends = com.swpp.team5.frameless.ui.viewmodel.TrendsState(
                        page = com.swpp.team5.frameless.data.TrendPage("2026-11-25T09:00:00Z", false, entries)))
            }
        }
        compose.onAllNodesWithText("트렌드 기사 5").assertCountEquals(0)
        compose.onNodeWithContentDescription("실시간 트렌드 펼치기").performClick()
        compose.onNodeWithContentDescription("실시간 트렌드 최신, 트렌드 기사 5").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("t5", opened) }
        compose.onNodeWithContentDescription("실시간 트렌드 접기").performClick()
        compose.onAllNodesWithText("트렌드 기사 5").assertCountEquals(0)
    }
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
                PortalHomeScreen(stories = listOf(public), subscribedPublishers = listOf(publisher),
                    isLoading = false, error = null, listState = rememberLazyListState(),
                    onSearch = {}, onArticle = { selectedArticle = it }, onProfile = {},
                    onSubscriptions = {}, onRetry = {}, subscribedArticles = listOf(subscribed.representativeArticle), isSignedIn = true)
            }
        }
        compose.onNodeWithText("주요 뉴스").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구독한 언론사").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("구독한 언론사의 새 기사").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("a2", selectedArticle) }
    }

    @Test
    fun homePublisherCirclesChangeTheArticlesBelow() {
        var selectedArticle: String? = null
        val mbc = PublisherItem("mbc", "MBC", null, isFollowed = true)
        compose.setContent {
            var selectedCode by remember { mutableStateOf("yna") }
            FrameLESSTheme {
                PortalHomeScreen(stories = listOf(story("public", "주요 소식")),
                    subscribedPublishers = listOf(publisher.copy(isFollowed = true), mbc),
                    selectedPublisherCode = selectedCode, onSelectPublisher = { selectedCode = it },
                    isLoading = false, error = null, listState = rememberLazyListState(),
                    onSearch = {}, onArticle = { selectedArticle = it }, onProfile = {},
                    onSubscriptions = {}, onRetry = {}, isSignedIn = true,
                    subscribedArticles = listOf(story(selectedCode,
                        if (selectedCode == "yna") "연합뉴스의 새 기사" else "MBC의 새 기사").representativeArticle))
            }
        }
        compose.onNodeWithText("연합뉴스의 새 기사").performScrollTo()
        compose.onNodeWithContentDescription("연합뉴스 구독 뉴스").assertIsSelected()
        compose.onNodeWithContentDescription("MBC 구독 뉴스").performClick().assertIsSelected()
        compose.onNodeWithText("MBC의 새 기사").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("mbc", selectedArticle) }
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
