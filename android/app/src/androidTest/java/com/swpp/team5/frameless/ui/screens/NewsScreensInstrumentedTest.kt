package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import com.swpp.team5.frameless.ui.viewmodel.ReaderFocus
import com.swpp.team5.frameless.ui.viewmodel.SearchFilter
import com.swpp.team5.frameless.ui.viewmodel.SearchSectionState
import com.swpp.team5.frameless.ui.viewmodel.SavedArticlesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                onCategory = {}, onSearchFilter = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = { selectedId = it }, onLogin = {}, onLogout = {}
            )
        }

        compose.onNodeWithText("정부, 정책 전격 추진").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("분석 완료").assertCountEquals(0)
        compose.runOnIdle { assertEquals("article-1", selectedId) }
        compose.onNodeWithContentDescription("계정 메뉴").performClick()
        compose.onNodeWithText("reader").assertIsDisplayed()
    }

    @Test
    fun feedWithAnotherPageOffersMoreArticlesButton() {
        var loadMoreCalls = 0
        val state = NewsFeedState(
            publishers = listOf(publisher),
            items = (1..20).map { n ->
                ArticleSummary("article-$n", publisher, "기사 $n", NewsCategory.POLITICS,
                    "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY)
            },
            nextCursor = "next"
        )
        compose.setContent {
            val listState = rememberLazyListState()
            NewsFeedScreen(
                state, null, listState,
                onCategory = {}, onSearchFilter = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = { loadMoreCalls += 1 }, onLoadMoreSearch = {},
                onRetrySearch = {}, onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {},
                onSearchClose = {}, onSavedOpen = {}, onArticle = {}, onLogin = {}, onLogout = {}
            )
        }

        // LazyColumn only composes nearby rows, so reveal the pagination row by index first.
        compose.onNodeWithTag("feed-list").performScrollToIndex(21)
        compose.onNodeWithText("기사 더 보기").performClick()
        compose.runOnIdle { assertTrue(loadMoreCalls >= 1) }
    }

    @Test
    fun normalFeedPullRefreshesWithoutAHeaderButton() {
        var refreshCalls = 0
        val state = NewsFeedState(
            publishers = listOf(publisher),
            items = listOf(
                ArticleSummary(
                    "article-refresh", publisher, "당겨서 갱신할 기사", NewsCategory.POLITICS,
                    "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY
                )
            )
        )
        compose.setContent {
            NewsFeedScreen(
                state, null, rememberLazyListState(),
                onCategory = {}, onSearchFilter = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = { refreshCalls += 1 }, onRetry = {}, onLoadMore = {},
                onLoadMoreSearch = {}, onRetrySearch = {}, onSearchOpen = {},
                onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {}, onSavedOpen = {},
                onArticle = {}, onLogin = {}, onLogout = {}
            )
        }

        compose.onAllNodesWithText("새로고침").assertCountEquals(0)
        compose.onNodeWithTag("feed-list").performTouchInput {
            swipeDown(startY = 80f, endY = height * 0.8f, durationMillis = 500)
        }
        compose.runOnIdle { assertEquals(1, refreshCalls) }
    }

    @Test
    fun savedUsesPullRefreshAndEdgeSwipeBack() {
        var refreshCalls = 0
        var backCalls = 0
        val item = ArticleSummary(
            "saved-article", publisher, "저장한 기사", NewsCategory.POLITICS,
            "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY
        )
        compose.setContent {
            NewsSavedScreen(
                state = SavedArticlesState(items = listOf(item)),
                listState = rememberLazyListState(),
                onBack = { backCalls += 1 },
                onRefresh = { refreshCalls += 1 },
                onRetry = {}, onLoadMore = {}, onArticle = {}
            )
        }

        compose.onAllNodesWithText("새로고침").assertCountEquals(0)
        compose.onNodeWithTag("saved-list").performTouchInput {
            swipeDown(startY = 80f, endY = height * 0.8f, durationMillis = 500)
        }
        compose.runOnIdle { assertEquals(1, refreshCalls) }

        compose.onNodeWithTag("saved-screen").performTouchInput {
            swipe(
                start = Offset(width * 0.35f, centerY),
                end = Offset(width * 0.85f, centerY),
                durationMillis = 350
            )
        }
        compose.runOnIdle { assertEquals(0, backCalls) }

        compose.onNodeWithTag("saved-screen").performTouchInput {
            swipe(
                start = Offset(1f, centerY),
                end = Offset(width * 0.65f, centerY),
                durationMillis = 350
            )
        }
        compose.runOnIdle { assertEquals(1, backCalls) }
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
                onCategory = {}, onSearchFilter = {}, onScope = { selectedScope = it }, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = {}, onLogin = {}, onLogout = {}
            )
        }

        compose.onNodeWithContentDescription("정치 섹션").assertIsDisplayed()
        compose.onNodeWithContentDescription("사회 섹션").assertIsDisplayed()
        val politicsWidth = compose.onNodeWithContentDescription("정치 섹션")
            .fetchSemanticsNode().boundsInRoot.width
        val societyWidth = compose.onNodeWithContentDescription("사회 섹션")
            .fetchSemanticsNode().boundsInRoot.width
        assertEquals(politicsWidth, societyWidth, 0.5f)
        compose.onNodeWithText("최신  ⌄").performClick()
        compose.onNodeWithContentDescription("최신 피드").assertIsSelected()
        compose.onNodeWithContentDescription("팔로잉 피드").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(FeedScope.FOLLOWING, selectedScope) }
    }

    @Test
    fun authFormDismissesKeyboardWhenTheUserTapsOrdinaryContent() {
        compose.setContent {
            LoginScreen(
                identifier = "",
                password = "",
                loading = false,
                error = null,
                onIdentifier = {},
                onPassword = {},
                onLogin = {},
                onRegister = {}
            )
        }

        val identifier = compose.onNodeWithText("이메일 또는 사용자 이름")
        identifier.performClick().assertIsFocused()
        compose.onNodeWithText("다른 보도를\n함께 읽는 뉴스")
            .performTouchInput { click() }
        identifier.assertIsNotFocused()
    }

    @Test
    fun zeroResultSearchPreviewsUrlAnalysisWithoutSubmitting() {
        val state = NewsFeedState(
            publishers = listOf(publisher),
            isSearchOpen = true,
            searchInput = "없는 기사",
            activeSearchQuery = "없는 기사",
            searchSections = mapOf(
                SearchFilter.ALL to SearchSectionState(filter = SearchFilter.ALL)
            )
        )
        val session = AppSession(NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry")
        compose.setContent {
            NewsFeedScreen(
                state, session, rememberLazyListState(),
                onCategory = {}, onSearchFilter = {}, onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = {}, onLogin = {}, onLogout = {}
            )
        }

        compose.onNodeWithText("기사 링크로 분석하기").performClick()
        compose.onNodeWithText("계속").performClick()
        compose.onNodeWithText("http:// 또는 https://로 시작하는 전체 기사 주소를 입력해 주세요.")
            .assertIsDisplayed()
        val urlInput = compose.onNodeWithContentDescription("기사 URL 입력")
        urlInput.performTextInput("https://news.example.com/story")
        urlInput.assertIsFocused()
        compose.onNodeWithText("공개된 뉴스 기사 주소를 입력해", substring = true)
            .performTouchInput { click() }
        compose.onNodeWithContentDescription("기사 URL 입력").assertIsNotFocused()
        compose.onNodeWithText("계속").performClick()
        compose.onNodeWithText("기사 분석 기능을 준비하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("검색으로 돌아가기").performClick()
        compose.onNodeWithText("검색 결과가 없어요").assertIsDisplayed()
    }

    @Test
    fun searchUsesAllPoliticsAndSocietyFiltersWithoutCountBadges() {
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
                SearchFilter.ALL to SearchSectionState(
                    SearchFilter.ALL, listOf(politics, society), totalCount = 10
                ),
                SearchFilter.POLITICS to SearchSectionState(
                    SearchFilter.POLITICS, emptyList(), totalCount = 0
                ),
                SearchFilter.SOCIETY to SearchSectionState(
                    SearchFilter.SOCIETY, listOf(society), totalCount = 3
                )
            )
        ))
        val session = AppSession(
            NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry"
        )
        compose.setContent {
            NewsFeedScreen(
                state.value, session, rememberLazyListState(),
                onCategory = {},
                onSearchFilter = { state.value = state.value.copy(searchFilter = it) },
                onScope = {}, onSources = { _, _ -> },
                onRefresh = {}, onRetry = {}, onLoadMore = {}, onLoadMoreSearch = {}, onRetrySearch = {},
                onSearchOpen = {}, onSearchInput = {}, onSearchSubmit = {}, onSearchClose = {},
                onSavedOpen = {}, onArticle = {}, onLogin = {}, onLogout = {}
            )
        }

        compose.onNodeWithContentDescription("전체 검색 필터").assertIsDisplayed()
        compose.onNodeWithContentDescription("정치 검색 필터").assertIsDisplayed()
        compose.onNodeWithContentDescription("사회 검색 필터").assertIsDisplayed()
        compose.onNodeWithText("정치 검색 결과").assertIsDisplayed()
        compose.onNodeWithText("사회 검색 결과").assertIsDisplayed()
        compose.onAllNodesWithText("7").assertCountEquals(0)
        compose.onAllNodesWithText("3").assertCountEquals(0)
        compose.onNodeWithContentDescription("기사 검색어").assertIsFocused()

        compose.onNodeWithContentDescription("정치 검색 필터").performClick()
        compose.onNodeWithContentDescription("기사 검색어").assertIsNotFocused()
        compose.onNodeWithText("정치 분야에는 일치하는 기사가 없어요.").assertIsDisplayed()
        compose.onNodeWithText("전체 검색 결과 보기").assertIsDisplayed().performClick()
        compose.onNodeWithText("정치 검색 결과").assertIsDisplayed()
        compose.onNodeWithText("사회 검색 결과").assertIsDisplayed()

        compose.onNodeWithContentDescription("사회 검색 필터").performClick()
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
    fun comparisonTargetExplainsTransientContextWithoutClaimingAStandaloneFinding() {
        val body = "조선비즈는 통상 약 12시간 머물렀다고 전했다."
        val marker = "통상 약 12시간"
        val markerStart = body.indexOf(marker)
        val article = ArticleDetail(
            "comparison-article", publisher, "비교 기사", body,
            NewsCategory.SOCIETY, "2026-11-15T09:00:00+09:00",
            "https://example.com/comparison-article", StoredAnalysisState.READY
        )
        compose.setContent {
            NewsReaderScreen(
                state = ArticleReaderState(
                    articleId = article.id,
                    article = article,
                    analysis = ArticleAnalysisUi(
                        StoredAnalysisState.READY, "reviewed-v1", emptyList()
                    ),
                    analysisEnabled = true,
                    comparisonFocus = ReaderFocus(
                        "BODY", TextRange(markerStart, markerStart + marker.length)
                    )
                ),
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        compose.onNodeWithText("비교 기사에서 이어 읽는 중").assertIsDisplayed()
        compose.onNodeWithText("확인한 표현을 본문에 표시했어요.").assertIsDisplayed()
        compose.onNodeWithText("이 기사의 별도 표현 분석은 없어요.").assertIsDisplayed()
        compose.onAllNodesWithText("분석은 완료되었지만", substring = true).assertCountEquals(0)
    }

    @Test
    fun comparisonTargetWaitsForLayoutBeforeScrollingToFocusedPassage() {
        val prefix = List(60) { "앞부분 기사 문장입니다." }.joinToString("\n\n")
        val marker = "비교에서 확인한 표현"
        val body = "$prefix\n\n마지막에 $marker 이 있습니다."
        val markerStart = body.indexOf(marker)
        val article = ArticleDetail(
            "long-comparison-article", publisher, "긴 비교 기사", body,
            NewsCategory.SOCIETY, "2026-11-15T09:00:00+09:00",
            "https://example.com/long-comparison-article", StoredAnalysisState.READY
        )
        compose.setContent {
            NewsReaderScreen(
                state = ArticleReaderState(
                    articleId = article.id,
                    article = article,
                    analysis = ArticleAnalysisUi(
                        StoredAnalysisState.READY, "reviewed-v1", emptyList()
                    ),
                    analysisEnabled = true,
                    comparisonFocus = ReaderFocus(
                        "BODY", TextRange(markerStart, markerStart + marker.length)
                    )
                ),
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        compose.waitForIdle()
        compose.onNodeWithText("비교 기사에서 이어 읽는 중").assertIsNotDisplayed()
        compose.onNodeWithText(body).assertIsDisplayed()
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
    fun readerAnnotationOpensFullComparisonDetail() {
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
        var openedComparison: ComparisonArticleUi? = null
        compose.setContent {
            NewsReaderScreen(
                state = remember { state },
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null,
                onOpenComparisonArticle = { article, _, _ -> openedComparison = article }
            )
        }

        compose.onNodeWithTag("analysis-annotation-dock").assertIsDisplayed()
        compose.onNodeWithText("“$body”").assertIsDisplayed()
        compose.onNodeWithText("다른 보도 2곳", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("같은 내용을 다른 기사에서는").assertCountEquals(0)

        compose.onNodeWithText("다른 보도 2곳", substring = true).performClick()
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
        compose.onNodeWithTag("analysis-annotation-dock").assertIsDisplayed()

        compose.onNodeWithText("다른 보도 2곳", substring = true).performClick()
        compose.onNodeWithText("이 기사 읽기").performClick()
        compose.waitUntil { openedComparison != null }
        assertEquals("comparison-1", openedComparison?.articleId)
        assertEquals(targetMarker, openedComparison?.evidence?.single()?.markerText)
    }

    @Test
    fun readerKeepsFixedAnnotationDockAndUpdatesItDuringUserScroll() {
        val paragraphs = (0 until 24).map { index ->
            if (index in 2..20 && index % 2 == 0) {
                "문단 $index 에서는 강조표현$index 을 사용한 보도를 설명합니다."
            } else {
                "문단 $index 에서는 일반적인 기사 내용을 차분하게 설명합니다."
            }
        }
        val body = paragraphs.joinToString("\n\n")
        val comparison = summaryComparison(
            id = "auto-target",
            publisherName = "MBC",
            body = "다른 보도는 사업 영역을 넓히고 있다고 전했습니다.",
            marker = "넓히고 있다"
        )
        val findings = (2..20 step 2).map { index ->
            val marker = "강조표현$index"
            val start = body.indexOf(marker)
            FindingUi(
                id = "auto-$index",
                categories = listOf("D"),
                scope = "BODY",
                sentenceRange = TextRange(start, start + marker.length),
                sentenceText = marker,
                markerRange = TextRange(start, start + marker.length),
                markerText = marker,
                explanation = "강조된 표현이에요.",
                comparisonOverview = "두 기사가 확장 상태를 다르게 표현해요.",
                comparisons = listOf(comparison)
            )
        }
        val article = ArticleDetail(
            "auto-peek-article", publisher, "자동 분석 미리보기", body,
            NewsCategory.POLITICS, "2026-11-21T12:00:00+09:00",
            "https://example.com/auto-peek", StoredAnalysisState.READY
        )
        compose.setContent {
            NewsReaderScreen(
                state = ArticleReaderState(
                    articleId = article.id,
                    article = article,
                    analysis = ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", findings),
                    analysisEnabled = true
                ),
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        compose.waitForIdle()
        val dock = compose.onNodeWithTag("analysis-annotation-dock").assertIsDisplayed()
        val heightBefore = dock.fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("“강조표현2”").assertIsDisplayed()

        var changed = false
        repeat(10) {
            if (!changed) {
                compose.onNodeWithTag("article-reader-scroll").performTouchInput {
                    swipeUp(startY = centerY + 140f, endY = centerY - 140f, durationMillis = 350)
                }
                compose.waitForIdle()
                changed = (4..20 step 2).any { index ->
                    compose.onAllNodesWithText("“강조표현$index”")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
        assertTrue(changed)
        compose.waitUntil(timeoutMillis = 2_000) {
            (4..20 step 2).any { index ->
                compose.onAllNodesWithText("“강조표현$index”")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        assertEquals(heightBefore, dock.fetchSemanticsNode().boundsInRoot.height)
        compose.onNodeWithText("다른 보도 1곳", substring = true).assertIsDisplayed()
    }

    @Test
    fun sourceOnlyFindingStaysInDockWithoutDetailAction() {
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

        compose.onNodeWithTag("analysis-annotation-dock").assertIsDisplayed()
        compose.onNodeWithText(explanation).assertIsDisplayed()
        compose.onNodeWithText("평가·감정이 담긴 표현", substring = true).assertIsDisplayed()
        compose.onNodeWithText("확신·범주화가 드러나는 표현", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("곳과 비교", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("같은 내용을 다른 기사에서는").assertCountEquals(0)

        compose.onNodeWithText("지역별 반응").performTouchInput { click() }
        compose.onNodeWithText("“$marker”").assertIsDisplayed()
    }

    @Test
    fun readerSnapshotRestoresThePreviouslySelectedFinding() {
        val body = "첫 표현을 사용했다. 이어서 둘째 표현을 사용했다."
        val first = summaryFinding("first", body, "첫 표현", emptyList())
        val second = summaryFinding("second", body, "둘째 표현", emptyList())
        val article = summaryArticle("restored-reader", body)

        compose.setContent {
            NewsReaderScreen(
                state = ArticleReaderState(
                    articleId = article.id,
                    article = article,
                    analysis = ArticleAnalysisUi(
                        StoredAnalysisState.READY, "reviewed-v1", listOf(first, second)
                    ),
                    analysisEnabled = true,
                    initialSelectedFindingId = second.id
                ),
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null
            )
        }

        compose.onNodeWithTag("analysis-annotation-dock").assertIsDisplayed()
        compose.onNodeWithText("“둘째 표현”").assertIsDisplayed()
        compose.onAllNodesWithText("“첫 표현”").assertCountEquals(0)
    }

    private fun summaryComparison(
        id: String,
        publisherName: String,
        body: String,
        marker: String
    ): ComparisonArticleUi {
        val start = body.indexOf(marker)
        return ComparisonArticleUi(
            articleId = id,
            publisher = PublisherItem(id, publisherName, null),
            title = "$publisherName 비교 기사",
            publishedAt = "2026-11-18T10:00:00+09:00",
            originalUrl = "https://example.com/$id",
            body = body,
            evidence = listOf(ComparisonEvidenceUi(
                scope = "BODY",
                sentenceRange = TextRange(0, body.length),
                sentenceText = body,
                markerRange = TextRange(start, start + marker.length),
                markerText = marker,
                owner = "REPORTER",
                speakerLabel = null,
                summary = "같은 내용을 다른 말로 전해요."
            ))
        )
    }

    private fun summaryFinding(
        id: String,
        body: String,
        marker: String,
        comparisons: List<ComparisonArticleUi>,
        overview: String = "한 기사는 징계라고 쓰고 다른 기사는 중징계라고 전해요."
    ): FindingUi {
        val start = body.indexOf(marker)
        return FindingUi(
            id = id,
            categories = listOf("C", "D"),
            scope = "BODY",
            sentenceRange = TextRange(start, start + marker.length),
            sentenceText = marker,
            markerRange = TextRange(start, start + marker.length),
            markerText = marker,
            explanation = "$marker 표현의 특징이에요.",
            comparisonOverview = overview,
            comparisons = comparisons
        )
    }

    private fun summaryArticle(id: String, body: String) = ArticleDetail(
        id, publisher, "윤리위 징계 결정", body,
        NewsCategory.POLITICS, "2026-11-18T09:00:00+09:00",
        "https://example.com/$id", StoredAnalysisState.READY
    )

    private fun setSummaryReader(
        article: ArticleDetail,
        findings: List<FindingUi>,
        onOpenComparison: (ComparisonArticleUi) -> Unit = {}
    ) {
        compose.setContent {
            NewsReaderScreen(
                state = ArticleReaderState(
                    articleId = article.id,
                    article = article,
                    analysis = ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", findings),
                    analysisEnabled = true
                ),
                onBack = {}, onRetry = {}, onAnalysisEnabled = {},
                onToggleSaved = { _, _ -> }, savedPending = false, saveError = null,
                onOpenComparisonArticle = { page, _, _ -> onOpenComparison(page) }
            )
        }
    }

    @Test
    fun comparisonPointsSectionIsHiddenWithoutAValidComparison() {
        val body = "윤리위는 의장에게 징계를 내렸다. 위원들은 거세게 반발했다."
        val sourceOnly = summaryFinding("source-only", body, "징계", emptyList())
        val blankTarget = summaryComparison("kbs", "KBS", "위원들이 반발했다.", "반발했다").let { page ->
            page.copy(evidence = page.evidence.map { it.copy(markerText = "  ") })
        }
        val noTargetMarker = summaryFinding("no-target-marker", body, "거세게", listOf(blankTarget))
        setSummaryReader(summaryArticle("article-no-points", body), listOf(sourceOnly, noTargetMarker))

        compose.onNodeWithText(body).assertIsDisplayed()
        compose.onAllNodesWithText("비교 포인트").assertCountEquals(0)
        compose.onAllNodesWithText("표현 비교 보기", substring = true).assertCountEquals(0)
    }

    @Test
    fun comparisonPointsSectionShowsPublisherMarkersOnceWithoutOverviewOrLabels() {
        val body = "윤리위는 의장에게 징계를 내렸다. 부의장에게도 징계를 내렸다. 결정은 신속했다."
        val kbs = summaryComparison("kbs", "KBS", "윤리위가 중징계를 결정했다.", "중징계")
        val overview = "A5 기사는 징계라고 쓰고 B2 기사는 중징계라고 써요."
        val first = summaryFinding("f1", body, "징계", listOf(kbs), overview)
        val secondStart = body.indexOf("징계", body.indexOf("징계") + 1)
        val duplicate = summaryFinding("f2", body, "징계", listOf(kbs), overview).copy(
            markerRange = TextRange(secondStart, secondStart + 2),
            sentenceRange = TextRange(secondStart, secondStart + 2)
        )
        val other = summaryFinding("f3", body, "신속했다",
            listOf(summaryComparison("mbc", "MBC", "결정은 졸속이었다.", "졸속이었다")), overview)
        setSummaryReader(summaryArticle("article-points", body), listOf(first, duplicate, other))

        compose.onNodeWithText("비교 포인트").assertIsDisplayed()
        compose.onAllNodesWithText("연합뉴스  “징계”", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("KBS  “중징계”", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("MBC  “졸속이었다”", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("표현 비교 보기", substring = true).assertCountEquals(2)
        compose.onAllNodesWithText(overview).assertCountEquals(0)
        compose.onAllNodesWithText(first.explanation).assertCountEquals(1)
        listOf("A", "B", "C", "D", "E", "F", "G").forEach { code ->
            compose.onAllNodesWithText(code).assertCountEquals(0)
            compose.onAllNodesWithText(categoryLabel(code)).assertCountEquals(0)
        }
    }

    @Test
    fun comparisonPointOpensTheExistingComparisonDetail() {
        val body = "윤리위는 의장에게 징계를 내렸다."
        val finding = summaryFinding("f1", body, "징계", listOf(
            summaryComparison("kbs", "KBS", "윤리위가 중징계를 결정했다.", "중징계"),
            summaryComparison("mbc", "MBC", "윤리위가 탈당권고를 의결했다.", "탈당권고")
        ))
        var opened: ComparisonArticleUi? = null
        setSummaryReader(summaryArticle("article-route", body), listOf(finding)) { opened = it }

        compose.onNodeWithText("표현 비교 보기", substring = true).performClick()
        compose.onNodeWithText("같은 내용을 다른 기사에서는").assertIsDisplayed()
        compose.onNodeWithContentDescription("비교 상세 닫기").assertIsDisplayed()
        compose.onAllNodesWithText(finding.comparisonOverview).assertCountEquals(1)
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        // The fixed annotation dock remains visible behind the detail sheet by design.
        compose.onAllNodesWithText(finding.explanation).assertCountEquals(1)

        compose.onNodeWithContentDescription("비교 상세 닫기").performClick()
        compose.onAllNodesWithText("같은 내용을 다른 기사에서는").assertCountEquals(0)
        compose.onNodeWithText("비교 포인트").assertIsDisplayed()

        compose.onNodeWithText("표현 비교 보기", substring = true).performClick()
        compose.onNodeWithText("이 기사 읽기").performClick()
        compose.waitUntil { opened != null }
        assertEquals("kbs", opened?.articleId)
    }
}
