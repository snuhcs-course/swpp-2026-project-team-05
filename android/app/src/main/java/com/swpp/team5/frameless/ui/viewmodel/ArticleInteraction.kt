package com.swpp.team5.frameless.ui.viewmodel

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swpp.team5.frameless.data.ArticleReactions
import com.swpp.team5.frameless.data.HomeHighlights
import com.swpp.team5.frameless.data.NewsApiException
import com.swpp.team5.frameless.data.NewsDataSource
import com.swpp.team5.frameless.data.ReactionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

internal data class ReactionIntent(val kind: ReactionKind?)
internal data class ArticleReactionState(
    val page: ArticleReactions? = null,
    val loading: Boolean = false,
    val pending: Boolean = false,
    val intent: ReactionIntent? = null,
    val error: String? = null
)

/** State belongs to an article and viewer, never to the currently visible Reader slot. */
internal class ReaderReactions(
    private val repository: NewsDataSource,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val onAuthFailure: () -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {
    var states by mutableStateOf(emptyMap<String, ArticleReactionState>())
        private set
    private val loadedAt = mutableMapOf<String, Long>()
    private val jobs = mutableMapOf<String, Job>()
    fun state(id: String) = states[id] ?: ArticleReactionState()

    fun load(id: String, force: Boolean = false) {
        if (jobs[id]?.isActive == true) return
        if (!force && loadedAt[id]?.let { clock() - it < 60_000 } == true) return
        update(id, state(id).copy(loading = true, error = null, intent = null))
        request(id, null)
    }

    fun select(id: String, kind: ReactionKind) {
        val state = state(id)
        val page = state.page ?: return
        if (state.pending) return
        mutate(id, ReactionIntent(kind.takeUnless { page.selected == it }))
    }

    fun retry(id: String) {
        val state = state(id)
        if (state.pending) return
        state.intent?.let { mutate(id, it) } ?: load(id, force = true)
    }

    private fun mutate(id: String, intent: ReactionIntent) {
        jobs.remove(id)?.cancel()
        update(id, state(id).copy(pending = true, loading = false, intent = intent, error = null))
        request(id, intent)
    }

    private fun update(id: String, value: ArticleReactionState) { states = states + (id to value) }

    private fun request(id: String, intent: ReactionIntent?) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = if (intent == null) repository.reactions(id) else repository.setReaction(id, intent.kind)
                currentCoroutineContext().ensureActive()
                loadedAt[id] = clock()
                update(id, ArticleReactionState(page = page))
                if (intent != null) onChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                update(id, state(id).copy(loading = false, pending = false,
                    error = if (intent == null) "반응을 불러오지 못했어요" else "반응을 저장하지 못했어요"))
                if ((failure as? NewsApiException)?.code == "AUTH_REQUIRED") onAuthFailure()
            } finally {
                if (jobs[id] === currentCoroutineContext()[Job]) jobs.remove(id)
            }
        }
        jobs[id] = job
        job.start()
    }

    fun reset() {
        val active = jobs.values.toList()
        jobs.clear()
        active.forEach { it.cancel() }
        loadedAt.clear()
        states = emptyMap()
    }
}

internal data class HomeHighlightsState(val page: HomeHighlights? = null, val loading: Boolean = false)

/** A one-minute read cache and a local expiry timer; neither performs background polling. */
internal class HomeHeadlines(
    private val repository: NewsDataSource,
    private val scope: CoroutineScope,
    private val onAuthFailure: () -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {
    var state by mutableStateOf(HomeHighlightsState())
        private set
    private var loadedAt: Long? = null
    private var job: Job? = null
    private var expiry: Job? = null
    private var breakingDeadline: Long? = null

    private fun expireBreaking() {
        if (breakingDeadline?.let { clock() >= it } != true) return
        state = state.copy(page = state.page?.copy(breaking = null))
        breakingDeadline = null
        expiry?.cancel()
        expiry = null
    }

    fun load(force: Boolean = false) {
        // Handler delays pause during device sleep; the elapsed deadline still expires on resume.
        expireBreaking()
        if (job?.isActive == true) return
        if (!force && loadedAt?.let { clock() - it < 60_000 } == true) return
        val started = clock()
        state = state.copy(loading = true)
        job = scope.launch {
            try {
                val page = repository.homeHighlights()
                currentCoroutineContext().ensureActive()
                loadedAt = clock()
                state = HomeHighlightsState(page)
                expiry?.cancel()
                breakingDeadline = null
                page.breaking?.let { breaking ->
                    val duration = Duration.between(OffsetDateTime.parse(page.generatedAt),
                        OffsetDateTime.parse(breaking.expiresAt)).toMillis()
                    val remaining = (duration - (clock() - started)).coerceAtLeast(0)
                    breakingDeadline = clock() + remaining
                    if (remaining == 0L) expireBreaking()
                    else expiry = scope.launch {
                        delay(remaining)
                        expireBreaking()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                val fresh = loadedAt?.let { clock() - it < 60_000 } == true
                state = HomeHighlightsState(state.page.takeIf { fresh })
                if (!fresh) {
                    expiry?.cancel()
                    breakingDeadline = null
                }
                if ((failure as? NewsApiException)?.code == "AUTH_REQUIRED") onAuthFailure()
            }
        }
    }

    fun invalidate() {
        job?.cancel()
        job = null
        loadedAt = null
        state = state.copy(loading = false)
    }

    fun reset() {
        invalidate()
        expiry?.cancel()
        breakingDeadline = null
        state = HomeHighlightsState()
    }
}
