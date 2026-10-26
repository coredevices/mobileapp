package io.rebble.libpebblecommon.database.entity

import coredev.BlobDatabase
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import io.rebble.libpebblecommon.services.blobdb.WriteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SwipeQuickLaunchTest {
    private val swipes = listOf(
        QuicklaunchWatchPref.QlSwipeUp,
        QuicklaunchWatchPref.QlSwipeDown,
        QuicklaunchWatchPref.QlSwipeLeft,
        QuicklaunchWatchPref.QlSwipeRight,
    )
    private val timestamp = Instant.fromEpochSeconds(1757376000)
    private val params = ValueParams(
        platform = WatchType.BASALT,
        capabilities = setOf(ProtocolCapsFlag.SupportsSwipeShortcuts),
        firmwareVersion = FirmwareVersion(
            stringVersion = "v0.0.0", timestamp = Instant.DISTANT_PAST,
            major = 0, minor = 0, patch = 0, suffix = null, gitHash = "",
            isRecovery = false, isDualSlot = false, isSlot0 = false,
        ),
        libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(LibPebbleConfig())),
    )

    private fun item(pref: QuicklaunchWatchPref, setting: QuickLaunchSetting) = WatchPrefItem(
        id = pref.id,
        value = pref.encodeValue(setting),
        timestamp = timestamp.asMillisecond(),
    )

    @Test
    fun swipeCapabilityUsesFirmwareBit29() {
        val bytes = ubyteArrayOf(0u, 0u, 0u, 0x20u, 0u, 0u, 0u, 0u)
        assertContentEquals(
            bytes, ProtocolCapsFlag.makeFlags(listOf(ProtocolCapsFlag.SupportsSwipeShortcuts))
        )
        assertEquals(params.capabilities, ProtocolCapsFlag.fromFlags(bytes))
    }

    @Test
    fun sendsEnabledUuidAndReadsWatchChangesForEveryDirection() {
        val uuid = Uuid.parse("00112233-4455-6677-8899-aabbccddeeff")
        val setting = QuickLaunchSetting(true, uuid)
        val expected = ubyteArrayOf(1u) + uuid.toByteArray().toUByteArray()
        for (pref in swipes) {
            val item = item(pref, setting)
            assertEquals(pref, WatchPref.from(pref.id))
            assertContentEquals(expected, item.value(params))
            val write = DbWrite(
                token = 1u, database = BlobDatabase.WatchPrefs,
                timestamp = timestamp.epochSeconds.toUInt(), key = item.key(), value = expected,
                writeType = WriteType.Write,
            )
            assertEquals(item, write.asWatchPrefItem(params))
        }
    }

    @Test
    fun noneAndResetDisableEverySwipeUsingTheFirmwareInvalidUuid() {
        for (pref in swipes) {
            assertEquals(QuickLaunchSetting(false, null), pref.defaultValue)
            assertContentEquals(
                ubyteArrayOf(0u) + UByteArray(16) { 0xffu },
                item(pref, pref.defaultValue).value(params),
            )
        }
    }

    @Test
    fun unsupportedWatchDoesNotReceiveSwipePrefsEvenWhenAnotherWatchSupportsThem() {
        val unsupported = params.copy(capabilities = setOf(ProtocolCapsFlag.SupportsBlobDbVersion))
        for (pref in swipes) {
            assertNull(item(pref, QuickLaunchSetting(true, Uuid.random())).value(unsupported))
        }
        val button = QuicklaunchWatchPref.QlUp
        assertNotNull(item(button, button.defaultValue).value(unsupported))
    }
}
