package io.rebble.libpebblecommon.database.entity

import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue.Companion.asBytes
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue.Companion.asBytes
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue.Companion.encodeToString
import io.rebble.libpebblecommon.health.HealthSettings
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.structmapper.SByte
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@GenerateRoomEntity(
    primaryKey = "id",
    databaseId = BlobDatabase.HealthParams,
    windowBeforeSecs = -1,
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class HealthSettingsEntry(
    val id: String,
    val value: String,
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
        return when (id) {
            KEY_ACTIVITY_PREFERENCES -> ActivityPrefsValue.fromString(value)?.asBytes()
            KEY_HRM_PREFERENCES -> null // TODO
            KEY_UNITS_DISTANCE -> UnitsDistanceValue.fromString(value)?.asBytes()
            KEY_HEART_RATE_PREFERENCES -> null // TODO
            else -> null
        }
    }

    override fun recordHashCode(): Int = hashCode()
}

private const val KEY_ACTIVITY_PREFERENCES = "activityPreferences"
private const val KEY_HRM_PREFERENCES = "hrmPreferences"
private const val KEY_UNITS_DISTANCE = "unitsDistance"
private const val KEY_HEART_RATE_PREFERENCES = "heartRatePreferences"
private val json = Json { ignoreUnknownKeys = true }

fun HealthSettingsEntryDao.getWatchSettings(): Flow<HealthSettings> {
    val activityPrefsFlow= getEntryFlow(KEY_ACTIVITY_PREFERENCES).map {
        ActivityPrefsValue.fromString(it?.value) ?: ActivityPrefsValue()
    }
    val unitPrefsFlow = getEntryFlow(KEY_UNITS_DISTANCE).map {
        UnitsDistanceValue.fromString(it?.value) ?: UnitsDistanceValue()
    }
    return activityPrefsFlow.combine(unitPrefsFlow) { activityPrefs, unitPrefs ->
        HealthSettings(
            heightMm = activityPrefs.heightMm,
            weightDag = activityPrefs.weightDag,
            ageYears = activityPrefs.ageYears,
            gender = activityPrefs.gender,
            trackingEnabled = activityPrefs.trackingEnabled,
            activityInsightsEnabled = activityPrefs.activityInsightsEnabled,
            sleepInsightsEnabled = activityPrefs.sleepInsightsEnabled,
            imperialUnits = unitPrefs.imperialUnits,
        )
    }
}

suspend fun HealthSettingsEntryDao.setWatchSettings(healthSettings: HealthSettings) {
    insertOrReplace(
        HealthSettingsEntry(
            id = KEY_ACTIVITY_PREFERENCES,
            value = ActivityPrefsValue(
                heightMm = healthSettings.heightMm,
                weightDag = healthSettings.weightDag,
                trackingEnabled = healthSettings.trackingEnabled,
                activityInsightsEnabled = healthSettings.activityInsightsEnabled,
                sleepInsightsEnabled = healthSettings.sleepInsightsEnabled,
                ageYears = healthSettings.ageYears,
                gender = healthSettings.gender,
            ).encodeToString(),
        )
    )
    insertOrReplace(
        HealthSettingsEntry(
            id = KEY_UNITS_DISTANCE,
            value = UnitsDistanceValue(
                imperialUnits = healthSettings.imperialUnits,
            ).encodeToString(),
        )
    )
}

@Serializable
data class ActivityPrefsValue(
    val heightMm: Short = 1700, // 170cm in mm (default height)
    val weightDag: Short = 7000, // 70kg in decagrams (default weight)
    val trackingEnabled: Boolean = false,
    val activityInsightsEnabled: Boolean = false,
    val sleepInsightsEnabled: Boolean = false,
    val ageYears: Int = 35,
    val gender: HealthGender = HealthGender.Female,
) {
    companion object {
        fun ActivityPrefsValue.encodeToString(): String = json.encodeToString(this)
        fun fromString(value: String?): ActivityPrefsValue? = value?.let { json.decodeFromString(value) }
        fun ActivityPrefsValue.asBytes(): UByteArray = ActivityPrefsBlobItem(
            heightMm = heightMm.toUShort(),
            weightDag = weightDag.toUShort(),
            trackingEnabled = trackingEnabled,
            activityInsightsEnabled = activityInsightsEnabled,
            sleepInsightsEnabled = sleepInsightsEnabled,
            ageYears = ageYears.toByte(),
            gender = gender.value,
        ).toBytes()
    }
}

@Serializable
data class UnitsDistanceValue(
    val imperialUnits: Boolean = false, // false = metric (km/kg), true = imperial (mi/lb)
) {
    companion object {
        fun UnitsDistanceValue.encodeToString(): String = json.encodeToString(this)
        fun fromString(value: String?): UnitsDistanceValue? = value?.let { json.decodeFromString(value) }
        fun UnitsDistanceValue.asBytes(): UByteArray = DistanceUnitsBlobItem(
            imperialUnits = imperialUnits,
        ).toBytes()
    }
}

class ActivityPrefsBlobItem(
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

class DistanceUnitsBlobItem(
    imperialUnits: Boolean,
) : StructMappable(endianness = Endian.Little) {
    val imperialUnits = SByte(m, if (imperialUnits) 0x01 else 0x00)
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
