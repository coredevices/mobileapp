package io.rebble.libpebblecommon.connection.qemu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class QemuFramingTest {
    @Test
    fun `frame has the wire layout the firmware expects`() {
        assertContentEquals(
            byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0, 1, 0, 2, 'h'.code.toByte(), 'i'.code.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            QemuFraming.frame(QemuFraming.PROTOCOL_SPP, "hi".encodeToByteArray()),
        )
    }

    @Test
    fun `round trips a frame`() {
        val frames = QemuFrameParser().feed(QemuFraming.frame(1, "hello".encodeToByteArray()))
        assertEquals(listOf(QemuFrame(1, "hello".encodeToByteArray())), frames)
    }

    @Test
    fun `reassembles a frame split across every possible read boundary`() {
        val raw = QemuFraming.frame(1, "abcdef".encodeToByteArray())
        for (split in 1 until raw.size) {
            val parser = QemuFrameParser()
            val got = parser.feed(raw.copyOfRange(0, split)) + parser.feed(raw.copyOfRange(split, raw.size))
            assertEquals(listOf(QemuFrame(1, "abcdef".encodeToByteArray())), got, "split at $split")
        }
    }

    @Test
    fun `reads several frames from one chunk`() {
        val raw = QemuFraming.frame(1, "aa".encodeToByteArray()) +
                QemuFraming.frame(3, byteArrayOf(1)) +
                QemuFraming.frame(1, "bb".encodeToByteArray())
        assertEquals(
            listOf(
                QemuFrame(1, "aa".encodeToByteArray()),
                QemuFrame(3, byteArrayOf(1)),
                QemuFrame(1, "bb".encodeToByteArray()),
            ),
            QemuFrameParser().feed(raw),
        )
    }

    @Test
    fun `resyncs past garbage`() {
        val raw = byteArrayOf(0x00, 0x11, 0x22) + QemuFraming.frame(1, "ok".encodeToByteArray())
        assertEquals(listOf(QemuFrame(1, "ok".encodeToByteArray())), QemuFrameParser().feed(raw))
    }

    @Test
    fun `keeps a header signature that straddles two reads`() {
        val raw = QemuFraming.frame(1, "x".encodeToByteArray())
        val parser = QemuFrameParser()
        assertEquals(emptyList(), parser.feed(raw.copyOfRange(0, 1)))
        assertEquals(listOf(QemuFrame(1, "x".encodeToByteArray())), parser.feed(raw.copyOfRange(1, raw.size)))
    }

    @Test
    fun `handles an empty payload`() {
        assertEquals(listOf(QemuFrame(7, ByteArray(0))), QemuFrameParser().feed(QemuFraming.frame(7, ByteArray(0))))
    }

    @Test
    fun `a frame is not a message - payloads concatenate into one stream`() {
        // The firmware splits the Pebble Protocol stream across frames arbitrarily.
        val message = ByteArray(100) { it.toByte() }
        val raw = QemuFraming.frame(1, message.copyOfRange(0, 30)) +
                QemuFraming.frame(1, message.copyOfRange(30, 100))
        val payloads = QemuFrameParser().feed(raw).flatMap { it.payload.asList() }
        assertContentEquals(message.asList(), payloads)
    }
}
