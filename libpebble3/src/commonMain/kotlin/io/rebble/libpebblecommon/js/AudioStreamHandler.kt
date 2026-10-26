package io.rebble.libpebblecommon.js

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds

/** Subscribe before accepting the session, including while the JS runner is starting. */
internal suspend fun streamAudioToJs(
    sampleRate: Int,
    pcm: Flow<ByteArray>,
    readyState: StateFlow<Boolean>,
    signal: suspend (String) -> Unit,
    onReady: suspend () -> Unit,
): Boolean = coroutineScope {
    val chunks = Channel<ByteArray>(Channel.BUFFERED)
    val producer = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            pcm.collect { chunks.send(it) }
        } finally {
            chunks.close()
        }
    }
    // Batch so JS is not woken up per codec frame.
    val batchBytes = sampleRate / 10 * Short.SIZE_BYTES
    val pending = Buffer()
    var started = false
    try {
        if (withTimeoutOrNull(6.seconds) { readyState.first { it } } == null) {
            return@coroutineScope false
        }
        started = true
        signal(buildJsonObject {
            put("type", "audiostreamstart")
            put("sampleRate", sampleRate)
            put("format", "pcm16le")
        }.toString())
        onReady()
        for (chunk in chunks) {
            pending.write(chunk)
            while (pending.size >= batchBytes) {
                signal(audioStreamDataJson(pending.readByteArray(batchBytes)))
            }
        }
        true
    } finally {
        producer.cancel()
        chunks.cancel()
        if (started) {
            withContext(NonCancellable) {
                // A stopped JS runner must not hold up session cancellation indefinitely.
                withTimeoutOrNull(1.seconds) {
                    try {
                        if (pending.size > 0) signal(audioStreamDataJson(pending.readByteArray()))
                    } finally {
                        signal(buildJsonObject { put("type", "audiostreamstop") }.toString())
                    }
                }
            }
        }
    }
}

private fun audioStreamDataJson(batch: ByteArray): String = buildJsonObject {
    put("type", "audiostream")
    put("data", Base64.encode(batch))
}.toString()
