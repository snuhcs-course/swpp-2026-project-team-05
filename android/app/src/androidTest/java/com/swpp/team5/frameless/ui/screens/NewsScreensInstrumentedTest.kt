package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FeedScope
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsUser
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.data.TextRange
import com.swpp.team5.frameless.ui.viewmodel.ArticleReaderState
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import com.swpp.team5.frameless.ui.viewmodel.SearchSectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class NewsScreensInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

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
                onCategory = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = { selectedId = it }, onLogout = {}
            )
        }

        compose.onNodeWithText("정부, 정책 전격 추진").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("분석 완료").assertCountEquals(0)
        compose.runOnIdle { assertEquals("article-1", selectedId) }
        compose.onNodeWithContentDescription("계정 메뉴").performClick()
        compose.onNodeWithText("reader").assertIsDisplayed()
    }

    @Test
    fun feedScopeUsesSingleSelectionRadioOptions() {
        var selectedScope: FeedScope? = null
        val state = NewsFeedState(
            publishers = listOf(publisher.copy(isFollowed = true)),
            scope = FeedScope.ALL
        )
        val session = AppSession(NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry")
        compose.setContent {
            NewsFeedScreen(
                state, session, rememberLazyListState(),
                onCategory = {}, onScope = { selectedScope = it }, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = {}, onLogout = {}
            )
        }

        compose.onNodeWithText("최신  ⌄").performClick()
        compose.onNodeWithContentDescription("최신 피드").assertIsSelected()
        compose.onNodeWithContentDescription("팔로잉 피드").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(FeedScope.FOLLOWING, selectedScope) }
    }

    @Test
    fun zeroResultSearchPreviewsUrlAnalysisWithoutSubmitting() {
        val state = NewsFeedState(
            publishers = listOf(publisher),
            isSearchOpen = true,
            searchInput = "없는 기사",
            activeSearchQuery = "없는 기사",
            searchSections = NewsCategory.entries.associateWith {
                SearchSectionState(category = it, totalCount = 0)
            }
        )
        val session = AppSession(NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry")
        compose.setContent {
            NewsFeedScreen(
                state, session, rememberLazyListState(),
                onCategory = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = {}, onLogout = {}
            )
        }

        compose.onNodeWithText("기사 링크로 분석하기").performClick()
        compose.onNodeWithText("계속").performClick()
        compose.onNodeWithText("http:// 또는 https://로 시작하는 전체 기사 주소를 입력해 주세요.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("기사 URL 입력")
            .performTextInput("https://news.example.com/story")
        compose.onNodeWithText("계속").performClick()
        compose.onNodeWithText("기사 분석 기능을 준비하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("검색으로 돌아가기").performClick()
        compose.onNodeWithText("검색 결과가 없어요").assertIsDisplayed()
    }

    @Test
    fun searchKeepsCategoryTabsWithExactCountsAndShowsSelectedSection() {
        val politics = ArticleSummary(
            "politics-1", publisher, "정치 검색 결과", NewsCategory.POLITICS,
            "2026-11-05T12:00:00+09:00", StoredAnalysisState.READY
        )
        val society = ArticleSummary(
            "society-1", publisher, "사회 검색 결과", NewsCategory.SOCIETY,
            "2026-11-05T11:00:00+09:00", StoredAnalysisState.READY
        )
        val state = mutableStateOf(NewsFeedState(
            publishers = listOf(publisher),
            isSearchOpen = true,
            searchInput = "검색어",
            activeSearchQuery = "검색어",
            searchSections = mapOf(
                NewsCategory.POLITICS to SearchSectionState(
                    NewsCategory.POLITICS, listOf(politics), totalCount = 7
                ),
                NewsCategory.SOCIETY to SearchSectionState(
                    NewsCategory.SOCIETY, listOf(society), totalCount = 3
                )
            )
        ))
        val session = AppSession(
            NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry"
        )
        compose.setContent {
            NewsFeedScreen(
                state.value, session, rememberLazyListState(),
                onCategory = { state.value = state.value.copy(searchCategory = it) },
                onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = {}, onLogout = {}
            )
        }

        compose.onNodeWithContentDescription("정치 검색 결과 7개").assertIsDisplayed()
        compose.onNodeWithContentDescription("사회 검색 결과 3개").assertIsDisplayed()
        compose.onNodeWithText("정치 검색 결과").assertIsDisplayed()
        compose.onAllNodesWithText("사회 검색 결과").assertCountEquals(0)

        val searchPoliticsWidth = compose.onNodeWithContentDescription("정치 검색 결과 7개")
            .fetchSemanticsNode().boundsInRoot.width
        val searchSocietyWidth = compose.onNodeWithContentDescription("사회 검색 결과 3개")
            .fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { state.value = state.value.copy(isSearchOpen = false) }
        val ordinaryPoliticsWidth = compose.onNodeWithContentDescription("정치 섹션")
            .fetchSemanticsNode().boundsInRoot.width
        val ordinarySocietyWidth = compose.onNodeWithContentDescription("사회 섹션")
            .fetchSemanticsNode().boundsInRoot.width
        assertEquals(searchPoliticsWidth, ordinaryPoliticsWidth, 0.5f)
        assertEquals(searchSocietyWidth, ordinarySocietyWidth, 0.5f)
        compose.runOnIdle { state.value = state.value.copy(isSearchOpen = true) }

        compose.onNodeWithText("사회").performClick()
        compose.onNodeWithText("사회 검색 결과").assertIsDisplayed()
        compose.onAllNodesWithText("정치 검색 결과").assertCountEquals(0)
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
                onBack = {}, onRetry = {}, onAnalysisEnabled = { analysisEnabled = it },
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        compose.onNodeWithText("기사 전문입니다.").assertIsDisplayed()
        compose.onNodeWithText("분석은 완료되었지만 현재 표시할 표현 특징을 확인하지 못했어요.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("표현 분석 표시").performClick()
        compose.runOnIdle { assertFalse(analysisEnabled) }
    }

    @Test
    fun analysisToggleAddsMarkersWithoutMovingArticleContent() {
        val body = "빠르게 확장했다."
        val marker = "빠르게"
        val article = ArticleDetail(
            "article-stable-toggle", publisher, "사업 확장", body,
            NewsCategory.POLITICS, "2026-10-31T12:00:00+09:00",
            "https://example.com/article-stable-toggle", StoredAnalysisState.READY
        )
        val finding = FindingUi(
            id = "finding-stable-toggle",
            categories = listOf("D"),
            scope = "BODY",
            sentenceRange = TextRange(0, body.length),
            sentenceText = body,
            markerRange = TextRange(0, marker.length),
            markerText = marker,
            explanation = "확장 속도를 강조해요.",
            comparisonOverview = "",
            comparisons = emptyList()
        )
        val analysisEnabled = mutableStateOf(false)
        compose.setContent {
            NewsReaderScreen(
                state = ArticleReaderState(
                    articleId = article.id,
                    article = article,
                    analysis = ArticleAnalysisUi(
                        StoredAnalysisState.READY, "reviewed-v1", listOf(finding)
                    ),
                    analysisEnabled = analysisEnabled.value
                ),
                onBack = {}, onRetry = {},
                onAnalysisEnabled = { analysisEnabled.value = it },
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        val bodyNode = compose.onNodeWithText(body)
        val topBefore = bodyNode.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("표현 분석 표시").performClick()
        compose.waitForIdle()

        assertEquals(topBefore, bodyNode.fetchSemanticsNode().boundsInRoot.top)
        compose.onAllNodesWithText("밑줄 친 표현을 누르면", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun readerPreviewsSelectedFindingBeforeOpeningFullDetail() {
        val body = "빠르게 확장했다"
        val targetBody = "회사는 사업 영역을 넓히고 있다."
        val targetMarker = "넓히고 있다"
        val targetStart = targetBody.indexOf(targetMarker)
        val comparison = ComparisonArticleUi(
            articleId = "comparison-1",
            publisher = publisher,
            title = "사업 영역 확장",
            publishedAt = "2026-10-31T13:00:00+09:00",
            originalUrl = "https://example.com/comparison-1",
            body = targetBody,
            evidence = listOf(ComparisonEvidenceUi(
                scope = "BODY",
                sentenceRange = TextRange(0, targetBody.length),
                sentenceText = targetBody,
                markerRange = TextRange(targetStart, targetStart + targetMarker.length),
                markerText = targetMarker,
                owner = "REPORTER",
                speakerLabel = null,
                summary = "속도를 평가하지 않고 확장 상태를 서술해요."
            ))
        )
        val secondBody = "회사는 사업 영역을 확대하고 있다."
        val secondMarker = "확대하고 있다"
        val secondStart = secondBody.indexOf(secondMarker)
        val secondComparison = ComparisonArticleUi(
            articleId = "comparison-2",
            publisher = PublisherItem("mbc", "MBC", null),
            title = "사업 영역 확대",
            publishedAt = "2026-10-31T14:00:00+09:00",
            originalUrl = "https://example.com/comparison-2",
            body = secondBody,
            evidence = listOf(ComparisonEvidenceUi(
                scope = "BODY",
                sentenceRange = TextRange(0, secondBody.length),
                sentenceText = secondBody,
                markerRange = TextRange(secondStart, secondStart + secondMarker.length),
                markerText = secondMarker,
                owner = "REPORTER",
                speakerLabel = null,
                summary = "확장 상태를 중립적으로 서술해요."
            ))
        )
        val finding = FindingUi(
            id = "finding-1",
            categories = listOf("D"),
            scope = "BODY",
            sentenceRange = TextRange(0, body.length),
            sentenceText = body,
            markerRange = TextRange(0, body.length),
            markerText = body,
            explanation = "확장 속도를 강조하는 표현이에요.",
            comparisonOverview = "한 기사는 빠른 확장을 강조하지만 다른 두 기사는 확장 상태를 중립적으로 전해요.",
            comparisons = listOf(comparison, secondComparison)
        )
        val article = ArticleDetail(
            "article-1", publisher, "사업 확장 소식", body,
            NewsCategory.POLITICS, "2026-10-31T12:00:00+09:00",
            "https://example.com/article-1", StoredAnalysisState.READY
        )
        val state = ArticleReaderState(
            articleId = article.id,
            article = article,
            analysis = ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", listOf(finding)),
            analysisEnabled = true
        )
        compose.setContent {
            NewsReaderScreen(
                state = remember { state },
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        compose.onNodeWithText(body).performTouchInput { click(Offset(24f, centerY)) }
        compose.onNodeWithText("“$body”").assertIsDisplayed()
        compose.onNodeWithText("다른 기사 2곳과 비교", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("같은 내용을 다른 기사에서는").assertCountEquals(0)

        compose.onNodeWithText("다른 기사 2곳과 비교", substring = true).performClick()
        compose.onNodeWithText("같은 내용을 다른 기사에서는").assertIsDisplayed()
        compose.onNodeWithContentDescription("비교 상세 닫기").assertIsDisplayed()
        compose.onAllNodesWithText(finding.comparisonOverview).assertCountEquals(1)
        compose.onAllNodesWithText("기자 표현").assertCountEquals(0)
        compose.onAllNodesWithText("속도를 평가하지 않고 확장 상태를 서술해요.").assertCountEquals(0)
        compose.onNodeWithText("1 / 2").assertIsDisplayed()

        compose.onNodeWithContentDescription("다음 비교 기사").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onAllNodesWithText(finding.comparisonOverview).assertCountEquals(1)
        compose.onNodeWithContentDescription("이전 비교 기사").performClick()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()

        compose.onNodeWithContentDescription("비교 상세 닫기").performClick()
        compose.onAllNodesWithText("같은 내용을 다른 기사에서는").assertCountEquals(0)
        compose.onAllNodesWithText("다른 기사 2곳과 비교", substring = true).assertCountEquals(0)
    }

    @Test
    fun sourceOnlyFindingEndsInPreviewWithoutDetailAction() {
        val body = "지역별로 온도차가 있었다."
        val marker = "온도차가 있었다"
        val markerStart = body.indexOf(marker)
        val explanation = "서로 다른 반응을 온도차라는 평가적 표현으로 묶어 설명해요. " +
            "여러 지역의 복잡한 반응을 하나의 스펙트럼 위에 놓은 서술이라는 점을 " +
            "함께 확인할 수 있도록 추가 설명을 충분히 제공해요."
        val finding = FindingUi(
            id = "source-only",
            categories = listOf("E", "G"),
            scope = "BODY",
            sentenceRange = TextRange(0, body.length),
            sentenceText = body,
            markerRange = TextRange(markerStart, markerStart + marker.length),
            markerText = marker,
            explanation = explanation,
            comparisonOverview = "",
            comparisons = emptyList()
        )
        val article = ArticleDetail(
            "article-source-only", publisher, "지역별 반응", body,
            NewsCategory.SOCIETY, "2026-10-31T12:00:00+09:00",
            "https://example.com/source-only", StoredAnalysisState.READY
        )
        compose.setContent {
            NewsReaderScreen(
                state = remember {
                    ArticleReaderState(
                        articleId = article.id,
                        article = article,
                        analysis = ArticleAnalysisUi(
                            StoredAnalysisState.READY, "reviewed-v1", listOf(finding)
                        ),
                        analysisEnabled = true
                    )
                },
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        compose.onNodeWithText(body).performTouchInput { click() }
        compose.onNodeWithText(explanation).assertIsDisplayed()
        compose.onNodeWithText("평가·감정이 담긴 표현").assertIsDisplayed()
        compose.onNodeWithText("확신·범주화가 드러나는 표현").assertIsDisplayed()
        compose.onNodeWithText("설명 더보기").assertIsDisplayed().performClick()
        compose.onNodeWithText("설명 접기").assertIsDisplayed()
        compose.onAllNodesWithText("곳과 비교", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("같은 내용을 다른 기사에서는").assertCountEquals(0)

        compose.onNodeWithText("지역별 반응").performTouchInput { click() }
        compose.onAllNodesWithText("“$marker”").assertCountEquals(0)
    }
}
