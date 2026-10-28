package io.rebble.libpebblecommon.packets

import assertIs
import io.rebble.libpebblecommon.connection.endpointmanager.toWatchLanguagePacks
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
    fun multipleLanguagePacksCapabilityUsesBit28() {
        val flags = ProtocolCapsFlag.makeFlags(listOf(ProtocolCapsFlag.SupportsMultipleLanguagePacks))
        assertEquals(0x10u.toUByte(), flags[3])
        assertEquals(setOf(ProtocolCapsFlag.SupportsMultipleLanguagePacks), ProtocolCapsFlag.fromFlags(flags))
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

    @Test
    fun serializeLanguagePackRemoval() {
        val bytes = SystemMessage.LanguagePackRemoveRequest(7u, 2u, 0x1234u, "fr_FR").serialize()
        assertEquals(
            listOf(0x00u, 0x0cu, 0x00u, 0x12u, 0x00u, 0x0fu, 7u, 2u, 0x34u, 0x12u,
                0x66u, 0x72u, 0x5fu, 0x46u, 0x52u, 0x00u).map { it.toUByte() },
            bytes.toList(),
        )
    }

    @Test
    fun deserializeInstalledLanguagePacks() {
        val records = UByteArray(160)
        for (slot in 0 until 4) records[slot * 40] = slot.toUByte()
        records[1] = 7u
        records[2] = 0x34u
        records[3] = 0x12u
        "fr_FR".encodeToByteArray().asUByteArray().copyInto(records, 4)
        "Français".encodeToByteArray().asUByteArray().copyInto(records, 10)
        records[41] = 3u
        records[81] = 1u
        val wire = ubyteArrayOf(0u, 0xa4u, 0u, 0x12u, 0u, 0x0eu, 9u, 0u) + records
        val packet = PebblePacket.deserialize(wire)
        assertIs<SystemMessage.LanguagePacksResponse>(packet)
        assertEquals(9u.toUByte(), packet.token.get())
        val packs = packet.toWatchLanguagePacks()
        assertEquals(3, packs.size)
        assertEquals("Français", packs[0].name)
        assertEquals("fr_FR", packs[0].locale)
        assertEquals(0x1234, packs[0].version)
        assertEquals(true, packs[0].hasTranslation)
        assertEquals(true, packs[0].isActive)
        assertEquals(true, packs[1].readable)
        assertEquals(false, packs[1].hasTranslation)
        assertEquals("", packs[1].name)
        assertEquals(false, packs[2].readable)
        assertEquals(2, packs[2].slot)
    }

    @Test
    fun noPackActiveWhenEnglishSelected() {
        val records = UByteArray(160)
        for (slot in 0 until 4) records[slot * 40] = slot.toUByte()
        records[1] = 7u
        records[41] = 7u
        val wire = ubyteArrayOf(0u, 0xa4u, 0u, 0x12u, 0u, 0x0eu, 1u, 0xffu) + records
        val packet = PebblePacket.deserialize(wire)
        assertIs<SystemMessage.LanguagePacksResponse>(packet)
        val packs = packet.toWatchLanguagePacks()
        assertEquals(2, packs.size)
        assertEquals(false, packs.any { it.isActive })
    }

    @Test
    fun serializeBuiltInEnglishSelection() {
        val bytes = SystemMessage.LanguageSelectRequest(8u, 0xffu, 0u, "").serialize()
        assertEquals(
            listOf(0x00u, 0x0cu, 0x00u, 0x12u, 0x00u, 0x11u, 8u, 0xffu,
                0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u).map { it.toUByte() },
            bytes.toList(),
        )
    }

    @Test
    fun deserializeLanguageSelectionAcknowledgment() {
        val bytes = ubyteArrayOf(0u, 5u, 0u, 0x12u, 0u, 0x13u, 9u, 2u, 1u)
        val packet = PebblePacket.deserialize(bytes)
        assertIs<SystemMessage.LanguageSelectResponse>(packet)
        assertEquals(9u.toUByte(), packet.token.get())
        assertEquals(2u.toUByte(), packet.slot.get())
        assertEquals(1u.toUByte(), packet.success.get())
    }
}