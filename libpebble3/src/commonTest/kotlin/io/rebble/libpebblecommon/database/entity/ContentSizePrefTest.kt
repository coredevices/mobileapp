package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.metadata.WatchType
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentSizePrefTest {
    @Test
    fun systemTextSizeRoundTripsOnEmery() {
        val prefId = EnumWatchPref.SystemTextSize.id
        for (size in ContentSize.entries) {
            val wire = size.code.applyOffsetForSendToWatch(prefId, WatchType.EMERY)
            val decoded = wire.applyOffsetForReceiveFromWatch(prefId, WatchType.EMERY)
            assertEquals(size.code, decoded, size.displayName)
        }
    }

    @Test
    fun notificationSameAsSystemIsNotOffsetOnEmery() {
        val prefId = EnumWatchPref.NotificationTextSize.id
        val wire = NotificationContentSize.SameAsSystem.code.applyOffsetForSendToWatch(
            prefId,
            WatchType.EMERY,
        )
        assertEquals(NotificationContentSize.SameAsSystem.code, wire)
        val decoded = wire.applyOffsetForReceiveFromWatch(prefId, WatchType.EMERY)
        assertEquals(NotificationContentSize.SameAsSystem.code, decoded)
    }

    @Test
    fun notificationTextSizeDecodesSameAsSystem() {
        val pref = EnumWatchPref.NotificationTextSize
        val decoded = pref.decodeValue(NotificationContentSize.SameAsSystem.code.toString())
        assertEquals(NotificationContentSize.SameAsSystem, decoded)
    }
}
