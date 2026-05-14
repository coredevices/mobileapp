package io.rebble.libpebblecommon.database.entity

import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue.Companion.asBytes
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.HeartRatePreferencesValue.Companion.asBytes
import io.rebble.libpebblecommon.database.entity.HeartRatePreferencesValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.HrmPreferencesValue.Companion.asBytes
import io.rebble.libpebblecommon.database.entity.HrmPreferencesValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue.Companion.asBytes
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue.Companion.encodeToString
import io.rebble.libpebblecommon.health.HealthSettings
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.structmapper.SBoolean
import io.rebble.libpebblecommon.structmapper.SByte
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SOptional
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Instant.Companion.DISTANT_PAST
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
            KEY_HRM_PREFERENCES -> HrmPreferencesValue.fromString(value)?.asBytes(params.firmwareVersion)
            KEY_UNITS_DISTANCE -> UnitsDistanceValue.fromString(value)?.asBytes()
            KEY_HEART_RATE_PREFERENCES -> HeartRatePreferencesValue.fromString(value)?.asBytes()
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

// ActivityHRMSettings struct grew in firmware:
//   v4.9.146: added uint8_t measurement_interval (1 → 2 bytes)
//   v4.9.150: added bool activity_tracking_enabled  (2 → 3 bytes)
private val FW_HRM_MEASUREMENT_INTERVAL = fwSentinel(4, 9, 146)
private val FW_HRM_ACTIVITY_TRACKING = fwSentinel(4, 9, 150)

private fun fwSentinel(major: Int, minor: Int, patch: Int) = FirmwareVersion(
    stringVersion = "v$major.$minor.$patch",
    timestamp = DISTANT_PAST,
    major = major,
    minor = minor,
    patch = patch,
    suffix = null,
    gitHash = "",
    isRecovery = false,
    isDualSlot = false,
    isSlot0 = false,
)

fun HealthSettingsEntryDao.getWatchSettings(): Flow<HealthSettings> {
    val activityPrefsFlow= getEntryFlow(KEY_ACTIVITY_PREFERENCES).map {
        ActivityPrefsValue.fromString(it?.value) ?: ActivityPrefsValue()
    }
    val unitPrefsFlow = getEntryFlow(KEY_UNITS_DISTANCE).map {
        UnitsDistanceValue.fromString(it?.value) ?: UnitsDistanceValue()
    }
    val hrmPrefsFlow = getEntryFlow(KEY_HRM_PREFERENCES).map {
        HrmPreferencesValue.fromString(it?.value) ?: HrmPreferencesValue()
    }
    val heartRatePrefsFlow = getEntryFlow(KEY_HEART_RATE_PREFERENCES).map {
        HeartRatePreferencesValue.fromString(it?.value) ?: HeartRatePreferencesValue()
    }
    return combine(
        activityPrefsFlow,
        unitPrefsFlow,
        hrmPrefsFlow,
        heartRatePrefsFlow,
    ) { activityPrefs, unitPrefs, hrmPrefs, heartRatePrefs ->
        HealthSettings(
            heightMm = activityPrefs.heightMm,
            weightDag = activityPrefs.weightDag,
            ageYears = activityPrefs.ageYears,
            gender = activityPrefs.gender,
            trackingEnabled = activityPrefs.trackingEnabled,
            activityInsightsEnabled = activityPrefs.activityInsightsEnabled,
            sleepInsightsEnabled = activityPrefs.sleepInsightsEnabled,
            imperialUnits = unitPrefs.imperialUnits,
            hrmEnabled = hrmPrefs.enabled,
            hrmMeasurementInterval = hrmPrefs.measurementInterval,
            hrmActivityTrackingEnabled = hrmPrefs.activityTrackingEnabled,
            restingHr = heartRatePrefs.restingHr,
            elevatedHr = heartRatePrefs.elevatedHr,
            maxHr = heartRatePrefs.maxHr,
            hrZone1Threshold = heartRatePrefs.zone1Threshold,
            hrZone2Threshold = heartRatePrefs.zone2Threshold,
            hrZone3Threshold = heartRatePrefs.zone3Threshold,
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
    insertOrReplace(
        HealthSettingsEntry(
            id = KEY_HRM_PREFERENCES,
            value = HrmPreferencesValue(
                enabled = healthSettings.hrmEnabled,
                measurementInterval = healthSettings.hrmMeasurementInterval,
                activityTrackingEnabled = healthSettings.hrmActivityTrackingEnabled,
            ).encodeToString(),
        )
    )
    insertOrReplace(
        HealthSettingsEntry(
            id = KEY_HEART_RATE_PREFERENCES,
            value = HeartRatePreferencesValue(
                restingHr = healthSettings.restingHr,
                elevatedHr = healthSettings.elevatedHr,
                maxHr = healthSettings.maxHr,
                zone1Threshold = healthSettings.hrZone1Threshold,
                zone2Threshold = healthSettings.hrZone2Threshold,
                zone3Threshold = healthSettings.hrZone3Threshold,
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

@Serializable
data class HrmPreferencesValue(
    val enabled: Boolean = true,
    val measurementInterval: HRMonitoringInterval = HRMonitoringInterval.TenMin,
    val activityTrackingEnabled: Boolean = false,
) {
    companion object {
        fun HrmPreferencesValue.encodeToString(): String = json.encodeToString(this)
        fun fromString(value: String?): HrmPreferencesValue? = value?.let { json.decodeFromString(value) }

        // ActivityHRMSettings grew over time and the watch rejects writes whose length doesn't
        // match the compiled-in struct size. Cutoffs:
        //   < v4.9.146 → 1 byte (just `enabled`) — legacy Pebble hardware that can't be updated
        //   v4.9.146 to v4.9.149 → 2 bytes (added measurement_interval)
        //   ≥ v4.9.150 → 3 bytes (added activity_tracking_enabled)
        fun HrmPreferencesValue.asBytes(firmwareVersion: FirmwareVersion): UByteArray =
            HrmPreferencesBlobItem(
                enabled = enabled,
                measurementInterval = measurementInterval.value,
                hasMeasurementInterval = firmwareVersion >= FW_HRM_MEASUREMENT_INTERVAL,
                activityTrackingEnabled = activityTrackingEnabled,
                hasActivityTrackingEnabled = firmwareVersion >= FW_HRM_ACTIVITY_TRACKING,
            ).toBytes()
    }
}

@Serializable
data class HeartRatePreferencesValue(
    val restingHr: Short = 70,
    val elevatedHr: Short = 100,
    val maxHr: Short = 190, // 220 − default age (30)
    val zone1Threshold: Short = 130, // 50% of HRR
    val zone2Threshold: Short = 154, // 70% of HRR
    val zone3Threshold: Short = 172, // 85% of HRR
) {
    companion object {
        fun HeartRatePreferencesValue.encodeToString(): String = json.encodeToString(this)
        fun fromString(value: String?): HeartRatePreferencesValue? = value?.let { json.decodeFromString(value) }
        fun HeartRatePreferencesValue.asBytes(): UByteArray = HeartRatePreferencesBlobItem(
            restingHr = restingHr.toUByte(),
            elevatedHr = elevatedHr.toUByte(),
            maxHr = maxHr.toUByte(),
            zone1Threshold = zone1Threshold.toUByte(),
            zone2Threshold = zone2Threshold.toUByte(),
            zone3Threshold = zone3Threshold.toUByte(),
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
    val heightMm = SUShort(m, heightMm, endianness = Endian.Little)
    val weightDag = SUShort(m, weightDag, endianness = Endian.Little)
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

class HrmPreferencesBlobItem(
    enabled: Boolean,
    measurementInterval: Byte,
    hasMeasurementInterval: Boolean,
    activityTrackingEnabled: Boolean,
    hasActivityTrackingEnabled: Boolean,
) : StructMappable(endianness = Endian.Little) {
    val enabled = SByte(m, if (enabled) 0x01 else 0x00)
    val measurementInterval = SOptional(m, SByte(StructMapper(), measurementInterval), hasMeasurementInterval)
    val activityTrackingEnabled = SOptional(m, SBoolean(StructMapper(), activityTrackingEnabled), hasActivityTrackingEnabled)
}

class HeartRatePreferencesBlobItem(
    restingHr: UByte,
    elevatedHr: UByte,
    maxHr: UByte,
    zone1Threshold: UByte,
    zone2Threshold: UByte,
    zone3Threshold: UByte,
) : StructMappable(endianness = Endian.Little) {
    val restingHr = SUByte(m, restingHr)
    val elevatedHr = SUByte(m, elevatedHr)
    val maxHr = SUByte(m, maxHr)
    val zone1Threshold = SUByte(m, zone1Threshold)
    val zone2Threshold = SUByte(m, zone2Threshold)
    val zone3Threshold = SUByte(m, zone3Threshold)
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

enum class HRMonitoringInterval(val value: Byte) {
    TenMin(0),
    ThirtyMin(1),
    OneHour(2),
    Disabled(3),
    ;

    companion object {
        fun fromInt(value: Byte) = entries.first { it.value == value }
    }
}
