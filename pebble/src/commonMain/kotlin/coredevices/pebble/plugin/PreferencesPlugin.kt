package coredevices.pebble.plugin

import androidx.compose.ui.text.intl.Locale
import com.russhwolf.settings.Settings
import coredevices.database.WeatherLocationDao
import coredevices.database.WeatherLocationEntity
import io.rebble.libpebblecommon.connection.ConnectedWatchInfo
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.WindUnits
import io.rebble.libpebblecommon.plugin.BooleanShape
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.NativePlugin
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.uuid.Uuid

/** A reading the user wants in a complication slot: instance [index] of `category/item`. */
@Serializable
data class ComplicationSlot(
    val category: String,
    val item: String,
    val index: Int,
    val property: String,
)

/**
 * Built-in plugin for how the user wants things shown, the readings they want in complication
 * slots, and their weather locations.
 */
class PreferencesPlugin(
    private val libPebble: LibPebble,
    private val weatherLocationDao: WeatherLocationDao,
    private val settings: Settings,
) : NativePlugin {
    override val pluginUuid: Uuid = BUILT_IN_PREFERENCES_UUID
    override val name: String = "Preferences"
    override val description: String =
        "The user's units and locale, the readings they want in complication slots, and their weather locations."

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_UNITS),
            properties = UNIT_PROPERTIES.associateWith { listOf(SourceShapeNames.SHORT_TEXT) },
        ),
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_COMPLICATIONS),
            properties = mapOf(
                PROPERTY_CATEGORY to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_ITEM to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_INDEX to listOf(SourceShapeNames.NUMERIC_VALUE),
                PROPERTY_PROPERTY to listOf(SourceShapeNames.SHORT_TEXT),
            ),
            supportsMultiple = true,
        ),
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_WEATHER_LOCATION),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_LATITUDE to listOf(SourceShapeNames.NUMERIC_VALUE),
                PROPERTY_LONGITUDE to listOf(SourceShapeNames.NUMERIC_VALUE),
                PROPERTY_CURRENT to listOf(SourceShapeNames.BOOLEAN),
            ),
            supportsMultiple = true,
            callerPermissions = listOf(PluginPermission(PluginPermissions.LOCATION)),
        ),
    )

    private val _complications by lazy { MutableStateFlow(loadComplications()) }

    /** Always [SLOT_COUNT] long; null is an empty slot. */
    val complications: StateFlow<List<ComplicationSlot?>> get() = _complications

    fun setComplication(position: Int, slot: ComplicationSlot?) =
        save(_complications.value.toMutableList().apply { set(position, slot) })

    private fun save(slots: List<ComplicationSlot?>) {
        _complications.value = slots
        settings.putString(COMPLICATIONS_KEY, Json.encodeToString(slots))
    }

    private fun loadComplications(): List<ComplicationSlot?> {
        val stored = settings.getStringOrNull(COMPLICATIONS_KEY)?.let {
            try {
                Json.decodeFromString<List<ComplicationSlot?>>(it)
            } catch (e: SerializationException) {
                null
            }
        } ?: DEFAULT_COMPLICATIONS
        return List(SLOT_COUNT) { stored.getOrNull(it) }
    }

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        return when (item) {
            ITEM_UNITS -> combine(libPebble.healthSettings, libPebble.watchPrefs, libPebble.watches) { health, prefs, watches ->
                val watchLanguage = watches.filterIsInstance<ConnectedWatchInfo>().firstOrNull()?.watchInfo?.language
                val locale = watchLanguage?.takeIf { it.isNotBlank() }?.replace('_', '-') ?: Locale.current.toLanguageTag()
                val units = units(
                    imperial = health.imperialUnits,
                    wind = prefs.valueOf(EnumWatchPref.WindSpeed) as? WindUnits ?: WindUnits.FromDistance,
                    clock24h = prefs.valueOf(BoolWatchPref.Clock24h) as? Boolean ?: false,
                    locale = locale,
                )
                envelope(listOf(SourceInstance(INSTANCE_UNITS, units.mapValues { text(it.value) })))
            }
            ITEM_COMPLICATIONS -> complications.map { slots ->
                envelope(slots.mapIndexedNotNull { position, slot -> slot?.let { SourceInstance(position.toString(), it.properties()) } })
            }
            else -> weatherLocationDao.getAllLocationsFlow().map { locations ->
                envelope(locations.map { SourceInstance(it.key.toString(), it.properties()) })
            }
        }
    }

    private fun List<WatchPreference<*>>.valueOf(pref: Any) = firstOrNull { it.pref == pref }?.valueOrDefault()

    private fun ComplicationSlot.properties() = mapOf(
        PROPERTY_CATEGORY to text(category),
        PROPERTY_ITEM to text(item),
        PROPERTY_INDEX to mapOf(SourceShapeNames.NUMERIC_VALUE to encode(NumericValueShape(index.toDouble()))),
        PROPERTY_PROPERTY to text(property),
    )

    private fun WeatherLocationEntity.properties() = buildMap {
        put(SourceInstance.PROPERTY_NAME, text(name))
        latitude?.let { put(PROPERTY_LATITUDE, mapOf(SourceShapeNames.NUMERIC_VALUE to encode(NumericValueShape(it)))) }
        longitude?.let { put(PROPERTY_LONGITUDE, mapOf(SourceShapeNames.NUMERIC_VALUE to encode(NumericValueShape(it)))) }
        put(PROPERTY_CURRENT, mapOf(SourceShapeNames.BOOLEAN to encode(BooleanShape(currentLocation))))
    }

    private fun envelope(instances: List<SourceInstance>) =
        SourceEnvelope(pluginUuid = pluginUuid.toString(), validUntilMs = null, instances = instances)

    private fun text(value: String) = mapOf(SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(value)))

    private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "preferences"
        const val ITEM_UNITS = "units"
        const val ITEM_COMPLICATIONS = "complications"
        const val ITEM_WEATHER_LOCATION = "weather_location"

        const val PROPERTY_LOCALE = "locale"
        const val PROPERTY_TEMPERATURE = "temperature"
        const val PROPERTY_DISTANCE = "distance"
        const val PROPERTY_WIND_SPEED = "wind_speed"
        const val PROPERTY_PRESSURE = "pressure"
        const val PROPERTY_PRECIPITATION = "precipitation"
        const val PROPERTY_CLOCK = "clock"
        private val UNIT_PROPERTIES = listOf(
            PROPERTY_LOCALE, PROPERTY_TEMPERATURE, PROPERTY_DISTANCE, PROPERTY_WIND_SPEED,
            PROPERTY_PRESSURE, PROPERTY_PRECIPITATION, PROPERTY_CLOCK,
        )

        const val PROPERTY_CATEGORY = "category"
        const val PROPERTY_ITEM = "item"
        const val PROPERTY_INDEX = "index"
        const val PROPERTY_PROPERTY = "property"

        const val PROPERTY_LATITUDE = "latitude"
        const val PROPERTY_LONGITUDE = "longitude"
        const val PROPERTY_CURRENT = "current"

        private const val INSTANCE_UNITS = "units"
        private const val COMPLICATIONS_KEY = "complication_slots"

        const val SLOT_COUNT = 5

        /** Until the user sets their own. */
        val DEFAULT_COMPLICATIONS = listOf(
            ComplicationSlot("phone", "phone_state", 0, "battery_level"),
            ComplicationSlot("weather", "location", 0, "temperature"),
            ComplicationSlot("calendar", "event", 0, "starts_at"),
            ComplicationSlot("finance", "stock", 0, "price"),
        )

        val BUILT_IN_PREFERENCES_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000009")
    }
}

internal fun units(imperial: Boolean, wind: WindUnits, clock24h: Boolean, locale: String): Map<String, String> = mapOf(
    PreferencesPlugin.PROPERTY_LOCALE to locale,
    PreferencesPlugin.PROPERTY_TEMPERATURE to if (imperial) "°F" else "°C",
    PreferencesPlugin.PROPERTY_DISTANCE to if (imperial) "mi" else "km",
    PreferencesPlugin.PROPERTY_WIND_SPEED to when (wind) {
        WindUnits.FromDistance -> if (imperial) "mph" else "km/h"
        WindUnits.KmH -> "km/h"
        WindUnits.Mph -> "mph"
        WindUnits.MetersPerSecond -> "m/s"
        WindUnits.Knots -> "knots"
    },
    PreferencesPlugin.PROPERTY_PRESSURE to if (imperial) "inHg" else "hPa",
    PreferencesPlugin.PROPERTY_PRECIPITATION to if (imperial) "in" else "mm",
    PreferencesPlugin.PROPERTY_CLOCK to if (clock24h) "24h" else "12h",
)
