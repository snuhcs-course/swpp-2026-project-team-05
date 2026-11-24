package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swpp.team5.frameless.data.NewsDataSource
import com.swpp.team5.frameless.data.TrendPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.OffsetDateTime

internal data class TrendsState(
    val page: TrendPage? = null,
    val loading: Boolean = false,
    val error: String? = null
)

/** Independent from feed loading; account changes cancel personalized hydration. */
internal class RecentTrends(
    private val repository: NewsDataSource,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now
) {
    var state by mutableStateOf(TrendsState())
        private set
    private var job: Job? = null
    private var expiryJob: Job? = null
    private var requestedAt: Instant? = null

    fun load(force: Boolean = false) {
        if (job?.isActive == true) return
        val instant = now()
        val pageAge = state.page?.let { instant.epochSecond - OffsetDateTime.parse(it.calculatedAt).toEpochSecond() }
        if (pageAge != null && pageAge > 900) state = state.copy(page = null)
        if (!force && pageAge != null && pageAge < 300 && state.error == null) return
        // Explicit retry also respects the short failure backoff.
        if ((state.error != null || state.page?.isStale == true) &&
            requestedAt?.let { instant.epochSecond - it.epochSecond < 60 } == true) return
        requestedAt = instant
        state = state.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val page = repository.trends()
                currentCoroutineContext().ensureActive()
                state = TrendsState(page = page)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                val cached = state.page?.takeIf {
                    now().epochSecond - OffsetDateTime.parse(it.calculatedAt).toEpochSecond() <= 900
                }?.copy(isStale = true)
                state = TrendsState(page = cached, error = "트렌드를 불러오지 못했어요")
            }
            expiryJob?.cancel()
            state.page?.let { page ->
                expiryJob = scope.launch {
                    val expiresIn = (OffsetDateTime.parse(page.calculatedAt).toInstant().toEpochMilli() +
                        900_001 - now().toEpochMilli()).coerceAtLeast(0)
                    delay(expiresIn)
                    state = TrendsState(error = "최신 트렌드를 확인해 주세요")
                }
            }
        }
    }

    fun reset() {
        job?.cancel()
        expiryJob?.cancel()
        requestedAt = null
        state = TrendsState()
    }
}

/** A visit survives rotation; foreground interruptions restart only the pending timer. */
internal class QualifiedReadTracker(
    private val scope: CoroutineScope,
    private val onRead: suspend (String) -> Unit
) {
    private var job: Job? = null
    private var attempted = false
    private var target: Pair<String, String?>? = null

    fun newVisit() {
        job?.cancel()
        attempted = false
        target = null
    }

    fun update(articleId: String?, foreground: Boolean, identity: String?) {
        val next = articleId?.takeIf { foreground }?.let { it to identity }
        if (next == target) return
        job?.cancel()
        target = next
        if (next == null || attempted) return
        job = scope.launch {
            delay(3_000)
            attempted = true
            try {
                onRead(next.first)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Best-effort analytics never interrupts reading or queues offline work.
            }
        }
    }
}
