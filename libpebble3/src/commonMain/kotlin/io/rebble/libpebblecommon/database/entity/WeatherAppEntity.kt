package io.rebble.libpebblecommon.database.entity

import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.structmapper.SBoolean
import io.rebble.libpebblecommon.structmapper.SLongString
import io.rebble.libpebblecommon.structmapper.SShort
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.Endian
import kotlin.uuid.Uuid

@GenerateRoomEntity(
    primaryKey = "key",
    databaseId = BlobDatabase.Weather,
    windowBeforeSecs = -1,
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class WeatherAppEntry(
    val key: Uuid,
    val currentTemp: Short,
    val currentWeatherType: Byte,
    val todayHighTemp: Short,
    val todayLowTemp: Short,
    val tomorrowWeatherType: Byte,
    val tomorrowHighTemp: Short,
    val tomorrowLowTemp: Short,
    val lastUpdateTimeUtcSecs: Long,
    val isCurrentLocation: Boolean,
    val locationName: String,
    val forecastShort: String,
) : BlobDbItem {
    override fun key(): UByteArray = SUUID(StructMapper(), key).toBytes()

    override fun value(params: ValueParams): UByteArray? {
        if (!params.capabilities.contains(ProtocolCapsFlag.SupportsWeatherApp)) {
            return null
        }
        return WeatherAppBlobRecord(
            currentTemp = currentTemp,
            currentWeatherType = currentWeatherType.toUByte(),
            todayHighTemp = todayHighTemp,
            todayLowTemp = todayLowTemp,
            tomorrowWeatherType = tomorrowWeatherType.toUByte(),
            tomorrowHighTemp = tomorrowHighTemp,
            tomorrowLowTemp = tomorrowLowTemp,
            lastUpdateTimeUtc = lastUpdateTimeUtcSecs.toUInt(),
            isCurrentLocation = isCurrentLocation,
            locationName = locationName,
            forecastShort = forecastShort,
        ).toBytes()
    }

    override fun recordHashCode(): Int = hashCode()
}

class WeatherAppBlobRecord(
    version: UByte = 3u,
    currentTemp: Short,
    currentWeatherType: UByte,
    todayHighTemp: Short,
    todayLowTemp: Short,
    tomorrowWeatherType: UByte,
    tomorrowHighTemp: Short,
    tomorrowLowTemp: Short,
    lastUpdateTimeUtc: UInt,
    isCurrentLocation: Boolean,
    locationName: String,
    forecastShort: String,
) : StructMappable(endianness = Endian.Little) {
    val version = SUByte(m, version)
    val currentTemp = SShort(m, currentTemp, endianness = Endian.Little)
    val currentWeatherType = SUByte(m, currentWeatherType)
    val todayHighTemp = SShort(m, todayHighTemp, endianness = Endian.Little)
    val todayLowTemp = SShort(m, todayLowTemp, endianness = Endian.Little)
    val tomorrowWeatherType = SUByte(m, tomorrowWeatherType)
    val tomorrowHighTemp = SShort(m, tomorrowHighTemp, endianness = Endian.Little)
    val tomorrowLowTemp = SShort(m, tomorrowLowTemp, endianness = Endian.Little)
    val lastUpdateTimeUtc = SUInt(m, lastUpdateTimeUtc, endianness = Endian.Little)
    val isCurrentLocation = SBoolean(m, isCurrentLocation)
    val allStringsLength = SUShort(m, (locationName.length + 2 + forecastShort.length + 2).toUShort(), endianness = Endian.Little)
    val locationName = SLongString(m, locationName, endianness = Endian.Little)
    val forecastShort = SLongString(m, forecastShort, endianness = Endian.Little)
}