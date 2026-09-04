package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.uuid.Uuid

/**
 * Built-in plugin for the state of the phone itself. One instance — the phone — with
 * `battery_level`, `cell`, `wifi` and `mobile_data` properties under `phone/phone_state`.
 *
 * `cell` is which generation the radio is on; `mobile_data` is whether anything is actually
 * going over it, which is false whenever Wi-Fi is carrying the traffic instead.
 */
class PhoneStatePlugin(
    private val phoneBattery: PhoneBatteryMonitor,
    private val phoneNetwork: PhoneNetworkMonitor,
) : Plugin {
    override val pluginUuid: Uuid = BUILT_IN_PHONE_STATE_UUID
    override val name: String = "Phone"

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_PHONE_STATE),
            properties = mapOf(
                PROPERTY_BATTERY_LEVEL to listOf(
                    SourceShapeNames.NUMERIC_VALUE,
                    SourceShapeNames.SHORT_TEXT,
                ),
                PROPERTY_CELL to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_WIFI to listOf(
                    SourceShapeNames.BOOLEAN,
                    SourceShapeNames.SHORT_TEXT,
                ),
                PROPERTY_MOBILE_DATA to listOf(
                    SourceShapeNames.BOOLEAN,
                    SourceShapeNames.SHORT_TEXT,
                ),
            ),
            supportsMultiple = false,
            suggestedRefreshIntervalSec = 60,
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        return combine(
            phoneBattery.batteryLevel,
            phoneNetwork.cellGeneration,
            phoneNetwork.connection,
        ) { level, cell, connection -> toEnvelope(level, cell, connection) }
    }

    private fun toEnvelope(level: Int?, cell: String?, connection: String?): SourceEnvelope {
        val properties = buildMap {
            if (level != null) {
                put(
                    PROPERTY_BATTERY_LEVEL,
                    mapOf(
                        SourceShapeNames.NUMERIC_VALUE to encode(
                            NumericValueShape(
                                value = level.toDouble(),
                                unit = "%",
                                min = 0.0,
                                max = 100.0,
                            )
                        ),
                        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(text = "$level%")),
                    ),
                )
            }
            if (cell != null) {
                put(PROPERTY_CELL, mapOf(SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(cell))))
            }
            if (connection != null) {
                put(PROPERTY_WIFI, onOff(connection == Connections.WIFI, WIFI))
                put(PROPERTY_MOBILE_DATA, onOff(connection == Connections.CELLULAR, MOBILE))
            }
        }
        return SourceEnvelope(
            pluginUuid = pluginUuid.toString(),
            validUntilMs = null,
            instances = if (properties.isEmpty()) {
                emptyList()
            } else {
                listOf(SourceInstance(instanceId = INSTANCE_ID, properties = properties))
            },
        )
    }

    private fun onOff(on: Boolean, label: String) = mapOf(
        SourceShapeNames.BOOLEAN to encode(BooleanShape(on)),
        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(if (on) label else OFF)),
    )

    private inline fun <reified T> encode(value: T): JsonElement =
        Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "phone"
        const val ITEM_PHONE_STATE = "phone_state"
        const val PROPERTY_BATTERY_LEVEL = "battery_level"
        const val PROPERTY_CELL = "cell"
        const val PROPERTY_WIFI = "wifi"
        const val PROPERTY_MOBILE_DATA = "mobile_data"

        private const val WIFI = "Wi-Fi"
        private const val MOBILE = "Mobile"
        private const val OFF = "Off"
        private const val INSTANCE_ID = "phone"

        // Reserved built-in UUID namespace: prefix 00000000-0000-0000-0001-* for built-ins.
        val BUILT_IN_PHONE_STATE_UUID: Uuid =
            Uuid.parse("00000000-0000-0000-0001-000000000001")
    }
}
