package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.FeedScope
import com.swpp.team5.frameless.data.NewsApiException
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsDataSource
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StorySummary
import com.swpp.team5.frameless.data.TextRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal enum class NewsRoute { FEED, SAVED, READER }

internal enum class NewsSection(val label: String, val category: NewsCategory?) {
    NEWS("전체 뉴스", null),
    PUBLISHERS("언론사", null),
    POLITICS("정치", NewsCategory.POLITICS),
    SOCIETY("사회", NewsCategory.SOCIETY),
    ECONOMY("경제", NewsCategory.ECONOMY)
}

internal enum class SearchFilter(val category: NewsCategory?, val label: String) {
    ALL(null, "전체"),
    POLITICS(NewsCategory.POLITICS, "정치"),
    SOCIETY(NewsCategory.SOCIETY, "사회"),
    ECONOMY(NewsCategory.ECONOMY, "경제")
}

private val reviewedExampleOrder = listOf(
    "HD현대일렉트릭, 3분기 매출 1조1799억원 '역대 최대'",
    "에버랜드 막내 판다 '슈바오' 30일 첫 나들이…쌍둥이 언니들은 작별 준비",
    "[한 손 뉴스] 변호사 업무 재개한 홍준표, 대선 경선 관계자 변호 맡아(종합)",
    "홍대 12만·이태원 2만 인파에도 '우측통행'…\"즐기는 것 좋지만 안전 최선\"",
    "한화, 美 해군 함정 건조 '첫' 수주…필리조선소 인수 2년만",
    "'우주산업 거점' 고흥 우주발사체 국가산단 조성 속도"
).withIndex().associate { it.value to it.index }

private fun orderFeedItems(
    items: List<ArticleSummary>,
    scope: FeedScope,
    query: String?
): List<ArticleSummary> {
    if (scope != FeedScope.ALL || !query.isNullOrBlank()) return items
    return items.withIndex().sortedWith(
        compareBy<IndexedValue<ArticleSummary>>(
            { reviewedExampleOrder[it.value.title] ?: Int.MAX_VALUE },
            { if (reviewedExampleOrder.containsKey(it.value.title)) 0 else it.index }
        )
    ).map { it.value }
}

internal data class NewsFeedState(
    val publishers: List<PublisherItem> = emptyList(),
    val isLoadingPublishers: Boolean = false,
    val publisherDirectoryError: String? = null,
    val items: List<ArticleSummary> = emptyList(),
    val category: NewsCategory = NewsCategory.POLITICS,
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val scope: FeedScope = FeedScope.ALL,
    val isSavingSources: Boolean = false,
    val sourceSaveError: String? = null,
    val sourceMutationPublisherCodes: Set<String> = emptySet(),
    val sourceSaveErrors: Map<String, String> = emptyMap(),
    val isSearchOpen: Boolean = false,
    val searchInput: String = "",
    val activeSearchQuery: String = "",
    val isSearchPending: Boolean = false,
    val searchSections: Map<SearchFilter, SearchSectionState> = emptyMap(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val nextPageError: String? = null
)

internal data class SearchSectionState(
    val filter: SearchFilter,
    val items: List<ArticleSummary> = emptyList(),
    val totalCount: Int = 0,
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPageError: String? = null
)

private fun initialSearchSections(loading: Boolean = false): Map<SearchFilter, SearchSectionState> =
    mapOf(SearchFilter.ALL to SearchSectionState(filter = SearchFilter.ALL, isLoading = loading))

internal data class ArticleReaderState(
    val articleId: String = "",
    val article: ArticleDetail? = null,
    val analysis: ArticleAnalysisUi? = null,
    val analysisEnabled: Boolean = true,
    val isArticleLoading: Boolean = false,
    val isAnalysisLoading: Boolean = false,
    val isOffline: Boolean = false,
    val articleError: String? = null,
    val analysisError: String? = null,
    val comparisonFocus: ReaderFocus? = null,
    val initialScrollOffset: Int = 0,
    val initialSelectedFindingId: String? = null
)

internal data class ReaderFocus(
    val scope: String,
    val markerRange: TextRange
)

private data class ReaderDestination(
    val snapshot: ArticleReaderState
)

private data class PublisherSubscriptionUpdate(
    val changes: Map<String, Boolean>,
    val onSuccess: () -> Unit = {}
)

internal data class SavedArticlesState(
    val items: List<ArticleSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val nextPageError: String? = null
)

internal data class PortalDiscoveryState(
    val homeStories: List<StorySummary> = emptyList(),
    val homeNextCursor: String? = null,
    val isHomeLoading: Boolean = false,
    val isHomeRefreshing: Boolean = false,
    val homeError: String? = null,
    val subscribedArticles: List<ArticleSummary> = emptyList(),
    val selectedHomePublisherCode: String? = null,
    val isSubscribedLoading: Boolean = false,
    val subscribedError: String? = null,
    val newsSection: NewsSection = NewsSection.NEWS,
    val newsStories: List<StorySummary> = emptyList(),
    val newsNextCursor: String? = null,
    val isNewsLoading: Boolean = false,
    val isNewsRefreshing: Boolean = false,
    val newsError: String? = null,
    val isLoadingMore: Boolean = false,
    val nextPageError: String? = null,
    val activePublisher: PublisherItem? = null,
    val publisherArticles: List<ArticleSummary> = emptyList(),
    val publisherNextCursor: String? = null,
    val isPublisherLoading: Boolean = false,
    val publisherError: String? = null
)

internal fun isHttpLink(value: String): Boolean = try {
    val uri = java.net.URI(value.trim())
    (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
} catch (_: Exception) {
    false
}

/** The feed's link dialog: the pasted link, whether the server is analysing it, and the last error. */
internal data class LinkAnalysisState(
    val isOpen: Boolean = false,
    val input: String = "",
    val isRunning: Boolean = false,
    val error: String? = null
)

internal class NewsViewModel(private val repository: NewsDataSource) : ViewModel() {
    val trends = RecentTrends(repository, viewModelScope)
    val reading = QualifiedReadTracker(viewModelScope, repository::recordView)
    var route by mutableStateOf(NewsRoute.FEED)
        private set
    var link by mutableStateOf(LinkAnalysisState())
        private set
    private var linkJob: Job? = null
    var feed by mutableStateOf(
        NewsFeedState(
            category = repository.savedCategory()
        )
    )
        private set
    var reader by mutableStateOf(ArticleReaderState(analysisEnabled = repository.savedAnalysisEnabled()))
        private set
    var saved by mutableStateOf(SavedArticlesState())
        private set
    var discovery by mutableStateOf(PortalDiscoveryState())
        private set
    var savedMutationArticleIds by mutableStateOf(emptySet<String>())
        private set
    var saveErrors by mutableStateOf(emptyMap<String, String>())
        private set
    var authExpired by mutableStateOf(false)
        private set

    private var initialized = false
    private var feedJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var feedBeforeSearch: NewsFeedState? = null
    private var readerJob: Job? = null
    private var savedJob: Job? = null
    private var homeJob: Job? = null
    private var subscribedHomeJob: Job? = null
    private val homePublisherCache = mutableMapOf<String, List<ArticleSummary>>()
    private var newsStoryJob: Job? = null
    private var publisherJob: Job? = null
    private var sourceSaveJob: Job? = null
    private val sourceUpdates = ArrayDeque<PublisherSubscriptionUpdate>()
    private val savedMutationJobs = mutableMapOf<String, Job>()
    private var homeRequested = false
    private var readerOrigin = NewsRoute.FEED
    private val readerHistory = ArrayDeque<ReaderDestination>()

    fun initialize() {
        if (initialized) return
        initialized = true
        feed = feed.copy(isLoading = true, error = null,
            isLoadingPublishers = true, publisherDirectoryError = null)
        feedJob = viewModelScope.launch {
            try {
                val publishers = repository.publishers()
                feed = feed.copy(publishers = publishers, isLoadingPublishers = false)
                if (homeRequested) loadHome(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                feed = feed.copy(isLoadingPublishers = false,
                    publisherDirectoryError = failure.message ?: "언론사를 불러오지 못했어요.")
            }
            loadFirstPage()
        }
    }

    fun reset() {
        cancelPersonalMutations()
        trends.reset()
        reading.newVisit()
        feedJob?.cancel()
        searchDebounceJob?.cancel()
        readerJob?.cancel()
        savedJob?.cancel()
        homeJob?.cancel()
        subscribedHomeJob?.cancel()
        homePublisherCache.clear()
        newsStoryJob?.cancel()
        publisherJob?.cancel()
        feedBeforeSearch = null
        initialized = false
        route = NewsRoute.FEED
        feed = NewsFeedState(
            category = repository.savedCategory()
        )
        reader = ArticleReaderState(analysisEnabled = repository.savedAnalysisEnabled())
        saved = SavedArticlesState()
        discovery = PortalDiscoveryState()
        homeRequested = false
        readerOrigin = NewsRoute.FEED
        readerHistory.clear()
        authExpired = false
    }

    fun onViewerChanged() {
        cancelPersonalMutations()
        feedBeforeSearch = null
        trends.reset()
        if (homeRequested) trends.load()
        feedJob?.cancel()
        homeJob?.cancel()
        subscribedHomeJob?.cancel()
        newsStoryJob?.cancel()
        homePublisherCache.clear()
        val currentRoute = route
        val currentArticleId = reader.articleId
        val currentFocus = reader.comparisonFocus
        feed = feed.copy(
            publishers = feed.publishers.map { it.copy(isFollowed = false) },
            items = feed.items.map { it.copy(isSaved = false) },
            searchSections = feed.searchSections.mapValues { (_, section) ->
                section.copy(items = section.items.map { it.copy(isSaved = false) })
            }
        )
        reader = reader.copy(article = reader.article?.copy(isSaved = false))
        saved = SavedArticlesState()
        discovery = discovery.copy(
            activePublisher = discovery.activePublisher?.copy(isFollowed = false),
            publisherArticles = discovery.publisherArticles.map { it.copy(isSaved = false) },
            homeStories = emptyList(),
            homeNextCursor = null,
            homeError = null,
            isHomeLoading = false,
            isHomeRefreshing = false,
            subscribedArticles = emptyList(),
            selectedHomePublisherCode = null,
            isSubscribedLoading = false,
            subscribedError = null,
            newsStories = emptyList(),
            newsNextCursor = null,
            newsError = null,
            isNewsLoading = false,
            isNewsRefreshing = false,
            isLoadingMore = false
        )
        authExpired = false
        initialized = false
        initialize()
        when {
            currentRoute == NewsRoute.READER && currentArticleId.isNotBlank() ->
                loadReader(currentArticleId, currentFocus, isNewVisit = false)
            currentRoute == NewsRoute.SAVED -> route = NewsRoute.FEED
        }
    }

    fun retryFeed() {
        if (feed.isSearchOpen) {
            reloadForFilters()
            return
        }
        initialized = false
        initialize()
    }

    fun refresh() {
        if (feed.isRefreshing) return
        feedJob?.cancel()
        val query = feed.activeSearchQuery.takeIf { feed.isSearchOpen && it.isNotBlank() }
        feed = feed.copy(
            isRefreshing = true,
            error = null,
            searchSections = if (query == null) feed.searchSections else feed.searchSections +
                (feed.searchFilter to (
                    feed.searchSections[feed.searchFilter]
                        ?: SearchSectionState(feed.searchFilter)
                    ).copy(isLoading = true, error = null, nextPageError = null))
        )
        feedJob = viewModelScope.launch {
            if (query != null) loadSearchFilter(feed.searchFilter, query) else loadFirstPage()
        }
    }

    fun selectCategory(category: NewsCategory) {
        if (feed.isSearchOpen) return
        if (feed.category == category) return
        feed = feed.copy(category = category)
        repository.saveCategory(category)
        reloadForFilters()
    }

    fun selectSearchFilter(filter: SearchFilter) {
        if (!feed.isSearchOpen || feed.searchFilter == filter) return
        val query = feed.activeSearchQuery
        feed = feed.copy(searchFilter = filter)
        if (query.isBlank() || feed.searchSections[filter] != null) return
        feed = feed.copy(
            searchSections = feed.searchSections +
                (filter to SearchSectionState(filter = filter, isLoading = true))
        )
        viewModelScope.launch { loadSearchFilter(filter, query) }
    }

    fun selectScope(scope: FeedScope) {
        if (feed.scope == scope) return
        if (feed.isSearchOpen) return
        feed = feed.copy(scope = scope)
        reloadForFilters()
    }

    fun replaceFollowedPublishers(codes: Set<String>, onSuccess: () -> Unit = {}) {
        if (feed.isSavingSources) return
        val valid = codes.intersect(feed.publishers.map { it.code }.toSet())
        val previous = feed.publishers.filter { it.isFollowed }.map { it.code }.toSet()
        val changed = (valid - previous) + (previous - valid)
        if (changed.isEmpty()) {
            onSuccess()
            return
        }
        enqueuePublisherUpdate(PublisherSubscriptionUpdate(changed.associateWith { it in valid }, onSuccess))
    }

    fun setPublisherSubscription(code: String, followed: Boolean) {
        val publisher = feed.publishers.firstOrNull { it.code == code } ?: return
        if (code in feed.sourceMutationPublisherCodes || publisher.isFollowed == followed) return
        enqueuePublisherUpdate(PublisherSubscriptionUpdate(mapOf(code to followed)))
    }

    private fun enqueuePublisherUpdate(update: PublisherSubscriptionUpdate) {
        sourceUpdates.addLast(update)
        feed = feed.copy(isSavingSources = true, sourceSaveError = null,
            sourceMutationPublisherCodes = feed.sourceMutationPublisherCodes + update.changes.keys,
            sourceSaveErrors = feed.sourceSaveErrors - update.changes.keys)
        if (sourceSaveJob?.isActive == true) return
        sourceSaveJob = viewModelScope.launch {
            var changed = false
            while (sourceUpdates.isNotEmpty()) {
                val next = sourceUpdates.removeFirst()
                val target = feed.publishers.filter { it.isFollowed }.map { it.code }.toMutableSet()
                next.changes.forEach { (code, followed) -> if (followed) target += code else target -= code }
                try {
                    val savedCodes = repository.replaceFollowedPublishers(target)
                    ensureActive()
                    applyConfirmedSubscriptions(savedCodes)
                    feed = feed.copy(sourceSaveErrors = feed.sourceSaveErrors - next.changes.keys)
                    changed = true
                    if (feed.isSearchOpen) feedBeforeSearch = null
                    next.onSuccess()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (isAuthFailure(failure)) authExpired = true
                    val message = failure.message ?: "언론사 설정을 저장하지 못했어요."
                    feed = feed.copy(
                        sourceSaveError = message,
                        sourceSaveErrors = feed.sourceSaveErrors + next.changes.keys.associateWith { message }
                    )
                }
                val pending = feed.sourceMutationPublisherCodes - next.changes.keys
                feed = feed.copy(isSavingSources = pending.isNotEmpty(), sourceMutationPublisherCodes = pending)
                if (authExpired) {
                    sourceUpdates.clear()
                    feed = feed.copy(isSavingSources = false, sourceMutationPublisherCodes = emptySet())
                    return@launch
                }
            }
            if (changed) {
                if (feed.scope == FeedScope.FOLLOWING && !feed.isSearchOpen) reloadForFilters()
                if (homeRequested) loadHome(force = true)
            }
        }
    }

    private fun applyConfirmedSubscriptions(codes: Set<String>) {
        fun publisher(value: PublisherItem) = value.copy(isFollowed = value.code in codes)
        fun story(value: StorySummary) = value.copy(
            representativeArticle = value.representativeArticle.copy(publisher = publisher(value.representativeArticle.publisher)),
            publishers = value.publishers.map(::publisher)
        )
        feed = feed.copy(publishers = feed.publishers.map(::publisher))
        discovery = discovery.copy(
            activePublisher = discovery.activePublisher?.let(::publisher),
            homeStories = discovery.homeStories.map(::story),
            newsStories = discovery.newsStories.map(::story)
        )
    }

    private fun cancelPersonalMutations() {
        sourceSaveJob?.cancel()
        sourceSaveJob = null
        sourceUpdates.clear()
        savedMutationJobs.values.forEach { it.cancel() }
        savedMutationJobs.clear()
        savedMutationArticleIds = emptySet()
        saveErrors = emptyMap()
        feed = feed.copy(isSavingSources = false, sourceMutationPublisherCodes = emptySet(),
            sourceSaveError = null, sourceSaveErrors = emptyMap())
    }

    fun ensureHomeLoaded() {
        trends.load()
        homeRequested = true
        if (discovery.homeStories.isEmpty() && !discovery.isHomeLoading) loadHome()
        else loadSubscribedHome()
    }

    fun refreshHome() {
        trends.load(force = true)
        loadHome(force = true, refreshing = true)
    }

    private fun loadHome(force: Boolean = false, refreshing: Boolean = false) {
        loadSubscribedHome(force = force)
        if (!force && (discovery.isHomeLoading || discovery.homeStories.isNotEmpty())) return
        homeJob?.cancel()
        discovery = discovery.copy(
            isHomeLoading = !refreshing && discovery.homeStories.isEmpty(),
            isHomeRefreshing = refreshing,
            homeError = null
        )
        homeJob = viewModelScope.launch {
            try {
                val page = repository.stories(null, FeedScope.ALL)
                discovery = discovery.copy(
                    homeStories = page.items,
                    homeNextCursor = page.nextCursor,
                    isHomeLoading = false,
                    isHomeRefreshing = false,
                    homeError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                val offline = if (failure is NewsApiException && failure.code == "NETWORK_ERROR") {
                    repository.offlineSavedArticles()?.items.orEmpty().map(::singletonStory)
                } else emptyList()
                discovery = discovery.copy(
                    homeStories = offline.ifEmpty { discovery.homeStories },
                    homeNextCursor = null,
                    isHomeLoading = false,
                    isHomeRefreshing = false,
                    homeError = if (offline.isEmpty()) failure.message ?: "홈 뉴스를 불러오지 못했어요." else null
                )
            }
        }
    }

    fun retrySubscribedHome() = loadSubscribedHome(force = true)

    fun selectHomePublisher(code: String) {
        if (feed.publishers.none { it.code == code && it.isFollowed }) return
        if (discovery.selectedHomePublisherCode == code) return
        discovery = discovery.copy(selectedHomePublisherCode = code,
            subscribedArticles = emptyList(), isSubscribedLoading = false, subscribedError = null)
        loadSubscribedHome()
    }

    private fun loadSubscribedHome(force: Boolean = false) {
        if (force) homePublisherCache.clear()
        val followed = feed.publishers.filter { it.isFollowed }
        val selected = followed.firstOrNull { it.code == discovery.selectedHomePublisherCode }
            ?: followed.firstOrNull()
        if (selected == null) {
            subscribedHomeJob?.cancel()
            homePublisherCache.clear()
            discovery = discovery.copy(
                subscribedArticles = emptyList(), selectedHomePublisherCode = null,
                isSubscribedLoading = false, subscribedError = null
            )
            return
        }
        if (!force && discovery.isSubscribedLoading && discovery.selectedHomePublisherCode == selected.code) return
        subscribedHomeJob?.cancel()
        val cached = homePublisherCache[selected.code]
        discovery = discovery.copy(selectedHomePublisherCode = selected.code,
            subscribedArticles = cached.orEmpty(), isSubscribedLoading = cached == null, subscribedError = null)
        if (cached != null) return
        subscribedHomeJob = viewModelScope.launch {
            try {
                val page = repository.publisherArticles(selected.code)
                ensureActive()
                if (discovery.selectedHomePublisherCode != selected.code) return@launch
                homePublisherCache[selected.code] = page.items
                discovery = discovery.copy(
                    subscribedArticles = page.items, isSubscribedLoading = false, subscribedError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                if (discovery.selectedHomePublisherCode != selected.code) return@launch
                if (isAuthFailure(failure)) authExpired = true
                discovery = discovery.copy(
                    isSubscribedLoading = false,
                    subscribedError = failure.message ?: "구독 뉴스를 불러오지 못했어요."
                )
            }
        }
    }

    fun selectNewsSection(section: NewsSection) {
        if (discovery.newsSection == section &&
            (section == NewsSection.PUBLISHERS || discovery.newsStories.isNotEmpty())) return
        newsStoryJob?.cancel()
        discovery = discovery.copy(
            newsSection = section,
            newsStories = if (section == NewsSection.PUBLISHERS) discovery.newsStories else emptyList(),
            newsNextCursor = null,
            newsError = null,
            nextPageError = null
        )
        if (section != NewsSection.PUBLISHERS) loadNewsStories(section)
    }

    fun ensureNewsLoaded() {
        val section = discovery.newsSection
        if (section != NewsSection.PUBLISHERS && discovery.newsStories.isEmpty() && !discovery.isNewsLoading) {
            loadNewsStories(section)
        }
    }

    fun refreshNews() {
        if (discovery.newsSection != NewsSection.PUBLISHERS) {
            loadNewsStories(discovery.newsSection, refreshing = true)
        }
    }

    private fun loadNewsStories(section: NewsSection, refreshing: Boolean = false) {
        newsStoryJob?.cancel()
        discovery = discovery.copy(
            isNewsLoading = !refreshing && discovery.newsStories.isEmpty(),
            isNewsRefreshing = refreshing,
            newsError = null
        )
        newsStoryJob = viewModelScope.launch {
            try {
                val page = repository.stories(section.category, FeedScope.ALL)
                if (discovery.newsSection != section) return@launch
                discovery = discovery.copy(
                    newsStories = page.items,
                    newsNextCursor = page.nextCursor,
                    isNewsLoading = false,
                    isNewsRefreshing = false,
                    newsError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                if (discovery.newsSection != section) return@launch
                discovery = discovery.copy(
                    isNewsLoading = false,
                    isNewsRefreshing = false,
                    newsError = failure.message ?: "뉴스를 불러오지 못했어요."
                )
            }
        }
    }

    fun loadMoreNews() {
        val cursor = discovery.newsNextCursor ?: return
        val section = discovery.newsSection
        if (section == NewsSection.PUBLISHERS || discovery.isLoadingMore) return
        discovery = discovery.copy(isLoadingMore = true, nextPageError = null)
        newsStoryJob = viewModelScope.launch {
            try {
                val page = repository.stories(section.category, FeedScope.ALL, cursor)
                if (discovery.newsSection != section) return@launch
                discovery = discovery.copy(
                    newsStories = discovery.newsStories + page.items,
                    newsNextCursor = page.nextCursor,
                    isLoadingMore = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                discovery = discovery.copy(
                    isLoadingMore = false,
                    nextPageError = failure.message ?: "다음 뉴스를 불러오지 못했어요."
                )
            }
        }
    }

    fun openPublisher(publisher: PublisherItem) {
        publisherJob?.cancel()
        discovery = discovery.copy(
            activePublisher = publisher,
            publisherArticles = emptyList(),
            publisherNextCursor = null,
            isPublisherLoading = true,
            publisherError = null
        )
        publisherJob = viewModelScope.launch { loadPublisherPage(publisher, null) }
    }

    fun closePublisher() {
        publisherJob?.cancel()
        discovery = discovery.copy(
            activePublisher = null,
            publisherArticles = emptyList(),
            publisherNextCursor = null,
            isPublisherLoading = false,
            publisherError = null
        )
    }

    fun retryPublisher() {
        val publisher = discovery.activePublisher ?: return
        openPublisher(publisher)
    }

    fun loadMorePublisher() {
        val publisher = discovery.activePublisher ?: return
        val cursor = discovery.publisherNextCursor ?: return
        if (discovery.isPublisherLoading) return
        discovery = discovery.copy(isPublisherLoading = true, publisherError = null)
        publisherJob = viewModelScope.launch { loadPublisherPage(publisher, cursor) }
    }

    private suspend fun loadPublisherPage(publisher: PublisherItem, cursor: String?) {
        try {
            val page = repository.publisherArticles(publisher.code, cursor)
            if (discovery.activePublisher?.code != publisher.code) return
            discovery = discovery.copy(
                publisherArticles = if (cursor == null) page.items else discovery.publisherArticles + page.items,
                publisherNextCursor = page.nextCursor,
                isPublisherLoading = false,
                publisherError = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isAuthFailure(failure)) authExpired = true
            if (discovery.activePublisher?.code != publisher.code) return
            discovery = discovery.copy(
                isPublisherLoading = false,
                publisherError = failure.message ?: "언론사 기사를 불러오지 못했어요."
            )
        }
    }

    private fun singletonStory(article: ArticleSummary) = StorySummary(
        storyKey = "article:${article.id}",
        representativeArticle = article,
        publisherCount = 1,
        publishers = listOf(article.publisher),
        latestPublishedAt = article.publishedAt
    )

    fun openSearch() {
        if (feed.isSearchOpen) return
        feedBeforeSearch = feed.copy(
            isSearchOpen = false,
            searchInput = "",
            activeSearchQuery = "",
            isSearchPending = false
        )
        feed = feed.copy(isSearchOpen = true, searchFilter = SearchFilter.ALL)
    }

    fun updateSearchInput(value: String) {
        if (!feed.isSearchOpen) return
        searchDebounceJob?.cancel()
        val normalized = normalizeSearch(value)
        feed = feed.copy(searchInput = value, error = null, nextPageError = null)
        when {
            normalized.isEmpty() -> showSearchPrompt(value)
            else -> {
                feed = feed.copy(isSearchPending = true)
                searchDebounceJob = viewModelScope.launch {
                    delay(300)
                    activateSearch(normalized)
                }
            }
        }
    }

    fun submitSearch() {
        val normalized = normalizeSearch(feed.searchInput)
        if (!feed.isSearchOpen || normalized.isEmpty()) return
        searchDebounceJob?.cancel()
        activateSearch(normalized)
    }

    fun closeSearch() {
        if (!feed.isSearchOpen) return
        searchDebounceJob?.cancel()
        feedJob?.cancel()
        val snapshot = feedBeforeSearch
        feedBeforeSearch = null
        if (snapshot != null && snapshot.category == feed.category && snapshot.scope == feed.scope) {
            feed = snapshot.copy(
                publishers = feed.publishers,
                isSavingSources = feed.isSavingSources,
                sourceMutationPublisherCodes = feed.sourceMutationPublisherCodes,
                sourceSaveError = feed.sourceSaveError,
                sourceSaveErrors = feed.sourceSaveErrors
            )
            return
        }
        feed = feed.copy(
            items = emptyList(),
            searchSections = emptyMap(),
            nextCursor = null,
            isSearchOpen = false,
            searchInput = "",
            activeSearchQuery = "",
            isSearchPending = false,
            isLoading = true,
            isRefreshing = false,
            isLoadingMore = false,
            error = null,
            nextPageError = null
        )
        feedJob = viewModelScope.launch { loadFirstPage() }
    }

    private fun activateSearch(query: String) {
        if (!feed.isSearchOpen || normalizeSearch(feed.searchInput) != query) return
        feedJob?.cancel()
        feed = feed.copy(
            items = emptyList(),
            searchFilter = SearchFilter.ALL,
            searchSections = initialSearchSections(loading = true),
            nextCursor = null,
            activeSearchQuery = query,
            isSearchPending = false,
            isLoading = true,
            isRefreshing = false,
            isLoadingMore = false,
            error = null,
            nextPageError = null
        )
        feedJob = viewModelScope.launch { loadSearchFilter(SearchFilter.ALL, query) }
    }

    /** An empty field inside search shows only the prompt; the feed comes back when search closes. */
    private fun showSearchPrompt(input: String) {
        feedJob?.cancel()
        feed = feed.copy(
            searchInput = input,
            searchFilter = SearchFilter.ALL,
            searchSections = emptyMap(),
            activeSearchQuery = "",
            isSearchPending = false,
            isLoading = false,
            isRefreshing = false,
            isLoadingMore = false,
            error = null,
            nextPageError = null
        )
    }

    private fun reloadForFilters() {
        feedJob?.cancel()
        searchDebounceJob?.cancel()
        val normalized = normalizeSearch(feed.searchInput)
        val query = normalized.takeIf { feed.isSearchOpen && it.isNotEmpty() }
        feed = feed.copy(
            items = emptyList(),
            searchFilter = if (query == null) feed.searchFilter else SearchFilter.ALL,
            searchSections = if (query == null) emptyMap() else initialSearchSections(loading = true),
            nextCursor = null,
            isLoading = true,
            isRefreshing = false,
            isLoadingMore = false,
            error = null,
            nextPageError = null,
            activeSearchQuery = query.orEmpty(),
            isSearchPending = false
        )
        feedJob = viewModelScope.launch {
            if (query == null) loadFirstPage() else loadSearchFilter(SearchFilter.ALL, query)
        }
    }

    private suspend fun loadFirstPage() {
        val category = feed.category
        val scope = feed.scope
        try {
            val page = repository.articles(category, scope)
            if (category != feed.category || scope != feed.scope ||
                feed.activeSearchQuery.isNotBlank()) return
            feed = feed.copy(
                items = orderFeedItems(page.items, scope, null),
                nextCursor = page.nextCursor,
                isLoading = false,
                isRefreshing = false,
                isOffline = false,
                error = null,
                nextPageError = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            handleFeedFailure(failure, category, scope)
        }
    }

    private suspend fun loadSearchFilter(filter: SearchFilter, query: String) {
        try {
            val page = repository.articles(filter.category, FeedScope.ALL, query = query)
            if (!feed.isSearchOpen || feed.activeSearchQuery != query) return
            val updated = feed.searchSections.toMutableMap().apply {
                this[filter] = SearchSectionState(
                    filter = filter,
                    items = page.items,
                    totalCount = page.totalCount ?: page.items.size,
                    nextCursor = page.nextCursor
                )
            }
            feed = feed.copy(
                searchSections = updated,
                isLoading = updated.values.any { it.isLoading },
                isRefreshing = updated.values.any { it.isLoading },
                error = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isAuthFailure(failure)) authExpired = true
            if (!feed.isSearchOpen || feed.activeSearchQuery != query) return
            val updated = feed.searchSections.toMutableMap().apply {
                val current = this[filter] ?: SearchSectionState(filter)
                this[filter] = current.copy(
                    isLoading = false,
                    error = failure.message ?: "검색 결과를 불러오지 못했어요."
                )
            }
            feed = feed.copy(
                searchSections = updated,
                isLoading = updated.values.any { it.isLoading },
                isRefreshing = updated.values.any { it.isLoading }
            )
        }
    }

    fun retrySearch(filter: SearchFilter) {
        val query = feed.activeSearchQuery
        val section = feed.searchSections[filter] ?: return
        if (query.isBlank() || section.isLoading) return
        feed = feed.copy(searchSections = feed.searchSections + (
            filter to section.copy(isLoading = true, error = null)
        ))
        viewModelScope.launch { loadSearchFilter(filter, query) }
    }

    fun loadMore() {
        if (feed.activeSearchQuery.isNotBlank()) return
        val cursor = feed.nextCursor ?: return
        if (feed.isLoadingMore || feed.isLoading || feed.isRefreshing) return
        feed = feed.copy(isLoadingMore = true, nextPageError = null)
        val category = feed.category
        val scope = feed.scope
        feedJob = viewModelScope.launch {
            try {
                val page = repository.articles(category, scope, cursor)
                if (category != feed.category || scope != feed.scope ||
                    feed.activeSearchQuery.isNotBlank()) return@launch
                feed = feed.copy(
                    items = orderFeedItems(feed.items + page.items, scope, null),
                    nextCursor = page.nextCursor,
                    isLoadingMore = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                feed = feed.copy(
                    isLoadingMore = false,
                    nextPageError = failure.message ?: "다음 기사를 불러오지 못했어요."
                )
            }
        }
    }

    fun loadMoreSearch(filter: SearchFilter) {
        val query = feed.activeSearchQuery
        val section = feed.searchSections[filter] ?: return
        val cursor = section.nextCursor ?: return
        if (query.isBlank() || section.isLoading || section.isLoadingMore) return
        feed = feed.copy(searchSections = feed.searchSections + (
            filter to section.copy(isLoadingMore = true, nextPageError = null)
        ))
        viewModelScope.launch {
            try {
                val page = repository.articles(filter.category, FeedScope.ALL, cursor, query)
                if (!feed.isSearchOpen || feed.activeSearchQuery != query) return@launch
                val current = feed.searchSections[filter] ?: return@launch
                feed = feed.copy(searchSections = feed.searchSections + (
                    filter to current.copy(
                        items = current.items + page.items,
                        totalCount = page.totalCount ?: current.totalCount,
                        nextCursor = page.nextCursor,
                        isLoadingMore = false
                    )
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                if (!feed.isSearchOpen || feed.activeSearchQuery != query) return@launch
                val current = feed.searchSections[filter] ?: return@launch
                feed = feed.copy(searchSections = feed.searchSections + (
                    filter to current.copy(
                        isLoadingMore = false,
                        nextPageError = failure.message ?: "다음 검색 결과를 불러오지 못했어요."
                    )
                ))
            }
        }
    }

    private suspend fun handleFeedFailure(
        failure: Exception,
        category: NewsCategory,
        scope: FeedScope
    ) {
        if (isAuthFailure(failure)) authExpired = true
        if (category != feed.category || scope != feed.scope ||
            feed.activeSearchQuery.isNotBlank()) return
        val isNetworkFailure = failure is NewsApiException && failure.code == "NETWORK_ERROR"
        val offlineItems = if (isNetworkFailure) {
            try {
                repository.offlineSavedArticles(category)?.items.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        feed = feed.copy(
            items = if (isNetworkFailure) offlineItems else feed.items,
            nextCursor = if (isNetworkFailure) null else feed.nextCursor,
            isLoading = false,
            isRefreshing = false,
            isLoadingMore = false,
            isOffline = isNetworkFailure,
            error = if (isNetworkFailure) null else failure.message ?: "기사 목록을 불러오지 못했어요."
        )
    }

    fun openSaved() {
        savedJob?.cancel()
        route = NewsRoute.SAVED
        saved = saved.copy(
            isLoading = true,
            isRefreshing = false,
            isLoadingMore = false,
            error = null,
            nextPageError = null
        )
        savedJob = viewModelScope.launch { loadSavedFirstPage() }
    }

    fun closeSaved() {
        savedJob?.cancel()
        route = NewsRoute.FEED
    }

    fun retrySaved() = openSaved()

    fun refreshSaved() {
        if (saved.isRefreshing) return
        savedJob?.cancel()
        saved = saved.copy(isRefreshing = true, error = null, nextPageError = null)
        savedJob = viewModelScope.launch { loadSavedFirstPage() }
    }

    private suspend fun loadSavedFirstPage() {
        val offlineItems = try {
            repository.offlineSavedArticles()?.items.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (offlineItems.isNotEmpty()) {
            if (route == NewsRoute.SAVED) {
                saved = saved.copy(
                    items = offlineItems,
                    nextCursor = null,
                    isLoading = false,
                    isRefreshing = true,
                    isOffline = false,
                    error = null
                )
            }
        }
        try {
            val page = repository.savedArticles()
            if (route != NewsRoute.SAVED) return
            saved = saved.copy(
                items = page.items,
                nextCursor = page.nextCursor,
                isLoading = false,
                isRefreshing = false,
                isOffline = false,
                error = null,
                nextPageError = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isAuthFailure(failure)) authExpired = true
            val isNetworkFailure = failure is NewsApiException && failure.code == "NETWORK_ERROR"
            saved = saved.copy(
                items = if (isNetworkFailure) offlineItems else saved.items,
                nextCursor = if (isNetworkFailure) null else saved.nextCursor,
                isLoading = false,
                isRefreshing = false,
                isOffline = isNetworkFailure,
                error = if (isNetworkFailure) null else failure.message ?: "저장한 기사를 불러오지 못했어요."
            )
        }
    }

    fun loadMoreSaved() {
        val cursor = saved.nextCursor ?: return
        if (saved.isLoading || saved.isRefreshing || saved.isLoadingMore) return
        saved = saved.copy(isLoadingMore = true, nextPageError = null)
        savedJob = viewModelScope.launch {
            try {
                val page = repository.savedArticles(cursor)
                if (route != NewsRoute.SAVED) return@launch
                saved = saved.copy(
                    items = saved.items + page.items,
                    nextCursor = page.nextCursor,
                    isLoadingMore = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                saved = saved.copy(
                    isLoadingMore = false,
                    nextPageError = failure.message ?: "다음 저장 기사를 불러오지 못했어요."
                )
            }
        }
    }

    fun toggleSaved(articleId: String, currentlySaved: Boolean) {
        if (articleId in savedMutationArticleIds) return
        val target = !currentlySaved
        savedMutationArticleIds = savedMutationArticleIds + articleId
        saveErrors = saveErrors - articleId
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.setArticleSaved(articleId, target)
                ensureActive()
                feed = feed.copy(items = feed.items.map { article ->
                    if (article.id == articleId) article.copy(isSaved = target) else article
                }, searchSections = feed.searchSections.mapValues { (_, section) ->
                    section.copy(items = section.items.map { article ->
                        if (article.id == articleId) article.copy(isSaved = target) else article
                    })
                })
                feedBeforeSearch = feedBeforeSearch?.let { snapshot ->
                    snapshot.copy(items = snapshot.items.map { article ->
                        if (article.id == articleId) article.copy(isSaved = target) else article
                    })
                }
                reader = reader.copy(article = reader.article?.let { article ->
                    if (article.id == articleId) article.copy(isSaved = target) else article
                })
                for (index in readerHistory.indices) {
                    val destination = readerHistory[index]
                    val article = destination.snapshot.article
                    if (article?.id == articleId) readerHistory[index] = destination.copy(
                        snapshot = destination.snapshot.copy(article = article.copy(isSaved = target)))
                }
                discovery = discovery.copy(
                    homeStories = discovery.homeStories.map { story ->
                        if (story.representativeArticle.id == articleId) {
                            story.copy(representativeArticle = story.representativeArticle.copy(isSaved = target))
                        } else story
                    },
                    newsStories = discovery.newsStories.map { story ->
                        if (story.representativeArticle.id == articleId) {
                            story.copy(representativeArticle = story.representativeArticle.copy(isSaved = target))
                        } else story
                    },
                    publisherArticles = discovery.publisherArticles.map { article ->
                        if (article.id == articleId) article.copy(isSaved = target) else article
                    }
                )
                saved = saved.copy(items = if (target) {
                    saved.items.map { article ->
                        if (article.id == articleId) article.copy(isSaved = true) else article
                    }
                } else {
                    saved.items.filterNot { it.id == articleId }
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                saveErrors = saveErrors + (articleId to (failure.message ?: "저장 상태를 바꾸지 못했어요."))
            }
            savedMutationArticleIds = savedMutationArticleIds - articleId
            savedMutationJobs.remove(articleId)
        }
        savedMutationJobs[articleId] = job
        job.start()
    }

    fun openLinkAnalysis() {
        if (!link.isRunning) link = LinkAnalysisState(isOpen = true)
    }

    fun updateLinkInput(value: String) {
        if (!link.isRunning) link = link.copy(input = value, error = null)
    }

    /** Closing while the server analyses stops waiting; the analysis already sent still finishes on the server. */
    fun closeLinkAnalysis() {
        linkJob?.cancel()
        linkJob = null
        link = LinkAnalysisState()
    }

    fun submitLinkAnalysis() {
        val url = link.input.trim()
        if (link.isRunning) return
        if (url.isBlank()) {
            link = link.copy(error = "기사 링크를 입력해 주세요.")
            return
        }
        if (!isHttpLink(url)) {
            link = link.copy(error = "http:// 또는 https://로 시작하는 전체 기사 주소를 입력해 주세요.")
            return
        }
        link = link.copy(isRunning = true, error = null)
        linkJob = viewModelScope.launch {
            try {
                val articleId = repository.analyzeLink(url)
                link = LinkAnalysisState()
                openArticle(articleId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                link = link.copy(isRunning = false, error = failure.message ?: "기사를 분석하지 못했어요.")
            }
        }
    }

    fun openArticle(articleId: String) {
        if (route != NewsRoute.READER) {
            readerOrigin = route
            readerHistory.clear()
        }
        route = NewsRoute.READER
        loadReader(articleId, null)
    }

    fun openComparisonArticle(
        article: ComparisonArticleUi,
        sourceScrollOffset: Int,
        sourceSelectedFindingId: String? = null
    ) {
        if (route != NewsRoute.READER || reader.articleId.isBlank()) {
            openArticle(article.articleId)
            return
        }
        if (restorePreviousIfSameArticle(article.articleId)) return
        readerHistory.addLast(readerDestination(sourceScrollOffset, sourceSelectedFindingId))
        val evidence = article.evidence.firstOrNull()
        loadReader(
            article.articleId,
            evidence?.let { ReaderFocus(it.scope, it.markerRange) }
        )
    }

    /** Another outlet's article on the same event, opened from the economic lens; back returns here. */
    fun openRelatedArticle(
        articleId: String,
        sourceScrollOffset: Int,
        sourceSelectedFindingId: String? = null
    ) {
        if (route != NewsRoute.READER || reader.articleId.isBlank()) {
            openArticle(articleId)
            return
        }
        if (restorePreviousIfSameArticle(articleId)) return
        readerHistory.addLast(readerDestination(sourceScrollOffset, sourceSelectedFindingId))
        loadReader(articleId, null)
    }

    private fun readerDestination(
        scrollOffset: Int,
        selectedFindingId: String?
    ) = ReaderDestination(
        reader.copy(
            isArticleLoading = false,
            isAnalysisLoading = false,
            articleError = null,
            analysisError = null,
            initialScrollOffset = scrollOffset,
            initialSelectedFindingId = selectedFindingId
        )
    )

    /** A comparison link back to the immediately previous article behaves like Back, not A → B → A. */
    private fun restorePreviousIfSameArticle(articleId: String): Boolean {
        val previous = readerHistory.lastOrNull()?.takeIf { it.snapshot.articleId == articleId }
            ?: return false
        readerHistory.removeLast()
        restoreReader(previous)
        return true
    }

    private fun restoreReader(destination: ReaderDestination) {
        val snapshot = destination.snapshot
        loadReader(
            articleId = snapshot.articleId,
            comparisonFocus = snapshot.comparisonFocus,
            initialScrollOffset = snapshot.initialScrollOffset,
            initialSelectedFindingId = snapshot.initialSelectedFindingId,
            snapshot = snapshot
        )
    }

    private fun loadReader(
        articleId: String,
        comparisonFocus: ReaderFocus?,
        initialScrollOffset: Int = 0,
        initialSelectedFindingId: String? = null,
        snapshot: ArticleReaderState? = null,
        isNewVisit: Boolean = true
    ) {
        if (isNewVisit) reading.newVisit()
        readerJob?.cancel()
        val analysisEnabled = reader.analysisEnabled
        reader = snapshot?.copy(
            analysisEnabled = analysisEnabled,
            isArticleLoading = false,
            isAnalysisLoading = false,
            isOffline = false,
            articleError = null,
            analysisError = null,
            comparisonFocus = comparisonFocus,
            initialScrollOffset = initialScrollOffset,
            initialSelectedFindingId = initialSelectedFindingId
        ) ?: ArticleReaderState(
            articleId = articleId,
            analysisEnabled = analysisEnabled,
            isArticleLoading = true,
            comparisonFocus = comparisonFocus,
            initialScrollOffset = initialScrollOffset,
            initialSelectedFindingId = initialSelectedFindingId
        )
        readerJob = viewModelScope.launch {
            if (snapshot?.article == null) {
                val cachedArticle = repository.cachedArticle(articleId)
                if (cachedArticle != null && route == NewsRoute.READER &&
                    reader.articleId == articleId) {
                    val cachedAnalysis = repository.cachedAnalysis(cachedArticle)
                    if (route != NewsRoute.READER || reader.articleId != articleId) return@launch
                    reader = reader.copy(
                        article = cachedArticle,
                        analysis = cachedAnalysis,
                        isArticleLoading = false,
                        isAnalysisLoading = cachedAnalysis == null,
                        isOffline = false
                    )
                }
            }
            try {
                val article = repository.article(articleId)
                if (route != NewsRoute.READER || reader.articleId != articleId) return@launch
                val previousArticle = reader.article
                val retainAnalysis = previousArticle?.contentHash == article.contentHash
                reader = reader.copy(
                    article = article,
                    analysis = reader.analysis.takeIf { retainAnalysis },
                    isArticleLoading = false,
                    isAnalysisLoading = !retainAnalysis || reader.analysis == null,
                    isOffline = false,
                    articleError = null
                )
                try {
                    val analysis = repository.analysis(article)
                    if (route != NewsRoute.READER || reader.articleId != articleId) return@launch
                    reader = reader.copy(
                        analysis = analysis,
                        isAnalysisLoading = false,
                        isOffline = false,
                        analysisError = null
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (isAuthFailure(failure)) authExpired = true
                    reader = reader.copy(
                        isAnalysisLoading = false,
                        analysisError = if (reader.analysis == null) {
                            failure.message ?: "분석 표시를 불러오지 못했어요."
                        } else null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                val retainCache = failure is NewsApiException && failure.code == "NETWORK_ERROR" &&
                    reader.article != null
                reader = if (retainCache) {
                    reader.copy(
                        isArticleLoading = false,
                        isAnalysisLoading = false,
                        isOffline = true,
                        articleError = null,
                        analysisError = if (reader.analysis == null) {
                            "오프라인에서는 저장된 분석만 볼 수 있어요."
                        } else null
                    )
                } else {
                    reader.copy(
                        isArticleLoading = false,
                        articleError = if (
                            readerOrigin == NewsRoute.SAVED && failure is NewsApiException &&
                            failure.code == "NETWORK_ERROR"
                        ) {
                            "오프라인에서 읽으려면 온라인 상태에서 기사를 한 번 열어 주세요."
                        } else failure.message ?: "기사를 불러오지 못했어요."
                    )
                }
            }
        }
    }

    fun retryReader() = loadReader(reader.articleId, reader.comparisonFocus, isNewVisit = false)

    fun closeReader() {
        reading.update(null, false, null)
        readerJob?.cancel()
        if (readerHistory.isNotEmpty()) {
            val previous = readerHistory.removeLast()
            restoreReader(previous)
        } else {
            route = readerOrigin
        }
    }

    fun setAnalysisEnabled(enabled: Boolean) {
        reader = reader.copy(analysisEnabled = enabled)
        repository.saveAnalysisEnabled(enabled)
    }

    fun consumeAuthExpired() {
        authExpired = false
    }

    fun logout(userId: String, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.clearSavedCache(userId)
            } finally {
                reset()
                onReady()
            }
        }
    }

    private fun isAuthFailure(failure: Exception): Boolean =
        failure is NewsApiException && failure.code == "AUTH_REQUIRED"

    private fun normalizeSearch(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
}
