package io.rebble.libpebblecommon.database.entity

import kotlin.test.Test
import kotlin.test.assertEquals

internal class WeatherAppBlobRecordTest {

    @Test
    fun trailingStringLengthMatchesSerializedBytes() {
        val cases = listOf(
            "" to "",
            "Prague" to "Overcast",
            "České Budějovice" to "Overcast",
            "München" to "Déšť",
            "東京" to "雷雨 🌧️",
            "Cafe\u0301" to "Café",
        )

        cases.forEach { (locationName, forecastShort) ->
            val record = weatherRecord(locationName, forecastShort)
            val serializedStringsSize =
                record.locationName.toBytes().size + record.forecastShort.toBytes().size

            assertEquals(
                serializedStringsSize,
                record.allStringsLength.get().toInt(),
                "locationName=$locationName, forecastShort=$forecastShort",
            )
        }
    }

    private fun weatherRecord(locationName: String, forecastShort: String) =
        WeatherAppBlobRecord(
            currentTemp = 20,
            currentWeatherType = 1u,
            todayHighTemp = 26,
            todayLowTemp = 13,
            tomorrowWeatherType = 1u,
            tomorrowHighTemp = 26,
            tomorrowLowTemp = 15,
            lastUpdateTimeUtc = 0u,
            isCurrentLocation = false,
            locationName = locationName,
            forecastShort = forecastShort,
        )
}
