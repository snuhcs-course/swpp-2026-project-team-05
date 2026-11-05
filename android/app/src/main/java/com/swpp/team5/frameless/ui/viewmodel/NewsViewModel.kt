package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.FeedScope
import com.swpp.team5.frameless.data.NewsApiException
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsDataSource
import com.swpp.team5.frameless.data.PublisherItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class NewsRoute { FEED, SAVED, READER }

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
    val scope: FeedScope = FeedScope.ALL,
    val isSavingSources: Boolean = false,
    val sourceSaveError: String? = null,
    val isSearchOpen: Boolean = false,
    val searchInput: String = "",
    val activeSearchQuery: String = "",
    val isSearchPending: Boolean = false,
    val searchSections: Map<NewsCategory, SearchSectionState> = emptyMap(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPageError: String? = null
)

internal data class SearchSectionState(
    val category: NewsCategory,
    val items: List<ArticleSummary> = emptyList(),
    val totalCount: Int = 0,
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPageError: String? = null
)

private fun emptySearchSections(loading: Boolean = false): Map<NewsCategory, SearchSectionState> =
    NewsCategory.entries.associateWith { SearchSectionState(category = it, isLoading = loading) }

internal data class ArticleReaderState(
    val articleId: String = "",
    val article: ArticleDetail? = null,
    val analysis: ArticleAnalysisUi? = null,
    val analysisEnabled: Boolean = true,
    val isArticleLoading: Boolean = false,
    val isAnalysisLoading: Boolean = false,
    val articleError: String? = null,
    val analysisError: String? = null
)

internal data class SavedArticlesState(
    val items: List<ArticleSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPageError: String? = null
)

internal class NewsViewModel(private val repository: NewsDataSource) : ViewModel() {
    var route by mutableStateOf(NewsRoute.FEED)
        private set
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

    fun initialize() {
        if (initialized) return
        initialized = true
        feed = feed.copy(isLoading = true, error = null)
        feedJob = viewModelScope.launch {
            try {
                val publishers = repository.publishers()
                feed = feed.copy(publishers = publishers)
                loadFirstPage()
            } catch (failure: Exception) {
                handleFeedFailure(failure)
            }
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
        authExpired = false
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
            searchSections = if (query == null) feed.searchSections else
                feed.searchSections.mapValues { (_, section) ->
                    section.copy(isLoading = true, error = null, nextPageError = null)
                }
        )
        feedJob = viewModelScope.launch {
            if (query != null) loadSearchFirstPages(query) else loadFirstPage()
        }
    }

    fun selectCategory(category: NewsCategory) {
        if (feed.category == category) return
        if (feed.isSearchOpen) return
        feed = feed.copy(category = category)
        repository.saveCategory(category)
        reloadForFilters()
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
        feed = feed.copy(isSearchOpen = true)
    }

    fun updateSearchInput(value: String) {
        if (!feed.isSearchOpen) return
        searchDebounceJob?.cancel()
        val normalized = normalizeSearch(value)
        feed = feed.copy(searchInput = value, error = null, nextPageError = null)
        when {
            normalized.isEmpty() -> restoreOrdinaryFeedInsideSearch(value)
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
        feed = feed.copy(
            items = emptyList(),
            searchSections = emptySearchSections(loading = true),
            nextCursor = null,
            activeSearchQuery = query,
            isSearchPending = false,
            isLoading = true,
            isRefreshing = false,
            isLoadingMore = false,
            error = null,
            nextPageError = null
        )
        feedJob = viewModelScope.launch { loadSearchFirstPages(query) }
    }

    private fun restoreOrdinaryFeedInsideSearch(input: String) {
        feedJob?.cancel()
        val snapshot = feedBeforeSearch
        if (snapshot != null && snapshot.category == feed.category && snapshot.scope == feed.scope) {
            feed = snapshot.copy(isSearchOpen = true, searchInput = input)
            return
        }
        feed = feed.copy(
            items = emptyList(),
            searchSections = emptyMap(),
            nextCursor = null,
            searchInput = input,
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

    private fun reloadForFilters() {
        feedJob?.cancel()
        searchDebounceJob?.cancel()
        val normalized = normalizeSearch(feed.searchInput)
        val query = normalized.takeIf { feed.isSearchOpen && it.length >= 2 }
        feed = feed.copy(
            items = emptyList(),
            searchSections = if (query == null) emptyMap() else emptySearchSections(loading = true),
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
            if (query == null) loadFirstPage() else loadSearchFirstPages(query)
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
                error = null,
                nextPageError = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            handleFeedFailure(failure)
        }
    }

    private suspend fun loadSearchFirstPages(query: String) = coroutineScope {
        NewsCategory.entries.forEach { category ->
            launch { loadSearchSection(category, query) }
        }
    }

    private suspend fun loadSearchSection(category: NewsCategory, query: String) {
        try {
            val page = repository.articles(category, FeedScope.ALL, query = query)
            if (!feed.isSearchOpen || feed.activeSearchQuery != query) return
            val updated = feed.searchSections.toMutableMap().apply {
                this[category] = SearchSectionState(
                    category = category,
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
                val current = this[category] ?: SearchSectionState(category)
                this[category] = current.copy(
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

    fun retrySearch(category: NewsCategory) {
        val query = feed.activeSearchQuery
        val section = feed.searchSections[category] ?: return
        if (query.isBlank() || section.isLoading) return
        feed = feed.copy(searchSections = feed.searchSections + (
            category to section.copy(isLoading = true, error = null)
        ))
        viewModelScope.launch { loadSearchSection(category, query) }
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
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                feed = feed.copy(
                    isLoadingMore = false,
                    nextPageError = failure.message ?: "다음 기사를 불러오지 못했어요."
                )
            }
        }
    }

    fun loadMoreSearch(category: NewsCategory) {
        val query = feed.activeSearchQuery
        val section = feed.searchSections[category] ?: return
        val cursor = section.nextCursor ?: return
        if (query.isBlank() || section.isLoading || section.isLoadingMore) return
        feed = feed.copy(searchSections = feed.searchSections + (
            category to section.copy(isLoadingMore = true, nextPageError = null)
        ))
        viewModelScope.launch {
            try {
                val page = repository.articles(category, FeedScope.ALL, cursor, query)
                if (!feed.isSearchOpen || feed.activeSearchQuery != query) return@launch
                val current = feed.searchSections[category] ?: return@launch
                feed = feed.copy(searchSections = feed.searchSections + (
                    category to current.copy(
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
                val current = feed.searchSections[category] ?: return@launch
                feed = feed.copy(searchSections = feed.searchSections + (
                    category to current.copy(
                        isLoadingMore = false,
                        nextPageError = failure.message ?: "다음 검색 결과를 불러오지 못했어요."
                    )
                ))
            }
        }
    }

    private fun handleFeedFailure(failure: Exception) {
        if (isAuthFailure(failure)) authExpired = true
        feed = feed.copy(
            isLoading = false,
            isRefreshing = false,
            error = failure.message ?: "기사 목록을 불러오지 못했어요."
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
        try {
            val page = repository.savedArticles()
            if (route != NewsRoute.SAVED) return
            saved = saved.copy(
                items = page.items,
                nextCursor = page.nextCursor,
                isLoading = false,
                isRefreshing = false,
                error = null,
                nextPageError = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isAuthFailure(failure)) authExpired = true
            saved = saved.copy(
                isLoading = false,
                isRefreshing = false,
                error = failure.message ?: "저장한 기사를 불러오지 못했어요."
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
        val previousFeed = feed
        val previousReader = reader
        val previousSaved = saved
        savedMutationArticleId = articleId
        saveError = null
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
        viewModelScope.launch {
            try {
                repository.setArticleSaved(articleId, target)
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                feed = previousFeed
                reader = previousReader
                saved = previousSaved
                saveError = failure.message ?: "저장 상태를 바꾸지 못했어요."
            } finally {
                savedMutationArticleId = null
            }
        }
    }

    fun openArticle(articleId: String) {
        if (route != NewsRoute.READER) readerOrigin = route
        route = NewsRoute.READER
        readerJob?.cancel()
        reader = ArticleReaderState(
            articleId = articleId,
            analysisEnabled = reader.analysisEnabled,
            isArticleLoading = true
        )
        readerJob = viewModelScope.launch {
            try {
                val article = repository.article(articleId)
                reader = reader.copy(article = article, isArticleLoading = false, isAnalysisLoading = true)
                try {
                    val analysis = repository.analysis(article)
                    reader = reader.copy(analysis = analysis, isAnalysisLoading = false)
                } catch (failure: Exception) {
                    if (isAuthFailure(failure)) authExpired = true
                    reader = reader.copy(
                        isAnalysisLoading = false,
                        analysisError = failure.message ?: "분석 표시를 불러오지 못했어요."
                    )
                }
            } catch (failure: Exception) {
                if (isAuthFailure(failure)) authExpired = true
                reader = reader.copy(
                    isArticleLoading = false,
                    articleError = failure.message ?: "기사를 불러오지 못했어요."
                )
            }
        }
    }

    fun retryReader() = openArticle(reader.articleId)

    fun closeReader() {
        readerJob?.cancel()
        route = readerOrigin
    }

    fun setAnalysisEnabled(enabled: Boolean) {
        reader = reader.copy(analysisEnabled = enabled)
        repository.saveAnalysisEnabled(enabled)
    }

    fun consumeAuthExpired() {
        authExpired = false
    }

    private fun isAuthFailure(failure: Exception): Boolean =
        failure is NewsApiException && failure.code == "AUTH_REQUIRED"

    private fun normalizeSearch(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
}
