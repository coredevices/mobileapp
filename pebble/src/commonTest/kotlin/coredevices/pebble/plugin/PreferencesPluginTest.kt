package coredevices.pebble.plugin

import com.russhwolf.settings.MapSettings
import coredevices.database.WeatherLocationDao
import coredevices.database.WeatherLocationEntity
import coredevices.pebble.weather.fahrenheitFor
import coredevices.pebble.weather.windUnitsFor
import io.rebble.libpebblecommon.connection.FakeLibPebble
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.TemperatureUnits
import io.rebble.libpebblecommon.database.entity.WindUnits
import io.rebble.libpebblecommon.plugin.BooleanShape
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private class FakeWeatherLocationDao(private val locations: List<WeatherLocationEntity>) : WeatherLocationDao {
    override suspend fun upsert(location: WeatherLocationEntity) {}
    override suspend fun delete(location: WeatherLocationEntity) {}
    override suspend fun updateOrder(key: Uuid, newIndex: Int) {}
    override fun getAllLocationsFlow() = flowOf(locations)
    override suspend fun getAllLocations() = locations
}

private val HOME = Uuid.parse("00000000-0000-0000-0003-000000000001")
private val HERE = Uuid.parse("00000000-0000-0000-0003-000000000002")

class PreferencesPluginTest {

    private val settings = MapSettings()
    private val plugin = plugin()

    private fun plugin(locations: List<WeatherLocationEntity> = emptyList()) =
        PreferencesPlugin(FakeLibPebble(), FakeWeatherLocationDao(locations), settings)

    private suspend fun read(item: String, p: PreferencesPlugin = plugin): SourceEnvelope =
        p.observe(PreferencesPlugin.CATEGORY, item, null, null).first()

    private fun SourceEnvelope.text(index: Int, property: String): String? =
        instances[index].properties[property]?.get(SourceShapeNames.SHORT_TEXT)
            ?.let { Json.decodeFromJsonElement<ShortTextShape>(it).text }

    private fun SourceEnvelope.number(index: Int, property: String): Double? =
        instances[index].properties[property]?.get(SourceShapeNames.NUMERIC_VALUE)
            ?.let { Json.decodeFromJsonElement<NumericValueShape>(it).value }

    @Test
    fun metricUnits() {
        assertEquals(
            mapOf(
                "locale" to "de-DE", "temperature" to "°C", "distance" to "km", "wind_speed" to "km/h",
                "pressure" to "hPa", "precipitation" to "mm", "clock" to "24h",
            ),
            units(imperial = false, fahrenheit = false, wind = WindUnits.KmH, clock24h = true, locale = "de-DE"),
        )
    }

    @Test
    fun imperialUnitsWithAnExplicitWindUnit() {
        val units = units(imperial = true, fahrenheit = true, wind = WindUnits.Knots, clock24h = false, locale = "en-US")
        assertEquals("°F", units["temperature"])
        assertEquals("mi", units["distance"])
        assertEquals("knots", units["wind_speed"])
        assertEquals("inHg", units["pressure"])
        assertEquals("in", units["precipitation"])
        assertEquals("12h", units["clock"])
    }

    @Test
    fun automaticUnitsFollowDistance() {
        val automatic = emptyList<WatchPreference<*>>()
        assertEquals(WindUnits.Mph, automatic.windUnitsFor(imperialUnits = true))
        assertEquals(WindUnits.KmH, automatic.windUnitsFor(imperialUnits = false))
        assertEquals(true, automatic.fahrenheitFor(imperialUnits = true))
        assertEquals(false, automatic.fahrenheitFor(imperialUnits = false))
    }

    @Test
    fun explicitUnitsOverrideDistance() {
        val uk = listOf(
            WatchPreference(EnumWatchPref.Temperature, TemperatureUnits.Celsius),
            WatchPreference(EnumWatchPref.WindSpeed, WindUnits.Mph),
        )
        assertEquals(false, uk.fahrenheitFor(imperialUnits = true))
        assertEquals(WindUnits.Mph, uk.windUnitsFor(imperialUnits = false))
    }

    @Test
    fun theDefaultSlotsUntilTheUserSetsTheirOwn() = runTest {
        val slots = read(PreferencesPlugin.ITEM_COMPLICATIONS)
        assertEquals(PreferencesPlugin.DEFAULT_COMPLICATIONS.size, slots.instances.size)
        assertEquals("weather", slots.text(0, PreferencesPlugin.PROPERTY_CATEGORY))
        assertEquals("temperature", slots.text(0, PreferencesPlugin.PROPERTY_PROPERTY))
    }

    @Test
    fun emptySlotsAreSkippedAndSlotsSurviveARestart() = runTest {
        plugin.setComplication(0, null)
        plugin.setComplication(4, ComplicationSlot("finance", "stock", 1, "price"))

        val reloaded = plugin()
        assertEquals(PreferencesPlugin.SLOT_COUNT, reloaded.complications.value.size)
        val slots = read(PreferencesPlugin.ITEM_COMPLICATIONS, reloaded)
        assertEquals(listOf("1", "2", "3", "4"), slots.instances.map { it.instanceId })
        assertEquals("phone", slots.text(0, PreferencesPlugin.PROPERTY_CATEGORY))
        assertEquals("stock", slots.text(3, PreferencesPlugin.PROPERTY_ITEM))
        assertEquals(1.0, slots.number(3, PreferencesPlugin.PROPERTY_INDEX))
    }

    @Test
    fun weatherLocationsAreKeyedAndOrderedLikeTheUsersList() = runTest {
        val locations = listOf(
            WeatherLocationEntity(HERE, 0, "Current location", null, null, currentLocation = true),
            WeatherLocationEntity(HOME, 1, "Home", 51.5, -0.12, currentLocation = false),
        )

        val served = read(PreferencesPlugin.ITEM_WEATHER_LOCATION, plugin(locations))

        assertEquals(listOf(HERE.toString(), HOME.toString()), served.instances.map { it.instanceId })
        assertNull(served.number(0, PreferencesPlugin.PROPERTY_LATITUDE))
        assertEquals(51.5, served.number(1, PreferencesPlugin.PROPERTY_LATITUDE))
        assertEquals("Home", served.text(1, "name"))
        val current = served.instances[0].properties.getValue(PreferencesPlugin.PROPERTY_CURRENT)
            .getValue(SourceShapeNames.BOOLEAN)
        assertEquals(true, Json.decodeFromJsonElement<BooleanShape>(current).value)
    }
}
