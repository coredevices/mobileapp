package coredevices.util.flow

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * Maps a [StateFlow] without needing a scope: [value] is recomputed from the source on every read,
 * so [transform] must be cheap and pure.
 */
fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> = MappedStateFlow(this, transform)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class MappedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R,
) : StateFlow<R> {
    override val value: R get() = transform(source.value)
    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing =
        source.collect { collector.emit(transform(it)) }
}
