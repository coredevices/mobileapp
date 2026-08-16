package io.rebble.libpebblecommon.packets

import assertIs
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlin.test.Test
import kotlin.test.assertEquals


internal class SystemMessageTest {
    @Test
    fun serializeDeserializeAppVersionRequest() {
        val originalMessage = PhoneAppVersion.AppVersionRequest()

        val bytes = originalMessage.serialize()
        val newMessage = PebblePacket.deserialize(bytes)

        assertIs<PhoneAppVersion.AppVersionRequest>(newMessage)
    }

    @Test
    fun serializeDeserializeAppVersionResponse() {
        val expectedPlatformFlags = listOf(
            PhoneAppVersion.PlatformFlag.Accelerometer,
            PhoneAppVersion.PlatformFlag.GPS,
            PhoneAppVersion.PlatformFlag.SMS,
            PhoneAppVersion.PlatformFlag.Telephony
        )

        val expectedProtocolCaps = listOf(
            ProtocolCapsFlag.SupportsAppRunStateProtocol,
            ProtocolCapsFlag.SupportsHealthInsights,
            ProtocolCapsFlag.SupportsLocalization,
            ProtocolCapsFlag.SupportsUnreadCoreDump,
            ProtocolCapsFlag.SupportsWorkoutApp
        )

        val originalMessage = PhoneAppVersion.AppVersionResponse(
            70u,
            0u,
            PhoneAppVersion.PlatformFlag.makeFlags(
                PhoneAppVersion.OSType.Linux,
                expectedPlatformFlags
            ),
            7u,
            12u,
            15u,
            1u,
            ProtocolCapsFlag.makeFlags(expectedProtocolCaps)
        )

        val bytes = originalMessage.serialize()
        val newMessage = PebblePacket.deserialize(bytes)

        assertIs<PhoneAppVersion.AppVersionResponse>(newMessage)

        assertEquals(70u, newMessage.protocolVersion.get())
        // Convert flags to set since we don't care about the order
        assertEquals(
            expectedPlatformFlags.toSet(),
            PhoneAppVersion.PlatformFlag.fromFlags(newMessage.platformFlags.get()).second.toSet()
        )
        assertEquals(
            PhoneAppVersion.OSType.Linux,
            PhoneAppVersion.PlatformFlag.fromFlags(newMessage.platformFlags.get()).first
        )
        assertEquals(7u, newMessage.responseVersion.get())
        assertEquals(12u, newMessage.majorVersion.get())
        assertEquals(15u, newMessage.minorVersion.get())
        assertEquals(
            expectedProtocolCaps.toSet(),
            ProtocolCapsFlag.fromFlags(newMessage.protocolCaps.get()).toSet()
        )
    }

    @Test
    fun serializeFirmwareUpdateStatusRequest() {
        val bytes = SystemMessage.FirmwareUpdateStatusRequest().serialize()
        assertEquals(
            listOf(0x00u, 0x02u, 0x00u, 0x12u, 0x00u, 0x0Bu).map { it.toUByte() },
            bytes.toList()
        )
    }

    @Test
    fun deserializeFirmwareUpdateStatusResponse() {
        val bytes = ubyteArrayOf(
            0x00u, 0x14u, // length
            0x00u, 0x12u, // endpoint: system message
            0x00u, // command
            0x0Cu, // FirmwareUpdateStatusResponse
            0x00u, 0x00u, // reserved
            0x10u, 0x20u, 0x00u, 0x00u, // resources bytes written (LE)
            0x78u, 0x56u, 0x34u, 0x12u, // resources crc (LE)
            0x40u, 0x30u, 0x00u, 0x00u, // firmware bytes written (LE)
            0xEFu, 0xBEu, 0xADu, 0xDEu, // firmware crc (LE)
        )

        val message = PebblePacket.deserialize(bytes)

        assertIs<SystemMessage.FirmwareUpdateStatusResponse>(message)
        assertEquals(0x2010u, message.resourcesBytesWritten.get())
        assertEquals(0x12345678u, message.resourcesCrc.get())
        assertEquals(0x3040u, message.firmwareBytesWritten.get())
        assertEquals(0xDEADBEEFu, message.firmwareCrc.get())
    }

    @Test
    fun serializeDeserializeSetTimeUtcMessage() {
        val originalMessage = TimeMessage.SetUTC(
            12345678u,
            2500,
            "Europe/Berlin"
        )

        val bytes = originalMessage.serialize()
        val newMessage = PebblePacket.deserialize(bytes)

        assertIs<TimeMessage.SetUTC>(newMessage)

        assertEquals(12345678u, newMessage.unixTime.get())
        assertEquals(2500, newMessage.utcOffset.get())
        assertEquals("Europe/Berlin", newMessage.timeZoneName.get())
    }
}