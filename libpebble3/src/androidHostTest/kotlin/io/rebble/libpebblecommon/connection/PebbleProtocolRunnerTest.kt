package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.packets.PingPong
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class PebbleProtocolRunnerTest {
    @Test
    fun `dispatch throws instead of wedging when a subscriber stalls`() = runTest {
        val streams = PebbleProtocolStreams()
        val runner = PebbleProtocolRunner(streams, PebbleSocketIdentifier("test"))
        val subscriber = launch {
            streams.inboundMessagesFlow.collect { awaitCancellation() }
        }
        streams.inboundMessagesFlow.subscriptionCount.first { it > 0 }

        val message = InboundPPMessage(PingPong.Ping(1u), ubyteArrayOf())
        var thrown: Throwable? = null
        for (i in 0 until 150) {
            val result = runCatching { runner.dispatch(message) }
            if (result.isFailure) {
                thrown = result.exceptionOrNull()
                break
            }
        }

        assertTrue("expected IllegalStateException, got $thrown", thrown is IllegalStateException)
        subscriber.cancel()
    }
}
