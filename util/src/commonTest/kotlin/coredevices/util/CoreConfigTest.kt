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
            assertEquals(unit, json.decodeFromString<CoreConfig>(encoded).weatherUnits)
        }
    }

    @Test
    fun unsetWeatherUnitsDecodesAsNull() {
        assertEquals(null, json.decodeFromString<CoreConfig>("{}").weatherUnits)
    }

    @Test
    fun navBarTabsMigrationPutsPreferredTabFirst() {
        assertEquals(
            listOf("WatchFaces", "Index", "Watches", "Health", "Notifications", "Settings"),
            CoreConfig().migrateNavBarTabs().navBarTabs,
        )
        assertEquals(
            listOf("WatchFaces", "Index", "Watches", "Notifications", "Health", "Settings"),
            CoreConfig(preferHealthTab = false).migrateNavBarTabs().navBarTabs,
        )
    }

    @Test
    fun navBarTabsMigrationKeepsExistingTabs() {
        assertEquals(listOf("Watches"), CoreConfig(navBarTabs = listOf("Watches")).migrateNavBarTabs().navBarTabs)
    }
}
