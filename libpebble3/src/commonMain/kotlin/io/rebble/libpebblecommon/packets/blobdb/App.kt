package io.rebble.libpebblecommon.packets.blobdb

import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.Endian
import kotlin.uuid.Uuid

/**
 * Data of the APP BlobDB Entry
 */
class AppMetadata(
    uuid: Uuid = Uuid.NIL,
    flags: UInt = 0u,
    icon: UInt = 0u,
    appVersionMajor: UByte = 0u,
    appVersionMinor: UByte = 0u,
    sdkVersionMajor: UByte = 0u,
    sdkVersionMinor: UByte = 0u,
    appFaceBgColor: UByte = 0u,
    appFaceTemplateId: UByte = 0u,
    appName: String = "Untitled",
    watchPermissions: UInt? = null,
) : StructMappable() {
    /**
     * UUID of the app
     */
    val uuid: SUUID = SUUID(m, uuid)

    /**
     * App install flags.
     */
    val flags: SUInt = SUInt(m, flags, endianness = Endian.Little)

    /**
     * Resource ID of the primary icon.
     */
    val icon: SUInt = SUInt(m, icon, endianness = Endian.Little)

    /**
     * Major app version.
     */
    val appVersionMajor: SUByte = SUByte(m, appVersionMajor)

    /**
     * Minor app version.
     */
    val appVersionMinor: SUByte = SUByte(m, appVersionMinor)

    /**
     * Major sdk version.
     */
    val sdkVersionMajor: SUByte = SUByte(m, sdkVersionMajor)

    /**
     * Minor sdk version.
     */
    val sdkVersionMinor: SUByte = SUByte(m, sdkVersionMinor)

    /**
     * ??? (Always sent as 0 in the Pebble app)
     */
    val appFaceBgColor: SUByte = SUByte(m, appFaceBgColor)

    /**
     * ??? (Always sent as 0 in the Pebble app)
     */
    val appFaceTemplateId: SUByte = SUByte(m, appFaceTemplateId)

    /**
     * Name of the app
     */
    val appName: SFixedString = SFixedString(m, 96, appName)

    /** Optional AppDB extension of [WatchPermission] bits, sent only to firmware advertising app permissions. */
    val watchPermissions: SUInt? = watchPermissions?.let { SUInt(m, it, endianness = Endian.Little) }
}

object WatchPermission {
    const val MICROPHONE = 1 shl 0
    /** Without it the watch allows health whatever [HEALTH_GRANTED] says, as it does for a phone that never sends it. */
    const val HEALTH_ENFORCED = 1 shl 1
    const val HEALTH_GRANTED = 1 shl 2
}

