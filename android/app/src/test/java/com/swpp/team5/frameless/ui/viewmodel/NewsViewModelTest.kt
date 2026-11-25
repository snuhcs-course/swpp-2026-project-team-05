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
import com.swpp.team5.frameless.data.StoryPage
import com.swpp.team5.frameless.data.StorySummary
import com.swpp.team5.frameless.data.TextRange
import com.swpp.team5.frameless.telemetry.Telemetry
import com.swpp.team5.frameless.telemetry.TelemetryEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    @Test
    fun qualifyingReadNeedsThreeContinuousForegroundSecondsAndOnlySendsOnce() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val reads = mutableListOf<String>()
        val tracker = QualifiedReadTracker(scope) { reads += it }
        tracker.update("a", true, "guest")
        mainLooper.idleFor(2, TimeUnit.SECONDS)
        assertTrue(reads.isEmpty())
        tracker.update(null, false, "guest")
        mainLooper.idleFor(5, TimeUnit.SECONDS)
        assertTrue(reads.isEmpty())
        tracker.update("a", true, "guest")
        mainLooper.idleFor(3, TimeUnit.SECONDS)
        assertEquals(listOf("a"), reads)
        tracker.update(null, false, "guest")
        tracker.update("a", true, "account")
        mainLooper.idleFor(4, TimeUnit.SECONDS)
        assertEquals(listOf("a"), reads)
        tracker.newVisit()
        tracker.update("b", true, "account")
        mainLooper.idleFor(3, TimeUnit.SECONDS)
        assertEquals(listOf("a", "b"), reads)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun readIdentityChangesRestartPendingTimerAndAnalyticsFailureIsSilent() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        var attempts = 0
        val tracker = QualifiedReadTracker(scope) { attempts++; throw IllegalStateException("offline") }
        tracker.update("a", true, null)
        mainLooper.idleFor(2, TimeUnit.SECONDS)
        tracker.update("a", true, "account")
        mainLooper.idleFor(2, TimeUnit.SECONDS)
        assertEquals(0, attempts)
        mainLooper.idleFor(1, TimeUnit.SECONDS)
        assertEquals(1, attempts)
        mainLooper.idleFor(10, TimeUnit.SECONDS)
        assertEquals(1, attempts)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun trendsReuseFreshCacheBackOffFailuresAndDiscardExpiredResults() {
        var now = java.time.Instant.parse("2026-11-25T09:00:00Z")
        val repository = FakeNewsDataSource().apply {
            trendResult = com.swpp.team5.frameless.data.TrendPage(now.toString(), false, emptyList())
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val trends = RecentTrends(repository, scope) { now }
        trends.load()
        mainLooper.idle()
        trends.load()
        mainLooper.idle()
        assertEquals(1, repository.trendCalls)
        now = now.plusSeconds(300)
        repository.trendFailure = IllegalStateException("unavailable")
        trends.load()
        mainLooper.idle()
        assertTrue(trends.state.page!!.isStale)
        assertFalse(trends.state.loading)
        trends.load(force = true)
        mainLooper.idle()
        assertEquals(2, repository.trendCalls)
        now = now.plusSeconds(601)
        trends.load()
        mainLooper.idle()
        assertEquals(null, trends.state.page)
        trends.reset()
        assertEquals(null, trends.state.error)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
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
    fun publisherFailureEndsLoadingAndCanBeRetried() {
        val repository = FakeNewsDataSource().apply {
            publisherFailure = NewsApiException("NETWORK_ERROR", "연결 오류")
            pages += ArticlePage(listOf(summary("public")), null)
            pages += ArticlePage(listOf(summary("public")), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        assertFalse(viewModel.feed.isLoadingPublishers)
        assertEquals("연결 오류", viewModel.feed.publisherDirectoryError)
        assertEquals("public", viewModel.feed.items.single().id)

        repository.publisherFailure = null
        viewModel.retryFeed()
        mainLooper.idle()

        assertEquals(listOf(yna, mbc), viewModel.feed.publishers)
        assertFalse(viewModel.feed.isLoadingPublishers)
        assertEquals(null, viewModel.feed.publisherDirectoryError)
    }

    @Test
    fun homeLoadsPublicAndSubscribedStoriesIndependently() {
        val repository = FakeNewsDataSource().apply {
            publisherItems = listOf(yna.copy(isFollowed = true), mbc)
            pages += ArticlePage(emptyList(), null)
            storyHandler = { storyPage("public") }
            publisherHandler = { ArticlePage(listOf(summary("subscribed")), null) }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.ensureHomeLoaded()
        mainLooper.idle()

        assertEquals(listOf(FeedScope.ALL), repository.storyScopes)
        assertEquals(listOf("yna"), repository.publisherRequests)
        assertEquals("public", viewModel.discovery.homeStories.single().representativeArticle.id)
        assertEquals("subscribed", viewModel.discovery.subscribedArticles.single().id)
        assertFalse(viewModel.discovery.isSubscribedLoading)
    }

    @Test
    fun homeDoesNotRequestSubscribedStoriesWithoutSubscriptions() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(emptyList(), null)
            storyHandler = { storyPage("public") }
            publisherHandler = { ArticlePage(listOf(summary("subscribed")), null) }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.ensureHomeLoaded()
        mainLooper.idle()

        assertEquals(listOf(FeedScope.ALL), repository.storyScopes)
        assertTrue(repository.publisherRequests.isEmpty())
        assertTrue(viewModel.discovery.subscribedArticles.isEmpty())

        viewModel.replaceFollowedPublishers(setOf("yna"))
        mainLooper.idle()

        assertEquals(listOf("yna"), repository.publisherRequests)
        assertEquals("public", viewModel.discovery.homeStories.single().representativeArticle.id)
        assertTrue(viewModel.discovery.subscribedArticles.isNotEmpty())

        viewModel.replaceFollowedPublishers(emptySet())
        mainLooper.idle()
        assertTrue(viewModel.discovery.subscribedArticles.isEmpty())
    }

    @Test
    fun subscribedHomeFailureKeepsPublicStories() {
        val repository = FakeNewsDataSource().apply {
            publisherItems = listOf(yna.copy(isFollowed = true), mbc)
            pages += ArticlePage(emptyList(), null)
            storyHandler = { storyPage("public") }
            publisherHandler = { throw NewsApiException("NETWORK_ERROR", "연결 오류") }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.ensureHomeLoaded()
        mainLooper.idle()

        assertEquals("public", viewModel.discovery.homeStories.single().representativeArticle.id)
        assertEquals("연결 오류", viewModel.discovery.subscribedError)
        assertEquals(null, viewModel.discovery.homeError)
    }

    @Test
    fun homePublisherSelectionUsesExactQueriesAndCachedPages() {
        val repository = FakeNewsDataSource().apply {
            publisherItems = listOf(yna.copy(isFollowed = true), mbc.copy(isFollowed = true))
            pages += ArticlePage(emptyList(), null)
            storyHandler = { storyPage("public") }
            publisherHandler = { code -> ArticlePage(listOf(summary(code)), null) }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.ensureHomeLoaded()
        mainLooper.idle()
        assertEquals("yna", viewModel.discovery.selectedHomePublisherCode)

        viewModel.selectHomePublisher("mbc")
        mainLooper.idle()
        assertEquals("mbc", viewModel.discovery.subscribedArticles.single().id)
        viewModel.selectHomePublisher("yna")
        mainLooper.idle()
        assertEquals("yna", viewModel.discovery.subscribedArticles.single().id)
        assertEquals(listOf("yna", "mbc"), repository.publisherRequests)

        viewModel.selectHomePublisher("unknown")
        viewModel.selectHomePublisher("yna")
        mainLooper.idle()
        assertEquals(listOf("yna", "mbc"), repository.publisherRequests)

        viewModel.replaceFollowedPublishers(setOf("mbc"))
        mainLooper.idle()
        assertEquals("mbc", viewModel.discovery.selectedHomePublisherCode)
        assertEquals(listOf("yna", "mbc", "mbc"), repository.publisherRequests)
    }

    @Test
    fun lateHomePublisherResponseCannotReplaceTheSelectedOutlet() {
        val gate = CompletableDeferred<ArticlePage>()
        val repository = FakeNewsDataSource().apply {
            publisherItems = listOf(yna.copy(isFollowed = true), mbc.copy(isFollowed = true))
            pages += ArticlePage(emptyList(), null)
            storyHandler = { storyPage("public") }
            publisherHandler = { code ->
                if (code == "yna") withContext(NonCancellable) { gate.await() }
                else ArticlePage(listOf(summary(code)), null)
            }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.ensureHomeLoaded()
        mainLooper.idle()
        viewModel.selectHomePublisher("mbc")
        mainLooper.idle()
        gate.complete(ArticlePage(listOf(summary("late-yna")), null))
        mainLooper.idle()

        assertEquals("mbc", viewModel.discovery.selectedHomePublisherCode)
        assertEquals("mbc", viewModel.discovery.subscribedArticles.single().id)
        assertFalse(viewModel.discovery.isSubscribedLoading)
    }

    private fun storyPage(id: String): StoryPage {
        val article = summary(id)
        return StoryPage(listOf(StorySummary("cluster:$id", article, 1,
            listOf(article.publisher), article.publishedAt)), null)
    }

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
    fun publisherChangesAreSerializedWithoutDroppingOtherPublishers() {
        val first = CompletableDeferred<Set<String>>()
        val second = CompletableDeferred<Set<String>>()
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(emptyList(), null)
            sourceWriteHandler = { if (sourceWrites.size == 1) first.await() else second.await() }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.setPublisherSubscription("yna", true)
        mainLooper.idle()
        viewModel.setPublisherSubscription("yna", true)
        viewModel.setPublisherSubscription("mbc", true)
        mainLooper.idle()
        assertEquals(listOf(setOf("yna")), repository.sourceWrites)
        assertEquals(setOf("yna", "mbc"), viewModel.feed.sourceMutationPublisherCodes)
        assertTrue(viewModel.feed.publishers.none { it.isFollowed })

        first.complete(setOf("yna"))
        mainLooper.idle()
        assertEquals(listOf(setOf("yna"), setOf("yna", "mbc")), repository.sourceWrites)
        assertEquals(setOf("mbc"), viewModel.feed.sourceMutationPublisherCodes)
        second.complete(setOf("yna", "mbc"))
        mainLooper.idle()
        assertEquals(setOf("yna", "mbc"), repository.followedCodes)
        assertTrue(viewModel.feed.publishers.all { it.isFollowed })
        assertTrue(viewModel.feed.sourceMutationPublisherCodes.isEmpty())
        assertFalse(viewModel.feed.isSavingSources)
    }

    @Test
    fun failedPublisherChangeDoesNotLeakIntoTheNextWrite() {
        val first = CompletableDeferred<Set<String>>()
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(emptyList(), null)
            sourceWriteHandler = { codes -> if (sourceWrites.size == 1) first.await() else codes }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.setPublisherSubscription("yna", true)
        mainLooper.idle()
        viewModel.setPublisherSubscription("mbc", true)
        first.completeExceptionally(IllegalStateException("연합뉴스 저장 실패"))
        mainLooper.idle()
        assertEquals(listOf(setOf("yna"), setOf("mbc")), repository.sourceWrites)
        assertFalse(viewModel.feed.publishers.single { it.code == "yna" }.isFollowed)
        assertTrue(viewModel.feed.publishers.single { it.code == "mbc" }.isFollowed)
        assertEquals(mapOf("yna" to "연합뉴스 저장 실패"), viewModel.feed.sourceSaveErrors)
        viewModel.setPublisherSubscription("yna", true)
        mainLooper.idle()
        assertEquals(setOf("yna", "mbc"), repository.followedCodes)
        assertTrue(viewModel.feed.sourceSaveErrors.isEmpty())
    }

    @Test
    fun closingSearchDoesNotRestoreStaleSubscriptionState() {
        val gate = CompletableDeferred<Set<String>>()
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            sourceWriteHandler = { gate.await() }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.setPublisherSubscription("yna", true)
        mainLooper.idle()
        viewModel.openSearch()
        viewModel.closeSearch()
        assertEquals(setOf("yna"), viewModel.feed.sourceMutationPublisherCodes)
        viewModel.openSearch()
        gate.completeExceptionally(IllegalStateException("저장 실패"))
        mainLooper.idle()
        viewModel.closeSearch()
        assertFalse(viewModel.feed.isSavingSources)
        assertTrue(viewModel.feed.sourceMutationPublisherCodes.isEmpty())
        assertEquals(mapOf("yna" to "저장 실패"), viewModel.feed.sourceSaveErrors)
        assertEquals(listOf("ordinary"), viewModel.feed.items.map { it.id })
    }

    @Test
    fun viewerChangeCancelsPendingPersonalMutations() {
        val source = CompletableDeferred<Set<String>>()
        val bookmark = CompletableDeferred<Unit>()
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("a")), null)
            pages += ArticlePage(listOf(summary("a")), null)
            publisherItems = listOf(yna.copy(isFollowed = true), mbc)
            publisherHandler = { ArticlePage(emptyList(), null) }
            sourceWriteHandler = { withContext(NonCancellable) { source.await() } }
            savedMutationHandler = { _, _ -> withContext(NonCancellable) { bookmark.await() } }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.openArticle("a")
        mainLooper.idle()
        viewModel.openPublisher(repository.publisherItems.first())
        viewModel.setPublisherSubscription("yna", false)
        viewModel.toggleSaved("a", false)
        mainLooper.idle()
        viewModel.setPublisherSubscription("mbc", true)
        repository.publisherItems = listOf(yna, mbc)
        viewModel.onViewerChanged()
        mainLooper.idle()
        source.complete(emptySet())
        bookmark.complete(Unit)
        mainLooper.idle()
        assertEquals(1, repository.sourceWrites.size)
        assertTrue(viewModel.feed.publishers.none { it.isFollowed })
        assertFalse(viewModel.discovery.activePublisher!!.isFollowed)
        assertFalse(viewModel.feed.items.single().isSaved)
        assertFalse(viewModel.reader.article!!.isSaved)
        assertTrue(viewModel.savedMutationArticleIds.isEmpty())
        assertTrue(viewModel.feed.sourceMutationPublisherCodes.isEmpty())
        assertTrue(viewModel.saveErrors.isEmpty())
        assertTrue(viewModel.feed.sourceSaveErrors.isEmpty())
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
        assertTrue(viewModel.feed.isSearchPending)
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
    fun singleCharacterSearchSupportsDebounceAndImmediateSubmit() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            searchPages.getValue(null) += ArticlePage(listOf(summary("one-character")), null, 1)
            searchPages.getValue(null) += ArticlePage(emptyList(), null, 0)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.openSearch()
        viewModel.updateSearchInput(" 정 ")
        mainLooper.idleFor(299, TimeUnit.MILLISECONDS)
        assertEquals(1, repository.articlePageCalls)
        mainLooper.idleFor(2, TimeUnit.MILLISECONDS)
        assertEquals("정", viewModel.feed.activeSearchQuery)
        assertEquals("정", repository.queries.last())
        assertEquals(FeedScope.ALL, repository.scopes.last())
        assertEquals(null, repository.categories.last())
        assertEquals(listOf("one-character"), viewModel.feed.searchSections.getValue(SearchFilter.ALL).items.map { it.id })

        viewModel.updateSearchInput("뿡")
        viewModel.submitSearch()
        mainLooper.idle()
        assertEquals("뿡", repository.queries.last())
        assertEquals("뿡", viewModel.feed.searchInput)
        assertEquals(0, viewModel.feed.searchSections.getValue(SearchFilter.ALL).totalCount)
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)
        assertEquals(3, repository.articlePageCalls)
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
        assertEquals("저장 실패", viewModel.saveErrors["a"])

        repository.savedMutationFailure = null
        viewModel.toggleSaved("a", false)
        mainLooper.runToEndOfTasks()
        assertTrue(viewModel.feed.items.single().isSaved)
        assertEquals(listOf("a" to true), repository.savedMutations)
        assertTrue(viewModel.savedMutationArticleIds.isEmpty())
    }

    @Test
    fun savedMutationsAndErrorsAreIsolatedPerArticle() {
        val a = CompletableDeferred<Unit>()
        val b = CompletableDeferred<Unit>()
        val gates = mutableMapOf("a" to a, "b" to b)
        val attempts = mutableListOf<String>()
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("a"), summary("b")), null)
            savedMutationHandler = { id, _ -> attempts += id; gates[id]?.await(); Unit }
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.openArticle("a")
        mainLooper.idle()
        viewModel.toggleSaved("a", false)
        viewModel.toggleSaved("a", false)
        mainLooper.idle()
        viewModel.openRelatedArticle("b", 100, null)
        mainLooper.idle()
        viewModel.toggleSaved("b", false)
        mainLooper.idle()
        assertEquals(listOf("a", "b"), attempts)
        assertEquals(setOf("a", "b"), viewModel.savedMutationArticleIds)
        b.complete(Unit)
        mainLooper.idle()
        assertTrue(viewModel.reader.article!!.isSaved)
        a.completeExceptionally(IllegalStateException("A 저장 실패"))
        mainLooper.idle()
        assertEquals(mapOf("a" to "A 저장 실패"), viewModel.saveErrors)
        assertTrue(viewModel.reader.article!!.isSaved)
        gates.remove("a")
        viewModel.toggleSaved("a", false)
        mainLooper.idle()
        // Hold revalidation to check the immediate Back snapshot independently.
        repository.articleGate = CompletableDeferred()
        viewModel.closeReader()
        assertEquals("a", viewModel.reader.articleId)
        assertTrue(viewModel.reader.article!!.isSaved)
        assertTrue(viewModel.saveErrors.isEmpty())
        assertTrue(viewModel.savedMutationArticleIds.isEmpty())
        repository.articleGate!!.complete(detail("a").copy(isSaved = true))
        mainLooper.idle()
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
        var trendResult = com.swpp.team5.frameless.data.TrendPage("2026-11-25T09:00:00Z", false, emptyList())
        var trendFailure: Exception? = null
        var trendCalls = 0
        override suspend fun trends(): com.swpp.team5.frameless.data.TrendPage {
            trendCalls++
            trendFailure?.let { throw it }
            return trendResult
        }
        var publisherItems = listOf(yna, mbc)
        var publisherFailure: Exception? = null
        var storyHandler: (suspend (FeedScope) -> StoryPage)? = null
        var publisherHandler: (suspend (String) -> ArticlePage)? = null
        val publisherRequests = mutableListOf<String>()
        val storyScopes = mutableListOf<FeedScope>()

        override suspend fun stories(category: NewsCategory?, scope: FeedScope, cursor: String?): StoryPage {
            storyScopes += scope
            return storyHandler?.invoke(scope) ?: super.stories(category, scope, cursor)
        }

        override suspend fun publisherArticles(publisherCode: String, cursor: String?): ArticlePage {
            publisherRequests += publisherCode
            return publisherHandler?.invoke(publisherCode) ?: super.publisherArticles(publisherCode, cursor)
        }
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
        val sourceWrites = mutableListOf<Set<String>>()
        var sourceWriteHandler: (suspend (Set<String>) -> Set<String>)? = null
        var savedMutationHandler: (suspend (String, Boolean) -> Unit)? = null
        var articlePageCalls = 0
        var articleCalls = 0
        val queries = mutableListOf<String?>()
        val scopes = mutableListOf<FeedScope>()
        val categories = mutableListOf<NewsCategory?>()
        val savedCategories = mutableListOf<NewsCategory>()
        val savedMutations = mutableListOf<Pair<String, Boolean>>()
        val clearedSavedUsers = mutableListOf<String>()
        val offlineCategories = mutableListOf<NewsCategory?>()

        override suspend fun publishers(): List<PublisherItem> {
            publisherFailure?.let { throw it }
            return publisherItems
        }
        override suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String> {
            sourceWrites += publisherCodes.toSet()
            val result = sourceWriteHandler?.invoke(publisherCodes) ?: publisherCodes
            followedCodes = result
            return result
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
            savedMutationHandler?.invoke(articleId, saved)
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
