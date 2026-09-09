package io.rebble.libpebblecommon.pebblekit.classic

import android.content.Context
import android.content.Intent
import android.util.Base64
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.datalogging.CompanionDatalogging
import io.rebble.libpebblecommon.packets.DataItemType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Delivers third-party datalogging via the classic PebbleKit broadcast
 * protocol (`com.getpebble.action.dl.*`), one broadcast per logged item.
 * Timestamp and tag ride as long extras, matching PebbleKit >= 2.6
 * receivers. Best-effort: no phone-side buffering or redelivery.
 *
 * Session bookkeeping and item encoding live in [ClassicDataloggingSessions];
 * this class only translates its [ClassicDataloggingSessions.Broadcast]
 * values into intents.
 */
class PebbleKitClassicDatalogging(private val context: Context) : CompanionDatalogging {

    private val sessions = ClassicDataloggingSessions { broadcast ->
        context.sendOrderedBroadcast(broadcast.toIntent(), null)
    }

    override fun onSessionOpened(
        watchIdentity: String,
        sessionId: UByte,
        appUuid: Uuid,
        timestamp: UInt,
        tag: UInt,
        itemType: DataItemType,
        itemSize: UShort,
    ) = sessions.onSessionOpened(watchIdentity, sessionId, appUuid, timestamp, tag, itemType, itemSize)

    override fun onDataItems(watchIdentity: String, sessionId: UByte, data: ByteArray) =
        sessions.onDataItems(watchIdentity, sessionId, data)

    override fun onSessionClosed(watchIdentity: String, sessionId: UByte) =
        sessions.onSessionClosed(watchIdentity, sessionId)

    internal companion object {
        const val INTENT_DL_RECEIVE_DATA = "com.getpebble.action.dl.RECEIVE_DATA"
        const val INTENT_DL_FINISH_SESSION = "com.getpebble.action.dl.FINISH_SESSION"
        const val APP_UUID = "uuid"
        const val DATA_LOG_UUID = "data_log_uuid"
        const val DATA_LOG_TIMESTAMP = "data_log_timestamp"
        const val DATA_LOG_TAG = "data_log_tag"
        const val PBL_DATA_ID = "pbl_data_id"
        const val PBL_DATA_TYPE = "pbl_data_type"
        const val PBL_DATA_OBJECT = "pbl_data_object"

        // PebbleKit's DataType enum ordinals (BYTES/UINT/INT).
        const val TYPE_BYTES: Byte = 0x00
        const val TYPE_UINT: Byte = 0x02
        const val TYPE_INT: Byte = 0x03

        internal fun ClassicDataloggingSessions.Broadcast.toIntent(): Intent = when (this) {
            is ClassicDataloggingSessions.Broadcast.Item ->
                Intent(INTENT_DL_RECEIVE_DATA).apply {
                    putSessionExtras(session)
                    putExtra(PBL_DATA_ID, id)
                    when (payload) {
                        is ClassicDataloggingSessions.ItemPayload.Bytes -> {
                            putExtra(PBL_DATA_TYPE, TYPE_BYTES)
                            putExtra(PBL_DATA_OBJECT, Base64.encodeToString(payload.value, Base64.NO_WRAP))
                        }
                        is ClassicDataloggingSessions.ItemPayload.UIntValue -> {
                            putExtra(PBL_DATA_TYPE, TYPE_UINT)
                            putExtra(PBL_DATA_OBJECT, payload.value)
                        }
                        is ClassicDataloggingSessions.ItemPayload.IntValue -> {
                            putExtra(PBL_DATA_TYPE, TYPE_INT)
                            putExtra(PBL_DATA_OBJECT, payload.value)
                        }
                    }
                }
            is ClassicDataloggingSessions.Broadcast.Finish ->
                Intent(INTENT_DL_FINISH_SESSION).apply { putSessionExtras(session) }
        }

        private fun Intent.putSessionExtras(session: ClassicDataloggingSessions.Session) {
            putExtra(APP_UUID, session.appUuid.toJavaUuid())
            putExtra(DATA_LOG_UUID, session.logUuid)
            putExtra(DATA_LOG_TIMESTAMP, session.timestamp)
            putExtra(DATA_LOG_TAG, session.tag)
        }
    }
}

/**
 * Platform-independent half of the classic-PebbleKit bridge: tracks open
 * sessions, splits payloads into items, and emits [Broadcast] values for the
 * transport to deliver. Kept free of Android types so it is unit-testable.
 */
internal class ClassicDataloggingSessions(
    private val emit: (Broadcast) -> Unit,
) : CompanionDatalogging {

    internal data class Session(
        val logUuid: UUID,
        val appUuid: Uuid,
        val timestamp: Long,
        val tag: Long,
        val itemType: DataItemType,
        val itemSize: Int,
    )

    internal sealed class Broadcast {
        data class Item(val session: Session, val payload: ItemPayload, val id: Int) : Broadcast()
        data class Finish(val session: Session) : Broadcast()
    }

    internal sealed class ItemPayload {
        data class Bytes(val value: ByteArray) : ItemPayload()
        data class UIntValue(val value: Long) : ItemPayload()
        data class IntValue(val value: Int) : ItemPayload()
    }

    private val sessions = ConcurrentHashMap<String, Session>()

    // Seeded from the clock so a process restart does not repeat ids that a
    // long-lived companion process already dedups against.
    private val dataId = AtomicInteger((System.currentTimeMillis() / 1000L).toInt() and 0x3FFFFFFF)

    private fun key(watchIdentity: String, sessionId: UByte) = "$watchIdentity:$sessionId"

    override fun onSessionOpened(
        watchIdentity: String,
        sessionId: UByte,
        appUuid: Uuid,
        timestamp: UInt,
        tag: UInt,
        itemType: DataItemType,
        itemSize: UShort,
    ) {
        if (itemSize.toInt() <= 0) {
            logger.w { "Ignoring datalogging session $sessionId for $appUuid: itemSize=$itemSize" }
            return
        }
        sessions[key(watchIdentity, sessionId)] = Session(
            logUuid = UUID.randomUUID(),
            appUuid = appUuid,
            timestamp = timestamp.toLong(),
            tag = tag.toLong(),
            itemType = itemType,
            itemSize = itemSize.toInt(),
        )
    }

    override fun onDataItems(watchIdentity: String, sessionId: UByte, data: ByteArray) {
        val session = sessions[key(watchIdentity, sessionId)] ?: run {
            logger.w { "Dropping ${data.size}B of datalogging: unknown session $sessionId on $watchIdentity" }
            return
        }
        if (data.size % session.itemSize != 0) {
            logger.w {
                "Datalogging payload for ${session.appUuid} tag ${session.tag} is ${data.size}B, " +
                    "not a multiple of itemSize ${session.itemSize}; trailing partial item dropped"
            }
        }
        var offset = 0
        while (offset + session.itemSize <= data.size) {
            val item = data.copyOfRange(offset, offset + session.itemSize)
            emit(Broadcast.Item(session, encodeItem(session.itemType, item), dataId.incrementAndGet()))
            offset += session.itemSize
        }
    }

    override fun onSessionClosed(watchIdentity: String, sessionId: UByte) {
        val session = sessions.remove(key(watchIdentity, sessionId)) ?: return
        emit(Broadcast.Finish(session))
    }

    internal companion object {
        private val logger = Logger.withTag("PebbleKitClassicDatalogging")

        /** Maps a logged item to its classic-PebbleKit representation.
         *  Unknown on-wire types deliver as bytes so nothing is lost. */
        internal fun encodeItem(type: DataItemType, item: ByteArray): ItemPayload = when (type) {
            DataItemType.UInt -> ItemPayload.UIntValue(decodeLittleEndian(item))
            DataItemType.Int -> ItemPayload.IntValue(decodeLittleEndian(item).toInt())
            else -> ItemPayload.Bytes(item)
        }

        /** Little-endian unsigned decode of a 1/2/4-byte item. */
        internal fun decodeLittleEndian(item: ByteArray): Long {
            var value = 0L
            for (i in item.indices.reversed()) {
                value = (value shl 8) or (item[i].toLong() and 0xFF)
            }
            return value
        }
    }
}
