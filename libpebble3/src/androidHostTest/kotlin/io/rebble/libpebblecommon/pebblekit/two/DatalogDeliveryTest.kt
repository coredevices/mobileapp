package io.rebble.libpebblecommon.pebblekit.two

import io.rebble.libpebblecommon.datalogging.ThirdPartyDatalogEvent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val WATCHAPP = Uuid.parse("f06ba3b8-10da-4c7c-9c38-4a79144b0f5b")

private fun batch(seq: Int, itemsLeft: UInt = 0u) = ThirdPartyDatalogEvent.Batch(
    uuid = WATCHAPP,
    tag = 100u,
    timestamp = 1000u,
    itemSize = 4u,
    watchSerial = "SERIAL",
    itemsLeft = itemsLeft,
    data = byteArrayOf(seq.toByte(), 0, 0, 0),
)

private fun finished() = ThirdPartyDatalogEvent.Finished(
    uuid = WATCHAPP,
    tag = 100u,
    timestamp = 1000u,
    itemSize = 4u,
    watchSerial = "SERIAL",
)

private class FakeSender(
    private val results: MutableList<Boolean> = mutableListOf(),
    private val defaultResult: Boolean = true,
) : DatalogSender {
    val sent = mutableListOf<ThirdPartyDatalogEvent>()
    var closed = false

    private fun next(): Boolean =
        if (results.isNotEmpty()) results.removeAt(0) else defaultResult

    override suspend fun sendBatch(event: ThirdPartyDatalogEvent.Batch): Boolean {
        sent += event
        return next()
    }

    override suspend fun sendFinished(event: ThirdPartyDatalogEvent.Finished): Boolean {
        sent += event
        return next()
    }

    override fun close() {
        closed = true
    }
}

class DatalogDeliveryTest {
    @Test
    fun deliversBatchesInOrderThenFinish() = runTest {
        val sender = FakeSender()
        val delivery = DatalogDelivery(this, { sender })
        delivery.enqueue(batch(1, 2u))
        delivery.enqueue(batch(2, 1u))
        delivery.enqueue(batch(3, 0u))
        delivery.enqueue(finished())
        advanceUntilIdle()
        assertEquals(4, sender.sent.size)
        assertEquals(
            listOf<Byte>(1, 2, 3),
            sender.sent.take(3).map { (it as ThirdPartyDatalogEvent.Batch).data[0] },
        )
        assertTrue(sender.sent[3] is ThirdPartyDatalogEvent.Finished)
        assertTrue(sender.closed)
    }

    @Test
    fun retriesANackedBatchUntilAck() = runTest {
        val sender = FakeSender(results = mutableListOf(false, false, true))
        val delivery = DatalogDelivery(this, { sender })
        delivery.enqueue(batch(1))
        advanceUntilIdle()
        assertEquals(3, sender.sent.size)
    }

    @Test
    fun discardsTheSessionAfterTheAttemptsAreUsedUp() = runTest {
        val sender = FakeSender(defaultResult = false)
        val delivery = DatalogDelivery(this, { sender })
        delivery.enqueue(batch(1))
        delivery.enqueue(batch(2))
        delivery.enqueue(finished())
        advanceUntilIdle()
        // Three attempts for batch 1; batch 2 and the finish are discarded
        assertEquals(3, sender.sent.size)
        assertTrue(sender.sent.all { (it as ThirdPartyDatalogEvent.Batch).data[0] == 1.toByte() })
        assertTrue(sender.closed)
    }

    @Test
    fun aSenderExceptionIsAFailedAttemptNotACrash() = runTest {
        var calls = 0
        val sender = object : DatalogSender {
            override suspend fun sendBatch(event: ThirdPartyDatalogEvent.Batch): Boolean {
                calls++
                if (calls == 1) throw RuntimeException("boom")
                return true
            }

            override suspend fun sendFinished(event: ThirdPartyDatalogEvent.Finished): Boolean {
                return true
            }

            override fun close() {}
        }
        val delivery = DatalogDelivery(this, { sender })
        delivery.enqueue(batch(1))
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun noCompanionAppConsumesAndDiscardsTheSession() = runTest {
        val delivery = DatalogDelivery(this, { null })
        delivery.enqueue(batch(1))
        delivery.enqueue(finished())
        // Completing without a hang is the assertion
        advanceUntilIdle()
    }

    @Test
    fun aNewEventAfterIdleStartsANewWorker() = runTest {
        var resolves = 0
        val sender = FakeSender()
        val delivery = DatalogDelivery(this, {
            resolves++
            sender
        })
        delivery.enqueue(batch(1))
        advanceUntilIdle()
        delivery.enqueue(batch(2))
        advanceUntilIdle()
        assertEquals(2, resolves)
        assertEquals(2, sender.sent.size)
        assertTrue(sender.closed)
    }
}
