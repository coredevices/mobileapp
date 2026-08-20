package io.rebble.libpebblecommon.pebblekit.two

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.datalogging.ThirdPartyDatalogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Sends datalogging events to one companion app. Returns true when the app stored the event. */
internal interface DatalogSender {
    suspend fun sendBatch(event: ThirdPartyDatalogEvent.Batch): Boolean
    suspend fun sendFinished(event: ThirdPartyDatalogEvent.Finished): Boolean
    fun close()
}

/**
 * Delivery state machine. One worker per session sends the batches in sequence, retries a
 * limited number of times, and sends the finish event after all batches were acknowledged.
 */
internal class DatalogDelivery(
    private val scope: CoroutineScope,
    private val resolveSender: suspend (Uuid) -> DatalogSender?,
    private val idleTimeout: Duration = 30.seconds,
    private val retryDelay: Duration = 10.seconds,
    private val sendTimeout: Duration = 30.seconds,
    private val attempts: Int = 3,
) {
    private val lock = Mutex()
    private val sessions = mutableMapOf<SessionKey, Channel<ThirdPartyDatalogEvent>>()

    suspend fun enqueue(event: ThirdPartyDatalogEvent) {
        lock.withLock {
            val key = event.sessionKey()
            val existing = sessions[key]
            if (existing != null && existing.trySend(event).isSuccess) return
            val channel = Channel<ThirdPartyDatalogEvent>(Channel.UNLIMITED)
            channel.trySend(event)
            sessions[key] = channel
            scope.launch {
                try {
                    runWorker(key, channel)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Datalogging delivery worker failed: ${e.message}" }
                    // Unregister so a later event starts a new worker
                    lock.withLock {
                        if (sessions[key] === channel) {
                            sessions.remove(key)
                            channel.close()
                        }
                    }
                }
            }
        }
    }

    private suspend fun runWorker(key: SessionKey, channel: Channel<ThirdPartyDatalogEvent>) {
        val sender = resolveSenderSafely(key.uuid)
        if (sender == null) {
            logger.d { "No companion app for watchapp ${key.uuid}; discarding datalogging session" }
            discardUntilIdle(key, channel)
            return
        }
        try {
            while (true) {
                val event = nextEventOrStop(key, channel) ?: return
                when (event) {
                    is ThirdPartyDatalogEvent.Batch -> {
                        if (!deliverWithRetry { sender.sendBatch(event) }) {
                            logger.w {
                                "Companion app did not store a datalogging batch " +
                                        "(uuid=${key.uuid} tag=${key.tag}); discarding the session"
                            }
                            discardUntilIdle(key, channel)
                            return
                        }
                    }

                    is ThirdPartyDatalogEvent.Finished -> {
                        if (!deliverWithRetry { sender.sendFinished(event) }) {
                            logger.w {
                                "Companion app did not acknowledge a datalogging session finish " +
                                        "(uuid=${key.uuid} tag=${key.tag})"
                            }
                        }
                    }
                }
            }
        } finally {
            sender.close()
        }
    }

    private suspend fun resolveSenderSafely(uuid: Uuid): DatalogSender? {
        return try {
            resolveSender(uuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to resolve the companion app for watchapp $uuid: ${e.message}" }
            null
        }
    }

    private suspend fun discardUntilIdle(key: SessionKey, channel: Channel<ThirdPartyDatalogEvent>) {
        while (nextEventOrStop(key, channel) != null) {
            // Discard
        }
    }

    /** Returns the next queued event, or null after the worker went idle and unregistered. */
    private suspend fun nextEventOrStop(
        key: SessionKey,
        channel: Channel<ThirdPartyDatalogEvent>,
    ): ThirdPartyDatalogEvent? {
        val received = withTimeoutOrNull(idleTimeout) { channel.receiveCatching().getOrNull() }
        if (received != null) return received
        lock.withLock {
            // Take an event that raced in after the timeout, or unregister
            val raced = channel.tryReceive().getOrNull()
            if (raced != null) return raced
            sessions.remove(key)
            channel.close()
        }
        return null
    }

    private suspend fun deliverWithRetry(send: suspend () -> Boolean): Boolean {
        repeat(attempts) { attempt ->
            val stored = try {
                withTimeoutOrNull(sendTimeout) { send() } == true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Datalogging delivery failed: ${e.message}" }
                false
            }
            if (stored) return true
            if (attempt < attempts - 1) delay(retryDelay)
        }
        return false
    }

    private data class SessionKey(
        val uuid: Uuid,
        val tag: UInt,
        val timestamp: UInt,
        val itemSize: UShort,
        val watchSerial: String,
    )

    private fun ThirdPartyDatalogEvent.sessionKey() =
        SessionKey(uuid, tag, timestamp, itemSize, watchSerial)

    companion object {
        private val logger = Logger.withTag(DatalogDelivery::class.simpleName!!)
    }
}
