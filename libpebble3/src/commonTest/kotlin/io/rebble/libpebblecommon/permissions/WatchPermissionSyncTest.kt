package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.packets.blobdb.AppMetadata
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WatchPermissionSyncTest {
    @Test
    fun microphoneGrantTravelsInTheExistingAppMetadata() {
        val legacy = AppMetadata().toBytes()
        val denied = AppMetadata(microphoneGranted = false).toBytes()
        val granted = AppMetadata(microphoneGranted = true).toBytes()
        assertEquals(126, legacy.size)
        assertEquals(130, granted.size)
        assertContentEquals(legacy, granted.copyOfRange(0, 126))
        assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), denied.copyOfRange(126, 130))
        assertContentEquals(ubyteArrayOf(1u, 0u, 0u, 0u), granted.copyOfRange(126, 130))
    }
    @Test
    fun lockerSyncGatesTheExtensionAndHashesPermissionChanges() {
        val id = Uuid.random()
        val platform = LockerEntryPlatform(id, "5.109", 0, "emery", "", "", "", 0)
        val entry = LockerEntry(id, "1.0", "Test", "watchapp", "Core", false, "0",
            platforms = listOf(platform), microphoneGranted = true)
        fun params(capabilities: Set<ProtocolCapsFlag>, enabled: Boolean = true) = ValueParams(
            platform = WatchType.EMERY,
            capabilities = capabilities,
            firmwareVersion = FirmwareVersion("v0.0.0", Instant.DISTANT_PAST, 0, 0, 0, null, "", false, false, false),
            libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(
                LibPebbleConfig(watchConfig = WatchConfig(enablePlugins = enabled)))),
        )
        assertEquals(126, entry.value(params(emptySet()))!!.size)
        val capable = setOf(ProtocolCapsFlag.SupportsAppPermissions)
        assertEquals(1u.toUByte(), entry.value(params(capable))!![126])
        assertEquals(1u.toUByte(), entry.value(params(capable, enabled = false))!![126])
        assertNotEquals(entry.recordHashCode(), entry.copy(microphoneGranted = false).recordHashCode())
    }

}
