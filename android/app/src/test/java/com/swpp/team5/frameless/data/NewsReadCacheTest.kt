package com.swpp.team5.frameless.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NewsReadCacheTest {
    @Test fun freshnessExpiryFailureAndBoundedReuse() = runBlocking {
        var now = 0L
        var calls = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cache = NewsReadCache({ now }, scope, capacity = 2)
        suspend fun read(key: String) = cache.read(key, 100) { ++calls }
        assertEquals(1, read("a"))
        assertEquals(1, read("a"))
        now = 100
        assertEquals(2, read("a"))
        read("b"); read("a"); read("c")
        assertEquals(5, read("b")) // b was the least recently used entry.
        var attempts = 0
        assertTrue(runCatching { cache.read("failed", 100) { attempts++; error("offline") } }.isFailure)
        assertEquals("ok", cache.read("failed", 100) { attempts++; "ok" })
        assertEquals(2, attempts)
        scope.cancel()
    }

    @Test fun concurrentReadsSurviveOneConsumersCancellation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cache = NewsReadCache({ 0 }, scope)
        val gate = CompletableDeferred<String>()
        var calls = 0
        suspend fun read() = cache.read("same", 100) { calls++; gate.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { read() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { read() }
        first.cancelAndJoin()
        gate.complete("shared")
        assertEquals("shared", second.await())
        assertEquals(1, calls)
        scope.cancel()
    }

    @Test fun invalidatedLateCompletionCannotReplaceNewResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cache = NewsReadCache({ 0 }, scope)
        val gate = CompletableDeferred<String>()
        val old = async(start = CoroutineStart.UNDISPATCHED) { cache.read("viewer", 100) { gate.await() } }
        cache.invalidate()
        assertEquals("new", cache.read("viewer", 100) { "new" })
        gate.complete("old")
        assertEquals("old", old.await())
        assertEquals("new", cache.read("viewer", 100) { error("should reuse new result") })
        scope.cancel()
    }
}
