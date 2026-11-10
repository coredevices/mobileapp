package coredevices.util.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile

/** Mirrors the receiver until it completes or [signal] emits, whichever comes first. */
fun <T> Flow<T>.takeUntil(signal: Flow<*>): Flow<T> {
    val stop = Any()
    val source = map<T, Any?> { it }.onCompletion { cause -> if (cause == null) emit(stop) }
    return merge(source, signal.take(1).map { stop })
        .takeWhile { it !== stop }
        .map {
            @Suppress("UNCHECKED_CAST")
            it as T
        }
}
