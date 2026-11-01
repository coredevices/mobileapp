package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.util.DataBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

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

class TimelineAttributeCapabilityTest {
    private fun params(capabilities: Set<ProtocolCapsFlag>) = ValueParams(
        platform = WatchType.EMERY,
        capabilities = capabilities,
        firmwareVersion = FW_TEST,
        libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(LibPebbleConfig())),
    )

    private val pin = buildTimelinePin(
        parentId = Uuid.random(),
        timestamp = Instant.fromEpochSeconds(1757376000),
    ) {
        itemID = Uuid.random()
        layout = TimelineItem.Layout.WeatherPin
        attributes {
            title { "Sunrise" }
            uByte(TimelineAttribute.WeatherPinKind) { 1u }
        }
    }

    private fun sentAttributeIds(capabilities: Set<ProtocolCapsFlag>): List<UByte> {
        val bytes = pin.value(params(capabilities))!!
        val item = TimelineItem()
        item.fromBytes(DataBuffer(bytes))
        return item.attributes.list.map { it.attributeId.get() }
    }

    @Test
    fun weatherPinKindIsDroppedWithoutTheCapability() {
        assertEquals(listOf(TimelineAttribute.Title.id), sentAttributeIds(emptySet()))
    }

    @Test
    fun weatherPinKindIsSentWithTheCapability() {
        assertEquals(
            listOf(TimelineAttribute.Title.id, TimelineAttribute.WeatherPinKind.id),
            sentAttributeIds(setOf(ProtocolCapsFlag.SupportsUnknownTimelineAttributes)),
        )
    }
}
