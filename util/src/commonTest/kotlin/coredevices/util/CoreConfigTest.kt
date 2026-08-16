package coredevices.util

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreConfigTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun explicitWeatherUnitsSurvivesRoundTrip() {
        WeatherUnit.entries.forEach { unit ->
            val encoded = json.encodeToString(CoreConfig(weatherUnits = unit))
            val decoded = json.decodeFromString<CoreConfig>(encoded)
            assertEquals(unit, decoded.weatherUnits)
            assertEquals(unit, decoded.resolvedWeatherUnits)
        }
    }

    @Test
    fun unsetWeatherUnitsFallsBackToDeviceDefault() {
        val decoded = json.decodeFromString<CoreConfig>("{}")
        assertEquals(null, decoded.weatherUnits)
        assertEquals(deviceDefaultWeatherUnit(), decoded.resolvedWeatherUnits)
    }
}
