package io.rebble.libpebblecommon.packets

import kotlin.test.Test
import kotlin.test.assertEquals

internal class VoiceTest {
    @Test
    fun audioStreamSetupResultCarriesTheSessionId() {
        val packet = AudioStreamSetupResult(Result.Success, 0x1234u).apply { flags.set(1u) }

        // command, flags (LE), session type, result, audio endpoint session id (LE)
        assertEquals(
            listOf<UByte>(0x01u, 0x01u, 0x00u, 0x00u, 0x00u, 0x04u, 0x00u, 0x34u, 0x12u),
            packet.serialize().toList().takeLast(9),
        )
        assertEquals(13, packet.serialize().size)
    }
}
