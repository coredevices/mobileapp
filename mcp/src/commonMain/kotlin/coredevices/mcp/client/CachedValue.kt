package coredevices.mcp.client

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Invalidation token. Values that go stale together (a prompt list and the prompt contents
 * fetched from it) share one generation so a single bump drops all of them.
 */
@OptIn(ExperimentalAtomicApi::class)
class CacheGeneration {
    private val counter = AtomicInt(0)

    val current: Int get() = counter.load()

    fun bump() {
        counter.incrementAndFetch()
    }
}

/**
 * A value fetched on demand and kept until it is older than the reader's `maxAge` or its
 * generation is bumped.
 */
class CachedValue<T : Any>(
    private val clock: Clock,
    private val generation: CacheGeneration = CacheGeneration(),
) {
    private class Entry<T>(val value: T, val fetchedAt: Instant, val generation: Int)

    private val mutex = Mutex()
    private var entry: Entry<T>? = null

    fun invalidate() = generation.bump()

    suspend fun get(maxAge: Duration, fetch: suspend () -> T): T = mutex.withLock {
        val started = generation.current
        entry?.takeIf { it.generation == started && clock.now() - it.fetchedAt <= maxAge }
            ?.let { return it.value }
        val value = fetch()
        if (generation.current == started) entry = Entry(value, clock.now(), started)
        value
    }
}
