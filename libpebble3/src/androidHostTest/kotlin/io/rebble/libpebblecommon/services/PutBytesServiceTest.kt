package io.rebble.libpebblecommon.services

import TestPebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.PutBytesPut
import io.rebble.libpebblecommon.packets.PutBytesResponse
import io.rebble.libpebblecommon.packets.PutBytesResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PutBytesServiceTest {
    private fun ackResponse(cookie: UInt) = PutBytesResponse().apply {
        result.set(PutBytesResult.ACK.value)
        this.cookie.set(cookie)
    }

    @Test
    fun staleResponseIsDiscardedBeforeNewRequest() = runTest {
        val handler = TestPebbleProtocolHandler { packet ->
            if (packet is PutBytesPut) {
                receivePacket(ackResponse(cookie = 2u))
            }
        }
        val service = PutBytesService(handler, ConnectionCoroutineScope(backgroundScope.coroutineContext))
        service.init()
        testScheduler.runCurrent()

        service.receivedMessages.send(ackResponse(cookie = 1u))
        val response = service.sendPut(2u, ubyteArrayOf(1u, 2u, 3u))

        assertEquals(2u, response.cookie.get())
    }
}
