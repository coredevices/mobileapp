package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.endpointmanager.audio.streamAudioWhileGranted
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioStreamHandlerTest {
    @Test
    fun buffersFramesReceivedBeforeJsIsReady() = runTest {
        val ready = MutableStateFlow(false)
        val pcm = MutableSharedFlow<ByteArray>()
        val events = mutableListOf<String>()
        var accepted = false
        val delivery = async {
            streamAudioToJs(20, pcm, ready, events::add) { accepted = true }
        }
        runCurrent()
        assertEquals(1, pcm.subscriptionCount.value)
        pcm.emit(byteArrayOf(1, 2, 3, 4))
        runCurrent()
        assertFalse(accepted)
        assertTrue(events.isEmpty())

        ready.value = true
        runCurrent()
        assertTrue(accepted)
        assertEquals(listOf("audiostreamstart", "audiostream"), events.map(::eventType))
        assertEquals("AQIDBA==", Json.parseToJsonElement(events[1]).jsonObject["data"]?.jsonPrimitive?.content)
        delivery.cancelAndJoin()
        assertEquals("audiostreamstop", eventType(events.last()))
        assertEquals(0, pcm.subscriptionCount.value)
    }

    @Test
    fun subscribesBeforeSetupSuccessCanTriggerWatchFrames() = runTest {
        val pcm = MutableSharedFlow<ByteArray>()
        val events = mutableListOf<String>()
        val delivery = launch {
            streamAudioToJs(20, pcm, MutableStateFlow(true), events::add) {
                assertEquals(1, pcm.subscriptionCount.value)
                pcm.emit(byteArrayOf(5, 6, 7, 8))
            }
        }
        runCurrent()
        assertEquals(listOf("audiostreamstart", "audiostream"), events.map(::eventType))
        assertEquals("BQYHCA==", Json.parseToJsonElement(events[1]).jsonObject["data"]?.jsonPrimitive?.content)
        delivery.cancelAndJoin()
    }

    @Test
    fun cancellationFlushesPartialBatchAndSendsStop() = runTest {
        val pcm = MutableSharedFlow<ByteArray>()
        val events = mutableListOf<String>()
        val delivery = launch {
            streamAudioToJs(20, pcm, MutableStateFlow(true), events::add) {}
        }
        runCurrent()
        pcm.emit(byteArrayOf(1, 2))
        runCurrent()
        assertEquals(listOf("audiostreamstart"), events.map(::eventType))
        delivery.cancelAndJoin()
        assertEquals(listOf("audiostreamstart", "audiostream", "audiostreamstop"), events.map(::eventType))
        assertEquals("AQI=", Json.parseToJsonElement(events[1]).jsonObject["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun normalCompletionFlushesPartialBatchAndSendsStop() = runTest {
        val pcm = Channel<ByteArray>(Channel.BUFFERED)
        val events = mutableListOf<String>()
        var accepted = false
        pcm.send(byteArrayOf(1, 2))
        pcm.close()
        assertTrue(streamAudioToJs(20, pcm.receiveAsFlow(), MutableStateFlow(true), events::add) {
            accepted = true
        })
        assertTrue(accepted)
        assertEquals(listOf("audiostreamstart", "audiostream", "audiostreamstop"), events.map(::eventType))
    }

    @Test
    fun cancellationDuringStartStillSendsStop() = runTest {
        val events = mutableListOf<String>()
        var accepted = false
        val delivery = launch {
            streamAudioToJs(20, MutableSharedFlow(), MutableStateFlow(true), { event ->
                events.add(event)
                if (eventType(event) == "audiostreamstart") awaitCancellation()
            }) { accepted = true }
        }
        runCurrent()
        delivery.cancelAndJoin()
        assertFalse(accepted)
        assertEquals(listOf("audiostreamstart", "audiostreamstop"), events.map(::eventType))
    }

    @Test
    fun stalledJsCleanupHasBoundedWait() = runTest {
        val events = mutableListOf<String>()
        val delivery = launch {
            streamAudioToJs(20, MutableSharedFlow(), MutableStateFlow(true), { event ->
                events.add(event)
                if (eventType(event) == "audiostreamstop") awaitCancellation()
            }) {}
        }
        runCurrent()
        delivery.cancelAndJoin()
        assertEquals(listOf("audiostreamstart", "audiostreamstop"), events.map(::eventType))
        assertTrue(delivery.isCompleted)
    }

    @Test
    fun unreadyJsTimesOutWithoutAcceptingStream() = runTest {
        val pcm = MutableSharedFlow<ByteArray>()
        val events = mutableListOf<String>()
        var accepted = false
        assertFalse(streamAudioToJs(20, pcm, MutableStateFlow(false), events::add) { accepted = true })
        assertFalse(accepted)
        assertTrue(events.isEmpty())
        assertEquals(0, pcm.subscriptionCount.value)
    }

    @Test
    fun permissionRevocationStopsDeliveryAndUnsubscribesFromFrames() = runTest {
        val granted = MutableStateFlow(setOf(PluginPermissions.WATCH_MICROPHONE))
        val pcm = MutableSharedFlow<ByteArray>()
        val events = mutableListOf<String>()
        var accepted = false
        val delivery = async {
            streamAudioWhileGranted(granted) {
                streamAudioToJs(20, pcm, MutableStateFlow(true), events::add) { accepted = true }
            }
        }
        runCurrent()
        assertTrue(accepted)
        pcm.emit(byteArrayOf(1, 2, 3, 4))
        runCurrent()
        granted.value = emptySet()
        runCurrent()
        assertFalse(delivery.await())
        assertEquals(listOf("audiostreamstart", "audiostream", "audiostreamstop"), events.map(::eventType))
        assertEquals(0, pcm.subscriptionCount.value)
        granted.value = setOf(PluginPermissions.WATCH_MICROPHONE)
        pcm.emit(byteArrayOf(5, 6, 7, 8))
        runCurrent()
        assertEquals(3, events.size)
    }

    @Test
    fun aRevokedPermissionBeforeJsIsReadyNeverAcceptsTheSession() = runTest {
        val granted = MutableStateFlow(setOf(PluginPermissions.WATCH_MICROPHONE))
        val pcm = MutableSharedFlow<ByteArray>()
        val events = mutableListOf<String>()
        var accepted = false
        val delivery = async {
            streamAudioWhileGranted(granted) {
                streamAudioToJs(20, pcm, MutableStateFlow(false), events::add) { accepted = true }
            }
        }
        runCurrent()
        assertEquals(1, pcm.subscriptionCount.value)
        granted.value = emptySet()
        runCurrent()
        assertFalse(delivery.await())
        assertFalse(accepted)
        assertTrue(events.isEmpty())
        assertEquals(0, pcm.subscriptionCount.value)
    }

    @Test
    fun permissionGuardPreservesNormalStreamCompletion() = runTest {
        val pcm = Channel<ByteArray>(Channel.BUFFERED)
        pcm.close()
        assertTrue(streamAudioWhileGranted(flowOf(setOf(PluginPermissions.WATCH_MICROPHONE))) {
            streamAudioToJs(20, pcm.receiveAsFlow(), MutableStateFlow(true), {}) {}
        })
    }

    private fun eventType(json: String): String =
        Json.parseToJsonElement(json).jsonObject.getValue("type").jsonPrimitive.content
}
