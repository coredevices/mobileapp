package coredevices.mcp.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CachedValueTest {

    private class FakeClock : Clock {
        private var current = Instant.fromEpochSeconds(0)
        override fun now() = current
        fun advance(by: Duration) { current += by }
    }

    private val clock = FakeClock()
    private var fetches = 0
    private val cached = CachedValue<String>(clock)

    private suspend fun get(maxAge: Duration = 1.minutes) = cached.get(maxAge) { "value${++fetches}" }

    @Test
    fun servesTheCachedValueWhileFresh() = runBlocking {
        assertEquals("value1", get())
        clock.advance(59.seconds)
        assertEquals("value1", get())
        assertEquals(1, fetches)
    }

    @Test
    fun refetchesOnceOlderThanMaxAge() = runBlocking {
        get()
        clock.advance(61.seconds)
        assertEquals("value2", get())
    }

    @Test
    fun maxAgeIsTheReadersChoice() = runBlocking {
        get(maxAge = 1.minutes)
        clock.advance(30.seconds)
        assertEquals("value1", get(maxAge = 1.minutes))
        assertEquals("value2", get(maxAge = 10.seconds))
    }

    @Test
    fun invalidateForcesARefetch() = runBlocking {
        get()
        cached.invalidate()
        assertEquals("value2", get())
    }

    @Test
    fun aFetchStartedBeforeInvalidationIsReturnedButNotKept() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val inFlight = async { cached.get(1.minutes) { gate.await(); "stale" } }
        yield()
        cached.invalidate()
        gate.complete(Unit)
        assertEquals("stale", inFlight.await())
        assertEquals("value1", get())
    }

    @Test
    fun concurrentReadersShareOneFetch() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val readers = List(3) { async { cached.get(1.minutes) { gate.await(); "value${++fetches}" } } }
        yield()
        gate.complete(Unit)
        assertEquals(listOf("value1", "value1", "value1"), readers.awaitAll())
    }

    @Test
    fun valuesSharingAGenerationAreInvalidatedTogether() = runBlocking {
        val generation = CacheGeneration()
        val first = CachedValue<Int>(clock, generation)
        val second = CachedValue<Int>(clock, generation)
        suspend fun read(value: CachedValue<Int>) = value.get(1.minutes) { ++fetches }
        read(first)
        read(second)
        generation.bump()
        read(first)
        read(second)
        assertEquals(4, fetches)
    }
}
