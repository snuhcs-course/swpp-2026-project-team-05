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
import com.swpp.team5.frameless.data.TextRange
import com.swpp.team5.frameless.telemetry.Telemetry
import com.swpp.team5.frameless.telemetry.TelemetryEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class NewsRoute { FEED, SAVED, READER }

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
    val items: List<ArticleSummary> = emptyList(),
    val category: NewsCategory = NewsCategory.POLITICS,
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val scope: FeedScope = FeedScope.ALL,
    val isSavingSources: Boolean = false,
    val sourceSaveError: String? = null,
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

internal class NewsViewModel(
    private val repository: NewsDataSource,
    private val telemetry: Telemetry = Telemetry.None
) : ViewModel() {
    /** Fixed codes, counts and flags only (specs/features/telemetry.md). */
    private fun record(event: String, vararg params: Pair<String, Any>) = telemetry.record(event, mapOf(*params))
    private fun errorCode(failure: Exception) = (failure as? NewsApiException)?.code ?: "UNKNOWN"

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
    var savedMutationArticleId by mutableStateOf<String?>(null)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    var authExpired by mutableStateOf(false)
        private set

    private var initialized = false
    private var feedJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var feedBeforeSearch: NewsFeedState? = null
    private var readerJob: Job? = null
    private var savedJob: Job? = null
    private var readerOrigin = NewsRoute.FEED
    private val readerHistory = ArrayDeque<ReaderDestination>()

    fun initialize() {
        if (initialized) return
        initialized = true
        feed = feed.copy(isLoading = true, error = null)
        feedJob = viewModelScope.launch {
            try {
                val publishers = repository.publishers()
                feed = feed.copy(publishers = publishers)
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
            }
            loadFirstPage()
        }
    }

    fun reset() {
        feedJob?.cancel()
        searchDebounceJob?.cancel()
        readerJob?.cancel()
        savedJob?.cancel()
        feedBeforeSearch = null
        initialized = false
        route = NewsRoute.FEED
        feed = NewsFeedState(
            category = repository.savedCategory()
        )
        reader = ArticleReaderState(analysisEnabled = repository.savedAnalysisEnabled())
        saved = SavedArticlesState()
        savedMutationArticleId = null
        saveError = null
        readerOrigin = NewsRoute.FEED
        readerHistory.clear()
        authExpired = false
    }

    fun onViewerChanged() {
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
        authExpired = false
        initialized = false
        initialize()
        when {
            currentRoute == NewsRoute.READER && currentArticleId.isNotBlank() ->
                loadReader(currentArticleId, currentFocus)
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
        record(TelemetryEvent.CATEGORY_SELECT, "category" to category.wire)
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
        feed = feed.copy(isSavingSources = true, sourceSaveError = null)
        viewModelScope.launch {
            try {
                val savedCodes = repository.replaceFollowedPublishers(valid)
                feed = feed.copy(
                    publishers = feed.publishers.map { publisher ->
                        publisher.copy(isFollowed = publisher.code in savedCodes)
                    },
                    isSavingSources = false,
                    sourceSaveError = null
                )
                if (feed.isSearchOpen) feedBeforeSearch = null
                if (feed.scope == FeedScope.FOLLOWING && !feed.isSearchOpen) reloadForFilters()
                onSuccess()
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                feed = feed.copy(
                    isSavingSources = false,
                    sourceSaveError = failure.message ?: "언론사 설정을 저장하지 못했어요."
                )
            }
        }
    }

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
            normalized.length < 2 -> feed = feed.copy(
                items = emptyList(),
                searchSections = emptyMap(),
                nextCursor = null,
                activeSearchQuery = "",
                isSearchPending = false,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false
            )
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
        if (!feed.isSearchOpen || normalized.length < 2) return
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
            feed = snapshot
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
        record(TelemetryEvent.SEARCH_SUBMIT)  // the query itself is never recorded
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
        val query = normalized.takeIf { feed.isSearchOpen && it.length >= 2 }
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
            record(TelemetryEvent.API_ERROR, "where" to "feed", "code" to errorCode(failure))
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
        if (savedMutationArticleId != null) return
        val target = !currentlySaved
        savedMutationArticleId = articleId
        saveError = null
        viewModelScope.launch {
            try {
                repository.setArticleSaved(articleId, target)
                feed = feed.copy(items = feed.items.map { article ->
                    if (article.id == articleId) article.copy(isSaved = target) else article
                }, searchSections = feed.searchSections.mapValues { (_, section) ->
                    section.copy(items = section.items.map { article ->
                        if (article.id == articleId) article.copy(isSaved = target) else article
                    })
                })
                reader = reader.copy(article = reader.article?.let { article ->
                    if (article.id == articleId) article.copy(isSaved = target) else article
                })
                saved = saved.copy(items = if (target) {
                    saved.items.map { article ->
                        if (article.id == articleId) article.copy(isSaved = true) else article
                    }
                } else {
                    saved.items.filterNot { it.id == articleId }
                })
                record(TelemetryEvent.SAVE_TOGGLE, "saved" to target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                record(TelemetryEvent.API_ERROR, "where" to "save", "code" to errorCode(failure))
                saveError = failure.message ?: "저장 상태를 바꾸지 못했어요."
            } finally {
                savedMutationArticleId = null
            }
        }
    }

    fun openLinkAnalysis() {
        if (!link.isRunning) link = LinkAnalysisState(isOpen = true)
    }

    fun updateLinkInput(value: String) {
        if (!link.isRunning) link = link.copy(input = value, error = null)
    }

    /** Closing while the server analyses stops waiting; the analysis already sent still finishes on the server. */
    fun closeLinkAnalysis() {
        if (link.isRunning) record(TelemetryEvent.LINK_ANALYSIS_RESULT, "result" to "cancelled")
        linkJob?.cancel()
        linkJob = null
        link = LinkAnalysisState()
    }

    /** The pasted link is never recorded: only the outcome, an error code, and the seconds it took. */
    fun submitLinkAnalysis() {
        val url = link.input.trim()
        if (link.isRunning) return
        if (url.isBlank()) {
            link = link.copy(error = "기사 링크를 입력해 주세요.")
            record(TelemetryEvent.LINK_ANALYSIS_RESULT, "result" to "invalid")
            return
        }
        if (!isHttpLink(url)) {
            link = link.copy(error = "http:// 또는 https://로 시작하는 전체 기사 주소를 입력해 주세요.")
            record(TelemetryEvent.LINK_ANALYSIS_RESULT, "result" to "invalid")
            return
        }
        link = link.copy(isRunning = true, error = null)
        val started = System.currentTimeMillis()
        val seconds = { ((System.currentTimeMillis() - started) / 1000).toInt() }
        linkJob = viewModelScope.launch {
            try {
                val articleId = repository.analyzeLink(url)
                record(TelemetryEvent.LINK_ANALYSIS_RESULT, "result" to "opened", "seconds" to seconds())
                link = LinkAnalysisState()
                openArticle(articleId, source = "link")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                record(TelemetryEvent.LINK_ANALYSIS_RESULT, "result" to "error", "code" to errorCode(failure),
                    "seconds" to seconds())
                link = link.copy(isRunning = false, error = failure.message ?: "기사를 분석하지 못했어요.")
            }
        }
    }

    /** `source` says where the reader came from: feed, search, saved, or link. */
    fun openArticle(articleId: String, source: String = articleSource()) {
        if (route != NewsRoute.READER) {
            readerOrigin = route
            readerHistory.clear()
        }
        record(TelemetryEvent.ARTICLE_OPEN, "source" to source)
        route = NewsRoute.READER
        loadReader(articleId, null)
    }

    private fun articleSource(): String = when {
        route == NewsRoute.SAVED -> "saved"
        route == NewsRoute.READER -> "reader"
        feed.isSearchOpen -> "search"
        else -> "feed"
    }

    fun recordScreen(screen: NewsRoute) = record(TelemetryEvent.SCREEN_VIEW, "screen_name" to screen.name.lowercase())

    fun recordComparisonShown(pages: Int) = record(TelemetryEvent.COMPARISON_OPEN, "pages" to pages)

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
        record(TelemetryEvent.ARTICLE_OPEN, "source" to "comparison")
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
        record(TelemetryEvent.ARTICLE_OPEN, "source" to "related")
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
        snapshot: ArticleReaderState? = null
    ) {
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
                    record(
                        TelemetryEvent.ANALYSIS_VIEW,
                        "findings" to analysis.findings.size,
                        "with_comparison" to analysis.findings.count { it.comparisons.isNotEmpty() },
                        "economic" to (analysis.economic != null)
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (isAuthFailure(failure)) authExpired = true
                    record(TelemetryEvent.API_ERROR, "where" to "analysis", "code" to errorCode(failure))
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
                record(TelemetryEvent.API_ERROR, "where" to "article", "code" to errorCode(failure))
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

    fun retryReader() = loadReader(reader.articleId, reader.comparisonFocus)

    fun closeReader() {
        readerJob?.cancel()
        if (readerHistory.isNotEmpty()) {
            val previous = readerHistory.removeLast()
            restoreReader(previous)
        } else {
            route = readerOrigin
        }
    }

    fun setAnalysisEnabled(enabled: Boolean) {
        if (enabled != reader.analysisEnabled) record(TelemetryEvent.ANALYSIS_TOGGLE, "enabled" to enabled)
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
