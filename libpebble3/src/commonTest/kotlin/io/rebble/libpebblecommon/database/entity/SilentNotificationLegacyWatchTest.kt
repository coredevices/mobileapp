package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SilentNotificationLegacyWatchTest {
    private val fw = FirmwareVersion(
        stringVersion = "v0.0.0",
        timestamp = Instant.DISTANT_PAST,
        major = 0,
        minor = 0,
        patch = 0,
        suffix = null,
        gitHash = "",
        isRecovery = false,
        isDualSlot = false,
        isSlot0 = false,
    )

    private fun params(capabilities: Set<ProtocolCapsFlag>, respectDnd: Boolean) = ValueParams(
        platform = WatchType.BASALT,
        capabilities = capabilities,
        firmwareVersion = fw,
        libPebbleConfigFlow = LibPebbleConfigFlow(
            MutableStateFlow(LibPebbleConfig(notificationConfig = NotificationConfig(respectDoNotDisturb = respectDnd)))
        ),
    )

    private fun notification(silent: Boolean) = buildTimelineNotification(
        parentId = Uuid.random(),
        timestamp = Instant.fromEpochSeconds(1757376000),
    ) {
        itemID = Uuid.random()
        layout = TimelineItem.Layout.GenericNotification
        if (silent) flags { silent() }
        attributes { title { "Hi" } }
    }

    private val legacy = emptySet<ProtocolCapsFlag>()
    private val modern = setOf(ProtocolCapsFlag.SupportsBlobDbVersion)

    @Test
    fun silentNotificationDroppedForLegacyWatchWhenRespectingDnd() {
        assertTrue(notification(silent = true).skipForWatch(params(legacy, respectDnd = true)))
    }

    @Test
    fun silentNotificationSentToLegacyWatchWhenNotRespectingDnd() {
        assertFalse(notification(silent = true).skipForWatch(params(legacy, respectDnd = false)))
    }

    @Test
    fun silentNotificationSentToWatchWhichHandlesTheFlag() {
        assertFalse(notification(silent = true).skipForWatch(params(modern, respectDnd = true)))
    }

    @Test
    fun normalNotificationSentToLegacyWatch() {
        assertFalse(notification(silent = false).skipForWatch(params(legacy, respectDnd = true)))
    }
}
