package io.rebble.libpebblecommon.database.entity

import coredev.BlobDatabase
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import io.rebble.libpebblecommon.services.blobdb.WriteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val TIMESTAMP = Instant.fromEpochSeconds(1757376000)

private val FW_TEST = FirmwareVersion(
    stringVersion = "v0.0.0",
    timestamp = Instant.DISTANT_PAST,
    major = 0,
    minor = 0,
    patch = 0,
    suffix = null,
    gitHash = "",
    isRecovery = false,
    isDualSlot = false,
    isSlot0 = false,
)

private val PARAMS = ValueParams(
    platform = WatchType.BASALT,
    capabilities = emptySet(),
    firmwareVersion = FW_TEST,
    libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(LibPebbleConfig())),
)

class QuietTimeScheduleTest {
    @Test
    fun encodesAndParsesBackToTheSameSchedule() {
        val schedule = QuietTimeSchedule(22, 30, 7, 5)
        assertEquals("22:30-07:05", schedule.encode())
        assertEquals(schedule, QuietTimeSchedule.parse(schedule.encode()))
    }

    @Test
    fun rejectsValuesThatArentATimeRange() {
        assertNull(QuietTimeSchedule.parse(""))
        assertNull(QuietTimeSchedule.parse("22:00"))
        assertNull(QuietTimeSchedule.parse("22:00-07:00-08:00"))
        assertNull(QuietTimeSchedule.parse("24:00-07:00"))
        assertNull(QuietTimeSchedule.parse("22:60-07:00"))
        assertNull(QuietTimeSchedule.parse("ten-eleven"))
    }

    @Test
    fun unparseableStoredValueFallsBackToTheDefault() {
        val pref = ScheduleWatchPref.QuietTimeWeekdaySchedule
        assertEquals(pref.defaultValue, pref.decodeValue("nonsense"))
        assertEquals(QuietTimeSchedule(22, 0, 7, 0), pref.decodeValue("22:00-07:00"))
    }

    @Test
    fun sendsTheScheduleAsTheWatchsFourByteStruct() {
        val item = WatchPrefItem(
            id = ScheduleWatchPref.QuietTimeWeekendSchedule.id,
            value = "22:30-07:05",
            timestamp = TIMESTAMP.asMillisecond(),
        )
        assertContentEquals(ubyteArrayOf(22u, 30u, 7u, 5u), item.value(PARAMS))
    }

    @Test
    fun readsTheScheduleBackFromAWatchWrite() {
        val item = WatchPrefItem(
            id = ScheduleWatchPref.QuietTimeWeekdaySchedule.id,
            value = "23:15-06:45",
            timestamp = TIMESTAMP.asMillisecond(),
        )
        val write = DbWrite(
            token = 1.toUShort(),
            database = BlobDatabase.WatchPrefs,
            timestamp = TIMESTAMP.epochSeconds.toUInt(),
            key = item.key(),
            value = item.value(PARAMS)!!,
            writeType = WriteType.Write,
        )
        assertEquals(item, write.asWatchPrefItem(PARAMS))
    }
}
