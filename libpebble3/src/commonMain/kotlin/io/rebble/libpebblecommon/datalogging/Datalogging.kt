package io.rebble.libpebblecommon.datalogging

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class Datalogging(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val webServices: WebServices,
) {
    private val logger = Logger.withTag("Datalogging")

    fun logData(uuid: Uuid, tag: UInt, data: ByteArray, watchInfo: WatchInfo, itemSize: UShort) {
        if (uuid == SYSTEM_APP_UUID) {
            when (tag) {
                MEMFAULT_CHUNKS_TAG -> {
                    libPebbleCoroutineScope.launch {
                        val chunk = MemfaultChunk()
                        chunk.fromBytes(DataBuffer(data.toUByteArray()))
                        val chunkBytes = chunk.bytes.get()
                        webServices.uploadMemfaultChunk(chunkBytes.toByteArray(), watchInfo)
                    }
                }
            }
        }
    }

    companion object {
        private val MEMFAULT_CHUNKS_TAG: UInt = 86u
    }
}

class MemfaultChunk : StructMappable() {
    val chunkSize: SUInt = SUInt(m, 0u, Endian.Little)
    val bytes: SBytes = SBytes(m).apply { linkWithSize(chunkSize) }
}
