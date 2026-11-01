package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val UNITS = SourceRequest("preferences", "units")
private val LOCATIONS = SourceRequest("preferences", "weather_location")

private fun data(n: Int, validUntilMs: Long? = null) =
    SourceUpdate.Data(SourceEnvelope("p", validUntilMs, listOf(SourceInstance(n.toString(), emptyMap()))))

@OptIn(ExperimentalCoroutinesApi::class)
class PluginUpstreamsTest {
    private val values = mutableMapOf<SourceRequest, MutableStateFlow<SourceUpdate>>()
    private val opened = mutableListOf<SourceRequest>()
    private val closed = mutableListOf<SourceRequest>()
    private val reruns = mutableListOf<Pair<Set<Int>, SourceRequest>>()

    private fun TestScope.upstreams(max: Int = 8) = PluginUpstreams(
        backgroundScope,
        { request ->
            opened += request
            values.getOrPut(request) { MutableStateFlow(data(0)) }.onCompletion { closed += request }
        },
        { owners, trigger -> reruns += owners to trigger },
        debounce = 1.seconds,
        max = max,
    )

    private fun PluginUpstreams.run(owners: Set<Int>, vararg declared: SourceRequest) {
        beginRun(owners)
        declared.forEach { declare(it) }
        endRun()
    }

    @Test
    fun aChangeRerunsTheOwnersDebouncedButTheFirstValueDoesNot() = runTest {
        val upstreams = upstreams()
        upstreams.run(setOf(1), UNITS)
        runCurrent()
        assertEquals(data(0), upstreams.latest(UNITS))
        advanceTimeBy(5.seconds)
        assertTrue(reruns.isEmpty())

        values.getValue(UNITS).value = data(1)
        advanceTimeBy(500.milliseconds)
        values.getValue(UNITS).value = data(2)
        advanceTimeBy(1500.milliseconds)

        assertEquals(listOf(setOf(1) to UNITS), reruns)
        assertEquals(data(2), upstreams.latest(UNITS))
    }

    @Test
    fun aNewValidityAloneIsntAChange() = runTest {
        val upstreams = upstreams()
        upstreams.run(setOf(1), UNITS)
        runCurrent()
        values.getValue(UNITS).value = data(0, validUntilMs = 123)
        advanceTimeBy(2.seconds)
        assertTrue(reruns.isEmpty())
    }

    @Test
    fun ownersShareOneSubscriptionThatEndsWithTheLast() = runTest {
        val upstreams = upstreams()
        upstreams.run(setOf(1), UNITS)
        upstreams.run(setOf(2), UNITS)
        runCurrent()
        assertEquals(listOf(UNITS), opened)

        upstreams.ownerGone(1)
        runCurrent()
        assertTrue(closed.isEmpty())
        upstreams.ownerGone(2)
        runCurrent()
        assertEquals(listOf(UNITS), closed)
        assertNull(upstreams.latest(UNITS))
    }

    @Test
    fun whatALaterRunDoesntDeclareIsDropped() = runTest {
        val upstreams = upstreams()
        upstreams.run(setOf(1), UNITS, LOCATIONS)
        runCurrent()
        upstreams.run(setOf(1), LOCATIONS)
        runCurrent()
        assertEquals(setOf(LOCATIONS), upstreams.subscribed)
        assertEquals(listOf(UNITS), closed)
    }

    @Test
    fun aRunWithoutSubscribersKeepsNothing() = runTest {
        val upstreams = upstreams()
        upstreams.beginRun(emptySet())
        assertEquals(false, upstreams.declare(UNITS))
        upstreams.endRun()
        assertEquals(false, upstreams.declare(UNITS))
        assertTrue(upstreams.subscribed.isEmpty())
    }

    @Test
    fun subscriptionsAreCapped() = runTest {
        val upstreams = upstreams(max = 1)
        upstreams.beginRun(setOf(1))
        assertEquals(true, upstreams.declare(UNITS))
        assertEquals(false, upstreams.declare(LOCATIONS))
        upstreams.endRun()
        assertEquals(setOf(UNITS), upstreams.subscribed)
    }
}
