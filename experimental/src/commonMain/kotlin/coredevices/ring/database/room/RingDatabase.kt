package coredevices.ring.database.room

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RingTransferInfo
import coredevices.indexai.data.entity.ToolCall
import coredevices.indexai.data.entity.mcp_sandbox.BuiltinMcpGroupAssociation
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpGroupAssociation
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.database.dao.BuiltinMcpGroupAssociationDao
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.HttpMcpGroupAssociationDao
import coredevices.indexai.database.dao.HttpMcpServerDao
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.McpSandboxGroupDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.indexai.database.dao.RecordingFeedItem
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.data.SemanticResult
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.data.entity.room.RecordingProcessingTaskEntity
import coredevices.ring.data.entity.room.RingDebugTransfer
import coredevices.ring.data.entity.room.RingTransfer
import coredevices.ring.data.entity.room.TraceEntryEntity
import coredevices.ring.data.entity.room.TraceSessionEntity
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.CachedRecordingMetadataDao
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.dao.RecordingProcessingTaskDao
import coredevices.ring.database.room.dao.RingDebugTransferDao
import coredevices.ring.database.room.dao.RingTransferDao
import coredevices.ring.database.room.dao.RingTransferFeedItem
import coredevices.ring.database.room.dao.TraceEntryDao
import coredevices.ring.database.room.dao.TraceSessionDao
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Database(
    exportSchema = true,
    entities = [
        LocalReminderData::class,
        CachedRecordingMetadata::class,
        RingDebugTransfer::class,
        LocalRecording::class,
        ConversationMessageEntity::class,
        RecordingEntryEntity::class,
        RingTransfer::class,
        BuiltinMcpGroupAssociation::class,
        HttpMcpGroupAssociation::class,
        HttpMcpServerEntity::class,
        McpSandboxGroupEntity::class,
        RecordingProcessingTaskEntity::class,
        TraceSessionEntity::class,
        TraceEntryEntity::class,
    ],
    views = [
        RecordingFeedItem::class,
        RingTransferFeedItem::class
    ],
    version = 24,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, Migrate5To6::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10, Migrate9To10::class),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17, Migrate16To17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24),
    ]
)
@TypeConverters(Converters::class)
@ConstructedBy(RingDatabaseConstructor::class)
abstract class RingDatabase: RoomDatabase() {
    abstract fun localReminderDao(): LocalReminderDao
    abstract fun cachedRecordingMetadataDao(): CachedRecordingMetadataDao
    abstract fun ringDebugTransferDao(): RingDebugTransferDao
    abstract fun localRecordingDao(): LocalRecordingDao
    abstract fun conversationMessageDao(): ConversationMessageDao
    abstract fun recordingEntryDao(): RecordingEntryDao
    abstract fun ringTransferDao(): RingTransferDao
    abstract fun builtinMcpGroupAssociationDao(): BuiltinMcpGroupAssociationDao
    abstract fun httpMcpGroupAssociationDao(): HttpMcpGroupAssociationDao
    abstract fun httpMcpServerDao(): HttpMcpServerDao
    abstract fun mcpSandboxGroupDao(): McpSandboxGroupDao
    abstract fun recordingProcessingTaskDao(): RecordingProcessingTaskDao
    abstract fun traceSessionDao(): TraceSessionDao
    abstract fun traceEntryDao(): TraceEntryDao
}

@DeleteColumn("LocalReminderData", "platformId")
class Migrate5To6: AutoMigrationSpec

@DeleteColumn("LocalRecording", "recording")
class Migrate9To10: AutoMigrationSpec

@DeleteColumn("LocalRecording", "notified")
@DeleteColumn("LocalRecording", "discarded")
@DeleteColumn("LocalRecording", "ringRxIndex")
class Migrate16To17: AutoMigrationSpec

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RingDatabaseConstructor : RoomDatabaseConstructor<RingDatabase> {
    override fun initialize(): RingDatabase
}

class Converters {
    @TypeConverter
    fun StringToUuid(string: String?): Uuid? = string?.let { Uuid.parse(it) }

    @TypeConverter
    fun UuidToString(uuid: Uuid?): String? = uuid?.toString()

    @TypeConverter
    fun LongToInstant(long: Long?): Instant? = long?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun InstantToLong(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun RecordingToString(recording: RecordingDocument?) = recording?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToRecording(string: String?) = string?.let {
        try {
            JsonSnake.decodeFromString<RecordingDocument>(it)
        } catch (e: SerializationException) {
            Logger.w { "Failed to deserialize Recording from database, returning empty recording: $e\n$string" }
            RecordingDocument(
                timestamp = Instant.DISTANT_PAST,
                updated = Instant.DISTANT_PAST.toEpochMilliseconds()
            )
        }
    }

    @TypeConverter
    fun ToolCallListToString(toolCalls: List<ToolCall>?) = toolCalls?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToToolCallList(string: String?) = string?.let {
        JsonSnake.decodeFromString<List<ToolCall>>(it)
    }

    @TypeConverter
    fun StringToRingTransferInfo(string: String?) = string?.let {
        return@let try {
            JsonSnake.decodeFromString<RingTransferInfo>(it)
        } catch (e: SerializationException) {
            // Handle legacy data format
            // TODO: Remove this block after a while
            try {
                val ob = JsonSnake.parseToJsonElement(it).jsonObject
                if (ob.containsKey("collection_index")) {
                    return RingTransferInfo(
                        collectionStartIndex = ob["collection_index"]!!.jsonPrimitive.int,
                        collectionEndIndex = ob["collection_index"]!!.jsonPrimitive.int,
                        buttonPressed = ob["button_pressed"]?.jsonPrimitive?.long,
                        buttonReleased = ob["button_released"]?.jsonPrimitive?.long,
                        advertisementReceived = ob["advertisement_received"]!!.jsonPrimitive.long,
                        transferCompleted = ob["transfer_completed"]!!.jsonPrimitive.long,
                        buttonReleaseAdvertisementLatencyMs = ob["button_release_advertisement_latency_ms"]?.jsonPrimitive?.long,
                    )
                } else {
                    Logger.w(e) { "Failed to deserialize RingTransferInfo from database, returning null: ${e.message}\n$string" }
                    return null
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to deserialize legacy RingTransferInfo from database, returning null: ${e.message}\n$string" }
                return null
            }
        }
    }

    @TypeConverter
    fun RingTransferInfoToString(info: RingTransferInfo?) = info?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun SemanticResultToString(result: SemanticResult?) =
        result?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToSemanticResult(string: String?) = string?.let {
        JsonSnake.decodeFromString<SemanticResult>(it)
    }

    @TypeConverter
    fun StringListToString(list: List<String>?) = list?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToStringList(string: String?) = string?.let {
        JsonSnake.decodeFromString<List<String>>(it)
    }
}