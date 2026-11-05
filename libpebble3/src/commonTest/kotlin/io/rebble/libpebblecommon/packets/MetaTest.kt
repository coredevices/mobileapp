package io.rebble.libpebblecommon.packets

import assertIs
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class MetaTest {
    /** A packet as it arrives: big-endian length, big-endian endpoint, payload. */
    private fun received(vararg payload: UByte) =
        ubyteArrayOf(0u, payload.size.toUByte(), 0u, 0u) + payload

    @Test
    fun anEndpointTheWatchDoesNotHandleIsNamed() {
        // What a recovery firmware answers a ping with: endpoint 2001, unhandled.
        val packet = PebblePacket.deserialize(received(0xDCu, 0x07u, 0xD1u))

        assertIs<MetaMessage>(packet)
        assertEquals(MetaMessage.Error.Unhandled.value, packet.error.get())
        assertEquals(ProtocolEndpoint.PING.value, packet.rejectedEndpoint.get())
    }

    @Test
    fun anEndpointTheWatchWillNotAllowIsNamedTheSameWay() {
        val packet = PebblePacket.deserialize(received(0xDDu, 0x07u, 0xD1u))

        assertIs<MetaMessage>(packet)
        assertEquals(MetaMessage.Error.Disallowed.value, packet.error.get())
        assertEquals(ProtocolEndpoint.PING.value, packet.rejectedEndpoint.get())
    }

    @Test
    fun aCorruptedMessageNamesNoEndpoint() {
        // The firmware cannot attribute this one, so it sends the code alone and
        // the endpoint has to read as absent rather than as zero.
        val packet = PebblePacket.deserialize(received(0xD0u))

        assertIs<MetaMessage>(packet)
        assertEquals(MetaMessage.Error.CorruptedMessage.value, packet.error.get())
        assertNull(packet.rejectedEndpoint.get())
    }
}
