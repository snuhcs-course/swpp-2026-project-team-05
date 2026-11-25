package com.swpp.team5.frameless.ui.viewmodel

import android.os.Looper
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticlePage
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.ComparisonEvidenceUi
import com.swpp.team5.frameless.data.FeedScope
import com.swpp.team5.frameless.data.NewsApiException
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsDataSource
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.data.TextRange
import com.swpp.team5.frameless.telemetry.Telemetry
import com.swpp.team5.frameless.telemetry.TelemetryEvent
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class NewsViewModelTest {
    private val mainLooper get() = shadowOf(Looper.getMainLooper())
    private val yna = PublisherItem("yna", "연합뉴스", null)
    private val mbc = PublisherItem("mbc", "MBC", null)

    private fun summary(id: String, publisher: PublisherItem = yna) = ArticleSummary(
        id, publisher, "기사 $id", NewsCategory.POLITICS,
        "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY
    )

    private fun detail(id: String) = ArticleDetail(
        id, yna, "기사 $id", "본문 $id", NewsCategory.POLITICS,
        "2026-10-31T12:00:00+09:00", "https://example.com/$id", StoredAnalysisState.READY
    )

    @Test
    fun initializeLoadsPublishersAndTheCompleteLatestFeed() {
        val repository = FakeNewsDataSource().apply {
            savedCategoryValue = NewsCategory.SOCIETY
            pages += ArticlePage(listOf(summary("a")), "next")
        }
        val viewModel = NewsViewModel(repository)

        assertEquals(NewsCategory.SOCIETY, viewModel.feed.category)
        viewModel.initialize()
        mainLooper.idle()

        assertEquals(listOf(yna, mbc), viewModel.feed.publishers)
        assertEquals(FeedScope.ALL, viewModel.feed.scope)
        assertEquals(FeedScope.ALL, repository.scopes.single())
        assertEquals(listOf("a"), viewModel.feed.items.map { it.id })
        assertEquals("next", viewModel.feed.nextCursor)
        assertFalse(viewModel.feed.isLoading)
        viewModel.initialize()
        mainLooper.idle()
        assertEquals(1, repository.articlePageCalls)
    }

    @Test
    fun reviewedExamplesLeadOnlyTheLatestFeed() {
        val regular = summary("regular")
        val panda = summary("panda").copy(
            title = "에버랜드 막내 판다 '슈바오' 30일 첫 나들이…쌍둥이 언니들은 작별 준비"
        )
        val hd = summary("hd").copy(
            title = "HD현대일렉트릭, 3분기 매출 1조1799억원 '역대 최대'"
        )
        val serverOrder = listOf(regular, panda, hd)
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(serverOrder, null)
            pages += ArticlePage(serverOrder, null)
            pages += ArticlePage(serverOrder, null)
            searchPages.getValue(null) += ArticlePage(serverOrder, null, 3)
        }
        val viewModel = NewsViewModel(repository)

        viewModel.initialize()
        mainLooper.idle()
        assertEquals(listOf("hd", "panda", "regular"), viewModel.feed.items.map { it.id })

        viewModel.selectScope(FeedScope.FOLLOWING)
        mainLooper.idle()
        assertEquals(listOf("regular", "panda", "hd"), viewModel.feed.items.map { it.id })

        viewModel.selectScope(FeedScope.ALL)
        mainLooper.idle()
        viewModel.openSearch()
        viewModel.updateSearchInput("분석")
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)
        assertEquals(
            listOf("regular", "panda", "hd"),
            viewModel.feed.searchSections.getValue(SearchFilter.ALL).items.map { it.id }
        )
    }

    @Test
    fun categoryScopeRefreshAndPaginationPreserveExpectedState() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("first")), "page-2")
            pages += ArticlePage(listOf(summary("society")), null)
            pages += ArticlePage(listOf(summary("filtered", mbc)), "more")
            pages += ArticlePage(listOf(summary("more", mbc)), null)
            pages += ArticlePage(listOf(summary("refreshed", mbc)), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.selectCategory(NewsCategory.SOCIETY)
        mainLooper.idle()
        assertEquals(NewsCategory.SOCIETY, repository.savedCategories.last())
        assertEquals(listOf("society"), viewModel.feed.items.map { it.id })

        viewModel.selectScope(FeedScope.FOLLOWING)
        mainLooper.idle()
        assertEquals(FeedScope.FOLLOWING, viewModel.feed.scope)
        assertEquals(listOf("filtered"), viewModel.feed.items.map { it.id })

        viewModel.loadMore()
        mainLooper.idle()
        assertEquals(listOf("filtered", "more"), viewModel.feed.items.map { it.id })
        viewModel.refresh()
        mainLooper.idle()
        assertEquals(listOf("refreshed"), viewModel.feed.items.map { it.id })
    }

    @Test
    fun sourcePreferencesAreAccountBackedAndFollowingReloadsAfterSave() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("latest")), null)
            pages += ArticlePage(emptyList(), null)
            pages += ArticlePage(listOf(summary("followed", mbc)), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.selectScope(FeedScope.FOLLOWING)
        mainLooper.idle()

        var completed = false
        viewModel.replaceFollowedPublishers(setOf("mbc", "unknown")) { completed = true }
        mainLooper.idle()

        assertEquals(setOf("mbc"), repository.followedCodes)
        assertTrue(viewModel.feed.publishers.single { it.code == "mbc" }.isFollowed)
        assertEquals(listOf("followed"), viewModel.feed.items.map { it.id })
        assertTrue(completed)
    }

    @Test
    fun feedFailuresAreRetryableAndAuthenticationFailureExpiresSession() {
        val repository = FakeNewsDataSource().apply {
            pageFailure = IllegalStateException("피드 실패")
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        assertEquals("피드 실패", viewModel.feed.error)

        repository.pageFailure = null
        repository.pages += ArticlePage(listOf(summary("ok")), "more")
        viewModel.retryFeed()
        mainLooper.idle()
        repository.pageFailure = NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")
        viewModel.loadMore()
        mainLooper.idle()
        assertTrue(viewModel.authExpired)
        assertEquals("로그인이 필요합니다.", viewModel.feed.nextPageError)
        viewModel.consumeAuthExpired()
        assertFalse(viewModel.authExpired)
    }

    @Test
    fun networkFailureShowsOnlyOfflineSavedArticles() {
        val repository = FakeNewsDataSource().apply {
            offlineSavedPage = ArticlePage(
                listOf(summary("saved-offline").copy(isSaved = true)), null
            )
            pageFailure = NewsApiException("NETWORK_ERROR", "연결할 수 없어요.")
        }
        val viewModel = NewsViewModel(repository)

        viewModel.initialize()
        mainLooper.idle()

        assertEquals(listOf("saved-offline"), viewModel.feed.items.map { it.id })
        assertTrue(viewModel.feed.isOffline)
        assertEquals(null, viewModel.feed.error)
        assertEquals(listOf(NewsCategory.POLITICS), repository.offlineCategories)
    }

    @Test
    fun guestNetworkFailureShowsHonestOfflineEmptyState() {
        val repository = FakeNewsDataSource().apply {
            pageFailure = NewsApiException("NETWORK_ERROR", "연결할 수 없어요.")
        }
        val viewModel = NewsViewModel(repository)

        viewModel.initialize()
        mainLooper.idle()

        assertTrue(viewModel.feed.items.isEmpty())
        assertTrue(viewModel.feed.isOffline)
        assertEquals(null, viewModel.feed.error)
    }

    @Test
    fun searchDebouncesServerQueryAndClosingRestoresFeed() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            searchPages.getValue(null) +=
                ArticlePage(listOf(summary("politics-result")), "all-more", 2)
            searchPages.getValue(NewsCategory.SOCIETY) +=
                ArticlePage(listOf(summary("society-result").copy(category = NewsCategory.SOCIETY)), null, 1)
            searchPages.getValue(null) +=
                ArticlePage(listOf(summary("politics-result-2")), null, 2)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.openSearch()
        viewModel.updateSearchInput("정")
        assertTrue(viewModel.feed.items.isEmpty())
        assertEquals("", viewModel.feed.activeSearchQuery)

        viewModel.updateSearchInput("정책")
        viewModel.updateSearchInput("정책 추진")
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)

        assertEquals("정책 추진", repository.queries.last())
        assertEquals(FeedScope.ALL, repository.scopes.last())
        assertEquals(
            listOf("politics-result"),
            viewModel.feed.searchSections.getValue(SearchFilter.ALL).items.map { it.id }
        )
        assertEquals(2, viewModel.feed.searchSections.getValue(SearchFilter.ALL).totalCount)
        assertEquals(null, repository.categories.last())
        assertEquals("정책 추진", viewModel.feed.activeSearchQuery)

        viewModel.selectSearchFilter(SearchFilter.SOCIETY)
        mainLooper.idle()
        assertEquals(SearchFilter.SOCIETY, viewModel.feed.searchFilter)
        assertEquals(NewsCategory.POLITICS, viewModel.feed.category)
        assertEquals(
            listOf("society-result"),
            viewModel.feed.searchSections.getValue(SearchFilter.SOCIETY).items.map { it.id }
        )

        viewModel.selectSearchFilter(SearchFilter.ALL)
        viewModel.loadMoreSearch(SearchFilter.ALL)
        mainLooper.idle()
        assertEquals(
            listOf("politics-result", "politics-result-2"),
            viewModel.feed.searchSections.getValue(SearchFilter.ALL).items.map { it.id }
        )
        assertEquals(
            listOf("society-result"),
            viewModel.feed.searchSections.getValue(SearchFilter.SOCIETY).items.map { it.id }
        )

        viewModel.closeSearch()
        assertFalse(viewModel.feed.isSearchOpen)
        assertEquals(NewsCategory.POLITICS, viewModel.feed.category)
        assertEquals(listOf("ordinary"), viewModel.feed.items.map { it.id })
        assertEquals(4, repository.articlePageCalls)
    }

    @Test
    fun clearingTheSearchFieldShowsOnlyThePromptWithoutReloadingTheFeed() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            searchPages.getValue(null) += ArticlePage(listOf(summary("result")), null, 1)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.openSearch()
        viewModel.updateSearchInput("정책")
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)
        assertEquals("정책", viewModel.feed.activeSearchQuery)
        val calls = repository.articlePageCalls

        viewModel.updateSearchInput("")
        mainLooper.idle()
        assertEquals("", viewModel.feed.activeSearchQuery)
        assertTrue(viewModel.feed.searchSections.isEmpty())
        assertEquals(SearchFilter.ALL, viewModel.feed.searchFilter)
        assertFalse(viewModel.feed.isLoading)
        assertEquals(calls, repository.articlePageCalls)

        viewModel.closeSearch()
        assertEquals(listOf("ordinary"), viewModel.feed.items.map { it.id })
    }

    @Test
    fun searchRetriesOneFilterWithoutDiscardingTheOther() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            searchPages.getValue(null) += ArticlePage(
                listOf(summary("society-ok").copy(category = NewsCategory.SOCIETY)), null, 1
            )
            categoryFailures[NewsCategory.POLITICS] = IllegalStateException("정치 검색 실패")
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.openSearch()
        viewModel.updateSearchInput("검색 오류")
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)

        assertEquals(
            listOf("society-ok"),
            viewModel.feed.searchSections.getValue(SearchFilter.ALL).items.map { it.id }
        )

        viewModel.selectSearchFilter(SearchFilter.POLITICS)
        mainLooper.idle()
        assertEquals(
            "정치 검색 실패",
            viewModel.feed.searchSections.getValue(SearchFilter.POLITICS).error
        )

        repository.categoryFailures.remove(NewsCategory.POLITICS)
        repository.searchPages.getValue(NewsCategory.POLITICS) +=
            ArticlePage(listOf(summary("politics-retried")), null, 1)
        viewModel.retrySearch(SearchFilter.POLITICS)
        mainLooper.idle()

        assertEquals(
            listOf("politics-retried"),
            viewModel.feed.searchSections.getValue(SearchFilter.POLITICS).items.map { it.id }
        )
        assertEquals(
            listOf("society-ok"),
            viewModel.feed.searchSections.getValue(SearchFilter.ALL).items.map { it.id }
        )
    }

    @Test
    fun readerLoadsArticleAndAnalysisAndKeepsAnalysisPreference() {
        val repository = FakeNewsDataSource()
        val viewModel = NewsViewModel(repository)

        viewModel.openArticle("a")
        mainLooper.idle()
        assertEquals(NewsRoute.READER, viewModel.route)
        assertEquals("a", viewModel.reader.article?.id)
        assertEquals(StoredAnalysisState.READY, viewModel.reader.analysis?.state)
        assertFalse(viewModel.reader.isArticleLoading)
        assertFalse(viewModel.reader.isAnalysisLoading)

        viewModel.setAnalysisEnabled(false)
        assertFalse(viewModel.reader.analysisEnabled)
        assertEquals(false, repository.savedAnalysisEnabledValue)
        viewModel.closeReader()
        assertEquals(NewsRoute.FEED, viewModel.route)
    }

    @Test
    fun comparisonReaderNavigationPreservesSourceBackStackAndFocus() {
        val repository = FakeNewsDataSource()
        val viewModel = NewsViewModel(repository)
        val targetBody = "회사는 사업 영역을 넓히고 있다."
        val markerStart = targetBody.indexOf("넓히고 있다")
        val target = ComparisonArticleUi(
            articleId = "comparison",
            publisher = mbc,
            title = "비교 기사",
            publishedAt = "2026-10-31T13:00:00+09:00",
            originalUrl = "https://example.com/comparison",
            body = targetBody,
            evidence = listOf(
                ComparisonEvidenceUi(
                    scope = "BODY",
                    sentenceRange = TextRange(0, targetBody.length),
                    sentenceText = targetBody,
                    markerRange = TextRange(markerStart, markerStart + "넓히고 있다".length),
                    markerText = "넓히고 있다",
                    owner = "REPORTER",
                    speakerLabel = null,
                    summary = "속도 평가 없이 확장 상태를 전한다."
                )
            )
        )

        viewModel.openArticle("source")
        mainLooper.idle()
        viewModel.openComparisonArticle(
            target,
            sourceScrollOffset = 420,
            sourceSelectedFindingId = "source-finding"
        )
        mainLooper.idle()

        assertEquals(NewsRoute.READER, viewModel.route)
        assertEquals("comparison", viewModel.reader.article?.id)
        assertEquals(target.evidence.single().markerRange, viewModel.reader.comparisonFocus?.markerRange)

        viewModel.closeReader()
        assertFalse(viewModel.reader.isArticleLoading)
        assertEquals(NewsRoute.READER, viewModel.route)
        assertEquals("source", viewModel.reader.article?.id)
        assertEquals(null, viewModel.reader.comparisonFocus)
        assertEquals(420, viewModel.reader.initialScrollOffset)
        assertEquals("source-finding", viewModel.reader.initialSelectedFindingId)
        mainLooper.idle()

        viewModel.closeReader()
        assertEquals(NewsRoute.FEED, viewModel.route)
    }

    @Test
    fun reverseComparisonLinkRestoresThePreviousSnapshotWithoutDuplicatingHistory() {
        val repository = FakeNewsDataSource()
        val viewModel = NewsViewModel(repository)
        val target = comparisonArticle("comparison", "비교 표현")
        val reverseTarget = comparisonArticle("source", "원문 표현")

        viewModel.openArticle("source")
        mainLooper.idle()
        viewModel.openComparisonArticle(target, 420, "source-finding")
        mainLooper.idle()

        viewModel.openComparisonArticle(reverseTarget, 180, "comparison-finding")

        assertEquals("source", viewModel.reader.article?.id)
        assertEquals(420, viewModel.reader.initialScrollOffset)
        assertEquals("source-finding", viewModel.reader.initialSelectedFindingId)
        assertFalse(viewModel.reader.isArticleLoading)
        mainLooper.idle()

        viewModel.closeReader()
        assertEquals(NewsRoute.FEED, viewModel.route)
    }

    @Test
    fun cachedReaderDoesNotClaimOfflineWhileItsRefreshIsPending() {
        val refresh = CompletableDeferred<ArticleDetail>()
        val repository = FakeNewsDataSource().apply {
            cachedArticleValue = detail("cached-reader")
            cachedAnalysisValue = ArticleAnalysisUi(
                StoredAnalysisState.READY, "cached-v1", emptyList()
            )
            articleGate = refresh
        }
        val viewModel = NewsViewModel(repository)

        viewModel.openArticle("cached-reader")
        mainLooper.idle()

        assertEquals("cached-reader", viewModel.reader.article?.id)
        assertEquals("cached-v1", viewModel.reader.analysis?.version)
        assertFalse(viewModel.reader.isArticleLoading)
        assertFalse(viewModel.reader.isOffline)

        refresh.complete(detail("cached-reader"))
        mainLooper.idle()
        assertFalse(viewModel.reader.isOffline)
    }

    @Test
    fun cachedReaderAndAnalysisRemainVisibleWhenRefreshIsOffline() {
        val repository = FakeNewsDataSource().apply {
            cachedArticleValue = detail("cached-reader")
            cachedAnalysisValue = ArticleAnalysisUi(
                StoredAnalysisState.READY, "cached-v1", emptyList()
            )
            articleFailure = NewsApiException("NETWORK_ERROR", "연결할 수 없어요.")
        }
        val viewModel = NewsViewModel(repository)

        viewModel.openArticle("cached-reader")
        mainLooper.idle()

        assertEquals("cached-reader", viewModel.reader.article?.id)
        assertEquals("cached-v1", viewModel.reader.analysis?.version)
        assertTrue(viewModel.reader.isOffline)
        assertEquals(null, viewModel.reader.articleError)
    }

    @Test
    fun viewerChangeRefreshesPersonalizationWithoutLeavingReader() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("feed")), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.openArticle("reader")
        mainLooper.idle()

        viewModel.onViewerChanged()
        mainLooper.idle()

        assertEquals(NewsRoute.READER, viewModel.route)
        assertEquals("reader", viewModel.reader.article?.id)
        assertEquals(2, repository.articleCalls)
        assertEquals(1, repository.articlePageCalls)
    }

    @Test
    fun readerSeparatesArticleAndAnalysisFailuresAndResetRestoresDefaults() {
        val repository = FakeNewsDataSource().apply {
            articleFailure = IllegalStateException("기사 실패")
        }
        val viewModel = NewsViewModel(repository)
        viewModel.openArticle("bad")
        mainLooper.idle()
        assertEquals("기사 실패", viewModel.reader.articleError)

        repository.articleFailure = null
        repository.analysisFailure = NewsApiException("AUTH_REQUIRED", "분석 인증 만료")
        viewModel.retryReader()
        mainLooper.idle()
        assertEquals("분석 인증 만료", viewModel.reader.analysisError)
        assertTrue(viewModel.authExpired)

        viewModel.reset()
        assertEquals(NewsRoute.FEED, viewModel.route)
        assertTrue(viewModel.feed.items.isEmpty())
        assertFalse(viewModel.authExpired)
    }

    @Test
    fun savedRouteLoadsPagesAndReturnsToPreservedFeed() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            savedPages += ArticlePage(listOf(summary("saved-1").copy(isSaved = true)), "next")
            savedPages += ArticlePage(listOf(summary("saved-2").copy(isSaved = true)), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.openSaved()
        mainLooper.idle()
        assertEquals(NewsRoute.SAVED, viewModel.route)
        assertEquals(listOf("saved-1"), viewModel.saved.items.map { it.id })
        viewModel.loadMoreSaved()
        mainLooper.idle()
        assertEquals(listOf("saved-1", "saved-2"), viewModel.saved.items.map { it.id })

        viewModel.openArticle("saved-1")
        mainLooper.idle()
        viewModel.closeReader()
        assertEquals(NewsRoute.SAVED, viewModel.route)
        viewModel.closeSaved()
        assertEquals(NewsRoute.FEED, viewModel.route)
        assertEquals(listOf("ordinary"), viewModel.feed.items.map { it.id })
    }

    @Test
    fun offlineSavedRouteShowsOnlyCompleteLocalCopies() {
        val repository = FakeNewsDataSource().apply {
            offlineSavedPage = ArticlePage(
                listOf(summary("saved-offline").copy(isSaved = true)), null
            )
            savedPages += ArticlePage(
                listOf(summary("summary-only").copy(isSaved = true)), null
            )
        }
        val viewModel = NewsViewModel(repository)

        viewModel.openSaved()
        mainLooper.idle()
        assertEquals(listOf("summary-only"), viewModel.saved.items.map { it.id })
        viewModel.closeSaved()
        repository.savedFailure = NewsApiException("NETWORK_ERROR", "연결할 수 없어요.")
        viewModel.openSaved()
        mainLooper.idle()

        assertEquals(listOf("saved-offline"), viewModel.saved.items.map { it.id })
        assertTrue(viewModel.saved.isOffline)
        assertEquals(null, viewModel.saved.error)
    }

    @Test
    fun localSavedSnapshotDoesNotClaimOfflineWhileRefreshIsPending() {
        val pendingPage = CompletableDeferred<ArticlePage>()
        val repository = FakeNewsDataSource().apply {
            offlineSavedPage = ArticlePage(
                listOf(summary("saved-local").copy(isSaved = true)), null
            )
            savedPageGate = pendingPage
        }
        val viewModel = NewsViewModel(repository)

        viewModel.openSaved()
        mainLooper.idle()

        assertEquals(listOf("saved-local"), viewModel.saved.items.map { it.id })
        assertTrue(viewModel.saved.isRefreshing)
        assertFalse(viewModel.saved.isOffline)

        pendingPage.complete(
            ArticlePage(listOf(summary("saved-online").copy(isSaved = true)), null)
        )
        mainLooper.idle()
        assertEquals(listOf("saved-online"), viewModel.saved.items.map { it.id })
        assertFalse(viewModel.saved.isOffline)
    }

    @Test
    fun saveMutationChangesStateOnlyAfterSuccess() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("a")), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        repository.savedMutationFailure = IllegalStateException("저장 실패")
        viewModel.toggleSaved("a", false)
        mainLooper.runToEndOfTasks()
        assertFalse(viewModel.feed.items.single().isSaved)
        assertEquals("저장 실패", viewModel.saveError)

        repository.savedMutationFailure = null
        viewModel.toggleSaved("a", false)
        mainLooper.runToEndOfTasks()
        assertTrue(viewModel.feed.items.single().isSaved)
        assertEquals(listOf("a" to true), repository.savedMutations)
        assertEquals(null, viewModel.savedMutationArticleId)
    }

    @Test
    fun logoutClearsOnlyTheActiveSavedSnapshotAndAlwaysFinishes() {
        val repository = FakeNewsDataSource().apply {
            clearSavedFailure = IllegalStateException("cache failure")
        }
        val viewModel = NewsViewModel(repository)
        var finished = false

        viewModel.logout("user-a") { finished = true }
        mainLooper.idle()

        assertEquals(listOf("user-a"), repository.clearedSavedUsers)
        assertTrue(finished)
        assertEquals(NewsRoute.FEED, viewModel.route)
    }

    @Test
    fun anAnalysedLinkOpensItsArticleInTheReader() {
        val repository = FakeNewsDataSource().apply { linkResult = "linked" }
        val viewModel = NewsViewModel(repository)

        viewModel.openLinkAnalysis()
        viewModel.updateLinkInput("  https://n.news.naver.com/mnews/article/001/0000000001  ")
        viewModel.submitLinkAnalysis()
        mainLooper.idle()

        assertEquals(listOf("https://n.news.naver.com/mnews/article/001/0000000001"), repository.linkCalls)
        assertFalse(viewModel.link.isOpen)
        assertEquals(NewsRoute.READER, viewModel.route)
        assertEquals("linked", viewModel.reader.articleId)
    }

    @Test
    fun aFieldThatIsNotAnHttpLinkStaysInTheSheetWithoutARequest() {
        val repository = FakeNewsDataSource()
        val viewModel = NewsViewModel(repository)

        viewModel.openLinkAnalysis()
        viewModel.submitLinkAnalysis()
        assertEquals("기사 링크를 입력해 주세요.", viewModel.link.error)
        viewModel.updateLinkInput("ftp://example.com/a")
        viewModel.submitLinkAnalysis()
        mainLooper.idle()

        assertTrue(viewModel.link.isOpen)
        assertFalse(viewModel.link.isRunning)
        assertTrue(viewModel.link.error!!.contains("https://"))
        assertTrue(repository.linkCalls.isEmpty())
        assertEquals(NewsRoute.FEED, viewModel.route)
    }

    @Test
    fun aServerErrorStaysInTheSheetAndCanBeRetried() {
        val repository = FakeNewsDataSource().apply {
            linkFailure = NewsApiException("NO_SAME_EVENT", "같은 사건을 다룬 다른 언론사 기사를 찾지 못했어요.")
        }
        val viewModel = NewsViewModel(repository)

        viewModel.openLinkAnalysis()
        viewModel.updateLinkInput("https://example.com/news/1")
        viewModel.submitLinkAnalysis()
        mainLooper.idle()

        assertTrue(viewModel.link.isOpen)
        assertFalse(viewModel.link.isRunning)
        assertEquals("같은 사건을 다룬 다른 언론사 기사를 찾지 못했어요.", viewModel.link.error)
        assertEquals("https://example.com/news/1", viewModel.link.input)
        assertEquals(NewsRoute.FEED, viewModel.route)

        repository.linkFailure = null
        viewModel.submitLinkAnalysis()
        mainLooper.idle()
        assertEquals(NewsRoute.READER, viewModel.route)
    }

    @Test
    fun closingWhileTheServerAnalysesStopsWaiting() {
        val gate = CompletableDeferred<String>()
        val repository = FakeNewsDataSource().apply { linkGate = gate }
        val viewModel = NewsViewModel(repository)

        viewModel.openLinkAnalysis()
        viewModel.updateLinkInput("https://example.com/news/1")
        viewModel.submitLinkAnalysis()
        mainLooper.idle()
        assertTrue(viewModel.link.isRunning)

        viewModel.closeLinkAnalysis()
        gate.complete("late")
        mainLooper.idle()

        assertFalse(viewModel.link.isOpen)
        assertFalse(viewModel.link.isRunning)
        assertEquals(NewsRoute.FEED, viewModel.route)
    }

    /** specs/features/telemetry.md: fixed event names and codes, never typed or personal text. */
    private class RecordingTelemetry : Telemetry {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun record(event: String, params: Map<String, Any>) {
            events += event to params
        }
        fun names() = events.map { it.first }
        fun allValues() = events.flatMap { it.second.values }.map { it.toString() }
    }

    @Test
    fun browsingRecordsCategorySearchAndWhereAnArticleWasOpenedFrom() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("a")), null)
            pages += ArticlePage(listOf(summary("b")), null)
            searchPages.getValue(null) += ArticlePage(listOf(summary("s")), null)
        }
        val telemetry = RecordingTelemetry()
        val viewModel = NewsViewModel(repository, telemetry)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.selectCategory(NewsCategory.ECONOMY)
        mainLooper.idle()
        viewModel.openArticle("b")
        mainLooper.idle()

        assertTrue(TelemetryEvent.CATEGORY_SELECT to mapOf("category" to "ECONOMY") in telemetry.events)
        assertTrue(TelemetryEvent.ARTICLE_OPEN to mapOf("source" to "feed") in telemetry.events)
        val analysis = telemetry.events.single { it.first == TelemetryEvent.ANALYSIS_VIEW }.second
        assertEquals(0, analysis["findings"])
        assertFalse(telemetry.allValues().any { it == "b" || it.contains("기사") })
    }

    @Test
    fun linkAnalysisRecordsTheOutcomeButNeverTheLink() {
        val repository = FakeNewsDataSource().apply {
            linkFailure = NewsApiException("DAILY_LIMIT", "오늘 분석 한도를 모두 썼어요.")
        }
        val telemetry = RecordingTelemetry()
        val viewModel = NewsViewModel(repository, telemetry)

        viewModel.openLinkAnalysis()
        viewModel.updateLinkInput("ftp://example.com/a")
        viewModel.submitLinkAnalysis()
        viewModel.updateLinkInput("https://example.com/news/1")
        viewModel.submitLinkAnalysis()
        mainLooper.idle()
        repository.linkFailure = null
        viewModel.submitLinkAnalysis()
        mainLooper.idle()

        val results = telemetry.events.filter { it.first == TelemetryEvent.LINK_ANALYSIS_RESULT }.map { it.second }
        assertEquals(listOf("invalid", "error", "opened"), results.map { it["result"] })
        assertEquals("DAILY_LIMIT", results[1]["code"])
        assertTrue(TelemetryEvent.ARTICLE_OPEN to mapOf("source" to "link") in telemetry.events)
        assertFalse(telemetry.allValues().any { it.contains("example.com") || it.contains("http") })
    }

    @Test
    fun savingAndTheAnalysisToggleRecordOnlyTheirNewState() {
        val repository = FakeNewsDataSource()
        val telemetry = RecordingTelemetry()
        val viewModel = NewsViewModel(repository, telemetry)

        viewModel.toggleSaved("a", currentlySaved = false)
        mainLooper.idle()
        viewModel.setAnalysisEnabled(false)
        viewModel.setAnalysisEnabled(false)

        assertEquals(
            listOf(TelemetryEvent.SAVE_TOGGLE to mapOf("saved" to true),
                TelemetryEvent.ANALYSIS_TOGGLE to mapOf("enabled" to false)),
            telemetry.events
        )
    }

    private inner class FakeNewsDataSource : NewsDataSource {
        var linkResult = "linked"
        var linkFailure: Exception? = null
        var linkGate: CompletableDeferred<String>? = null
        val linkCalls = mutableListOf<String>()

        override suspend fun analyzeLink(url: String): String {
            linkCalls += url
            linkFailure?.let { throw it }
            return linkGate?.await() ?: linkResult
        }

        val pages = ArrayDeque<ArticlePage>()
        val searchPages = (listOf<NewsCategory?>(null) + NewsCategory.entries)
            .associateWith { ArrayDeque<ArticlePage>() }
        val savedPages = ArrayDeque<ArticlePage>()
        var pageFailure: Exception? = null
        val categoryFailures = mutableMapOf<NewsCategory?, Exception>()
        var articleFailure: Exception? = null
        var articleGate: CompletableDeferred<ArticleDetail>? = null
        var analysisFailure: Exception? = null
        var savedFailure: Exception? = null
        var savedPageGate: CompletableDeferred<ArticlePage>? = null
        var savedMutationFailure: Exception? = null
        var clearSavedFailure: Exception? = null
        var cachedArticleValue: ArticleDetail? = null
        var cachedAnalysisValue: ArticleAnalysisUi? = null
        var offlineSavedPage: ArticlePage? = null
        var savedCategoryValue = NewsCategory.POLITICS
        var savedAnalysisEnabledValue = true
        var followedCodes = emptySet<String>()
        var articlePageCalls = 0
        var articleCalls = 0
        val queries = mutableListOf<String?>()
        val scopes = mutableListOf<FeedScope>()
        val categories = mutableListOf<NewsCategory?>()
        val savedCategories = mutableListOf<NewsCategory>()
        val savedMutations = mutableListOf<Pair<String, Boolean>>()
        val clearedSavedUsers = mutableListOf<String>()
        val offlineCategories = mutableListOf<NewsCategory?>()

        override suspend fun publishers() = listOf(yna, mbc)
        override suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String> {
            followedCodes = publisherCodes
            return publisherCodes
        }
        override suspend fun articles(category: NewsCategory?, scope: FeedScope,
                                      cursor: String?, query: String?): ArticlePage {
            articlePageCalls += 1
            queries += query
            scopes += scope
            categories += category
            pageFailure?.let { throw it }
            if (query != null) categoryFailures[category]?.let { throw it }
            return if (query == null) pages.removeFirst()
            else searchPages.getValue(category).removeFirst()
        }
        override suspend fun article(articleId: String): ArticleDetail {
            articleCalls += 1
            articleFailure?.let { throw it }
            return articleGate?.await() ?: detail(articleId)
        }
        override suspend fun offlineSavedArticles(category: NewsCategory?): ArticlePage? {
            offlineCategories += category
            return offlineSavedPage
        }
        override suspend fun cachedArticle(articleId: String) = cachedArticleValue
        override suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi {
            analysisFailure?.let { throw it }
            return ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", emptyList())
        }
        override suspend fun cachedAnalysis(article: ArticleDetail) = cachedAnalysisValue
        override suspend fun savedArticles(cursor: String?): ArticlePage {
            savedFailure?.let { throw it }
            savedPageGate?.let { return it.await() }
            return savedPages.removeFirst()
        }
        override suspend fun setArticleSaved(articleId: String, saved: Boolean) {
            savedMutationFailure?.let { throw it }
            savedMutations += articleId to saved
        }
        override suspend fun clearSavedCache(userId: String) {
            clearedSavedUsers += userId
            clearSavedFailure?.let { throw it }
        }
        override fun savedCategory() = savedCategoryValue
        override fun savedAnalysisEnabled() = savedAnalysisEnabledValue
        override fun saveCategory(category: NewsCategory) {
            savedCategoryValue = category
            savedCategories += category
        }
        override fun saveAnalysisEnabled(enabled: Boolean) {
            savedAnalysisEnabledValue = enabled
        }
    }

    private fun comparisonArticle(articleId: String, marker: String): ComparisonArticleUi {
        val body = "$marker 이 포함된 기사입니다."
        return ComparisonArticleUi(
            articleId = articleId,
            publisher = mbc,
            title = "비교 기사 $articleId",
            publishedAt = "2026-10-31T13:00:00+09:00",
            originalUrl = "https://example.com/$articleId",
            body = body,
            evidence = listOf(
                ComparisonEvidenceUi(
                    scope = "BODY",
                    sentenceRange = TextRange(0, body.length),
                    sentenceText = body,
                    markerRange = TextRange(0, marker.length),
                    markerText = marker,
                    owner = "REPORTER",
                    speakerLabel = null,
                    summary = "표현을 비교합니다."
                )
            )
        )
    }
}
