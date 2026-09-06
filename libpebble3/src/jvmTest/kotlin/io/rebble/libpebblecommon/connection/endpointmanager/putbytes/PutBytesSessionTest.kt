package io.rebble.libpebblecommon.connection.endpointmanager.putbytes

import TestPebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.PutBytesInstall
import io.rebble.libpebblecommon.packets.PutBytesResponse
import io.rebble.libpebblecommon.packets.PutBytesResult
import io.rebble.libpebblecommon.services.PutBytesService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class PutBytesSessionTest {
    private fun ack(cookie: UInt) = PutBytesResponse().apply {
        result.set(PutBytesResult.ACK.value)
        this.cookie.set(cookie)
    }

    /** A session whose watch answers every install with [answerWith]. */
    private suspend fun TestScope.sessionAnswering(answerWith: UInt): PutBytesSession {
        val handler = TestPebbleProtocolHandler { packet ->
            if (packet is PutBytesInstall) {
                receivePacket(ack(answerWith))
            }
        }
        val service = PutBytesService(
            handler,
            ConnectionCoroutineScope(backgroundScope.coroutineContext),
        )
        service.init()
        testScheduler.runCurrent()
        return PutBytesSession(service)
    }

    @Test
    fun anInstallAnsweredWithItsOwnCookieIsAccepted() = runTest {
        sessionAnswering(answerWith = 7u).sendInstall(7u)
    }

    @Test
    fun anInstallAnsweredWithZeroIsAccepted() = runTest {
        // What the firmware actually sends: prv_cleanup_and_send_response reads
        // s_pb_state.token, which the preceding commit already cleared.
        sessionAnswering(answerWith = 0u).sendInstall(7u)
    }

    @Test
    fun anInstallAnsweredForAnotherTransferIsNot() = runTest {
        val session = sessionAnswering(answerWith = 8u)

        assertFailsWith<IllegalStateException> { session.sendInstall(7u) }
    }
}
