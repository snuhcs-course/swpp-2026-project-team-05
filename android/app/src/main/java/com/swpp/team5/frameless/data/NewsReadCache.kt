package com.swpp.team5.frameless.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

internal enum class NewsConnection { ONLINE, OFFLINE, UNVERIFIED }

/** Short-lived response reuse. A caller owns its wait, not the shared request. */
internal class NewsReadCache(
    private val clock: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val capacity: Int = 64
) {
    private data class Entry(val value: Any, val at: Long)
    private val entries = LinkedHashMap<Any, Entry>(16, .75f, true)
    private val requests = mutableMapOf<Any, Deferred<Any>>()
    private val lock = Any()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> read(
        key: Any,
        freshFor: Long,
        cacheIf: (T) -> Boolean = { true },
        load: suspend () -> T
    ): T {
        val request = synchronized(lock) {
            entries[key]?.let { entry ->
                val age = clock() - entry.at
                if (age in 0 until freshFor) return entry.value as T
                entries.remove(key)
            }
            requests[key] ?: run {
                lateinit var task: Deferred<Any>
                task = scope.async(start = CoroutineStart.LAZY) {
                    val result = load()
                    synchronized(lock) {
                        // An invalidated request may finish, but cannot restore old data.
                        if (requests[key] === task && cacheIf(result)) {
                            entries[key] = Entry(result, clock())
                            while (entries.size > capacity) entries.remove(entries.keys.first())
                        }
                    }
                    result
                }
                requests[key] = task
                task.invokeOnCompletion {
                    synchronized(lock) { if (requests[key] === task) requests.remove(key) }
                }
                task
            }
        }
        request.start()
        return request.await() as T
    }

    fun invalidate(predicate: (Any) -> Boolean = { true }) = synchronized(lock) {
        entries.keys.removeAll(predicate)
        // Do not cancel other consumers. Detached completions cannot repopulate entries.
        requests.keys.removeAll(predicate)
        Unit
    }
}
