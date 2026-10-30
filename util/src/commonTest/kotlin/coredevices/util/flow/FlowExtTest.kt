package coredevices.util.flow

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowExtTest {

    @Test
    fun takeUntilCompletesWhenSignalEmits() = runTest {
        val upstream = MutableSharedFlow<Int>(extraBufferCapacity = 4)
        val signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val received = mutableListOf<Int>()
        val job = launch { upstream.takeUntil(signal).collect { received += it } }
        runCurrent()
        upstream.emit(1)
        upstream.emit(2)
        runCurrent()
        signal.emit(Unit)
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(listOf(1, 2), received)
    }

    @Test
    fun takeUntilCompletesWhenUpstreamCompletes() = runTest {
        val signal = MutableSharedFlow<Unit>()
        assertEquals(listOf(1, 2, 3), flowOf(1, 2, 3).takeUntil(signal).toList())
    }

    @Test
    fun takeUntilPropagatesUpstreamFailure() = runTest {
        val signal = MutableSharedFlow<Unit>()
        assertFailsWith<IllegalStateException> {
            flow<Int> { error("boom") }.takeUntil(signal).toList()
        }
    }
}
