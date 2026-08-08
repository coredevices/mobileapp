package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val FW_TEST = FirmwareVersion(
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

class ChargeLimitValueTest {
    private val item = WatchPrefItem(
        id = EnumWatchPref.ChargeLimit.id,
        value = "80",
        timestamp = Instant.DISTANT_PAST.asMillisecond(),
    )

    private fun params(capabilities: Set<ProtocolCapsFlag>) = ValueParams(
        WatchType.APLITE,
        capabilities,
        FW_TEST,
        libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(LibPebbleConfig())),
    )

    @Test
    fun encodesWhenTheWatchSupportsChargeLimit() {
        assertContentEquals(
            ubyteArrayOf(80u),
            item.value(params(setOf(ProtocolCapsFlag.SupportsChargeLimit))),
        )
    }

    @Test
    fun skippedWhenTheWatchLacksTheCapability() {
        assertNull(item.value(params(emptySet())))
    }
}
