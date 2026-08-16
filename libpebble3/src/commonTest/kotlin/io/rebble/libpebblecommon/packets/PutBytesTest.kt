package io.rebble.libpebblecommon.packets

import kotlin.test.Test
import kotlin.test.assertContentEquals

internal class PutBytesTest {
    @Test
    fun serializePutBytesInit() {
        val bytes = PutBytesInit(1000u, ObjectType.FIRMWARE, 0u, "").serialize()
        assertContentEquals(
            ubyteArrayOf(
                0x00u, 0x07u, // length
                0xBEu, 0xEFu, // endpoint
                0x01u, // INIT
                0x00u, 0x00u, 0x03u, 0xE8u, // object size
                0x01u, // FIRMWARE
                0x00u, // bank
            ),
            bytes,
        )
    }

    @Test
    fun serializePutBytesInitWithResumeOffset() {
        val bytes =
            PutBytesInit(1000u, ObjectType.FIRMWARE, 0u, "", resumeOffset = 500u).serialize()
        assertContentEquals(
            ubyteArrayOf(
                0x00u, 0x0Fu, // length
                0xBEu, 0xEFu, // endpoint
                0x01u, // INIT
                0x00u, 0x00u, 0x03u, 0xE8u, // object size (remaining bytes)
                0x01u, // FIRMWARE
                0x00u, // bank
                0xBEu, 0x43u, 0x54u, 0xEFu, // resume magic
                0x00u, 0x00u, 0x01u, 0xF4u, // resume offset
            ),
            bytes,
        )
    }
}
