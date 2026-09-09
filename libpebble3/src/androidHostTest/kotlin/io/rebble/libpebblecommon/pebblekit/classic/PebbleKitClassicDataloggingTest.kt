package io.rebble.libpebblecommon.pebblekit.classic

import io.rebble.libpebblecommon.packets.DataItemType
import io.rebble.libpebblecommon.pebblekit.classic.ClassicDataloggingSessions.Broadcast
import io.rebble.libpebblecommon.pebblekit.classic.ClassicDataloggingSessions.Companion.decodeLittleEndian
import io.rebble.libpebblecommon.pebblekit.classic.ClassicDataloggingSessions.Companion.encodeItem
import io.rebble.libpebblecommon.pebblekit.classic.ClassicDataloggingSessions.ItemPayload
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PebbleKitClassicDataloggingTest {

    private val emitted = mutableListOf<Broadcast>()
    private val sessions = ClassicDataloggingSessions { emitted += it }

    private val appUuid = Uuid.parse("6bf6215b-c97f-40be-a9d9-4f2fbaab6ffb")

    private fun open(
        watch: String = "XXX111",
        sessionId: UByte = 7u,
        timestamp: UInt = 1724800000u,
        tag: UInt = 42u,
        itemType: DataItemType = DataItemType.ByteArray,
        itemSize: UShort = 4u,
    ) = sessions.onSessionOpened(watch, sessionId, appUuid, timestamp, tag, itemType, itemSize)

    @Test
    fun `little endian decode handles 1 2 and 4 byte items`() {
        assertEquals(0x7FL, decodeLittleEndian(byteArrayOf(0x7F)))
        assertEquals(0xFFL, decodeLittleEndian(byteArrayOf(-1)))
        assertEquals(0x0201L, decodeLittleEndian(byteArrayOf(0x01, 0x02)))
        assertEquals(0xFFFFFFFFL, decodeLittleEndian(byteArrayOf(-1, -1, -1, -1)))
        assertEquals(0x04030201L, decodeLittleEndian(byteArrayOf(0x01, 0x02, 0x03, 0x04)))
    }

    @Test
    fun `uint items become long payloads`() {
        assertEquals(
            ItemPayload.UIntValue(0x04030201L),
            encodeItem(DataItemType.UInt, byteArrayOf(0x01, 0x02, 0x03, 0x04)),
        )
    }

    @Test
    fun `int items preserve sign`() {
        assertEquals(ItemPayload.IntValue(-1), encodeItem(DataItemType.Int, byteArrayOf(-1, -1, -1, -1)))
    }

    @Test
    fun `byte array items pass through untouched`() {
        val payload = encodeItem(DataItemType.ByteArray, byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), (payload as ItemPayload.Bytes).value)
    }

    @Test
    fun `payload with multiple items emits one broadcast per item`() {
        open(itemSize = 2u)
        sessions.onDataItems("XXX111", 7u, byteArrayOf(1, 2, 3, 4, 5, 6))
        assertEquals(3, emitted.size)
        val items = emitted.map { (it as Broadcast.Item).payload as ItemPayload.Bytes }
        assertContentEquals(byteArrayOf(1, 2), items[0].value)
        assertContentEquals(byteArrayOf(3, 4), items[1].value)
        assertContentEquals(byteArrayOf(5, 6), items[2].value)
    }

    @Test
    fun `partial trailing item is dropped and full items still deliver`() {
        open(itemSize = 4u)
        sessions.onDataItems("XXX111", 7u, byteArrayOf(1, 2, 3, 4, 5, 6))
        assertEquals(1, emitted.size)
        val payload = (emitted[0] as Broadcast.Item).payload as ItemPayload.Bytes
        assertContentEquals(byteArrayOf(1, 2, 3, 4), payload.value)
    }

    @Test
    fun `session metadata is stable and the watch timestamp is retained`() {
        open(timestamp = 1724800000u, tag = 42u)
        sessions.onDataItems("XXX111", 7u, ByteArray(4))
        sessions.onDataItems("XXX111", 7u, ByteArray(4))
        val first = (emitted[0] as Broadcast.Item).session
        val second = (emitted[1] as Broadcast.Item).session
        assertEquals(first.logUuid, second.logUuid)
        assertEquals(1724800000L, first.timestamp)
        assertEquals(42L, first.tag)
        assertEquals(appUuid, first.appUuid)
    }

    @Test
    fun `reopening a session id starts a new log with a new uuid`() {
        open()
        sessions.onDataItems("XXX111", 7u, ByteArray(4))
        sessions.onSessionClosed("XXX111", 7u)
        open()
        sessions.onDataItems("XXX111", 7u, ByteArray(4))
        val first = (emitted[0] as Broadcast.Item).session
        val second = (emitted[2] as Broadcast.Item).session
        assertNotEquals(first.logUuid, second.logUuid)
    }

    @Test
    fun `close emits finish with the session metadata then forgets the session`() {
        open()
        sessions.onSessionClosed("XXX111", 7u)
        val finish = assertIs<Broadcast.Finish>(emitted.single())
        assertEquals(appUuid, finish.session.appUuid)
        sessions.onSessionClosed("XXX111", 7u)
        sessions.onDataItems("XXX111", 7u, ByteArray(4))
        assertEquals(1, emitted.size)
    }

    @Test
    fun `data for an unknown session is dropped`() {
        sessions.onDataItems("XXX111", 9u, ByteArray(4))
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `zero item size session is rejected`() {
        open(itemSize = 0u)
        sessions.onDataItems("XXX111", 7u, ByteArray(4))
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `same session id on two watches does not collide`() {
        open(watch = "AAA111", tag = 1u)
        open(watch = "BBB222", tag = 2u)
        sessions.onDataItems("AAA111", 7u, ByteArray(4))
        sessions.onDataItems("BBB222", 7u, ByteArray(4))
        val first = (emitted[0] as Broadcast.Item).session
        val second = (emitted[1] as Broadcast.Item).session
        assertEquals(1L, first.tag)
        assertEquals(2L, second.tag)
        assertNotEquals(first.logUuid, second.logUuid)
    }

    @Test
    fun `data ids are unique and increasing across sessions`() {
        open(watch = "AAA111")
        open(watch = "BBB222")
        sessions.onDataItems("AAA111", 7u, ByteArray(8))
        sessions.onDataItems("BBB222", 7u, ByteArray(4))
        val ids = emitted.map { (it as Broadcast.Item).id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.toSet().size, ids.size)
    }
}
