package io.rebble.libpebblecommon.database.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import co.touchlab.kermit.Logger
import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem.Attribute
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import io.rebble.libpebblecommon.structmapper.SFixedList
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.serialization.Serializable
import io.rebble.libpebblecommon.timeline.toPebbleColor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.time.Instant

@Immutable
@GenerateRoomEntity(
    primaryKey = "packageName",
    databaseId = BlobDatabase.CannedResponses,
    windowBeforeSecs = -1,
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class NotificationAppItem(
    val packageName: String,
    val name: String,
    val muteState: MuteState,
    val channelGroups: List<ChannelGroup>,
    /**
     * Last time [muteState] was changed. Used to resolve conflicts with watch on iOS.
     */
    val stateUpdated: MillisecondInstant,
    val lastNotified: MillisecondInstant,
    @ColumnInfo(defaultValue = "null")
    val vibePatternName: String?,
    @ColumnInfo(defaultValue = "null")
    val colorName: String?,
    @ColumnInfo(defaultValue = "null")
    val iconCode: String?,
) : BlobDbItem {
    override fun key(): UByteArray =
        SFixedString(StructMapper(), packageName.length, packageName).toBytes()

    override fun value(params: ValueParams): UByteArray? {
        val attributesList = attributes {
            appName { name }
            muteDayOfWeek { muteState.value }
            lastUpdated { stateUpdated.instant }
            
            vibePatternName?.let { name ->
                val pattern = params.vibePatternDao?.let { dao ->
                    runBlocking {
                        dao.getVibePattern(name)
                    }
                }
                pattern?.let {
                    vibrationPattern { it.pattern }
                }
            }
            
            colorName?.let { name ->
                io.rebble.libpebblecommon.timeline.TimelineColor.findByName(name)?.let { color ->
                    backgroundColor { color.toPebbleColor() }
                }
            }
            
            iconCode?.let { code ->
                io.rebble.libpebblecommon.packets.blobdb.TimelineIcon.fromCode(code)?.let { icon ->
                    icon { icon }
                }
            }
        }.map { it.asAttribute() }
        
        val entity = NotificationAppBlobItem(attributes = attributesList)
        return entity.toBytes()
    }

    override fun recordHashCode(): Int = hashCode()
}

fun NotificationAppItem.everNotified(): Boolean =
    lastNotified.instant.epochSeconds > Instant.DISTANT_PAST.epochSeconds

@Serializable
enum class MuteState(val value: UByte) {
    Always(127u),
    Weekends(65u),
    Weekdays(62u),
    Never(0u),
    Exempt(1u), // Not support on watch (only use for android things)
    ;

    companion object {
        fun fromValue(value: UByte): MuteState = entries.firstOrNull { it.value == value } ?: Never
    }
}

@Immutable
@Serializable
data class ChannelGroup(
    val id: String,
    val name: String?,
    val channels: List<ChannelItem>,
)

@Immutable
@Serializable
data class ChannelItem(
    val id: String,
    val name: String,
    val muteState: MuteState,
    val vibrationPattern: List<UInt>? = null,
)

class NotificationAppBlobItem(
    flags: UInt = 0u,
    attributes: List<Attribute> = emptyList(),
    actions: List<TimelineItem.Action> = emptyList()
) : StructMappable() {
    val flags = SUInt(m, flags, endianness = Endian.Little)
    val attrCount = SUByte(m, attributes.size.toUByte())
    val actionCount = SUByte(m, actions.size.toUByte())
    val attributes = SFixedList(m, attrCount.get().toInt(), attributes) {
        Attribute(0u, ubyteArrayOf())
    }.apply {
        linkWithCount(attrCount)
    }
    val actions = SFixedList(m, actionCount.get().toInt(), actions) {
        TimelineItem.Action(
            0u,
            TimelineItem.Action.Type.Empty,
            emptyList()
        )
    }.apply {
        linkWithCount(actionCount)
    }
}

private val logger = Logger.withTag("NotificationAppItem")

fun DbWrite.asNotificationAppItem(): NotificationAppItem? {
    try {
        val packageName = key.asByteArray().decodeToString()
        val item = NotificationAppBlobItem().apply { fromBytes(DataBuffer(value)) }
        val appName =
            item.attributes.get(TimelineAttribute.AppName)?.asByteArray()?.decodeToString()
        if (appName == null) {
            logger.e("appName is null")
            return null
        }
        val mutedState = item.attributes.get(TimelineAttribute.MuteDayOfWeek)?.let {
            MuteState.fromValue(it[0])
        }
        if (mutedState == null) {
            logger.e("mutedState is null")
            return null
        }
        val lastUpdated = timestamp.let { Instant.fromEpochSeconds(it.toLong()) }
//        val lastUpdated = item.attributes.get(TimelineAttribute.LastUpdated)
//            ?.getUIntAt(0, littleEndian = true)?.let { Instant.fromEpochSeconds(it.toLong()) }
//        if (lastUpdated == null) {
//            logger.e("lastUpdated is null")
//            return null
//        }
        return NotificationAppItem(
            packageName = packageName,
            muteState = mutedState,
            stateUpdated = lastUpdated.asMillisecond(),
            name = appName,
            channelGroups = emptyList(),
            lastNotified = lastUpdated.asMillisecond(),
            vibePatternName = null,
            colorName = null,
            iconCode = null,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.d("decoding app record ${e.message}", e)
        return null
    }

}

private fun SFixedList<Attribute>.get(attribute: TimelineAttribute): UByteArray? =
    list.find { it.attributeId.get() == attribute.id }?.content?.get()
