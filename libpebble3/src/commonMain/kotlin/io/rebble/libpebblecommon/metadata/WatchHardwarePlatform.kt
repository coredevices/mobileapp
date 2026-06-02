package io.rebble.libpebblecommon.metadata

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

enum class WatchHardwarePlatform(val protocolNumber: UByte, private val _watchType: WatchType, val revision: String, val displayName: String, val isConsumer: Boolean = false) {
    UNKNOWN(0u, WatchType.BASALT, "unknown", "Unknown (Basalt)"),
    PEBBLE_ONE_EV_1(1u, WatchType.APLITE, "ev1", "Pebble One EV1 (Aplite)"),
    PEBBLE_ONE_EV_2(2u, WatchType.APLITE, "ev2", "Pebble One EV2 (Aplite)"),
    PEBBLE_ONE_EV_2_3(3u, WatchType.APLITE, "ev2_3", "Pebble One EV2.3 (Aplite)"),
    PEBBLE_ONE_EV_2_4(4u, WatchType.APLITE, "ev2_4", "Pebble One EV2.4 (Aplite)"),
    PEBBLE_ONE_POINT_FIVE(5u, WatchType.APLITE, "v1_5", "Pebble One V1.5 (Aplite)"),
    PEBBLE_TWO_POINT_ZERO(6u, WatchType.APLITE, "v2_0", "Pebble Two V2.0 (Aplite)"),
    PEBBLE_SNOWY_EVT_2(7u, WatchType.BASALT, "snowy_evt2", "Pebble Time EVT2 (Basalt)"),
    PEBBLE_SNOWY_DVT(8u, WatchType.BASALT, "snowy_dvt", "Pebble Time DVT (Basalt)"),
    PEBBLE_BOBBY_SMILES(10u, WatchType.BASALT, "snowy_s3", "Pebble Time (Basalt)", isConsumer = true),
    PEBBLE_ONE_BIGBOARD_2(254u, WatchType.APLITE, "bb2", "Pebble One Bigboard 2 (Aplite)"),
    PEBBLE_ONE_BIGBOARD(255u, WatchType.APLITE, "bigboard", "Pebble One Bigboard (Aplite)"),
    PEBBLE_SNOWY_BIGBOARD(253u, WatchType.BASALT, "snowy_bb2", "Pebble Time Bigboard (Basalt)"),
    PEBBLE_SNOWY_BIGBOARD_2(252u, WatchType.BASALT, "unk", "Pebble Time Bigboard 2 (Basalt)"),
    PEBBLE_SPALDING_EVT(9u, WatchType.CHALK, "spalding_evt", "Pebble Time Round EVT (Chalk)"),
    PEBBLE_SPALDING_PVT(11u, WatchType.CHALK, "spalding", "Pebble Time Round (Chalk)", isConsumer = true),
    PEBBLE_SPALDING_BIGBOARD(251u, WatchType.CHALK, "spalding_bb2", "Pebble Time Round Bigboard (Chalk)"),
    PEBBLE_SILK_EVT(12u, WatchType.DIORITE, "silk_evt", "Pebble 2 EVT (Diorite)"),
    PEBBLE_SILK(14u, WatchType.DIORITE, "silk", "Pebble 2 (Diorite)", isConsumer = true),
    CORE_ASTERIX(15u, WatchType.FLINT, "asterix", "Core Asterix (Flint)", isConsumer = true),
    CORE_OBELIX_EVT(16u, WatchType.EMERY, "obelix_evt", "Core Obelix EVT (Emery)"),
    CORE_OBELIX_DVT(17u, WatchType.EMERY, "obelix_dvt", "Core Obelix DVT (Emery)"),
    CORE_OBELIX_PVT(18u, WatchType.EMERY, "obelix_pvt", "Core Obelix (Emery)", isConsumer = true),
    CORE_GETAFIX_EVT(19u, WatchType.GABBRO, "getafix_evt", "Core Getafix EVT (Gabbro)"),
    CORE_GETAFIX_DVT(20u, WatchType.GABBRO, "getafix_dvt", "Core Getafix DVT (Gabbro)"),
    CORE_GETAFIX_DVT2(21u, WatchType.GABBRO, "getafix_dvt2", "Core Getafix DVT2 (Gabbro)"),
    PEBBLE_SILK_BIGBOARD(250u, WatchType.DIORITE, "silk_bb", "Pebble 2 Bigboard (Diorite)"),
    PEBBLE_SILK_BIGBOARD_2_PLUS(248u, WatchType.DIORITE, "silk_bb2", "Pebble 2 Bigboard 2+ (Diorite)"),
    PEBBLE_ROBERT_EVT(13u, WatchType.EMERY, "robert_evt", "Pebble 2+ EVT (Emery)"),
    PEBBLE_ROBERT_BIGBOARD(249u, WatchType.EMERY, "robert_bb", "Pebble 2+ Bigboard (Emery)"),
    PEBBLE_ROBERT_BIGBOARD_2(247u, WatchType.EMERY, "robert_bb2", "Pebble 2+ Bigboard 2 (Emery)"),
    CORE_OBELIX_BIGBOARD(244u, WatchType.EMERY, "obelix_bb", "Core Obelix Bigboard (Emery)"),
    CORE_OBELIX_BIGBOARD_2(243u, WatchType.EMERY, "obelix_bb2", "Core Obelix Bigboard 2 (Emery)"),
    ;

    val watchType: WatchType
        get() = when (this) {
            UNKNOWN -> unknownWatchTypePlatform
            else -> _watchType
        }

    companion object {
        private var unknownWatchTypePlatform: WatchType = WatchType.EMERY

        fun fromProtocolNumber(number: UByte): WatchHardwarePlatform {
            return entries.firstOrNull { it.protocolNumber == number } ?: UNKNOWN.also {
                Logger.d { "unknown hardware revision: $number" }
            }
        }

        fun fromHWRevision(revision: String?): WatchHardwarePlatform {
            if (revision == "unk") return UNKNOWN
            return entries.firstOrNull() { it.revision == revision } ?: UNKNOWN.also {
                Logger.d { "unknown hardware revision: $revision" }
            }
        }

        fun init(
            libPebbleCoroutineScope: LibPebbleCoroutineScope,
            libPebbleConfigFlow: StateFlow<LibPebbleConfig>,
        ) {
            libPebbleCoroutineScope.launch {
                libPebbleConfigFlow.collect {
                    unknownWatchTypePlatform = it.watchConfig.unknownWatchTypePlatform
                }
            }
        }
    }
}

@Serializer(WatchHardwarePlatform::class)
class WatchHardwarePlatformSerializer {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("WatchHardwarePlatform", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): WatchHardwarePlatform {
        val revision = decoder.decodeString()
        return WatchHardwarePlatform.fromHWRevision(revision) ?: throw SerializationException("Unknown hardware revision $revision")
    }

    override fun serialize(encoder: Encoder, value: WatchHardwarePlatform) {
        val revision = value.revision
        encoder.encodeString(revision)
    }

}