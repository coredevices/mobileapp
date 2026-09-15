package io.rebble.libpebblecommon.database

import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomTypeConvertersTest {
    private val converters = RoomTypeConverters()

    @Test
    fun capabilitySetRoundTrips() {
        val capabilities = setOf(
            ProtocolCapsFlag.SupportsAppRunStateProtocol,
            ProtocolCapsFlag.SupportsWeatherApp,
        )
        assertEquals(
            capabilities,
            converters.StringToCapabilitySet(converters.CapabilitySetToString(capabilities)),
        )
    }

    @Test
    fun capabilitySetIgnoresUnknownNames() {
        assertEquals(
            setOf(ProtocolCapsFlag.SupportsWeatherApp),
            converters.StringToCapabilitySet(
                """["SupportsWeatherApp","SupportsSomethingFromTheFuture"]"""
            ),
        )
    }
}
