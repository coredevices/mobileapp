package io.rebble.libpebblecommon.database.entity

import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.health.HealthSettings
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.structmapper.SByte
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@GenerateRoomEntity(
    primaryKey = "id",
    databaseId = BlobDatabase.HealthParams,
    windowBeforeSecs = -1,
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class WatchSettings(
    val id: String,
    val heightMm: Short,
    val weightDag: Short,
    val trackingEnabled: Boolean,
    val activityInsightsEnabled: Boolean,
    val sleepInsightsEnabled: Boolean,
    val ageYears: Byte,
    val gender: Byte,
) : BlobDbItem {
    override fun key(): UByteArray = SFixedString(
        mapper = StructMapper(),
        initialSize = id.length,
        default = id,
    ).toBytes()

    override fun value(params: ValueParams): UByteArray? {
        if (!params.capabilities.contains(ProtocolCapsFlag.SupportsHealthInsights)) {
            return null
        }
        return WatchSettingsBlobItem(
            heightMm = heightMm.toUShort(),
            weightDag = weightDag.toUShort(),
            trackingEnabled = trackingEnabled,
            activityInsightsEnabled = activityInsightsEnabled,
            sleepInsightsEnabled = sleepInsightsEnabled,
            ageYears = ageYears,
            gender = gender,
        ).toBytes()
    }

    override fun recordHashCode(): Int = hashCode()
}

private const val KEY_ACTIVITY_PREFERENCES = "activityPreferences"

fun WatchSettingsDao.getWatchSettings(): Flow<HealthSettings> =
    getEntryFlow(KEY_ACTIVITY_PREFERENCES)
        .map {
            it?.toHealthSettings() ?: HealthSettings()
        }

suspend fun WatchSettingsDao.setWatchSettings(healthSettings: HealthSettings) {
    insertOrReplace(healthSettings.toDbItem())
}

class WatchSettingsBlobItem(
    heightMm: UShort,
    weightDag: UShort,
    trackingEnabled: Boolean,
    activityInsightsEnabled: Boolean,
    sleepInsightsEnabled: Boolean,
    ageYears: Byte,
    gender: Byte,
) : StructMappable(endianness = Endian.Little) {
    val heightMm = SUShort(m, heightMm)
    val weightDag = SUShort(m, weightDag)
    val trackingEnabled = SByte(m, if (trackingEnabled) 0x01 else 0x00)
    val activityInsightsEnabled = SByte(m, if (activityInsightsEnabled) 0x01 else 0x00)
    val sleepInsightsEnabled = SByte(m, if (sleepInsightsEnabled) 0x01 else 0x00)
    val ageYears = SByte(m, ageYears)
    val gender = SByte(m, gender)
}

enum class HealthGender(
    val value: Byte,
) {
    Female(0),
    Male(1),
    Other(2),
    ;

    companion object {
        fun fromInt(value: Byte) = entries.first { it.value == value }
    }
}

fun WatchSettings.toHealthSettings() = HealthSettings(
    heightMm = heightMm,
    weightDag = weightDag,
    ageYears = ageYears.toInt(),
    gender = HealthGender.fromInt(gender),
    trackingEnabled = trackingEnabled,
    activityInsightsEnabled = activityInsightsEnabled,
    sleepInsightsEnabled = sleepInsightsEnabled,
)

fun HealthSettings.toDbItem() = WatchSettings(
    id = KEY_ACTIVITY_PREFERENCES,
    heightMm = heightMm,
    weightDag = weightDag,
    ageYears = ageYears.toByte(),
    gender = gender.value,
    trackingEnabled = trackingEnabled,
    activityInsightsEnabled = activityInsightsEnabled,
    sleepInsightsEnabled = sleepInsightsEnabled,
)