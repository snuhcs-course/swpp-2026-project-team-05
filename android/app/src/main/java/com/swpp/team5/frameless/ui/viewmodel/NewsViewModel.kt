package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsApiException
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsDataSource
import com.swpp.team5.frameless.data.PublisherItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class NewsRoute { FEED, READER }

internal data class NewsFeedState(
    val publishers: List<PublisherItem> = emptyList(),
    val items: List<ArticleSummary> = emptyList(),
    val category: NewsCategory = NewsCategory.POLITICS,
    val selectedPublishers: Set<String> = emptySet(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPageError: String? = null
)

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

internal class NewsViewModel(private val repository: NewsDataSource) : ViewModel() {
    var route by mutableStateOf(NewsRoute.FEED)
        private set
    var feed by mutableStateOf(
        NewsFeedState(
            category = repository.savedCategory(),
            selectedPublishers = repository.savedPublishers()
        )
    )
        private set
    var reader by mutableStateOf(ArticleReaderState(analysisEnabled = repository.savedAnalysisEnabled()))
        private set
    var authExpired by mutableStateOf(false)
        private set

    private var initialized = false
    private var feedJob: Job? = null
    private var readerJob: Job? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        feed = feed.copy(isLoading = true, error = null)
        feedJob = viewModelScope.launch {
            try {
                val publishers = repository.publishers()
                val known = publishers.map { it.code }.toSet()
                val selected = feed.selectedPublishers.intersect(known)
                feed = feed.copy(publishers = publishers, selectedPublishers = selected)
                loadFirstPage()
            } catch (failure: Exception) {
                handleFeedFailure(failure)
            }
        }
    }

    fun reset() {
        feedJob?.cancel()
        readerJob?.cancel()
        initialized = false
        route = NewsRoute.FEED
        feed = NewsFeedState(
            category = repository.savedCategory(),
            selectedPublishers = repository.savedPublishers()
        )
        reader = ArticleReaderState(analysisEnabled = repository.savedAnalysisEnabled())
        authExpired = false
    }

    fun retryFeed() {
        initialized = false
        initialize()
    }

    fun refresh() {
        if (feed.isRefreshing) return
        feedJob?.cancel()
        feed = feed.copy(isRefreshing = true, error = null)
        feedJob = viewModelScope.launch { loadFirstPage() }
    }

    fun selectCategory(category: NewsCategory) {
        if (feed.category == category) return
        feed = feed.copy(category = category)
        repository.saveFilters(category, feed.selectedPublishers)
        reloadForFilters()
    }

    fun applyPublishers(codes: Set<String>) {
        val valid = codes.intersect(feed.publishers.map { it.code }.toSet())
        if (valid == feed.selectedPublishers) return
        feed = feed.copy(selectedPublishers = valid)
        repository.saveFilters(feed.category, valid)
        reloadForFilters()
    }

    private fun reloadForFilters() {
        feedJob?.cancel()
        feed = feed.copy(
            items = emptyList(),
            nextCursor = null,
            isLoading = true,
            isRefreshing = false,
            isLoadingMore = false,
            error = null,
            nextPageError = null
        )
        feedJob = viewModelScope.launch { loadFirstPage() }
    }

    private suspend fun loadFirstPage() {
        try {
            val page = repository.articles(feed.category, feed.selectedPublishers)
            feed = feed.copy(
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
            handleFeedFailure(failure)
        }
    }

    fun loadMore() {
        val cursor = feed.nextCursor ?: return
        if (feed.isLoadingMore || feed.isLoading || feed.isRefreshing) return
        feed = feed.copy(isLoadingMore = true, nextPageError = null)
        feedJob = viewModelScope.launch {
            try {
                val page = repository.articles(feed.category, feed.selectedPublishers, cursor)
                feed = feed.copy(
                    items = feed.items + page.items,
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

    private fun handleFeedFailure(failure: Exception) {
        if (isAuthFailure(failure)) authExpired = true
        feed = feed.copy(
            isLoading = false,
            isRefreshing = false,
            error = failure.message ?: "기사 목록을 불러오지 못했어요."
        )
    }

    fun openArticle(articleId: String) {
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
        route = NewsRoute.FEED
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
}
