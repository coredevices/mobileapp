package io.rebble.libpebblecommon.database

import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class RoomTypeConvertersTest {
    @Test
    fun capabilitiesFromANewerBuildAreDropped() {
        assertEquals(
            setOf(ProtocolCapsFlag.SupportsAppRunStateProtocol),
            RoomTypeConverters().StringToCapabilitySet("""["SupportsAppRunStateProtocol","SupportsSomethingNew"]"""),
        )
    }

    @Test
    fun aLockerPlatformStoredBeforeTheTintFlagIsNotTintable() {
        val stored = """[{"lockerEntryId":"0d520cba-326c-4a22-a269-d1ab1ae38977","sdkVersion":"5.86","processInfoFlags":0,
            "name":"basalt","listImageUrl":"https://list","pbwIconResourceId":0}]"""
        assertFalse(RoomTypeConverters().StringToLockerPlatformList(stored).single().listImageTintable)
    }

    @Test
    fun theTintFlagSurvivesTheLockerPlatformColumn() {
        val converters = RoomTypeConverters()
        val platforms = listOf(
            LockerEntryPlatform(Uuid.random(), "5.86", 0, "basalt", pbwIconResourceId = 0, listImageTintable = true),
        )
        assertEquals(platforms, converters.StringToLockerPlatformList(converters.LockerPlatformListToString(platforms)))
    }
}
