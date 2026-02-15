package io.rebble.libpebblecommon.connection.endpointmanager.blobdb

import coredev.BlobDatabase
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.database.entity.asNotificationAppItem
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket.Companion.deserialize
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import io.rebble.libpebblecommon.services.blobdb.WriteType
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.StructMapper
import kotlin.time.Instant
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NotificationAppsDbTest {
    private val PP_BYTES = byteArrayOf(
        0,
        52,
        -78,
        -37,
        8,
        5,
        0,
        6,
        -19,
        -55,
        13,
        104,
        16,
        99,
        111,
        109,
        46,
        103,
        111,
        111,
        103,
        108,
        101,
        46,
        71,
        109,
        97,
        105,
        108,
        25,
        0,
        0,
        0,
        0,
        0,
        3,
        0,
        30,
        5,
        0,
        71,
        109,
        97,
        105,
        108,
        40,
        1,
        0,
        0,
        14,
        4,
        0,
        -19,
        -55,
        13,
        104
    )
    private val BLOB_DB_BYTES = byteArrayOf(
        0,
        0,
        0,
        0,
        3,
        0,
        30,
        5,
        0,
        71,
        109,
        97,
        105,
        108,
        40,
        1,
        0,
        0,
        14,
        4,
        0,
        -19,
        -55,
        13,
        104
    ).asUByteArray()
    private val APP_NAME = "Gmail"
    private val TIMESTAMP = Instant.fromEpochSeconds(1745734125)
    private val PACKAGE_NAME = "com.google.Gmail"
    private val KEY = SFixedString(StructMapper(), PACKAGE_NAME.length, PACKAGE_NAME).toBytes()
    private val MUTE_STATE = MuteState.Never

    @Test
    fun iosKnownValueDeserialization() {
        val packet = deserialize(PP_BYTES.toUByteArray()) as BlobDB2Command.Write
        val value = packet.value.get()
        assertContentEquals(BLOB_DB_BYTES, value)
        val write = DbWrite(
            token = 1.toUShort(),
            database = BlobDatabase.CannedResponses,
            timestamp = TIMESTAMP.epochSeconds.toUInt(),
            key = KEY,
            value = value,
            writeType = WriteType.Write,
        )
        val item = write.asNotificationAppItem()!!
        assertEquals(APP_NAME, item.name)
        assertEquals(PACKAGE_NAME, item.packageName)
        assertEquals(MUTE_STATE, item.muteState)
        assertEquals(TIMESTAMP, item.stateUpdated.instant)
    }

    @Test
    fun iosNotificationAppSerialization() {
        val item = NotificationAppItem(
            packageName = PACKAGE_NAME,
            name = APP_NAME,
            muteState = MUTE_STATE,
            channelGroups = emptyList(),
            stateUpdated = TIMESTAMP.asMillisecond(),
            lastNotified = TIMESTAMP.asMillisecond(),
            vibePatternName = null,
            colorName = null,
            iconCode = null,
        )
        val params = ValueParams(WatchType.APLITE, emptySet())
        val encoded = item.value(params)!!
        val write = DbWrite(
            token = 1.toUShort(),
            database = BlobDatabase.CannedResponses,
            timestamp = TIMESTAMP.epochSeconds.toUInt(),
            key = KEY,
            value = encoded,
            writeType = WriteType.Write,
        )
        val decoded = write.asNotificationAppItem()
        assertEquals(item, decoded)
    }
}