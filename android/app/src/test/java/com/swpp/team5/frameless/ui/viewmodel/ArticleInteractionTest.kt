package com.swpp.team5.frameless.ui.viewmodel

import android.os.Looper
import com.swpp.team5.frameless.data.*
import com.swpp.team5.frameless.ui.screens.reactionCountLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
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
class ArticleInteractionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loop get() = shadowOf(Looper.getMainLooper())
    private val source = Source()
    private var now = 0L
    private var changed = 0
    private var expired = 0
    private val reactions = ReaderReactions(source, scope, { changed++ }, { expired++ }, { now })
    @After fun stop() { scope.cancel(); loop.idle() }

    @Test fun selectionCancellationRetryAndFreshCacheUseConfirmedState() {
        reactions.load("a"); loop.idle()
        reactions.load("a"); loop.idle()
        assertEquals(1, source.reads)
        val gate = CompletableDeferred<ArticleReactions>()
        source.write = { _, _ -> gate.await() }
        reactions.select("a", ReactionKind.LIKE); loop.idle()
        reactions.select("a", ReactionKind.SAD); loop.idle()
        assertTrue(reactions.state("a").pending)
        assertNull(reactions.state("a").page!!.selected)
        assertEquals(1, source.writes.size)
        gate.completeExceptionally(IllegalStateException("failed")); loop.idle()
        assertNotNull(reactions.state("a").error)
        source.write = { id, kind -> page(id, kind) }
        reactions.retry("a"); loop.idle()
        assertEquals(ReactionKind.LIKE, reactions.state("a").page!!.selected)
        assertEquals(1, changed)
        reactions.select("a", ReactionKind.LIKE); loop.idle()
        assertNull(source.writes.last().second)
        assertNull(reactions.state("a").page!!.selected)
        now = 60_000
        reactions.load("a"); loop.idle()
        assertEquals(2, source.reads)
    }

    @Test fun otherArticleAndViewerAreIsolatedFromLateWrites() {
        reactions.load("a"); reactions.load("b"); loop.idle()
        reactions.load("a"); loop.idle()
        assertEquals(2, source.reads)
        val gate = CompletableDeferred<ArticleReactions>()
        source.write = { id, kind -> if (id == "a") withContext(NonCancellable) { gate.await() } else page(id, kind) }
        reactions.select("a", ReactionKind.ANGRY); loop.idle()
        reactions.select("b", ReactionKind.MOVED); loop.idle()
        assertTrue(reactions.state("a").pending)
        assertEquals(ReactionKind.MOVED, reactions.state("b").page!!.selected)
        reactions.reset()
        gate.complete(page("a", ReactionKind.ANGRY)); loop.idle()
        assertTrue(reactions.states.isEmpty())
        assertEquals(1, changed)
        source.read = { throw NewsApiException("AUTH_REQUIRED", "expired") }
        reactions.load("c"); loop.idle()
        assertEquals(1, expired)
    }

    @Test fun highlightsExpireWithElapsedTimeAndDoNotPollOrUseExpiredFailureCache() {
        val article = ArticleSummary("a", PublisherItem("news", "뉴스", null), "[속보] 소식", NewsCategory.SOCIETY,
            "2026-11-25T00:00:00Z", StoredAnalysisState.READY)
        source.highlights = { HomeHighlights("2026-11-25T00:00:00Z", BreakingHeadline(article, "2026-11-25T00:00:20Z"), null) }
        val home = HomeHeadlines(source, scope, { expired++ }, { now })
        home.load(); loop.idle()
        home.load(); loop.idle()
        assertEquals(1, source.homeReads)
        now = 20_000; loop.idleFor(20, TimeUnit.SECONDS)
        assertNull(home.state.page!!.breaking)
        assertEquals(1, source.homeReads)
        source.highlights = { throw IllegalStateException("offline") }
        now = 60_000; home.load(); loop.idle()
        assertNull(home.state.page)
        assertEquals(2, source.homeReads)
        source.highlights = { HomeHighlights("2026-11-25T00:00:00Z", null, null) }
        home.load(); loop.idle()
        home.invalidate(); home.load(); loop.idle()
        assertEquals(4, source.homeReads)
        home.reset()
        assertNull(home.state.page)
    }

    @Test fun countLabelsUseFloorAndKeepIntegerPrecision() {
        assertEquals("0", reactionCountLabel(0))
        assertEquals("9,999", reactionCountLabel(9_999))
        assertEquals("1만", reactionCountLabel(10_000))
        assertEquals("1.2만", reactionCountLabel(12_999))
        assertEquals("1억", reactionCountLabel(100_000_000))
        assertEquals("21.4억", reactionCountLabel(2_147_483_647))
    }

    @Test fun resumingAfterSleepExpiresBreakingEvenWhenTheCacheIsStillFresh() {
        val article = ArticleSummary("a", PublisherItem("news", "뉴스", null), "[속보] 소식", NewsCategory.SOCIETY,
            "2026-11-25T00:00:00Z", StoredAnalysisState.READY)
        source.highlights = { HomeHighlights("2026-11-25T00:00:00Z", BreakingHeadline(article, "2026-11-25T00:00:20Z"), null) }
        val home = HomeHeadlines(source, scope, {}, { now })
        home.load(); loop.idle()
        // Advance elapsed time without delivering a Handler callback, as in device sleep.
        now = 21_000
        home.load(); loop.idle()
        assertNull(home.state.page!!.breaking)
        assertEquals(1, source.homeReads)
    }

    private fun page(id: String, selected: ReactionKind?) = ArticleReactions(id,
        ReactionKind.entries.associateWith { if (it == selected) 1L else 0L }, selected)

    private inner class Source : NewsDataSource {
        var reads = 0
        var homeReads = 0
        val writes = mutableListOf<Pair<String, ReactionKind?>>()
        var read: suspend (String) -> ArticleReactions = { page(it, null) }
        var write: suspend (String, ReactionKind?) -> ArticleReactions = { id, kind -> page(id, kind) }
        var highlights: suspend () -> HomeHighlights = { HomeHighlights("2026-11-25T00:00:00Z", null, null) }
        override suspend fun reactions(articleId: String): ArticleReactions { reads++; return read(articleId) }
        override suspend fun setReaction(articleId: String, kind: ReactionKind?): ArticleReactions {
            writes += articleId to kind; return write(articleId, kind)
        }
        override suspend fun homeHighlights(): HomeHighlights { homeReads++; return highlights() }
        override suspend fun publishers() = emptyList<PublisherItem>()
        override suspend fun replaceFollowedPublishers(publisherCodes: Set<String>) = publisherCodes
        override suspend fun articles(category: NewsCategory?, scope: FeedScope, cursor: String?, query: String?) = error("unused")
        override suspend fun article(articleId: String): ArticleDetail = error("unused")
        override suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi = error("unused")
        override suspend fun savedArticles(cursor: String?): ArticlePage = error("unused")
        override suspend fun setArticleSaved(articleId: String, saved: Boolean) = Unit
        override fun savedCategory() = NewsCategory.POLITICS
        override fun savedAnalysisEnabled() = true
        override fun saveCategory(category: NewsCategory) = Unit
        override fun saveAnalysisEnabled(enabled: Boolean) = Unit
    }
}
